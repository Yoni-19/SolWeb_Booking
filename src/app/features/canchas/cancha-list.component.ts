import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Cancha } from '../../core/models/cancha.model';
import { PageHeader } from '../../shared/ui/page-header.component';
import { CanchaService } from './cancha.service';

@Component({
  selector: 'app-cancha-list',
  standalone: true,
  imports: [RouterLink, MatCard, MatCardContent, MatIconModule, MatButtonModule, MatChipsModule, DecimalPipe, PageHeader],
  templateUrl: './cancha-list.component.html',
  styleUrl: './cancha-list.component.scss',
})
export class CanchaListComponent {
  private readonly service = inject(CanchaService);
  private readonly snackBar = inject(MatSnackBar);

  readonly canchas = signal<Cancha[]>([]);

  constructor() {
    this.service.list().subscribe((canchas) => this.canchas.set(canchas));
  }

  alternarEstado(cancha: Cancha): void {
    if (cancha.id == null) return;
    this.service.update({ ...cancha, activa: !cancha.activa }).subscribe((actualizada) => {
      this.canchas.update((list) => list.map((c) => (c.id === actualizada.id ? actualizada : c)));
      this.snackBar.open('Estado actualizado (datos simulados).', 'Cerrar', { duration: 2500 });
    });
  }
}
