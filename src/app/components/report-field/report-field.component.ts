import { Component, computed, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FieldTree, FormField } from '@angular/forms/signals';
import { DateRangeValue, ReportField, ReportParamValue } from '../../models';

/**
 * Renders one report field. To add a field type: add a model variant, a `@case` in the template,
 * and handle it in `report-field-values.ts` (the compiler flags the missing cases).
 */
@Component({
  selector: 'app-report-field',
  imports: [FormField, NgTemplateOutlet],
  templateUrl: './report-field.component.html',
  styleUrl: './report-field.component.scss',
})
export class ReportFieldComponent {
  readonly field = input.required<ReportField>();
  readonly control = input.required<FieldTree<ReportParamValue>>();

  protected readonly id = computed(() => `field-${this.field().key}`);
  protected readonly helpId = computed(() => `${this.id()}-help`);
  protected readonly errorId = computed(() => `${this.id()}-error`);

  protected readonly errors = computed(() => this.control()().errors());
  protected readonly showErrors = computed(
    () => this.control()().touched() && this.errors().length > 0,
  );

  /** Space-separated ids for `aria-describedby`, or null when there is nothing to describe. */
  protected readonly describedBy = computed(() => {
    const ids = [
      this.field().helpText ? this.helpId() : null,
      this.showErrors() ? this.errorId() : null,
    ].filter(Boolean);
    return ids.length ? ids.join(' ') : null;
  });

  // Typed views of the control for each value shape. The @switch on `field().type` guarantees
  // the model value matches, because initial values are derived from the same field config.
  protected readonly stringControl = computed(() => this.control() as unknown as FieldTree<string>);
  protected readonly numberControl = computed(
    () => this.control() as unknown as FieldTree<number | null>,
  );
  protected readonly rangeControl = computed(
    () => this.control() as unknown as FieldTree<DateRangeValue>,
  );
  protected readonly listControl = computed(() => this.control() as unknown as FieldTree<string[]>);

  protected isChecked(value: string): boolean {
    return this.listControl()().value().includes(value);
  }

  protected toggle(value: string, checked: boolean): void {
    const field = this.field();
    if (field.type !== 'multiSelect') return;
    const state = this.listControl()();
    state.value.update((values) => {
      const selected = new Set(values);
      if (checked) selected.add(value);
      else selected.delete(value);
      // Option order, not click order, so parameters and summaries read consistently.
      return field.options.map((o) => o.value).filter((v) => selected.has(v));
    });
    state.markAsTouched();
  }

  protected markTouched(): void {
    this.control()().markAsTouched();
  }
}
