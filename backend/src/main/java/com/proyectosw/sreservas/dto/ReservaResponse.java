package com.proyectosw.sreservas.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyectosw.sreservas.model.Pago;
import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.enums.EstadoPago;
import com.proyectosw.sreservas.model.enums.EstadoReserva;

/**
 * Reservation view. The nested {@code Usuario} and {@code Cancha} are
 * flattened to id + name to avoid serialization cycles and hash leakage.
 * The payment is 1:1, so its state and the paid/remaining amounts travel here
 * too; that lets the table show "pagado / saldo" without an extra request.
 */
public record ReservaResponse(
        Integer id,
        LocalDateTime fechaHoraInicio,
        LocalDateTime fechaHoraFin,
        EstadoReserva estadoReserva,
        BigDecimal montoTotal,
        Integer canchaId,
        String canchaNombre,
        Integer usuarioId,
        String usuarioNombre,
        EstadoPago estadoPago,
        BigDecimal montoPagado,
        BigDecimal saldo
) {
    public static ReservaResponse toResponse(Reserva reserva) {
        return toResponse(reserva, null);
    }

    public static ReservaResponse toResponse(Reserva reserva, Pago pago) {
        BigDecimal pagado = pago != null ? pago.getMonto() : BigDecimal.ZERO;
        BigDecimal saldo = reserva.getMontoTotal().subtract(pagado);
        if (saldo.signum() < 0) {
            saldo = BigDecimal.ZERO;
        }
        return new ReservaResponse(
                reserva.getId(),
                reserva.getFechaHoraInicio(),
                reserva.getFechaHoraFin(),
                reserva.getEstadoReserva(),
                reserva.getMontoTotal(),
                reserva.getCancha().getId(),
                reserva.getCancha().getNombre(),
                reserva.getUsuario().getId(),
                reserva.getUsuario().getNombres() + " " + reserva.getUsuario().getApellidos(),
                pago != null ? pago.getEstadoPago() : null,
                pagado,
                saldo
        );
    }
}
