package com.proyectosw.sreservas.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
                    EstadoReserva.EN_CURSO);

    /** Horario de atencion del complejo: las reservas deben caer dentro de esta ventana. */
    private static final LocalTime HORA_APERTURA = LocalTime.of(8, 0);
    private static final LocalTime HORA_CIERRE = LocalTime.of(22, 0);

    private final ReservaRepository repository;
    private final CanchaRepository canchaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;
    private final CurrentUserService currentUser;
    private final Clock reloj;

    public ReservaService(final ReservaRepository repository, final CanchaRepository canchaRepository,
            final UsuarioRepository usuarioRepository, final PagoRepository pagoRepository,
            final CurrentUserService currentUser, final Clock reloj) {
        this.repository = repository;
        this.canchaRepository = canchaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pagoRepository = pagoRepository;
        this.currentUser = currentUser;
        this.reloj = reloj;
    }

    /**
     * ADMIN y RECEPCIONISTA ven todas las reservas; CLIENTE solo las propias.
     * El filtro por estado y por dia es opcional y se resuelve en la consulta,
     * no en memoria, para que la paginacion sea correcta (totalElements cuenta
     * solo lo filtrado).
     */
    @Transactional
    public Page<Reserva> listarTodos(Pageable pageable, EstadoReserva estado, LocalDate fecha) {
        // Antes de listar, las confirmadas ya vencidas pasan a NO_ASISTIO.
        repository.marcarNoAsistio(EstadoReserva.CONFIRMADA, EstadoReserva.NO_ASISTIO, LocalDateTime.now(reloj));

        Usuario actual = currentUser.get();
        boolean esCliente = Rol.CLIENTE.equals(actual.getRol());
        Integer usuarioId = esCliente ? actual.getId() : null;
        // El cliente no ve sus canceladas salvo que las pida explicitamente.
        boolean incluirCancelada = !esCliente || estado != null;
        // Sin fecha se usa un rango abierto: PostgreSQL no puede tipar parametros nulos
        // dentro de un coalesce, asi que nunca se envian nulos al filtro de fechas.
        LocalDateTime desde = fecha == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : fecha.atStartOfDay();
        LocalDateTime hasta = fecha == null ? LocalDateTime.of(9999, 12, 31, 0, 0) : desde.plusDays(1);
        return repository.buscar(usuarioId, estado, incluirCancelada, desde, hasta, pageable);
    }

    /**
     * Franjas ocupadas de una cancha en un dia concreto. Solo cuentan los estados
     * que bloquean el horario (CANCELADA no ocupa), igual que la validacion al crear.
     */
    public List<Reserva> listarOcupadas(Integer canchaId, LocalDate fecha) {
        LocalDateTime desde = fecha.atStartOfDay();
        return repository.buscarOcupadas(canchaId, ESTADOS_QUE_OCUPAN, desde, desde.plusDays(1));
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
        verificarHorarioAtencion(reserva.getFechaHoraInicio(), reserva.getFechaHoraFin());
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
        verificarHorarioAtencion(existente.getFechaHoraInicio(), existente.getFechaHoraFin());
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

        if (destino == EstadoReserva.EN_CURSO) {
            // El check-in solo se hace el mismo dia y antes de que termine la reserva.
            if (!existente.getFechaHoraInicio().toLocalDate().equals(LocalDate.now(reloj))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Solo se puede realizar el check-in el mismo día de la reserva");
            }
            if (!existente.getFechaHoraFin().isAfter(LocalDateTime.now(reloj))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El horario de la reserva ya finalizó");
            }
            if (pagoRepository.findByReserva_Id(id).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Registrá la seña antes de iniciar la reserva");
            }
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
            case COMPLETADA, CANCELADA, NO_ASISTIO -> false;
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

    /** Solo se reserva dentro del horario de atencion (08:00 a 22:00), el mismo dia. */
    private void verificarHorarioAtencion(LocalDateTime inicio, LocalDateTime fin) {
        if (!inicio.isAfter(LocalDateTime.now(reloj))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha y hora de inicio debe ser posterior al momento actual");
        }
        if (!inicio.toLocalDate().equals(fin.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La reserva debe empezar y terminar el mismo día");
        }
        LocalTime horaInicio = inicio.toLocalTime();
        LocalTime horaFin = fin.toLocalTime();
        if (horaInicio.isBefore(HORA_APERTURA) || horaFin.isAfter(HORA_CIERRE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El horario de atención es de 08:00 a 22:00");
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
