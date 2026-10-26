import { useState, type FormEvent } from 'react';
import { Alert, Button, Field } from '../../components/ui';
import type { Player, PlayerInput, Position } from './api';

export const POSITIONS: { value: Position; label: string }[] = [
  { value: 'GK', label: 'ARQ' },
  { value: 'DEF', label: 'DEF' },
  { value: 'MID', label: 'MED' },
  { value: 'FWD', label: 'DEL' },
];

export function positionLabel(position: Position): string {
  return POSITIONS.find((item) => item.value === position)?.label ?? position;
}

export function formatLevel(level: number): string {
  return level.toFixed(1).replace('.', ',');
}

// Acepta coma o punto, un decimal (o dos con el segundo en cero: 8,50 → 8.5).
function parseLevel(text: string): number | undefined {
  const value = text.trim();
  if (!/^\d+(?:[.,]\d0?)?$/.test(value)) {
    return undefined;
  }
  const level = Number(value.replace(',', '.'));
  return level >= 1 && level <= 10 ? Math.round(level * 10) / 10 : undefined;
}

export function PlayerForm({
  player,
  isAdmin,
  submitLabel,
  pending,
  error,
  onSubmit,
  onCancel,
}: {
  player?: Player;
  isAdmin: boolean;
  submitLabel: string;
  pending: boolean;
  error?: string;
  onSubmit: (input: PlayerInput) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(player?.name ?? '');
  const [nickname, setNickname] = useState(player?.nickname ?? '');
  const [position, setPosition] = useState<Position>(player?.preferredPosition ?? 'MID');
  const [levelText, setLevelText] = useState(
    player?.level !== undefined ? formatLevel(player.level) : '',
  );
  const [nameError, setNameError] = useState<string>();
  const [levelError, setLevelError] = useState<string>();

  function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    const nextNameError =
      !trimmed || trimmed.length > 60 ? 'Ingresá un nombre de hasta 60 caracteres.' : undefined;
    const level = isAdmin ? parseLevel(levelText) : undefined;
    const nextLevelError =
      isAdmin && level === undefined ? 'Ingresá un nivel de 1,0 a 10,0 con un decimal.' : undefined;
    setNameError(nextNameError);
    setLevelError(nextLevelError);
    if (nextNameError || nextLevelError) {
      return;
    }
    onSubmit({
      name: trimmed,
      ...(nickname.trim() ? { nickname: nickname.trim() } : {}),
      preferredPosition: position,
      ...(level !== undefined ? { level } : {}),
    });
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      {error ? <Alert variant="error">{error}</Alert> : null}
      <Field
        label="Nombre"
        value={name}
        error={nameError}
        onChange={(event) => setName(event.target.value)}
      />
      <Field
        label="Apodo"
        value={nickname}
        maxLength={30}
        onChange={(event) => setNickname(event.target.value)}
      />
      <div className="flex flex-col gap-1">
        <label htmlFor="player-position" className="text-sm font-semibold">
          Puesto
        </label>
        <select
          id="player-position"
          value={position}
          onChange={(event) => setPosition(event.target.value as Position)}
          className="min-h-10 rounded-lg border border-border-strong bg-surface px-3 py-2 text-text"
        >
          {POSITIONS.map((item) => (
            <option key={item.value} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
      </div>
      {isAdmin ? (
        <Field
          label="Nivel (1,0 a 10,0)"
          inputMode="decimal"
          value={levelText}
          error={levelError}
          onChange={(event) => setLevelText(event.target.value)}
        />
      ) : null}
      <div className="flex gap-2">
        <Button type="submit" disabled={pending}>
          {submitLabel}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}
