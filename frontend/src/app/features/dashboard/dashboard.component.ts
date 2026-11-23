import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { CanchaService } from '../canchas/cancha.service';
import { ClienteService } from '../clientes/cliente.service';
import { ReservaService } from '../reservas/reserva.service';
import { PageHeader } from '../../shared/ui/page-header.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, MatCard, MatCardContent, MatIconModule, MatButton, MatChipsModule, MatProgressSpinnerModule, PageHeader],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  readonly auth = inject(AuthService);
  private readonly reservasService = inject(ReservaService);
  private readonly canchasService = inject(CanchaService);
  private readonly clientesService = inject(ClienteService);
  private readonly snackBar = inject(MatSnackBar);

  /** True until every counter has resolved; drives the spinner. */
  readonly loading = signal(true);
  readonly reservasPendientes = signal(0);
  readonly reservasConfirmadas = signal(0);
  readonly totalReservas = signal(0);
  readonly canchasActivas = signal(0);
  readonly totalClientes = signal(0);

  /** First name for the greeting, falling back to the e-mail. */
  readonly nombre = signal('');

  constructor() {
    // El saludo y los accesos dependen del rol, y el rol llega de /api/auth/me.
    this.auth.asegurarSesion().subscribe({
      next: (sesion) => {
        this.nombre.set(sesion.nombres?.split(' ')[0] ?? sesion.correo);
        this.cargar();
      },
      error: () => this.cargar(),
    });
  }

  private cargar(): void {
    this.loading.set(true);
    // El backend ya filtra: un CLIENTE recibe solo sus propias reservas.
    // Con size=1 alcanza: solo se usa el total de la pagina, no las filas.
    forkJoin({
      total: this.reservasService.list(0, 1),
      pendientes: this.reservasService.list(0, 1, 'PENDIENTE'),
      confirmadas: this.reservasService.list(0, 1, 'CONFIRMADA'),
    }).subscribe({
      next: ({ total, pendientes, confirmadas }) => {
        this.totalReservas.set(total.page.totalElements);
        this.reservasPendientes.set(pendientes.page.totalElements);
        this.reservasConfirmadas.set(confirmadas.page.totalElements);
        this.cargarStaff();
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('No se pudieron cargar los indicadores.', 'Cerrar', { duration: 5000 });
      },
    });
  }

  /** Global counters only make sense (and are only allowed) for staff. */
  private cargarStaff(): void {
    if (this.auth.esCliente()) {
      this.loading.set(false);
      return;
    }
    forkJoin({
      canchas: this.canchasService.list(),
      clientes: this.clientesService.list(0, 1, 'CLIENTE'),
    }).subscribe({
      next: ({ canchas, clientes }) => {
        this.canchasActivas.set(canchas.filter((c) => c.activa).length);
        this.totalClientes.set(clientes.page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('No se pudieron cargar los indicadores.', 'Cerrar', { duration: 5000 });
      },
    });
  }
}
