package com.proyectosw.sreservas.dto;

import java.time.LocalDateTime;

import com.proyectosw.sreservas.model.Cancha;
import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.Usuario;

import jakarta.validation.constraints.NotNull;

/**
 * Create/update payload for a reservation. References {@code usuario} and
 * {@code cancha} by id only; {@code estadoReserva} and {@code montoTotal}
 * are computed by the service and therefore excluded.
 */
public record ReservaRequest(
        @NotNull(message = "La fecha y hora de inicio es obligatoria")
        LocalDateTime fechaHoraInicio,

        @NotNull(message = "La fecha y hora de fin es obligatoria")
        LocalDateTime fechaHoraFin,

        @NotNull(message = "La reserva debe indicar una cancha")
        Integer canchaId,

        @NotNull(message = "La reserva debe indicar un usuario")
        Integer usuarioId
) {
    public Reserva toEntity() {
        Reserva reserva = new Reserva();
        reserva.setFechaHoraInicio(fechaHoraInicio);
        reserva.setFechaHoraFin(fechaHoraFin);

        Cancha cancha = new Cancha();
        cancha.setId(canchaId);
        reserva.setCancha(cancha);

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        reserva.setUsuario(usuario);

        return reserva;
    }
}
