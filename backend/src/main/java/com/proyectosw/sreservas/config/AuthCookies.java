package com.proyectosw.sreservas.config;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

/**
 * Construye la cookie que transporta el JWT.
 *
 * <p>{@code HttpOnly} la esconde de JavaScript (un XSS no puede leer el token),
 * {@code SameSite=Strict} evita que el navegador la adjunte a requests cruzados
 * (mitiga CSRF) y {@code Path=/api} evita enviarla al resto del servidor.</p>
 */
public final class AuthCookies {

    public static final String NOMBRE = "access_token";
    public static final String RUTA = "/api";

    private AuthCookies() {
    }

    public static ResponseCookie crear(String token, long dias, boolean secure) {
        return ResponseCookie.from(NOMBRE, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(RUTA)
                .maxAge(Duration.ofDays(dias))
                .build();
    }

    public static ResponseCookie limpiar(boolean secure) {
        return ResponseCookie.from(NOMBRE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(RUTA)
                .maxAge(Duration.ZERO)
                .build();
    }
}
