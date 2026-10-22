# AGENTS.md — sreservas (backend)

> Este backend vive en `backend/` dentro del monorepo `SistemaComplejoDeportivo`.
> **Todos los comandos de esta página se ejecutan desde `backend/`.** El repo git está un nivel
> arriba: `git status` desde acá muestra el monorepo entero. Convenciones generales del
> monorepo en `../AGENTS.md`.

## Build y run

- **El build oficial es Maven.** `mvn` NO está en el PATH de esta máquina: usá el wrapper.
- `.\mvnw.cmd clean compile` — build completo (39 fuentes).
- `.\mvnw.cmd spring-boot:run` — levanta en `http://localhost:8080`.
- `.\mvnw.cmd test` — tests. Requieren MySQL arriba (el test es un `@SpringBootTest`).
- MySQL: `localhost:3306`, user `root`, pass `mysql`, base `db_complejoDeportivo`.
  El esquema y el seed (`src/main/resources/data.sql`) se crean solos.

### Trampa activa en Windows: `clean` con la app corriendo

Con el backend levantado, `.\mvnw.cmd clean` falla con
`Failed to delete target/generated-test-sources/test-annotations`: el JVM bloquea los archivos
de `target/`. Mientras la app esté en ejecución usá **solo** `.\mvnw.cmd compile`.

`spring-boot-devtools` está en el `pom.xml`: al recompilar, la app **se reinicia sola** con las
clases nuevas. No hace falta bajar y volver a levantar para probar un cambio de código.

## Trampa activa: Lombok + JDK 25

El LS de VS Code (`redhat.java`) compila con ECJ usando su Lombok embebido **1.18.39**, que
no soporta JDK 25 (soporte JDK 25 llegó en 1.18.40). Genera constructores sin `super()` →
`VerifyError: Constructor must call super() or this() before return`.

Estado actual:

- `.vscode/settings.json` trae `"java.jdt.ls.javac.enabled": "on"` → el LS compila con javac
  y usa el Lombok 1.18.46 del proyecto. **Requiere *Developer: Reload Window* para aplicar.**
- `java.autobuild.enabled` está en `false` a propósito: el IDE no debe pisar `target/classes`
  en cada guardado.

Si aparece ese `VerifyError`: **no es bug del código**. `.\mvnw.cmd clean compile` y seguí.
Para auditar el bytecode: `javap -c -p -cp target/classes com.proyectosw.sreservas.config.SecurityConfig`
— el constructor debe mostrar `invokespecial java/lang/Object."<init>"`.

## Convenciones

- Idioma del código y los comentarios: español. Nombres de dominio: `Cancha`, `Reserva`,
  `Pago`, `Usuario`, `EstadoReserva`, `TipoDeporte`. Endpoints en inglés/plural
  (`/api/canchas`, `/api/reservas`, `/api/pagos`, `/api/usuarios`).
- **Nunca serializar entidades**: todo sale por DTOs (`dto/`). `Usuario.clave` es el hash
  BCrypt y jamás debe viajar en una respuesta.
- Errores: `@RestControllerAdvice` centralizado + `server.error.include-message=always`.
- CORS: solo `http://localhost:4200` y `http://127.0.0.1:4200` (frontend Angular).
- Sin atributos de IA en comentarios ni commits. Commits en castellano, formato
  `tipo(ámbito): descripción` (`feat`, `fix`, `refactor`, `docs`, `chore`).
- Java 25 disponible en `C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot`.

## Roles y seguridad

- Enum `Rol` (`model/enums/Rol.java`): `ADMIN`, `RECEPCIONISTA`, `CLIENTE`. La columna
  `usuario.rol` es `varchar(20) not null default 'CLIENTE'`.
- El JWT **solo lleva el correo**. `JwtAuthFilter` resuelve el usuario desde la base en cada
  request (`CurrentUserService`), así que los tokens viejos siguen válidos y un cambio de rol
  aplica al instante. No agregar `rol` al token: obligaría a re-loguear a todos.
- `POST /api/usuarios` es público y **fuerza `CLIENTE`** en `UsuarioService.crear`: nunca confiar
  en un rol que venga del cliente.
- `PUT /api/usuarios/{id}/rol` (`UsuarioRolRequest`) es el único camino para cambiar roles y
  exige `ADMIN`. Quitar el rol al último `ADMIN` → `409`.
- Matriz en `SecurityConfig`; permisos a nivel de fila (reserva ajena) en `ReservaService`.
  Un `403` pasa por `AccessDeniedHandler` (envelope JSON), no por el handler por defecto.
- `PUT` y `DELETE /api/reservas/**` aceptan a los tres roles, pero `ReservaService.actualizar` y
  `eliminar` llaman a `verificarPropiedad`: un `CLIENTE` solo toca sus reservas (si no, `403`).
  Nunca mover esa validación solo a `SecurityConfig`: ahí no se conoce el dueño de la fila.
- Una reserva `COMPLETADA` no se reprograma ni se cancela (`409`).
- Los tests de la matriz están en `config/SeguridadPorRolTests` (login real contra el seed).

### Trampa: agregar una columna `NOT NULL` a una tabla con filas

`@Enumerated(STRING)` + `nullable = false` deja `''` en las filas existentes y MySQL rechaza el
`ALTER TABLE`. Por eso `Usuario.rol` usa
`@Column(columnDefinition = "varchar(20) not null default 'CLIENTE'")`: Hibernate agrega la
columna con default y las filas viejas quedan como `CLIENTE`. Si alguna vez hay que migrar una
base existente a mano: `UPDATE usuario SET rol = 'ADMIN' WHERE correo = 'admin@email.com';`.

## Reglas de negocio

- Solapamiento de reservas → `409 Conflict` (`ReservaRepository.existeSolapamiento`).
  `CANCELADA` no bloquea; al actualizar se excluye la propia reserva.
- Monto por **hora iniciada**: `horas = (minutos + 59) / 60`. No usar `Duration.toHours()`.
- `PUT /api/usuarios/{id}` acepta clave opcional (`UsuarioUpdateRequest`): si viene `null`,
  la clave no cambia.
- `POST /api/usuarios` exige clave (`UsuarioRequest`); `PUT` también la acepta y la re-codifica.

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
