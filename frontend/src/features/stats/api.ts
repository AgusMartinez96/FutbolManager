import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../../lib/api';

export type RankingEntry = {
  playerId: string;
  name: string;
  nickname: string | null;
  currentElo: number;
  played: number;
  provisional: boolean;
};

export type Streak = { type: 'W' | 'D' | 'L'; length: number };

export type PlayerStats = {
  playerId: string;
  name: string;
  played: number;
  won: number;
  drawn: number;
  lost: number;
  goals: number;
  assists: number;
  mvps: number;
  streak: Streak | null;
  currentElo: number;
  provisional: boolean;
};

/** The first point is the INITIAL one and carries no match. */
export type EloPoint = { matchId: string | null; playedAt: string | null; eloAfter: number };

export function useRanking(groupId: string) {
  return useQuery({
    queryKey: ['groups', groupId, 'ranking'],
    queryFn: () => apiFetch<RankingEntry[]>(`/api/groups/${groupId}/ranking`),
  });
}

export function usePlayerStats(groupId: string, playerId: string) {
  return useQuery({
    queryKey: ['groups', groupId, 'players', playerId, 'stats'],
    queryFn: () => apiFetch<PlayerStats>(`/api/groups/${groupId}/players/${playerId}/stats`),
  });
}

export function useEloHistory(groupId: string, playerId: string) {
  return useQuery({
    queryKey: ['groups', groupId, 'players', playerId, 'elo-history'],
    queryFn: () => apiFetch<EloPoint[]>(`/api/groups/${groupId}/players/${playerId}/elo-history`),
  });
}
