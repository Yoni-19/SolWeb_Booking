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
  readonly canchasActivas = signal(0);
  readonly totalClientes = signal(0);

  constructor() {
    this.reservasService.list().subscribe((reservas) => {
      this.reservasPendientes.set(reservas.filter((r) => r.estadoReserva === 'PENDIENTE').length);
      this.reservasConfirmadas.set(reservas.filter((r) => r.estadoReserva === 'CONFIRMADA').length);
    });
    this.canchasService.list().subscribe((canchas) => {
      this.canchasActivas.set(canchas.filter((c) => c.activa).length);
    });
    this.clientesService.list().subscribe((clientes) => {
      this.totalClientes.set(clientes.length);
    });
  }
}
