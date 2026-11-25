import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { map } from 'rxjs';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';

/** A routed component that can report whether it has unsaved edits. */
export interface ComponenteConCambios {
  tieneCambiosSinGuardar(): boolean;
}

/**
 * Bloquea la navegación cuando el formulario tiene cambios sin guardar y pide
 * confirmación con el diálogo de la propia aplicación, no el nativo del navegador.
 * Cerrar el diálogo sin elegir equivale a quedarse en el formulario.
 */
export const unsavedChangesGuard: CanDeactivateFn<ComponenteConCambios> = (component) => {
  if (!component?.tieneCambiosSinGuardar?.()) {
    return true;
  }
  return inject(MatDialog)
    .open<ConfirmDialogComponent, unknown, boolean>(ConfirmDialogComponent, {
      data: {
        title: 'Cambios sin guardar',
        message: 'Si salís ahora, se perderán los datos ingresados.',
        confirmLabel: 'Salir',
        cancelLabel: 'Seguir editando',
        destructive: true,
      },
      autoFocus: 'dialog',
    })
    .afterClosed()
    .pipe(map((confirmado) => confirmado === true));
};
