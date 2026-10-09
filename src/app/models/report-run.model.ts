import { ReportColumn, ReportParamValue } from './report-config.model';

export interface ReportRunRequest {
  reportConfigId: string;
  parameters: Record<string, ReportParamValue>;
}

export type ReportCellValue = string | number | boolean | null;

export interface ReportRunResponse {
  runId: string;
  status: 'completed' | 'failed';
  message?: string;
  columns: ReportColumn[];
  rows: ReportCellValue[][];
  rowCount: number;
  truncated: boolean;
}

export interface ReportExport {
  blob: Blob;
  fileName: string;
}
