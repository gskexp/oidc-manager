import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ReportConfig } from '../../models';
import { API_SETTINGS } from '../../services/api-settings';
import { ReportService } from '../../services/report.service';
import { ReportFormComponent } from './report-form.component';

const config: ReportConfig = {
  reportConfigId: 'monthly-sales',
  name: 'Monthly Sales',
  description: 'Sales totals by month.',
  fields: [
    { key: 'period', label: 'Reporting period', type: 'dateRange', required: true },
    {
      key: 'regions',
      label: 'Regions',
      type: 'multiSelect',
      minSelected: 1,
      options: [
        { value: 'north', label: 'North' },
        { value: 'south', label: 'South' },
      ],
    },
    {
      key: 'currency',
      label: 'Currency',
      type: 'select',
      defaultValue: 'USD',
      options: [
        { value: 'USD', label: 'US Dollar' },
        { value: 'EUR', label: 'Euro' },
      ],
    },
    { key: 'minAmount', label: 'Minimum order amount', type: 'number', min: 0 },
  ],
};

const response = {
  runId: 'run-1',
  status: 'completed',
  columns: [
    { key: 'sales_month', label: 'Month', format: 'month' },
    { key: 'region', label: 'Region' },
    { key: 'total_amount', label: 'Total sales', format: 'decimal' },
  ],
  rows: [
    ['2026-01-01', 'south', 1234.5],
    ['2026-02-01', 'south', 99],
  ],
  rowCount: 2,
  truncated: false,
};

async function setup(canExport = true) {
  const runReport = vi.fn();
  const exportReport = vi.fn();
  TestBed.configureTestingModule({
    imports: [ReportFormComponent],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: API_SETTINGS, useValue: { mode: 'mock', baseUrl: '' } },
      { provide: ReportService, useValue: { runReport, exportReport, canExport } },
    ],
  });
  const fixture = TestBed.createComponent(ReportFormComponent);
  fixture.componentRef.setInput('reportConfigId', 'monthly-sales');
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  http.expectOne('/mock-api/report-configs/monthly-sales.json').flush(config);
  await fixture.whenStable();
  const el = fixture.nativeElement as HTMLElement;
  return { fixture, el, http, runReport, exportReport };
}

function setValue(input: HTMLInputElement, value: string) {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('ReportFormComponent', () => {
  it('renders the report name and one control per field', async () => {
    const { el } = await setup();
    expect(el.querySelector('h1')?.textContent).toContain('Monthly Sales');
    expect(el.querySelectorAll('app-report-field').length).toBe(4);
    expect((el.querySelector('#field-currency') as HTMLSelectElement).value).toBe('USD');
  });

  it('shows errors and does not run when submitted invalid', async () => {
    const { fixture, el, runReport } = await setup();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    const errors = Array.from(el.querySelectorAll('.field__error')).map((e) =>
      e.textContent?.trim(),
    );
    expect(errors).toContain('Enter both a start and end date for reporting period.');
    expect(errors.some((e) => e?.includes('Select at least 1 option'))).toBe(true);
    expect(runReport).not.toHaveBeenCalled();
  });

  async function runValidNoWait(el: HTMLElement) {
    setValue(el.querySelector('#field-period-from')!, '2026-01-01');
    setValue(el.querySelector('#field-period-to')!, '2026-03-31');
    el.querySelector<HTMLInputElement>('#field-regions-1')!.click();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
  }

  async function runValid(fixture: { whenStable(): Promise<unknown> }, el: HTMLElement) {
    setValue(el.querySelector('#field-period-from')!, '2026-01-01');
    setValue(el.querySelector('#field-period-to')!, '2026-03-31');
    el.querySelector<HTMLInputElement>('#field-regions-1')!.click();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('runs with the form values and shows the results table', async () => {
    const { fixture, el, runReport } = await setup();
    runReport.mockReturnValue(of(response));
    await runValid(fixture, el);

    expect(runReport).toHaveBeenCalledWith({
      reportConfigId: 'monthly-sales',
      parameters: {
        period: { from: '2026-01-01', to: '2026-03-31' },
        regions: ['south'],
        currency: 'USD',
        minAmount: null,
      },
    });
    const headers = Array.from(el.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Month', 'Region', 'Total sales']);
    const firstRow = Array.from(el.querySelectorAll('tbody tr:first-child td')).map((td) =>
      td.textContent?.trim(),
    );
    expect(firstRow[0]).toMatch(/Jan.*2026/);
    expect(firstRow[1]).toBe('south');
    expect(firstRow[2]).toMatch(/1,?234\.50/);
    expect(el.querySelector('td:last-child')?.classList).toContain('num');

    const results = el.querySelector('.results')?.textContent ?? '';
    expect(results).toContain('2 rows');
    expect(results).toContain('South');
    expect(results).toContain('US Dollar');
  });

  it('keeps multi-select values in option order', async () => {
    const { fixture, el, runReport } = await setup();
    runReport.mockReturnValue(of(response));
    setValue(el.querySelector('#field-period-from')!, '2026-01-01');
    setValue(el.querySelector('#field-period-to')!, '2026-03-31');
    el.querySelector<HTMLInputElement>('#field-regions-1')!.click(); // South first
    el.querySelector<HTMLInputElement>('#field-regions-0')!.click(); // then North
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
    expect(runReport.mock.calls[0][0].parameters.regions).toEqual(['north', 'south']);
  });

  it('ignores results that arrive after switching to another report', async () => {
    const { fixture, el, http, runReport } = await setup();
    const pending = new Subject<typeof response>();
    runReport.mockReturnValue(pending);
    await runValidNoWait(el);

    fixture.componentRef.setInput('reportConfigId', 'other-report');
    fixture.detectChanges();
    http
      .expectOne('/mock-api/report-configs/other-report.json')
      .flush({ ...config, reportConfigId: 'other-report' });
    pending.next(response);
    pending.complete();
    await fixture.whenStable();

    expect(el.querySelector('table')).toBeNull();
  });

  it('shows an empty state when no rows match', async () => {
    const { fixture, el, runReport } = await setup();
    runReport.mockReturnValue(of({ ...response, rows: [], rowCount: 0 }));
    await runValid(fixture, el);
    expect(el.querySelector('table')).toBeNull();
    expect(el.querySelector('.results')?.textContent).toContain('No rows match');
  });

  it('warns when results were truncated', async () => {
    const { fixture, el, runReport } = await setup();
    runReport.mockReturnValue(
      of({ ...response, truncated: true, message: 'Showing the first 2 rows.' }),
    );
    await runValid(fixture, el);
    expect(el.querySelector('.alert--warning')?.textContent).toContain('Showing the first 2 rows.');
  });

  it('exports the same request to Excel', async () => {
    const { fixture, el, runReport, exportReport } = await setup();
    runReport.mockReturnValue(of(response));
    exportReport.mockReturnValue(of({ blob: new Blob(['x']), fileName: 'monthly-sales.xlsx' }));
    URL.createObjectURL = vi.fn(() => 'blob:test');
    URL.revokeObjectURL = vi.fn();
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    await runValid(fixture, el);

    Array.from(el.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes('Export to Excel'))!
      .click();
    await fixture.whenStable();

    expect(exportReport).toHaveBeenCalledWith(runReport.mock.calls[0][0]);
    expect(click).toHaveBeenCalled();
  });

  it('hides export when not connected to the backend', async () => {
    const { fixture, el, runReport } = await setup(false);
    runReport.mockReturnValue(of(response));
    await runValid(fixture, el);
    expect(el.textContent).not.toContain('Export to Excel');
    expect(el.textContent).toContain('Excel export is available when the app is connected');
  });

  it('keeps input and shows an error when the run fails', async () => {
    const { fixture, el, runReport } = await setup();
    runReport.mockReturnValue(throwError(() => new Error('boom')));

    setValue(el.querySelector('#field-period-from')!, '2026-01-01');
    setValue(el.querySelector('#field-period-to')!, '2026-03-31');
    el.querySelector<HTMLInputElement>('#field-regions-0')!.click();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    expect(el.querySelector('.alert--error')).toBeTruthy();
    expect(el.querySelector<HTMLInputElement>('#field-period-from')!.value).toBe('2026-01-01');
  });

  it('resets to default values', async () => {
    const { fixture, el } = await setup();
    const select = el.querySelector<HTMLSelectElement>('#field-currency')!;
    select.value = 'EUR';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    Array.from(el.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes('Reset'))!
      .click();
    await fixture.whenStable();
    expect(select.value).toBe('USD');
  });

  it('shows a not-found state for unknown reports', async () => {
    TestBed.configureTestingModule({
      imports: [ReportFormComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_SETTINGS, useValue: { mode: 'mock', baseUrl: '' } },
      ],
    });
    const fixture = TestBed.createComponent(ReportFormComponent);
    fixture.componentRef.setInput('reportConfigId', 'nope');
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/mock-api/report-configs/nope.json')
      .flush('', { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Report not found');
  });
});
