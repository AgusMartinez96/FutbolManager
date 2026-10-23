import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '../../lib/api';

export type Role = 'ADMIN' | 'MEMBER';
export type Position = 'GK' | 'DEF' | 'MID' | 'FWD';
export type Foot = 'RIGHT' | 'LEFT' | 'BOTH';

export type GroupSummary = { id: string; name: string; myRole: Role };
export type GroupDetail = {
  id: string;
  name: string;
  inviteCode: string;
  membersCanScore: boolean;
  myRole: Role;
  myUserId: string;
};
export type Member = { userId: string; username: string; displayName: string; role: Role };
export type Player = {
  id: string;
  name: string;
  nickname: string | null;
  preferredPosition: Position;
  dominantFoot: Foot;
  currentElo: number;
  userId: string | null;
};
export type PlayerInput = {
  name?: string;
  nickname?: string;
  preferredPosition?: Position;
  dominantFoot?: Foot;
};

export function useGroups() {
  return useQuery({
    queryKey: ['groups'],
    queryFn: () => apiFetch<GroupSummary[]>('/api/groups'),
  });
}

export function useCreateGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) =>
      apiFetch<GroupSummary>('/api/groups', { method: 'POST', body: { name } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['groups'] }),
  });
}

export function useJoinGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (inviteCode: string) =>
      apiFetch<{ groupId: string }>('/api/groups/join', { method: 'POST', body: { inviteCode } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['groups'] }),
  });
}

export function useGroup(groupId: string) {
  return useQuery({
    queryKey: ['groups', groupId],
    queryFn: () => apiFetch<GroupDetail>(`/api/groups/${groupId}`),
  });
}

function useGroupMutation<TVars, TResult>(
  groupId: string,
  request: (vars: TVars) => Promise<TResult>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['groups', groupId] }),
  });
}

export function useUpdateGroup(groupId: string) {
  return useGroupMutation(groupId, (body: { name?: string; membersCanScore?: boolean }) =>
    apiFetch<GroupDetail>(`/api/groups/${groupId}`, { method: 'PATCH', body }),
  );
}

export function useRegenerateCode(groupId: string) {
  return useGroupMutation(groupId, () =>
    apiFetch<{ inviteCode: string }>(`/api/groups/${groupId}/invite-code`, { method: 'POST' }),
  );
}

export function useMembers(groupId: string) {
  return useQuery({
    queryKey: ['groups', groupId, 'members'],
    queryFn: () => apiFetch<Member[]>(`/api/groups/${groupId}/members`),
  });
}

export function useChangeRole(groupId: string) {
  return useGroupMutation(groupId, (vars: { userId: string; role: Role }) =>
    apiFetch<Member>(`/api/groups/${groupId}/members/${vars.userId}`, {
      method: 'PATCH',
      body: { role: vars.role },
    }),
  );
}

export function useRemoveMember(groupId: string) {
  return useGroupMutation(groupId, (userId: string) =>
    apiFetch<void>(`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' }),
  );
}

export function usePlayers(groupId: string) {
  return useQuery({
    queryKey: ['groups', groupId, 'players'],
    queryFn: () => apiFetch<Player[]>(`/api/groups/${groupId}/players`),
  });
}

export function useCreatePlayer(groupId: string) {
  return useGroupMutation(groupId, (body: PlayerInput) =>
    apiFetch<Player>(`/api/groups/${groupId}/players`, { method: 'POST', body }),
  );
}

export function useUpdatePlayer(groupId: string) {
  return useGroupMutation(groupId, (vars: { playerId: string; body: PlayerInput }) =>
    apiFetch<Player>(`/api/groups/${groupId}/players/${vars.playerId}`, {
      method: 'PATCH',
      body: vars.body,
    }),
  );
}

export function useDeletePlayer(groupId: string) {
  return useGroupMutation(groupId, (playerId: string) =>
    apiFetch<void>(`/api/groups/${groupId}/players/${playerId}`, { method: 'DELETE' }),
  );
}
