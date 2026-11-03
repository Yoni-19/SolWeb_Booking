import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CanchaService } from '../canchas/cancha.service';
import { ClienteService } from '../clientes/cliente.service';
import { ReservaService } from '../reservas/reserva.service';
import { PageHeader } from '../../shared/ui/page-header.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, MatCard, MatCardContent, MatIconModule, MatButton, MatChipsModule, PageHeader],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  readonly auth = inject(AuthService);
  private readonly reservasService = inject(ReservaService);
  private readonly canchasService = inject(CanchaService);
  private readonly clientesService = inject(ClienteService);

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
    // El backend ya filtra: un CLIENTE recibe solo sus propias reservas.
    // Con size=1 alcanza: solo se usa el total de la pagina, no las filas.
    this.reservasService.list(0, 1).subscribe((pagina) => {
      this.totalReservas.set(pagina.page.totalElements);
    });
    this.reservasService.list(0, 1, 'PENDIENTE').subscribe((pagina) => {
      this.reservasPendientes.set(pagina.page.totalElements);
    });
    this.reservasService.list(0, 1, 'CONFIRMADA').subscribe((pagina) => {
      this.reservasConfirmadas.set(pagina.page.totalElements);
    });

    // Global counters only make sense (and are only allowed) for staff.
    if (!this.auth.esCliente()) {
      this.canchasService.list().subscribe((canchas) => {
        this.canchasActivas.set(canchas.filter((c) => c.activa).length);
      });
      this.clientesService.list(0, 1).subscribe((pagina) => {
        this.totalClientes.set(pagina.page.totalElements);
      });
    }
  }
}
