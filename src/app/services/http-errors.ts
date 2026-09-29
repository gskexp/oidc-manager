import { HttpErrorResponse } from '@angular/common/http';

/** True when a resource failed because the requested item does not exist. */
export function isNotFound(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 404;
}
