import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  Observable,
  catchError,
  defer,
  delay,
  from,
  map,
  of,
  switchMap,
  throwError,
  timer,
} from 'rxjs';
import { ReportExport, ReportRunRequest, ReportRunResponse } from '../models';
import { API_SETTINGS } from './api-settings';
import { ApiUrls } from './api-urls';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly urls = inject(ApiUrls);
  private readonly settings = inject(API_SETTINGS);

  /** Excel export needs the backend; mock mode has no file to offer. */
  readonly canExport = this.urls.mode === 'api';

  runReport(request: ReportRunRequest): Observable<ReportRunResponse> {
    if (this.urls.mode === 'api') {
      return this.http.post<ReportRunResponse>(
        this.urls.runReport(request.reportConfigId),
        request,
      );
    }
    return this.mockRun(request);
  }

  /** Runs the report on the server with the same parameters and returns it as an .xlsx file. */
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
          fileName:
            fileNameFrom(response.headers.get('Content-Disposition')) ??
            `${request.reportConfigId}.xlsx`,
        })),
        catchError(parseBlobError),
      );
  }

  /**
   * Mock mode: logs the request and, after a short delay, returns canned rows from
   * mock-api/report-results/{id}.json, or an empty table if that report has none.
   * Reports listed in mock.failingReportConfigIds fail, to exercise the error state.
   */
  private mockRun(request: ReportRunRequest): Observable<ReportRunResponse> {
    const { runDelayMs = 800, failingReportConfigIds = [] } = this.settings.mock ?? {};
    return defer(() => {
      console.info('[mock] runReport', request);
      if (failingReportConfigIds.includes(request.reportConfigId)) {
        return timer(runDelayMs).pipe(
          switchMap(() => throwError(() => new Error('Simulated failure (mock mode).'))),
        );
      }
      const empty: ReportRunResponse = {
        runId: '',
        status: 'completed',
        columns: [],
        rows: [],
        rowCount: 0,
        truncated: false,
      };
      return this.http
        .get<Omit<ReportRunResponse, 'runId' | 'status'>>(
          this.urls.mockRunResult(request.reportConfigId),
        )
        .pipe(
          map((result) => ({ ...empty, ...result })),
          catchError((error: unknown) =>
            error instanceof HttpErrorResponse && error.status === 404
              ? of(empty)
              : throwError(() => error),
          ),
          map((result) => ({ ...result, runId: crypto.randomUUID() })),
          delay(runDelayMs),
        );
    });
  }
}

/**
 * With responseType 'blob', error bodies arrive as a Blob. Re-throws the error with its JSON body
 * parsed, so callers can show the server's message (e.g. a timeout) like for any other request.
 */
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

/** Reads the file name from a Content-Disposition header (plain or RFC 5987 encoded). */
function fileNameFrom(header: string | null): string | null {
  if (!header) return null;
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encoded) return decodeURIComponent(encoded[1]);
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1] : null;
}
