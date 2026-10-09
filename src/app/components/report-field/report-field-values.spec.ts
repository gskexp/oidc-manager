import { ReportField } from '../../models';
import { formatValue, initialModel, toParameter, validateValue } from './report-field-values';

const kinds = (field: ReportField, value: Parameters<typeof validateValue>[1]) =>
  validateValue(field, value).map((e) => e.kind);

describe('report-field-values', () => {
  it('builds an initial model from defaults and empty values', () => {
    const model = initialModel([
      { key: 't', label: 'T', type: 'text' },
      { key: 'n', label: 'N', type: 'number', defaultValue: 5 },
      { key: 'r', label: 'R', type: 'dateRange' },
      { key: 's', label: 'S', type: 'select', defaultValue: 'USD', options: [] },
      { key: 'm', label: 'M', type: 'multiSelect', options: [] },
    ]);
    expect(model).toEqual({ t: '', n: 5, r: { from: '', to: '' }, s: 'USD', m: [] });
  });

  it('validates text length and pattern', () => {
    const field: ReportField = {
      key: 'c',
      label: 'Code',
      type: 'text',
      required: true,
      minLength: 3,
      pattern: 'CC-[0-9]{4}',
    };
    expect(kinds(field, '')).toEqual(['required']);
    expect(kinds(field, '   ')).toEqual(['required']); // trimmed, like the backend
    expect(kinds(field, 'CC')).toEqual(['minLength', 'pattern']);
    expect(kinds(field, 'CC-1234')).toEqual([]);
    expect(kinds(field, 'xCC-1234')).toEqual(['pattern']); // pattern is anchored
  });

  it('validates number bounds and treats null as empty', () => {
    const field: ReportField = { key: 'n', label: 'N', type: 'number', min: 1, max: 10 };
    expect(kinds(field, null)).toEqual([]);
    expect(kinds({ ...field, required: true }, null)).toEqual(['required']);
    expect(kinds(field, 0)).toEqual(['min']);
    expect(kinds(field, 11)).toEqual(['max']);
  });

  it('validates date bounds', () => {
    const field: ReportField = {
      key: 'd',
      label: 'D',
      type: 'date',
      minDate: '2024-01-01',
      maxDate: '2024-12-31',
    };
    expect(kinds(field, '2023-12-31')).toEqual(['minDate']);
    expect(kinds(field, '2025-01-01')).toEqual(['maxDate']);
    expect(kinds(field, '2024-06-01')).toEqual([]);
  });

  it('validates date ranges', () => {
    const field: ReportField = { key: 'p', label: 'Period', type: 'dateRange', required: true };
    expect(kinds(field, { from: '', to: '' })).toEqual(['required']);
    expect(kinds(field, { from: '2024-02-01', to: '2024-01-01' })).toEqual(['rangeOrder']);
    expect(kinds(field, { from: '2024-01-01', to: '2024-01-01' })).toEqual([]);
    expect(kinds({ ...field, required: false }, { from: '2024-01-01', to: '' })).toEqual([
      'incomplete',
    ]);
  });

  it('validates multi-select counts', () => {
    const field: ReportField = {
      key: 'm',
      label: 'M',
      type: 'multiSelect',
      minSelected: 1,
      maxSelected: 2,
      options: [],
    };
    expect(kinds(field, [])).toEqual(['minSelected']);
    expect(kinds(field, ['a', 'b', 'c'])).toEqual(['maxSelected']);
    expect(kinds(field, ['a'])).toEqual([]);
  });

  it('converts empty values to null parameters', () => {
    expect(toParameter({ key: 't', label: 'T', type: 'text' }, '  ')).toBeNull();
    expect(
      toParameter({ key: 'r', label: 'R', type: 'dateRange' }, { from: '', to: '' }),
    ).toBeNull();
    expect(toParameter({ key: 'n', label: 'N', type: 'number' }, 0)).toBe(0);
  });

  it('formats option labels for the summary', () => {
    const options = [
      { value: 'north', label: 'North' },
      { value: 'south', label: 'South' },
    ];
    expect(
      formatValue({ key: 'm', label: 'M', type: 'multiSelect', options }, ['south', 'north']),
    ).toBe('South, North');
    expect(formatValue({ key: 's', label: 'S', type: 'select', options }, null)).toBe(
      'Not specified',
    );
  });
});
