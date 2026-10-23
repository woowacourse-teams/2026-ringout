import { useCallback, useMemo, useState } from 'react';
import { AlarmClock, Check, ChevronDown, Footprints, UserRound, X, Clock3 } from 'lucide-react';
import { getRoomTimeline, type OccurrenceTimeline, type TimelineEventType } from '../../api/adminRooms';
import useApiData from '../../hooks/useApiData';
import LoadingIndicator from '../../components/LoadingIndicator';

const movementEvents = new Set<TimelineEventType>(['MOVEMENT_STARTED', 'ARRIVED', 'GAVE_UP']);
const eventOrder: Partial<Record<TimelineEventType, number>> = { MOVEMENT_STARTED: 0, ARRIVED: 1, GAVE_UP: 2 };

function clockTime(value: string, date: string, period = false) {
  const hour = Number(value.slice(11, 13));
  const time = period
    ? `${hour < 12 ? '오전' : '오후'} ${String(hour % 12 || 12).padStart(2, '0')}:${value.slice(14, 16)}`
    : value.slice(11, 16);
  return value.slice(0, 10) === date ? time : `${value.slice(5, 10).replace('-', '/')} ${time}`;
}

function MemberAvatar({ imageUrl, nickname }: { imageUrl: string | null; nickname: string }) {
  const [failed, setFailed] = useState(false);
  return <span className="room-timeline-avatar">
    {imageUrl && !failed
      ? <img src={imageUrl} alt={`${nickname} 프로필`} onError={() => setFailed(true)} />
      : <UserRound size={15} aria-hidden="true" />}
  </span>;
}

function PersonalAlarm({ occurrence, date }: { occurrence: OccurrenceTimeline; date: string }) {
  const ringings = occurrence.ringings ?? [];
  const initial = ringings.find(ringing => ringing.type === 'INITIAL')?.ringingAt
    ?? occurrence.events.find(event => event.event === 'ALARM_TRIGGERED')?.occurredAt ?? occurrence.scheduledAt;
  const ended = occurrence.events.find(event => event.event === 'ARRIVED' || event.event === 'GAVE_UP');
  return (
    <details className="room-personal-alarm" open>
      <summary>
        <span>{clockTime(initial, date, true)}에 울린 알람 <small>#{occurrence.occurrenceId}</small></span>
        <ChevronDown size={17} aria-hidden="true" />
      </summary>
      <ol className="room-activity-list personal">
        {ringings.map(ringing => <li key={ringing.id}>
          <span className="room-activity-icon alarm" aria-hidden="true"><AlarmClock size={20} /></span>
          <div className="room-personal-period">
            <strong>{clockTime(ringing.ringingAt, date, true)} ~ {ringing.dismissedAt ? clockTime(ringing.dismissedAt, date, true) : '—'}</strong>
            {!ringing.dismissedAt && <small>해제 기록 없음</small>}
          </div>
        </li>)}
        {!ringings.length && <li>
          <span className="room-activity-icon alarm" aria-hidden="true"><AlarmClock size={20} /></span>
          <div className="room-personal-period"><strong>{clockTime(initial, date, true)}</strong><small>울림 상세 기록 없음</small></div>
        </li>}
        {ended ? <li className={ended.event === 'ARRIVED' ? 'arrived' : 'gave-up'}>
          <span className={`room-activity-icon ${ended.event === 'ARRIVED' ? 'arrived' : 'gave-up'}`} aria-hidden="true">
            {ended.event === 'ARRIVED' ? <Check size={22} /> : <X size={22} />}
          </span>
          <div className="room-personal-period">
            <strong>{clockTime(ended.occurredAt, date, true)}</strong>
            <span>{ended.event === 'ARRIVED' ? '목적지 도착 완료!' : '알람 강제 종료'}</span>
          </div>
        </li> : <li>
          <span className="room-activity-icon unconfirmed" aria-hidden="true"><Clock3 size={20} /></span>
          <span className="room-personal-unconfirmed">종료 미확인</span>
        </li>}
      </ol>
    </details>
  );
}

export default function RoomTimeline({ roomId, date, userId }: {
  roomId: number;
  date: string;
  userId?: number;
}) {
  const [page, setPage] = useState(0);
  const load = useCallback((signal: AbortSignal) => getRoomTimeline(roomId, { date, userId, page }, signal), [roomId, date, userId, page]);
  const result = useApiData(load);
  const data = result.data;
  const isGroup = userId === undefined;
  const events = useMemo(() => (data?.content ?? []).flatMap(occurrence => occurrence.events
    .filter(event => movementEvents.has(event.event))
    .map(event => ({ ...event, occurrenceId: occurrence.occurrenceId, userId: occurrence.userId,
      nickname: occurrence.nickname, imageUrl: occurrence.imageUrl })))
    .sort((a, b) => a.occurredAt.localeCompare(b.occurredAt) || a.occurrenceId - b.occurrenceId
      || (eventOrder[a.event] ?? 0) - (eventOrder[b.event] ?? 0)), [data]);
  const arrived = new Set(events.filter(event => event.event === 'ARRIVED').map(event => event.userId)).size;
  const partial = page > 0 || Boolean(data?.hasNext);
  const unconfirmed = data?.content.filter(occurrence => occurrence.endType === null).length ?? 0;

  return (
    <section className="room-event-timeline" aria-label={isGroup ? '전체 회원 활동 타임라인' : '회원 알람 기록'} aria-busy={result.loading}>
      <div className="room-activity-heading">
        <h4>{isGroup ? '모임 활동 기록' : '알람 기록'}</h4>
        {isGroup && data && <span className="room-arrival-count">{partial ? '현재 페이지 도착 회원' : '목표 달성 사용자'} <strong>{arrived}명</strong></span>}
      </div>
      <p className="room-record-note">{date}에 시작한 실행 · 한국 시간</p>
      <div className="room-timeline-content">
        {result.loading ? <LoadingIndicator /> : result.error ? (
          <div role="alert"><p>{result.error}</p><button className="secondary-button" onClick={result.retry}>다시 시도</button></div>
        ) : isGroup ? (
          !events.length ? <p className="room-record-empty">이동·도착·강제 종료 기록이 없어요.</p> : (
            <ol className="room-activity-list group">
              {events.map(event => {
                const kind = event.event === 'ARRIVED' ? 'arrived' : event.event === 'GAVE_UP' ? 'gave-up' : 'moving';
                return <li className={kind} key={`${event.occurrenceId}-${event.key}`}>
                  <span className={`room-activity-icon ${kind}`} aria-hidden="true">
                    {kind === 'arrived' ? <Check size={22} /> : kind === 'gave-up' ? <X size={22} /> : <Footprints size={22} />}
                  </span>
                  <time dateTime={`${event.occurredAt}+09:00`}>{clockTime(event.occurredAt, date)}</time>
                  <div className="room-group-event-copy">
                    <MemberAvatar imageUrl={event.imageUrl} nickname={event.nickname} />
                    <span><strong>{event.nickname}</strong>{kind === 'arrived' ? '님이 목적지에 도착' : kind === 'gave-up' ? '님, 알람 강제 종료' : '님, 이동 시작'}</span>
                  </div>
                </li>;
              })}
            </ol>
          )
        ) : !data?.content.length ? <p className="room-record-empty">해당 날짜의 알람 기록이 없어요.</p> : (
          <div className="room-personal-alarms">{data.content.map(occurrence => <PersonalAlarm key={occurrence.occurrenceId} occurrence={occurrence} date={date} />)}</div>
        )}
      </div>
      {data && <>
        {unconfirmed > 0 && <p className="room-record-note">이 페이지의 실행 {unconfirmed}건은 종료 기록이 없습니다. 진행 중이나 실패로 단정하지 않습니다.</p>}
        {(partial || data.content.length > 0) && <div className="pagination room-timeline-pagination">
          <span>실행 기준 {page + 1}페이지 · {data.content.length}건 · 페이지 내 시간순</span>
          <div>
            <button className="text-button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>이전</button>
            <button className="text-button" disabled={!data.hasNext} onClick={() => setPage(value => value + 1)}>다음</button>
          </div>
        </div>}
        {data.hasNext && <p className="room-record-note">다음 페이지에도 기록이 있습니다. 현재 페이지는 전체 기록의 일부입니다.</p>}
      </>}
    </section>
  );
}
