import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  const root = document.documentElement;

  beforeEach(() => {
    localStorage.clear();
    delete root.dataset['theme'];
  });

  function create(): ThemeService {
    const service = TestBed.inject(ThemeService);
    TestBed.tick(); // run the effect that applies the choice
    return service;
  }

  it('follows the OS (light here) until the user chooses', () => {
    const theme = create();
    expect(theme.theme()).toBe('light');
    expect(root.dataset['theme']).toBeUndefined();
  });

  it('saves and applies an explicit dark choice', () => {
    const theme = create();
    theme.toggle();
    TestBed.tick();
    expect(theme.theme()).toBe('dark');
    expect(root.dataset['theme']).toBe('dark');
    expect(localStorage.getItem('theme')).toBe('dark');
  });

  it('goes back to following the OS when the OS theme is chosen again', () => {
    const theme = create();
    theme.toggle();
    TestBed.tick();
    theme.toggle();
    TestBed.tick();
    expect(theme.choice()).toBeNull();
    expect(root.dataset['theme']).toBeUndefined();
    expect(localStorage.getItem('theme')).toBeNull();
  });

  it('restores a saved choice', () => {
    localStorage.setItem('theme', 'dark');
    expect(create().theme()).toBe('dark');
  });
});
