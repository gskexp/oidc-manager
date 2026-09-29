import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ThemeService } from './services/theme.service';
import { IconComponent } from './components/icon/icon.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, IconComponent],
  template: `
    <a class="skip-link" href="#main">Skip to main content</a>
    <header class="app-header">
      <div class="container app-header__inner">
        <a class="app-header__brand" routerLink="/categories">Enterprise Reports</a>
        <button
          type="button"
          class="theme-toggle"
          [attr.aria-pressed]="theme.theme() === 'dark'"
          [title]="theme.theme() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'"
          (click)="theme.toggle()"
        >
          <app-icon [name]="theme.theme() === 'dark' ? 'sun' : 'moon'" />
          <span class="visually-hidden">Dark theme</span>
        </button>
      </div>
    </header>
    <main id="main" class="container app-main" tabindex="-1">
      <router-outlet />
    </main>
  `,
  styleUrl: './app.scss',
})
export class App {
  protected readonly theme = inject(ThemeService);
}
