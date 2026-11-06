import { Trophy } from 'lucide-react';
import { useParams } from 'react-router';
import { Alert, Button, Card, TextLink } from '../../components/ui';
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

function ProvisionalBadge() {
  return (
    <span className="ml-2 inline-flex items-center rounded-pill border border-border-strong px-2 text-xs font-normal">
      Provisorio
    </span>
  );
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
      <h1 className="font-display text-3xl font-medium">Ranking</h1>
      <TextLink to={`/grupos/${groupId}`} block className="self-start">
        Volver al grupo
      </TextLink>
      <table className="w-full text-left">
        <caption className="sr-only">Jugadores ordenados por ELO</caption>
        <thead className="text-sm text-text-muted">
          <tr>
            <th scope="col" className="py-2 pr-2 font-normal">
              #
            </th>
            <th scope="col" className="py-2 pr-2 font-normal">
              Jugador
            </th>
            <th scope="col" className="py-2 pr-2 text-right font-normal">
              ELO
            </th>
            <th scope="col" className="py-2 text-right font-normal">
              PJ
            </th>
          </tr>
        </thead>
        <tbody>
          {ranking.data.map((entry, index) => (
            <tr key={entry.playerId} className="border-t border-border">
              <td className="py-2 pr-2 font-display text-xl font-medium tabular-nums">
                {index + 1}
              </td>
              <td className="py-2 pr-2 font-display text-xl font-medium break-words">
                <TextLink to={`/grupos/${groupId}/jugadores/${entry.playerId}`}>
                  {entry.name}
                </TextLink>
                {entry.provisional ? <ProvisionalBadge /> : null}
              </td>
              <td className="py-2 pr-2 text-right font-display text-xl font-medium tabular-nums">
                {entry.currentElo}
              </td>
              <td className="py-2 text-right tabular-nums">{entry.played}</td>
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
      <dd className="text-2xl font-medium tabular-nums">{value}</dd>
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
          <th scope="col" className="py-2 pr-2 font-normal">
            Partido
          </th>
          <th scope="col" className="py-2 text-right font-normal">
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
            <td className="py-2 text-right tabular-nums">{point.eloAfter}</td>
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
      <h1 className="text-2xl font-medium break-words">{player.name}</h1>
      <TextLink to={`/grupos/${groupId}/ranking`} block className="self-start">
        Volver al ranking
      </TextLink>
      <Card className="flex flex-col gap-3">
        <p>
          ELO <span className="text-2xl font-medium tabular-nums">{player.currentElo}</span>
          {player.provisional ? <ProvisionalBadge /> : null}
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
              <span
                data-testid="mvp-badge"
                className="inline-flex items-center gap-1 rounded-pill bg-primary px-3 py-1 font-medium text-on-primary tabular-nums"
              >
                <Trophy aria-hidden="true" size={16} />
                {player.mvps}
              </span>
            </dd>
          </div>
        </dl>
        <p>
          Racha: <span className="font-medium">{streakText(player.streak)}</span>
        </p>
      </Card>
      <section aria-labelledby="elo-history-title" className="flex flex-col gap-3">
        <h2 id="elo-history-title" className="text-xl font-medium">
          Historial de ELO
        </h2>
        <EloHistory groupId={groupId} playerId={playerId} />
      </section>
    </div>
  );
}
