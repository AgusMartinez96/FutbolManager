import { Button, Card } from '../../components/ui';
import type { Position } from '../groups/api';
import type { LineupItem, TeamKey } from './api';

export const POSITIONS: { value: Position; label: string }[] = [
  { value: 'GK', label: 'ARQ' },
  { value: 'DEF', label: 'DEF' },
  { value: 'MID', label: 'MED' },
  { value: 'FWD', label: 'DEL' },
];

export const selectClass =
  'min-h-10 rounded-lg border border-border-strong bg-surface px-3 py-2 text-text';

export const numberFormat = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });
export const levelFormat = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

export function averageElo(items: LineupItem[]): number {
  return items.length === 0
    ? 0
    : items.reduce((sum, item) => sum + item.currentElo, 0) / items.length;
}

/** Suma de nivel en décimas enteras; null cuando algún jugador no trae nivel (equipos guardados). */
export function totalLevelTenths(items: LineupItem[]): number | null {
  if (items.some((item) => item.level === undefined)) {
    return null;
  }
  return items.reduce((sum, item) => sum + Math.round((item.level ?? 0) * 10), 0);
}

export function TeamColumn({
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
  const totalTenths = totalLevelTenths(items);
  return (
    <Card className="flex flex-col gap-3">
      <h3 className="text-lg font-bold">Equipo {team}</h3>
      <p className="text-sm text-text-muted tabular-nums">
        {items.length} {items.length === 1 ? 'jugador' : 'jugadores'}
      </p>
      {totalTenths !== null ? (
        <p>
          Nivel total:{' '}
          <span className="font-mono tabular-nums">{levelFormat.format(totalTenths / 10)}</span>
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
