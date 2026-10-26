import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAppRouter } from '../../app/router';

type Route = (init?: RequestInit) => Response | undefined;

const ME = { id: 'u1', username: 'agus', displayName: 'Agus' };
const GROUP_ID = 'g1';
const MATCH_ID = 'm1';
const MATCH_URL = `/api/groups/${GROUP_ID}/matches/${MATCH_ID}`;

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

function player(id: string, name: string, elo: number, position: string, userId: string | null) {
  return {
    id,
    name,
    nickname: null,
    preferredPosition: position,
    dominantFoot: 'RIGHT',
    currentElo: elo,
    userId,
  };
}

const PLAYERS = [
  player('p1', 'Agus', 1000, 'MID', 'u1'),
  player('p2', 'Lu', 1100, 'FWD', 'u2'),
  player('p3', 'Tomi', 1050, 'DEF', null),
  player('p4', 'Nico', 1020, 'GK', null),
];

const MATCH = {
  id: MATCH_ID,
  playedAt: '2026-11-05T21:00:00Z',
  location: 'Cancha 1',
  format: 7,
  status: 'SCHEDULED',
  scoreA: null,
  scoreB: null,
  mvpPlayerId: null,
  callUps: [],
  lineup: [],
};

const BALANCE = {
  teamA: [
    { playerId: 'p1', name: 'Agus', currentElo: 1000, level: 6.5, position: 'MID' },
    { playerId: 'p2', name: 'Lu', currentElo: 1100, level: 7.5, position: 'FWD' },
  ],
  teamB: [
    { playerId: 'p3', name: 'Tomi', currentElo: 1050, level: 7, position: 'DEF' },
    { playerId: 'p4', name: 'Nico', currentElo: 1020, level: 6.5, position: 'GK' },
  ],
  avgLevelA: 7,
  avgLevelB: 6.75,
  levelDifference: 0.25,
  avgEloA: 1050,
  avgEloB: 1035,
  eloDifference: 15,
};

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('MatchesPage', () => {
  it('sin partidos un admin ve el vacío y la acción Crear partido', async () => {
    stubApi({
      [`GET /api/groups/${GROUP_ID}`]: () => json(groupDetail('ADMIN')),
      [`GET /api/groups/${GROUP_ID}/matches`]: () => json([]),
    });
    renderAt(`/grupos/${GROUP_ID}/partidos`);

    expect(await screen.findByText('No hay partidos todavía.')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Crear partido' })).toBeInTheDocument();
  });

  it('crear un partido hace POST con fecha ISO, lugar y formato y navega al detalle', async () => {
    const fetchMock = stubApi({
      [`GET /api/groups/${GROUP_ID}`]: () => json(groupDetail('ADMIN')),
      [`GET /api/groups/${GROUP_ID}/matches`]: () => json([]),
      [`POST /api/groups/${GROUP_ID}/matches`]: () => json(MATCH, 201),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
      [`GET /api/groups/${GROUP_ID}/players`]: () => json(PLAYERS),
    });
    const user = userEvent.setup();
    const router = renderAt(`/grupos/${GROUP_ID}/partidos`);

    await user.click(await screen.findByRole('button', { name: 'Crear partido' }));
    fireEvent.change(screen.getByLabelText('Fecha y hora'), {
      target: { value: '2026-11-05T21:00' },
    });
    await user.type(screen.getByLabelText('Lugar'), 'Cancha 1');
    await user.click(screen.getByRole('button', { name: 'Crear' }));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`),
    );
    const post = fetchMock.mock.calls.find(
      ([url, init]) => String(url) === `/api/groups/${GROUP_ID}/matches` && init?.method === 'POST',
    );
    expect(post?.[1]?.body).toBe(
      JSON.stringify({
        playedAt: new Date('2026-11-05T21:00').toISOString(),
        location: 'Cancha 1',
        format: 7,
      }),
    );
  });

  it('si falla la lista muestra el error y Reintentar repite la petición', async () => {
    let calls = 0;
    stubApi({
      [`GET /api/groups/${GROUP_ID}`]: () => json(groupDetail('MEMBER')),
      [`GET /api/groups/${GROUP_ID}/matches`]: () => {
        calls += 1;
        return calls === 1 ? problem(500, 'boom') : json([]);
      },
    });
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}/partidos`);

    expect(await screen.findByText('No pudimos cargar los partidos.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('No hay partidos todavía.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Crear partido' })).not.toBeInTheDocument();
  });
});

describe('MatchSetupPage', () => {
  function stubSetup(role: 'ADMIN' | 'MEMBER', extra: Record<string, Route> = {}) {
    return stubApi({
      [`GET /api/groups/${GROUP_ID}`]: () => json(groupDetail(role)),
      [`GET /api/groups/${GROUP_ID}/players`]: () => json(PLAYERS),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
      ...extra,
    });
  }

  it('un MEMBER ve solo su propia asistencia y no el armado de equipos', async () => {
    stubSetup('MEMBER');
    renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

    expect(await screen.findByLabelText('Asistencia de Agus')).toBeInTheDocument();
    expect(screen.queryByLabelText('Asistencia de Lu')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Armar equipos' })).not.toBeInTheDocument();
    expect(screen.queryByText(/nivel/i)).not.toBeInTheDocument();
  });

  it('Armar equipos llama a balance sin cuerpo y muestra nivel promedio, diferencia de nivel y ELO', async () => {
    const fetchMock = stubSetup('ADMIN', {
      [`POST ${MATCH_URL}/balance`]: () => json(BALANCE),
    });
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Armar equipos' }));

    expect(await screen.findByRole('heading', { name: 'Equipo A' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Equipo B' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Un arquero por equipo')).not.toBeInTheDocument();
    expect(screen.getAllByText(/Nivel promedio/)).toHaveLength(2);
    expect(screen.getByText(/Diferencia de nivel:/)).toHaveTextContent('Diferencia de nivel: 0,3');
    expect(screen.getAllByText(/ELO promedio/)).toHaveLength(2);
    expect(screen.getByText(/Diferencia de ELO:/)).toHaveTextContent('Diferencia de ELO: 15');
    const balance = fetchMock.mock.calls.find(([url]) => String(url) === `${MATCH_URL}/balance`);
    expect(balance?.[1]?.body).toBeUndefined();
  });

  it('Pasar al equipo B mueve al jugador con el teclado y los selects de puesto tienen nombre accesible', async () => {
    stubSetup('ADMIN', { [`POST ${MATCH_URL}/balance`]: () => json(BALANCE) });
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Armar equipos' }));
    const teamA = (await screen.findByRole('heading', { name: 'Equipo A' })).closest('div')!;
    expect(within(teamA).getByLabelText('Puesto de Agus')).toBeInTheDocument();

    const move = within(teamA).getByRole('button', { name: /Pasar al equipo B \(Agus\)/ });
    move.focus();
    await user.keyboard('{Enter}');

    const teamB = screen.getByRole('heading', { name: 'Equipo B' }).closest('div')!;
    expect(within(teamB).getByLabelText('Puesto de Agus')).toBeInTheDocument();
    expect(within(teamA).queryByLabelText('Puesto de Agus')).not.toBeInTheDocument();
  });

  it('Guardar equipos hace PUT del lineup con una entrada por jugador', async () => {
    const fetchMock = stubSetup('ADMIN', {
      [`POST ${MATCH_URL}/balance`]: () => json(BALANCE),
      [`PUT ${MATCH_URL}/lineup`]: () => json([]),
    });
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Armar equipos' }));
    await screen.findByRole('heading', { name: 'Equipo A' });
    await user.selectOptions(screen.getByLabelText('Puesto de Lu'), 'DEF');
    await user.click(screen.getByRole('button', { name: 'Guardar equipos' }));

    await waitFor(() => {
      const put = fetchMock.mock.calls.find(([url]) => String(url) === `${MATCH_URL}/lineup`);
      expect(put?.[1]?.body).toBe(
        JSON.stringify({
          entries: [
            { playerId: 'p1', team: 'A', position: 'MID' },
            { playerId: 'p2', team: 'A', position: 'DEF' },
            { playerId: 'p3', team: 'B', position: 'DEF' },
            { playerId: 'p4', team: 'B', position: 'GK' },
          ],
        }),
      );
    });
  });

  describe('equipos guardados', () => {
    const SAVED_MATCH = {
      ...MATCH,
      lineup: [
        { playerId: 'p1', name: 'Agus', currentElo: 1000, team: 'A', position: 'MID' },
        { playerId: 'p2', name: 'Lu', currentElo: 1100, team: 'A', position: 'FWD' },
        { playerId: 'p3', name: 'Tomi', currentElo: 1050, team: 'B', position: 'DEF' },
        { playerId: 'p4', name: 'Nico', currentElo: 1020, team: 'B', position: 'GK' },
      ],
    };
    const LEVELS: Record<string, number> = { p1: 6, p2: 8, p3: 7, p4: 5 };
    const ADMIN_PLAYERS = PLAYERS.map((p) => ({ ...p, level: LEVELS[p.id] }));

    it('el admin ve nivel promedio y diferencia de nivel y se actualizan al mover jugadores', async () => {
      stubSetup('ADMIN', {
        [`GET /api/groups/${GROUP_ID}/players`]: () => json(ADMIN_PLAYERS),
        [`GET ${MATCH_URL}`]: () => json(SAVED_MATCH),
      });
      const user = userEvent.setup();
      renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

      expect(await screen.findByText(/Diferencia de nivel:/)).toHaveTextContent(
        'Diferencia de nivel: 1,0',
      );
      const teamA = screen.getByRole('heading', { name: 'Equipo A' }).closest('div')!;
      expect(within(teamA).getByText(/Nivel promedio/)).toHaveTextContent('Nivel promedio: 7,0');

      await user.click(within(teamA).getByRole('button', { name: /Pasar al equipo B \(Lu\)/ }));

      expect(screen.getByText(/Diferencia de nivel:/)).toHaveTextContent(
        'Diferencia de nivel: 0,7',
      );
      const movedA = screen.getByRole('heading', { name: 'Equipo A' }).closest('div')!;
      expect(within(movedA).getByText(/Nivel promedio/)).toHaveTextContent('Nivel promedio: 6,0');
    });

    it('el MEMBER ve los equipos guardados en solo lectura, con apodo y puesto y sin nivel', async () => {
      stubSetup('MEMBER', {
        [`GET /api/groups/${GROUP_ID}/players`]: () =>
          json(PLAYERS.map((p) => (p.id === 'p2' ? { ...p, nickname: 'Luchi' } : p))),
        [`GET ${MATCH_URL}`]: () => json(SAVED_MATCH),
      });
      renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

      const teamA = (await screen.findByRole('heading', { name: 'Equipo A' })).closest('div')!;
      expect(await within(teamA).findByText('(Luchi)')).toBeInTheDocument();
      expect(within(teamA).getByText('(Luchi)').closest('li')).toHaveTextContent('Lu (Luchi)');
      expect(within(teamA).getByText('DEL')).toBeInTheDocument();
      const teamB = screen.getByRole('heading', { name: 'Equipo B' }).closest('div')!;
      expect(within(teamB).getByText('Nico')).toBeInTheDocument();
      expect(within(teamB).getByText('ARQ')).toBeInTheDocument();
      expect(screen.queryByText(/nivel/i)).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Pasar al equipo/ })).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/Puesto de/)).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Guardar equipos' })).not.toBeInTheDocument();
    });
  });
});
