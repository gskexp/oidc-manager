import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfigService } from '../../services/config.service';
import { isNotFound } from '../../services/error-message';

@Component({
  selector: 'app-sub-category-list',
  imports: [RouterLink],
  templateUrl: './sub-category-list.component.html',
  styleUrl: './sub-category-list.component.scss',
})
export class SubCategoryListComponent {
  readonly categoryId = input.required<string>();

  private readonly configService = inject(ConfigService);
  protected readonly category = this.configService.getCategory(() => this.categoryId());
  protected readonly subCategories = this.configService.getSubCategories(() => this.categoryId());

  protected readonly loading = computed(
    () => (this.category.isLoading() && !this.category.hasValue()) || (this.subCategories.isLoading() && !this.subCategories.hasValue()),
  );
  protected readonly notFound = computed(() => isNotFound(this.category.error()));
  protected readonly failed = computed(() => !!this.category.error() || !!this.subCategories.error());

  protected retry(): void {
    this.category.reload();
    this.subCategories.reload();
  }
}
