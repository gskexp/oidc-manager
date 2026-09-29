import { ApiSettings } from '../app/services/api-settings';

/**
 * Switching from mock JSON to the Spring Boot API is a change to `api` only:
 *   mode: 'api', baseUrl: 'https://reports.example.com' (or '' when served from the same origin).
 */
export const environment: { api: ApiSettings } = {
  api: {
    mode: 'mock',
    baseUrl: '',
    mock: {
      runDelayMs: 800,
      // Report runs for these IDs fail in mock mode, so the error state can be exercised.
      failingReportConfigIds: ['sales-trends'],
    },
  },
};
