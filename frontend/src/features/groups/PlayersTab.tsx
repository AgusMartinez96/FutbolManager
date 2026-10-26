import { useState } from 'react';
import { Link } from 'react-router';
import { Alert, Button, Card } from '../../components/ui';
import {
  useCreatePlayer,
  useDeletePlayer,
  usePlayers,
  useUpdatePlayer,
  type GroupDetail,
  type Player,
} from './api';
import { errorMessage } from './errorMessage';
import { PlayerForm, positionLabel } from './PlayerForm';

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

export function PlayersTab({ group }: { group: GroupDetail }) {
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
