# AGENTS.md — sreservas (backend)

> Este backend vive en `backend/` dentro del monorepo `SistemaComplejoDeportivo`.
> **Todos los comandos de esta página se ejecutan desde `backend/`.** El repo git está un nivel
> arriba: `git status` desde acá muestra el monorepo entero. Convenciones generales del
> monorepo en `../AGENTS.md`.

## Build y run

- **El build oficial es Maven.** `mvn` NO está en el PATH de esta máquina: usá el wrapper.
- `.\mvnw.cmd clean compile` — build completo (39 fuentes).
- `.\mvnw.cmd spring-boot:run` — levanta en `http://localhost:8080`.
- `.\mvnw.cmd test` — tests. Requieren PostgreSQL arriba (el test es un `@SpringBootTest`).
- PostgreSQL local: `docker compose up -d` desde la raíz del repo. Queda en
  `localhost:5433`, user `postgres`, pass `postgres`, base `db_complejoDeportivo`.
  El esquema y el seed (`src/main/resources/data.sql`) se crean solos.

### Trampa activa en Windows: `clean` con la app corriendo

Con el backend levantado, `.\mvnw.cmd clean` falla con
`Failed to delete target/generated-test-sources/test-annotations`: el JVM bloquea los archivos
de `target/`. Mientras la app esté en ejecución usá **solo** `.\mvnw.cmd compile`.

`spring-boot-devtools` está en el `pom.xml`: al recompilar, la app **se reinicia sola** con las
clases nuevas. No hace falta bajar y volver a levantar para probar un cambio de código.

## Sin Lombok (desde 2026-10-24)

El proyecto **no usa Lombok**. Las 4 entidades tienen getters, setters y constructor vacío
explícitos; las 14 clases de `config/`, `controllers/` y `service/` tienen constructor propio
en lugar de `@RequiredArgsConstructor`. Al agregar un campo `final` nuevo en cualquiera de esas
clases hay que **agregarlo también al constructor**.

Motivo: el LS de VS Code no puede combinar Lombok con JDK 25 (su Lombok embebido es 1.18.39,
sin soporte de JDK 25) y el modo experimental `java.jdt.ls.javac.enabled: "on"` rompía al leer
AssertJ. Sin Lombok se usa el modo normal del LS.

Estado de `.vscode/settings.json` (raíz y `backend/`):

- `"java.jdt.ls.javac.enabled": "off"` — modo normal. **Requiere *Developer: Reload Window*.**
- `"java.autobuild.enabled": true` — el IDE compila al abrir/guardar para que el botón Run
  encuentre las clases en `target/classes` también en un clon limpio (donde `target/` no
  existe porque está gitignoreado).

## Trampa activa: el IDE compila en `target/classes` sin `-parameters`

El LS escribe en el mismo `target/classes` que Maven. Ese bytecode puede quedar sin la bandera
`-parameters`, y Spring entonces no resuelve `@PathVariable Integer id` sin nombre explícito:

```
IllegalArgumentException: Name for argument of type [java.lang.Integer] not specified
```

El síntoma es un `500 Internal Server Error` en cualquier `PUT`/`DELETE` con id de ruta,
aunque todo compile. Si aparece después de que el IDE recompiló: borrá `target/` y corré
`.\mvnw.cmd clean compile` (o `.\mvnw.cmd test`, que recompila con `[debug parameters release 25]`).

Con autobuild activo, evitá `.\mvnw.cmd clean` mientras VS Code compila (o cerrá la ventana):
los dos procesos se pelean por `target/`. Si `clean` falla por archivos bloqueados, borrá
`target/` a mano y volvé a compilar.

## Convenciones

- Idioma del código y los comentarios: español. Nombres de dominio: `Cancha`, `Reserva`,
  `Pago`, `Usuario`, `EstadoReserva`, `TipoDeporte`. Endpoints en inglés/plural
  (`/api/canchas`, `/api/reservas`, `/api/pagos`, `/api/usuarios`).
- **Nunca serializar entidades**: todo sale por DTOs (`dto/`). `Usuario.clave` es el hash
  BCrypt y jamás debe viajar en una respuesta.
- Errores: `@RestControllerAdvice` centralizado + `server.error.include-message=always`.
- CORS: configurable con `CORS_ALLOWED_ORIGINS`; por defecto `http://localhost:4200` y
  `http://127.0.0.1:4200`. En producción el proxy de Vercel evita CORS porque hay un solo origen.
- Sin atributos de IA en comentarios ni commits. Commits en castellano, formato
  `tipo(ámbito): descripción` (`feat`, `fix`, `refactor`, `docs`, `chore`).
- Java 25 disponible en `C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot`.

## Roles y seguridad

- Enum `Rol` (`model/enums/Rol.java`): `ADMIN`, `RECEPCIONISTA`, `CLIENTE`, `ENTRENADOR`,
  `MANTENIMIENTO`, `ARBITRO`. La columna `usuario.rol` es `varchar(20) not null default 'CLIENTE'`.
  Los tres últimos son informativos: `Rol.puedeIngresar()` los excluye y `AuthService.login`
  responde `403` ("Tu rol no tiene acceso al sistema"). No tienen pantallas ni permisos.
- El JWT **solo lleva el correo**. `JwtAuthFilter` resuelve el usuario desde la base en cada
  request (`CurrentUserService`), así que los tokens viejos siguen válidos y un cambio de rol
  aplica al instante. No agregar `rol` al token: obligaría a re-loguear a todos.
- El JWT viaja en la cookie **`access_token`** (`HttpOnly`, `SameSite=Strict`, `Path=/api`) que
  setea `POST /api/auth/login` y borra `POST /api/auth/logout`. `AuthCookies` centraliza los
  atributos; `COOKIE_SECURE=true` en producción. El filtro prioriza la cookie y acepta
  `Authorization: Bearer` como alternativa (Swagger/Postman). El frontend **no** guarda el token.
- No hay invalidación server-side del JWT: el logout solo borra la cookie y el token sigue
  válido hasta expirar. Si se pide revocación real, hace falta lista negra o refresh rotativo.
- `POST /api/usuarios` es público y **fuerza `CLIENTE`** en `UsuarioService.crear`: nunca confiar
  en un rol que venga del cliente. El alta de trabajadores va por
  `POST /api/usuarios/personal` (solo `ADMIN`), que exige un rol de personal (nunca `CLIENTE`).
- `PUT /api/usuarios/{id}/rol` (`UsuarioRolRequest`) es el único camino para cambiar roles y
  exige `ADMIN`. Quitar el rol al último `ADMIN` → `409`.
- `GET /api/usuarios` se filtra en el backend según quién consulta: `RECEPCIONISTA` recibe solo
  `CLIENTE` (no se ve a sí mismo ni al staff) y ni `?rol=` ni `?personal=true` pueden ampliar esa
  vista; `ADMIN` ve todos (salvo a sí mismo), puede filtrar con `?rol=` y pedir solo personal con
  `?personal=true` (todo lo que no es `CLIENTE`). `?q=` busca por nombres, apellidos o correo
  respetando ese mismo alcance. `GET /api/usuarios/{id}` aplica la misma regla.
  El recepcionista puede crear clientes en `/clientes/nueva`; editar, eliminar, cambiar roles y
  gestionar personal sigue siendo exclusivo del `ADMIN`.
- Matriz en `SecurityConfig`; permisos a nivel de fila (reserva ajena) en `ReservaService`.
  Un `403` pasa por `AccessDeniedHandler` (envelope JSON), no por el handler por defecto.
- `PUT`, `DELETE` y `PATCH /api/reservas/**` aceptan a los tres roles, pero
  `ReservaService.actualizar`, `eliminar` y `cambiarEstado` llaman a `verificarPropiedad`: un
  `CLIENTE` solo toca sus reservas (si no, `403`). En `cambiarEstado` el `CLIENTE` solo puede
  pasar a `CANCELADA`; check-in (`CONFIRMADA → EN_CURSO`) y check-out (`EN_CURSO → COMPLETADA`)
  quedan para el personal. Nunca mover esa validación solo a `SecurityConfig`: ahí no se conoce
  el dueño de la fila.
- `POST /api/pagos` acepta a los tres roles: `PagoService` deja que el `CLIENTE` pague solo la
  seña del 50% o el total de una reserva propia y sin pago previo. `GET/PUT/DELETE /api/pagos/**`
  es solo `ADMIN`/`RECEPCIONISTA`.
- La interfaz cancela con `PATCH /api/reservas/{id}/estado` (baja lógica). El `DELETE` sigue
  existiendo para limpieza vía Swagger y no se expone en la UI.
- El listado de un `CLIENTE` oculta las reservas `CANCELADA`; el personal las sigue viendo
  atenuadas.
- Solo se reprograman o cancelan reservas `PENDIENTE` o `CONFIRMADA`; `EN_CURSO` y `COMPLETADA`
  responden `409`.
- Los tests de la matriz están en `config/SeguridadPorRolTests` (login real contra el seed).

### Trampa: agregar una columna `NOT NULL` a una tabla con filas

`@Enumerated(STRING)` + `nullable = false` deja `''` en las filas existentes y PostgreSQL rechaza el
`ALTER TABLE`. Por eso `Usuario.rol` usa
`@Column(columnDefinition = "varchar(20) not null default 'CLIENTE'")`: Hibernate agrega la
columna con default y las filas viejas quedan como `CLIENTE`. Si alguna vez hay que migrar una
base existente a mano: `UPDATE usuario SET rol = 'ADMIN' WHERE correo = 'admin@email.com';`.

## Reglas de negocio

- Solapamiento de reservas → `409 Conflict` (`ReservaRepository.existeSolapamiento`).
  `CANCELADA` no bloquea; al actualizar se excluye la propia reserva.
- `GET /api/reservas/disponibilidad?canchaId=&fecha=yyyy-MM-dd` devuelve las franjas ocupadas de
  esa cancha y día (mismos estados que bloquean, sin `CANCELADA`). La usan los tres roles para
  que el formulario avise el choque antes del `409`.
- Monto por **hora iniciada**: `horas = (minutos + 59) / 60`. No usar `Duration.toHours()`.
- `PUT /api/usuarios/{id}` acepta clave opcional (`UsuarioUpdateRequest`): si viene `null`,
  la clave no cambia.
- `POST /api/usuarios` exige clave (`UsuarioRequest`); `PUT` también la acepta y la re-codifica.
- **Ciclo de vida de la reserva**: nace `PENDIENTE`; registrar la seña la confirma
  (`CONFIRMADA`); el check-in la pasa a `EN_CURSO`; el check-out o cobrar el saldo estando
  `EN_CURSO` la completan. `CONFIRMADA` no se asigna a mano por `PATCH`.
- **Pago 1:1**: un solo `Pago` por reserva (`unique reserva_id`), el monto se acumula. La
  primera entrega debe cubrir `seniaMinima` (50% redondeado a 2 decimales) y nunca se puede
  superar `montoTotal`. `EstadoPago` queda `PARCIAL` o `PAGADO`.
- **Check-in/out**: iniciar exige pago registrado (`409` "Registrá la seña antes de iniciar la
  reserva"); completar exige saldo cero (`409` "Falta cobrar el saldo..."). El pago del saldo
  en `EN_CURSO` completa la reserva automáticamente.
- **Reprogramar con pago**: se recalcula el total; si lo abonado supera el nuevo total, `409`;
  si no, se ajusta `estadoPago` a `PARCIAL`/`PAGADO`.

### Enums en PostgreSQL

En PostgreSQL los `@Enumerated(STRING)` se guardan como `varchar`, no como `ENUM` nativo.
Hibernate **sí** genera un `CHECK` constraint con la lista de valores al crear la tabla, y
`ddl-auto=update` **no lo modifica**. Por eso agregar un valor nuevo al enum de Java en una base
ya creada requiere actualizar el constraint a mano; si no, el `UPDATE`/`INSERT` falla con un
`409` engañoso ("existen registros relacionados"). Ejemplo:
`scripts/migrate-estado-reserva-check.sql`. En una base nueva el constraint se crea con todos los
valores actuales. El viejo script MySQL `scripts/migrate-estados-pago.sql` ya no existe ni hace
falta.

## Contratos que el frontend NO puede cambiar

- `POST /api/usuarios` y `PUT /api/usuarios/{id}` devuelven `409` si el correo ya existe.
- `POST /api/usuarios` crea siempre un `CLIENTE`; el rol solo cambia por
  `PUT /api/usuarios/{id}/rol` y solo un `ADMIN` puede hacerlo.
- `DELETE /api/usuarios/{id}` y `DELETE /api/canchas/{id}` devuelven `409` si el registro tiene
  reservas o pagos asociados (`existsByUsuario_Id` / `existsByCancha_Id`).
- `PUT /api/reservas/{id}` **ignora el `usuarioId`**: el cliente de una reserva existente no se
  puede cambiar. `estadoReserva` tampoco se puede enviar: el backend lo calcula.
- Cualquier violación de FK que se escape a la base devuelve `409` (handler de
  `DataIntegrityViolationException`), nunca `500`.
