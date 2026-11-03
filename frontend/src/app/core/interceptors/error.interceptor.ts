import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Manejo central de errores de autenticacion: si el backend responde 401 con la
 * sesion abierta (cookie vencida o revocada), limpia el estado y manda al login.
 *
 * Los 401 de /api/auth/** se dejan pasar: los resuelve el guard o el formulario
 * de login, que sabe mostrar el mensaje correcto.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/api/auth/')) {
        auth.limpiar();
        const returnUrl = router.url;
        router.navigate(['/login'], returnUrl === '/login' ? {} : { queryParams: { returnUrl } });
      }
      return throwError(() => error);
    }),
  );
};
