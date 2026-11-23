import { CanDeactivateFn } from '@angular/router';

/** A routed component that can report whether it has unsaved edits. */
export interface ComponenteConCambios {
  tieneCambiosSinGuardar(): boolean;
}

/**
 * Blocks navigation away from a form with unsaved edits and asks the user.
 * Uses the native confirm so it works without extra providers.
 */
export const unsavedChangesGuard: CanDeactivateFn<ComponenteConCambios> = (component) => {
  if (!component?.tieneCambiosSinGuardar?.()) {
    return true;
  }
  return window.confirm('Tenés cambios sin guardar. ¿Querés salir igual?');
};
