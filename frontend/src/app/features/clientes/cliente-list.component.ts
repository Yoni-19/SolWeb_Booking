import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { apiErrorMessage } from '../../core/http/api-error';
import { Cliente } from '../../core/models/cliente.model';
import { Pagina } from '../../core/models/pagina.model';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { PageHeader } from '../../shared/ui/page-header.component';
import { ClienteService } from './cliente.service';

@Component({
  selector: 'app-cliente-list',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, MatTableModule, MatButtonModule, MatIconModule, MatPaginatorModule, MatProgressSpinnerModule, MatFormFieldModule, MatInputModule, MatTooltipModule, PageHeader],
  templateUrl: './cliente-list.component.html',
  styleUrl: './cliente-list.component.scss',
})
export class ClienteListComponent {
  private readonly service = inject(ClienteService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  readonly auth = inject(AuthService);

  /** Página actual devuelta por el backend (content + metadatos). */
  readonly pagina = signal<Pagina<Cliente> | null>(null);
  readonly clientes = computed(() => this.pagina()?.content ?? []);
  readonly total = computed(() => this.pagina()?.page.totalElements ?? 0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly loading = signal(true);
  /** Guarda el id mientras se elimina, para deshabilitar el boton y evitar doble clic. */
  readonly eliminando = signal<number | null>(null);
  /** Búsqueda por nombres, apellidos o correo; la resuelve el backend. */
  readonly busqueda = new FormControl('', { nonNullable: true });
  /** El ADMIN edita y elimina; el recepcionista solo consulta y da de alta clientes. */
  readonly puedeGestionar = this.auth.esAdmin;
  readonly puedeCrear = this.auth.esStaff;
  readonly displayedColumns = computed(() =>
    this.puedeGestionar() ? ['id', 'nombre', 'correo', 'acciones'] : ['id', 'nombre', 'correo'],
  );

  constructor() {
    this.busqueda.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.cargar(0));
    this.cargar();
  }

  /** Trae una página de clientes; el backend fuerza rol=CLIENTE. */
  private cargar(page = 0): void {
    this.loading.set(true);
    const q = this.busqueda.value.trim();
    this.service.list(page, this.pageSize(), 'CLIENTE', q || undefined).subscribe({
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
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Eliminar cliente',
          message: `¿Eliminar a ${cliente.nombres} ${cliente.apellidos}? Esta acción no se puede deshacer.`,
          confirmLabel: 'Eliminar',
          destructive: true,
        },
        width: '420px',
      })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) this.eliminarConfirmado(cliente);
      });
  }

  private eliminarConfirmado(cliente: Cliente): void {
    if (cliente.id == null) return;
    this.eliminando.set(cliente.id);
    this.service.remove(cliente.id).subscribe({
      next: () => {
        this.eliminando.set(null);
        // Si era la única fila de la página, retrocedemos: si no, quedaría vacía.
        const eraLaUltimaFila = this.clientes().length === 1 && this.pageIndex() > 0;
        this.cargar(eraLaUltimaFila ? this.pageIndex() - 1 : this.pageIndex());
        this.snackBar.open('Cliente eliminado.', 'Cerrar', { duration: 2500 });
      },
      error: (err) => {
        this.eliminando.set(null);
        this.snackBar.open(apiErrorMessage(err, 'No se pudo eliminar el cliente'), 'Cerrar', { duration: 5000 });
      },
    });
  }
}
