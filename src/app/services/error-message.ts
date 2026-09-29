import { HttpErrorResponse } from '@angular/common/http';

/**
 * A message to show for a failed request: field errors or the server's safe detail when present,
 * otherwise a generic message. Never includes technical details.
 */
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
