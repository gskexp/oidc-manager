import { Injectable, inject } from '@angular/core';
import { API_SETTINGS } from './api-settings';

@Injectable({ providedIn: 'root' })
export class ApiUrls {
  private readonly settings = inject(API_SETTINGS);

  categories(): string {
    return this.api('categories');
  }

  category(categoryId: string): string {
    return this.api(`categories/${enc(categoryId)}`);
  }

  subCategories(categoryId: string): string {
    return this.api(`categories/${enc(categoryId)}/subcategories`);
  }

  reportConfig(reportConfigId: string): string {
    return this.api(`report-configs/${enc(reportConfigId)}`);
  }

  runReport(reportConfigId: string): string {
    return this.api(`reports/${enc(reportConfigId)}/run`);
  }

  exportReport(reportConfigId: string): string {
    return this.api(`reports/${enc(reportConfigId)}/export`);
  }

  private api(path: string): string {
    return `${this.settings.baseUrl}/api/${path}`;
  }
}

const enc = encodeURIComponent;
