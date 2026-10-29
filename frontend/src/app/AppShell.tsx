import { Moon, Sun } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, Outlet } from 'react-router';
import { Breadcrumbs } from './Breadcrumbs';

type Theme = 'dark' | 'light';

const THEME_KEY = 'fm-theme';

function currentTheme(): Theme {
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'dark';
}

function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme);
  const next: Theme = theme === 'dark' ? 'light' : 'dark';

  function toggle() {
    document.documentElement.dataset.theme = next;
    try {
      window.localStorage.setItem(THEME_KEY, next);
    } catch {
      // Storage blocked (private mode): the theme still applies for this visit.
    }
    setTheme(next);
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={next === 'light' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'}
      className="inline-flex size-10 items-center justify-center rounded-lg border border-border-strong text-text hover:bg-surface-raised"
    >
      {theme === 'dark' ? (
        <Sun aria-hidden="true" size={20} />
      ) : (
        <Moon aria-hidden="true" size={20} />
      )}
    </button>
  );
}

export function AppShell({ actions }: { actions?: ReactNode }) {
  return (
    <div className="min-h-dvh bg-background text-text">
      <a
        href="#main"
        className="sr-only rounded-lg bg-primary px-4 py-2 text-on-primary focus:not-sr-only focus:absolute focus:top-2 focus:left-2"
      >
        Saltar al contenido
      </a>
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex max-w-240 items-center justify-between gap-4 px-4 py-3">
          <Link to="/grupos" className="text-xl font-bold">
            Fútbol Manager
          </Link>
          <div className="flex items-center gap-2">
            {actions}
            <ThemeToggle />
          </div>
        </div>
      </header>
      <Breadcrumbs />
      <main id="main" className="mx-auto max-w-240 px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
