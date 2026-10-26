import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { Alert, Button, Card, Field } from '../../components/ui';
import { ApiError } from '../../lib/api';
import {
  useChangeRole,
  useGroup,
  useMembers,
  useRegenerateCode,
  useRemoveMember,
  useUpdateGroup,
  type GroupDetail,
} from './api';
import { errorMessage } from './errorMessage';
import { PlayersTab } from './PlayersTab';

const TABS = [
  { key: 'jugadores', label: 'Jugadores' },
  { key: 'miembros', label: 'Miembros' },
  { key: 'ajustes', label: 'Ajustes' },
] as const;
type TabKey = (typeof TABS)[number]['key'];

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
