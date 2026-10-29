import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Alert, Button, DialogShell } from '../../components/ui';
import { ApiError } from '../../lib/api';
import type { LineupItem } from '../matches/api';
import {
  useAnnulMatch,
  useChangeMvp,
  useCloseMatch,
  type CloseResult,
  type EloChange,
  type LiveState,
} from './api';

type Mode = 'close' | 'mvp' | 'annul';

const selectClass =
  'min-h-10 rounded-lg border border-border-strong bg-surface px-3 py-2 text-text';

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : 'No pudimos conectar con el servidor.';
}

/** Signed delta plus a word, so the direction never depends on color alone. */
export function formatDelta(delta: number): string {
  if (delta > 0) return `+${delta} (sube)`;
  if (delta < 0) return `${delta} (baja)`;
  return '0 (sin cambios)';
}

function EloSummary({ changes }: { changes: EloChange[] }) {
  return (
    <ul className="flex flex-col gap-2" aria-label="Cambios de ELO">
      {changes.map((change) => (
        <li key={change.playerId} className="flex items-center justify-between gap-3">
          <span>{change.name}</span>
          <span className="font-mono tabular-nums">
            {change.eloBefore} → {change.eloAfter} · {formatDelta(change.delta)}
          </span>
        </li>
      ))}
    </ul>
  );
}

function MvpSelect({
  lineup,
  value,
  onChange,
  optional,
}: {
  lineup: LineupItem[];
  value: string;
  onChange: (value: string) => void;
  optional: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor="close-mvp" className="text-sm font-semibold">
        {optional ? 'MVP (opcional)' : 'MVP'}
      </label>
      <select
        id="close-mvp"
        className={selectClass}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">{optional ? 'Sin MVP' : 'Elegí un jugador'}</option>
        {lineup.map((item) => (
          <option key={item.playerId} value={item.playerId}>
            {item.name}
          </option>
        ))}
      </select>
    </div>
  );
}

/** Admin-only match lifecycle: close a live match, change the MVP or annul a closed one. */
export function CloseControls({
  groupId,
  matchId,
  state,
  lineup,
}: {
  groupId: string;
  matchId: string;
  state: LiveState;
  lineup: LineupItem[];
}) {
  const navigate = useNavigate();
  const closeMatch = useCloseMatch(groupId, matchId);
  const changeMvp = useChangeMvp(groupId, matchId);
  const annul = useAnnulMatch(groupId, matchId);
  const [mode, setMode] = useState<Mode | null>(null);
  const [mvp, setMvp] = useState('');
  const [result, setResult] = useState<CloseResult | null>(null);

  if (!state.isAdmin) return null;

  const dismiss = () => {
    setMode(null);
    setMvp('');
    setResult(null);
    closeMatch.reset();
    changeMvp.reset();
    annul.reset();
  };
  const open = (next: Mode) => {
    dismiss();
    setMode(next);
  };

  return (
    <section aria-label="Cierre del partido" className="flex flex-col gap-3">
      {state.status === 'LIVE' ? (
        <Button size="live" variant="danger" onClick={() => open('close')}>
          Cerrar partido
        </Button>
      ) : null}
      {state.status === 'CLOSED' ? (
        <>
          <Button size="live" variant="secondary" onClick={() => open('mvp')}>
            Cambiar MVP
          </Button>
          <Button size="live" variant="danger" onClick={() => open('annul')}>
            Anular partido
          </Button>
        </>
      ) : null}

      {mode === 'close' || mode === 'mvp' ? (
        <DialogShell
          title={result ? 'Resumen del ELO' : mode === 'close' ? 'Cerrar partido' : 'Cambiar MVP'}
          description={
            result
              ? `Marcador final ${result.scoreA} – ${result.scoreB}.`
              : 'El ELO se recalcula con el resultado y los eventos cargados.'
          }
          onClose={dismiss}
        >
          {result ? (
            <>
              <EloSummary changes={result.eloChanges} />
              <div className="flex justify-end">
                <Button onClick={dismiss}>Listo</Button>
              </div>
            </>
          ) : (
            <>
              <MvpSelect
                lineup={lineup}
                value={mvp}
                onChange={setMvp}
                optional={mode === 'close'}
              />
              {closeMatch.isError || changeMvp.isError ? (
                <Alert variant="error">{errorMessage(closeMatch.error ?? changeMvp.error)}</Alert>
              ) : null}
              <div className="flex justify-end gap-2">
                <Button variant="secondary" onClick={dismiss}>
                  Cancelar
                </Button>
                <Button
                  disabled={closeMatch.isPending || changeMvp.isPending || (mode === 'mvp' && !mvp)}
                  onClick={() => {
                    const onSuccess = (data: CloseResult) => setResult(data);
                    if (mode === 'close') {
                      closeMatch.mutate(mvp || undefined, { onSuccess });
                    } else {
                      changeMvp.mutate(mvp, { onSuccess });
                    }
                  }}
                >
                  {mode === 'close' ? 'Confirmar cierre' : 'Guardar MVP'}
                </Button>
              </div>
            </>
          )}
        </DialogShell>
      ) : null}

      {mode === 'annul' ? (
        <DialogShell
          title="Anular partido"
          description="El partido deja de contar y el ELO de los partidos posteriores se recalcula."
          onClose={dismiss}
        >
          {annul.isError ? <Alert variant="error">{errorMessage(annul.error)}</Alert> : null}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={dismiss}>
              Cancelar
            </Button>
            <Button
              variant="danger"
              disabled={annul.isPending}
              onClick={() =>
                annul.mutate(undefined, {
                  onSuccess: () => void navigate(`/grupos/${groupId}/partidos`),
                })
              }
            >
              Sí, anular
            </Button>
          </div>
        </DialogShell>
      ) : null}
    </section>
  );
}
