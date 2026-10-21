import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_BASE } from '../api.constants';

const TOKEN_KEY = 'auth_token';
const CORREO_KEY = 'auth_correo';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /** POST /api/auth/login — real backend call. Stores the JWT on success. */
  login(correo: string, clave: string): Observable<{ token: string }> {
    return this.http.post<{ token: string }>(`${API_BASE}/api/auth/login`, { correo, clave }).pipe(
      tap((res) => {
        localStorage.setItem(TOKEN_KEY, res.token);
        localStorage.setItem(CORREO_KEY, correo);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(CORREO_KEY);
    this.router.navigate(['/login']);
  }

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  get correo(): string | null {
    return localStorage.getItem(CORREO_KEY);
  }

  get isAuthenticated(): boolean {
    return !!this.token;
  }
}
