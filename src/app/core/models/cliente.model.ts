/**
 * Client view of backend Usuario. The `clave` field never leaves the backend
 * (UsuarioResponse DTO); telefono/telefono do not exist in the API either.
 */
export interface Cliente {
  id: number | null;
  nombres: string;
  apellidos: string;
  correo: string;
}

/** POST /api/usuarios: the backend requires a password on creation. */
export interface ClienteAlta {
  nombres: string;
  apellidos: string;
  correo: string;
  clave: string;
}

/** PUT /api/usuarios/{id}: `clave` is optional; empty keeps the current one. */
export interface ClienteEdicion {
  nombres: string;
  apellidos: string;
  correo: string;
  clave?: string;
}
