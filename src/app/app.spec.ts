import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  it('renders the header and a skip link', async () => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] });

    const fixture = TestBed.createComponent(App);

    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.app-header__brand')?.textContent).toContain('Enterprise Reports');
    expect(el.querySelector('a.skip-link')?.getAttribute('href')).toBe('#main');
    expect(el.querySelector('.theme-toggle')?.getAttribute('aria-pressed')).toBe('false');
  });
});
