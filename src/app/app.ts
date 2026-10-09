import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  template: `
    <a class="skip-link" href="#main">Skip to main content</a>
    <header class="app-header">
      <div class="container app-header__inner">
        <a class="app-header__brand" routerLink="/categories">Enterprise Reports</a>
      </div>
    </header>
    <main id="main" class="container app-main" tabindex="-1">
      <router-outlet />
    </main>
  `,
  styleUrl: './app.scss',
})
export class App {}
