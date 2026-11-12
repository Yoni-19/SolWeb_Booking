import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/http/api-error';
import { Cliente } from '../../core/models/cliente.model';
import { Pagina } from '../../core/models/pagina.model';
import { AuthService } from '../../core/services/auth.service';
import { PageHeader } from '../../shared/ui/page-header.component';
import { ClienteService } from './cliente.service';

@Component({
  selector: 'app-cliente-list',
  standalone: true,
  imports: [RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatPaginatorModule, MatProgressSpinnerModule, PageHeader],
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
  /** El ADMIN edita y elimina; el recepcionista solo consulta y da de alta clientes. */
  readonly puedeGestionar = this.auth.esAdmin;
  readonly puedeCrear = this.auth.esStaff;
  readonly displayedColumns = computed(() =>
    this.puedeGestionar() ? ['id', 'nombre', 'correo', 'acciones'] : ['id', 'nombre', 'correo'],
  );

  constructor() {
    this.cargar();
  }

  /** Trae una página de clientes; el backend fuerza rol=CLIENTE. */
  private cargar(page = 0): void {
    this.loading.set(true);
    this.service.list(page, this.pageSize(), 'CLIENTE').subscribe({
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
