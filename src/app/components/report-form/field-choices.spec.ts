import { FieldChoice, ReportField } from '../../models';
import { layoutFields, validateChoice } from './field-choices';

const fields: ReportField[] = [
  { key: 'asAt', label: 'As at', type: 'date' },
  { key: 'customerId', label: 'Customer ID', type: 'text' },
  { key: 'minBalance', label: 'Minimum balance', type: 'number' },
  { key: 'customerName', label: 'Customer name', type: 'text' },
  { key: 'postcode', label: 'Postcode', type: 'text' },
];
const choice: FieldChoice = {
  label: 'Customer',
  alternatives: [['customerId'], ['customerName', 'postcode']],
};

describe('field-choices', () => {
  it('groups choice fields at the position of the first one', () => {
    const items = layoutFields(fields, [choice]);
    expect(items.map((i) => (i.kind === 'field' ? i.field.key : i.id))).toEqual(['asAt', 'choice-0', 'minBalance']);
    const group = items[1];
    expect(group.kind === 'choice' && group.alternatives.map((a) => a.map((f) => f.key))).toEqual([['customerId'], ['customerName', 'postcode']]);
  });

  it('ignores unknown keys and lays out plainly without choices', () => {
    expect(layoutFields(fields).every((i) => i.kind === 'field')).toBe(true);
    const items = layoutFields(fields, [{ label: 'X', alternatives: [['nope']] }]);
    expect(items.every((i) => i.kind === 'field')).toBe(true);
  });

  it('passes when any one alternative is complete', () => {
    const [, group] = layoutFields(fields, [choice]);
    if (group.kind !== 'choice') throw new Error('expected a choice');
    const check = (model: Record<string, string>) => validateChoice(group.alternatives, choice, model);

    expect(check({ customerId: 'C-12345', customerName: '', postcode: '' })).toBeNull();
    expect(check({ customerId: '', customerName: 'Acme', postcode: '2600' })).toBeNull();
    expect(check({ customerId: '', customerName: 'Acme', postcode: '' })).toBe('Enter Customer ID, or Customer name and Postcode.');
    expect(check({ customerId: '  ', customerName: '', postcode: '' })).not.toBeNull(); // blank text
    expect(validateChoice(group.alternatives, { ...choice, message: 'Pick a customer.' }, {})).toBe('Pick a customer.');
  });
});
