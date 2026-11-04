import { apiResult } from './apiClient';

export const alarmStatuses = {
  ALARM_TRIGGERED: { label: '알람 울림', className: 'unconfirmed' },
  MOVEMENT_STARTED: { label: '이동 시작', className: 'moving' },
  MOVING: { label: '이동 중', className: 'moving' },
  ARRIVED: { label: '도착 완료', className: 'arrived' },
  FORCE_ENDED: { label: '강제 종료', className: 'gave-up' },
} as const;
export type AlarmStatus = keyof typeof alarmStatuses;
export type AlarmHistory = {
  id: number; occurrenceUuid: string; userId: number; nickname: string;
  clientAlarmId: string; scheduledAt: string | null; alarmTime: string | null;
  startedAt: string; movementStartedAt: string | null; endedAt: string | null;
  status: AlarmStatus; endType: string | null; ringingCount: number;
};
export type AlarmHistoryPage = {
  content: AlarmHistory[]; page: number; size: number; totalElements: number;
  totalPages: number; date: string; timeZone: string;
};
export function getAlarmHistories(params: {
  date: string; userId?: number; status: AlarmStatus | ''; sortDirection: 'asc' | 'desc'; page: number;
}, signal?: AbortSignal) {
  const query = new URLSearchParams({ date: params.date, sortDirection: params.sortDirection, page: String(params.page), size: '20' });
  if (params.userId !== undefined) query.set('userId', String(params.userId));
  if (params.status) query.set('status', params.status);
  return apiResult<AlarmHistoryPage>(`/alarm-histories?${query}`, signal);
}
