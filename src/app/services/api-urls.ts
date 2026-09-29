import { Injectable, inject } from '@angular/core';
import { API_SETTINGS } from './api-settings';

/** The single place where API and mock URLs are built. */
@Injectable({ providedIn: 'root' })
export class ApiUrls {
  private readonly settings = inject(API_SETTINGS);

  get mode() {
    return this.settings.mode;
  }

  categories(): string {
    return this.resource('categories');
  }

  category(categoryId: string): string {
    return this.resource(`categories/${enc(categoryId)}`);
  }

  subCategories(categoryId: string): string {
    return this.resource(`categories/${enc(categoryId)}/subcategories`);
  }

  reportConfig(reportConfigId: string): string {
    return this.resource(`report-configs/${enc(reportConfigId)}`);
  }

  /** API mode only — mock mode never posts. */
  runReport(reportConfigId: string): string {
    return `${this.settings.baseUrl}/api/reports/${enc(reportConfigId)}/run`;
  }

  /** API mode only: returns an .xlsx of the run. */
  exportReport(reportConfigId: string): string {
    return `${this.settings.baseUrl}/api/reports/${enc(reportConfigId)}/export`;
  }

  /** Mock mode only: canned results for a report (not every report has them). */
  mockRunResult(reportConfigId: string): string {
    return `${this.settings.baseUrl}/mock-api/report-results/${enc(reportConfigId)}.json`;
  }

  private resource(path: string): string {
    return this.settings.mode === 'mock'
      ? `${this.settings.baseUrl}/mock-api/${path}.json`
      : `${this.settings.baseUrl}/api/${path}`;
  }
}

const enc = encodeURIComponent;
