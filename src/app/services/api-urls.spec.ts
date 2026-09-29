import { TestBed } from '@angular/core/testing';
import { API_SETTINGS, ApiSettings } from './api-settings';
import { ApiUrls } from './api-urls';

function setup(settings: ApiSettings): ApiUrls {
  TestBed.configureTestingModule({ providers: [{ provide: API_SETTINGS, useValue: settings }] });
  return TestBed.inject(ApiUrls);
}

describe('ApiUrls', () => {
  it('builds mock JSON paths in mock mode', () => {
    const urls = setup({ mode: 'mock', baseUrl: '' });
    expect(urls.categories()).toBe('/mock-api/categories.json');
    expect(urls.category('sales')).toBe('/mock-api/categories/sales.json');
    expect(urls.subCategories('sales')).toBe('/mock-api/categories/sales/subcategories.json');
    expect(urls.reportConfig('monthly-sales')).toBe('/mock-api/report-configs/monthly-sales.json');
  });

  it('builds REST paths in api mode', () => {
    const urls = setup({ mode: 'api', baseUrl: 'https://reports.example.com' });
    expect(urls.categories()).toBe('https://reports.example.com/api/categories');
    expect(urls.subCategories('sales')).toBe(
      'https://reports.example.com/api/categories/sales/subcategories',
    );
    expect(urls.reportConfig('monthly-sales')).toBe(
      'https://reports.example.com/api/report-configs/monthly-sales',
    );
    expect(urls.runReport('monthly-sales')).toBe(
      'https://reports.example.com/api/reports/monthly-sales/run',
    );
  });

  it('encodes IDs', () => {
    const urls = setup({ mode: 'api', baseUrl: '' });
    expect(urls.category('a/b')).toBe('/api/categories/a%2Fb');
  });
});
