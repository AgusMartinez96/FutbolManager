import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAppRouter } from '../../app/router';

type Route = (init?: RequestInit) => Response | undefined;

const ME = { id: 'u1', username: 'agus', displayName: 'Agus' };
const GROUP_ID = 'g1';

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function problem(status: number, detail: string) {
  return new Response(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function stubApi(routes: Record<string, Route>) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (url === '/api/auth/me') {
      return Promise.resolve(json(ME));
    }
    const response = routes[`${method} ${url}`]?.(init);
    return Promise.resolve(response ?? problem(404, `Sin ruta simulada: ${method} ${url}`));
  });
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

function groupDetail(myRole: 'ADMIN' | 'MEMBER') {
  return {
    id: GROUP_ID,
    name: 'Los del jueves',
    inviteCode: 'ABCD2345',
    membersCanScore: false,
    myRole,
    myUserId: ME.id,
  };
}

const MEMBERS = [
  { userId: 'u1', username: 'agus', displayName: 'Agus', role: 'MEMBER' },
  { userId: 'u2', username: 'lu', displayName: 'Lu', role: 'ADMIN' },
];
const PLAYERS = [
  {
    id: 'p1',
    name: 'Agus',
    nickname: null,
    preferredPosition: 'MID',
    dominantFoot: 'RIGHT',
    currentElo: 1000,
    userId: 'u1',
  },
];

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('GroupsPage', () => {
  it('sin grupos muestra el estado vacío con las dos acciones', async () => {
    stubApi({ 'GET /api/groups': () => json([]) });
    renderAt('/grupos');

    expect(await screen.findByText('Todavía no tenés grupos')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear grupo' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Unirme con código' })).toBeInTheDocument();
  });

  it('crear un grupo hace POST con el nombre y navega al detalle', async () => {
    const fetchMock = stubApi({
      'GET /api/groups': () => json([]),
      'POST /api/groups': () =>
        json({ id: GROUP_ID, name: 'Los del jueves', myRole: 'ADMIN' }, 201),
      [`GET /api/groups/${GROUP_ID}`]: () => json(groupDetail('ADMIN')),
      [`GET /api/groups/${GROUP_ID}/players`]: () => json(PLAYERS),
    });
    const user = userEvent.setup();
    const router = renderAt('/grupos');

    await user.click(await screen.findByRole('button', { name: 'Crear grupo' }));
    await user.type(screen.getByLabelText('Nombre del grupo'), 'Los del jueves');
    await user.click(screen.getByRole('button', { name: 'Crear' }));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/grupos/${GROUP_ID}`));
    const post = fetchMock.mock.calls.find(
      ([url, init]) => String(url) === '/api/groups' && init?.method === 'POST',
    );
    expect(post?.[1]?.body).toBe(JSON.stringify({ name: 'Los del jueves' }));
  });

  it('si falla la lista muestra el error y Reintentar repite la petición', async () => {
    let calls = 0;
    const fetchMock = stubApi({
      'GET /api/groups': () => {
        calls += 1;
        return calls === 1 ? problem(500, 'boom') : json([]);
      },
    });
    const user = userEvent.setup();
    renderAt('/grupos');

    expect(await screen.findByText('No pudimos cargar tus grupos.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Todavía no tenés grupos')).toBeInTheDocument();
    const listCalls = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/groups');
    expect(listCalls).toHaveLength(2);
  });
});

describe('JoinByLinkPage', () => {
  it('une con el código del link y navega al grupo', async () => {
    const fetchMock = stubApi({
      'POST /api/groups/join': () => json({ groupId: GROUP_ID }),
      [`GET /api/groups/${GROUP_ID}`]: () => json(groupDetail('MEMBER')),
      [`GET /api/groups/${GROUP_ID}/players`]: () => json(PLAYERS),
    });
    const router = renderAt('/unirse/ABCD2345');

    await waitFor(() => expect(router.state.location.pathname).toBe(`/grupos/${GROUP_ID}`));
    const join = fetchMock.mock.calls.find(([url]) => String(url) === '/api/groups/join');
    expect(join?.[1]?.body).toBe(JSON.stringify({ inviteCode: 'ABCD2345' }));
  });

  it('con un código inexistente muestra el error del servidor', async () => {
    stubApi({ 'POST /api/groups/join': () => problem(404, 'Código de invitación inválido.') });
    renderAt('/unirse/ZZZZZZZZ');

    expect(await screen.findByText('Código de invitación inválido.')).toBeInTheDocument();
  });
});

describe('GroupDetailPage', () => {
  function stubGroup(role: 'ADMIN' | 'MEMBER') {
    stubApi({
      [`GET /api/groups/${GROUP_ID}`]: () => json(groupDetail(role)),
      [`GET /api/groups/${GROUP_ID}/players`]: () => json(PLAYERS),
      [`GET /api/groups/${GROUP_ID}/members`]: () => json(MEMBERS),
    });
  }

  it('para un MEMBER no muestra los controles de admin', async () => {
    stubGroup('MEMBER');
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}`);

    expect(await screen.findByRole('heading', { level: 1, name: 'Los del jueves' })).toBeVisible();
    expect(await screen.findByText('Agus')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Agregar jugador' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Miembros' }));
    expect(await screen.findByText('Lu')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cambiar rol/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Salir del grupo' })).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Ajustes' }));
    expect(screen.queryByRole('button', { name: 'Regenerar código' })).not.toBeInTheDocument();
  });

  it('para un ADMIN muestra Agregar jugador, Cambiar rol y Regenerar código', async () => {
    stubGroup('ADMIN');
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}`);

    expect(await screen.findByRole('button', { name: 'Agregar jugador' })).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Miembros' }));
    expect(await screen.findAllByRole('button', { name: /Cambiar rol/ })).toHaveLength(2);

    await user.click(screen.getByRole('tab', { name: 'Ajustes' }));
    expect(screen.getByRole('button', { name: 'Regenerar código' })).toBeInTheDocument();
  });
});

describe('nivel de jugador', () => {
  const playersUrl = `/api/groups/${GROUP_ID}/players`;

  function stubLevelGroup(role: 'ADMIN' | 'MEMBER') {
    const withLevel = role === 'ADMIN' ? PLAYERS.map((p) => ({ ...p, level: 5 })) : PLAYERS;
    return stubApi({
      [`GET /api/groups/${GROUP_ID}`]: () => json(groupDetail(role)),
      [`GET ${playersUrl}`]: () => json(withLevel),
      [`POST ${playersUrl}`]: () => json({ ...withLevel[0], id: 'p2', level: 7 }, 201),
      [`PATCH ${playersUrl}/p1`]: () => json({ ...withLevel[0], level: 9 }),
    });
  }

  function findCall(fetchMock: ReturnType<typeof stubApi>, method: string, url: string) {
    return fetchMock.mock.calls.find(
      ([input, init]) => String(input) === url && init?.method === method,
    );
  }

  it.each([
    ['8,5', 8.5],
    ['8.5', 8.5],
    ['8,50', 8.5],
    ['8.50', 8.5],
    ['4,8', 4.8],
    ['7', 7],
  ])('el alta del admin con nivel «%s» envía %d como número', async (text, expected) => {
    const fetchMock = stubLevelGroup('ADMIN');
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Agregar jugador' }));
    await user.type(screen.getByLabelText('Nombre'), 'Nico');
    await user.type(screen.getByLabelText('Nivel (1,0 a 10,0)'), text);
    await user.click(screen.getByRole('button', { name: 'Agregar' }));

    await waitFor(() => expect(findCall(fetchMock, 'POST', playersUrl)).toBeDefined());
    const body = JSON.parse(String(findCall(fetchMock, 'POST', playersUrl)?.[1]?.body));
    expect(body.level).toBe(expected);
  });

  it.each(['', '0,9', '10,1', '8,55', '8,51', 'abc'])(
    'con nivel «%s» no envía y marca el campo inválido',
    async (text) => {
      const fetchMock = stubLevelGroup('ADMIN');
      const user = userEvent.setup();
      renderAt(`/grupos/${GROUP_ID}`);

      await user.click(await screen.findByRole('button', { name: 'Agregar jugador' }));
      await user.type(screen.getByLabelText('Nombre'), 'Nico');
      if (text) {
        await user.type(screen.getByLabelText('Nivel (1,0 a 10,0)'), text);
      }
      await user.click(screen.getByRole('button', { name: 'Agregar' }));

      const field = screen.getByLabelText('Nivel (1,0 a 10,0)');
      expect(field).toHaveAttribute('aria-invalid', 'true');
      expect(
        screen.getByText('Ingresá un nivel de 1,0 a 10,0 con un decimal.'),
      ).toBeInTheDocument();
      expect(findCall(fetchMock, 'POST', playersUrl)).toBeUndefined();
    },
  );

  it('la edición del admin envía el nivel nuevo con PATCH', async () => {
    const fetchMock = stubLevelGroup('ADMIN');
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}`);

    expect(await screen.findByText('Nivel 5,0')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Editar' }));
    const field = screen.getByLabelText('Nivel (1,0 a 10,0)');
    expect(field).toHaveValue('5,0');
    await user.clear(field);
    await user.type(field, '9,5');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(findCall(fetchMock, 'PATCH', `${playersUrl}/p1`)).toBeDefined());
    const body = JSON.parse(String(findCall(fetchMock, 'PATCH', `${playersUrl}/p1`)?.[1]?.body));
    expect(body.level).toBe(9.5);
  });

  it('un MEMBER no ve ningún nivel en la lista', async () => {
    stubLevelGroup('MEMBER');
    renderAt(`/grupos/${GROUP_ID}`);

    expect(await screen.findByText('Agus')).toBeInTheDocument();
    expect(screen.queryByText(/Nivel/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Nivel/)).not.toBeInTheDocument();
  });
});
