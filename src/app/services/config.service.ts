import { HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Category, ReportConfig, SubCategory } from '../models';
import { ApiUrls } from './api-urls';

/**
 * Read-only configuration. Each method creates an `httpResource`, so call it in an injection
 * context (e.g. a component field initializer). Returning `undefined` from a parameter skips the request.
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  private readonly urls = inject(ApiUrls);

  getCategories(): HttpResourceRef<Category[] | undefined> {
    return httpResource<Category[]>(() => this.urls.categories());
  }

  getCategory(categoryId: () => string | undefined): HttpResourceRef<Category | undefined> {
    return httpResource<Category>(() => {
      const id = categoryId();
      return id ? this.urls.category(id) : undefined;
    });
  }

  getSubCategories(categoryId: () => string | undefined): HttpResourceRef<SubCategory[] | undefined> {
    return httpResource<SubCategory[]>(() => {
      const id = categoryId();
      return id ? this.urls.subCategories(id) : undefined;
    });
  }

  getReportConfig(reportConfigId: () => string | undefined): HttpResourceRef<ReportConfig | undefined> {
    return httpResource<ReportConfig>(() => {
      const id = reportConfigId();
      return id ? this.urls.reportConfig(id) : undefined;
    });
  }
}
