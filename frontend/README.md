# Frontend — Sistema Complejo Deportivo

SPA del sistema de reservas. Angular standalone con signals y Angular Material.
Consume la API real del backend (`http://localhost:8080`): no hay datos en memoria.

## Requisitos

- Node.js 22.12+ con npm
- La API corriendo (`cd ../backend` → `.\mvnw.cmd spring-boot:run`)

## Cómo correr

```powershell
cd frontend
npm install
npm start        # dev server en http://localhost:4200
npm run build    # build de producción -> dist/
```

### Tests

El frontend **todavía no tiene runner de tests**: `angular.json` no define el target `test`, no
hay dependencias de Vitest/Karma y no existe ningún `*.spec.ts`. `npm test` falla con
`Cannot determine project or target for command`. La verificación por ahora es `npm run build`
más pruebas manuales contra la API.

## Despliegue en Vercel

- Root Directory: `frontend`.
- `vercel.json` proxea `/api/:path*` a `https://complejo-api-xcb4.onrender.com/api/:path*` y
  hace fallback de las rutas SPA a `/index.html`.
- Si el servicio de Render se recrea, actualizar `destination` en `vercel.json`.
- El build de producción usa `environment.prod.ts` (`apiBase: ''`): los services pegan relativo
  a `/api` y el proxy de Vercel evita CORS, manteniendo la cookie `SameSite=Strict` como mismo
  origen.
- Verificar en DevTools que el `Set-Cookie` del login pase por el proxy y quede en el dominio de
  Vercel.

## Estructura

```
src/app/
├── core/          servicios, modelos, interceptores y guards compartidos
│   ├── api.constants.ts     API_BASE (sale de src/environments/)
│   ├── guards/              auth.guard (valida la sesión contra /api/auth/me) y role.guard
│   ├── http/api-error.ts    traduce el envelope de error del backend a un mensaje
│   ├── interceptors/        withCredentials + redirección central ante 401
│   ├── models/              tipos de dominio (Cancha, Cliente, Reserva, Pago)
│   └── services/auth.service.ts
├── features/      un módulo por dominio: canchas/, clientes/, personal/, reservas/,
│                  pagos/, dashboard/, login/ y registro/
├── shared/        layout (menú lateral) y componentes de UI
└── app.routes.ts  rutas + guard de autenticación
```

No hay una pantalla de **pagos**: el cobro vive dentro de la tabla de reservas. El `CLIENTE`
paga la seña del 50% o el total con un botón (pago simulado con tarjeta); el personal cobra
cualquier monto del saldo desde el diálogo "Registrar pago" (`features/pagos/`).

**Clientes** y **Personal** son módulos separados: `clientes/` lista solo usuarios `CLIENTE`
(admin y recepcionista), mientras que `personal/` es exclusivo del `ADMIN` y lista/crea todo lo
demás (`ADMIN`, `RECEPCIONISTA`, `ENTRENADOR`, `MANTENIMIENTO`, `ARBITRO`). Los roles que no
sean `ADMIN`, `RECEPCIONISTA` o `CLIENTE` son solo informativos: no tienen pantallas y el
backend rechaza su login con `403`.

## Convenciones

- Cada módulo tiene su `*.service.ts` (solo HTTP contra la API), sus componentes y sus modelos.
- Los formularios son reactivos con `FormBuilder`; cada uno maneja `loading` y `errorMessage`
  como signals y muestra el error del backend con `apiErrorMessage()`.
- **El token no se guarda en el navegador.** El backend lo deja en una cookie `httpOnly` con
  `SameSite=Strict`; el `authInterceptor` solo agrega `withCredentials` para que el navegador la
  mande en los requests cross-origin (frontend `:4200`, API `:8080`).
- `authGuard` no puede leer la cookie: valida la sesión con `GET /api/auth/me` (una sola vez,
  cacheada en un signal) y redirige a `/login` si falla.
- `errorInterceptor` centraliza el `401`: limpia la sesión y manda al login con `returnUrl`.
- El logout hace `POST /api/auth/logout` (borra la cookie) y recién después navega.

## Contratos que el frontend no puede cambiar

- `POST /api/usuarios` exige clave; `PUT /api/usuarios/{id}` la acepta opcional
  (vacía = no cambiar la actual).
- `PUT /api/reservas/{id}` ignora el `usuarioId` y nunca acepta `estadoReserva` ni
  `montoTotal`: los calcula el backend.
- La interfaz **cancela** reservas con `PATCH /api/reservas/{id}/estado` (baja lógica); no usa
  `DELETE`. Check-in y check-out son solo para staff, y un `CLIENTE` deja de ver sus canceladas.
- `POST /api/pagos` exige `monto`, `metodoPago` y `reservaId`. Un `CLIENTE` solo puede pagar la
  seña del 50% o el total, una vez, sobre su propia reserva; el saldo lo cobra el personal.
- La reserva nace `PENDIENTE`: hasta que entra la seña no se puede hacer check-in. El staff
  inicia (`EN_CURSO`), cobra el saldo y finaliza (o la reserva se completa sola al pagar todo).
- El solapamiento de horarios devuelve `409`.
- `GET /api/usuarios` y `GET /api/reservas` devuelven `{ content, page: { size, number,
  totalElements, totalPages } }`, no un array. Las tablas usan `MatPaginator` y vuelven a pedir
  la página al backend (`page` es base 0). El `<select>` de clientes del formulario de reservas
  usa `ClienteService.listAll()`, que pide una única página grande porque no es una tabla.
- El listado de personal usa `GET /api/usuarios?personal=true` y el alta
  `POST /api/usuarios/personal` con `rol` obligatorio (solo `ADMIN`). El listado de clientes
  manda `?rol=CLIENTE` y el backend además lo fuerza para el recepcionista.
