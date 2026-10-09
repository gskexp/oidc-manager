import { Component, computed, input } from '@angular/core';
import { ReportCellValue, ReportColumn } from '../../models';
import { formatCell, isNumericColumn } from './cell-format';

/** The complete results of a run as one table (no pagination), with a sticky header. */
@Component({
  selector: 'app-report-results',
  templateUrl: './report-results.component.html',
  styleUrl: './report-results.component.scss',
})
export class ReportResultsComponent {
  readonly columns = input.required<ReportColumn[]>();
  readonly rows = input.required<ReportCellValue[][]>();
  /** Accessible name for the table, e.g. "Monthly Sales results". */
  readonly caption = input.required<string>();

  /** Right-align numeric columns; decided by format, or by the first value if unformatted. */
  protected readonly numeric = computed(() =>
    this.columns().map((column, i) =>
      isNumericColumn(column.format, this.rows().find((row) => row[i] != null)?.[i] ?? null),
    ),
  );

  /** Formatted once per result, not on every change detection. */
  protected readonly cells = computed(() => {
    const formats = this.columns().map((c) => c.format);
    return this.rows().map((row) => formats.map((format, i) => formatCell(row[i] ?? null, format)));
  });
}
