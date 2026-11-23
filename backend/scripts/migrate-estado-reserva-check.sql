-- Agrega NO_ASISTIO al CHECK constraint de reserva en bases ya creadas.
-- Hibernate genera la restriccion al crear la tabla, pero ddl-auto=update no la
-- modifica: una base creada antes de que el enum sumara NO_ASISTIO rechaza ese
-- estado y rompe el listado (409 "existen registros relacionados").
-- Es idempotente: se puede correr las veces que haga falta.
ALTER TABLE reserva DROP CONSTRAINT IF EXISTS reserva_estado_reserva_check;

ALTER TABLE reserva ADD CONSTRAINT reserva_estado_reserva_check
  CHECK (estado_reserva IN ('PENDIENTE', 'CONFIRMADA', 'EN_CURSO', 'CANCELADA', 'COMPLETADA', 'NO_ASISTIO'));
