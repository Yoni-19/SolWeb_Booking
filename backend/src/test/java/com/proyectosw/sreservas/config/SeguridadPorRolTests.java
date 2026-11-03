package com.proyectosw.sreservas.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;

// Spring Boot 4 usa Jackson 3: el ObjectMapper autoconfigurado vive en tools.jackson.
import tools.jackson.databind.ObjectMapper;

/**
 * Verifica la matriz de permisos de SecurityConfig contra la API real.
 * Usa los usuarios del seed: admin@email.com (ADMIN) y laura@email.com (CLIENTE).
 * Requiere MySQL arriba, igual que el resto de los tests del proyecto.
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
        // La reserva 2 del seed pertenece a otro usuario: reprogramarla da 403, no 400 ni 200.
        mockMvc.perform(put("/api/reservas/2").cookie(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHoraInicio":"2026-11-03T09:00:00","fechaHoraFin":"2026-11-03T10:30:00","canchaId":1}
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
    void elRecepcionistaLeeUsuariosPeroNoCambiaRolesNiGestionaCanchas() throws Exception {
        Cookie admin = cookieDe("admin@email.com", CLAVE_SEED);
        String correo = "prueba.recepcionista." + UUID.randomUUID() + "@test.local";

        String alta = mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Prueba","apellidos":"Recepcionista","correo":"%s","clave":"test123"}
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

            Cookie recepcionista = cookieDe(correo, CLAVE_SEED);

            mockMvc.perform(get("/api/usuarios").cookie(recepcionista))
                    .andExpect(status().isOk());
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
