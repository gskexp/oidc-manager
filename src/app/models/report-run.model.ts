import { ReportColumn, ReportParamValue } from './report-config.model';

export interface ReportRunRequest {
  reportConfigId: string;
  parameters: Record<string, ReportParamValue>; // keyed by ReportField.key
}

/** A result cell: numbers, strings, ISO dates (YYYY-MM-DD) or date-times, booleans, or null. */
export type ReportCellValue = string | number | boolean | null;

export interface ReportRunResponse {
  runId: string;
  status: 'completed' | 'failed';
  message?: string; // e.g. why results were truncated
  columns: ReportColumn[];
  rows: ReportCellValue[][]; // values aligned with columns
  rowCount: number;
  truncated: boolean;
}

/** An Excel export of a run, ready to save. */
export interface ReportExport {
  blob: Blob;
  fileName: string;
}
