import { ReportCellValue, ReportColumnFormat } from '../../models';

const integer = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
const decimal = new Intl.NumberFormat(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const percent = new Intl.NumberFormat(undefined, {
  style: 'percent',
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});
const plainNumber = new Intl.NumberFormat();

export function formatCell(value: ReportCellValue, format?: ReportColumnFormat): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'number') {
    switch (format) {
      case 'integer':
        return integer.format(value);
      case 'decimal':
        return decimal.format(value);
      case 'percent':
        return percent.format(value);
      default:
        return plainNumber.format(value);
    }
  }
  if (format === 'date' || format === 'month') return formatDateValue(value, format);
  return value;
}

export function isNumericColumn(format: ReportColumnFormat | undefined, sample: ReportCellValue): boolean {
  return format === 'integer' || format === 'decimal' || format === 'percent' ? true : format === undefined && typeof sample === 'number';
}

function formatDateValue(value: string, format: 'date' | 'month'): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(value);
  if (!match) return value;
  const [, y, m, d, hh, mm] = match;
  const date = new Date(Date.UTC(+y, +m - 1, +d, hh ? +hh : 0, mm ? +mm : 0));
  if (format === 'month') {
    return date.toLocaleDateString(undefined, { month: 'short', year: 'numeric', timeZone: 'UTC' });
  }
  return hh
    ? date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' })
    : date.toLocaleDateString(undefined, { dateStyle: 'medium', timeZone: 'UTC' });
}
