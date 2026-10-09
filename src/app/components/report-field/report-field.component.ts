import { Component, computed, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FieldTree, FormField } from '@angular/forms/signals';
import { DateRangeValue, ReportField, ReportParamValue } from '../../models';

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
  protected readonly showErrors = computed(() => this.control()().touched() && this.errors().length > 0);

  protected readonly describedBy = computed(() => {
    const ids = [this.field().helpText ? this.helpId() : null, this.showErrors() ? this.errorId() : null].filter(Boolean);
    return ids.length ? ids.join(' ') : null;
  });

  protected readonly stringControl = computed(() => this.control() as unknown as FieldTree<string>);
  protected readonly numberControl = computed(() => this.control() as unknown as FieldTree<number | null>);
  protected readonly rangeControl = computed(() => this.control() as unknown as FieldTree<DateRangeValue>);
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
      return field.options.map((o) => o.value).filter((v) => selected.has(v));
    });
    state.markAsTouched();
  }

  protected markTouched(): void {
    this.control()().markAsTouched();
  }
}
