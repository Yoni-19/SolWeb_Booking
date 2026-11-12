import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/http/api-error';
import {
  Cliente,
  PERSONAL_ROLES,
  ROL_LABEL,
  RolPersonal,
} from '../../core/models/cliente.model';
import { Pagina } from '../../core/models/pagina.model';
import { PageHeader } from '../../shared/ui/page-header.component';
import { PersonalService } from './personal.service';

@Component({
  selector: 'app-personal-list',
  standalone: true,
  imports: [RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatSelectModule, MatPaginatorModule, MatProgressSpinnerModule, PageHeader],
  templateUrl: './personal-list.component.html',
  styleUrl: './personal-list.component.scss',
})
export class PersonalListComponent {
  private readonly service = inject(PersonalService);
  private readonly snackBar = inject(MatSnackBar);

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

  constructor() {
    this.cargar();
  }

  rolLabel(usuario: Cliente): string {
    return ROL_LABEL[usuario.rol ?? 'RECEPCIONISTA'];
  }

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
    this.service.remove(usuario.id).subscribe({
      next: () => {
        const eraLaUltimaFila = this.personal().length === 1 && this.pageIndex() > 0;
        this.cargar(eraLaUltimaFila ? this.pageIndex() - 1 : this.pageIndex());
        this.snackBar.open('Personal eliminado.', 'Cerrar', { duration: 2500 });
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'No se pudo eliminar al trabajador'), 'Cerrar', { duration: 5000 });
      },
    });
  }
}
