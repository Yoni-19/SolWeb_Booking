package com.proyectosw.sreservas.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;

// Spring Boot 4 usa Jackson 3: el ObjectMapper autoconfigurado vive en tools.jackson.
import tools.jackson.databind.ObjectMapper;

/**
 * Verifica la matriz de permisos de SecurityConfig contra la API real.
 * Usa los usuarios del seed: admin@email.com (ADMIN) y laura@email.com (CLIENTE).
 * Requiere PostgreSQL arriba, igual que el resto de los tests del proyecto.
 *
 * <p>La sesion viaja en la cookie httpOnly que setea el login, igual que en el
 * navegador. {@code @Transactional} hace rollback de todo lo que escribe cada test:
 * el usuario temporal de {@code elRecepcionista...} no queda en la base aunque la
 * corrida se corte. El DELETE del {@code finally} se conserva y exige 204, asi un
 * fallo de limpieza rompe el test en vez de ensuciar la base.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeguridadPorRolTests {

    private static final String CLAVE_SEED = "test123";
    /** Clave de los usuarios creados por los tests: el alta exige 8 caracteres. */
    private static final String CLAVE_ALTA = "test1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void sinTokenLasRutasPrivadasDevuelven401() throws Exception {
        mockMvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void elRegistroEsPublicoYLaSeguridadNoLoFrena() throws Exception {
        // Cuerpo vacio: llega al controller y falla la validacion (400), no la seguridad.
        mockMvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elClienteConsultaCanchasYReservasPeroNoUsuariosNiReservasAjenas() throws Exception {
        Cookie sesion = cookieDe("laura@email.com", CLAVE_SEED);

        mockMvc.perform(get("/api/canchas").cookie(sesion))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reservas").cookie(sesion))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/usuarios").cookie(sesion))
                .andExpect(status().isForbidden());
        // Una reserva ajena: reprogramarla da 403, no 400 ni 200.
        int ajena = crearReserva(cookieDe("pedro@email.com", CLAVE_SEED));
        mockMvc.perform(put("/api/reservas/" + ajena).cookie(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHoraInicio":"2030-11-03T09:00:00","fechaHoraFin":"2030-11-03T10:30:00","canchaId":1}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void elPerfilPropioDevuelveElRolDeLaBase() throws Exception {
        Cookie sesion = cookieDe("laura@email.com", CLAVE_SEED);

        mockMvc.perform(get("/api/auth/me").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("laura@email.com"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void elAdminGestionaUsuariosYRoles() throws Exception {
        Cookie sesion = cookieDe("admin@email.com", CLAVE_SEED);

        mockMvc.perform(get("/api/usuarios").cookie(sesion))
                .andExpect(status().isOk());
    }

    @Test
    void elAdminVeClientesSinListarseYFiltraPorRol() throws Exception {
        Cookie sesion = cookieDe("admin@email.com", CLAVE_SEED);

        // El admin no se lista a si mismo, pero si ve a los clientes.
        mockMvc.perform(get("/api/usuarios?size=100").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.correo == 'admin@email.com')]").isEmpty())
                .andExpect(jsonPath("$.content[?(@.rol == 'CLIENTE')]").isNotEmpty());

        mockMvc.perform(get("/api/usuarios?rol=CLIENTE&size=100").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.rol != 'CLIENTE')]").isEmpty());
    }

    @Test
    void elClienteCancelaSuReservaPeroNoLaAjenaNiHaceCheckIn() throws Exception {
        Cookie laura = cookieDe("laura@email.com", CLAVE_SEED);

        int propia = crearReserva(laura);
        mockMvc.perform(patch("/api/reservas/" + propia + "/estado").cookie(laura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoReserva\":\"CANCELADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReserva").value("CANCELADA"));
        // El listado del cliente oculta las canceladas.
        mockMvc.perform(get("/api/reservas?size=100").cookie(laura))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(propia)).isEmpty());

        // No puede cancelar la reserva de otro usuario.
        int ajena = crearReserva(cookieDe("pedro@email.com", CLAVE_SEED));
        mockMvc.perform(patch("/api/reservas/" + ajena + "/estado").cookie(laura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoReserva\":\"CANCELADA\"}"))
                .andExpect(status().isForbidden());

        // Tampoco puede hacer check-in.
        int otra = crearReserva(laura);
        mockMvc.perform(patch("/api/reservas/" + otra + "/estado").cookie(laura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoReserva\":\"CONFIRMADA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elRecepcionistaLeeUsuariosPeroNoCambiaRolesNiGestionaCanchas() throws Exception {
        Cookie admin = cookieDe("admin@email.com", CLAVE_SEED);
        String correo = "prueba.recepcionista." + UUID.randomUUID() + "@test.local";

        String alta = mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Prueba","apellidos":"Recepcionista","correo":"%s","clave":"test1234"}
                                """.formatted(correo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = mapper.readTree(alta).get("id").asInt();

        try {
            mockMvc.perform(put("/api/usuarios/" + id + "/rol")
                            .cookie(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rol\":\"RECEPCIONISTA\"}"))
                    .andExpect(status().isOk());

            Cookie recepcionista = cookieDe(correo, CLAVE_ALTA);

            // Solo recibe CLIENTES: ni se ve a si mismo ni aparece el ADMIN del seed.
            mockMvc.perform(get("/api/usuarios?size=100").cookie(recepcionista))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.rol != 'CLIENTE')]").isEmpty())
                    .andExpect(jsonPath("$.content[?(@.correo == '%s')]".formatted(correo)).isEmpty())
                    .andExpect(jsonPath("$.content[?(@.correo == 'admin@email.com')]").isEmpty());
            // El filtro que mande el cliente no puede ampliar lo que ve.
            mockMvc.perform(get("/api/usuarios?rol=ADMIN&size=100").cookie(recepcionista))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.rol != 'CLIENTE')]").isEmpty());
            // Tampoco puede consultar por id a un trabajador (el admin del seed es id 1).
            mockMvc.perform(get("/api/usuarios/1").cookie(recepcionista))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/usuarios/" + id + "/rol")
                            .cookie(recepcionista)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rol\":\"CLIENTE\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/canchas")
                            .cookie(recepcionista)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        } finally {
            mockMvc.perform(delete("/api/usuarios/" + id).cookie(admin))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void elAdminCreaPersonalYRechazaRolDeCliente() throws Exception {
        Cookie admin = cookieDe("admin@email.com", CLAVE_SEED);
        String correo = "prueba.personal." + UUID.randomUUID() + "@test.local";

        mockMvc.perform(post("/api/usuarios/personal").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Prueba","apellidos":"Personal","correo":"%s","clave":"test1234","rol":"RECEPCIONISTA"}
                                """.formatted(correo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("RECEPCIONISTA"));

        // El alta de personal no puede crear clientes.
        mockMvc.perform(post("/api/usuarios/personal").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Prueba","apellidos":"Cliente","correo":"%s","clave":"test1234","rol":"CLIENTE"}
                                """.formatted("cliente." + UUID.randomUUID() + "@test.local")))
                .andExpect(status().isBadRequest());

        // El rol asignado aplica al instante: puede iniciar sesion como recepcionista.
        cookieDe(correo, CLAVE_ALTA);
    }

    @Test
    void elAdminListaPersonalSinClientesNiSuPropioUsuario() throws Exception {
        Cookie admin = cookieDe("admin@email.com", CLAVE_SEED);
        String correo = "prueba.lista.personal." + UUID.randomUUID() + "@test.local";

        mockMvc.perform(post("/api/usuarios/personal").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Prueba","apellidos":"Entrenador","correo":"%s","clave":"test1234","rol":"ENTRENADOR"}
                                """.formatted(correo)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/usuarios?personal=true&size=100").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.correo == '%s')]".formatted(correo)).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.rol == 'CLIENTE')]").isEmpty())
                .andExpect(jsonPath("$.content[?(@.correo == 'admin@email.com')]").isEmpty());
    }

    @Test
    void elRecepcionistaNoPuedeVerNiCrearPersonal() throws Exception {
        Cookie admin = cookieDe("admin@email.com", CLAVE_SEED);
        String correo = "prueba.recepcion.personal." + UUID.randomUUID() + "@test.local";

        mockMvc.perform(post("/api/usuarios/personal").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Prueba","apellidos":"Recepcion","correo":"%s","clave":"test1234","rol":"RECEPCIONISTA"}
                                """.formatted(correo)))
                .andExpect(status().isCreated());

        Cookie recepcionista = cookieDe(correo, CLAVE_ALTA);

        // El flag personal no amplia la vista: sigue recibiendo solo CLIENTES.
        mockMvc.perform(get("/api/usuarios?personal=true&size=100").cookie(recepcionista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.rol != 'CLIENTE')]").isEmpty());

        mockMvc.perform(post("/api/usuarios/personal").cookie(recepcionista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Prueba","apellidos":"Bloqueado","correo":"%s","clave":"test1234","rol":"RECEPCIONISTA"}
                                """.formatted("bloqueado." + UUID.randomUUID() + "@test.local")))
                .andExpect(status().isForbidden());
    }

    @Test
    void losRolesDeRellenoNoPuedenIniciarSesion() throws Exception {
        Cookie admin = cookieDe("admin@email.com", CLAVE_SEED);
        String correo = "prueba.arbitro." + UUID.randomUUID() + "@test.local";

        mockMvc.perform(post("/api/usuarios/personal").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Prueba","apellidos":"Arbitro","correo":"%s","clave":"test1234","rol":"ARBITRO"}
                                """.formatted(correo)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"clave\":\"test1234\"}".formatted(correo)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elClientePagaLaSeniaYElPersonalCobraElSaldoHastaCompletar() throws Exception {
        Cookie laura = cookieDe("laura@email.com", CLAVE_SEED);
        Cookie recepcion = cookieDe("recepcion@email.com", CLAVE_SEED);
        int id = crearReserva(laura);

        String cuerpo = mockMvc.perform(get("/api/reservas/" + id).cookie(laura))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        BigDecimal total = new BigDecimal(mapper.readTree(cuerpo).get("montoTotal").asText());
        BigDecimal senia = total.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldo = total.subtract(senia);

        // La reserva nace PENDIENTE; la seña la confirma.
        mockMvc.perform(post("/api/pagos").cookie(laura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPago":"TARJETA","reservaId":%d,"monto":%s}
                                """.formatted(id, senia)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoPago").value("PARCIAL"));
        mockMvc.perform(get("/api/reservas/" + id).cookie(laura))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReserva").value("CONFIRMADA"));

        // El cliente no puede pagar el saldo: eso se cobra en recepción.
        mockMvc.perform(post("/api/pagos").cookie(laura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPago":"TARJETA","reservaId":%d,"monto":%s}
                                """.formatted(id, saldo)))
                .andExpect(status().isForbidden());

        // Check-in del personal y cobro del saldo: la reserva se completa sola.
        mockMvc.perform(patch("/api/reservas/" + id + "/estado").cookie(recepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoReserva\":\"EN_CURSO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReserva").value("EN_CURSO"));

        mockMvc.perform(post("/api/pagos").cookie(recepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPago":"EFECTIVO","reservaId":%d,"monto":%s}
                                """.formatted(id, saldo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoPago").value("PAGADO"));

        String finalizada = mockMvc.perform(get("/api/reservas/" + id).cookie(recepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReserva").value("COMPLETADA"))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(finalizada).get("saldo").asDouble()).isZero();
    }

    /**
     * Crea una reserva para HOY en la franja libre mas tardia posible: el check-in
     * exige el mismo dia y un horario que todavia no haya terminado.
     */
    private int crearReserva(Cookie sesion) throws Exception {
        // Mismo reloj que el negocio: el check-in exige hoy en la zona del complejo.
        ZoneId zona = ZoneId.of("America/Lima");
        for (int hora = 21; hora >= 8; hora--) {
            LocalDateTime inicio = LocalDate.now(zona).atTime(hora, 0);
            if (!inicio.plusHours(1).isAfter(LocalDateTime.now(zona))) {
                continue;
            }
            MvcResult intento = mockMvc.perform(post("/api/reservas")
                            .cookie(sesion)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fechaHoraInicio":"%s","fechaHoraFin":"%s","canchaId":1}
                                    """.formatted(inicio, inicio.plusHours(1))))
                    .andReturn();
            if (intento.getResponse().getStatus() == 201) {
                return mapper.readTree(intento.getResponse().getContentAsString()).get("id").asInt();
            }
        }
        throw new AssertionError("No hay franja libre hoy para crear la reserva de prueba");
    }

    /** Hace login real y devuelve la cookie de sesion; asi se prueba tambien que el rol sale de la base. */
    private Cookie cookieDe(String correo, String clave) throws Exception {
        Cookie cookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"clave\":\"%s\"}".formatted(correo, clave)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(AuthCookies.NOMBRE);
        assertThat(cookie).as("el login debe setear la cookie %s", AuthCookies.NOMBRE).isNotNull();
        return cookie;
    }
}
