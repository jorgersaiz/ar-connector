import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

/** Extrae el cuerpo `{message, errors, problems}` de un error HTTP (o lo aproxima). */
export function apiError(err: unknown): Required<ApiError> & { status: number } {
  if (err instanceof HttpErrorResponse) {
    const body = (err.error && typeof err.error === 'object' ? err.error : {}) as ApiError;
    return {
      status: err.status,
      message: body.message ?? (typeof err.error === 'string' && err.error ? err.error : defaultMessage(err.status)),
      errors: body.errors ?? [],
      problems: body.problems ?? [],
    };
  }
  return { status: 0, message: String(err ?? 'Error desconocido'), errors: [], problems: [] };
}

function defaultMessage(status: number): string {
  switch (status) {
    case 0: return 'No se pudo contactar con el servidor';
    case 401: return 'Sesión caducada';
    case 403: return 'Sin permiso para esta operación';
    case 404: return 'No encontrado';
    case 409: return 'Conflicto con datos existentes';
    case 500: return 'Error interno del servidor';
    default: return `Error HTTP ${status}`;
  }
}

/** Lista plana de mensajes para pintar (message + errors + problems). */
export function errorLines(err: unknown): string[] {
  const e = apiError(err);
  const lines: string[] = [];
  if (e.message) lines.push(e.message);
  for (const s of e.errors) lines.push(s);
  for (const p of e.problems) {
    const where = [p.uid, p.date].filter(Boolean).join(' ');
    lines.push(`${where ? where + ': ' : ''}${p.message}${p.code ? ` (${p.code})` : ''}`);
  }
  return lines;
}

/** Texto multilínea para un MatSnackBar. */
export function errorText(err: unknown): string {
  const lines = errorLines(err);
  return lines.length ? lines.join('\n') : 'Error desconocido';
}
