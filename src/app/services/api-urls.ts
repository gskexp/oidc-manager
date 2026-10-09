import { Injectable, inject } from '@angular/core';
import { API_SETTINGS } from './api-settings';

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

  runReport(reportConfigId: string): string {
    return `${this.settings.baseUrl}/api/reports/${enc(reportConfigId)}/run`;
  }

  exportReport(reportConfigId: string): string {
    return `${this.settings.baseUrl}/api/reports/${enc(reportConfigId)}/export`;
  }

  mockRunResult(reportConfigId: string): string {
    return `${this.settings.baseUrl}/mock-api/report-results/${enc(reportConfigId)}.json`;
  }

  private resource(path: string): string {
    return this.settings.mode === 'mock' ? `${this.settings.baseUrl}/mock-api/${path}.json` : `${this.settings.baseUrl}/api/${path}`;
  }
}

const enc = encodeURIComponent;
