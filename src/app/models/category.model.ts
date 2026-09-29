/** Named palette colours a category can use; each has light and dark variants that meet WCAG AA. */
export type CategoryAccent = 'teal' | 'indigo' | 'plum' | 'amber' | 'green' | 'blue';

export interface Category {
  id: string;
  name: string;
  description?: string;
  icon?: string;
  accent?: CategoryAccent; // colour for the category's cards and pages; picked from the id if absent
  subcategoryPath?: string; // Purpose undecided — do not use. Ask the user before building anything on it.
}

export interface SubCategory {
  id: string;
  categoryId: string;
  name: string;
  description?: string;
  icon?: string;
  reports: ReportSummary[];
}

export interface ReportSummary {
  id: string;
  name: string;
  description?: string;
  reportConfigId: string;
}
