// ---- Report configuration (page 3) ----
// Must never contain SQL text or SQL file paths.

export interface ReportConfig {
  reportConfigId: string;
  name: string;
  description?: string;
  fields: ReportField[];
  /** Groups of fields the user fills in as alternatives, e.g. a customer ID *or* a name and postcode. */
  choices?: FieldChoice[];
  /** Results table columns, in display order. Without it, every SQL column is shown under its SQL name. */
  columns?: ReportColumn[];
}

/**
 * A required either/or between sets of fields: at least one alternative must have every one of
 * its fields filled in. The fields are still declared in `fields` (without `required`); the group
 * is shown where its first field would be.
 */
export interface FieldChoice {
  label: string; // group heading, e.g. "Customer"
  alternatives: string[][]; // field keys, e.g. [["customerId"], ["customerName", "postcode"]]
  helpText?: string;
  message?: string; // error shown when no alternative is complete; generated when omitted
}

/** How a results column is displayed (and formatted in the Excel export). */
export type ReportColumnFormat = 'text' | 'integer' | 'decimal' | 'percent' | 'date' | 'month';

export interface ReportColumn {
  key: string; // SQL result column name (matched case-insensitively)
  label: string; // table header
  format?: ReportColumnFormat;
}

interface BaseField {
  key: string; // parameter name sent to the backend
  label: string;
  required?: boolean;
  helpText?: string;
  placeholder?: string;
}

export interface SelectOption {
  value: string;
  label: string;
}

export interface TextField extends BaseField {
  type: 'text';
  defaultValue?: string;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
}

export interface NumberField extends BaseField {
  type: 'number';
  defaultValue?: number;
  min?: number;
  max?: number;
  step?: number;
}

export interface DateField extends BaseField {
  type: 'date';
  defaultValue?: string; // ISO YYYY-MM-DD
  minDate?: string;
  maxDate?: string;
}

export interface DateRangeField extends BaseField {
  type: 'dateRange';
  defaultValue?: { from: string; to: string };
  minDate?: string;
  maxDate?: string;
}

export interface SelectField extends BaseField {
  type: 'select';
  options: SelectOption[];
  defaultValue?: string;
}

export interface MultiSelectField extends BaseField {
  type: 'multiSelect';
  options: SelectOption[];
  defaultValue?: string[];
  minSelected?: number;
  maxSelected?: number;
}

export type ReportField =
  TextField | NumberField | DateField | DateRangeField | SelectField | MultiSelectField;

export type DateRangeValue = { from: string; to: string };

export type ReportParamValue = string | number | string[] | DateRangeValue | null;
