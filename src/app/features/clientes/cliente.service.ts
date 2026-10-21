import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Cliente } from '../../core/models/cliente.model';

/**
 * MOCK DATA — in-memory replacement for a future real HTTP service.
 * Maps 1:1 to GET/POST/PUT /api/usuarios (client view, no password).
 */
@Injectable({ providedIn: 'root' })
export class ClienteService {
  private data: Cliente[] = [
    { id: 1, nombres: 'Juan', apellidos: 'Pérez', correo: 'juan@email.com' },
    { id: 2, nombres: 'María', apellidos: 'Gómez', correo: 'maria@email.com' },
    { id: 3, nombres: 'Carlos', apellidos: 'Ruiz', correo: 'carlos@email.com' },
    { id: 4, nombres: 'Ana', apellidos: 'Torres', correo: 'ana@email.com' },
    { id: 5, nombres: 'Luis', apellidos: 'Fernández', correo: 'luis@email.com' },
  ];

  list(): Observable<Cliente[]> {
    return of(this.data.map((c) => ({ ...c })));
  }

  get(id: number): Observable<Cliente | undefined> {
    return of(this.data.find((c) => c.id === id));
  }

  create(cliente: Omit<Cliente, 'id'>): Observable<Cliente> {
    const nuevo: Cliente = { ...cliente, id: Math.max(0, ...this.data.map((c) => c.id ?? 0)) + 1 };
    this.data.push(nuevo);
    return of({ ...nuevo });
  }

  update(cliente: Cliente): Observable<Cliente> {
    this.data = this.data.map((c) => (c.id === cliente.id ? { ...cliente } : c));
    return of({ ...cliente });
  }

  remove(id: number): Observable<void> {
    this.data = this.data.filter((c) => c.id !== id);
    return of(undefined);
  }
}
