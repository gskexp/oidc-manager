import { InjectionToken } from '@angular/core';

export interface ApiSettings {
  baseUrl: string;
}

export const API_SETTINGS = new InjectionToken<ApiSettings>('API_SETTINGS');
