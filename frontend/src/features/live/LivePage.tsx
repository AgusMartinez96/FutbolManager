import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { Alert, Button, Card } from '../../components/ui';
import { ApiError } from '../../lib/api';
import { useMatch, type TeamKey } from '../matches/api';
import {
  useLive,
  useRecordEvent,
  useVoidEvent,
  type EventType,
  type LiveEvent,
  type LiveState,
} from './api';
import { CloseControls } from './CloseControls';
import { EventDialog, NO_AUTHOR, TYPE_LABEL, type PendingEvent } from './EventDialog';

const ACTIONS: { type: EventType; team: TeamKey; label: string }[] = [
  { type: 'GOAL', team: 'A', label: 'Gol A' },
  { type: 'GOAL', team: 'B', label: 'Gol B' },
  { type: 'SAVE', team: 'A', label: 'Atajada A' },
  { type: 'SAVE', team: 'B', label: 'Atajada B' },
  { type: 'TACKLE', team: 'A', label: 'Corte A' },
  { type: 'TACKLE', team: 'B', label: 'Corte B' },
  { type: 'PLAY', team: 'A', label: 'Jugadón A' },
  { type: 'PLAY', team: 'B', label: 'Jugadón B' },
];

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : 'No pudimos conectar con el servidor.';
}

function describe(event: LiveEvent): string {
  const who = event.playerName ?? (event.type === 'GOAL' ? NO_AUTHOR : 'Sin jugador');
  const assist = event.assistPlayerName ? ` (asistencia: ${event.assistPlayerName})` : '';
  return `${TYPE_LABEL[event.type]} del equipo ${event.team}: ${who}${assist}`;
}

function Scoreboard({ live }: { live: LiveState }) {
  return (
    <div
      role="status"
      aria-label="Marcador"
      className="sticky top-0 z-10 flex items-center justify-center gap-4 border-b border-border bg-background py-3 font-mono text-5xl font-bold tabular-nums"
    >
      <span>
        <span className="mr-2 text-base text-text-muted">A</span>
        {live.scoreA}
      </span>
      <span aria-hidden="true">–</span>
      <span>
        {live.scoreB}
        <span className="ml-2 text-base text-text-muted">B</span>
      </span>
    </div>
  );
}

function EventList({
  live,
  onVoid,
  busy,
}: {
  live: LiveState;
  onVoid: (eventId: string) => void;
  busy: boolean;
}) {
  const events = [...live.events].reverse();
  if (events.length === 0) {
    return <p className="text-text-muted">Todavía no hay eventos.</p>;
  }
  return (
    <ul className="flex flex-col gap-2">
      {events.map((event) => {
        const voided = event.voidedAt !== null;
        const mine = event.recordedBy.userId === live.myUserId;
        const canVoid =
          !voided && (live.isAdmin || (mine && live.status === 'LIVE')) && live.canRecord;
        return (
          <li key={event.id}>
            <Card className="flex items-center justify-between gap-3 p-3">
              <div className="flex flex-col">
                <span className={voided ? 'line-through' : undefined}>{describe(event)}</span>
                <span className="text-sm text-text-muted">
                  cargado por {event.recordedBy.displayName}
                  {voided ? ' · anulado' : ''}
                </span>
              </div>
              {canVoid ? (
                <Button
                  variant="secondary"
                  disabled={busy}
                  aria-label={`Anular: ${describe(event)}`}
                  onClick={() => onVoid(event.id)}
                >
                  Anular
                </Button>
              ) : null}
            </Card>
          </li>
        );
      })}
    </ul>
  );
}

/** Where to go once the match is closed: ranking, match list or the group. */
function AfterMatchLinks({ groupId }: { groupId: string }) {
  const linkClass = 'inline-flex min-h-10 items-center text-accent underline';
  return (
    <nav aria-label="Después del partido" className="flex flex-wrap gap-x-4 gap-y-1">
      <Link to={`/grupos/${groupId}/ranking`} className={linkClass}>
        Ver ranking
      </Link>
      <Link to={`/grupos/${groupId}/partidos`} className={linkClass}>
        Volver a partidos
      </Link>
      <Link to={`/grupos/${groupId}`} className={linkClass}>
        Volver al grupo
      </Link>
    </nav>
  );
}

export function LivePage() {
  const { groupId = '', matchId = '' } = useParams();
  const live = useLive(groupId, matchId);
  const match = useMatch(groupId, matchId);
  const record = useRecordEvent(groupId, matchId);
  const voidEvent = useVoidEvent(groupId, matchId);
  const [pending, setPending] = useState<PendingEvent | null>(null);

  if (live.isPending) {
    return <p role="status">Cargando partido…</p>;
  }
  if (!live.data) {
    return (
      <div className="flex flex-col gap-3">
        <Alert variant="error">No pudimos cargar el partido en vivo.</Alert>
        <Button variant="secondary" onClick={() => void live.refetch()}>
          Reintentar
        </Button>
      </div>
    );
  }

  const state = live.data;
  const lastMine = [...state.events]
    .reverse()
    .find((event) => event.recordedBy.userId === state.myUserId && event.voidedAt === null);
  const submit = (playerId: string, assistPlayerId: string) => {
    if (!pending) return;
    record.mutate({
      id: crypto.randomUUID(),
      type: pending.type,
      team: pending.team,
      playerId: playerId || undefined,
      assistPlayerId: assistPlayerId || undefined,
    });
    setPending(null);
  };

  return (
    <div className="flex scroll-pt-28 flex-col gap-4">
      <h1 className="text-2xl font-bold">{state.status === 'CLOSED' ? 'Resumen' : 'En vivo'}</h1>
      {live.isError ? <Alert variant="error">Sin conexión, reintentando…</Alert> : null}
      <Scoreboard live={state} />
      {state.status === 'CLOSED' ? <AfterMatchLinks groupId={groupId} /> : null}
      {state.canRecord ? (
        <section aria-label="Registro" className="flex flex-col gap-3">
          <div className="grid grid-cols-2 gap-3">
            {ACTIONS.map((action) => (
              <Button
                key={action.label}
                size="live"
                variant={action.type === 'GOAL' ? 'primary' : 'secondary'}
                onClick={() => setPending({ type: action.type, team: action.team })}
              >
                {action.label}
              </Button>
            ))}
          </div>
          <Button
            size="live"
            variant="ghost"
            disabled={!lastMine || voidEvent.isPending}
            onClick={() => lastMine && voidEvent.mutate(lastMine.id)}
          >
            Deshacer
          </Button>
          {record.isError ? <Alert variant="error">{errorMessage(record.error)}</Alert> : null}
          {voidEvent.isError ? (
            <Alert variant="error">{errorMessage(voidEvent.error)}</Alert>
          ) : null}
        </section>
      ) : null}
      <CloseControls
        groupId={groupId}
        matchId={matchId}
        state={state}
        lineup={match.data?.lineup ?? []}
      />
      <section aria-labelledby="events-title" className="flex flex-col gap-3">
        <h2 id="events-title" className="text-xl font-bold">
          Eventos
        </h2>
        <EventList live={state} busy={voidEvent.isPending} onVoid={(id) => voidEvent.mutate(id)} />
      </section>
      {pending ? (
        <EventDialog
          pending={pending}
          lineup={match.data?.lineup ?? []}
          onSubmit={submit}
          onClose={() => setPending(null)}
        />
      ) : null}
    </div>
  );
}
