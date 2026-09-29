import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'theme';

/**
 * Light/dark theme. Follows the OS setting until the user picks one; the pick is saved in
 * localStorage and applied as data-theme on <html> (index.html applies it before first paint).
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly media = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
  private readonly systemTheme = signal<Theme>(this.media?.matches ? 'dark' : 'light');

  /** The user's explicit choice, or null to follow the OS. */
  readonly choice = signal<Theme | null>(readStoredTheme());
  readonly theme = computed<Theme>(() => this.choice() ?? this.systemTheme());

  constructor() {
    this.media?.addEventListener('change', (e) =>
      this.systemTheme.set(e.matches ? 'dark' : 'light'),
    );
    effect(() => {
      const choice = this.choice();
      const root = this.document.documentElement;
      if (choice) root.dataset['theme'] = choice;
      else delete root.dataset['theme'];
      try {
        if (choice) localStorage.setItem(STORAGE_KEY, choice);
        else localStorage.removeItem(STORAGE_KEY);
      } catch {
        // Storage unavailable (e.g. privacy mode): the choice lasts for this page only.
      }
    });
  }

  /** Switches to the other theme. Choosing the OS's own theme goes back to following the OS. */
  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.choice.set(next === this.systemTheme() ? null : next);
  }
}

function readStoredTheme(): Theme | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : null;
  } catch {
    return null;
  }
}
