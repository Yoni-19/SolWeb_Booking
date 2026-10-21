import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Reserva } from '../../core/models/reserva.model';
import { PageHeader } from '../../shared/ui/page-header.component';
import { ReservaService } from './reserva.service';

@Component({
  selector: 'app-reserva-list',
  standalone: true,
  imports: [RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatChipsModule, DatePipe, DecimalPipe, PageHeader],
  templateUrl: './reserva-list.component.html',
  styleUrl: './reserva-list.component.scss',
})
export class ReservaListComponent {
  private readonly service = inject(ReservaService);

  readonly reservas = signal<Reserva[]>([]);
  readonly displayedColumns = ['id', 'cancha', 'cliente', 'inicio', 'estado', 'monto', 'acciones'];

  constructor() {
    this.service.list().subscribe((reservas) => this.reservas.set(reservas));
  }

  eliminar(reserva: Reserva): void {
    if (reserva.id == null) return;
    this.service.remove(reserva.id).subscribe(() => {
      this.reservas.update((list) => list.filter((r) => r.id !== reserva.id));
    });
  }
}
