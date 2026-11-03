# sreservas — Backend

API REST del Sistema de Reservas de un complejo deportivo. Spring Boot sobre MySQL, con JWT
transportado en una cookie `httpOnly`.

## Stack

| Elemento | Versión |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.1 |
| Spring Security + JWT (jjwt) | 6.x / 0.12.6 |
| JPA / Hibernate | 7.4.5 |
| MySQL | 8.0 |

Sin Lombok: getters, setters y constructores se escriben explícitos.

## Requisitos

- JDK 25
- MySQL 8 corriendo en `localhost:3306` (la base `db_complejoDeportivo` se crea sola)
- Maven no hace falta instalado: el proyecto incluye el wrapper (`mvnw.cmd`)

## Cómo correr

```powershell
cd backend
.\mvnw.cmd clean compile      # build oficial
.\mvnw.cmd spring-boot:run    # levanta en http://localhost:8080
.\mvnw.cmd test               # tests (requiere MySQL arriba)
```

### En VS Code

1. Abrí el workspace **raíz** (`SistemaComplejoDeportivo`), no solo `backend/`.
2. Esperá a que la extensión de Java termine de compilar (barra de estado "Java: Ready").
3. Abrí `SreservasApplication.java` y usá el botón **Run**.
4. Alternativa a prueba de balas: `Terminal > Run Task > backend: run`, o el comando Maven
   de arriba.

La base se inicializa sola: `spring.jpa.hibernate.ddl-auto=update` crea el esquema y
`src/main/resources/data.sql` inserta los datos iniciales de forma idempotente
(`INSERT IGNORE`), así que se puede volver a correr sin duplicar nada.

## Credenciales de prueba

Las tres cuentas del seed usan la clave `test123` y sus roles salen de `data.sql`:

| Correo | Rol |
|---|---|
| `admin@email.com` | `ADMIN` — Admin Sistema |
| `laura@email.com` | `CLIENTE` — Laura Gomez |
| `pedro@email.com` | `CLIENTE` — Pedro Diaz |

```powershell
# El login responde 204 y deja el JWT en la cookie access_token.
curl -i -c cookies.txt -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"correo":"admin@email.com","clave":"test123"}'

# Reusa la cookie guardada
curl -b cookies.txt http://localhost:8080/api/auth/me
```

## Endpoints

Todos salvo `POST /api/auth/login`, `POST /api/auth/logout` y `POST /api/usuarios` (registro)
exigen la cookie `access_token`. El header `Authorization: Bearer <token>` sigue aceptándose
como alternativa para Postman y scripts.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/login` | Valida credenciales y setea la cookie (204) |
| POST | `/api/auth/logout` | Borra la cookie (204) |
| GET | `/api/auth/me` | Usuario autenticado con su `rol` |
| GET/POST | `/api/usuarios` | Listar (paginado) / registrar usuario (el alta nace `CLIENTE`) |
| GET/PUT/DELETE | `/api/usuarios/{id}` | Detalle / actualizar (PUT no exige clave) / eliminar |
| PUT | `/api/usuarios/{id}/rol` | Cambiar el rol (solo `ADMIN`) |
| GET/POST | `/api/canchas` | Listar / crear cancha |
| GET/PUT/DELETE | `/api/canchas/{id}` | Detalle / actualizar / eliminar |
| GET/POST | `/api/reservas` | Listar (paginado) / crear reserva |
| GET/PUT/DELETE | `/api/reservas/{id}` | Detalle / actualizar / eliminar |
| GET/POST | `/api/pagos` | Listar / crear pago |
| GET/PUT/DELETE | `/api/pagos/{id}` | Detalle / actualizar / eliminar |

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
- **CORS con credenciales.** `SecurityConfig` habilita `allowCredentials(true)` para
  `http://localhost:4200` y `http://127.0.0.1:4200`; con `*` el navegador rechazaría la cookie.

## Paginación

`GET /api/usuarios` y `GET /api/reservas` devuelven una página, no un array:

| Query param | Default | Descripción |
|---|---|---|
| `page` | `0` | Número de página (base 0) |
| `size` | `10` | Filas por página |
| `sort` | `id,desc` | Campo y dirección (`campo,asc` o `campo,desc`) |
| `estado` | — | Solo en reservas: `PENDIENTE`, `CONFIRMADA`, `CANCELADA`, `COMPLETADA` |

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
| Ver clientes | sí | sí | no |
| Crear/editar/eliminar clientes y cambiar roles | sí | no | no |
| Ver todas las reservas | sí | sí | solo las propias |
| Crear reserva | sí | sí | sí (a su nombre) |
| Editar/eliminar reserva | sí | sí | solo las propias |
| Pagos | sí | sí | no (fase 2) |

Un `CLIENTE` puede reprogramar o cancelar **sus** reservas: el `403` por reserva ajena lo aplica
`ReservaService.verificarPropiedad`. Las reservas `COMPLETADA` no se reprograman ni se cancelan
(`409`).

Un rol sin permiso recibe `403` con el envelope JSON `{ timestamp, status, error, message }`.

Reglas de negocio implementadas:

- **Solapamiento de reservas**: `409 Conflict` si se superpone con otra reserva de la misma
  cancha. Una reserva `CANCELADA` no bloquea.
- **Monto**: se cobra por hora iniciada (`ceil` de los minutos / 60), no por hora exacta.
- **Contratos**: las respuestas usan DTOs (`dto/`), nunca las entidades. El hash BCrypt de
  la clave nunca sale por la API.
- **Último administrador**: no se puede quitar el rol al último `ADMIN` (`409`).
- **CORS**: habilitado para `http://localhost:4200` y `http://127.0.0.1:4200` (frontend Angular).

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
