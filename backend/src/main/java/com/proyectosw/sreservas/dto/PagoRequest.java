package com.proyectosw.sreservas.dto;

import java.math.BigDecimal;

import com.proyectosw.sreservas.model.Pago;
import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.enums.MetodoPago;

import jakarta.validation.constraints.NotNull;

/**
 * Create/update payload for a payment. References the reservation by id and
 * carries the amount being paid in this step: the service accumulates it over
 * the single payment row of the reservation.
 *
 * <p>{@code estadoPago} and {@code fechaPago} are managed by the service.</p>
 */
public record PagoRequest(
        @NotNull(message = "El método de pago es obligatorio")
        MetodoPago metodoPago,

        @NotNull(message = "El pago debe referenciar una reserva")
        Integer reservaId,

        BigDecimal monto
) {
    public Pago toEntity() {
        Pago pago = new Pago();
        pago.setMetodoPago(metodoPago);
        pago.setMonto(monto);

        Reserva reserva = new Reserva();
        reserva.setId(reservaId);
        pago.setReserva(reserva);

        return pago;
    }
}
