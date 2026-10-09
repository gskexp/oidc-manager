export interface Category {
  id: string;
  name: string;
  description?: string;
  subcategoryPath?: string;
}

export interface SubCategory {
  id: string;
  categoryId: string;
  name: string;
  description?: string;
  reports: ReportSummary[];
}

export interface ReportSummary {
  id: string;
  name: string;
  description?: string;
  reportConfigId: string;
}
