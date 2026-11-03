import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/http/api-error';
import { Cliente, Rol, ROL_LABEL } from '../../core/models/cliente.model';
import { Pagina } from '../../core/models/pagina.model';
import { AuthService } from '../../core/services/auth.service';
import { PageHeader } from '../../shared/ui/page-header.component';
import { ClienteService } from './cliente.service';

@Component({
  selector: 'app-cliente-list',
  standalone: true,
  imports: [RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatChipsModule, MatSelectModule, MatPaginatorModule, MatProgressSpinnerModule, PageHeader],
  templateUrl: './cliente-list.component.html',
  styleUrl: './cliente-list.component.scss',
})
export class ClienteListComponent {
  private readonly service = inject(ClienteService);
  private readonly snackBar = inject(MatSnackBar);
  readonly auth = inject(AuthService);

  /** Página actual devuelta por el backend (content + metadatos). */
  readonly pagina = signal<Pagina<Cliente> | null>(null);
  readonly clientes = computed(() => this.pagina()?.content ?? []);
  readonly total = computed(() => this.pagina()?.page.totalElements ?? 0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly loading = signal(true);
  readonly roles: Rol[] = ['ADMIN', 'RECEPCIONISTA', 'CLIENTE'];
  readonly etiquetasRol = ROL_LABEL;
  /** El rol es editable solo por un ADMIN; el recepcionista consulta. */
  readonly puedeGestionar = this.auth.esAdmin;
  /** El recepcionista no ve la columna de acciones (solo lectura). */
  readonly displayedColumns = computed(() =>
    this.puedeGestionar() ? ['id', 'nombre', 'correo', 'rol', 'acciones'] : ['id', 'nombre', 'correo', 'rol'],
  );
  /** Guarda el id mientras se confirma el cambio de rol en el backend. */
  readonly cambiando = signal<number | null>(null);

  constructor() {
    this.cargar();
  }

  /** Typed label helper: table rows are untyped, so indexing the map needs a cast. */
  rolLabel(cliente: Cliente): string {
    return ROL_LABEL[cliente.rol ?? 'CLIENTE'];
  }

  /** Trae una página del backend; `page` es base 0. */
  private cargar(page = 0): void {
    this.loading.set(true);
    this.service.list(page, this.pageSize()).subscribe({
      next: (pagina) => {
        this.pagina.set(pagina);
        this.pageIndex.set(pagina.page.number);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.snackBar.open(apiErrorMessage(err, 'No se pudieron cargar los clientes'), 'Cerrar', { duration: 4000 });
      },
    });
  }

  cambiarPagina(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.cargar(event.pageIndex);
  }

  cambiarRol(cliente: Cliente, rol: Rol): void {
    if (cliente.id == null || cliente.rol === rol) return;

    this.cambiando.set(cliente.id);
    this.service.cambiarRol(cliente.id, rol).subscribe({
      next: (actualizado) => {
        this.pagina.update((pagina) =>
          pagina
            ? { ...pagina, content: pagina.content.map((c) => (c.id === actualizado.id ? { ...c, rol: actualizado.rol } : c)) }
            : pagina,
        );
        this.cambiando.set(null);
        this.snackBar.open('Rol actualizado.', 'Cerrar', { duration: 2500 });
      },
      error: (err) => {
        this.cambiando.set(null);
        // Se recarga para descartar el valor que el usuario intentó seleccionar.
        this.cargar(this.pageIndex());
        this.snackBar.open(apiErrorMessage(err, 'No se pudo cambiar el rol'), 'Cerrar', { duration: 5000 });
      },
    });
  }

  eliminar(cliente: Cliente): void {
    if (cliente.id == null) return;
    this.service.remove(cliente.id).subscribe({
      next: () => {
        // Si era la única fila de la página, retrocedemos: si no, quedaría vacía.
        const eraLaUltimaFila = this.clientes().length === 1 && this.pageIndex() > 0;
        this.cargar(eraLaUltimaFila ? this.pageIndex() - 1 : this.pageIndex());
        this.snackBar.open('Cliente eliminado.', 'Cerrar', { duration: 2500 });
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'No se pudo eliminar el cliente'), 'Cerrar', { duration: 5000 });
      },
    });
  }
}
