package com.proyectosw.sreservas.dto;

import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.model.enums.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de POST /api/usuarios/personal (solo ADMIN).
 * A diferencia del registro publico, aca el rol es obligatorio y nunca puede ser CLIENTE.
 */
public record UsuarioPersonalRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 100)
        String nombres,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(min = 2, max = 100)
        String apellidos,

        @NotBlank
        @Email(message = "El correo debe tener un formato válido")
        @Size(max = 100)
        String correo,

        @NotBlank(message = "La clave es obligatoria")
        @Size(min = 2, max = 100)
        String clave,

        @NotNull(message = "El rol es obligatorio")
        Rol rol
) {
    public Usuario toEntity() {
        Usuario usuario = new Usuario();
        usuario.setNombres(nombres);
        usuario.setApellidos(apellidos);
        usuario.setCorreo(correo);
        usuario.setClave(clave);
        return usuario;
    }
}
