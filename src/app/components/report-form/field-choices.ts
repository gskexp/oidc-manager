import { FieldChoice, ReportField } from '../../models';
import { ReportFormModel, toParameter } from '../report-field/report-field-values';

export type FormItem = { kind: 'field'; field: ReportField } | { kind: 'choice'; id: string; choice: FieldChoice; alternatives: ReportField[][] };

export function layoutFields(fields: ReportField[], choices: FieldChoice[] = []): FormItem[] {
  const items: FormItem[] = [];
  for (const field of fields) {
    const index = choices.findIndex((c) => c.alternatives.flat().includes(field.key));
    if (index === -1) {
      items.push({ kind: 'field', field });
      continue;
    }
    const id = `choice-${index}`;
    if (items.some((item) => item.kind === 'choice' && item.id === id)) continue;
    const choice = choices[index];
    const alternatives = choice.alternatives.map((keys) => fields.filter((f) => keys.includes(f.key))).filter((alt) => alt.length > 0);
    items.push({ kind: 'choice', id, choice, alternatives });
  }
  return items;
}

export function validateChoice(alternatives: ReportField[][], choice: FieldChoice, model: ReportFormModel): string | null {
  const complete = alternatives.some((alt) => alt.every((f) => isFilled(f, model)));
  if (complete) return null;
  const options = alternatives.map((alt) => alt.map((f) => f.label).join(' and '));
  return choice.message ?? `Enter ${options.join(', or ')}.`;
}

function isFilled(field: ReportField, model: ReportFormModel): boolean {
  const value = toParameter(field, model[field.key] ?? null);
  return value !== null && !(Array.isArray(value) && value.length === 0);
}
