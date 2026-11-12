-- El rol viaja en el seed: ADMIN para la cuenta administradora, CLIENTE para los clientes
-- y roles de personal para los trabajadores de ejemplo. INSERT IGNORE no pisa las filas que
-- ya existen, asi que un cambio de rol hecho desde la aplicacion no se revierte al reiniciar.
-- Para una base ya creada hay que correr a mano:
--   UPDATE usuario SET rol = 'ADMIN' WHERE correo = 'admin@email.com';
-- La clave de todos los usuarios del seed es test123.
INSERT IGNORE INTO usuario (id, nombres, apellidos, correo, clave, rol) VALUES
  (1, 'Admin', 'Sistema', 'admin@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'ADMIN'),
  (2, 'Laura', 'Gomez', 'laura@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'CLIENTE'),
  (3, 'Pedro', 'Diaz', 'pedro@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'CLIENTE'),
  (101, 'Rocio', 'Recepcion', 'recepcion@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'RECEPCIONISTA'),
  (102, 'Ezequiel', 'Entrenador', 'entrenador@email.com', '$2a$10$QdpRmEnffZjK3RIvTCAKIOA374XrawxGxwPZBN7VsEeGhTc59fDpG', 'ENTRENADOR');

INSERT IGNORE INTO cancha (id, nombre, deporte, precio_por_hora, activa) VALUES
  (1, 'Cancha Futbol 1', 'FUTBOL', 70.00, 1),
  (2, 'Cancha Futsal 1', 'FUTSAL', 75.00, 1),
  (3, 'Cancha Tenis 1', 'TENIS', 90.00, 1),
  (4, 'Cancha Voley 1', 'VOLEY', 60.00, 1);


