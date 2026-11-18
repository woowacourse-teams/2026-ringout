import { apiResult } from './apiClient';

export type RoomSummary = {
  id: number;
  name: string;
  imageUrl: string | null;
  host: { id: number; nickname: string };
  memberCount: number;
  activityDays: string[];
  activityTime: string;
};

export type RoomPage = {
  content: RoomSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type RoomDetail = RoomSummary & {
  description: string | null;
  createdAt: string | null;
  latestActivityAt: string | null;
};

export type RecordStatus = 'ARRIVED' | 'FORCE_ENDED' | 'UNCONFIRMED' | 'NO_RECORD';

export type MemberRecord = {
  userId: number;
  nickname: string;
  imageUrl: string | null;
  host: boolean;
  joinedAt: string | null;
  occurrenceId: number | null;
  occurrenceCount: number;
  alarmAt: string | null;
  movementStartedAt: string | null;
  endedAt: string | null;
  durationSeconds: number | null;
  status: RecordStatus;
};

export type RoomRecords = {
  date: string;
  timeZone: string;
  totalMembers: number;
  recordedMembers: number;
  arrivedMembers: number;
  unconfirmedMembers: number;
  totalElements: number;
  content: MemberRecord[];
};

export function getRooms(params: { search: string; page: number; size: number }, signal?: AbortSignal) {
  const query = new URLSearchParams({ search: params.search, page: String(params.page), size: String(params.size) });
  return apiResult<RoomPage>(`/rooms?${query}`, signal);
}

export function getRoom(roomId: number, signal?: AbortSignal) {
  return apiResult<RoomDetail>(`/rooms/${roomId}`, signal);
}

export function getRoomRecords(roomId: number, params: { date: string; search: string; status: RecordStatus | '' }, signal?: AbortSignal) {
  const query = new URLSearchParams({ date: params.date, search: params.search });
  if (params.status) query.set('status', params.status);
  return apiResult<RoomRecords>(`/rooms/${roomId}/records?${query}`, signal);
}

export type RoomStats = {
  date: string;
  timeZone: string;
  updatedAt: string;
  totalRooms: number;
  createdToday: number;
  activeToday: number;
};

export function getRoomStats(signal?: AbortSignal) {
  return apiResult<RoomStats>('/rooms/stats', signal);
}

export type TimelineEventType = 'ALARM_TRIGGERED' | 'ALARM_RINGING' | 'ALARM_DISMISSED' | 'MOVEMENT_STARTED' | 'ARRIVED' | 'GAVE_UP';
export type OccurrenceTimeline = {
  occurrenceId: number;
  userId: number;
  nickname: string;
  imageUrl: string | null;
  scheduledAt: string;
  ringings: { id: number; type: 'INITIAL' | 'REPEAT'; ringingAt: string; dismissedAt: string | null }[];
  endType: 'ARRIVED' | 'FORCE_ENDED' | null;
  events: { key: string; event: TimelineEventType; occurredAt: string }[];
};
export type TimelinePage = {
  date: string;
  timeZone: string;
  content: OccurrenceTimeline[];
  page: number;
  size: number;
  hasNext: boolean;
};

export function getRoomTimeline(roomId: number, params: { date: string; userId?: number; page: number }, signal?: AbortSignal) {
  const query = new URLSearchParams({ date: params.date, page: String(params.page), size: '50' });
  if (params.userId !== undefined) query.set('userId', String(params.userId));
  return apiResult<TimelinePage>(`/rooms/${roomId}/timeline?${query}`, signal);
}
