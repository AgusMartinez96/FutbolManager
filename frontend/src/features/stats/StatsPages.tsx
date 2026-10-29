import { Link, useParams } from 'react-router';
import { Alert, Button, Card } from '../../components/ui';
import { formatMatchDate } from '../matches/api';
import { useEloHistory, usePlayerStats, useRanking, type Streak } from './api';

const STREAK_LABEL: Record<Streak['type'], { one: string; many: string }> = {
  W: { one: 'victoria', many: 'victorias seguidas' },
  D: { one: 'empate', many: 'empates seguidos' },
  L: { one: 'derrota', many: 'derrotas seguidas' },
};

function streakText(streak: Streak | null): string {
  if (!streak) return 'Sin partidos todavía';
  const label = STREAK_LABEL[streak.type];
  return `${streak.length} ${streak.length === 1 ? label.one : label.many}`;
}

function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="flex flex-col gap-3">
      <Alert variant="error">{message}</Alert>
      <Button variant="secondary" onClick={onRetry}>
        Reintentar
      </Button>
    </div>
  );
}

export function RankingPage() {
  const { groupId = '' } = useParams();
  const ranking = useRanking(groupId);

  if (ranking.isPending) return <p role="status">Cargando ranking…</p>;
  if (!ranking.data) {
    return (
      <LoadError message="No pudimos cargar el ranking." onRetry={() => void ranking.refetch()} />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-bold">Ranking</h1>
      <Link to={`/grupos/${groupId}`} className="text-accent underline">
        Volver al grupo
      </Link>
      <table className="w-full text-left">
        <caption className="sr-only">Jugadores ordenados por ELO</caption>
        <thead className="text-sm text-text-muted">
          <tr>
            <th scope="col" className="py-2 pr-2">
              #
            </th>
            <th scope="col" className="py-2 pr-2">
              Jugador
            </th>
            <th scope="col" className="py-2 pr-2 text-right">
              ELO
            </th>
            <th scope="col" className="py-2 text-right">
              PJ
            </th>
          </tr>
        </thead>
        <tbody>
          {ranking.data.map((entry, index) => (
            <tr key={entry.playerId} className="border-t border-border">
              <td className="py-2 pr-2 font-mono tabular-nums">{index + 1}</td>
              <td className="py-2 pr-2">
                <Link
                  to={`/grupos/${groupId}/jugadores/${entry.playerId}`}
                  className="text-accent underline"
                >
                  {entry.name}
                </Link>
                {entry.provisional ? (
                  <span className="ml-2 rounded-full border border-border-strong px-2 text-xs">
                    Provisorio
                  </span>
                ) : null}
              </td>
              <td className="py-2 pr-2 text-right font-mono tabular-nums">{entry.currentElo}</td>
              <td className="py-2 text-right font-mono tabular-nums">{entry.played}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatItem({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex flex-col">
      <dt className="text-sm text-text-muted">{label}</dt>
      <dd className="font-mono text-2xl font-bold tabular-nums">{value}</dd>
    </div>
  );
}

function EloHistory({ groupId, playerId }: { groupId: string; playerId: string }) {
  const history = useEloHistory(groupId, playerId);

  if (history.isPending) return <p role="status">Cargando historial…</p>;
  if (!history.data) {
    return (
      <LoadError
        message="No pudimos cargar el historial de ELO."
        onRetry={() => void history.refetch()}
      />
    );
  }

  return (
    <table className="w-full text-left">
      <caption className="sr-only">Historial de ELO</caption>
      <thead className="text-sm text-text-muted">
        <tr>
          <th scope="col" className="py-2 pr-2">
            Partido
          </th>
          <th scope="col" className="py-2 text-right">
            ELO
          </th>
        </tr>
      </thead>
      <tbody>
        {history.data.map((point) => (
          <tr key={point.matchId ?? 'initial'} className="border-t border-border">
            <td className="py-2 pr-2">
              {point.playedAt ? formatMatchDate(point.playedAt) : 'Inicial'}
            </td>
            <td className="py-2 text-right font-mono tabular-nums">{point.eloAfter}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function PlayerProfilePage() {
  const { groupId = '', playerId = '' } = useParams();
  const stats = usePlayerStats(groupId, playerId);

  if (stats.isPending) return <p role="status">Cargando jugador…</p>;
  if (!stats.data) {
    return (
      <LoadError message="No pudimos cargar el jugador." onRetry={() => void stats.refetch()} />
    );
  }

  const player = stats.data;
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-bold">{player.name}</h1>
      <Link to={`/grupos/${groupId}/ranking`} className="text-accent underline">
        Volver al ranking
      </Link>
      <Card className="flex flex-col gap-3">
        <p>
          ELO <span className="font-mono text-2xl font-bold tabular-nums">{player.currentElo}</span>
          {player.provisional ? (
            <span className="ml-2 rounded-full border border-border-strong px-2 text-xs">
              Provisorio
            </span>
          ) : null}
        </p>
        <dl className="grid grid-cols-3 gap-4 sm:grid-cols-4">
          <StatItem label="PJ" value={player.played} />
          <StatItem label="G" value={player.won} />
          <StatItem label="E" value={player.drawn} />
          <StatItem label="P" value={player.lost} />
          <StatItem label="Goles" value={player.goals} />
          <StatItem label="Asistencias" value={player.assists} />
          <div className="flex flex-col">
            <dt className="text-sm text-text-muted">MVPs</dt>
            <dd>
              <span className="rounded-full bg-mvp-badge px-3 py-1 font-mono font-bold text-on-mvp tabular-nums">
                {player.mvps}
              </span>
            </dd>
          </div>
        </dl>
        <p>
          Racha: <span className="font-semibold">{streakText(player.streak)}</span>
        </p>
      </Card>
      <section aria-labelledby="elo-history-title" className="flex flex-col gap-3">
        <h2 id="elo-history-title" className="text-xl font-bold">
          Historial de ELO
        </h2>
        <EloHistory groupId={groupId} playerId={playerId} />
      </section>
    </div>
  );
}
