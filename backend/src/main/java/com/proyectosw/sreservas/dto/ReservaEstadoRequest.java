package com.proyectosw.sreservas.dto;

import com.proyectosw.sreservas.model.enums.EstadoReserva;

import jakarta.validation.constraints.NotNull;

/**
 * Payload for PATCH /api/reservas/{id}/estado. Only the target state is sent;
 * the service decides whether the transition is allowed.
 */
public record ReservaEstadoRequest(
        @NotNull(message = "El estado es obligatorio")
        EstadoReserva estadoReserva
) {
}
