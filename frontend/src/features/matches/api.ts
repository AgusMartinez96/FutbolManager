import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, apiFetch } from '../../lib/api';
import type { Position } from '../groups/api';

export type MatchStatus = 'SCHEDULED' | 'LIVE' | 'CLOSED' | 'ANNULLED';
export type CallUpStatus = 'CONFIRMED' | 'MAYBE' | 'NOT_GOING';
export type TeamKey = 'A' | 'B';

export type MatchSummary = {
  id: string;
  playedAt: string;
  location: string;
  format: number;
  status: MatchStatus;
  scoreA: number | null;
  scoreB: number | null;
  mvpPlayerId: string | null;
};
export type CallUp = { playerId: string; name: string; currentElo: number; status: CallUpStatus };
export type LineupItem = {
  playerId: string;
  name: string;
  currentElo: number;
  team: TeamKey;
  position: Position;
  /** Solo viene del armado de equipos (admin); el detalle guardado no lo trae. */
  level?: number;
};
export type MatchDetail = MatchSummary & { callUps: CallUp[]; lineup: LineupItem[] };

export type BalancedPlayer = {
  playerId: string;
  name: string;
  currentElo: number;
  level: number;
  position: Position;
};
export type BalanceOption = {
  teamA: BalancedPlayer[];
  teamB: BalancedPlayer[];
  totalLevelA: number;
  totalLevelB: number;
  levelDifference: number;
  avgEloA: number;
  avgEloB: number;
  eloDifference: number;
};
export type BalanceResult = { options: BalanceOption[] };

export type CreateMatchInput = { playedAt: string; location: string; format: number };
export type LineupEntryInput = { playerId: string; team: TeamKey; position: Position };

const STATUS_LABEL: Record<MatchStatus, string> = {
  SCHEDULED: 'Programado',
  LIVE: 'En vivo',
  CLOSED: 'Cerrado',
  ANNULLED: 'Anulado',
};

const dateFormat = new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short' });

export function formatMatchDate(iso: string): string {
  return dateFormat.format(new Date(iso));
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : 'No pudimos conectar con el servidor.';
}

export function statusLabel(status: MatchStatus): string {
  return STATUS_LABEL[status];
}

export function useMatches(groupId: string) {
  return useQuery({
    queryKey: ['groups', groupId, 'matches'],
    queryFn: () => apiFetch<MatchSummary[]>(`/api/groups/${groupId}/matches`),
  });
}

export function useCreateMatch(groupId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateMatchInput) =>
      apiFetch<MatchDetail>(`/api/groups/${groupId}/matches`, { method: 'POST', body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['groups', groupId, 'matches'] }),
  });
}

export function useMatch(groupId: string, matchId: string) {
  return useQuery({
    queryKey: ['groups', groupId, 'matches', matchId],
    queryFn: () => apiFetch<MatchDetail>(`/api/groups/${groupId}/matches/${matchId}`),
  });
}

export function useSetCallUp(groupId: string, matchId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { playerId: string; status: CallUpStatus }) =>
      apiFetch<CallUp>(`/api/groups/${groupId}/matches/${matchId}/call-ups/${vars.playerId}`, {
        method: 'PUT',
        body: { status: vars.status },
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['groups', groupId, 'matches', matchId] }),
  });
}

/** Proposes teams; it writes nothing on the server, so it invalidates nothing. */
export function useBalance(groupId: string, matchId: string) {
  return useMutation({
    mutationFn: () =>
      apiFetch<BalanceResult>(`/api/groups/${groupId}/matches/${matchId}/balance`, {
        method: 'POST',
      }),
  });
}

export function useSaveLineup(groupId: string, matchId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (entries: LineupEntryInput[]) =>
      apiFetch<LineupItem[]>(`/api/groups/${groupId}/matches/${matchId}/lineup`, {
        method: 'PUT',
        body: { entries },
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['groups', groupId, 'matches', matchId] }),
  });
}

export function useStartMatch(groupId: string, matchId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<MatchDetail>(`/api/groups/${groupId}/matches/${matchId}/start`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['groups', groupId, 'matches'] }),
  });
}
