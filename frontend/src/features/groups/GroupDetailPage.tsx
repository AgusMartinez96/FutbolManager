import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { Alert, Button, Card, Field } from '../../components/ui';
import { ApiError } from '../../lib/api';
import {
  useChangeRole,
  useCreatePlayer,
  useDeletePlayer,
  useGroup,
  useMembers,
  usePlayers,
  useRegenerateCode,
  useRemoveMember,
  useUpdateGroup,
  useUpdatePlayer,
  type GroupDetail,
  type Player,
  type PlayerInput,
  type Position,
} from './api';

const TABS = [
  { key: 'jugadores', label: 'Jugadores' },
  { key: 'miembros', label: 'Miembros' },
  { key: 'ajustes', label: 'Ajustes' },
] as const;
type TabKey = (typeof TABS)[number]['key'];

const POSITIONS: { value: Position; label: string }[] = [
  { value: 'GK', label: 'ARQ' },
  { value: 'DEF', label: 'DEF' },
  { value: 'MID', label: 'MED' },
  { value: 'FWD', label: 'DEL' },
];

function positionLabel(position: Position): string {
  return POSITIONS.find((item) => item.value === position)?.label ?? position;
}

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : 'No pudimos conectar con el servidor.';
}

function parseLevel(text: string): number | undefined {
  const value = text.trim();
  if (!/^\d+$/.test(value)) {
    return undefined;
  }
  const level = Number(value);
  return level >= 1 && level <= 10 ? level : undefined;
}

function PlayerForm({
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
  const [levelText, setLevelText] = useState(player?.level?.toString() ?? '');
  const [nameError, setNameError] = useState<string>();
  const [levelError, setLevelError] = useState<string>();

  function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    const nextNameError =
      !trimmed || trimmed.length > 60 ? 'Ingresá un nombre de hasta 60 caracteres.' : undefined;
    const level = isAdmin ? parseLevel(levelText) : undefined;
    const nextLevelError =
      isAdmin && level === undefined ? 'Ingresá un nivel entero de 1 a 10.' : undefined;
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
          label="Nivel (1 a 10)"
          type="number"
          inputMode="numeric"
          min={1}
          max={10}
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

function PlayerRow({ group, player }: { group: GroupDetail; player: Player }) {
  const isAdmin = group.myRole === 'ADMIN';
  const [mode, setMode] = useState<'view' | 'edit' | 'delete'>('view');
  const update = useUpdatePlayer(group.id);
  const remove = useDeletePlayer(group.id);

  if (mode === 'edit') {
    return (
      <Card>
        <PlayerForm
          player={player}
          isAdmin={isAdmin}
          submitLabel="Guardar"
          pending={update.isPending}
          error={update.isError ? errorMessage(update.error) : undefined}
          onCancel={() => setMode('view')}
          onSubmit={(body) =>
            update.mutate({ playerId: player.id, body }, { onSuccess: () => setMode('view') })
          }
        />
      </Card>
    );
  }

  return (
    <Card className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <Link
          to={`/grupos/${group.id}/jugadores/${player.id}`}
          className="font-semibold text-accent underline"
        >
          {player.name}
        </Link>
        <span className="flex items-center gap-3">
          <span className="text-sm text-text-muted">{positionLabel(player.preferredPosition)}</span>
          {isAdmin && player.level !== undefined ? (
            <span className="text-sm text-text-muted">Nivel {player.level}</span>
          ) : null}
          <span className="font-mono tabular-nums">{player.currentElo}</span>
        </span>
      </div>
      {player.nickname ? <p className="text-sm text-text-muted">“{player.nickname}”</p> : null}
      {isAdmin && mode === 'view' ? (
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setMode('edit')}>
            Editar
          </Button>
          <Button variant="secondary" onClick={() => setMode('delete')}>
            Borrar
          </Button>
        </div>
      ) : null}
      {mode === 'delete' ? (
        <div className="flex flex-col gap-2">
          <Alert variant="error">
            ¿Borrar a {player.name}? Su historial de ELO se conserva.
            {remove.isError ? ` ${errorMessage(remove.error)}` : ''}
          </Alert>
          <div className="flex gap-2">
            <Button
              variant="danger"
              disabled={remove.isPending}
              onClick={() => remove.mutate(player.id)}
            >
              Confirmar borrado
            </Button>
            <Button variant="secondary" onClick={() => setMode('view')}>
              Cancelar
            </Button>
          </div>
        </div>
      ) : null}
    </Card>
  );
}

function PlayersTab({ group }: { group: GroupDetail }) {
  const players = usePlayers(group.id);
  const create = useCreatePlayer(group.id);
  const [adding, setAdding] = useState(false);

  if (players.isPending) {
    return <p role="status">Cargando jugadores…</p>;
  }
  if (players.isError) {
    return <Alert variant="error">No pudimos cargar los jugadores.</Alert>;
  }
  return (
    <div className="flex flex-col gap-4">
      {group.myRole === 'ADMIN' ? (
        adding ? (
          <Card>
            <PlayerForm
              isAdmin
              submitLabel="Agregar"
              pending={create.isPending}
              error={create.isError ? errorMessage(create.error) : undefined}
              onCancel={() => setAdding(false)}
              onSubmit={(body) => create.mutate(body, { onSuccess: () => setAdding(false) })}
            />
          </Card>
        ) : (
          <div>
            <Button onClick={() => setAdding(true)}>Agregar jugador</Button>
          </div>
        )
      ) : null}
      {players.data.length === 0 ? (
        <p className="text-text-muted">Todavía no hay jugadores.</p>
      ) : null}
      <ul className="flex flex-col gap-2">
        {players.data.map((player) => (
          <li key={player.id}>
            <PlayerRow group={group} player={player} />
          </li>
        ))}
      </ul>
    </div>
  );
}

function MembersTab({ group }: { group: GroupDetail }) {
  const members = useMembers(group.id);
  const changeRole = useChangeRole(group.id);
  const remove = useRemoveMember(group.id);
  const navigate = useNavigate();
  const isAdmin = group.myRole === 'ADMIN';

  if (members.isPending) {
    return <p role="status">Cargando miembros…</p>;
  }
  if (members.isError) {
    return <Alert variant="error">No pudimos cargar los miembros.</Alert>;
  }
  const failure = changeRole.error ?? remove.error;
  return (
    <div className="flex flex-col gap-4">
      {failure ? <Alert variant="error">{errorMessage(failure)}</Alert> : null}
      <ul className="flex flex-col gap-2">
        {members.data.map((member) => {
          const isMe = member.userId === group.myUserId;
          return (
            <li key={member.userId}>
              <Card className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-semibold">{member.displayName}</p>
                  <p className="text-sm text-text-muted">
                    @{member.username} · {member.role === 'ADMIN' ? 'Admin' : 'Miembro'}
                  </p>
                </div>
                <div className="flex gap-2">
                  {isAdmin ? (
                    <Button
                      variant="secondary"
                      disabled={changeRole.isPending}
                      aria-label={`Cambiar rol de ${member.displayName}`}
                      onClick={() =>
                        changeRole.mutate({
                          userId: member.userId,
                          role: member.role === 'ADMIN' ? 'MEMBER' : 'ADMIN',
                        })
                      }
                    >
                      Cambiar rol
                    </Button>
                  ) : null}
                  {isAdmin && !isMe ? (
                    <Button
                      variant="secondary"
                      disabled={remove.isPending}
                      aria-label={`Quitar a ${member.displayName}`}
                      onClick={() => remove.mutate(member.userId)}
                    >
                      Quitar
                    </Button>
                  ) : null}
                  {isMe ? (
                    <Button
                      variant="danger"
                      disabled={remove.isPending}
                      onClick={() =>
                        remove.mutate(member.userId, {
                          onSuccess: () => navigate('/grupos', { replace: true }),
                        })
                      }
                    >
                      Salir del grupo
                    </Button>
                  ) : null}
                </div>
              </Card>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function SettingsTab({ group }: { group: GroupDetail }) {
  const [name, setName] = useState(group.name);
  const [copied, setCopied] = useState(false);
  const update = useUpdateGroup(group.id);
  const regenerate = useRegenerateCode(group.id);
  const link = `${window.location.origin}/unirse/${group.inviteCode}`;

  if (group.myRole !== 'ADMIN') {
    return <p className="text-text-muted">Solo los administradores pueden cambiar los ajustes.</p>;
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (name.trim()) {
              update.mutate({ name: name.trim() });
            }
          }}
        >
          {update.isError ? <Alert variant="error">{errorMessage(update.error)}</Alert> : null}
          <Field
            label="Nombre del grupo"
            value={name}
            maxLength={60}
            onChange={(event) => setName(event.target.value)}
          />
          <div>
            <Button type="submit" disabled={update.isPending}>
              Guardar nombre
            </Button>
          </div>
        </form>
      </Card>
      <Card className="flex items-center gap-3">
        <input
          id="members-can-score"
          type="checkbox"
          className="size-6"
          checked={group.membersCanScore}
          disabled={update.isPending}
          onChange={(event) => update.mutate({ membersCanScore: event.target.checked })}
        />
        <label htmlFor="members-can-score">Los miembros pueden cargar goles en vivo</label>
      </Card>
      <Card className="flex flex-col gap-3">
        <p className="text-sm text-text-muted">Código de invitación</p>
        <p className="font-mono text-xl tabular-nums">{group.inviteCode}</p>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => void copy()}>
            Copiar link
          </Button>
          <Button
            variant="secondary"
            disabled={regenerate.isPending}
            onClick={() => {
              setCopied(false);
              regenerate.mutate();
            }}
          >
            Regenerar código
          </Button>
        </div>
        {copied ? <p role="status">Link copiado.</p> : null}
        {regenerate.isError ? (
          <Alert variant="error">{errorMessage(regenerate.error)}</Alert>
        ) : null}
      </Card>
    </div>
  );
}

export function GroupDetailPage() {
  const { groupId = '' } = useParams();
  const group = useGroup(groupId);
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get('tab');
  const tab: TabKey = TABS.find((item) => item.key === requested)?.key ?? 'jugadores';

  if (group.isPending) {
    return <p role="status">Cargando grupo…</p>;
  }
  if (group.isError) {
    const notFound = group.error instanceof ApiError && group.error.status === 404;
    return (
      <div className="flex flex-col gap-4">
        <Alert variant="error">
          {notFound ? 'Grupo no encontrado.' : 'No pudimos cargar el grupo.'}
        </Alert>
        <Link to="/grupos" className="text-accent underline">
          Volver a mis grupos
        </Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-bold">{group.data.name}</h1>
      <nav aria-label="Secciones del grupo" className="flex gap-4">
        <Link to={`/grupos/${groupId}/partidos`} className="text-accent underline">
          Partidos
        </Link>
        <Link to={`/grupos/${groupId}/ranking`} className="text-accent underline">
          Ranking
        </Link>
      </nav>
      <div role="tablist" aria-label="Detalle del grupo" className="flex gap-2">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            id={`tab-${item.key}`}
            aria-selected={tab === item.key}
            aria-controls="group-panel"
            onClick={() => setSearchParams({ tab: item.key }, { replace: true })}
            className={`min-h-10 rounded-lg border px-4 py-2 font-semibold ${
              tab === item.key
                ? 'border-accent text-accent'
                : 'border-border-strong text-text hover:bg-surface-raised'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id="group-panel" aria-labelledby={`tab-${tab}`}>
        {tab === 'jugadores' ? <PlayersTab group={group.data} /> : null}
        {tab === 'miembros' ? <MembersTab group={group.data} /> : null}
        {tab === 'ajustes' ? <SettingsTab group={group.data} /> : null}
      </div>
    </div>
  );
}
