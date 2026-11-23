import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { apiErrorMessage } from '../../core/http/api-error';
import { MetodoPago } from '../../core/models/pago.model';
import { Reserva } from '../../core/models/reserva.model';
import { PagoService } from './pago.service';

export interface PagoDialogData {
  reserva: Reserva;
}

/**
 * Staff dialog to register a payment. The amount is free (minimum the 50%
 * seña on the first payment) and can never exceed the pending balance; the
 * backend enforces both rules again.
 */
@Component({
  selector: 'app-pago-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    CurrencyPipe,
  ],
  templateUrl: './pago-dialog.component.html',
  styleUrl: './pago-dialog.component.scss',
})
export class PagoDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly ref = inject(MatDialogRef<PagoDialogComponent, boolean>);
  private readonly pagoService = inject(PagoService);
  readonly data = inject<PagoDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly esPrimerPago = !this.data.reserva.estadoPago;
  readonly senia = Math.round(this.data.reserva.montoTotal * 50) / 100;
  readonly saldo = this.data.reserva.saldo;

  readonly form = this.fb.nonNullable.group({
    monto: [this.saldo, [Validators.required, Validators.min(0.01)]],
    metodoPago: ['EFECTIVO' as MetodoPago, Validators.required],
  });

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const reservaId = this.data.reserva.id;
    if (reservaId == null) {
      return;
    }

    const { monto, metodoPago } = this.form.getRawValue();
    if (monto > this.saldo) {
      this.errorMessage.set('El monto no puede superar el saldo pendiente.');
      return;
    }
    if (this.esPrimerPago && monto < this.senia) {
      this.errorMessage.set(`La primera entrega debe cubrir al menos la seña del 50% (${this.senia}).`);
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.pagoService.create({ reservaId, monto, metodoPago }).subscribe({
      next: () => {
        this.loading.set(false);
        this.ref.close(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(apiErrorMessage(err, 'No se pudo registrar el pago'));
      },
    });
  }
}
