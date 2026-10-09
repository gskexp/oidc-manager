export interface ReportConfig {
  reportConfigId: string;
  name: string;
  description?: string;
  fields: ReportField[];
  choices?: FieldChoice[];
  columns?: ReportColumn[];
}

export interface FieldChoice {
  label: string;
  alternatives: string[][];
  helpText?: string;
  message?: string;
}

export type ReportColumnFormat = 'text' | 'integer' | 'decimal' | 'percent' | 'date' | 'month';

export interface ReportColumn {
  key: string;
  label: string;
  format?: ReportColumnFormat;
}

interface BaseField {
  key: string;
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
  defaultValue?: string;
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

export type ReportField = TextField | NumberField | DateField | DateRangeField | SelectField | MultiSelectField;

export type DateRangeValue = { from: string; to: string };

export type ReportParamValue = string | number | string[] | DateRangeValue | null;
