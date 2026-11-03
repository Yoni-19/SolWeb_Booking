import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, of, switchMap, tap } from 'rxjs';
import { API_BASE } from '../api.constants';
import { UsuarioSesion } from '../models/cliente.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sesionState = signal<UsuarioSesion | null>(null);
  /** Current session, including the role. Null until it is loaded from the API. */
  readonly sesion = this.sesionState.asReadonly();
  readonly correo = computed(() => this.sesionState()?.correo ?? null);

  readonly esAdmin = computed(() => this.sesionState()?.rol === 'ADMIN');
  readonly esStaff = computed(() => {
    const rol = this.sesionState()?.rol;
    return rol === 'ADMIN' || rol === 'RECEPCIONISTA';
  });
  readonly esCliente = computed(() => this.sesionState()?.rol === 'CLIENTE');

  /**
   * POST /api/auth/login: the backend stores the JWT in an httpOnly cookie,
   * so the browser never exposes it to JavaScript. Then loads the session.
   */
  login(correo: string, clave: string): Observable<UsuarioSesion> {
    return this.http.post<void>(`${API_BASE}/api/auth/login`, { correo, clave }).pipe(
      switchMap(() => this.cargarSesion()),
    );
  }

  /** GET /api/auth/me: the role always comes from the backend, never from the client. */
  cargarSesion(): Observable<UsuarioSesion> {
    return this.http.get<UsuarioSesion>(`${API_BASE}/api/auth/me`).pipe(
      tap((sesion) => this.sesionState.set(sesion)),
    );
  }

  /** Returns the cached session or fetches it once (used by guards and the layout). */
  asegurarSesion(): Observable<UsuarioSesion> {
    const actual = this.sesionState();
    return actual ? of(actual) : this.cargarSesion();
  }

  /** POST /api/auth/logout: the backend expires the cookie, then the local state is cleared. */
  logout(): void {
    this.http.post<void>(`${API_BASE}/api/auth/logout`, {}).subscribe({
      next: () => this.finalizar(),
      error: () => this.finalizar(),
    });
  }

  /** Clears the local session without calling the API (used by guards and the 401 interceptor). */
  limpiar(): void {
    this.sesionState.set(null);
  }

  private finalizar(): void {
    this.limpiar();
    this.router.navigate(['/login']);
  }
}
