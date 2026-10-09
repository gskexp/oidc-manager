import { Component, computed, input } from '@angular/core';
import { ReportCellValue, ReportColumn } from '../../models';
import { formatCell, isNumericColumn } from './cell-format';

@Component({
  selector: 'app-report-results',
  templateUrl: './report-results.component.html',
  styleUrl: './report-results.component.scss',
})
export class ReportResultsComponent {
  readonly columns = input.required<ReportColumn[]>();
  readonly rows = input.required<ReportCellValue[][]>();
  readonly caption = input.required<string>();

  protected readonly numeric = computed(() =>
    this.columns().map((column, i) => isNumericColumn(column.format, this.rows().find((row) => row[i] != null)?.[i] ?? null)),
  );

  protected readonly cells = computed(() => {
    const formats = this.columns().map((c) => c.format);
    return this.rows().map((row) => formats.map((format, i) => formatCell(row[i] ?? null, format)));
  });
}
