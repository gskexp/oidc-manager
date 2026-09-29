import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <nav class="breadcrumb" aria-label="Breadcrumb">
      <ol>
        <li><a routerLink="/categories">Categories</a></li>
        <li aria-current="page">Page not found</li>
      </ol>
    </nav>
    <section class="state state--empty">
      <h1>Page not found</h1>
      <p>The page you're looking for doesn't exist or has moved.</p>
      <a class="btn btn--primary" routerLink="/categories">Go to categories</a>
    </section>
  `,
})
export class NotFoundComponent {}
