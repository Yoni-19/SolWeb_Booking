package com.proyectosw.sreservas.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.proyectosw.sreservas.model.Cancha;
import com.proyectosw.sreservas.model.Pago;
import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.model.enums.EstadoPago;
import com.proyectosw.sreservas.model.enums.EstadoReserva;
import com.proyectosw.sreservas.model.enums.Rol;
import com.proyectosw.sreservas.repository.CanchaRepository;
import com.proyectosw.sreservas.repository.PagoRepository;
import com.proyectosw.sreservas.repository.ReservaRepository;
import com.proyectosw.sreservas.repository.UsuarioRepository;

import jakarta.transaction.Transactional;

@Service
public class ReservaService {
    private static final Collection<EstadoReserva> ESTADOS_QUE_OCUPAN =
            List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA,
                    EstadoReserva.EN_CURSO, EstadoReserva.COMPLETADA);

    private final ReservaRepository repository;
    private final CanchaRepository canchaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;
    private final CurrentUserService currentUser;

    public ReservaService(final ReservaRepository repository, final CanchaRepository canchaRepository,
            final UsuarioRepository usuarioRepository, final PagoRepository pagoRepository,
            final CurrentUserService currentUser) {
        this.repository = repository;
        this.canchaRepository = canchaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pagoRepository = pagoRepository;
        this.currentUser = currentUser;
    }

    /**
     * ADMIN y RECEPCIONISTA ven todas las reservas; CLIENTE solo las propias.
     * El filtro por estado es opcional y se resuelve en la consulta, no en memoria,
     * para que la paginacion sea correcta (totalElements cuenta solo lo filtrado).
     */
    public Page<Reserva> listarTodos(Pageable pageable, EstadoReserva estado) {
        Usuario actual = currentUser.get();
        if (Rol.CLIENTE.equals(actual.getRol())) {
            // El cliente no ve sus canceladas: no muestran el estado y solo ensucian el listado.
            return estado == null
                    ? repository.findByUsuario_IdAndEstadoReservaNot(actual.getId(), EstadoReserva.CANCELADA, pageable)
                    : repository.findByUsuario_IdAndEstadoReserva(actual.getId(), estado, pageable);
        }
        return estado == null
                ? repository.findAll(pageable)
                : repository.findByEstadoReserva(estado, pageable);
    }

    public Optional<Reserva> buscarPorId(Integer id) {
        Optional<Reserva> reserva = repository.findById(id);
        reserva.ifPresent(this::verificarPropiedad);
        return reserva;
    }

    @Transactional 
    public Reserva crear(Reserva reserva){
        if (reserva.getCancha() == null || reserva.getCancha().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reserva debe indicar una cancha");
        }

        Usuario actual = currentUser.get();
        boolean esCliente = Rol.CLIENTE.equals(actual.getRol());
        if (!esCliente && (reserva.getUsuario() == null || reserva.getUsuario().getId() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reserva debe indicar un usuario");
        }

        Cancha cancha = canchaRepository.findById(reserva.getCancha().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la cancha con id " + reserva.getCancha().getId()));
        if (!cancha.isActiva()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cancha " + cancha.getNombre() + " está inactiva y no se puede reservar");
        }
        reserva.setCancha(cancha);

        if (esCliente) {
            // El cliente reserva siempre para si mismo: se ignora el usuarioId del cuerpo.
            reserva.setUsuario(actual);
        } else {
            Usuario usuario = usuarioRepository.findById(reserva.getUsuario().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No existe el usuario con id " + reserva.getUsuario().getId()));
            reserva.setUsuario(usuario);
        }

        if (!reserva.getFechaHoraFin().isAfter(reserva.getFechaHoraInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la de inicio");
        }
        verificarSolapamiento(cancha.getId(), null,
                reserva.getFechaHoraInicio(), reserva.getFechaHoraFin());

        reserva.setEstadoReserva(EstadoReserva.PENDIENTE);
        recalcularMonto(reserva);

        return repository.save(reserva);
    }

    @Transactional 
    public Reserva actualizar(Integer id, Reserva datos){
        Reserva existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la reserva con id " + id));

        // Un CLIENTE solo reprograma sus propias reservas; el personal puede con todas.
        verificarPropiedad(existente);

        if (existente.getEstadoReserva() != EstadoReserva.PENDIENTE
                && existente.getEstadoReserva() != EstadoReserva.CONFIRMADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede reprogramar una reserva pendiente o confirmada");
        }

        if (datos.getFechaHoraInicio() != null) existente.setFechaHoraInicio(datos.getFechaHoraInicio());
        if (datos.getFechaHoraFin() != null)     existente.setFechaHoraFin(datos.getFechaHoraFin());

        if (datos.getCancha() != null && datos.getCancha().getId() != null) {
            Cancha cancha = canchaRepository.findById(datos.getCancha().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la cancha con id " + datos.getCancha().getId()));
            if (!cancha.isActiva()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cancha " + cancha.getNombre() + " está inactiva y no se puede reservar");
            }

            existente.setCancha(cancha);
        }

        if (!existente.getFechaHoraFin().isAfter(existente.getFechaHoraInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la de inicio");
        }
        verificarSolapamiento(existente.getCancha().getId(), id,
                existente.getFechaHoraInicio(), existente.getFechaHoraFin());

        recalcularMonto(existente);
        actualizarEstadoPago(existente);

        return repository.save(existente);      
    }

    @Transactional
    public void eliminar(Integer id) {
        Reserva existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe reserva con id " + id));

        // Un CLIENTE solo cancela sus propias reservas.
        verificarPropiedad(existente);

        if (existente.getEstadoReserva() == EstadoReserva.COMPLETADA
                || existente.getEstadoReserva() == EstadoReserva.EN_CURSO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar una reserva completada o en curso");
        }
        repository.delete(existente);
    }

    /**
     * Cambia el estado de una reserva respetando el ciclo de vida:
     * PENDIENTE -> CANCELADA, CONFIRMADA -> EN_CURSO (check-in) o CANCELADA,
     * y EN_CURSO -> COMPLETADA (check-out). La confirmacion no se hace a mano:
     * la produce el registro de la seña en {@link PagoService}.
     * El check-in/out es exclusivo del personal; el CLIENTE solo puede cancelar
     * sus propias reservas.
     */
    @Transactional
    public Reserva cambiarEstado(Integer id, EstadoReserva destino) {
        Reserva existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe reserva con id " + id));

        Usuario actual = currentUser.get();
        if (Rol.CLIENTE.equals(actual.getRol())) {
            if (destino != EstadoReserva.CANCELADA) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Solo el personal puede iniciar o completar una reserva");
            }
            verificarPropiedad(existente);
        }

        EstadoReserva estadoActual = existente.getEstadoReserva();
        if (!transicionPermitida(estadoActual, destino)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede pasar de " + estadoActual + " a " + destino);
        }

        if (destino == EstadoReserva.EN_CURSO
                && pagoRepository.findByReserva_Id(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Registrá la seña antes de iniciar la reserva");
        }
        if (destino == EstadoReserva.COMPLETADA) {
            BigDecimal pagado = pagoRepository.findByReserva_Id(id)
                    .map(Pago::getMonto)
                    .orElse(BigDecimal.ZERO);
            if (pagado.compareTo(existente.getMontoTotal()) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Falta cobrar el saldo antes de completar la reserva");
            }
        }

        existente.setEstadoReserva(destino);
        return repository.save(existente);
    }

    private boolean transicionPermitida(EstadoReserva actual, EstadoReserva destino) {
        return switch (actual) {
            case PENDIENTE -> destino == EstadoReserva.CANCELADA;
            case CONFIRMADA -> destino == EstadoReserva.EN_CURSO || destino == EstadoReserva.CANCELADA;
            case EN_CURSO -> destino == EstadoReserva.COMPLETADA;
            case COMPLETADA, CANCELADA -> false;
        };
    }

    /** Un cliente no puede ver reservas ajenas; el personal si. */
    private void verificarPropiedad(Reserva reserva) {
        Usuario actual = currentUser.get();
        if (Rol.CLIENTE.equals(actual.getRol())
                && !reserva.getUsuario().getId().equals(actual.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La reserva pertenece a otro usuario");
        }
    }

    private void verificarSolapamiento(Integer canchaId, Integer reservaPropiaId,
                                       LocalDateTime inicio, LocalDateTime fin) {
        boolean ocupada = repository.existeSolapamiento(canchaId, reservaPropiaId,
                ESTADOS_QUE_OCUPAN, inicio, fin);
        if (ocupada) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La cancha ya está reservada en ese horario");
        }
    }

    /**
     * Si la reserva ya tiene un pago, valida que el nuevo total no quede por
     * debajo de lo abonado y recalcula si el pago es PARCIAL o PAGADO.
     */
    private void actualizarEstadoPago(Reserva reserva) {
        pagoRepository.findByReserva_Id(reserva.getId()).ifPresent(pago -> {
            if (pago.getMonto().compareTo(reserva.getMontoTotal()) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El pago registrado supera el nuevo total de la reserva");
            }
            pago.setEstadoPago(pago.getMonto().compareTo(reserva.getMontoTotal()) >= 0
                    ? EstadoPago.PAGADO
                    : EstadoPago.PARCIAL);
            pagoRepository.save(pago);
        });
    }

    private void recalcularMonto(Reserva reserva) {
        BigDecimal precioHora = reserva.getCancha().getPrecioPorHora();
        long minutos = Duration.between(
                reserva.getFechaHoraInicio(),
                reserva.getFechaHoraFin()).toMinutes();
        long horas = (minutos + 59) / 60;
        reserva.setMontoTotal(precioHora.multiply(BigDecimal.valueOf(horas)));
    }
}
