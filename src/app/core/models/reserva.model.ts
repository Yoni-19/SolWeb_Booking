/** Matches backend EstadoReserva enum (model/enums/EstadoReserva.java). */
export type EstadoReserva = 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA' | 'COMPLETADA';

export const ESTADOS_RESERVA: EstadoReserva[] = ['PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'COMPLETADA'];

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
}
