import type { EstadoPago } from './pago.model';

/** Matches backend EstadoReserva enum (model/enums/EstadoReserva.java). */
export type EstadoReserva = 'PENDIENTE' | 'CONFIRMADA' | 'EN_CURSO' | 'CANCELADA' | 'COMPLETADA';

export const ESTADOS_RESERVA: EstadoReserva[] = [
  'PENDIENTE',
  'CONFIRMADA',
  'EN_CURSO',
  'CANCELADA',
  'COMPLETADA',
];

/** Matches backend ReservaResponse DTO (nested entities flattened to id + name). */
export interface Reserva {
  id: number | null;
  fechaHoraInicio: string;
  fechaHoraFin: string;
  estadoReserva: EstadoReserva;
  montoTotal: number;
  canchaId: number | null;
  canchaNombre?: string;
  usuarioId: number | null;
  usuarioNombre?: string;
  /** 1:1 payment summary; null until the first payment is registered. */
  estadoPago: EstadoPago | null;
  montoPagado: number;
  saldo: number;
}

/**
 * Payload for POST/PUT /api/reservas (backend ReservaRequest DTO).
 * `estadoReserva` and `montoTotal` are absent on purpose: the backend
 * computes the state and the amount by hour started. `usuarioId` is optional:
 * a CLIENTE books for itself and the backend overwrites it.
 */
export interface ReservaAlta {
  fechaHoraInicio: string;
  fechaHoraFin: string;
  canchaId: number;
  usuarioId?: number;
}
