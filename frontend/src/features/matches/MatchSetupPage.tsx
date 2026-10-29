import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { Alert, Button, Card } from '../../components/ui';
import { ApiError } from '../../lib/api';
import { useGroup, usePlayers, type GroupDetail, type Position } from '../groups/api';
import {
  formatMatchDate,
  statusLabel,
  useBalance,
  useMatch,
  useSaveLineup,
  useSetCallUp,
  useStartMatch,
  type CallUpStatus,
  type LineupItem,
  type MatchDetail,
  type TeamKey,
} from './api';

const POSITIONS: { value: Position; label: string }[] = [
  { value: 'GK', label: 'ARQ' },
  { value: 'DEF', label: 'DEF' },
  { value: 'MID', label: 'MED' },
  { value: 'FWD', label: 'DEL' },
];

const CALL_UP_OPTIONS: { value: CallUpStatus; label: string }[] = [
  { value: 'CONFIRMED', label: 'Confirmado' },
  { value: 'MAYBE', label: 'Quizás' },
  { value: 'NOT_GOING', label: 'No va' },
];

const numberFormat = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });
const levelFormat = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

const selectClass =
  'min-h-10 rounded-lg border border-border-strong bg-surface px-3 py-2 text-text';

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : 'No pudimos conectar con el servidor.';
}

function averageElo(items: LineupItem[]): number {
  return items.length === 0
    ? 0
    : items.reduce((sum, item) => sum + item.currentElo, 0) / items.length;
}

/** Null cuando algún jugador no trae nivel (equipos guardados, que el detalle no expone). */
function averageLevel(items: LineupItem[]): number | null {
  if (items.length === 0 || items.some((item) => item.level === undefined)) {
    return null;
  }
  return items.reduce((sum, item) => sum + (item.level ?? 0), 0) / items.length;
}

function CallUpsSection({ group, match }: { group: GroupDetail; match: MatchDetail }) {
  const players = usePlayers(group.id);
  const setCallUp = useSetCallUp(group.id, match.id);
  const isAdmin = group.myRole === 'ADMIN';
  const editable = match.status === 'SCHEDULED';

  if (players.isPending) {
    return <p role="status">Cargando jugadores…</p>;
  }
  if (players.isError) {
    return <Alert variant="error">No pudimos cargar los jugadores.</Alert>;
  }
  const visible = players.data.filter((player) => isAdmin || player.userId === group.myUserId);
  const statusOf = new Map(match.callUps.map((callUp) => [callUp.playerId, callUp.status]));
  const confirmed = match.callUps.filter((callUp) => callUp.status === 'CONFIRMED').length;

  return (
    <section aria-labelledby="callups-title" className="flex flex-col gap-3">
      <h2 id="callups-title" className="text-xl font-bold">
        Convocatoria
      </h2>
      <p className="text-sm text-text-muted">
        <span className="font-mono tabular-nums">{confirmed}</span> confirmados
      </p>
      {setCallUp.isError ? <Alert variant="error">{errorMessage(setCallUp.error)}</Alert> : null}
      {visible.length === 0 ? (
        <p className="text-text-muted">No tenés un jugador vinculado en este grupo.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {visible.map((player) => (
            <li key={player.id}>
              <Card className="flex items-center justify-between gap-3">
                <label htmlFor={`callup-${player.id}`} className="font-semibold">
                  {player.name}
                </label>
                <select
                  id={`callup-${player.id}`}
                  aria-label={`Asistencia de ${player.name}`}
                  value={statusOf.get(player.id) ?? ''}
                  disabled={!editable || setCallUp.isPending}
                  onChange={(event) =>
                    setCallUp.mutate({
                      playerId: player.id,
                      status: event.target.value as CallUpStatus,
                    })
                  }
                  className={selectClass}
                >
                  <option value="" disabled>
                    Sin responder
                  </option>
                  {CALL_UP_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function TeamColumn({
  team,
  items,
  onMove,
  onPosition,
}: {
  team: TeamKey;
  items: LineupItem[];
  onMove: (playerId: string) => void;
  onPosition: (playerId: string, position: Position) => void;
}) {
  const other = team === 'A' ? 'B' : 'A';
  const level = averageLevel(items);
  return (
    <Card className="flex flex-col gap-3">
      <h3 className="text-lg font-bold">Equipo {team}</h3>
      {level !== null ? (
        <p>
          Nivel promedio:{' '}
          <span className="font-mono tabular-nums">{levelFormat.format(level)}</span>
        </p>
      ) : null}
      <p className="text-sm text-text-muted">
        ELO promedio:{' '}
        <span className="font-mono tabular-nums">{numberFormat.format(averageElo(items))}</span>
      </p>
      <ul className="flex flex-col gap-2">
        {items.map((item) => (
          <li key={item.playerId} className="flex flex-wrap items-center gap-2">
            <span className="min-w-24 flex-1 font-semibold">{item.name}</span>
            {item.level !== undefined ? (
              <span className="font-mono font-semibold tabular-nums">
                <span className="sr-only">Nivel </span>
                {levelFormat.format(item.level)}
              </span>
            ) : null}
            <span className="font-mono text-sm text-text-muted tabular-nums">
              <span className="sr-only">ELO </span>
              {item.currentElo}
            </span>
            <select
              aria-label={`Puesto de ${item.name}`}
              value={item.position}
              onChange={(event) => onPosition(item.playerId, event.target.value as Position)}
              className={selectClass}
            >
              {POSITIONS.map((position) => (
                <option key={position.value} value={position.value}>
                  {position.label}
                </option>
              ))}
            </select>
            <Button variant="secondary" onClick={() => onMove(item.playerId)}>
              Pasar al equipo {other} <span className="sr-only">{`(${item.name})`}</span>
            </Button>
          </li>
        ))}
      </ul>
    </Card>
  );
}

/** Vista de solo lectura para MEMBER: sin nivel ni controles de armado. */
function SavedTeams({ groupId, match }: { groupId: string; match: MatchDetail }) {
  const players = usePlayers(groupId);
  const nicknameOf = new Map((players.data ?? []).map((player) => [player.id, player.nickname]));
  if (match.lineup.length === 0) {
    return null;
  }
  return (
    <section aria-labelledby="saved-teams-title" className="flex flex-col gap-3">
      <h2 id="saved-teams-title" className="text-xl font-bold">
        Equipos
      </h2>
      <div className="grid gap-4 md:grid-cols-2">
        {(['A', 'B'] as const).map((team) => (
          <Card key={team} className="flex flex-col gap-3">
            <h3 className="text-lg font-bold">Equipo {team}</h3>
            <ul className="flex flex-col gap-2">
              {match.lineup
                .filter((item) => item.team === team)
                .map((item) => {
                  const nickname = nicknameOf.get(item.playerId);
                  return (
                    <li key={item.playerId} className="flex items-center gap-2">
                      <span className="flex-1 font-semibold">
                        {item.name}
                        {nickname ? (
                          <span className="font-normal text-text-muted"> ({nickname})</span>
                        ) : null}
                      </span>
                      <span className="text-sm text-text-muted">
                        {POSITIONS.find((position) => position.value === item.position)?.label}
                      </span>
                    </li>
                  );
                })}
            </ul>
          </Card>
        ))}
      </div>
    </section>
  );
}

function LineupBuilder({ group, match }: { group: GroupDetail; match: MatchDetail }) {
  const navigate = useNavigate();
  const balance = useBalance(group.id, match.id);
  const save = useSaveLineup(group.id, match.id);
  const start = useStartMatch(group.id, match.id);
  const players = usePlayers(group.id);
  const [draft, setDraft] = useState<LineupItem[] | null>(null);
  // El detalle guardado no trae nivel: el admin lo toma de la lista de jugadores.
  const levelOf = new Map((players.data ?? []).map((player) => [player.id, player.level]));
  const items = (draft ?? match.lineup).map((item) => ({
    ...item,
    level: item.level ?? levelOf.get(item.playerId),
  }));
  const teamA = items.filter((item) => item.team === 'A');
  const teamB = items.filter((item) => item.team === 'B');
  const eloDifference = Math.abs(averageElo(teamA) - averageElo(teamB));
  const levelA = averageLevel(teamA);
  const levelB = averageLevel(teamB);
  const levelDifference = levelA === null || levelB === null ? null : Math.abs(levelA - levelB);
  const failure = balance.error ?? save.error ?? start.error;

  function runBalance() {
    balance.mutate(undefined, {
      onSuccess: (result) =>
        setDraft([
          ...result.teamA.map((player) => ({ ...player, team: 'A' as const })),
          ...result.teamB.map((player) => ({ ...player, team: 'B' as const })),
        ]),
    });
  }

  function edit(playerId: string, change: Partial<LineupItem>) {
    setDraft(items.map((item) => (item.playerId === playerId ? { ...item, ...change } : item)));
  }

  function saveLineup() {
    save.mutate(
      items.map(({ playerId, team, position }) => ({ playerId, team, position })),
      { onSuccess: () => setDraft(null) },
    );
  }

  return (
    <section aria-labelledby="teams-title" className="flex flex-col gap-3">
      <h2 id="teams-title" className="text-xl font-bold">
        Equipos
      </h2>
      {failure ? <Alert variant="error">{errorMessage(failure)}</Alert> : null}
      <div className="flex flex-wrap items-center gap-4">
        <Button onClick={runBalance} disabled={balance.isPending}>
          Armar equipos
        </Button>
      </div>
      {items.length > 0 ? (
        <>
          {levelDifference !== null ? (
            <p>
              Diferencia de nivel:{' '}
              <span className="font-mono tabular-nums">{levelFormat.format(levelDifference)}</span>
            </p>
          ) : null}
          <p className="text-sm text-text-muted">
            Diferencia de ELO:{' '}
            <span className="font-mono tabular-nums">{numberFormat.format(eloDifference)}</span>
          </p>
          <div className="grid gap-4 md:grid-cols-2">
            <TeamColumn
              team="A"
              items={teamA}
              onMove={(id) => edit(id, { team: 'B' })}
              onPosition={(id, position) => edit(id, { position })}
            />
            <TeamColumn
              team="B"
              items={teamB}
              onMove={(id) => edit(id, { team: 'A' })}
              onPosition={(id, position) => edit(id, { position })}
            />
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              onClick={saveLineup}
              disabled={save.isPending || teamA.length === 0 || teamB.length === 0}
            >
              Guardar equipos
            </Button>
            <Button
              variant="secondary"
              disabled={draft !== null || match.lineup.length === 0 || start.isPending}
              onClick={() =>
                start.mutate(undefined, {
                  onSuccess: () => navigate(`/grupos/${group.id}/partidos/${match.id}/en-vivo`),
                })
              }
            >
              Empezar partido
            </Button>
          </div>
          {draft !== null ? (
            <p role="status" className="text-sm text-text-muted">
              Hay cambios sin guardar.
            </p>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

export function MatchSetupPage() {
  const { groupId = '', matchId = '' } = useParams();
  const group = useGroup(groupId);
  const match = useMatch(groupId, matchId);

  if (group.isPending || match.isPending) {
    return <p role="status">Cargando partido…</p>;
  }
  if (group.isError || match.isError) {
    const notFound = match.error instanceof ApiError && match.error.status === 404;
    return (
      <div className="flex flex-col gap-4">
        <Alert variant="error">
          {notFound ? 'Partido no encontrado.' : 'No pudimos cargar el partido.'}
        </Alert>
        <Link to={`/grupos/${groupId}/partidos`} className="text-accent underline">
          Volver a los partidos
        </Link>
      </div>
    );
  }

  const isAdmin = group.data.myRole === 'ADMIN';
  const scheduled = match.data.status === 'SCHEDULED';
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">
          {formatMatchDate(match.data.playedAt)} · {match.data.location}
        </h1>
        <p className="text-sm text-text-muted">
          Fútbol {match.data.format} · {statusLabel(match.data.status)}
        </p>
        <Link to={`/grupos/${groupId}/partidos`} className="text-accent underline">
          Volver a los partidos
        </Link>
      </div>
      <CallUpsSection group={group.data} match={match.data} />
      {isAdmin && scheduled ? <LineupBuilder group={group.data} match={match.data} /> : null}
      {!isAdmin ? <SavedTeams groupId={groupId} match={match.data} /> : null}
      {match.data.status === 'LIVE' ? (
        <Link
          to={`/grupos/${groupId}/partidos/${matchId}/en-vivo`}
          className="text-accent underline"
        >
          Ir al partido en vivo
        </Link>
      ) : null}
    </div>
  );
}
