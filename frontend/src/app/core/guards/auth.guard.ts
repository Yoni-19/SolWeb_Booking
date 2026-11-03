import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Blocks app routes unless the backend confirms a valid session. The JWT lives in
 * an httpOnly cookie, so the only way to know is GET /api/auth/me (cached once).
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.asegurarSesion().pipe(
    map(() => true),
    catchError(() => {
      auth.limpiar();
      return of(router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }));
    }),
  );
};
