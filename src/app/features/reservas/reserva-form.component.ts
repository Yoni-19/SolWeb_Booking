import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DateAdapter } from '@angular/material/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Cancha } from '../../core/models/cancha.model';
import { Cliente } from '../../core/models/cliente.model';
import { ESTADOS_RESERVA, Reserva } from '../../core/models/reserva.model';
import { PageHeader } from '../../shared/ui/page-header.component';
import { CanchaService } from '../canchas/cancha.service';
import { ClienteService } from '../clientes/cliente.service';
import { ReservaService } from './reserva.service';

/** Combines a Date (datepicker) and a "HH:mm" string into "yyyy-MM-ddTHH:mm". */
function toLocalDateTime(date: Date, time: string): string {
  const iso = new Date(date).toISOString();
  return `${iso.slice(0, 10)}T${time}`;
}

@Component({
  selector: 'app-reserva-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatCard, MatCardContent, MatInputModule, MatSelectModule, MatDatepickerModule, MatButton, MatIconModule, MatChipsModule, RouterLink, PageHeader],
  templateUrl: './reserva-form.component.html',
  styleUrl: './reserva-form.component.scss',
})
export class ReservaFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ReservaService);
  private readonly canchaService = inject(CanchaService);
  private readonly clienteService = inject(ClienteService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  readonly canchas = signal<Cancha[]>([]);
  readonly clientes = signal<Cliente[]>([]);
  readonly estados = ESTADOS_RESERVA;
  readonly isEdit = signal(false);
  private editingId: number | null = null;

  readonly form = this.fb.nonNullable.group({
    usuarioId: [null as number | null, Validators.required],
    canchaId: [null as number | null, Validators.required],
    fecha: [null as Date | null, Validators.required],
    horaInicio: ['10:00', Validators.required],
    horaFin: ['12:00', [Validators.required]],
    estadoReserva: ['PENDIENTE' as Reserva['estadoReserva'], Validators.required],
  });

  constructor() {
    this.canchaService.list().subscribe((canchas) => this.canchas.set(canchas.filter((c) => c.activa)));
    this.clienteService.list().subscribe((clientes) => this.clientes.set(clientes));

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEdit.set(true);
      this.editingId = Number(idParam);
      this.service.get(this.editingId).subscribe((reserva) => {
        if (!reserva) return;
        this.form.patchValue({
          usuarioId: reserva.usuarioId,
          canchaId: reserva.canchaId,
          fecha: new Date(reserva.fechaHoraInicio),
          horaInicio: reserva.fechaHoraInicio.slice(11, 16),
          horaFin: reserva.fechaHoraFin.slice(11, 16),
          estadoReserva: reserva.estadoReserva,
        });
      });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const cancha = this.canchas().find((c) => c.id === value.canchaId);
    const cliente = this.clientes().find((c) => c.id === value.usuarioId);
    const inicio = toLocalDateTime(value.fecha!, value.horaInicio);
    const fin = toLocalDateTime(value.fecha!, value.horaFin);

    const payload: Omit<Reserva, 'id'> = {
      fechaHoraInicio: inicio,
      fechaHoraFin: fin,
      estadoReserva: value.estadoReserva,
      montoTotal: 0,
      canchaId: value.canchaId,
      canchaNombre: cancha?.nombre,
      usuarioId: value.usuarioId,
      usuarioNombre: cliente ? `${cliente.nombres} ${cliente.apellidos}` : undefined,
    };

    const request$ = this.isEdit()
      ? this.service.update({ ...payload, id: this.editingId })
      : this.service.create(payload);

    request$.subscribe(() => {
      this.snackBar.open('Reserva guardada (datos simulados).', 'Cerrar', { duration: 3000 });
      this.router.navigate(['/reservas']);
    });
  }
}
