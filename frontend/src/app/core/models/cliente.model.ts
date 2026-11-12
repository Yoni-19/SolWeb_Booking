/**
 * Client view of backend Usuario. The `clave` field never leaves the backend
 * (UsuarioResponse DTO); telefono/telefono do not exist in the API either.
 */
export interface Cliente {
  id: number | null;
  nombres: string;
  apellidos: string;
  correo: string;
  /** Always present in API responses; optional for locally built objects. */
  rol?: Rol;
}

/** Role of a user; mirrors the backend enum `Rol`. */
export type Rol =
  | 'ADMIN'
  | 'RECEPCIONISTA'
  | 'CLIENTE'
  | 'ENTRENADOR'
  | 'MANTENIMIENTO'
  | 'ARBITRO';

/** Roles that can sign in and use the app; the rest exist only as data. */
export type RolOperativo = 'ADMIN' | 'RECEPCIONISTA' | 'CLIENTE';

/** Personal = every role except CLIENTE. */
export type RolPersonal = Exclude<Rol, 'CLIENTE'>;

/** Human-readable role names used in chips and menus. */
export const ROL_LABEL: Record<Rol, string> = {
  ADMIN: 'Administrador',
  RECEPCIONISTA: 'Recepcionista',
  CLIENTE: 'Cliente',
  ENTRENADOR: 'Entrenador',
  MANTENIMIENTO: 'Mantenimiento',
  ARBITRO: 'Árbitro',
};

/** Roles assignable from the Personal module. */
export const PERSONAL_ROLES: RolPersonal[] = [
  'ADMIN',
  'RECEPCIONISTA',
  'ENTRENADOR',
  'MANTENIMIENTO',
  'ARBITRO',
];

/** GET /api/auth/me: what the UI needs to know about the current session. */
export interface UsuarioSesion {
  id: number;
  nombres: string;
  apellidos: string;
  correo: string;
  rol: Rol;
}

/** POST /api/usuarios: the backend requires a password on creation. */
export interface ClienteAlta {
  nombres: string;
  apellidos: string;
  correo: string;
  clave: string;
}

/** POST /api/usuarios/personal: personal creation requires a role. */
export interface PersonalAlta extends ClienteAlta {
  rol: RolPersonal;
}

/** PUT /api/usuarios/{id}: `clave` is optional; empty keeps the current one. */
export interface ClienteEdicion {
  nombres: string;
  apellidos: string;
  correo: string;
  clave?: string;
}
