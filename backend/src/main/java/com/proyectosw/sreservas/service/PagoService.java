package com.proyectosw.sreservas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.proyectosw.sreservas.model.Pago;
import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.model.enums.EstadoPago;
import com.proyectosw.sreservas.model.enums.EstadoReserva;
import com.proyectosw.sreservas.model.enums.Rol;
import com.proyectosw.sreservas.repository.PagoRepository;
import com.proyectosw.sreservas.repository.ReservaRepository;

import jakarta.transaction.Transactional;

/**
 * Registra el pago de una reserva. Hay un solo pago por reserva y el monto se
 * acumula: primero la seña del 50%, despues el saldo.
 *
 * <p>Reglas:</p>
 * <ul>
 *   <li>La primera entrega debe cubrir al menos el 50% del total.</li>
 *   <li>El CLIENTE solo puede pagar la seña (50%) o el total (100%) de una
 *       reserva propia; el saldo restante lo cobra el personal.</li>
 *   <li>Registrar la seña confirma una reserva PENDIENTE.</li>
 *   <li>Si la reserva esta EN_CURSO y el pago llega al total, se completa.</li>
 * </ul>
 */
@Service
public class PagoService {
    private final PagoRepository repository;
    private final ReservaRepository reservaRepository;
    private final CurrentUserService currentUser;
    private final Clock reloj;

    public PagoService(final PagoRepository repository, final ReservaRepository reservaRepository,
            final CurrentUserService currentUser, final Clock reloj) {
        this.repository = repository;
        this.reservaRepository = reservaRepository;
        this.currentUser = currentUser;
        this.reloj = reloj;
    }

    public List<Pago> listarTodos() {
        return repository.findAll();
    }

    public Optional<Pago> buscarPorId(Integer id) {
        return repository.findById(id);
    }

    public Optional<Pago> buscarPorReserva(Integer reservaId) {
        return repository.findByReserva_Id(reservaId);
    }

    /**
     * Carga en lote los pagos de una pagina de reservas, indexados por id de
     * reserva, para evitar una consulta por fila.
     */
    public Map<Integer, Pago> buscarPorReservas(Collection<Integer> reservaIds) {
        if (reservaIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByReserva_IdIn(reservaIds).stream()
                .collect(Collectors.toMap(pago -> pago.getReserva().getId(), pago -> pago));
    }

    /** Porcentaje minimo que se debe entregar al reservar. */
    public static BigDecimal seniaMinima(BigDecimal montoTotal) {
        return montoTotal.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Pago crear(Pago pago) {
        if (pago.getReserva() == null || pago.getReserva().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pago debe referenciar una reserva");
        }
        if (pago.getMetodoPago() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El método de pago es obligatorio");
        }
        if (pago.getMonto() == null || pago.getMonto().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor a cero");
        }

        Reserva reserva = reservaRepository.findById(pago.getReserva().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la reserva con id " + pago.getReserva().getId()));

        Usuario actual = currentUser.get();
        boolean esCliente = Rol.CLIENTE.equals(actual.getRol());

        if (esCliente && !reserva.getUsuario().getId().equals(actual.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La reserva pertenece a otro usuario");
        }
        if (reserva.getEstadoReserva() == EstadoReserva.CANCELADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede pagar una reserva cancelada");
        }
        if (reserva.getEstadoReserva() == EstadoReserva.COMPLETADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La reserva ya está completada");
        }
        if (reserva.getEstadoReserva() == EstadoReserva.NO_ASISTIO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La reserva figura como no asistida");
        }

        Optional<Pago> existenteOpt = repository.findByReserva_Id(reserva.getId());
        BigDecimal total = reserva.getMontoTotal();
        BigDecimal senia = seniaMinima(total);
        BigDecimal yaPagado = existenteOpt.map(Pago::getMonto).orElse(BigDecimal.ZERO);
        BigDecimal nuevoTotal = yaPagado.add(pago.getMonto());

        if (esCliente) {
            if (existenteOpt.isPresent()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "El saldo restante se abona en recepción");
            }
            boolean pagaSenia = pago.getMonto().compareTo(senia) == 0;
            boolean pagaTotal = pago.getMonto().compareTo(total) == 0;
            if (!pagaSenia && !pagaTotal) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El cliente solo puede abonar la seña del 50% o el total de la reserva");
            }
        } else {
            // El saldo final solo se cobra con la reserva ya iniciada (check-in).
            if (existenteOpt.isPresent() && reserva.getEstadoReserva() != EstadoReserva.EN_CURSO) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Iniciá la reserva (check-in) antes de cobrar el saldo");
            }
            if (existenteOpt.isEmpty() && pago.getMonto().compareTo(senia) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La primera entrega debe cubrir al menos el 50% del total");
            }
            if (nuevoTotal.compareTo(total) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El pago supera el saldo pendiente de la reserva");
            }
        }

        Pago pagoFinal = existenteOpt.orElseGet(Pago::new);
        pagoFinal.setReserva(reserva);
        pagoFinal.setMetodoPago(pago.getMetodoPago());
        pagoFinal.setMonto(nuevoTotal);
        pagoFinal.setFechaPago(LocalDateTime.now(reloj));
        pagoFinal.setEstadoPago(nuevoTotal.compareTo(total) >= 0
                ? EstadoPago.PAGADO
                : EstadoPago.PARCIAL);

        if (reserva.getEstadoReserva() == EstadoReserva.PENDIENTE) {
            reserva.setEstadoReserva(EstadoReserva.CONFIRMADA);
            reservaRepository.save(reserva);
        } else if (reserva.getEstadoReserva() == EstadoReserva.EN_CURSO
                && pagoFinal.getEstadoPago() == EstadoPago.PAGADO) {
            reserva.setEstadoReserva(EstadoReserva.COMPLETADA);
            reservaRepository.save(reserva);
        }

        return repository.save(pagoFinal);
    }

    @Transactional
    public Pago actualizar(Integer id, Pago datos) {
        Pago existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe el pago con id " + id));
        if (datos.getMetodoPago() != null) {
            existente.setMetodoPago(datos.getMetodoPago());
        }
        return repository.save(existente);
    }

    @Transactional
    public void eliminar(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No existe el pago con id " + id);
        }
        repository.deleteById(id);
    }
}
