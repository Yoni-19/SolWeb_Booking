import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Impide mostrar /login y /registro cuando ya hay sesión abierta: si /me la
 * confirma, redirige al dashboard. Si falla (401 o backend caído), deja pasar
 * para que el usuario pueda autenticarse.
 */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.asegurarSesion().pipe(
    map(() => router.createUrlTree(['/dashboard'])),
    catchError(() => of(true)),
  );
};
