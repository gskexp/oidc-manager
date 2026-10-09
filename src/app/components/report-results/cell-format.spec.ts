import { formatCell, isNumericColumn } from './cell-format';

describe('formatCell', () => {
  it('formats numbers by column format', () => {
    expect(formatCell(1234.5, 'decimal')).toMatch(/1,?234\.50/);
    expect(formatCell(1234.5, 'integer')).toMatch(/1,?235/);
    expect(formatCell(0.934, 'percent')).toMatch(/93\.4\s?%/);
  });

  it('formats ISO dates without shifting the day', () => {
    expect(formatCell('2026-01-01', 'month')).toMatch(/Jan.*2026/);
    expect(formatCell('2026-01-01', 'date')).toMatch(/1.*Jan.*2026|Jan.*1.*2026/);
  });

  it('shows empty cells and booleans plainly', () => {
    expect(formatCell(null, 'decimal')).toBe('');
    expect(formatCell(true)).toBe('Yes');
    expect(formatCell('north', 'text')).toBe('north');
  });

  it('right-aligns numeric formats and unformatted numbers', () => {
    expect(isNumericColumn('integer', null)).toBe(true);
    expect(isNumericColumn(undefined, 5)).toBe(true);
    expect(isNumericColumn(undefined, 'x')).toBe(false);
    expect(isNumericColumn('month', '2026-01-01')).toBe(false);
  });
});
