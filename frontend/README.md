# Frontend — Sistema Complejo Deportivo

SPA del sistema de reservas. Angular standalone con signals y Angular Material.
Consume la API real del backend (`http://localhost:8080`): no hay datos en memoria.

## Requisitos

- Node.js 20+ con npm
- La API corriendo (`cd ../backend` → `.\mvnw.cmd spring-boot:run`)

## Cómo correr

```powershell
cd frontend
npm install
npm start        # dev server en http://localhost:4200
npm run build    # build de producción -> dist/
npm test         # tests (Vitest)
```

## Estructura

```
src/app/
├── core/          servicios, modelos, interceptores y guards compartidos
│   ├── api.constants.ts     API_BASE (apunta a http://localhost:8080)
│   ├── guards/              auth.guard (valida la sesión contra /api/auth/me) y role.guard
│   ├── http/api-error.ts    traduce el envelope de error del backend a un mensaje
│   ├── interceptors/        withCredentials + redirección central ante 401
│   ├── models/              tipos de dominio (Cancha, Cliente, Reserva)
│   └── services/auth.service.ts
├── features/      un módulo por dominio: canchas/, clientes/, reservas/, dashboard/, login/
├── shared/        layout (menú lateral) y componentes de UI
└── app.routes.ts  rutas + guard de autenticación
```

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
- `POST /api/pagos` no acepta `monto`: el servicio lo fuerza al total de la reserva.
- La solapamiento de horarios devuelve `409`.
- `GET /api/usuarios` y `GET /api/reservas` devuelven `{ content, page: { size, number,
  totalElements, totalPages } }`, no un array. Las tablas usan `MatPaginator` y vuelven a pedir
  la página al backend (`page` es base 0). El `<select>` de clientes del formulario de reservas
  usa `ClienteService.listAll()`, que pide una única página grande porque no es una tabla.
