import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Cliente } from '../../core/models/cliente.model';
import { PageHeader } from '../../shared/ui/page-header.component';
import { ClienteService } from './cliente.service';

@Component({
  selector: 'app-cliente-list',
  standalone: true,
  imports: [RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatChipsModule, PageHeader],
  templateUrl: './cliente-list.component.html',
  styleUrl: './cliente-list.component.scss',
})
export class ClienteListComponent {
  private readonly service = inject(ClienteService);

  readonly clientes = signal<Cliente[]>([]);
  readonly displayedColumns = ['id', 'nombre', 'correo', 'acciones'];

  constructor() {
    this.service.list().subscribe((clientes) => this.clientes.set(clientes));
  }

  eliminar(cliente: Cliente): void {
    if (cliente.id == null) return;
    this.service.remove(cliente.id).subscribe(() => {
      this.clientes.update((list) => list.filter((c) => c.id !== cliente.id));
    });
  }
}
