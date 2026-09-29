import { Category, CategoryAccent } from '../models';

const ACCENTS: readonly CategoryAccent[] = ['teal', 'indigo', 'plum', 'amber', 'green', 'blue'];

/** CSS colour for a category: its configured accent, or a stable pick from its id. */
export function categoryAccent(category: Pick<Category, 'id' | 'accent'>): string {
  const accent =
    category.accent && ACCENTS.includes(category.accent)
      ? category.accent
      : ACCENTS[hash(category.id) % ACCENTS.length];
  return `var(--hue-${accent})`;
}

function hash(text: string): number {
  let h = 0x811c9dc5;
  for (const char of text) h = Math.imul(h ^ char.charCodeAt(0), 0x01000193) >>> 0;
  return h;
}
