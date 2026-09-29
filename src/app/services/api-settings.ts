import { InjectionToken } from '@angular/core';

export type ApiMode = 'mock' | 'api';

export interface ApiSettings {
  mode: ApiMode;
  /** Origin (and optional path prefix) the API or mock files are served from. No trailing slash. */
  baseUrl: string;
  mock?: {
    runDelayMs?: number;
    failingReportConfigIds?: string[];
  };
}

export const API_SETTINGS = new InjectionToken<ApiSettings>('API_SETTINGS');
