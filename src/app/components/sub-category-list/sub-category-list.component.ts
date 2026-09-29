import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfigService } from '../../services/config.service';
import { isNotFound } from '../../services/http-errors';
import { categoryAccent } from '../category-accent';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-sub-category-list',
  imports: [RouterLink, IconComponent],
  templateUrl: './sub-category-list.component.html',
  styleUrl: './sub-category-list.component.scss',
  host: { '[style.--cat]': 'accent()' },
})
export class SubCategoryListComponent {
  readonly categoryId = input.required<string>();

  private readonly configService = inject(ConfigService);
  protected readonly category = this.configService.getCategory(() => this.categoryId());
  protected readonly subCategories = this.configService.getSubCategories(() => this.categoryId());

  protected readonly loading = computed(
    () =>
      (this.category.isLoading() && !this.category.hasValue()) ||
      (this.subCategories.isLoading() && !this.subCategories.hasValue()),
  );
  /** Category colour for this page; the default primary colour until the category loads. */
  protected readonly accent = computed(() =>
    this.category.hasValue() ? categoryAccent(this.category.value()) : null,
  );
  protected readonly notFound = computed(() => isNotFound(this.category.error()));
  protected readonly failed = computed(
    () => !!this.category.error() || !!this.subCategories.error(),
  );

  protected retry(): void {
    this.category.reload();
    this.subCategories.reload();
  }
}
