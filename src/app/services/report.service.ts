import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, delay, from, map, of, switchMap, throwError, timer } from 'rxjs';
import { ReportExport, ReportRunRequest, ReportRunResponse } from '../models';
import { API_SETTINGS } from './api-settings';
import { ApiUrls } from './api-urls';
import { isNotFound } from './error-message';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly urls = inject(ApiUrls);
  private readonly settings = inject(API_SETTINGS);

  readonly canExport = this.urls.mode === 'api';

  runReport(request: ReportRunRequest): Observable<ReportRunResponse> {
    if (this.urls.mode === 'api') {
      return this.http.post<ReportRunResponse>(this.urls.runReport(request.reportConfigId), request);
    }
    return this.mockRun(request);
  }

  exportReport(request: ReportRunRequest): Observable<ReportExport> {
    if (this.urls.mode !== 'api') {
      return throwError(() => new Error('Excel export needs the backend (API mode).'));
    }
    return this.http
      .post(this.urls.exportReport(request.reportConfigId), request, {
        observe: 'response',
        responseType: 'blob',
      })
      .pipe(
        map((response) => ({
          blob: response.body ?? new Blob(),
          fileName: fileNameFrom(response.headers.get('Content-Disposition')) ?? `${request.reportConfigId}.xlsx`,
        })),
        catchError(parseBlobError),
      );
  }

  private mockRun(request: ReportRunRequest): Observable<ReportRunResponse> {
    const { runDelayMs = 800, failingReportConfigIds = [] } = this.settings.mock ?? {};
    if (failingReportConfigIds.includes(request.reportConfigId)) {
      return timer(runDelayMs).pipe(switchMap(() => throwError(() => new Error('Simulated failure (mock mode).'))));
    }
    const empty = { columns: [], rows: [], rowCount: 0, truncated: false };
    return this.http.get<Partial<ReportRunResponse>>(this.urls.mockRunResult(request.reportConfigId)).pipe(
      catchError((error) => (isNotFound(error) ? of({}) : throwError(() => error))),
      map((result): ReportRunResponse => ({ ...empty, ...result, runId: crypto.randomUUID(), status: 'completed' })),
      delay(runDelayMs),
    );
  }
}

function parseBlobError(error: unknown): Observable<never> {
  if (!(error instanceof HttpErrorResponse && error.error instanceof Blob)) {
    return throwError(() => error);
  }
  return from(error.error.text()).pipe(
    switchMap((text) =>
      throwError(
        () =>
          new HttpErrorResponse({
            error: parseJson(text),
            headers: error.headers,
            status: error.status,
            statusText: error.statusText,
            url: error.url ?? undefined,
          }),
      ),
    ),
  );
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function fileNameFrom(header: string | null): string | null {
  if (!header) return null;
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encoded) return decodeURIComponent(encoded[1]);
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1] : null;
}
