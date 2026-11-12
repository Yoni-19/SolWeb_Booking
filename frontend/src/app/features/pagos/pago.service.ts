import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../core/api.constants';
import { Pago, PagoAlta } from '../../core/models/pago.model';

/**
 * Real HTTP service for payments. One row per reservation: the backend
 * accumulates the amount (seña first, balance later).
 */
@Injectable({ providedIn: 'root' })
export class PagoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/api/pagos`;

  create(pago: PagoAlta): Observable<Pago> {
    return this.http.post<Pago>(this.url, pago);
  }
}
