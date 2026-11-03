package com.proyectosw.sreservas.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.proyectosw.sreservas.config.AuthCookies;

import jakarta.servlet.http.Cookie;

/**
 * Verifica el contrato de autenticacion por cookie httpOnly.
 *
 * <p>Cubre lo que pide la rubrica: el token no se expone a JavaScript, no viaja
 * en el cuerpo, la cookie autentica y el logout la borra.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionCookieTests {

    private static final String CLAVE_SEED = "test123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elLoginDevuelveUnaCookieHttpOnlySinExponerElToken() throws Exception {
        Cookie cookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"admin@email.com\",\"clave\":\"" + CLAVE_SEED + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api")))
                .andReturn().getResponse().getCookie(AuthCookies.NOMBRE);

        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getValue()).isNotBlank();
    }

    @Test
    void laCookieAutenticaLasRutasProtegidas() throws Exception {
        Cookie sesion = login("laura@email.com");

        mockMvc.perform(get("/api/auth/me").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("laura@email.com"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void elLogoutBorraLaCookieDeSesion() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString(AuthCookies.NOMBRE + "=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void conCredencialesInvalidasNoSeSeteaCookie() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"admin@email.com\",\"clave\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    private Cookie login(String correo) throws Exception {
        Cookie cookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"clave\":\"%s\"}".formatted(correo, CLAVE_SEED)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(AuthCookies.NOMBRE);
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
