package com.proyectosw.sreservas.dto;

import java.time.LocalDateTime;

import com.proyectosw.sreservas.model.Reserva;

/**
 * Franja horaria ya ocupada de una cancha. El formulario de reserva la usa para
 * avisar antes de enviar y evitar el 409 de solapamiento.
 */
public record FranjaOcupadaResponse(
        Integer id,
        LocalDateTime inicio,
        LocalDateTime fin
) {
    public static FranjaOcupadaResponse toResponse(Reserva reserva) {
        return new FranjaOcupadaResponse(
                reserva.getId(),
                reserva.getFechaHoraInicio(),
                reserva.getFechaHoraFin()
        );
    }
}
