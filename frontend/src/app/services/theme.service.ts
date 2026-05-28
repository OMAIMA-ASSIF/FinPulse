import { Injectable, signal, computed, effect } from '@angular/core';

export type Theme = 'dark' | 'light';

/**
 * Gère le thème de l'application pour la session courante.
 * Aucune persistance (pas de localStorage, pas de backend).
 * Le thème dark est le défaut.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly _theme = signal<Theme>('dark');

  readonly theme     = this._theme.asReadonly();
  readonly isDark    = computed(() => this._theme() === 'dark');
  readonly isLight   = computed(() => this._theme() === 'light');
  readonly themeIcon = computed(() => this._theme() === 'dark' ? '☀️' : '🌙');
  readonly themeLabel = computed(() => this._theme() === 'dark' ? 'Light mode' : 'Dark mode');

  constructor() {
    // Applique la classe CSS sur <html> à chaque changement de thème
    effect(() => {
      const theme = this._theme();
      const root  = document.documentElement;
      root.setAttribute('data-theme', theme);
      // Transition fluide
      root.style.transition = 'background-color 0.25s ease, color 0.25s ease';
    });
  }

  toggle(): void {
    this._theme.update(t => t === 'dark' ? 'light' : 'dark');
  }

  setTheme(theme: Theme): void {
    this._theme.set(theme);
  }
}
