import { createBrowserRouter, createMemoryRouter, Navigate, type RouteObject } from 'react-router';
import { LoginPage, RegisterPage } from '../features/auth/AuthPages';
import { RequireAuth } from '../features/auth/session';

const routes: RouteObject[] = [
  { path: '/ingresar', element: <LoginPage /> },
  { path: '/registro', element: <RegisterPage /> },
  {
    element: <RequireAuth />,
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
