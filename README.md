# sreservas — Backend

API REST del Sistema de Reservas de un complejo deportivo. Spring Boot sobre MySQL, con JWT.

## Stack

| Elemento | Versión |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.1 |
| Spring Security + JWT (jjwt) | 6.x / 0.12.6 |
| JPA / Hibernate | 7.4.5 |
| MySQL | 8.0 |
| Lombok | 1.18.46 (lo fija el BOM de Spring Boot) |

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

La base se inicializa sola: `spring.jpa.hibernate.ddl-auto=update` crea el esquema y
`src/main/resources/data.sql` inserta los datos iniciales de forma idempotente
(`INSERT IGNORE`), así que se puede volver a correr sin duplicar nada.

## Credenciales de prueba

Las tres cuentas del seed usan la clave `test123`:

| Correo | Rol |
|---|---|
| `admin@email.com` | Admin Sistema |
| `laura@email.com` | Laura Gomez |
| `pedro@email.com` | Pedro Diaz |

```powershell
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"correo":"admin@email.com","clave":"test123"}'
```

## Endpoints

Todos salvo `POST /api/auth/login` y `POST /api/usuarios` (registro) exigen
`Authorization: Bearer <token>`.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/login` | Devuelve `{ token }` |
| GET/POST | `/api/usuarios` | Listar / registrar usuario |
| GET/PUT/DELETE | `/api/usuarios/{id}` | Detalle / actualizar (PUT no exige clave) / eliminar |
| GET/POST | `/api/canchas` | Listar / crear cancha |
| GET/PUT/DELETE | `/api/canchas/{id}` | Detalle / actualizar / eliminar |
| GET/POST | `/api/reservas` | Listar / crear reserva |
| GET/PUT/DELETE | `/api/reservas/{id}` | Detalle / actualizar / eliminar |
| GET/POST | `/api/pagos` | Listar / crear pago |
| GET/PUT/DELETE | `/api/pagos/{id}` | Detalle / actualizar / eliminar |

Reglas de negocio implementadas:

- **Solapamiento de reservas**: `409 Conflict` si se superpone con otra reserva de la misma
  cancha. Una reserva `CANCELADA` no bloquea.
- **Monto**: se cobra por hora iniciada (`ceil` de los minutos / 60), no por hora exacta.
- **Contratos**: las respuestas usan DTOs (`dto/`), nunca las entidades. El hash BCrypt de
  la clave nunca sale por la API.
- **CORS**: habilitado para `http://localhost:4200` y `http://127.0.0.1:4200` (frontend Angular).

## Gotcha importante: el IDE corrompe el build (Lombok + JDK 25)

Si el backend falla al arrancar con

```
java.lang.VerifyError: Constructor must call super() or this() before return
```

no es un error del código: **el bytecode en `target/classes` está corrupto**.

**Causa.** La extensión *Language Support for Java* de VS Code compila con ECJ usando el
Lombok que trae embebido (`lombok-1.18.39-4050.jar`). Lombok sólo ganó soporte de JDK 25 en
la versión **1.18.40** (4/9/2025, issue #3859), así que sobre JDK 25 ese agente genera
constructores sin la llamada a `super()` → el verificador de la JVM los rechaza. Maven, en
cambio, usa Lombok **1.18.46** (el que fija el BOM de Spring Boot) y compila bien.

**Solución.** `.vscode/settings.json` fuerza el modo de compilación por `javac` del
language server:

```json
{
  "java.autobuild.enabled": false,
  "java.jdt.ls.javac.enabled": "on"
}
```

Después de tocar ese archivo hay que recargar la ventana de VS Code
(*Developer: Reload Window*), porque el valor se lee al iniciar el language server.

**Regla de oro:** el build oficial es Maven. Si el IDE se queda trabado o falla algo raro, se
arregla con `.\mvnw.cmd clean compile`; nunca con el botón de compilar del IDE.

Para comprobar que el bytecode es válido:

```powershell
javap -c -p -cp target/classes com.proyectosw.sreservas.config.SecurityConfig
```

El constructor debe mostrar `invokespecial java/lang/Object."<init>"`.
