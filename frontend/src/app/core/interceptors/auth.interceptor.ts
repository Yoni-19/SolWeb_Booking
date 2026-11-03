import { HttpInterceptorFn } from '@angular/common/http';

/**
 * El JWT vive en una cookie httpOnly: JavaScript no lo ve ni lo adjunta a mano.
 * `withCredentials` hace que el navegador mande y reciba esa cookie en cada request
 * cross-origin contra la API (frontend :4200, backend :8080).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.clone({ withCredentials: true }));
