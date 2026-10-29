import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAppRouter } from './router';

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function stubApi() {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url === '/api/auth/me') {
        return Promise.resolve(json({ id: 'u1', username: 'agus', displayName: 'Agus' }));
      }
      if (url === '/api/groups') return Promise.resolve(json([]));
      if (url === '/api/groups/g1') {
        return Promise.resolve(
          json({
            id: 'g1',
            name: 'Los Pibes',
            inviteCode: 'ABC',
            membersCanScore: false,
            myRole: 'MEMBER',
            myUserId: 'u1',
          }),
        );
      }
      return Promise.resolve(json({ detail: 'Sin ruta simulada' }, 404));
    }),
  );
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={createAppRouter([path])} />
    </QueryClientProvider>,
  );
}

describe('Breadcrumbs', () => {
  beforeEach(() => stubApi());
  afterEach(() => vi.unstubAllGlobals());

  it('muestra Mis grupos › Grupo › Partidos › Partido y la página actual sin link', async () => {
    renderAt('/grupos/g1/partidos/m1');

    const nav = await screen.findByRole('navigation', { name: 'Migas de pan' });
    expect(await within(nav).findByRole('link', { name: 'Los Pibes' })).toHaveAttribute(
      'href',
      '/grupos/g1',
    );
    expect(within(nav).getByRole('link', { name: 'Mis grupos' })).toHaveAttribute(
      'href',
      '/grupos',
    );
    expect(within(nav).getByRole('link', { name: 'Partidos' })).toHaveAttribute(
      'href',
      '/grupos/g1/partidos',
    );
    expect(within(nav).queryByRole('link', { name: 'Partido' })).not.toBeInTheDocument();
    expect(within(nav).getByText('Partido')).toHaveAttribute('aria-current', 'page');
  });

  it('en el modo en vivo enlaza al partido y al grupo', async () => {
    renderAt('/grupos/g1/partidos/m1/en-vivo');

    const nav = await screen.findByRole('navigation', { name: 'Migas de pan' });
    expect(within(nav).getByRole('link', { name: 'Partido' })).toHaveAttribute(
      'href',
      '/grupos/g1/partidos/m1',
    );
    expect(within(nav).getByText('En vivo')).toHaveAttribute('aria-current', 'page');
  });

  it('no aparece fuera de un grupo y el título del encabezado lleva a Mis grupos', async () => {
    const user = userEvent.setup();
    renderAt('/grupos/g1/ranking');

    await screen.findByRole('navigation', { name: 'Migas de pan' });
    await user.click(screen.getByRole('link', { name: 'Fútbol Manager' }));

    expect(await screen.findByRole('heading', { name: 'Mis grupos' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Migas de pan' })).not.toBeInTheDocument();
  });
});
