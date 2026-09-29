import { HttpErrorResponse } from '@angular/common/http';

import { ApiError } from '../auth/auth.models';

/** Página de Spring Data (EnableSpringDataWebSupport VIA_DTO). */
export interface Pagina<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

/** Mensaje para mostrar al usuario a partir de un error del backend. */
export function mensajeError(e: HttpErrorResponse, porDefecto: string): string {
  if (e.status === 0) return 'No hay conexión con el servidor. Intente de nuevo en unos minutos.';
  if (e.status === 403) return 'Su rol no tiene permiso para esta acción.';
  const api = e.error as ApiError | null;
  return api?.mensaje ?? porDefecto;
}

/** Errores por campo (400 VALIDACION), p. ej. { numeroDocumento: 'Ingrese el número de documento' }. */
export function erroresCampos(e: HttpErrorResponse): Record<string, string> {
  const api = e.error as ApiError | null;
  return e.status === 400 && api?.campos ? api.campos : {};
}

/** Código de regla de negocio (422), p. ej. 'POSIBLE_DUPLICADO'. */
export function codigoError(e: HttpErrorResponse): string | null {
  return (e.error as ApiError | null)?.codigo ?? null;
}
