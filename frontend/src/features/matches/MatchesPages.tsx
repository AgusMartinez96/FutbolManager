import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { Alert, Button, Card, Field } from '../../components/ui';
import { ApiError } from '../../lib/api';
import { useGroup } from '../groups/api';
import { formatMatchDate, statusLabel, useCreateMatch, useMatches } from './api';

const FORMATS = [5, 7, 8, 11];

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : 'No pudimos conectar con el servidor.';
}

function CreateMatchForm({ groupId }: { groupId: string }) {
  const [playedAt, setPlayedAt] = useState('');
  const [location, setLocation] = useState('');
  const [format, setFormat] = useState(7);
  const [errors, setErrors] = useState<{ playedAt?: string; location?: string }>({});
  const navigate = useNavigate();
  const create = useCreateMatch(groupId);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = location.trim();
    const next = {
      playedAt: playedAt ? undefined : 'Elegí la fecha y la hora.',
      location:
        trimmed && trimmed.length <= 120 ? undefined : 'Ingresá un lugar de hasta 120 caracteres.',
    };
    setErrors(next);
    if (next.playedAt || next.location) {
      return;
    }
    create.mutate(
      { playedAt: new Date(playedAt).toISOString(), location: trimmed, format },
      { onSuccess: (match) => navigate(`/grupos/${groupId}/partidos/${match.id}`) },
    );
  }

  return (
    <Card>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        {create.isError ? <Alert variant="error">{errorMessage(create.error)}</Alert> : null}
        <Field
          label="Fecha y hora"
          type="datetime-local"
          value={playedAt}
          error={errors.playedAt}
          onChange={(event) => setPlayedAt(event.target.value)}
        />
        <Field
          label="Lugar"
          value={location}
          error={errors.location}
          onChange={(event) => setLocation(event.target.value)}
        />
        <div className="flex flex-col gap-1">
          <label htmlFor="match-format" className="text-sm font-semibold">
            Formato
          </label>
          <select
            id="match-format"
            value={format}
            onChange={(event) => setFormat(Number(event.target.value))}
            className="min-h-10 rounded-lg border border-border-strong bg-surface px-3 py-2 text-text"
          >
            {FORMATS.map((value) => (
              <option key={value} value={value}>
                Fútbol {value}
              </option>
            ))}
          </select>
        </div>
        <div>
          <Button type="submit" disabled={create.isPending}>
            Crear
          </Button>
        </div>
      </form>
    </Card>
  );
}

export function MatchesPage() {
  const { groupId = '' } = useParams();
  const group = useGroup(groupId);
  const matches = useMatches(groupId);
  const [creating, setCreating] = useState(false);
  const isAdmin = group.data?.myRole === 'ADMIN';

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-bold">Partidos</h1>
      <Link to={`/grupos/${groupId}`} className="text-accent underline">
        Volver al grupo
      </Link>
      {isAdmin ? (
        <div>
          <Button onClick={() => setCreating((open) => !open)} aria-expanded={creating}>
            Crear partido
          </Button>
        </div>
      ) : null}
      {isAdmin && creating ? <CreateMatchForm groupId={groupId} /> : null}

      {matches.isPending ? (
        <p role="status" className="text-text-muted">
          Cargando partidos…
        </p>
      ) : matches.isError ? (
        <Alert variant="error" className="flex flex-col items-start gap-2">
          <span>No pudimos cargar los partidos.</span>
          <Button variant="secondary" onClick={() => void matches.refetch()}>
            Reintentar
          </Button>
        </Alert>
      ) : matches.data.length === 0 ? (
        <Card>
          <p className="font-semibold">No hay partidos todavía.</p>
        </Card>
      ) : (
        <ul className="flex flex-col gap-2">
          {matches.data.map((match) => (
            <li key={match.id}>
              <Link
                to={`/grupos/${groupId}/partidos/${match.id}`}
                className="flex min-h-12 flex-wrap items-center justify-between gap-2 rounded-xl border border-border bg-surface px-4 py-3 hover:bg-surface-raised"
              >
                <span>
                  <span className="block font-semibold">{formatMatchDate(match.playedAt)}</span>
                  <span className="block text-sm text-text-muted">
                    {match.location} · Fútbol {match.format}
                  </span>
                </span>
                <span className="flex items-center gap-3 text-sm text-text-muted">
                  {match.status === 'CLOSED' ? (
                    <span className="font-mono tabular-nums">
                      {match.scoreA} - {match.scoreB}
                    </span>
                  ) : null}
                  {statusLabel(match.status)}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
