import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfigService } from '../../services/config.service';
import { categoryAccent } from '../category-accent';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-category-list',
  imports: [RouterLink, IconComponent],
  templateUrl: './category-list.component.html',
  styleUrl: './category-list.component.scss',
})
export class CategoryListComponent {
  protected readonly categories = inject(ConfigService).getCategories();
  protected readonly accent = categoryAccent;
}
