import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { Alert, Button, Card, Field } from '../../components/ui';
import { ApiError } from '../../lib/api';
import { useCreateGroup, useGroups, useJoinGroup } from './api';

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : 'No pudimos conectar con el servidor.';
}

function CreateGroupForm() {
  const [name, setName] = useState('');
  const [fieldError, setFieldError] = useState<string>();
  const navigate = useNavigate();
  const create = useCreateGroup();

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > 60) {
      setFieldError('Ingresá un nombre de hasta 60 caracteres.');
      return;
    }
    setFieldError(undefined);
    create.mutate(trimmed, {
      onSuccess: (group) => navigate(`/grupos/${group.id}`),
    });
  }

  return (
    <Card>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        {create.isError ? <Alert variant="error">{errorMessage(create.error)}</Alert> : null}
        <Field
          label="Nombre del grupo"
          value={name}
          error={fieldError}
          onChange={(event) => setName(event.target.value)}
        />
        <Button type="submit" disabled={create.isPending}>
          Crear
        </Button>
      </form>
    </Card>
  );
}

function JoinCodeForm() {
  const [code, setCode] = useState('');
  const [fieldError, setFieldError] = useState<string>();
  const navigate = useNavigate();
  const join = useJoinGroup();

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) {
      setFieldError('Ingresá el código de invitación.');
      return;
    }
    setFieldError(undefined);
    join.mutate(trimmed, {
      onSuccess: (joined) => navigate(`/grupos/${joined.groupId}`),
    });
  }

  return (
    <Card>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        {join.isError ? <Alert variant="error">{errorMessage(join.error)}</Alert> : null}
        <Field
          label="Código de invitación"
          value={code}
          error={fieldError}
          autoCapitalize="characters"
          onChange={(event) => setCode(event.target.value)}
        />
        <Button type="submit" disabled={join.isPending}>
          Unirme
        </Button>
      </form>
    </Card>
  );
}

export function GroupsPage() {
  const groups = useGroups();
  const [panel, setPanel] = useState<'create' | 'join' | null>(null);

  function toggle(next: 'create' | 'join') {
    setPanel((current) => (current === next ? null : next));
  }

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-bold">Mis grupos</h1>
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => toggle('create')} aria-expanded={panel === 'create'}>
          Crear grupo
        </Button>
        <Button variant="secondary" onClick={() => toggle('join')} aria-expanded={panel === 'join'}>
          Unirme con código
        </Button>
      </div>
      {panel === 'create' ? <CreateGroupForm /> : null}
      {panel === 'join' ? <JoinCodeForm /> : null}

      {groups.isPending ? (
        <p role="status" className="text-text-muted">
          Cargando grupos…
        </p>
      ) : groups.isError ? (
        <Alert variant="error" className="flex flex-col items-start gap-2">
          <span>No pudimos cargar tus grupos.</span>
          <Button variant="secondary" onClick={() => void groups.refetch()}>
            Reintentar
          </Button>
        </Alert>
      ) : groups.data.length === 0 ? (
        <Card>
          <p className="font-semibold">Todavía no tenés grupos</p>
          <p className="text-sm text-text-muted">
            Creá uno nuevo o sumate con el código que te pasó un amigo.
          </p>
        </Card>
      ) : (
        <ul className="flex flex-col gap-2">
          {groups.data.map((group) => (
            <li key={group.id}>
              <Link
                to={`/grupos/${group.id}`}
                className="flex min-h-12 items-center justify-between gap-2 rounded-xl border border-border bg-surface px-4 py-3 hover:bg-surface-raised"
              >
                <span className="font-semibold">{group.name}</span>
                <span className="text-sm text-text-muted">
                  {group.myRole === 'ADMIN' ? 'Admin' : 'Miembro'}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function JoinByLinkPage() {
  const { code = '' } = useParams();
  const navigate = useNavigate();
  const join = useJoinGroup();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) {
      return;
    }
    started.current = true;
    join.mutate(code, {
      onSuccess: (joined) => navigate(`/grupos/${joined.groupId}`, { replace: true }),
    });
  }, [code, join, navigate]);

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-bold">Unirme a un grupo</h1>
      {join.isError ? (
        <>
          <Alert variant="error">{errorMessage(join.error)}</Alert>
          <Link to="/grupos" className="text-accent underline">
            Volver a mis grupos
          </Link>
        </>
      ) : (
        <p role="status" className="text-text-muted">
          Uniéndote al grupo…
        </p>
      )}
    </div>
  );
}
