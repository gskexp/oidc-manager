import { Component, DOCUMENT, ElementRef, Injector, afterNextRender, computed, inject, input, linkedSignal, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FieldTree, FormRoot, applyEach, form, validate } from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';
import { ReportColumn, ReportParamValue, ReportRunRequest, ReportRunResponse } from '../../models';
import { errorMessage } from '../../services/error-message';
import { saveFile } from '../../services/save-file';
import { ConfigService } from '../../services/config.service';
import { isNotFound } from '../../services/http-errors';
import { ReportService } from '../../services/report.service';
import { categoryAccent } from '../category-accent';
import { ReportFieldComponent } from '../report-field/report-field.component';
import { ReportResultsComponent } from '../report-results/report-results.component';
import { ReportFormModel, formatValue, initialModel, toParameter, validateValue } from '../report-field/report-field-values';
import { layoutFields, validateChoice } from './field-choices';

type RunState =
  | { status: 'idle' }
  | { status: 'running' }
  | {
      status: 'success';
      request: ReportRunRequest; // what was run, reused for the Excel export
      response: ReportRunResponse;
      columns: ReportColumn[];
      summary: { label: string; value: string }[];
    }
  | { status: 'error'; message: string };

@Component({
  selector: 'app-report-form',
  imports: [RouterLink, FormRoot, ReportFieldComponent, ReportResultsComponent],
  templateUrl: './report-form.component.html',
  styleUrl: './report-form.component.scss',
  host: { '[style.--cat]': 'accent()' },
})
export class ReportFormComponent {
  readonly reportConfigId = input.required<string>();
  /** Optional `?category=` query param, used only for the breadcrumb. */
  readonly category = input<string>();

  private readonly reportService = inject(ReportService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly resultsHeading = viewChild<ElementRef<HTMLElement>>('resultsHeading');

  protected readonly canExport = this.reportService.canExport;

  private readonly configService = inject(ConfigService);
  protected readonly config = this.configService.getReportConfig(() => this.reportConfigId());
  protected readonly parentCategory = this.configService.getCategory(() => this.category());
  /** Colour of the category the user came from, if known; otherwise the default primary colour. */
  protected readonly accent = computed(() => (this.parentCategory.hasValue() ? categoryAccent(this.parentCategory.value()) : null));
  protected readonly parentName = computed(() => (this.parentCategory.hasValue() ? this.parentCategory.value().name : 'Category'));
  protected readonly notFound = computed(() => isNotFound(this.config.error()));

  protected readonly fields = computed(() => (this.config.hasValue() ? this.config.value().fields : []));
  private readonly fieldsByKey = computed(() => new Map(this.fields().map((f) => [f.key, f])));
  /** Fields and either/or groups, in display order. */
  protected readonly items = computed(() => layoutFields(this.fields(), this.config.hasValue() ? this.config.value().choices : []));
  /** Choice errors are shown only after a run attempt, so a half-filled form isn't flagged early. */
  protected readonly submitted = signal(false);

  /** Form model; resets to the config's defaults whenever a different config loads. */
  protected readonly model = linkedSignal<ReportFormModel>(() => initialModel(this.fields()));

  protected readonly reportForm = form(
    this.model,
    (root) => {
      // Fields are only known at runtime, so validate each child against its config.
      applyEach(root, (child) => {
        validate(child, ({ key, value }) => {
          const field = this.fieldsByKey().get(key());
          return field ? validateValue(field, value()) : undefined;
        });
      });
      // Either/or groups span several fields, so they are checked on the whole form.
      validate(root, ({ value }) =>
        this.items().flatMap((item) => {
          if (item.kind !== 'choice') return [];
          const message = validateChoice(item.alternatives, item.choice, value());
          return message ? [{ kind: item.id, message }] : [];
        }),
      );
    },
    {
      submission: {
        action: async () => {
          this.submitted.set(true);
          await this.run();
          return undefined;
        },
        onInvalid: () => {
          this.submitted.set(true);
          this.focusFirstInvalid();
        },
      },
    },
  );

  /** Run status; clears when navigating to another report. */
  protected readonly runState = linkedSignal<string, RunState>({
    source: () => this.reportConfigId(),
    computation: () => ({ status: 'idle' }),
  });
  protected readonly running = computed(() => this.runState().status === 'running');

  protected readonly exporting = signal(false);
  protected readonly exportError = signal<string | null>(null);

  protected controlFor(key: string): FieldTree<ReportParamValue> {
    return (this.reportForm as unknown as Record<string, FieldTree<ReportParamValue>>)[key];
  }

  protected choiceError(id: string): string | null {
    if (!this.submitted()) return null;
    return (
      this.reportForm()
        .errors()
        .find((e) => e.kind === id)?.message ?? null
    );
  }

  protected reset(): void {
    this.reportForm().reset(initialModel(this.fields()));
    this.submitted.set(false);
    this.runState.set({ status: 'idle' });
    this.exportError.set(null);
  }

  private async run(): Promise<void> {
    const config = this.config.value();
    if (!config) return;

    const values = this.model();
    const request: ReportRunRequest = {
      reportConfigId: config.reportConfigId,
      parameters: Object.fromEntries(config.fields.map((field) => [field.key, toParameter(field, values[field.key] ?? null)])),
    };

    this.runState.set({ status: 'running' });
    this.exportError.set(null);
    // The component is reused when navigating between reports; drop results for a report the
    // user has already left.
    const stillOnReport = () => this.reportConfigId() === request.reportConfigId;
    try {
      const response = await firstValueFrom(this.reportService.runReport(request));
      if (!stillOnReport()) return;
      if (response.status === 'failed') {
        this.runState.set({
          status: 'error',
          message: response.message ?? 'The report could not be run.',
        });
        return;
      }
      this.runState.set({
        status: 'success',
        request,
        response,
        // Mock results may omit columns; fall back to the config's.
        columns: response.columns.length ? response.columns : (config.columns ?? []),
        summary: config.fields.map((field) => ({
          label: field.label,
          value: formatValue(field, request.parameters[field.key]),
        })),
      });
      // Move focus to the new results so keyboard and screen-reader users land on them.
      afterNextRender(() => this.resultsHeading()?.nativeElement.focus(), {
        injector: this.injector,
      });
    } catch (error) {
      if (!stillOnReport()) return;
      this.runState.set({
        status: 'error',
        message: errorMessage(error, 'The report could not be run. Your inputs have been kept, so you can try again.'),
      });
    }
  }

  /** Downloads the last run as Excel. The server re-runs it with the same parameters. */
  protected async exportExcel(): Promise<void> {
    const state = this.runState();
    if (state.status !== 'success' || this.exporting()) return;
    this.exporting.set(true);
    this.exportError.set(null);
    try {
      const file = await firstValueFrom(this.reportService.exportReport(state.request));
      saveFile(this.document, file.blob, file.fileName);
    } catch (error) {
      this.exportError.set(errorMessage(error, 'The Excel file could not be created. Try again.'));
    } finally {
      this.exporting.set(false);
    }
  }

  private focusFirstInvalid(): void {
    // Wait for error messages to render before moving focus.
    setTimeout(() => {
      this.host.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
    });
  }
}
