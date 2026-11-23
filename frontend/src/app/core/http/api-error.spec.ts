import { HttpErrorResponse } from '@angular/common/http';
import { apiErrorMessage } from './api-error';

function httpError(status: number, body: unknown = null): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('apiErrorMessage', () => {
  it('returns the fallback for a non-HTTP error', () => {
    expect(apiErrorMessage(new Error('boom'), 'Fallback')).toBe('Fallback');
  });

  it('returns the connection message for status 0', () => {
    expect(apiErrorMessage(httpError(0))).toBe('No se pudo conectar con el servidor');
  });

  it('joins field-level validation details', () => {
    const error = httpError(400, { details: { correo: 'Correo inválido', clave: 'Mínimo 8' } });
    expect(apiErrorMessage(error)).toBe('Correo inválido · Mínimo 8');
  });

  it('prefers the envelope message over the error field', () => {
    expect(apiErrorMessage(httpError(409, { message: 'Solapamiento', error: 'Conflict' }))).toBe('Solapamiento');
  });

  it('falls back to the error field when there is no message', () => {
    expect(apiErrorMessage(httpError(500, { error: 'Internal Server Error' }))).toBe('Internal Server Error');
  });

  it('uses the custom fallback when the body has nothing useful', () => {
    expect(apiErrorMessage(httpError(500, null), 'Custom')).toBe('Custom');
  });
});
