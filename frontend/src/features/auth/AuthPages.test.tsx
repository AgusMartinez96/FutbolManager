import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { createAppRouter } from '../../app/router';
import { apiFetch } from '../../lib/api';

type Handler = (url: string, init?: RequestInit) => Response;

function problem(status: number, detail: string, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json', ...headers },
  });
}

function stubFetch(handler: Handler) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
    Promise.resolve(handler(String(input), init)),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createAppRouter([path]);
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

describe('LoginPage', () => {
  it('con el usuario vacío marca el campo, enlaza el error, enfoca y avisa en un alert', async () => {
    stubFetch(() => problem(401, 'x'));
    const user = userEvent.setup();
    renderAt('/ingresar');

    await user.type(screen.getByLabelText('Contraseña'), 'secreta123');
    await user.click(screen.getByRole('button', { name: 'Ingresar' }));

    const username = screen.getByLabelText('Usuario');
    expect(username).toHaveAttribute('aria-invalid', 'true');
    const errorId = username.getAttribute('aria-describedby');
    expect(errorId).toBeTruthy();
    expect(document.getElementById(errorId as string)).toHaveTextContent('Ingresá tu usuario.');
    expect(username).toHaveFocus();
    expect(screen.getByRole('alert')).toHaveTextContent('Revisá los campos marcados.');
  });

  it('con 401 muestra el error de credenciales y conserva el usuario', async () => {
    stubFetch(() => problem(401, 'Usuario o contraseña incorrectos.'));
    document.cookie = 'XSRF-TOKEN=tok; path=/';
    const user = userEvent.setup();
    renderAt('/ingresar');

    await user.type(screen.getByLabelText('Usuario'), 'agus');
    await user.type(screen.getByLabelText('Contraseña'), 'incorrecta1');
    await user.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByText('Usuario o contraseña incorrectos.')).toBeInTheDocument();
    expect(screen.getByLabelText('Usuario')).toHaveValue('agus');
  });

  it('con 429 y Retry-After 30 informa los segundos', async () => {
    stubFetch(() => problem(429, 'Demasiados intentos.', { 'Retry-After': '30' }));
    document.cookie = 'XSRF-TOKEN=tok; path=/';
    const user = userEvent.setup();
    renderAt('/ingresar');

    await user.type(screen.getByLabelText('Usuario'), 'agus');
    await user.type(screen.getByLabelText('Contraseña'), 'incorrecta1');
    await user.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByText(/30 segundos/)).toBeInTheDocument();
  });
});

describe('RegisterPage', () => {
  it('valida los tres campos al enviar', async () => {
    stubFetch(() => problem(400, 'x'));
    const user = userEvent.setup();
    renderAt('/registro');

    expect(screen.getByRole('heading', { level: 1, name: 'Crear cuenta' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(screen.getByLabelText('Usuario')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Nombre para mostrar')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('aria-invalid', 'true');
  });
});

describe('RequireAuth', () => {
  it('redirige a /ingresar?next= cuando no hay sesión', async () => {
    stubFetch(() => problem(401, 'No autenticado.'));
    const router = renderAt('/grupos');

    await waitFor(() => expect(router.state.location.pathname).toBe('/ingresar'));
    expect(router.state.location.search).toBe('?next=%2Fgrupos');
  });
});

describe('apiFetch', () => {
  it('manda X-XSRF-TOKEN igual a la cookie XSRF-TOKEN', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/';
    const fetchMock = stubFetch(() => new Response(null, { status: 204 }));

    await apiFetch('/api/auth/logout', { method: 'POST' });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Record<string, string>;
    expect(headers['X-XSRF-TOKEN']).toBe('abc123');
  });

  it('llama antes a GET /api/auth/csrf si la cookie no existe', async () => {
    const fetchMock = stubFetch((url) => {
      if (url === '/api/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=fresh; path=/';
        return new Response('{"headerName":"X-XSRF-TOKEN","token":"fresh"}', { status: 200 });
      }
      return new Response(null, { status: 204 });
    });

    await apiFetch('/api/auth/logout', { method: 'POST' });

    expect(String(fetchMock.mock.calls[0]?.[0])).toBe('/api/auth/csrf');
    const headers = fetchMock.mock.calls[1]?.[1]?.headers as Record<string, string>;
    expect(headers['X-XSRF-TOKEN']).toBe('fresh');
  });
});
