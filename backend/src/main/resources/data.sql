-- Seed de desarrollo y demo. Es idempotente: ON CONFLICT DO NOTHING no pisa las
-- filas que ya existen, asi que un cambio de rol hecho desde la aplicacion no se
-- revierte al reiniciar. La clave de todos los usuarios del seed es test123.
INSERT INTO usuario (id, nombres, apellidos, correo, clave, rol) VALUES
  (1, 'Admin', 'Sistema', 'admin@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'ADMIN'),
  (2, 'Laura', 'Gomez', 'laura@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'CLIENTE'),
  (3, 'Pedro', 'Diaz', 'pedro@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'CLIENTE'),
  (101, 'Rocio', 'Recepcion', 'recepcion@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'RECEPCIONISTA'),
  (102, 'Ezequiel', 'Entrenador', 'entrenador@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'ENTRENADOR')
ON CONFLICT DO NOTHING;

INSERT INTO cancha (id, nombre, deporte, precio_por_hora, activa) VALUES
  (1, 'Cancha Futbol 1', 'FUTBOL', 70.00, true),
  (2, 'Cancha Futsal 1', 'FUTSAL', 75.00, true),
  (3, 'Cancha Tenis 1', 'TENIS', 90.00, true),
  (4, 'Cancha Voley 1', 'VOLEY', 60.00, true)
ON CONFLICT DO NOTHING;

-- Los IDs del seed son fijos: hay que mover la secuencia para que el proximo
-- alta no choque con un ID ya usado.
SELECT setval(pg_get_serial_sequence('usuario', 'id'), COALESCE((SELECT MAX(id) FROM usuario), 1));
SELECT setval(pg_get_serial_sequence('cancha', 'id'), COALESCE((SELECT MAX(id) FROM cancha), 1));
