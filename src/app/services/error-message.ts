import { HttpErrorResponse } from '@angular/common/http';

export function isNotFound(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 404;
}

export function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return "Couldn't reach the server. Check your connection and try again.";

    const body = error.error as { detail?: string; errors?: Record<string, string[]> } | null;

    if (body && typeof body === 'object' && !(body instanceof Blob)) {
      const fieldErrors = Object.values(body.errors ?? {}).flat();

      if (fieldErrors.length) return fieldErrors.join(' ');

      if (body.detail) return body.detail;
    }

    return fallback;
  }
  return error instanceof Error && error.message ? error.message : fallback;
}
