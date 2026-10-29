import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAppRouter } from '../../app/router';

type Route = (init?: RequestInit) => Response | undefined;

const ME = { id: 'u1', username: 'agus', displayName: 'Agus' };
const GROUP_ID = 'g1';
const BASE = `/api/groups/${GROUP_ID}`;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
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
    return Promise.resolve(
      response ?? json({ detail: `Sin ruta simulada: ${method} ${url}` }, 404),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={createAppRouter([path])} />
    </QueryClientProvider>,
  );
}

const RANKING = [
  { playerId: 'p2', name: 'Lu', nickname: null, currentElo: 1120, played: 12, provisional: false },
  { playerId: 'p1', name: 'Agus', nickname: 'Gus', currentElo: 1040, played: 3, provisional: true },
  { playerId: 'p3', name: 'Tomi', nickname: null, currentElo: 1040, played: 0, provisional: true },
];

const STATS = {
  playerId: 'p1',
  name: 'Agus',
  played: 3,
  won: 2,
  drawn: 1,
  lost: 0,
  goals: 4,
  assists: 1,
  mvps: 2,
  streak: { type: 'D', length: 1 },
  currentElo: 1040,
  provisional: true,
};

const HISTORY = [
  { matchId: null, playedAt: null, eloAfter: 1000 },
  { matchId: 'm1', playedAt: '2026-03-01T20:00:00Z', eloAfter: 1020 },
  { matchId: 'm2', playedAt: '2026-03-08T20:00:00Z', eloAfter: 1040 },
];

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('RankingPage', () => {
  it('lista a los jugadores en el orden recibido con su posición, ELO y partidos', async () => {
    stubApi({ [`GET ${BASE}/ranking`]: () => json(RANKING) });
    renderAt(`/grupos/${GROUP_ID}/ranking`);

    expect(await screen.findByRole('heading', { level: 1, name: 'Ranking' })).toBeInTheDocument();
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(3);
    const cells = within(rows[0])
      .getAllByRole('cell')
      .map((cell) => cell.textContent);
    expect(cells).toEqual(['1', 'Lu', '1120', '12']);
    expect(within(rows[1]).getByRole('link', { name: 'Agus' })).toHaveAttribute(
      'href',
      `/grupos/${GROUP_ID}/jugadores/p1`,
    );
    expect(within(rows[2]).getAllByRole('cell')[0]).toHaveTextContent('3');
  });

  it('marca como Provisorio solo a quienes jugaron menos de 10 partidos', async () => {
    stubApi({ [`GET ${BASE}/ranking`]: () => json(RANKING) });
    renderAt(`/grupos/${GROUP_ID}/ranking`);

    await screen.findByRole('heading', { name: 'Ranking' });
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
    expect(within(rows[0]).queryByText('Provisorio')).not.toBeInTheDocument();
    expect(within(rows[1]).getByText('Provisorio')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Provisorio')).toBeInTheDocument();
  });

  it('con una lista vacía muestra la tabla sin filas', async () => {
    stubApi({ [`GET ${BASE}/ranking`]: () => json([]) });
    renderAt(`/grupos/${GROUP_ID}/ranking`);

    await screen.findByRole('heading', { name: 'Ranking' });
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(1);
  });

  it('si falla la carga muestra el error y Reintentar repite la petición', async () => {
    const user = userEvent.setup();
    let calls = 0;
    stubApi({
      [`GET ${BASE}/ranking`]: () => {
        calls += 1;
        return calls === 1 ? json({ detail: 'Falló' }, 500) : json(RANKING);
      },
    });
    renderAt(`/grupos/${GROUP_ID}/ranking`);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el ranking.');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('link', { name: 'Lu' })).toBeInTheDocument();
    expect(calls).toBe(2);
  });
});

describe('PlayerProfilePage', () => {
  const path = `/grupos/${GROUP_ID}/jugadores/p1`;

  it('muestra las estadísticas, la racha en texto y el badge de MVP', async () => {
    stubApi({
      [`GET ${BASE}/players/p1/stats`]: () => json(STATS),
      [`GET ${BASE}/players/p1/elo-history`]: () => json(HISTORY),
    });
    renderAt(path);

    expect(await screen.findByRole('heading', { level: 1, name: 'Agus' })).toBeInTheDocument();
    const value = (label: string) =>
      screen.getByText(label, { selector: 'dt' }).parentElement?.querySelector('dd')?.textContent;
    expect(value('PJ')).toBe('3');
    expect(value('G')).toBe('2');
    expect(value('E')).toBe('1');
    expect(value('P')).toBe('0');
    expect(value('Goles')).toBe('4');
    expect(value('Asistencias')).toBe('1');
    expect(value('MVPs')).toBe('2');
    expect(screen.getByText('1 empate')).toBeInTheDocument();
    expect(screen.getByText('Provisorio')).toBeInTheDocument();
  });

  it('muestra el historial de ELO con el punto inicial primero', async () => {
    stubApi({
      [`GET ${BASE}/players/p1/stats`]: () => json(STATS),
      [`GET ${BASE}/players/p1/elo-history`]: () => json(HISTORY),
    });
    renderAt(path);

    const table = await screen.findByRole('table', { name: 'Historial de ELO' });
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(3);
    expect(rows[0]).toHaveTextContent('Inicial');
    expect(rows[0]).toHaveTextContent('1000');
    expect(rows[2]).toHaveTextContent('1040');
  });

  it('sin partidos muestra «Sin partidos todavía» y solo el punto inicial', async () => {
    stubApi({
      [`GET ${BASE}/players/p1/stats`]: () =>
        json({
          ...STATS,
          played: 0,
          won: 0,
          drawn: 0,
          goals: 0,
          assists: 0,
          mvps: 0,
          streak: null,
          currentElo: 1000,
        }),
      [`GET ${BASE}/players/p1/elo-history`]: () => json([HISTORY[0]]),
    });
    renderAt(path);

    expect(await screen.findByText('Sin partidos todavía')).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'Historial de ELO' });
    expect(within(table).getAllByRole('row')).toHaveLength(2);
  });

  it('si falla el perfil muestra el error y Reintentar repite la petición', async () => {
    const user = userEvent.setup();
    let calls = 0;
    stubApi({
      [`GET ${BASE}/players/p1/stats`]: () => {
        calls += 1;
        return calls === 1 ? json({ detail: 'Falló' }, 500) : json(STATS);
      },
      [`GET ${BASE}/players/p1/elo-history`]: () => json(HISTORY),
    });
    renderAt(path);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el jugador.');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Agus' })).toBeInTheDocument();
  });

  it('si falla el historial lo avisa sin perder las estadísticas', async () => {
    stubApi({
      [`GET ${BASE}/players/p1/stats`]: () => json(STATS),
      [`GET ${BASE}/players/p1/elo-history`]: () => json({ detail: 'Falló' }, 500),
    });
    renderAt(path);

    expect(await screen.findByRole('heading', { level: 1, name: 'Agus' })).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos cargar el historial de ELO.',
    );
  });
});
