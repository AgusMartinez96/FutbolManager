import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
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

const GROUP = {
  id: GROUP_ID,
  name: 'Los del jueves',
  inviteCode: 'ABCD2345',
  membersCanScore: false,
  myRole: 'ADMIN',
  myUserId: ME.id,
};

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

function balancedPlayer(id: string, name: string, elo: number, level: number, position: string) {
  return { playerId: id, name, currentElo: elo, level, position };
}

const OPTION_1 = {
  teamA: [
    balancedPlayer('p1', 'Agus', 1000, 6.5, 'MID'),
    balancedPlayer('p2', 'Lu', 1100, 7.5, 'FWD'),
  ],
  teamB: [
    balancedPlayer('p3', 'Tomi', 1050, 7, 'DEF'),
    balancedPlayer('p4', 'Nico', 1020, 6.5, 'GK'),
  ],
  totalLevelA: 14,
  totalLevelB: 13.5,
  levelDifference: 0.5,
  avgEloA: 1050,
  avgEloB: 1035,
  eloDifference: 15,
};
const OPTION_2 = {
  ...OPTION_1,
  teamA: [
    balancedPlayer('p1', 'Agus', 1000, 6.5, 'MID'),
    balancedPlayer('p3', 'Tomi', 1050, 7, 'DEF'),
  ],
  teamB: [
    balancedPlayer('p2', 'Lu', 1100, 7.5, 'FWD'),
    balancedPlayer('p4', 'Nico', 1020, 6.5, 'GK'),
  ],
  totalLevelA: 13.5,
  totalLevelB: 14,
};
const OPTION_3 = {
  ...OPTION_1,
  teamA: [
    balancedPlayer('p1', 'Agus', 1000, 6.5, 'MID'),
    balancedPlayer('p4', 'Nico', 1020, 6.5, 'GK'),
  ],
  teamB: [
    balancedPlayer('p2', 'Lu', 1100, 7.5, 'FWD'),
    balancedPlayer('p3', 'Tomi', 1050, 7, 'DEF'),
  ],
  totalLevelA: 13,
  totalLevelB: 14.5,
};

function stubAdmin(options: unknown[]) {
  return stubApi({
    [`GET /api/groups/${GROUP_ID}`]: () => json(GROUP),
    [`GET /api/groups/${GROUP_ID}/players`]: () => json(PLAYERS),
    [`GET ${MATCH_URL}`]: () => json(MATCH),
    [`POST ${MATCH_URL}/balance`]: () => json({ options }),
  });
}

function balanceCalls(fetchMock: ReturnType<typeof stubApi>) {
  return fetchMock.mock.calls.filter(([url]) => String(url) === `${MATCH_URL}/balance`).length;
}

function teamOf(name: 'Equipo A' | 'Equipo B') {
  return screen.getByRole('heading', { name }).closest('div')!;
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('LineupBuilder: Otra opción', () => {
  it('recorre las opciones en orden, vuelve a la primera y no repite la petición a balance', async () => {
    const fetchMock = stubAdmin([OPTION_1, OPTION_2, OPTION_3]);
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Armar equipos' }));

    const status = await screen.findByText(/^Opción/);
    expect(status).toHaveAttribute('role', 'status');
    expect(status).toHaveTextContent('Opción 1 de 3');
    expect(within(teamOf('Equipo A')).getByLabelText('Puesto de Lu')).toBeInTheDocument();

    const other = screen.getByRole('button', { name: 'Otra opción' });
    expect(other).toBeEnabled();

    await user.click(other);
    expect(status).toHaveTextContent('Opción 2 de 3');
    expect(within(teamOf('Equipo A')).getByLabelText('Puesto de Tomi')).toBeInTheDocument();
    expect(within(teamOf('Equipo B')).getByLabelText('Puesto de Lu')).toBeInTheDocument();

    await user.click(other);
    expect(status).toHaveTextContent('Opción 3 de 3');
    expect(within(teamOf('Equipo A')).getByLabelText('Puesto de Nico')).toBeInTheDocument();

    await user.click(other);
    expect(status).toHaveTextContent('Opción 1 de 3');
    expect(within(teamOf('Equipo A')).getByLabelText('Puesto de Lu')).toBeInTheDocument();
    expect(balanceCalls(fetchMock)).toBe(1);
  });

  it('con una sola opción el botón Otra opción queda deshabilitado', async () => {
    stubAdmin([OPTION_1]);
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Armar equipos' }));

    expect(await screen.findByRole('button', { name: 'Otra opción' })).toBeDisabled();
    expect(screen.getByText(/^Opción/)).toHaveTextContent('Opción 1 de 1');
  });

  it('cambiar de opción descarta los movimientos manuales y Armar equipos pide de nuevo y vuelve a la primera', async () => {
    const fetchMock = stubAdmin([OPTION_1, OPTION_2]);
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Armar equipos' }));
    await user.click(await screen.findByRole('button', { name: /Pasar al equipo B \(Agus\)/ }));
    expect(screen.getByRole('alert')).toHaveTextContent('1 y 3 jugadores');

    await user.click(screen.getByRole('button', { name: 'Otra opción' }));

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(within(teamOf('Equipo A')).getByLabelText('Puesto de Agus')).toBeInTheDocument();
    expect(within(teamOf('Equipo A')).getByLabelText('Puesto de Tomi')).toBeInTheDocument();
    expect(screen.getByText(/^Opción/)).toHaveTextContent('Opción 2 de 2');

    await user.click(screen.getByRole('button', { name: 'Armar equipos' }));

    await waitFor(() => expect(screen.getByText(/^Opción/)).toHaveTextContent('Opción 1 de 2'));
    expect(within(teamOf('Equipo A')).getByLabelText('Puesto de Lu')).toBeInTheDocument();
    expect(balanceCalls(fetchMock)).toBe(2);
  });

  it('Otra opción se activa con el teclado igual que con el puntero', async () => {
    const fetchMock = stubAdmin([OPTION_1, OPTION_2]);
    const user = userEvent.setup();
    renderAt(`/grupos/${GROUP_ID}/partidos/${MATCH_ID}`);

    await user.click(await screen.findByRole('button', { name: 'Armar equipos' }));
    const other = await screen.findByRole('button', { name: 'Otra opción' });
    other.focus();

    await user.keyboard('{Enter}');
    expect(screen.getByText(/^Opción/)).toHaveTextContent('Opción 2 de 2');

    await user.keyboard(' ');
    expect(screen.getByText(/^Opción/)).toHaveTextContent('Opción 1 de 2');
    expect(balanceCalls(fetchMock)).toBe(1);
  });
});
