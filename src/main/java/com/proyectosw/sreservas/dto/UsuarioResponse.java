package com.proyectosw.sreservas.dto;

import com.proyectosw.sreservas.model.Usuario;

/**
 * Public view of a user. The {@code clave} hash is intentionally excluded.
 */
public record UsuarioResponse(
        Integer id,
        String nombres,
        String apellidos,
        String correo
) {
    public static UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombres(),
                usuario.getApellidos(),
                usuario.getCorreo()
        );
    }
}
