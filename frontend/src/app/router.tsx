import { createBrowserRouter, createMemoryRouter, Navigate, type RouteObject } from 'react-router';
import { LoginPage, RegisterPage } from '../features/auth/AuthPages';
import { RequireAuth } from '../features/auth/session';
import { GroupDetailPage } from '../features/groups/GroupDetailPage';
import { GroupsPage, JoinByLinkPage } from '../features/groups/GroupsPages';
import { MatchesPage } from '../features/matches/MatchesPages';
import { MatchSetupPage } from '../features/matches/MatchSetupPage';

const routes: RouteObject[] = [
  { path: '/ingresar', element: <LoginPage /> },
  { path: '/registro', element: <RegisterPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/', element: <Navigate to="/grupos" replace /> },
      { path: '/grupos', element: <GroupsPage /> },
      { path: '/unirse/:code', element: <JoinByLinkPage /> },
      { path: '/grupos/:groupId', element: <GroupDetailPage /> },
      { path: '/grupos/:groupId/partidos', element: <MatchesPage /> },
      { path: '/grupos/:groupId/partidos/:matchId', element: <MatchSetupPage /> },
      { path: '*', element: <h1 className="text-2xl font-bold">Página no encontrada</h1> },
    ],
  },
];

export const router = createBrowserRouter(routes);

export function createAppRouter(initialEntries?: string[]) {
  return createMemoryRouter(routes, { initialEntries });
}
