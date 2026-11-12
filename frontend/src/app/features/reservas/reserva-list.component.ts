import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/http/api-error';
import { Pagina } from '../../core/models/pagina.model';
import { EstadoReserva, Reserva } from '../../core/models/reserva.model';
import { AuthService } from '../../core/services/auth.service';
import { PageHeader } from '../../shared/ui/page-header.component';
import { PagoDialogComponent } from '../pagos/pago-dialog.component';
import { PagoService } from '../pagos/pago.service';
import { ReservaService } from './reserva.service';

@Component({
  selector: 'app-reserva-list',
  standalone: true,
  imports: [RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatPaginatorModule, MatProgressSpinnerModule, DatePipe, DecimalPipe, PageHeader],
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
  /** El rol decide las columnas y los botones; el backend igual valida la propiedad. */
  readonly rol = computed(() => this.auth.sesion()?.rol);
  readonly esCliente = computed(() => this.rol() === 'CLIENTE');
  readonly esStaff = computed(() => {
    const rol = this.rol();
    return rol === 'ADMIN' || rol === 'RECEPCIONISTA';
  });
  readonly displayedColumns = computed(() =>
    this.esCliente()
      ? ['id', 'cancha', 'inicio', 'total', 'pagado', 'saldo', 'pago', 'acciones']
      : ['id', 'cancha', 'cliente', 'inicio', 'total', 'pagado', 'saldo', 'estado', 'acciones'],
  );

  constructor() {
    // El authGuard ya cargo la sesion (y el rol) antes de activar esta ruta.
    this.cargar();
  }

  /** Trae una página del backend; `page` es base 0. */
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
        this.snackBar.open(apiErrorMessage(err, 'No se pudieron cargar las reservas'), 'Cerrar', { duration: 4000 });
      },
    });
  }

  cambiarPagina(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.cargar(event.pageIndex);
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

  /** Reprogramar y cancelar solo tienen sentido antes del check-in. */
  puedeEditar(reserva: Reserva): boolean {
    return reserva.estadoReserva === 'PENDIENTE' || reserva.estadoReserva === 'CONFIRMADA';
  }

  /** El CLIENTE solo puede pagar seña o total, una sola vez y desde la web. */
  puedePagar(reserva: Reserva): boolean {
    return this.esCliente() && !reserva.estadoPago && this.puedeEditar(reserva);
  }

  puedeCobrar(reserva: Reserva): boolean {
    return this.esStaff()
      && reserva.saldo > 0
      && reserva.estadoReserva !== 'CANCELADA'
      && reserva.estadoReserva !== 'COMPLETADA';
  }

  puedeIniciar(reserva: Reserva): boolean {
    return this.esStaff() && reserva.estadoReserva === 'CONFIRMADA';
  }

  /** El check-out exige saldo cero; el backend tambien lo valida. */
  puedeFinalizar(reserva: Reserva): boolean {
    return this.esStaff() && reserva.estadoReserva === 'EN_CURSO' && reserva.saldo === 0;
  }

  pagarSenia(reserva: Reserva): void {
    this.pagar(reserva, this.senia(reserva));
  }

  pagarTotal(reserva: Reserva): void {
    this.pagar(reserva, reserva.montoTotal);
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
    this.cambiarEstado(reserva, 'CANCELADA', 'Reserva cancelada.');
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
