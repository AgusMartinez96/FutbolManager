import { createBrowserRouter, createMemoryRouter, Navigate, type RouteObject } from 'react-router';
import { AppShell } from './AppShell';

const routes: RouteObject[] = [
  {
    element: <AppShell />,
    children: [
      { path: '/', element: <Navigate to="/grupos" replace /> },
      { path: '/grupos', element: <h1 className="text-2xl font-bold">Mis grupos</h1> },
      { path: '*', element: <h1 className="text-2xl font-bold">Página no encontrada</h1> },
    ],
  },
];

export const router = createBrowserRouter(routes);

export function createAppRouter(initialEntries?: string[]) {
  return createMemoryRouter(routes, { initialEntries });
}
