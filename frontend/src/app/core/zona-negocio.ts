/**
 * Zona horaria del complejo.
 *
 * La hora de una reserva siempre es la del complejo, no la del visitante: si
 * alguien entra desde otro país, las 16:00 siguen siendo las 16:00 en la cancha.
 * Por eso el reloj de negocio es fijo y no el del navegador.
 */
export const ZONA_NEGOCIO = 'America/Lima';

export interface AhoraEnComplejo {
  /** Fecha "yyyy-MM-dd" en la zona del complejo. */
  fecha: string;
  /** Minutos transcurridos desde la medianoche en la zona del complejo. */
  minutos: number;
}

/** Fecha y hora actuales en la zona del complejo, sin depender del reloj local. */
export function ahoraEnComplejo(ahora: Date = new Date()): AhoraEnComplejo {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: ZONA_NEGOCIO,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(ahora);

  const valor = (tipo: Intl.DateTimeFormatPartTypes): string =>
    partes.find((parte) => parte.type === tipo)?.value ?? '00';

  // Algunos motores devuelven "24" para la medianoche.
  const hora = Number(valor('hour')) % 24;

  return {
    fecha: `${valor('year')}-${valor('month')}-${valor('day')}`,
    minutos: hora * 60 + Number(valor('minute')),
  };
}
