package com.proyectosw.sreservas.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.proyectosw.sreservas.config.AuthCookies;

import jakarta.servlet.http.Cookie;

/**
 * Verifica el contrato paginado y la documentacion OpenAPI contra la API real.
 *
 * <p>La forma esperada es la estable de Spring Data: {@code content} mas un objeto
 * {@code page} con {@code size}, {@code number}, {@code totalElements} y
 * {@code totalPages} (ver {@code WebConfig}).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaginacionApiTests {

    private static final String CLAVE_SEED = "test123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elListadoDeReservasDevuelveMetadatosDePaginacion() throws Exception {
        Cookie sesion = cookieDe("rocio@email.com");
        // La base de demo puede estar vacia: la pagina necesita al menos una fila.
        crearReserva(sesion, 1);
        crearReserva(sesion, 2);

        mockMvc.perform(get("/api/reservas?page=0&size=2").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$.page.totalPages").isNumber());
    }

    @Test
    void elListadoDeUsuariosDevuelveMetadatosDePaginacion() throws Exception {
        Cookie sesion = cookieDe("admin@email.com");

        mockMvc.perform(get("/api/usuarios?page=0&size=2").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").isNumber());
    }

    @Test
    void elFiltroPorEstadoSoloDevuelveEseEstado() throws Exception {
        Cookie sesion = cookieDe("admin@email.com");

        mockMvc.perform(get("/api/reservas?estado=CANCELADA&size=100")
                        .cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.estadoReserva != 'CANCELADA')]").isEmpty());
    }

    @Test
    void elClienteSoloRecibeSusPropiasReservasPaginadas() throws Exception {
        Cookie sesion = cookieDe("laura@email.com");

        // Laura es la usuaria 2 del seed: ninguna fila de su pagina puede ser de otro.
        mockMvc.perform(get("/api/reservas?page=0&size=100").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.usuarioId != 2)]").isEmpty());
    }

    @Test
    void laDisponibilidadDevuelveLaFranjaReservada() throws Exception {
        Cookie sesion = cookieDe("rocio@email.com");
        // Fecha lejana y hora fija: no choca con el resto de los tests ni con el seed.
        LocalDateTime inicio = LocalDateTime.now().plusYears(7)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        mockMvc.perform(post("/api/reservas")
                        .cookie(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHoraInicio":"%s","fechaHoraFin":"%s","canchaId":1,"usuarioId":2}
                                """.formatted(inicio, inicio.plusHours(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reservas/disponibilidad")
                        .param("canchaId", "1")
                        .param("fecha", inicio.toLocalDate().toString())
                        .cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.inicio == '%s')]".formatted(inicio + ":00")).isNotEmpty());
    }

    @Test
    void laBusquedaDeUsuariosFiltraPorCorreo() throws Exception {
        Cookie sesion = cookieDe("admin@email.com");

        mockMvc.perform(get("/api/usuarios").param("q", "laura@email.com").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].correo").value("laura@email.com"));
    }

    @Test
    void elContratoOpenApiEsPublicoYDeclaraLosEsquemasDeAutenticacion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/reservas']").exists())
                .andExpect(jsonPath("$.paths['/api/usuarios']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    /** La recepcion reserva para Laura (id 2 del seed) en una fecha futura y sin solaparse. */
    private void crearReserva(Cookie sesion, int dias) throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusYears(6).plusDays(dias)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        mockMvc.perform(post("/api/reservas")
                        .cookie(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHoraInicio":"%s","fechaHoraFin":"%s","canchaId":1,"usuarioId":2}
                                """.formatted(inicio, inicio.plusHours(1))))
                .andExpect(status().isCreated());
    }

    /** Login real: devuelve la cookie httpOnly con la que se autentican las requests. */
    private Cookie cookieDe(String correo) throws Exception {
        Cookie cookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"clave\":\"%s\"}".formatted(correo, CLAVE_SEED)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(AuthCookies.NOMBRE);
        assertThat(cookie).as("el login debe setear la cookie %s", AuthCookies.NOMBRE).isNotNull();
        return cookie;
    }
}
