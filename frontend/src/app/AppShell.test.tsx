import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AppShell } from './AppShell';

function renderShell() {
  const router = createMemoryRouter(
    [{ element: <AppShell />, children: [{ index: true, element: <h1>Mis grupos</h1> }] }],
    { initialEntries: ['/'] },
  );
  return render(<RouterProvider router={router} />);
}

describe('AppShell', () => {
  it('expone skip link, un único h1 y el landmark main', () => {
    renderShell();

    expect(screen.getByRole('link', { name: 'Saltar al contenido' })).toHaveAttribute(
      'href',
      '#main',
    );
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main');
  });

  it('alterna el tema y lo guarda en localStorage', async () => {
    const user = userEvent.setup();
    renderShell();

    await user.click(screen.getByRole('button', { name: 'Cambiar a tema claro' }));
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(window.localStorage.getItem('fm-theme')).toBe('light');

    await user.click(screen.getByRole('button', { name: 'Cambiar a tema oscuro' }));
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(window.localStorage.getItem('fm-theme')).toBe('dark');
  });
});
