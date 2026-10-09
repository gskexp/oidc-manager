import { ApiSettings } from '../app/services/api-settings';

export const environment: { api: ApiSettings } = {
  api: {
    mode: 'mock',
    baseUrl: '',
    mock: {
      runDelayMs: 800,
      failingReportConfigIds: ['sales-trends'],
    },
  },
};
