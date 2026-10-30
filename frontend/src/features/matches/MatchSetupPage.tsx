import { Link, useParams } from 'react-router';
import { Alert, Card } from '../../components/ui';
import { ApiError } from '../../lib/api';
import { useGroup, usePlayers, type GroupDetail } from '../groups/api';
import {
  errorMessage,
  formatMatchDate,
  statusLabel,
  useMatch,
  useSetCallUp,
  type CallUpStatus,
  type MatchDetail,
} from './api';
import { LineupBuilder } from './LineupBuilder';
import { POSITIONS, selectClass } from './TeamColumn';

const CALL_UP_OPTIONS: { value: CallUpStatus; label: string }[] = [
  { value: 'CONFIRMED', label: 'Confirmado' },
  { value: 'MAYBE', label: 'Quizás' },
  { value: 'NOT_GOING', label: 'No va' },
];

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
