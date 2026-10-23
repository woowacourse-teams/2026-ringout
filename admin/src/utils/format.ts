// Server LocalDateTime fields are shown without interpreting the browser's timezone.
export function displayDate(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—';
}

export function roomSchedule(room: { activityDays: string[]; activityTime: string }) {
  const days: Record<string, string> = {
    MONDAY: '월', TUESDAY: '화', WEDNESDAY: '수', THURSDAY: '목',
    FRIDAY: '금', SATURDAY: '토', SUNDAY: '일',
  };
  return `${room.activityDays.map(day => days[day] ?? day).join(' · ')} ${room.activityTime.slice(0, 5)}`;
}

export function getSeoulToday() {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}
