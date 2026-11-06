import { TeamTag } from '../../components/ui';
import type { LiveState } from './api';

/** Each number lives in a fixed two-digit slot so nothing shifts when a score goes from 9 to 10. */
function ScoreSlot({ team, value }: { team: 'A' | 'B'; value: number }) {
  return (
    <span
      data-testid={`score-${team}`}
      className="inline-block min-w-score text-center font-display text-score leading-none font-medium tabular-nums"
    >
      {value}
    </span>
  );
}

export function Scoreboard({ live }: { live: LiveState }) {
  return (
    <div
      role="status"
      aria-label="Marcador"
      aria-atomic="true"
      className="sticky top-0 z-10 border-b border-border bg-background py-3"
    >
      <span className="sr-only">
        Equipo A {live.scoreA}, equipo B {live.scoreB}
      </span>
      <div aria-hidden="true" className="flex items-center justify-center gap-4">
        <TeamTag team="A" />
        <ScoreSlot team="A" value={live.scoreA} />
        <span className="font-display text-score leading-none font-medium">–</span>
        <ScoreSlot team="B" value={live.scoreB} />
        <TeamTag team="B" />
      </div>
    </div>
  );
}
