# sreservas — Backend

API REST del Sistema de Reservas de un complejo deportivo. Spring Boot sobre PostgreSQL, con JWT
transportado en una cookie `httpOnly`.

## Stack

| Elemento | Versión |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.1 |
| Spring Security + JWT (jjwt) | 6.x / 0.12.6 |
| JPA / Hibernate | 7.4.5 |
| PostgreSQL | 17 |

Sin Lombok: getters, setters y constructores se escriben explícitos.

## Requisitos

- JDK 25
- Docker Desktop (para PostgreSQL local)
- Maven no hace falta instalado: el proyecto incluye el wrapper (`mvnw.cmd`)

## Cómo correr

```powershell
# Desde la raíz del repo
docker compose up -d          # PostgreSQL en localhost:5433

cd backend
.\mvnw.cmd clean compile      # build oficial
.\mvnw.cmd spring-boot:run    # levanta en http://localhost:8080
.\mvnw.cmd test               # tests (requiere PostgreSQL arriba)
```

### En VS Code

1. Abrí el workspace **raíz** (`SistemaComplejoDeportivo`), no solo `backend/`.
2. Esperá a que la extensión de Java termine de compilar (barra de estado "Java: Ready").
3. Abrí `SreservasApplication.java` y usá el botón **Run**.
4. Alternativa a prueba de balas: `Terminal > Run Task > backend: run`, o el comando Maven
   de arriba.

La base se inicializa sola: `spring.jpa.hibernate.ddl-auto=update` crea el esquema y
`src/main/resources/data.sql` inserta los datos iniciales de forma idempotente
(`ON CONFLICT DO NOTHING` + `setval`), así que se puede volver a correr sin duplicar nada.

## Credenciales de prueba

Las cuentas del seed usan la clave `test123` y sus roles salen de `data.sql`:

| Correo | Rol |
|---|---|
| `admin@email.com` | `ADMIN` — Admin Sistema |
| `laura@email.com` | `CLIENTE` — Laura Gomez |
| `pedro@email.com` | `CLIENTE` — Pedro Diaz |
| `recepcion@email.com` | `RECEPCIONISTA` — Rocio Recepcion |
| `entrenador@email.com` | `ENTRENADOR` — Ezequiel Entrenador (rol informativo, no puede iniciar sesión) |

```powershell
# El login responde 204 y deja el JWT en la cookie access_token.
curl -i -c cookies.txt -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"correo":"admin@email.com","clave":"test123"}'

# Reusa la cookie guardada
curl -b cookies.txt http://localhost:8080/api/auth/me
```

## Endpoints

Todos los endpoints de `/api` salvo `POST /api/auth/login`, `POST /api/auth/logout` y
`POST /api/usuarios` (registro) exigen la cookie `access_token`. Swagger (`/swagger-ui/**` y
`/v3/api-docs/**`) es público. El header `Authorization: Bearer <token>` sigue aceptándose
como alternativa para Postman y scripts.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/login` | Valida credenciales y setea la cookie (204) |
| POST | `/api/auth/logout` | Borra la cookie (204) |
| GET | `/api/auth/me` | Usuario autenticado con su `rol` |
| GET/POST | `/api/usuarios` | Listar (paginado, con `?rol=` o `?personal=true`) / registrar cliente (siempre nace `CLIENTE`) |
| POST | `/api/usuarios/personal` | Alta de personal con rol (solo `ADMIN`, nunca `CLIENTE`) |
| GET/PUT/DELETE | `/api/usuarios/{id}` | Detalle / actualizar (clave opcional) / eliminar |
| PUT | `/api/usuarios/{id}/rol` | Cambiar el rol (solo `ADMIN`) |
| GET/POST | `/api/canchas` | Listar / crear cancha |
| GET/PUT/DELETE | `/api/canchas/{id}` | Detalle / actualizar / eliminar |
| GET/POST | `/api/reservas` | Listar (paginado) / crear reserva |
| GET/PUT | `/api/reservas/{id}` | Detalle / reprogramar |
| PATCH | `/api/reservas/{id}/estado` | Check-in (`CONFIRMADA → EN_CURSO`), check-out (`EN_CURSO → COMPLETADA`) o cancelar |
| DELETE | `/api/reservas/{id}` | Borrado físico, solo para limpieza por Swagger (la UI no lo usa) |
| GET/POST | `/api/pagos` | Listar (staff) / registrar seña o saldo |
| GET/PUT/DELETE | `/api/pagos/{id}` | Detalle / actualizar método / eliminar (solo staff) |

## Documentación OpenAPI (Swagger)

`springdoc-openapi` escanea los controllers en runtime y publica el contrato:

- `http://localhost:8080/swagger-ui.html` — UI interactiva para probar los endpoints.
- `http://localhost:8080/v3/api-docs` — contrato OpenAPI en JSON.

Las dos rutas son públicas (`SecurityConfig`). Como Swagger corre en el mismo host que la API,
alcanza con ejecutar `POST /api/auth/login` desde la UI: el navegador guarda la cookie y los
endpoints protegidos la envían solos. El botón **Authorize** con `bearerAuth` queda para probar
con un token pegado a mano.

La dependencia es la línea **3.x** de springdoc: la 2.x no arranca en Spring Boot 4.

## Autenticación por cookie `httpOnly`

`POST /api/auth/login` responde `204` y setea:

```
Set-Cookie: access_token=<jwt>; Path=/api; Max-Age=604800; HttpOnly; SameSite=Strict
```

| Atributo | Por qué |
|---|---|
| `HttpOnly` | JavaScript no puede leer el token: un XSS no se lo lleva |
| `SameSite=Strict` | El navegador no la adjunta a requests iniciados desde otro sitio (mitiga CSRF) |
| `Path=/api` | Solo viaja a la API, no a Swagger ni a otros recursos |
| `Secure` | Se activa con `COOKIE_SECURE=true` sobre HTTPS; en `http://localhost` va apagado |

Consecuencias de diseño:

- **No hay token en el cuerpo de la respuesta.** El frontend no guarda nada: la sesión se
  reconstruye con `GET /api/auth/me`, que es de donde sale el rol.
- **El logout borra la cookie, no el JWT.** El backend es stateless: el token sigue siendo
  válido hasta que expira (7 días). Invalidarlo del lado del servidor exigiría una lista negra
  o refresh tokens rotativos.
- **CSRF.** `SameSite=Strict` más una API que solo acepta JSON bloquea el CSRF clásico por
  formularios. Si el frontend y la API quedaran en sitios distintos, habría que pasar a
  `SameSite=None; Secure` y agregar un token CSRF.
- **Mismo host en desarrollo.** Usá `localhost` para el frontend y la API, o `127.0.0.1` para
  ambos. Mezclarlos (`localhost:4200` → `127.0.0.1:8080`) hace que el navegador los trate como
  sitios distintos y no mande la cookie.
- **CORS con credenciales.** `SecurityConfig` habilita `allowCredentials(true)` y lee los
  orígenes de `CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:4200` y
  `http://127.0.0.1:4200`); con `*` el navegador rechazaría la cookie. En producción el proxy
  de Vercel hace que todo sea mismo origen y CORS no se dispare.

## Paginación

`GET /api/usuarios` y `GET /api/reservas` devuelven una página, no un array:

| Query param | Default | Descripción |
|---|---|---|
| `page` | `0` | Número de página (base 0) |
| `size` | `10` | Filas por página |
| `sort` | `id,desc` | Campo y dirección (`campo,asc` o `campo,desc`) |
| `estado` | — | Solo en reservas: `PENDIENTE`, `CONFIRMADA`, `EN_CURSO`, `CANCELADA`, `COMPLETADA` |

La forma la fija `config/WebConfig` con `pageSerializationMode = VIA_DTO`:

```json
{ "content": [ ... ], "page": { "size": 10, "number": 0, "totalElements": 137, "totalPages": 14 } }
```

`canchas` y `pagos` siguen devolviendo arrays: son catálogos chicos y no se paginan.

## Roles y permisos

El JWT solo lleva el correo: `JwtAuthFilter` carga el usuario en cada request, así que **el rol
nunca viaja en el token** y un cambio de rol aplica al instante. El frontend lo obtiene con
`GET /api/auth/me`.

| Acción | ADMIN | RECEPCIONISTA | CLIENTE |
|---|---|---|---|
| Ver/crear/editar/eliminar canchas | sí | no | no |
| Ver clientes | sí | sí (solo `CLIENTE`) | no |
| Crear clientes | sí | sí | no (se registra solo) |
| Ver/crear/editar/eliminar personal y cambiar roles | sí | no | no |
| Ver todas las reservas | sí | sí | solo las propias |
| Crear reserva | sí | sí (para cualquier cliente) | sí (a su nombre) |
| Reprogramar o cancelar reserva | sí | sí | solo las propias y `PENDIENTE`/`CONFIRMADA` |
| Check-in / check-out | sí | sí | no |
| Registrar pagos | sí (monto libre) | sí (monto libre) | solo seña 50% o total de sus reservas |

El enum `Rol` también acepta `ENTRENADOR`, `MANTENIMIENTO` y `ARBITRO`: existen solo para
clasificar al personal, no tienen pantallas ni permisos y el login responde `403`
("Tu rol no tiene acceso al sistema"). El alta de esos trabajadores va por
`POST /api/usuarios/personal`; el registro público sigue creando siempre `CLIENTE`.

La interfaz **cancela** reservas con `PATCH /api/reservas/{id}/estado`: el `DELETE` existe solo
para limpieza por Swagger y no se expone en la UI. Un `CLIENTE` deja de ver sus reservas
`CANCELADA`; el personal las sigue viendo atenuadas. Además, `GET /api/usuarios` y
`GET /api/usuarios/{id}` ocultan el propio usuario al `ADMIN` y nunca le muestran staff a un
`RECEPCIONISTA`.

Un `CLIENTE` puede reprogramar o cancelar **sus** reservas: el `403` por reserva ajena lo aplica
`ReservaService.verificarPropiedad`. Solo se reprograman o cancelan reservas `PENDIENTE` o
`CONFIRMADA`; `EN_CURSO` y `COMPLETADA` responden `409`.

Un rol sin permiso recibe `403` con el envelope JSON `{ timestamp, status, error, message }`.

Reglas de negocio implementadas:

- **Solapamiento de reservas**: `409 Conflict` si se superpone con otra reserva de la misma
  cancha. Una reserva `CANCELADA` no bloquea.
- **Monto**: se cobra por hora iniciada (`ceil` de los minutos / 60), no por hora exacta.
- **Contratos**: las respuestas usan DTOs (`dto/`), nunca las entidades. El hash BCrypt de
  la clave nunca sale por la API.
- **Último administrador**: no se puede quitar el rol al último `ADMIN` (`409`).
- **CORS**: configurable con `CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:4200` y
  `http://127.0.0.1:4200`).
- **Ciclo de vida**: la reserva nace `PENDIENTE`; registrar la seña la pasa a `CONFIRMADA`;
  el check-in del personal la pasa a `EN_CURSO`; el check-out o el pago del saldo estando
  `EN_CURSO` la completan. Cancelar solo aplica a `PENDIENTE`/`CONFIRMADA`.
- **Pagos**: hay un solo pago por reserva (relación 1:1 `unique reserva_id`) y el monto se
  acumula. La primera entrega debe cubrir al menos el 50% (`seniaMinima`); nunca se puede
  superar el total. `EstadoPago` queda `PARCIAL` o `PAGADO` según lo acumulado.
- **Check-in/check-out**: iniciar exige un pago registrado (`409` sin seña) y completar exige
  saldo cero (`409` con saldo pendiente). Si el staff cobra el saldo estando `EN_CURSO`, la
  reserva se completa sola.
- **Reprogramación con pago**: se recalcula el total; si lo ya abonado supera el nuevo total
  devuelve `409`, si no, se actualiza `estadoPago` a `PARCIAL` o `PAGADO`.

### Enums en PostgreSQL

En PostgreSQL los `@Enumerated(STRING)` se guardan como `varchar`, no como `ENUM` nativo: agregar
un valor nuevo al enum de Java no requiere migración de base. El viejo script MySQL
`scripts/migrate-estados-pago.sql` ya no existe ni hace falta.

## Despliegue (Render + Supabase + Vercel)

La arquitectura es: Angular en Vercel → proxy `/api` → Spring Boot en Render → PostgreSQL en
Supabase. El proxy hace que el navegador vea un solo origen, así que la cookie `SameSite=Strict`
viaja sin problemas.

### Supabase

1. Crear el proyecto y guardar la contraseña.
2. En **Connect → Session pooler** copiar host, puerto `5432` y usuario
   `postgres.[PROJECT-REF]`.
3. Usar el Session Pooler (IPv4): Render no tiene salida IPv6 y el transaction pooler (`6543`)
   no soporta bien los prepared statements de Hibernate.
4. La URL lleva `?sslmode=require`.

El plan free tiene 500 MB, se pausa a los 7 días sin actividad y no incluye backups
automáticos: despertalo antes de la demo y exportá un dump.

### Render

- Root Directory: `backend`.
- Runtime: Docker (`backend/Dockerfile`).
- Variables de entorno:

```text
DB_URL=jdbc:postgresql://aws-[INDEX]-[REGION].pooler.supabase.com:5432/postgres?sslmode=require
DB_USER=postgres.[PROJECT-REF]
DB_PASSWORD=<password>
JWT_SECRET=<secreto-largo>
COOKIE_SECURE=true
CORS_ALLOWED_ORIGINS=https://<app>.vercel.app
DDL_AUTO=update
SQL_INIT_MODE=always
DB_POOL_SIZE=5
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -XX:+UseSerialGC
```

`PORT` lo inyecta Render; no lo setees. El seed corre en cada arranque y es idempotente.

### Vercel

- Root Directory: `frontend`.
- `frontend/vercel.json` proxea `/api/:path*` a Render y hace fallback de SPA a `/index.html`.
- La URL real del backend es `https://complejo-api-xcb4.onrender.com`; si el servicio de Render
  se recrea, actualizar `destination` en `vercel.json`.
- `environment.prod.ts` define `apiBase: ''`, así el frontend pega relativo a `/api`.

### Orden de despliegue

1. `docker compose up -d` y probar login local.
2. Deploy del backend en Render y revisar logs de esquema/seed.
3. Deploy del frontend en Vercel.
4. Probar login, `GET /api/auth/me`, recargar una ruta protegida y logout.

## Gotcha importante: el IDE escribe en `target/classes`

La extensión *Language Support for Java* de VS Code compila en segundo plano y escribe en el
mismo `target/classes` que Maven. Ese bytecode puede quedar sin la bandera `-parameters`, y
entonces Spring no puede resolver un `@PathVariable Integer id` sin nombre explícito:

```
IllegalArgumentException: Name for argument of type [java.lang.Integer] not specified
```

El síntoma es un `500 Internal Server Error` en cualquier `PUT`/`DELETE` con id de ruta,
aunque todo compile y los tests del servicio pasen.

**Solución.** El build oficial es Maven: borrá `target/` y corré

```powershell
.\mvnw.cmd clean compile
```

`.\mvnw.cmd test` recompila con `[debug parameters release 25]`, que es lo correcto. Si `clean`
falla con `Failed to delete ...`, cerrá la ventana de VS Code o borrá `target/` a mano: el
language server tiene los archivos abiertos.

`.\mvnw.cmd clean` con la app corriendo falla siempre por archivos bloqueados; en ese caso usá
solo `.\mvnw.cmd compile`.
