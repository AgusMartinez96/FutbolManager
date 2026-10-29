import { ChevronRight } from 'lucide-react';
import { Fragment } from 'react';
import { Link, useLocation } from 'react-router';
import { useGroup } from '../features/groups/api';

type Crumb = { label: string; to?: string };

function crumbsAfterGroup(base: string, rest: string[]): Crumb[] {
  const [section, id, leaf] = rest;
  if (section === 'ranking') return [{ label: 'Ranking' }];
  if (section === 'jugadores' && id) {
    return [{ label: 'Ranking', to: `${base}/ranking` }, { label: 'Jugador' }];
  }
  if (section !== 'partidos') return [];
  if (!id) return [{ label: 'Partidos' }];
  const matches = { label: 'Partidos', to: `${base}/partidos` };
  if (leaf === 'en-vivo') {
    return [matches, { label: 'Partido', to: `${base}/partidos/${id}` }, { label: 'En vivo' }];
  }
  return [matches, { label: 'Partido' }];
}

function GroupCrumbs({ groupId, rest }: { groupId: string; rest: string[] }) {
  const group = useGroup(groupId);
  const base = `/grupos/${groupId}`;
  const after = crumbsAfterGroup(base, rest);
  const crumbs: Crumb[] = [
    { label: 'Mis grupos', to: '/grupos' },
    { label: group.data?.name ?? 'Grupo', to: after.length > 0 ? base : undefined },
    ...after,
  ];

  return (
    <nav aria-label="Migas de pan" className="border-b border-border bg-background">
      <ol className="mx-auto flex max-w-240 flex-wrap items-center gap-1 px-4 py-2 text-sm">
        {crumbs.map((crumb, index) => (
          <Fragment key={`${index}-${crumb.label}`}>
            {index > 0 ? (
              <li aria-hidden="true" className="text-text-muted">
                <ChevronRight size={14} />
              </li>
            ) : null}
            <li>
              {crumb.to ? (
                <Link to={crumb.to} className="inline-block min-h-6 text-accent underline">
                  {crumb.label}
                </Link>
              ) : (
                <span aria-current="page" className="text-text-muted">
                  {crumb.label}
                </span>
              )}
            </li>
          </Fragment>
        ))}
      </ol>
    </nav>
  );
}

export function Breadcrumbs() {
  const { pathname } = useLocation();
  const [root, groupId, ...rest] = pathname.split('/').filter(Boolean);
  if (root !== 'grupos' || !groupId) return null;
  return <GroupCrumbs groupId={groupId} rest={rest} />;
}
