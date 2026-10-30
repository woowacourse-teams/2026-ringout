/** Preserve legacy log content without inventing event times from deployment boundaries. */
export function formatDeploymentEvents(events: string) {
  if (!events.trim()) return '기록된 이벤트가 없습니다.';
  const formatter = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  });
  return events.split('\n').map(line => {
    if (!line.trim()) return line;
    const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2}))\s+(.*)$/.exec(line);
    if (!match || Number.isNaN(Date.parse(match[1]))) return `[시각 미수집] ${line}`;
    return `[${formatter.format(new Date(match[1]))} KST] ${match[2]}`;
  }).join('\n');
}
