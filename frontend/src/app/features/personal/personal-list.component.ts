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
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { apiErrorMessage } from '../../core/http/api-error';
import {
  Cliente,
  PERSONAL_ROLES,
  ROL_LABEL,
  RolPersonal,
} from '../../core/models/cliente.model';
import { Pagina } from '../../core/models/pagina.model';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { PageHeader } from '../../shared/ui/page-header.component';
import { PersonalService } from './personal.service';

@Component({
  selector: 'app-personal-list',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, MatTableModule, MatButtonModule, MatIconModule, MatSelectModule, MatPaginatorModule, MatProgressSpinnerModule, MatFormFieldModule, MatInputModule, MatTooltipModule, PageHeader],
  templateUrl: './personal-list.component.html',
  styleUrl: './personal-list.component.scss',
})
export class PersonalListComponent {
  private readonly service = inject(PersonalService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly pagina = signal<Pagina<Cliente> | null>(null);
  readonly personal = computed(() => this.pagina()?.content ?? []);
  readonly total = computed(() => this.pagina()?.page.totalElements ?? 0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly loading = signal(true);
  readonly roles = PERSONAL_ROLES;
  readonly etiquetasRol = ROL_LABEL;
  readonly displayedColumns = ['id', 'nombre', 'correo', 'rol', 'acciones'];
  /** Guarda el id mientras se confirma el cambio de rol en el backend. */
  readonly cambiando = signal<number | null>(null);
  /** Guarda el id mientras se elimina, para deshabilitar el boton y evitar doble clic. */
  readonly eliminando = signal<number | null>(null);
  /** Busqueda por nombres, apellidos o correo; la resuelve el backend. */
  readonly busqueda = new FormControl('', { nonNullable: true });

  constructor() {
    this.busqueda.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.cargar(0));
    this.cargar();
  }

  rolLabel(usuario: Cliente): string {
    return ROL_LABEL[usuario.rol ?? 'RECEPCIONISTA'];
  }

  private cargar(page = 0): void {
    this.loading.set(true);
    const q = this.busqueda.value.trim();
    this.service.list(page, this.pageSize(), q || undefined).subscribe({
      next: (pagina) => {
        this.pagina.set(pagina);
        this.pageIndex.set(pagina.page.number);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.snackBar.open(apiErrorMessage(err, 'No se pudo cargar el personal'), 'Cerrar', { duration: 4000 });
      },
    });
  }

  cambiarPagina(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.cargar(event.pageIndex);
  }

  cambiarRol(usuario: Cliente, rol: RolPersonal): void {
    if (usuario.id == null || usuario.rol === rol) return;

    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Cambiar rol',
          message: `¿Cambiar el rol de ${usuario.nombres} ${usuario.apellidos} a ${ROL_LABEL[rol]}?`,
          confirmLabel: 'Cambiar rol',
        },
        width: '420px',
      })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.aplicarCambioRol(usuario, rol);
        } else {
          // El select ya muestra el rol nuevo: recargamos para volver al valor real.
          this.cargar(this.pageIndex());
        }
      });
  }

  private aplicarCambioRol(usuario: Cliente, rol: RolPersonal): void {
    if (usuario.id == null) return;

    this.cambiando.set(usuario.id);
    this.service.cambiarRol(usuario.id, rol).subscribe({
      next: (actualizado) => {
        this.cambiando.set(null);
        // Si dejó de ser personal, sale del listado: recargamos la página actual.
        this.cargar(this.pageIndex());
        this.snackBar.open(`Rol actualizado a ${ROL_LABEL[actualizado.rol ?? rol]}.`, 'Cerrar', { duration: 2500 });
      },
      error: (err) => {
        this.cambiando.set(null);
        // Se recarga para descartar el valor que el usuario intentó seleccionar.
        this.cargar(this.pageIndex());
        this.snackBar.open(apiErrorMessage(err, 'No se pudo cambiar el rol'), 'Cerrar', { duration: 5000 });
      },
    });
  }

  eliminar(usuario: Cliente): void {
    if (usuario.id == null) return;
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Eliminar personal',
          message: `¿Eliminar a ${usuario.nombres} ${usuario.apellidos}? Esta acción no se puede deshacer.`,
          confirmLabel: 'Eliminar',
          destructive: true,
        },
        width: '420px',
      })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) this.eliminarConfirmado(usuario);
      });
  }

  private eliminarConfirmado(usuario: Cliente): void {
    if (usuario.id == null) return;
    this.eliminando.set(usuario.id);
    this.service.remove(usuario.id).subscribe({
      next: () => {
        this.eliminando.set(null);
        const eraLaUltimaFila = this.personal().length === 1 && this.pageIndex() > 0;
        this.cargar(eraLaUltimaFila ? this.pageIndex() - 1 : this.pageIndex());
        this.snackBar.open('Personal eliminado.', 'Cerrar', { duration: 2500 });
      },
      error: (err) => {
        this.eliminando.set(null);
        this.snackBar.open(apiErrorMessage(err, 'No se pudo eliminar al trabajador'), 'Cerrar', { duration: 5000 });
      },
    });
  }
}
