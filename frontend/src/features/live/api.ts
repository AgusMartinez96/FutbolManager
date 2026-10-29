import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, apiFetch } from '../../lib/api';
import type { MatchStatus, TeamKey } from '../matches/api';

export type EventType = 'GOAL' | 'SAVE' | 'TACKLE' | 'PLAY';

export type LiveEvent = {
  id: string;
  type: EventType;
  team: TeamKey;
  playerId: string | null;
  playerName: string | null;
  assistPlayerId: string | null;
  assistPlayerName: string | null;
  recordedBy: { userId: string; displayName: string };
  createdAt: string;
  voidedAt: string | null;
};

export type LiveState = {
  matchId: string;
  status: MatchStatus;
  scoreA: number;
  scoreB: number;
  canRecord: boolean;
  isAdmin: boolean;
  myUserId: string;
  events: LiveEvent[];
};

export type RecordEventInput = {
  id: string;
  type: EventType;
  team: TeamKey;
  playerId?: string;
  assistPlayerId?: string;
};

const POLL_MS = 5000;
const MAX_RETRIES = 3;

export function liveKey(groupId: string, matchId: string) {
  return ['groups', groupId, 'matches', matchId, 'live'] as const;
}

export function useLive(groupId: string, matchId: string) {
  return useQuery({
    queryKey: liveKey(groupId, matchId),
    queryFn: () => apiFetch<LiveState>(`/api/groups/${groupId}/matches/${matchId}/live`),
    refetchInterval: POLL_MS,
    refetchIntervalInBackground: false,
  });
}

/**
 * The caller generates the event id once per tap and passes it in the variables, so every retry
 * of a network failure re-sends the same id and the server stays idempotent.
 */
export function useRecordEvent(groupId: string, matchId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: RecordEventInput) =>
      apiFetch<LiveEvent>(`/api/groups/${groupId}/matches/${matchId}/events`, {
        method: 'POST',
        body,
      }),
    retry: (failureCount, error) => !(error instanceof ApiError) && failureCount < MAX_RETRIES,
    retryDelay: (attempt) => 250 * (attempt + 1),
    onSettled: () => queryClient.invalidateQueries({ queryKey: liveKey(groupId, matchId) }),
  });
}

export type EloChange = {
  playerId: string;
  name: string;
  eloBefore: number;
  eloAfter: number;
  delta: number;
};

export type CloseResult = {
  scoreA: number;
  scoreB: number;
  mvpPlayerId: string | null;
  eloChanges: EloChange[];
};

/** Closing, changing the MVP and annulling all rewrite the ledger, so they refresh the whole group. */
function useLifecycle<TVars, TResult>(groupId: string, request: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['groups', groupId] }),
  });
}

export function useCloseMatch(groupId: string, matchId: string) {
  return useLifecycle(groupId, (mvpPlayerId: string | undefined) =>
    apiFetch<CloseResult>(`/api/groups/${groupId}/matches/${matchId}/close`, {
      method: 'POST',
      body: mvpPlayerId ? { mvpPlayerId } : {},
    }),
  );
}

export function useChangeMvp(groupId: string, matchId: string) {
  return useLifecycle(groupId, (mvpPlayerId: string) =>
    apiFetch<CloseResult>(`/api/groups/${groupId}/matches/${matchId}/mvp`, {
      method: 'PUT',
      body: { mvpPlayerId },
    }),
  );
}

export function useAnnulMatch(groupId: string, matchId: string) {
  return useLifecycle(groupId, () =>
    apiFetch<{ matchId: string; status: MatchStatus }>(
      `/api/groups/${groupId}/matches/${matchId}/annul`,
      { method: 'POST' },
    ),
  );
}

export function useVoidEvent(groupId: string, matchId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (eventId: string) =>
      apiFetch<LiveEvent>(`/api/groups/${groupId}/matches/${matchId}/events/${eventId}/void`, {
        method: 'POST',
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: liveKey(groupId, matchId) }),
  });
}
