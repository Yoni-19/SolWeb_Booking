package com.proyectosw.sreservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.proyectosw.sreservas.model.Cancha;
import com.proyectosw.sreservas.model.Pago;
import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.model.enums.EstadoReserva;
import com.proyectosw.sreservas.model.enums.Rol;
import com.proyectosw.sreservas.repository.CanchaRepository;
import com.proyectosw.sreservas.repository.PagoRepository;
import com.proyectosw.sreservas.repository.ReservaRepository;
import com.proyectosw.sreservas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTests {

    @Mock
    private ReservaRepository repository;
    @Mock
    private CanchaRepository canchaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private CurrentUserService currentUser;

    /** Reloj fijo en la zona del complejo para que las reglas temporales no dependan del dia real. */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 25, 13, 0);

    private ReservaService service;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
        service = new ReservaService(repository, canchaRepository, usuarioRepository,
                pagoRepository, currentUser, reloj);
    }

    @Test
    void elClienteSoloListaSusPropiasReservas() {
        when(currentUser.get()).thenReturn(usuario(2, Rol.CLIENTE));
        when(repository.buscar(eq(2), any(), eq(false), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reservaDe(2))));

        Page<Reserva> reservas = service.listarTodos(PageRequest.of(0, 10), null, null);

        assertThat(reservas.getContent()).hasSize(1);
        verify(repository, never()).findAll(any(Pageable.class));
        verify(repository, never()).findByUsuario_Id(anyInt(), any(Pageable.class));
    }

    @Test
    void elRecepcionistaListaTodasLasReservas() {
        when(currentUser.get()).thenReturn(usuario(9, Rol.RECEPCIONISTA));
        when(repository.buscar(any(), any(), eq(true), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reservaDe(2), reservaDe(3))));

        assertThat(service.listarTodos(PageRequest.of(0, 10), null, null).getContent()).hasSize(2);
        verify(repository, never()).findByUsuario_Id(anyInt(), any(Pageable.class));
    }

    @Test
    void elFiltroPorEstadoSeResuelveEnLaConsulta() {
        when(currentUser.get()).thenReturn(usuario(9, Rol.ADMIN));
        when(repository.buscar(any(), eq(EstadoReserva.PENDIENTE), eq(true), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reservaDe(2))));

        Page<Reserva> pagina = service.listarTodos(PageRequest.of(0, 10), EstadoReserva.PENDIENTE, null);

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void elClienteNoPuedeVerUnaReservaAjena() {
        when(currentUser.get()).thenReturn(usuario(2, Rol.CLIENTE));
        when(repository.findById(5)).thenReturn(Optional.of(reservaDe(3)));

        assertThatThrownBy(() -> service.buscarPorId(5))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void elClienteNoPuedeReservarAParaOtroUsuario() {
        when(currentUser.get()).thenReturn(usuario(2, Rol.CLIENTE));
        when(canchaRepository.findById(1)).thenReturn(Optional.of(canchaActiva()));
        when(repository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva creada = service.crear(reservaPara(1, 3));

        assertThat(creada.getUsuario().getId()).isEqualTo(2);
        assertThat(creada.getEstadoReserva()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(creada.getMontoTotal()).isEqualByComparingTo("150.00");
    }

    @Test
    void elClientePuedeReprogramarSuPropiaReserva() {
        when(currentUser.get()).thenReturn(usuario(2, Rol.CLIENTE));
        when(repository.findById(5)).thenReturn(Optional.of(reservaDe(2)));
        when(canchaRepository.findById(1)).thenReturn(Optional.of(canchaActiva()));
        when(repository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva reprogramada = service.actualizar(5, reservaPara(1, null));

        assertThat(reprogramada.getFechaHoraInicio()).isEqualTo(LocalDateTime.of(2026, 12, 1, 10, 0));
        assertThat(reprogramada.getMontoTotal()).isEqualByComparingTo("150.00");
    }

    @Test
    void elClienteNoPuedeReprogramarUnaReservaAjena() {
        when(currentUser.get()).thenReturn(usuario(2, Rol.CLIENTE));
        when(repository.findById(5)).thenReturn(Optional.of(reservaDe(3)));

        assertThatThrownBy(() -> service.actualizar(5, reservaPara(1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(repository, never()).save(any(Reserva.class));
    }

    @Test
    void elClienteNoPuedeCancelarUnaReservaAjena() {
        when(currentUser.get()).thenReturn(usuario(2, Rol.CLIENTE));
        when(repository.findById(5)).thenReturn(Optional.of(reservaDe(3)));

        assertThatThrownBy(() -> service.eliminar(5))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(repository, never()).delete(any(Reserva.class));
    }

    @Test
    void noSePuedeReprogramarUnaReservaCompletada() {
        when(currentUser.get()).thenReturn(usuario(9, Rol.ADMIN));
        Reserva completada = reservaDe(2);
        completada.setEstadoReserva(EstadoReserva.COMPLETADA);
        when(repository.findById(5)).thenReturn(Optional.of(completada));

        assertThatThrownBy(() -> service.actualizar(5, reservaPara(1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void noSePuedeConfirmarUnaReservaSinRegistrarLaSenia() {
        when(currentUser.get()).thenReturn(usuario(9, Rol.ADMIN));
        when(repository.findById(5)).thenReturn(Optional.of(reservaDe(2)));

        assertThatThrownBy(() -> service.cambiarEstado(5, EstadoReserva.CONFIRMADA))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void noSePuedeIniciarUnaReservaSinPagoRegistrado() {
        when(currentUser.get()).thenReturn(usuario(9, Rol.ADMIN));
        Reserva confirmada = reservaDe(2);
        confirmada.setEstadoReserva(EstadoReserva.CONFIRMADA);
        // El check-in exige el mismo dia; el pago faltante se valida despues.
        confirmada.setFechaHoraInicio(AHORA.toLocalDate().atStartOfDay());
        confirmada.setFechaHoraFin(AHORA.toLocalDate().atTime(23, 59, 59));
        when(repository.findById(5)).thenReturn(Optional.of(confirmada));
        when(pagoRepository.findByReserva_Id(5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(5, EstadoReserva.EN_CURSO))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void noSePuedeCompletarUnaReservaConSaldoPendiente() {
        when(currentUser.get()).thenReturn(usuario(9, Rol.ADMIN));
        Reserva enCurso = reservaDe(2);
        enCurso.setEstadoReserva(EstadoReserva.EN_CURSO);
        when(repository.findById(5)).thenReturn(Optional.of(enCurso));

        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("50.00"));
        when(pagoRepository.findByReserva_Id(5)).thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> service.cambiarEstado(5, EstadoReserva.COMPLETADA))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(repository, never()).save(any(Reserva.class));
    }

    @Test
    void elCheckOutConElPagoCompletoCompletaLaReserva() {
        when(currentUser.get()).thenReturn(usuario(9, Rol.ADMIN));
        Reserva enCurso = reservaDe(2);
        enCurso.setEstadoReserva(EstadoReserva.EN_CURSO);
        when(repository.findById(5)).thenReturn(Optional.of(enCurso));
        when(repository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("75.00"));
        when(pagoRepository.findByReserva_Id(5)).thenReturn(Optional.of(pago));

        Reserva completada = service.cambiarEstado(5, EstadoReserva.COMPLETADA);

        assertThat(completada.getEstadoReserva()).isEqualTo(EstadoReserva.COMPLETADA);
    }

    @Test
    void noSePuedeReprogramarSiElPagoSuperaElNuevoTotal() {
        when(currentUser.get()).thenReturn(usuario(9, Rol.ADMIN));
        when(repository.findById(5)).thenReturn(Optional.of(reservaDe(2)));
        when(canchaRepository.findById(1)).thenReturn(Optional.of(canchaActiva()));

        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("200.00"));
        when(pagoRepository.findByReserva_Id(5)).thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> service.actualizar(5, reservaPara(1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(repository, never()).save(any(Reserva.class));
    }

    @Test
    void laDisponibilidadConsultaElDiaCompleto() {
        LocalDate fecha = LocalDate.of(2030, 12, 1);
        when(repository.buscarOcupadas(eq(1), any(), eq(fecha.atStartOfDay()),
                eq(fecha.plusDays(1).atStartOfDay())))
                .thenReturn(List.of(reservaDe(2)));

        assertThat(service.listarOcupadas(1, fecha)).hasSize(1);
    }

    @Test
    void aceptaUnInicioFuturoDelMismoDia() {
        when(currentUser.get()).thenReturn(usuario(2, Rol.CLIENTE));
        when(canchaRepository.findById(1)).thenReturn(Optional.of(canchaActiva()));
        when(repository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva reserva = reservaPara(1, null);
        reserva.setFechaHoraInicio(AHORA.toLocalDate().atTime(16, 0));
        reserva.setFechaHoraFin(AHORA.toLocalDate().atTime(17, 0));

        assertThat(service.crear(reserva).getEstadoReserva()).isEqualTo(EstadoReserva.PENDIENTE);
    }

    @Test
    void rechazaUnInicioQueYaPaso() {
        when(currentUser.get()).thenReturn(usuario(2, Rol.CLIENTE));
        when(canchaRepository.findById(1)).thenReturn(Optional.of(canchaActiva()));

        Reserva reserva = reservaPara(1, null);
        reserva.setFechaHoraInicio(AHORA.toLocalDate().atTime(9, 0));
        reserva.setFechaHoraFin(AHORA.toLocalDate().atTime(10, 0));

        assertThatThrownBy(() -> service.crear(reserva))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any(Reserva.class));
    }

    private Usuario usuario(Integer id, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombres("Nombre" + id);
        usuario.setApellidos("Apellido" + id);
        usuario.setCorreo("usuario" + id + "@test.com");
        usuario.setClave("hash");
        usuario.setRol(rol);
        return usuario;
    }

    private Reserva reservaDe(Integer usuarioId) {
        Reserva reserva = new Reserva();
        reserva.setId(5);
        reserva.setUsuario(usuario(usuarioId, Rol.CLIENTE));
        reserva.setCancha(canchaActiva());
        reserva.setFechaHoraInicio(LocalDateTime.of(2026, 12, 1, 10, 0));
        reserva.setFechaHoraFin(LocalDateTime.of(2026, 12, 1, 11, 0));
        reserva.setEstadoReserva(EstadoReserva.PENDIENTE);
        reserva.setMontoTotal(new BigDecimal("75.00"));
        return reserva;
    }

    private Reserva reservaPara(Integer canchaId, Integer usuarioId) {
        Cancha cancha = new Cancha();
        cancha.setId(canchaId);
        Reserva reserva = new Reserva();
        reserva.setCancha(cancha);
        reserva.setUsuario(usuario(usuarioId, Rol.CLIENTE));
        reserva.setFechaHoraInicio(LocalDateTime.of(2026, 12, 1, 10, 0));
        reserva.setFechaHoraFin(LocalDateTime.of(2026, 12, 1, 11, 30));
        return reserva;
    }

    private Cancha canchaActiva() {
        Cancha cancha = new Cancha();
        cancha.setId(1);
        cancha.setNombre("Cancha 1");
        cancha.setActiva(true);
        cancha.setPrecioPorHora(new BigDecimal("75.00"));
        return cancha;
    }
}
