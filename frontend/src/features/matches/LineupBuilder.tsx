import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Alert, Button } from '../../components/ui';
import { usePlayers, type GroupDetail } from '../groups/api';
import {
  errorMessage,
  useBalance,
  useSaveLineup,
  useStartMatch,
  type LineupItem,
  type MatchDetail,
} from './api';
import { TeamColumn, averageElo, levelFormat, numberFormat, totalLevelTenths } from './TeamColumn';

export function LineupBuilder({ group, match }: { group: GroupDetail; match: MatchDetail }) {
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
  const totalA = totalLevelTenths(teamA);
  const totalB = totalLevelTenths(teamB);
  const levelDifferenceTenths =
    totalA === null || totalB === null ? null : Math.abs(totalA - totalB);
  const sizeGap = Math.abs(teamA.length - teamB.length);
  const failure = balance.error ?? save.error ?? start.error;

  function runBalance() {
    balance.mutate(undefined, {
      onSuccess: ({ options }) => {
        const first = options[0];
        if (first === undefined) {
          return;
        }
        setDraft([
          ...first.teamA.map((player) => ({ ...player, team: 'A' as const })),
          ...first.teamB.map((player) => ({ ...player, team: 'B' as const })),
        ]);
      },
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
          {sizeGap > 1 ? (
            <Alert variant="error">
              Los equipos tienen {teamA.length} y {teamB.length} jugadores: la diferencia es mayor a
              1.
            </Alert>
          ) : null}
          {levelDifferenceTenths !== null ? (
            <p>
              Diferencia de nivel:{' '}
              <span className="font-mono tabular-nums">
                {levelFormat.format(levelDifferenceTenths / 10)}
              </span>
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
