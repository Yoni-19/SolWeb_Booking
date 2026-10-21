import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PageHeader } from '../../shared/ui/page-header.component';
import { ClienteService } from './cliente.service';

@Component({
  selector: 'app-cliente-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatCard, MatCardContent, MatInputModule, MatButton, MatIconModule, MatChipsModule, RouterLink, PageHeader],
  templateUrl: './cliente-form.component.html',
  styleUrl: './cliente-form.component.scss',
})
export class ClienteFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ClienteService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  readonly isEdit = signal(false);
  private editingId: number | null = null;

  readonly form = this.fb.nonNullable.group({
    nombres: ['', Validators.required],
    apellidos: ['', Validators.required],
    correo: ['', [Validators.required, Validators.email]],
  });

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEdit.set(true);
      this.editingId = Number(idParam);
      this.service.get(this.editingId).subscribe((cliente) => {
        if (!cliente) return;
        this.form.patchValue({
          nombres: cliente.nombres,
          apellidos: cliente.apellidos,
          correo: cliente.correo,
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
    const request$ = this.isEdit()
      ? this.service.update({ ...value, id: this.editingId })
      : this.service.create(value);

    request$.subscribe(() => {
      this.snackBar.open('Cliente guardado (datos simulados).', 'Cerrar', { duration: 3000 });
      this.router.navigate(['/clientes']);
    });
  }
}
