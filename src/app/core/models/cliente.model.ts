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
