import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, map, switchMap, throwError } from 'rxjs';
import { ReportExport, ReportRunRequest, ReportRunResponse } from '../models';
import { ApiUrls } from './api-urls';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly urls = inject(ApiUrls);

  runReport(request: ReportRunRequest): Observable<ReportRunResponse> {
    return this.http.post<ReportRunResponse>(this.urls.runReport(request.reportConfigId), request);
  }

  exportReport(request: ReportRunRequest): Observable<ReportExport> {
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
