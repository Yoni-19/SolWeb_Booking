import { CurrencyPipe, DatePipe, formatDate, formatNumber } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
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
import { apiErrorMessage } from '../../core/http/api-error';
import { Pagina } from '../../core/models/pagina.model';
import { ESTADO_RESERVA_LABEL, ESTADOS_RESERVA, EstadoReserva, Reserva } from '../../core/models/reserva.model';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { PageHeader } from '../../shared/ui/page-header.component';
import { PagoDialogComponent } from '../pagos/pago-dialog.component';
import { PagoService } from '../pagos/pago.service';
import { ReservaService } from './reserva.service';

/** Local "yyyy-MM-dd" for the backend date filter (no timezone shift). */
function toLocalDate(date: Date): string {
  const anio = date.getFullYear();
  const mes = String(date.getMonth() + 1).padStart(2, '0');
  const dia = String(date.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

@Component({
  selector: 'app-reserva-list',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, MatTableModule, MatButtonModule, MatIconModule, MatPaginatorModule, MatProgressSpinnerModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule, MatTooltipModule, DatePipe, CurrencyPipe, PageHeader],
  templateUrl: './reserva-list.component.html',
  styleUrl: './reserva-list.component.scss',
})
export class ReservaListComponent {
  private readonly service = inject(ReservaService);
  private readonly pagoService = inject(PagoService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  readonly auth = inject(AuthService);

  /** Página actual devuelta por el backend (content + metadatos). */
  readonly pagina = signal<Pagina<Reserva> | null>(null);
  readonly reservas = computed(() => this.pagina()?.content ?? []);
  readonly total = computed(() => this.pagina()?.page.totalElements ?? 0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly loading = signal(true);
  /** Filtro por estado; vacio = todas. El backend filtra antes de paginar. */
  readonly filtroEstado = signal<EstadoReserva | ''>('');
  /** Filtro por dia de inicio; null = todas las fechas. */
  readonly filtroFecha = new FormControl<Date | null>(null);
  readonly estados = ESTADOS_RESERVA;
  /** El rol decide las columnas y los botones; el backend igual valida la propiedad. */
  readonly rol = computed(() => this.auth.sesion()?.rol);
  readonly esCliente = computed(() => this.rol() === 'CLIENTE');
  readonly esAdmin = computed(() => this.rol() === 'ADMIN');
  readonly esRecepcion = computed(() => this.rol() === 'RECEPCIONISTA');
  /** El ADMIN ve el historial sin columna de acciones: no opera reservas. */
  readonly displayedColumns = computed(() => {
    if (this.esAdmin()) {
      return ['id', 'cancha', 'cliente', 'inicio', 'total', 'pagado', 'saldo', 'estado'];
    }
    return this.esCliente()
      ? ['id', 'cancha', 'inicio', 'total', 'pagado', 'saldo', 'pago', 'acciones']
      : ['id', 'cancha', 'cliente', 'inicio', 'total', 'pagado', 'saldo', 'estado', 'acciones'];
  });

  constructor() {
    // El authGuard ya cargo la sesion (y el rol) antes de activar esta ruta.
    this.filtroFecha.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.cargar(0));
    this.cargar();
  }

  /** Trae una página del backend; `page` es base 0. */
  private cargar(page = 0): void {
    this.loading.set(true);
    const fecha = this.filtroFecha.value;
    this.service.list(
      page,
      this.pageSize(),
      this.filtroEstado() || undefined,
      fecha ? toLocalDate(fecha) : undefined,
    ).subscribe({
      next: (pagina) => {
        this.pagina.set(pagina);
        this.pageIndex.set(pagina.page.number);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.snackBar.open(apiErrorMessage(err, 'No se pudieron cargar las reservas'), 'Cerrar', { duration: 4000 });
      },
    });
  }

  cambiarPagina(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.cargar(event.pageIndex);
  }

  /** Al cambiar el filtro se vuelve a la primera pagina. */
  cambiarFiltro(estado: EstadoReserva | ''): void {
    this.filtroEstado.set(estado);
    this.cargar(0);
  }

  /** True when any filter is active (drives the empty-state text and the clear button). */
  hayFiltros(): boolean {
    return this.filtroEstado() !== '' || this.filtroFecha.value !== null;
  }

  limpiarFiltros(): void {
    this.filtroEstado.set('');
    this.filtroFecha.setValue(null, { emitEvent: false });
    this.cargar(0);
  }

  etiquetaEstado(estado: EstadoReserva): string {
    return ESTADO_RESERVA_LABEL[estado];
  }

  /** La seña es el 50% del total, igual que la valida el backend. */
  senia(reserva: Reserva): number {
    return Math.round(reserva.montoTotal * 50) / 100;
  }

  etiquetaPago(reserva: Reserva): string {
    if (!reserva.estadoPago) return 'Sin pago';
    if (reserva.estadoPago === 'PARCIAL') return this.esCliente() ? 'Seña 50%' : 'Parcial';
    if (reserva.estadoPago === 'PAGADO') return 'Pagado';
    return 'Reembolsado';
  }

  /** Reprogramar y cancelar solo tienen sentido antes del check-in. El ADMIN es solo lectura. */
  puedeEditar(reserva: Reserva): boolean {
    return !this.esAdmin()
      && (reserva.estadoReserva === 'PENDIENTE' || reserva.estadoReserva === 'CONFIRMADA');
  }

  /** El CLIENTE solo puede pagar seña o total, una sola vez y desde la web. */
  puedePagar(reserva: Reserva): boolean {
    return this.esCliente() && !reserva.estadoPago && this.puedeEditar(reserva);
  }

  /**
   * Recepcion cobra la seña de una reserva PENDIENTE y el saldo de una EN_CURSO.
   * En CONFIRMADA solo se muestra "Iniciar": nunca se solapan ambos botones.
   */
  puedeCobrar(reserva: Reserva): boolean {
    return this.esRecepcion()
      && reserva.saldo > 0
      && (reserva.estadoReserva === 'PENDIENTE' || reserva.estadoReserva === 'EN_CURSO');
  }

  /** El check-in solo se habilita el mismo dia y antes de que termine la reserva. */
  puedeIniciar(reserva: Reserva): boolean {
    return this.esRecepcion()
      && reserva.estadoReserva === 'CONFIRMADA'
      && reserva.fechaHoraInicio.slice(0, 10) === toLocalDate(new Date())
      && new Date(reserva.fechaHoraFin).getTime() > Date.now();
  }

  /** CONFIRMADA de una fecha futura: se muestra deshabilitada hasta ese dia. */
  iniciarDeshabilitado(reserva: Reserva): boolean {
    return this.esRecepcion()
      && reserva.estadoReserva === 'CONFIRMADA'
      && reserva.fechaHoraInicio.slice(0, 10) > toLocalDate(new Date());
  }

  /** El check-out exige saldo cero; el backend tambien lo valida. */
  puedeFinalizar(reserva: Reserva): boolean {
    return this.esRecepcion() && reserva.estadoReserva === 'EN_CURSO' && reserva.saldo === 0;
  }

  pagarSenia(reserva: Reserva): void {
    this.confirmarPago(reserva, this.senia(reserva));
  }

  pagarTotal(reserva: Reserva): void {
    this.confirmarPago(reserva, reserva.montoTotal);
  }

  private confirmarPago(reserva: Reserva, monto: number): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Registrar pago',
          message: `¿Registrar un pago de $ ${formatNumber(monto, 'es-AR', '1.2-2')} para la reserva #${reserva.id} con tarjeta (simulado)?`,
          confirmLabel: 'Pagar',
        },
        width: '420px',
      })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) this.pagar(reserva, monto);
      });
  }

  private pagar(reserva: Reserva, monto: number): void {
    if (reserva.id == null) return;
    this.pagoService.create({ reservaId: reserva.id, monto, metodoPago: 'TARJETA' }).subscribe({
      next: () => {
        this.cargar(this.pageIndex());
        this.snackBar.open('Pago simulado registrado.', 'Cerrar', { duration: 3000 });
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'No se pudo registrar el pago'), 'Cerrar', { duration: 5000 });
      },
    });
  }

  abrirPago(reserva: Reserva): void {
    const ref = this.dialog.open(PagoDialogComponent, {
      data: { reserva },
      width: '440px',
      autoFocus: 'first-tabbable',
    });
    ref.afterClosed().subscribe((registrado) => {
      if (registrado) {
        this.cargar(this.pageIndex());
        this.snackBar.open('Pago registrado.', 'Cerrar', { duration: 2500 });
      }
    });
  }

  iniciar(reserva: Reserva): void {
    this.cambiarEstado(reserva, 'EN_CURSO', 'Check-in registrado. Reserva en curso.');
  }

  finalizar(reserva: Reserva): void {
    this.cambiarEstado(reserva, 'COMPLETADA', 'Check-out registrado. Reserva completada.');
  }

  cancelar(reserva: Reserva): void {
    if (reserva.id == null) return;
    const mensaje = this.esCliente()
      ? '¿Estás seguro de cancelar esta reserva? El reembolso del anticipo pagado (50%) deberá coordinarse directamente en recepción según las políticas del complejo.'
      : `¿Cancelar la reserva #${reserva.id} de ${reserva.canchaNombre}? Esta acción no se puede deshacer.`;
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Cancelar reserva',
          message: mensaje,
          confirmLabel: 'Cancelar reserva',
          cancelLabel: 'Volver',
          destructive: true,
        },
        width: '420px',
      })
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.cambiarEstado(reserva, 'CANCELADA', 'Reserva cancelada.');
        }
      });
  }

  /** Descarga como PDF el historial visible (boton exclusivo del ADMIN). */
  async descargarPdf(): Promise<void> {
    const { jsPDF } = await import('jspdf');
    const { default: autoTable } = await import('jspdf-autotable');

    const doc = new jsPDF();
    doc.setFontSize(16);
    doc.text('Historial de reservas', 14, 18);
    doc.setFontSize(10);
    doc.text(`Generado: ${formatDate(new Date(), 'dd/MM/yyyy HH:mm', 'es-AR')}`, 14, 25);

    autoTable(doc, {
      startY: 31,
      head: [['Cancha', 'Cliente', 'Fecha / Horario', 'Estado', 'Monto']],
      body: this.reservas().map((r) => [
        r.canchaNombre ?? `#${r.canchaId}`,
        r.usuarioNombre ?? `#${r.usuarioId}`,
        `${formatDate(r.fechaHoraInicio, 'dd/MM/yy HH:mm', 'es-AR')} - ${formatDate(r.fechaHoraFin, 'HH:mm', 'es-AR')}`,
        ESTADO_RESERVA_LABEL[r.estadoReserva],
        `$ ${formatNumber(r.montoTotal, 'es-AR', '1.2-2')}`,
      ]),
      styles: { fontSize: 9 },
      headStyles: { fillColor: [63, 81, 181] },
    });

    doc.save('historial-reservas.pdf');
  }

  private cambiarEstado(reserva: Reserva, estado: EstadoReserva, mensaje: string): void {
    if (reserva.id == null) return;
    this.service.cambiarEstado(reserva.id, estado).subscribe({
      next: () => {
        this.cargar(this.pageIndex());
        this.snackBar.open(mensaje, 'Cerrar', { duration: 2500 });
      },
      error: (err) => {
        this.snackBar.open(apiErrorMessage(err, 'No se pudo cambiar el estado'), 'Cerrar', { duration: 5000 });
      },
    });
  }
}
