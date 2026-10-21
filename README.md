# Sistema Complejo Deportivo

Sistema de reservas de canchas de un complejo deportivo: API REST en Spring Boot sobre MySQL
y SPA en Angular. Un solo repositorio con las dos mitades.

## Estructura

```
.
├── backend/     API REST (Spring Boot + MySQL + JWT)
├── frontend/    SPA (Angular standalone + signals + Material)
├── .vscode/     configuración compartida del workspace
├── README.md    este archivo
└── AGENTS.md    convenciones del monorepo
```

Cada subproyecto tiene su propio `.gitignore` y su documentación:
`backend/README.md` y `frontend/README.md`.

## Stack

| Parte | Tecnología |
|---|---|
| Backend | Java 25, Spring Boot 4.1.1, Spring Security + JWT, Spring Data JPA, MySQL 8.0, Lombok |
| Frontend | Angular 22 (standalone, signals), Angular Material 22, TypeScript 6, RxJS 7.8 |

## Requisitos

- JDK 25
- MySQL 8 corriendo en `localhost:3306` — la base `db_complejoDeportivo` se crea sola
- Node.js 20+ con npm
- Maven no hace falta: el backend trae el wrapper (`backend/mvnw.cmd`)

## Cómo correr

Son **dos procesos separados**. Primero el backend, porque el frontend consume la API real.

```powershell
# Terminal 1 — API en http://localhost:8080
cd backend
.\mvnw.cmd spring-boot:run

# Terminal 2 — SPA en http://localhost:4200
cd frontend
npm install
npm start
```

La base se inicializa sola: `ddl-auto=update` crea el esquema y `backend/src/main/resources/data.sql`
inserta los datos iniciales de forma idempotente (`INSERT IGNORE`), así que se puede volver a
correr sin duplicar nada.

## Credenciales de prueba

Las tres cuentas del seed usan la clave `test123`:

| Correo | Nombre |
|---|---|
| `admin@email.com` | Admin Sistema |
| `laura@email.com` | Laura Gomez |
| `pedro@email.com` | Pedro Diaz |

## Reglas de negocio

- **Solapamiento**: `409 Conflict` si una reserva se superpone con otra de la misma cancha.
- **Monto**: por hora iniciada (`ceil(minutos / 60)`), no por hora exacta.
- **Contratos**: las respuestas usan DTOs; el hash BCrypt de la clave nunca sale por la API.
- **CORS**: habilitado solo para `http://localhost:4200` y `http://127.0.0.1:4200`.

El detalle de los endpoints y de los contratos está en `backend/README.md`.

## Estado actual

Sin roles todavía: `admin@email.com` es un nombre, no un rol, y cualquier usuario logueado
accede a todo. El plan de roles (ADMIN / RECEPCIONISTA / CLIENTE) y la regla del abono del 50%
están pendientes de implementación.
