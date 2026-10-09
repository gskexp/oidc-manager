import { ValidationError } from '@angular/forms/signals';
import { DateRangeValue, ReportField, ReportParamValue } from '../../models';

export type ReportFormModel = Record<string, ReportParamValue>;

export function initialValue(field: ReportField): ReportParamValue {
  switch (field.type) {
    case 'text':
    case 'date':
    case 'select':
      return field.defaultValue ?? '';
    case 'number':
      return field.defaultValue ?? null;
    case 'dateRange':
      return { from: field.defaultValue?.from ?? '', to: field.defaultValue?.to ?? '' };
    case 'multiSelect':
      return [...(field.defaultValue ?? [])];
    default:
      return assertNever(field);
  }
}

export function initialModel(fields: readonly ReportField[]): ReportFormModel {
  return Object.fromEntries(fields.map((field) => [field.key, initialValue(field)]));
}

export function validateValue(field: ReportField, value: ReportParamValue): ValidationError[] {
  const errors: ValidationError[] = [];
  const add = (kind: string, message: string) => errors.push({ kind, message });

  switch (field.type) {
    case 'text': {
      const text = asString(value).trim();
      if (!text) {
        if (field.required) add('required', `${field.label} is required.`);
        break;
      }
      if (field.minLength != null && text.length < field.minLength) {
        add('minLength', `${field.label} must be at least ${field.minLength} characters.`);
      }
      if (field.maxLength != null && text.length > field.maxLength) {
        add('maxLength', `${field.label} must be at most ${field.maxLength} characters.`);
      }
      if (field.pattern && !new RegExp(`^(?:${field.pattern})$`).test(text)) {
        add('pattern', `${field.label} is not in the expected format.`);
      }
      break;
    }
    case 'number': {
      if (typeof value !== 'number' || Number.isNaN(value)) {
        if (field.required) add('required', `${field.label} is required.`);
        break;
      }
      if (field.min != null && value < field.min) {
        add('min', `${field.label} must be ${field.min} or more.`);
      }
      if (field.max != null && value > field.max) {
        add('max', `${field.label} must be ${field.max} or less.`);
      }
      break;
    }
    case 'date': {
      const date = asString(value);
      if (!date) {
        if (field.required) add('required', `${field.label} is required.`);
        break;
      }
      checkDateBounds(date, field.minDate, field.maxDate, field.label, add);
      break;
    }
    case 'dateRange': {
      const { from, to } = asRange(value);
      if (field.required && (!from || !to)) {
        add('required', `Enter both a start and end date for ${lower(field.label)}.`);
      } else if (!field.required && Boolean(from) !== Boolean(to)) {
        add('incomplete', `Enter both dates for ${lower(field.label)}, or leave both empty.`);
      }
      if (from) checkDateBounds(from, field.minDate, field.maxDate, 'Start date', add);
      if (to) checkDateBounds(to, field.minDate, field.maxDate, 'End date', add);
      if (from && to && from > to) {
        add('rangeOrder', 'The start date must not be after the end date.');
      }
      break;
    }
    case 'select': {
      if (field.required && !asString(value)) {
        add('required', `${field.label} is required.`);
      }
      break;
    }
    case 'multiSelect': {
      const count = asList(value).length;
      const min = Math.max(field.minSelected ?? 0, field.required ? 1 : 0);
      if (count < min) {
        add('minSelected', `Select at least ${min} ${plural(min, 'option')}.`);
      }
      if (field.maxSelected != null && count > field.maxSelected) {
        add('maxSelected', `Select no more than ${field.maxSelected} ${plural(field.maxSelected, 'option')}.`);
      }
      break;
    }
    default:
      assertNever(field);
  }
  return errors;
}

export function toParameter(field: ReportField, value: ReportParamValue): ReportParamValue {
  switch (field.type) {
    case 'text':
      return asString(value).trim() || null;
    case 'date':
    case 'select':
      return asString(value) || null;
    case 'number':
      return typeof value === 'number' && !Number.isNaN(value) ? value : null;
    case 'dateRange': {
      const range = asRange(value);
      return range.from && range.to ? { from: range.from, to: range.to } : null;
    }
    case 'multiSelect':
      return [...asList(value)];
    default:
      return assertNever(field);
  }
}

export function formatValue(field: ReportField, value: ReportParamValue): string {
  const none = 'Not specified';
  switch (field.type) {
    case 'text':
      return asString(value) || none;
    case 'number':
      return typeof value === 'number' ? value.toLocaleString() : none;
    case 'date':
      return formatDate(asString(value)) || none;
    case 'dateRange': {
      const { from, to } = asRange(value);
      return from && to ? `${formatDate(from)} – ${formatDate(to)}` : none;
    }
    case 'select':
      return optionLabel(field.options, asString(value)) || none;
    case 'multiSelect': {
      const labels = asList(value).map((v) => optionLabel(field.options, v));
      return labels.length ? labels.join(', ') : none;
    }
    default:
      return assertNever(field);
  }
}

export function formatDate(iso: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!match) return iso;
  const date = new Date(Date.UTC(+match[1], +match[2] - 1, +match[3]));
  return date.toLocaleDateString(undefined, { dateStyle: 'medium', timeZone: 'UTC' });
}

function checkDateBounds(
  date: string,
  minDate: string | undefined,
  maxDate: string | undefined,
  label: string,
  add: (kind: string, message: string) => void,
) {
  if (minDate && date < minDate) add('minDate', `${label} must be on or after ${formatDate(minDate)}.`);
  if (maxDate && date > maxDate) add('maxDate', `${label} must be on or before ${formatDate(maxDate)}.`);
}

function optionLabel(options: { value: string; label: string }[], value: string): string {
  return options.find((o) => o.value === value)?.label ?? value;
}

function asString(value: ReportParamValue): string {
  return typeof value === 'string' ? value : '';
}

function asList(value: ReportParamValue): string[] {
  return Array.isArray(value) ? value : [];
}

function asRange(value: ReportParamValue): DateRangeValue {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : { from: '', to: '' };
}

function plural(count: number, word: string): string {
  return count === 1 ? word : `${word}s`;
}

function lower(label: string): string {
  return label.charAt(0).toLowerCase() + label.slice(1);
}

function assertNever(field: never): never {
  throw new Error(`Unsupported field type: ${(field as { type: string }).type}`);
}
