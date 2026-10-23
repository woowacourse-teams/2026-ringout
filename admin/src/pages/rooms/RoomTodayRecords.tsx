import { Fragment, useCallback, useEffect, useState } from 'react';
import { Check, Clock, Search, Users, ChevronDown } from 'lucide-react';
import { getRoomRecords, type RecordStatus, type RoomRecords } from '../../api/adminRooms';
import { displayDate, getSeoulToday } from '../../utils/format';
import { recordDuration, recordStatuses, recordTime } from './roomPresentation';
import LoadingIndicator from '../../components/LoadingIndicator';
import useApiData from '../../hooks/useApiData';
import RoomTimeline from './RoomTimeline';

export default function RoomTodayRecords({ roomId }: { roomId: number }) {
  const [date, setDate] = useState(getSeoulToday);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<RecordStatus | ''>('');
  const [expandedUserId, setExpandedUserId] = useState<number | null>(null);
  const toggleMember = (id: number) => setExpandedUserId(current => current === id ? null : id);

  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const loadRecords = useCallback((signal: AbortSignal) => getRoomRecords(
    roomId, { date, search: query, status }, signal,
  ), [roomId, date, query, status]);
  const result = useApiData<RoomRecords>(date ? loadRecords : null);
  const data = result.data;
  const pending = result.loading || search.trim() !== query;

  return (
    <section className="room-today-records" aria-labelledby="room-today-heading" aria-busy={pending}>
      <div className="room-section-heading">
        <div>
          <h3 id="room-today-heading">멤버 일별 기록</h3>
          <p>{data?.date ?? date} · 한국 시간 기준</p>
        </div>
        {data && <span className="room-record-total">전체 멤버 {data.totalMembers}명</span>}
      </div>
      <div className="room-record-toolbar">
        <input
          type="date"
          aria-label="기록 조회 날짜"
          value={date}
          onChange={event => { setDate(event.target.value); setExpandedUserId(null); }}
        />
        <div className="search-input">
          <Search size={16} />
          <input
            aria-label="모임 멤버 이름 검색"
            placeholder="멤버 이름 검색"
            maxLength={100}
            value={search}
            onChange={event => setSearch(event.target.value)}
          />
        </div>
        <select aria-label="멤버 기록 상태" value={status} onChange={event => setStatus(event.target.value as RecordStatus | '')}>
          <option value="">전체</option>
          {Object.entries(recordStatuses).map(([value, item]) => <option key={value} value={value}>{item.label}</option>)}
        </select>
      </div>
      <div className="room-record-results">
      {!date ? <p role="status">조회할 날짜를 선택해 주세요.</p> : pending ? (
        <LoadingIndicator />
      ) : result.error ? (
        <div role="alert">
          <p>{result.error}</p>
          <button className="secondary-button" onClick={result.retry}>다시 시도</button>
        </div>
      ) : data && <>
        <div className="room-record-summary">
          <div><span><Users size={14} />기록 있는 멤버</span><strong>{data.recordedMembers}<small> / {data.totalMembers}명</small></strong></div>
          <div><span><Check size={14} />도착 완료</span><strong>{data.arrivedMembers}<small>명</small></strong></div>
          <div><span><Clock size={14} />종료 미확인</span><strong>{data.unconfirmedMembers}<small>명</small></strong></div>
        </div>
        {!data.recordedMembers && <p className="room-no-records">해당 날짜의 서버 기록이 없어요. 멤버별로 기록 없음 상태를 표시합니다.</p>}
        <p className="room-record-total">검색 결과 {data.totalElements}명 · 요약은 전체 멤버 기준</p>
        <div className="room-record-table-scroll">
          <table className="room-record-table">
            <thead><tr><th scope="col">멤버</th><th scope="col">알람</th><th scope="col">이동 시작</th><th scope="col">종료</th><th scope="col">이동 시간</th><th scope="col">결과</th></tr></thead>
            <tbody>
              {data.content.map(record => <Fragment key={record.userId}>
              <tr className="detail-row" tabIndex={0} aria-expanded={expandedUserId === record.userId}
                aria-controls={`room-member-${record.userId}`} aria-label={`${record.nickname} 참여 정보 펼치기`}
                onClick={event => {
                  if (event.target instanceof Element && event.target.closest('button')) return;
                  toggleMember(record.userId);
                }}
                onKeyDown={event => {
                  if (event.target !== event.currentTarget) return;
                  if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); toggleMember(record.userId); }
                }}>

                <td>
                  <button className="room-record-member room-member-toggle"
                    aria-expanded={expandedUserId === record.userId} aria-controls={`room-member-${record.userId}`}
                    onClick={() => toggleMember(record.userId)}>
                    <span className="avatar small peach">{record.nickname.slice(0, 1)}</span>
                    <strong>{record.nickname}</strong>
                    {record.host && <span className="room-host-label">방장</span>}
                    <ChevronDown size={14} className={expandedUserId === record.userId ? 'expanded' : ''} />
                  </button>
                  {record.occurrenceCount > 1 && <small>당일 {record.occurrenceCount}건 중 최신</small>}
                </td>
                <td>{recordTime(record.alarmAt, data.date)}</td>
                <td>{recordTime(record.movementStartedAt, data.date)}</td>
                <td>{recordTime(record.endedAt, data.date)}</td>
                <td>{recordDuration(record.durationSeconds)}</td>
                <td><span className={`badge room-record-status ${recordStatuses[record.status].className}`}>{recordStatuses[record.status].label}</span></td>
              </tr>
              {expandedUserId === record.userId && <tr className="room-member-expanded" id={`room-member-${record.userId}`}>
                <td colSpan={6}>
                  <dl className="room-member-facts">
                    <div><dt>회원 ID</dt><dd>#{record.userId}</dd></div>
                    <div><dt>모임 참여일</dt><dd>{displayDate(record.joinedAt)}</dd></div>
                  </dl>
                  <RoomTimeline key={`${date}-${record.userId}`} roomId={roomId} date={date} userId={record.userId} />
                </td>
              </tr>}
              </Fragment>)}
            </tbody>
          </table>
          {!data.content.length && <div className="room-record-empty">조건에 맞는 멤버가 없어요.</div>}
        </div>
      </>}
      </div>
      {date && <RoomTimeline key={`${roomId}-${date}`} roomId={roomId} date={date} />}
      <p className="room-record-note">현재 멤버의 서버 저장 실행 기록입니다. 여러 건이면 최신 실행을 표시합니다. 종료 미확인은 실패나 이동 중을 의미하지 않으며, 기록 없음은 미전송·미사용 등 원인을 구분하지 않습니다.</p>
    </section>
  );
}
