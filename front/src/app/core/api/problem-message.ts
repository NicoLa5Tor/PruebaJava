import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblemDetails } from './api-problem-details';

export function problemMessage(error: unknown, fallback = 'Ocurrió un error inesperado. Intenta de nuevo.'): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (error.status === 0) {
    return 'No fue posible conectar con el servidor.';
  }
  if (error.status === 403) {
    return 'No tienes permiso para realizar esta acción.';
  }
  const problem = error.error as ApiProblemDetails | null;
  if (problem?.errors?.length) {
    return problem.errors.map((violation) => `${violation.field}: ${violation.message}`).join(' · ');
  }
  return typeof problem?.detail === 'string' ? problem.detail : fallback;
}
