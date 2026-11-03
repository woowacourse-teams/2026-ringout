import { apiResult } from './apiClient';

export type UserSort = 'id' | 'joinedAt' | 'roomCount';

export type UserSummary = {
  activeToday: boolean;
  id: number;
  nickname: string;
  imageUrl: string | null;
  provider: string;
  joinedAt: string | null;
  roomCount: number;
};
export type UserPage = {
  content: UserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
export type UserDetail = Omit<UserSummary, 'activeToday'> & {
  maskedEmail: string | null;
  lastLoginAt: string | null;
  role: string;
  termsAgreements: {
    allAgreed: boolean;
    agreements: {
      type: string;
      latestVersion: string;
      agreedVersion: string | null;
      agreedAt: string | null;
      needsReagreement: boolean;
    }[];
  };
};
export type JoinedRoom = {
  id: number;
  name: string;
  imageUrl: string | null;
  activityDays: string[];
  activityTime: string;
  memberCount: number;
  host: boolean;
};

export function getUsers(params: { search: string; page: number; size: number; sortBy?: UserSort; sortDirection?: 'asc' | 'desc'; activeToday?: boolean }, signal?: AbortSignal) {
  const query = new URLSearchParams({ search: params.search, page: String(params.page), size: String(params.size) });
  if (params.sortBy) query.set('sortBy', params.sortBy);
  if (params.sortDirection) query.set('sortDirection', params.sortDirection);
  if (params.activeToday !== undefined) query.set('activeToday', String(params.activeToday));
  return apiResult<UserPage>(`/users?${query}`, signal);
}

export function getUser(userId: number, signal?: AbortSignal) {
  return apiResult<UserDetail>(`/users/${userId}`, signal);
}

export function getUserRooms(userId: number, signal?: AbortSignal) {
  return apiResult<JoinedRoom[]>(`/users/${userId}/rooms`, signal);
}

export type UserStats = {
  date: string;
  timeZone: string;
  updatedAt: string;
  totalUsers: number;
  joinedToday: number;
  activeToday: number;
};

export function getUserStats(signal?: AbortSignal) {
  return apiResult<UserStats>('/users/stats', signal);
}
