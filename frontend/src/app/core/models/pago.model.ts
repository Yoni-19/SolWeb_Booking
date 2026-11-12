/** Matches backend MetodoPago enum (model/enums/MetodoPago.java). */
export type MetodoPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA';

/** Matches backend EstadoPago enum. PARCIAL = seña o entrega incompleta. */
export type EstadoPago = 'PENDIENTE' | 'PARCIAL' | 'PAGADO' | 'REEMBOLSADO';

/** Matches backend PagoResponse DTO. */
export interface Pago {
  id: number | null;
  monto: number;
  metodoPago: MetodoPago;
  estadoPago: EstadoPago;
  fechaPago: string;
  reservaId: number | null;
}

/**
 * Payload for POST /api/pagos. `monto` is the amount delivered in this step:
 * the backend accumulates it on the single payment row of the reservation.
 */
export interface PagoAlta {
  metodoPago: MetodoPago;
  reservaId: number;
  monto: number;
}
