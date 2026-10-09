import { FieldChoice, ReportField } from '../../models';
import { ReportFormModel, toParameter } from '../report-field/report-field-values';

/** What the form renders, in order: a single field, or an either/or group of fields. */
export type FormItem = { kind: 'field'; field: ReportField } | { kind: 'choice'; id: string; choice: FieldChoice; alternatives: ReportField[][] };

/**
 * Lays out fields in config order, replacing the fields of each choice with one group at the
 * position of its first field. Unknown keys in a choice are ignored, as are keys already used
 * by an earlier choice.
 */
export function layoutFields(fields: readonly ReportField[], choices: readonly FieldChoice[] = []): FormItem[] {
  const byKey = new Map(fields.map((f) => [f.key, f]));
  const groupOf = new Map<string, Extract<FormItem, { kind: 'choice' }>>();

  choices.forEach((choice, index) => {
    const alternatives = choice.alternatives
      .map((keys) => keys.filter((k) => byKey.has(k) && !groupOf.has(k)).map((k) => byKey.get(k)!))
      .filter((alt) => alt.length > 0);
    if (alternatives.length === 0) return;
    const item = { kind: 'choice' as const, id: `choice-${index}`, choice, alternatives };
    alternatives.flat().forEach((f) => groupOf.set(f.key, item));
  });

  const items: FormItem[] = [];
  const placed = new Set<FormItem>();
  for (const field of fields) {
    const group = groupOf.get(field.key);
    if (!group) {
      items.push({ kind: 'field', field });
    } else if (!placed.has(group)) {
      placed.add(group);
      items.push(group);
    }
  }
  return items;
}

/** The error message when no alternative of a choice is complete, or null when one is. */
export function validateChoice(alternatives: ReportField[][], choice: FieldChoice, model: ReportFormModel): string | null {
  const complete = alternatives.some((alt) => alt.every((f) => isFilled(f, model)));
  if (complete) return null;
  return choice.message ?? `Enter ${alternatives.map((alt) => joinLabels(alt.map((f) => f.label))).join(', or ')}.`;
}

function isFilled(field: ReportField, model: ReportFormModel): boolean {
  const value = toParameter(field, model[field.key] ?? null);
  return value !== null && !(Array.isArray(value) && value.length === 0);
}

function joinLabels(labels: string[]): string {
  return labels.length > 1 ? `${labels.slice(0, -1).join(', ')} and ${labels.at(-1)}` : labels[0];
}
