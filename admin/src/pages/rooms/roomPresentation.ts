export const recordStatuses = {
  ARRIVED: { label: '도착 완료', className: 'arrived' },
  FORCE_ENDED: { label: '강제 종료', className: 'gave-up' },
  UNCONFIRMED: { label: '종료 미확인', className: 'unconfirmed' },
  NO_RECORD: { label: '기록 없음', className: 'empty' },
} as const;

export function recordTime(value: string | null, date: string) {
  if (!value) return '—';
  return value.slice(0, 10) === date ? value.slice(11, 19) : value.replace('T', ' ').slice(0, 19);
}

export function recordDuration(seconds: number | null) {
  if (seconds === null) return '—';
  const minutes = Math.floor(seconds / 60);
  return minutes ? `${minutes}분 ${seconds % 60}초` : `${seconds}초`;
}
