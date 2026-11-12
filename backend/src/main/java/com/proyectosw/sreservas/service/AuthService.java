package com.proyectosw.sreservas.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.proyectosw.sreservas.dto.LoginRequest;
import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.repository.UsuarioRepository;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public AuthService(final AuthenticationManager authenticationManager, final JwtService jwtService,
            final UsuarioRepository usuarioRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    /** Valida las credenciales y devuelve el JWT que el controller guarda en la cookie. */
    public String login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.correo(), request.clave()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o clave incorrectos");
        }

        // Los roles de relleno (ENTRENADOR, MANTENIMIENTO, ARBITRO) no tienen pantallas:
        // se les niega el login en vez de dejarlos entrar a un dashboard sin permisos.
        Usuario usuario = usuarioRepository.findByCorreo(request.correo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Correo o clave incorrectos"));
        if (!usuario.getRol().puedeIngresar()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu rol no tiene acceso al sistema");
        }

        return jwtService.generateToken(request.correo());
    }
}
