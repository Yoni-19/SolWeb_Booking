package com.proyectosw.sreservas.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyectosw.sreservas.config.AuthCookies;
import com.proyectosw.sreservas.dto.LoginRequest;
import com.proyectosw.sreservas.dto.UsuarioResponse;
import com.proyectosw.sreservas.service.AuthService;
import com.proyectosw.sreservas.service.CurrentUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticacion", description = "Login con JWT en cookie httpOnly y datos de la sesion actual")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final long expirationDays;
    private final boolean secureCookie;

    public AuthController(final AuthService authService,
            final CurrentUserService currentUserService,
            @Value("${application.jwt.token-expiration-after-days}") long expirationDays,
            @Value("${application.auth.cookie.secure:false}") boolean secureCookie) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.expirationDays = expirationDays;
        this.secureCookie = secureCookie;
    }

    /**
     * Valida las credenciales y deja el JWT en una cookie httpOnly.
     * El token no viaja en el cuerpo: JavaScript no puede leerlo ni guardarlo.
     */
    @PostMapping("/login")
    @Operation(summary = "Inicia sesion y guarda el JWT en una cookie httpOnly",
            description = "Responde 204. El navegador guarda la cookie access_token y la envia sola "
                    + "en cada request posterior a /api.")
    @SecurityRequirements
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
        var cookie = AuthCookies.crear(authService.login(request), expirationDays, secureCookie);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    /** Borra la cookie de sesion. Es publico para que el logout funcione aun con la sesion vencida. */
    @PostMapping("/logout")
    @Operation(summary = "Cierra la sesion borrando la cookie",
            description = "Responde 204 con una cookie vencida. No invalida el JWT en el servidor "
                    + "porque el backend es stateless.")
    @SecurityRequirements
    public ResponseEntity<Void> logout() {
        var cookie = AuthCookies.limpiar(secureCookie);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    /** Datos del usuario de la sesion actual: es de donde el frontend saca el rol. */
    @GetMapping("/me")
    @Operation(summary = "Devuelve el usuario autenticado",
            description = "El rol no viaja en el JWT: se lee de la base en cada request.")
    public ResponseEntity<UsuarioResponse> me() {
        return ResponseEntity.ok(UsuarioResponse.toResponse(currentUserService.get()));
    }
}
