import { useState } from 'react';
import { Button, DialogShell } from '../../components/ui';
import type { LineupItem, TeamKey } from '../matches/api';
import type { EventType } from './api';

export type PendingEvent = { type: EventType; team: TeamKey };

export const TYPE_LABEL: Record<EventType, string> = {
  GOAL: 'Gol',
  SAVE: 'Atajada',
  TACKLE: 'Corte',
  PLAY: 'Jugadón',
};
export const NO_AUTHOR = 'En contra / no sé';

const selectClass =
  'min-h-10 rounded-lg border border-border-strong bg-surface px-3 py-2 text-text';

export function EventDialog({
  pending,
  lineup,
  onSubmit,
  onClose,
}: {
  pending: PendingEvent;
  lineup: LineupItem[];
  onSubmit: (playerId: string, assistPlayerId: string) => void;
  onClose: () => void;
}) {
  const side = lineup.filter((item) => item.team === pending.team);
  const [author, setAuthor] = useState('');
  const [assist, setAssist] = useState('');
  const isGoal = pending.type === 'GOAL';
  const canSubmit = isGoal || author !== '';

  return (
    <DialogShell
      title={`${TYPE_LABEL[pending.type]} del equipo ${pending.team}`}
      description="Elegí quién fue."
      onClose={onClose}
    >
      <div className="flex flex-col gap-1">
        <label htmlFor="live-author" className="text-sm font-semibold">
          Autor
        </label>
        <select
          id="live-author"
          className={selectClass}
          value={author}
          onChange={(e) => setAuthor(e.target.value)}
        >
          {isGoal ? <option value="">{NO_AUTHOR}</option> : null}
          {!isGoal && author === '' ? <option value="">Elegí un jugador</option> : null}
          {side.map((item) => (
            <option key={item.playerId} value={item.playerId}>
              {item.name}
            </option>
          ))}
        </select>
      </div>
      {isGoal && author !== '' ? (
        <div className="flex flex-col gap-1">
          <label htmlFor="live-assist" className="text-sm font-semibold">
            Asistencia
          </label>
          <select
            id="live-assist"
            className={selectClass}
            value={assist}
            onChange={(e) => setAssist(e.target.value)}
          >
            <option value="">Sin asistencia</option>
            {side
              .filter((item) => item.playerId !== author)
              .map((item) => (
                <option key={item.playerId} value={item.playerId}>
                  {item.name}
                </option>
              ))}
          </select>
        </div>
      ) : null}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Cancelar
        </Button>
        <Button disabled={!canSubmit} onClick={() => onSubmit(author, isGoal ? assist : '')}>
          Registrar
        </Button>
      </div>
    </DialogShell>
  );
}
