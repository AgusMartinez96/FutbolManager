import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { AppShell } from '../../app/AppShell';
import { Button } from '../../components/ui';
import { ApiError, apiFetch } from '../../lib/api';

export type Me = { id: string; username: string; displayName: string };

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async (): Promise<Me | null> => {
      try {
        return await apiFetch<Me>('/api/auth/me');
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          return null;
        }
        throw error;
      }
    },
    staleTime: 60_000,
    retry: false,
  });
}

export function UserMenu() {
  const { data: me } = useMe();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const logout = useMutation({
    mutationFn: () => apiFetch<void>('/api/auth/logout', { method: 'POST' }),
    onSuccess: async () => {
      queryClient.clear();
      await navigate('/ingresar');
    },
  });

  if (!me) {
    return null;
  }
  return (
    <div className="flex items-center gap-2">
      <span className="text-sm text-text-muted">{me.displayName}</span>
      <Button variant="secondary" disabled={logout.isPending} onClick={() => logout.mutate()}>
        Salir
      </Button>
    </div>
  );
}

export function RequireAuth() {
  const { data: me, isPending, isError } = useMe();
  const location = useLocation();

  if (isPending) {
    return (
      <div className="min-h-dvh bg-background p-4 text-text-muted" role="status">
        Cargando…
      </div>
    );
  }
  if (isError) {
    return (
      <div className="min-h-dvh bg-background p-4 text-danger" role="alert">
        No pudimos verificar tu sesión. Recargá la página.
      </div>
    );
  }
  if (!me) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/ingresar?next=${next}`} replace />;
  }
  return <AppShell actions={<UserMenu />} />;
}
