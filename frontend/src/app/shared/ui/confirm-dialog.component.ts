import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Destructive actions are highlighted in red. */
  destructive?: boolean;
}

/**
 * Reusable yes/no dialog for destructive or irreversible actions.
 * Returns `true` when the user confirms and `false` otherwise.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title class="confirm-title">
      @if (data.destructive) {
        <mat-icon class="confirm-icon">warning</mat-icon>
      }
      {{ data.title }}
    </h2>
    <mat-dialog-content class="confirm-content">{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">
        {{ data.cancelLabel ?? 'Cancelar' }}
      </button>
      <button
        mat-flat-button
        type="button"
        [mat-dialog-close]="true"
        [color]="data.destructive ? 'warn' : 'primary'"
      >
        {{ data.confirmLabel ?? 'Confirmar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .confirm-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .confirm-icon {
      color: var(--mat-sys-error);
    }
    .confirm-content {
      max-width: 440px;
      line-height: 1.5;
    }
  `,
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
