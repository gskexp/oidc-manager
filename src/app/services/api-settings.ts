import { InjectionToken } from '@angular/core';

export type ApiMode = 'mock' | 'api';

export interface ApiSettings {
  mode: ApiMode;
  baseUrl: string;
  mock?: {
    runDelayMs?: number;
    failingReportConfigIds?: string[];
  };
}

export const API_SETTINGS = new InjectionToken<ApiSettings>('API_SETTINGS');
