-- Migracion para bases creadas antes de los estados EN_CURSO (reserva) y
-- PARCIAL (pago). Hibernate con ddl-auto=update NO modifica columnas enum
-- existentes; en una base nueva no hace falta (se crean con todos los valores).
--
-- Uso: mysql -uroot -p db_complejoDeportivo < scripts/migrate-estados-pago.sql

ALTER TABLE reserva
    MODIFY estado_reserva ENUM('PENDIENTE','CONFIRMADA','EN_CURSO','CANCELADA','COMPLETADA') NOT NULL;

ALTER TABLE pago
    MODIFY estado_pago ENUM('PENDIENTE','PARCIAL','PAGADO','REEMBOLSADO') NOT NULL;
