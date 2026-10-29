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

function liveState(status: 'LIVE' | 'CLOSED', isAdmin = true) {
  return {
    matchId: MATCH_ID,
    status,
    scoreA: 2,
    scoreB: 1,
    canRecord: true,
    isAdmin,
    myUserId: ME.id,
    events: [],
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

const CLOSE_RESULT = {
  scoreA: 2,
  scoreB: 1,
  mvpPlayerId: null,
  eloChanges: [
    { playerId: 'p1', name: 'Agus', eloBefore: 1000, eloAfter: 1012, delta: 12 },
    { playerId: 'p3', name: 'Tomi', eloBefore: 1050, eloAfter: 1038, delta: -12 },
    { playerId: 'p2', name: 'Lu', eloBefore: 1100, eloAfter: 1100, delta: 0 },
  ],
};

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

afterEach(() => {
  vi.unstubAllGlobals();
});

type FetchMock = ReturnType<typeof stubApi>;

function findCall(fetchMock: FetchMock, method: string, url: string) {
  return fetchMock.mock.calls.find(
    ([input, init]) => String(input) === url && (init?.method ?? 'GET').toUpperCase() === method,
  );
}

function bodyOf(fetchMock: FetchMock, method: string, url: string) {
  const call = findCall(fetchMock, method, url);
  return call ? (JSON.parse(String(call[1]?.body ?? 'null')) as unknown) : undefined;
}

describe('CloseControls', () => {
  it('cierra sin MVP, manda un cuerpo vacío y muestra los deltas con signo y texto', async () => {
    const user = userEvent.setup();
    const fetchMock = stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState('LIVE')),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
      [`POST ${MATCH_URL}/close`]: () => json(CLOSE_RESULT),
    });
    renderLive();

    await user.click(await screen.findByRole('button', { name: 'Cerrar partido' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('MVP (opcional)')).toHaveValue('');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar cierre' }));

    const summary = await screen.findByRole('list', { name: 'Cambios de ELO' });
    expect(summary).toHaveTextContent('Agus1000 → 1012 · +12 (sube)');
    expect(summary).toHaveTextContent('Tomi1050 → 1038 · -12 (baja)');
    expect(summary).toHaveTextContent('Lu1100 → 1100 · 0 (sin cambios)');
    expect(bodyOf(fetchMock, 'POST', `${MATCH_URL}/close`)).toEqual({});
  });

  it('cierra con el MVP elegido y lo manda como mvpPlayerId', async () => {
    const user = userEvent.setup();
    const fetchMock = stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState('LIVE')),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
      [`POST ${MATCH_URL}/close`]: () => json({ ...CLOSE_RESULT, mvpPlayerId: 'p2' }),
    });
    renderLive();

    await user.click(await screen.findByRole('button', { name: 'Cerrar partido' }));
    const dialog = await screen.findByRole('dialog');
    await user.selectOptions(within(dialog).getByLabelText('MVP (opcional)'), 'Lu');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar cierre' }));

    await screen.findByRole('list', { name: 'Cambios de ELO' });
    expect(bodyOf(fetchMock, 'POST', `${MATCH_URL}/close`)).toEqual({ mvpPlayerId: 'p2' });
  });

  it('muestra el error de la API si no se puede cerrar', async () => {
    const user = userEvent.setup();
    stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState('LIVE')),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
      [`POST ${MATCH_URL}/close`]: () =>
        json({ detail: 'Solo se puede cerrar un partido en juego.' }, 409),
    });
    renderLive();

    await user.click(await screen.findByRole('button', { name: 'Cerrar partido' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar cierre' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Solo se puede cerrar un partido en juego.',
    );
  });

  it('cambia el MVP de un partido cerrado: exige elegir y hace PUT …/mvp', async () => {
    const user = userEvent.setup();
    const fetchMock = stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState('CLOSED')),
      [`GET ${MATCH_URL}`]: () => json({ ...MATCH, status: 'CLOSED' }),
      [`PUT ${MATCH_URL}/mvp`]: () => json({ ...CLOSE_RESULT, mvpPlayerId: 'p1' }),
    });
    renderLive();

    await user.click(await screen.findByRole('button', { name: 'Cambiar MVP' }));
    const dialog = await screen.findByRole('dialog');
    const save = within(dialog).getByRole('button', { name: 'Guardar MVP' });
    expect(save).toBeDisabled();
    await user.selectOptions(within(dialog).getByLabelText('MVP'), 'Agus');
    await user.click(save);

    expect(await screen.findByRole('list', { name: 'Cambios de ELO' })).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'PUT', `${MATCH_URL}/mvp`)).toEqual({ mvpPlayerId: 'p1' });
  });

  it('anular pide confirmación: Cancelar no llama a la API y Sí, anular hace POST …/annul', async () => {
    const user = userEvent.setup();
    const fetchMock = stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState('CLOSED')),
      [`GET ${MATCH_URL}`]: () => json({ ...MATCH, status: 'CLOSED' }),
      [`POST ${MATCH_URL}/annul`]: () => json({ matchId: MATCH_ID, status: 'ANNULLED' }),
    });
    renderLive();

    await user.click(await screen.findByRole('button', { name: 'Anular partido' }));
    let dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(findCall(fetchMock, 'POST', `${MATCH_URL}/annul`)).toBeUndefined();

    await user.click(screen.getByRole('button', { name: 'Anular partido' }));
    dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Sí, anular' }));

    await waitFor(() => expect(findCall(fetchMock, 'POST', `${MATCH_URL}/annul`)).toBeDefined());
  });

  it('un MEMBER no ve ningún control de cierre', async () => {
    stubApi({
      [`GET ${MATCH_URL}/live`]: () => json(liveState('LIVE', false)),
      [`GET ${MATCH_URL}`]: () => json(MATCH),
    });
    renderLive();

    await screen.findByRole('heading', { name: 'En vivo' });
    expect(screen.queryByRole('button', { name: 'Cerrar partido' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Anular partido' })).not.toBeInTheDocument();
  });
});
