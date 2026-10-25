import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAppRouter } from '../../app/router';

type Route = (init?: RequestInit) => Response | undefined;

const ME = { id: 'u1', username: 'agus', displayName: 'Agus' };
const GROUP_ID = 'g1';
const MATCH_ID = 'm1';
const MATCH_URL = `/api/groups/${GROUP_ID}/matches/${MATCH_ID}`;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

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

function renderLive() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <RouterProvider
        router={createAppRouter([`/grupos/${GROUP_ID}/partidos/${MATCH_ID}/en-vivo`])}
      />
    </QueryClientProvider>,
  );
}

function event(id: string, userId: string, name: string, voidedAt: string | null = null) {
  return {
    id,
    type: 'GOAL',
    team: 'A',
    playerId: 'p1',
    playerName: 'Agus',
    assistPlayerId: null,
    assistPlayerName: null,
    recordedBy: { userId, displayName: name },
    createdAt: '2026-11-05T21:10:00Z',
    voidedAt,
  };
}

function liveState(isAdmin: boolean, events: unknown[] = []) {
  return {
    matchId: MATCH_ID,
    status: 'LIVE',
    scoreA: 1,
    scoreB: 0,
    canRecord: true,
    isAdmin,
    myUserId: ME.id,
    events,
  };
}

const MATCH = {
  id: MATCH_ID,
  playedAt: '2026-11-05T21:00:00Z',
  location: 'Cancha 1',
  format: 7,
  status: 'LIVE',
  scoreA: null,
  scoreB: null,
  mvpPlayerId: null,
  callUps: [],
  lineup: [
    { playerId: 'p1', name: 'Agus', currentElo: 1000, team: 'A', position: 'MID' },
    { playerId: 'p2', name: 'Lu', currentElo: 1100, team: 'A', position: 'FWD' },
    { playerId: 'p3', name: 'Tomi', currentElo: 1050, team: 'B', position: 'DEF' },
  ],
};

const MIXED_EVENTS = [
  event('e1', 'u1', 'Agus'),
  event('e2', 'u2', 'Lu'),
  event('e3', 'u2', 'Lu', '2026-11-05T21:12:00Z'),
];

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

function setup() {
  return userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) });
}

describe('LivePage', () => {
  it('pide el estado en vivo de nuevo después de 5000 ms', async () => {
    const fetchMock = stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState(true)),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
    });
    renderLive();
    await screen.findByRole('heading', { name: 'En vivo' });
    const liveCalls = () =>
      fetchMock.mock.calls.filter(([url]) => String(url) === `${MATCH_URL}/live`).length;
    expect(liveCalls()).toBe(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });

    await waitFor(() => expect(liveCalls()).toBe(2));
  });

  it('Gol A con autor hace POST con UUID y reenvía el mismo id si falla por red', async () => {
    let attempts = 0;
    const fetchMock = stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState(true)),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
      [`POST ${MATCH_URL}/events`]: () => {
        attempts += 1;
        if (attempts === 1) {
          throw new TypeError('Failed to fetch');
        }
        return json(event('new', 'u1', 'Agus'), 201);
      },
    });
    const user = setup();
    renderLive();

    await user.click(await screen.findByRole('button', { name: 'Gol A' }));
    await user.selectOptions(await screen.findByLabelText('Autor'), 'Lu');
    await user.click(screen.getByRole('button', { name: 'Registrar' }));

    await waitFor(() => expect(attempts).toBe(2), { timeout: 3000 });
    const posts = fetchMock.mock.calls
      .filter(([url, init]) => String(url) === `${MATCH_URL}/events` && init?.method === 'POST')
      .map(([, init]) => JSON.parse(String(init?.body)) as Record<string, unknown>);
    expect(posts).toHaveLength(2);
    expect(posts[0]).toMatchObject({ type: 'GOAL', team: 'A', playerId: 'p2' });
    expect(posts[0].id).toMatch(UUID);
    expect(posts[1].id).toBe(posts[0].id);
  });

  it('Deshacer anula el último evento no anulado cargado por el usuario actual', async () => {
    const fetchMock = stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState(false, MIXED_EVENTS)),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
      [`POST ${MATCH_URL}/events/e1/void`]: () => json(MIXED_EVENTS[0]),
    });
    const user = setup();
    renderLive();

    await user.click(await screen.findByRole('button', { name: 'Deshacer' }));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          ([url, init]) => String(url) === `${MATCH_URL}/events/e1/void` && init?.method === 'POST',
        ),
      ).toBe(true),
    );
  });

  it('un MEMBER ve Anular solo en sus eventos', async () => {
    stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState(false, MIXED_EVENTS)),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
    });
    renderLive();
    await screen.findByRole('heading', { name: 'Eventos' });
    expect(screen.getAllByRole('button', { name: /^Anular/ })).toHaveLength(1);
    expect(screen.getByText(/· anulado/)).toBeInTheDocument();
  });

  it('un ADMIN ve Anular en todos los eventos no anulados', async () => {
    stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState(true, MIXED_EVENTS)),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
    });
    renderLive();
    await screen.findByRole('heading', { name: 'Eventos' });
    expect(screen.getAllByRole('button', { name: /^Anular/ })).toHaveLength(2);
  });

  it('los botones de registro usan min-h-16 y el marcador es un status', async () => {
    stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState(true)),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
    });
    renderLive();
    const region = await screen.findByRole('region', { name: 'Registro' });
    for (const label of ['Gol A', 'Gol B', 'Atajada A', 'Atajada B', 'Corte A', 'Corte B']) {
      expect(within(region).getByRole('button', { name: label })).toHaveClass('min-h-16');
    }
    expect(screen.getByRole('status', { name: 'Marcador' })).toHaveTextContent('1');
  });
});
