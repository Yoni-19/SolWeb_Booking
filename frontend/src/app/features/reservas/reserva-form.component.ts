import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, merge } from 'rxjs';
import { apiErrorMessage } from '../../core/http/api-error';
import { Cancha } from '../../core/models/cancha.model';
import { Cliente } from '../../core/models/cliente.model';
import { MetodoPago } from '../../core/models/pago.model';
import { ESTADO_RESERVA_LABEL, EstadoReserva, FranjaOcupada, ReservaAlta } from '../../core/models/reserva.model';
import { AuthService } from '../../core/services/auth.service';
import { PageHeader } from '../../shared/ui/page-header.component';
import { CanchaService } from '../canchas/cancha.service';
import { ClienteService } from '../clientes/cliente.service';
import { PagoService } from '../pagos/pago.service';
import { ReservaService } from './reserva.service';

/** Pago que se puede registrar al crear la reserva (el saldo queda para despues). */
type PagoInicial = 'DESPUES' | 'SENIA' | 'TOTAL';

/** Builds "yyyy-MM-ddTHH:mm" from the LOCAL date, so no timezone shift is applied. */
function toLocalDateTime(date: Date, time: string): string {
  const anio = date.getFullYear();
  const mes = String(date.getMonth() + 1).padStart(2, '0');
  const dia = String(date.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}T${time}`;
}

/** "yyyy-MM-dd" from the LOCAL date, for the availability query. */
function toLocalDate(date: Date): string {
  return toLocalDateTime(date, '00:00').slice(0, 10);
}

/** Minutes since midnight for an "HH:mm" string (null if malformed). */
function minutosDe(hora: string): number | null {
  const [h, m] = hora.split(':').map(Number);
  return Number.isNaN(h) || Number.isNaN(m) ? null : h * 60 + m;
}

/** "HH:mm" from the ISO local date-time returned by the backend. */
function horaDe(fechaIso: string): string {
  return fechaIso.slice(11, 16);
}

/** Duration in minutes between two "HH:mm" values. */
function minutosEntre(inicio: string, fin: string): number | null {
  const desde = minutosDe(inicio);
  const hasta = minutosDe(fin);
  return desde == null || hasta == null ? null : hasta - desde;
}

interface ResumenReserva {
  horas: number;
  precioHora: number;
  total: number;
  senia: number;
}

/** Una franja de una hora en la grilla de disponibilidad. */
interface SlotHorario {
  hora: string;
  fin: string;
  ocupado: boolean;
  seleccionado: boolean;
}

@Component({
  selector: 'app-reserva-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatCard, MatCardContent, MatInputModule, MatSelectModule, MatDatepickerModule, MatButton, MatIconModule, MatChipsModule, MatProgressSpinnerModule, MatTooltipModule, CurrencyPipe, RouterLink, PageHeader],
  templateUrl: './reserva-form.component.html',
  styleUrl: './reserva-form.component.scss',
})
export class ReservaFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ReservaService);
  private readonly pagoService = inject(PagoService);
  private readonly canchaService = inject(CanchaService);
  private readonly clienteService = inject(ClienteService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);
  readonly auth = inject(AuthService);

  readonly canchas = signal<Cancha[]>([]);
  readonly clientes = signal<Cliente[]>([]);
  readonly isEdit = signal(false);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  /** Ventana de atencion del complejo: misma que valida el backend. */
  readonly HORA_APERTURA = 8;
  readonly HORA_CIERRE = 22;
  /** Las reservas no pueden empezar antes de hoy. */
  readonly minFecha = (() => {
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    return hoy;
  })();
  /** Estado actual de la reserva; el backend lo calcula y no se puede enviar. */
  readonly estadoActual = signal<EstadoReserva | null>(null);
  /** Franjas ya tomadas de la cancha elegida en el dia elegido. */
  readonly franjas = signal<FranjaOcupada[]>([]);
  readonly cargandoDisponibilidad = signal(false);
  /** Aviso cuando el horario elegido se pisa con una reserva existente. */
  readonly conflicto = signal<string | null>(null);
  /** Duracion, total y seña calculados en vivo (misma regla que el backend). */
  readonly resumen = signal<ResumenReserva | null>(null);
  private editingId: number | null = null;
  /** True once the form was saved, so the unsaved-changes guard lets it leave. */
  private guardado = false;

  readonly form = this.fb.nonNullable.group({
    // El cliente no elige usuario: el backend lo fuerza al usuario autenticado.
    usuarioId: [null as number | null],
    canchaId: [null as number | null, Validators.required],
    fecha: [null as Date | null, Validators.required],
    horaInicio: ['10:00', Validators.required],
    horaFin: ['12:00', Validators.required],
    // Solo se usa al crear: la reserva nace PENDIENTE hasta que entra la seña.
    pagoInicial: ['DESPUES' as PagoInicial],
    metodoPago: ['EFECTIVO' as MetodoPago],
  });

  constructor() {
    this.canchaService.list().subscribe({
      next: (canchas) => {
        this.canchas.set(canchas.filter((c) => c.activa));
        this.recalcular();
      },
      error: (err) => this.errorMessage.set(apiErrorMessage(err, 'No se pudieron cargar las canchas')),
    });

    // El resumen (total y seña) se recalcula con cualquier cambio del formulario.
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.recalcular());

    // La disponibilidad solo se consulta cuando cambia la cancha o la fecha.
    merge(this.form.controls.canchaId.valueChanges, this.form.controls.fecha.valueChanges)
      .pipe(debounceTime(200), takeUntilDestroyed())
      .subscribe(() => this.cargarDisponibilidad());

    // El rol define si hace falta elegir el cliente; el rol llega de /api/auth/me.
    this.auth.asegurarSesion().subscribe({
      next: () => this.configurarPorRol(),
      error: () => this.configurarPorRol(),
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEdit.set(true);
      this.editingId = Number(idParam);
      this.service.get(this.editingId).subscribe({
        next: (reserva) => {
          if (!reserva) {
            this.errorMessage.set('No existe la reserva solicitada.');
            return;
          }
          this.estadoActual.set(reserva.estadoReserva);
          this.form.patchValue({
            usuarioId: reserva.usuarioId,
            canchaId: reserva.canchaId,
            fecha: new Date(reserva.fechaHoraInicio),
            horaInicio: reserva.fechaHoraInicio.slice(11, 16),
            horaFin: reserva.fechaHoraFin.slice(11, 16),
          });
          // El backend ignora el usuario al actualizar: se muestra bloqueado.
          this.form.controls.usuarioId.disable();
        },
        error: (err) => {
          this.errorMessage.set(apiErrorMessage(err, 'No se pudo cargar la reserva'));
        },
      });
    }
  }

  /** Staff picks the client; a CLIENTE books for itself (no client list to load). */
  private configurarPorRol(): void {
    const usuarioId = this.form.controls.usuarioId;

    if (this.auth.esCliente()) {
      usuarioId.clearValidators();
    } else {
      usuarioId.setValidators(Validators.required);
      this.clienteService.listAll().subscribe({
        next: (clientes) => this.clientes.set(clientes),
        error: (err) => this.errorMessage.set(apiErrorMessage(err, 'No se pudieron cargar los clientes')),
      });
    }
    usuarioId.updateValueAndValidity({ emitEvent: false });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { usuarioId, canchaId, fecha, horaInicio, horaFin, pagoInicial, metodoPago } =
      this.form.getRawValue();
    if (canchaId == null || fecha == null || (!this.auth.esCliente() && usuarioId == null)) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);

    if (horaFin <= horaInicio) {
      this.errorMessage.set('La hora de fin debe ser posterior a la de inicio');
      return;
    }

    const inicioMin = minutosDe(horaInicio) ?? 0;
    const finMin = minutosDe(horaFin) ?? 0;
    if (inicioMin < this.HORA_APERTURA * 60 || finMin > this.HORA_CIERRE * 60) {
      this.errorMessage.set('El horario de atención es de 08:00 a 22:00');
      return;
    }

    if (fecha < this.minFecha) {
      this.errorMessage.set('La fecha no puede ser anterior a hoy');
      return;
    }

    if (this.conflicto()) {
      return;
    }

    this.loading.set(true);

    const payload: ReservaAlta = {
      fechaHoraInicio: toLocalDateTime(fecha, horaInicio),
      fechaHoraFin: toLocalDateTime(fecha, horaFin),
      canchaId,
    };
    if (usuarioId != null) {
      payload.usuarioId = usuarioId;
    }

    const request$ = this.isEdit()
      ? this.service.update(this.editingId as number, payload)
      : this.service.create(payload);

    request$.subscribe({
      next: (reserva) => {
        if (this.isEdit() || pagoInicial === 'DESPUES' || reserva.id == null) {
          this.finalizar(
            this.isEdit()
              ? 'Reserva actualizada.'
              : 'Reserva registrada. Queda pendiente hasta registrar la seña.',
          );
          return;
        }

        // La seña es el 50% del total; el cliente paga simulado con tarjeta.
        const monto = pagoInicial === 'SENIA'
          ? Math.round(reserva.montoTotal * 50) / 100
          : reserva.montoTotal;

        this.pagoService.create({
          reservaId: reserva.id,
          monto,
          metodoPago: this.auth.esCliente() ? 'TARJETA' : metodoPago,
        }).subscribe({
          next: () => this.finalizar('Reserva creada con pago registrado.'),
          error: (err) => this.finalizar(
            `Reserva creada sin pago: ${apiErrorMessage(err, 'no se pudo registrar el pago')}. `
              + 'Reintentá desde Mis reservas.',
          ),
        });
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(apiErrorMessage(err, 'No se pudo guardar la reserva'));
      },
    });
  }

  /** Etiqueta legible del estado para el modo edicion. */
  etiquetaEstado(estado: EstadoReserva | null): string {
    return estado ? ESTADO_RESERVA_LABEL[estado] : ESTADO_RESERVA_LABEL.PENDIENTE;
  }

  /** Used by unsavedChangesGuard. */
  tieneCambiosSinGuardar(): boolean {
    return this.form.dirty && !this.guardado;
  }

  private finalizar(mensaje: string): void {
    this.loading.set(false);
    this.guardado = true;
    this.snackBar.open(mensaje, 'Cerrar', { duration: 5000 });
    this.router.navigate(['/reservas']);
  }

  /** Recalcula el resumen de precio y el aviso de solapamiento. */
  private recalcular(): void {
    this.actualizarResumen();
    this.detectarConflicto();
  }

  private actualizarResumen(): void {
    const { canchaId, horaInicio, horaFin } = this.form.getRawValue();
    const cancha = this.canchas().find((c) => c.id === canchaId);
    const minutos = minutosEntre(horaInicio, horaFin);
    if (!cancha || minutos == null || minutos <= 0) {
      this.resumen.set(null);
      return;
    }
    // Monto por hora iniciada, igual que el backend: (minutos + 59) / 60.
    const horas = Math.ceil(minutos / 60);
    const total = horas * cancha.precioPorHora;
    this.resumen.set({
      horas,
      precioHora: cancha.precioPorHora,
      total,
      senia: Math.round(total * 50) / 100,
    });
  }

  private detectarConflicto(): void {
    const choque = this.franjas().find((f) => this.chocaCon(f));
    this.conflicto.set(
      choque
        ? `El horario se superpone con una reserva de ${horaDe(choque.inicio)} a ${horaDe(choque.fin)}. Elegí otro horario.`
        : null,
    );
  }

  /** True if the chosen hours overlap with the taken slot (the edited row is ignored). */
  chocaCon(franja: FranjaOcupada): boolean {
    if (this.isEdit() && franja.id === this.editingId) return false;
    const { horaInicio, horaFin } = this.form.getRawValue();
    const inicio = minutosDe(horaInicio);
    const fin = minutosDe(horaFin);
    const fInicio = minutosDe(horaDe(franja.inicio));
    const fFin = minutosDe(horaDe(franja.fin));
    return inicio != null && fin != null && fInicio != null && fFin != null
      && inicio < fFin && fin > fInicio;
  }

  /** Grilla de franjas de una hora entre la apertura y el cierre. */
  slots(): SlotHorario[] {
    const { horaInicio } = this.form.getRawValue();
    return Array.from({ length: this.HORA_CIERRE - this.HORA_APERTURA }, (_, i) => {
      const hora = `${String(this.HORA_APERTURA + i).padStart(2, '0')}:00`;
      const fin = `${String(this.HORA_APERTURA + i + 1).padStart(2, '0')}:00`;
      return { hora, fin, ocupado: this.slotOcupado(this.HORA_APERTURA + i), seleccionado: horaInicio === hora };
    });
  }

  /** Un clic en una franja libre completa el rango; si toca el fin actual, lo extiende. */
  elegirSlot(slot: SlotHorario): void {
    if (slot.ocupado) return;
    const { horaFin } = this.form.getRawValue();
    if (horaFin === slot.hora) {
      this.form.patchValue({ horaFin: slot.fin });
      return;
    }
    this.form.patchValue({ horaInicio: slot.hora, horaFin: slot.fin });
  }

  private slotOcupado(hora: number): boolean {
    return this.franjas().some((f) => {
      if (this.isEdit() && f.id === this.editingId) return false;
      const inicio = minutosDe(horaDe(f.inicio));
      const fin = minutosDe(horaDe(f.fin));
      return inicio != null && fin != null && inicio < (hora + 1) * 60 && fin > hora * 60;
    });
  }

  private cargarDisponibilidad(): void {
    const { canchaId, fecha } = this.form.getRawValue();
    if (canchaId == null || fecha == null) {
      this.franjas.set([]);
      this.cargandoDisponibilidad.set(false);
      this.recalcular();
      return;
    }
    this.cargandoDisponibilidad.set(true);
    this.service.disponibilidad(canchaId, toLocalDate(fecha)).subscribe({
      next: (franjas) => {
        this.franjas.set(franjas);
        this.cargandoDisponibilidad.set(false);
        this.recalcular();
      },
      error: () => {
        this.franjas.set([]);
        this.cargandoDisponibilidad.set(false);
        this.recalcular();
      },
    });
  }
}
