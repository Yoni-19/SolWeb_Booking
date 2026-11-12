import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/http/api-error';
import {
  PERSONAL_ROLES,
  ROL_LABEL,
  RolPersonal,
} from '../../core/models/cliente.model';
import { PageHeader } from '../../shared/ui/page-header.component';
import { PersonalService } from './personal.service';

@Component({
  selector: 'app-personal-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatCard, MatCardContent, MatInputModule, MatButton, MatIconModule, MatSelectModule, MatProgressSpinnerModule, RouterLink, PageHeader],
  templateUrl: './personal-form.component.html',
  styleUrl: './personal-form.component.scss',
})
export class PersonalFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PersonalService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  readonly isEdit = signal(false);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly roles = PERSONAL_ROLES;
  readonly etiquetasRol = ROL_LABEL;
  private editingId: number | null = null;
  /** Evita guardar si por URL se abrió un usuario que no es del personal. */
  private usuarioInvalido = false;

  readonly form = this.fb.nonNullable.group({
    nombres: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    apellidos: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(100)]],
    clave: ['', [Validators.minLength(2), Validators.maxLength(100)]],
    rol: ['RECEPCIONISTA' as RolPersonal, Validators.required],
  });

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEdit.set(true);
      this.editingId = Number(idParam);
      this.service.get(this.editingId).subscribe({
        next: (usuario) => {
          if (!usuario) {
            this.errorMessage.set('No existe el trabajador solicitado.');
            this.usuarioInvalido = true;
            return;
          }
          if (usuario.rol === 'CLIENTE') {
            this.errorMessage.set('Ese usuario es un cliente, no pertenece al personal.');
            this.usuarioInvalido = true;
            return;
          }
          this.form.patchValue({
            nombres: usuario.nombres,
            apellidos: usuario.apellidos,
            correo: usuario.correo,
            rol: (usuario.rol ?? 'RECEPCIONISTA') as RolPersonal,
          });
        },
        error: (err) => {
          this.errorMessage.set(apiErrorMessage(err, 'No se pudo cargar el trabajador'));
        },
      });
    } else {
      // El alta de personal exige clave; en edición es opcional.
      this.form.controls.clave.setValidators([
        Validators.required,
        Validators.minLength(2),
        Validators.maxLength(100),
      ]);
      this.form.controls.clave.updateValueAndValidity();
    }
  }

  submit(): void {
    if (this.usuarioInvalido) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { nombres, apellidos, correo, clave, rol } = this.form.getRawValue();
    const request$ = this.isEdit()
      ? this.service.update({ id: this.editingId, nombres, apellidos, correo }, clave)
      : this.service.create({ nombres, apellidos, correo, clave, rol });

    request$.subscribe({
      next: () => {
        this.loading.set(false);
        this.snackBar.open('Personal guardado.', 'Cerrar', { duration: 3000 });
        this.router.navigate(['/personal']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(apiErrorMessage(err, 'No se pudo guardar el trabajador'));
      },
    });
  }
}
