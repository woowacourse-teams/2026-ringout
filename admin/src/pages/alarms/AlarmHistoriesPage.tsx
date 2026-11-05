import { useCallback, useState } from 'react';
import { ChevronLeft, ChevronRight, RefreshCw, RotateCcw, X } from 'lucide-react';
import { getAlarmHistories, alarmStatuses, type AlarmStatus } from '../../api/adminAlarmHistories';
import useApiData from '../../hooks/useApiData';
import LoadingIndicator from '../../components/LoadingIndicator';
import SortHeader from '../../components/SortHeader';
import { getSeoulToday } from '../../utils/format';
import { recordTime } from '../rooms/roomPresentation';
import AlarmCalendar from './AlarmCalendar';
import AlarmMemberPicker from './AlarmMemberPicker';
import './alarms.css';

export default function AlarmHistoriesPage() {
  const [date, setDate] = useState(getSeoulToday);
  const [member, setMember] = useState<{ id: number; nickname: string } | null>(null);
  const [status, setStatus] = useState<AlarmStatus | ''>('');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const [page, setPage] = useState(0);
  const [memberSearchVersion, setMemberSearchVersion] = useState(0);
  const request = useCallback((signal: AbortSignal) => getAlarmHistories({ date, userId: member?.id, status, sortDirection, page }, signal), [date, member?.id, status, sortDirection, page]);
  const result = useApiData(request);
  const data = result.data;
  return <>
    <div className="page-heading"><div><div className="eyebrow">ALARM HISTORY</div><h1>개인 알람 기록<span className="heading-dot">.</span></h1><p>날짜와 회원을 선택해 개인 알람의 실행 기록을 확인하세요.</p></div></div>
    <div className="alarm-filters">
      <AlarmCalendar date={date} onChange={value => { setDate(value); setPage(0); }} />
      <AlarmMemberPicker date={date} key={memberSearchVersion} selected={member} onSelect={value => { setMember(value); setPage(0); }} />
    </div>
    <section className="panel records-panel alarm-history-panel" aria-labelledby="alarm-history-title" aria-busy={result.loading}>
      <div className="alarm-history-heading"><div><h2 id="alarm-history-title">알람 실행 기록</h2><p>{date} · {member ? `${member.nickname} (#${member.id})` : '전체 회원'} · 한국 시간 기준</p></div><button className="secondary-button" onClick={result.retry} disabled={result.loading}><RefreshCw size={14} />새로고침</button></div>
      <div className="records-toolbar">
        <label className="management-control-field"><span>정렬</span><select aria-label="알람 기록 정렬 기준" value="time" onChange={() => {}}><option value="time">시간순</option></select></label>
        <label className="management-control-field"><span>상태</span><select aria-label="알람 기록 상태 필터" value={status} onChange={event => { setStatus(event.target.value as AlarmStatus | ''); setPage(0); }}><option value="">전체 상태</option>{Object.entries(alarmStatuses).map(([value, item]) => <option key={value} value={value}>{item.label} ({value})</option>)}</select></label>
        <button className="text-button" onClick={() => { setMember(null); setStatus(''); setSortDirection('asc'); setDate(getSeoulToday()); setPage(0); setMemberSearchVersion(value => value + 1); }}>필터 초기화</button>
      </div>
      {(status || member) && <div className="active-filters" role="group" aria-label="적용된 필터">
        <button className="clear-filters" onClick={() => { setStatus(''); setMember(null); setPage(0); }}><RotateCcw size={15} aria-hidden="true" />전체 해제</button>
        <span className="active-filters-divider" aria-hidden="true" />
        {member && <button className="active-filter-chip" onClick={() => { setMember(null); setPage(0); }} aria-label={`${member.nickname} 회원 필터 해제`}>{member.nickname} · #{member.id}<X size={15} aria-hidden="true" /></button>}
        {status && <button className="active-filter-chip" onClick={() => { setStatus(''); setPage(0); }} aria-label={`${alarmStatuses[status].label} 상태 필터 해제`}>{alarmStatuses[status].label}<X size={15} aria-hidden="true" /></button>}
      </div>}
      <div className="alarm-history-results">
        {result.loading ? <LoadingIndicator /> : result.error ? <div className="empty-state" role="alert"><p>{result.error}</p><button className="secondary-button" onClick={result.retry}>다시 시도</button></div> : data && <>
          <div className="list-count">조회 결과 <strong>{data.totalElements}</strong>건</div>
          <div className="table-scroll"><table className="data-table alarm-history-table"><thead><tr><SortHeader label="실행 시각" active direction={sortDirection} onSort={() => { setSortDirection(value => value === 'asc' ? 'desc' : 'asc'); setPage(0); }} /><th scope="col">회원</th><th scope="col">설정 시각</th><th scope="col">이동 시작</th><th scope="col">종료 시각</th><th scope="col">울림 횟수</th><th scope="col">상태</th></tr></thead><tbody>{data.content.map(record => <tr key={record.id}>
            <td>{recordTime(record.startedAt, data.date)}</td><td><button className="alarm-record-member" onClick={() => { setMember(current => current?.id === record.userId ? null : { id: record.userId, nickname: record.nickname }); setPage(0); }} title="이 회원의 기록만 조회"><strong>{record.nickname}</strong><small>#{record.userId}</small></button></td>
            <td>{record.scheduledAt ? recordTime(record.scheduledAt, data.date) : record.alarmTime?.slice(0, 8) ?? '—'}</td><td>{recordTime(record.movementStartedAt, data.date)}</td><td>{recordTime(record.endedAt, data.date)}</td><td>{record.ringingCount}회</td><td><span className={`badge room-record-status ${alarmStatuses[record.status].className}`} title={record.status}>{alarmStatuses[record.status].label}</span></td>
          </tr>)}</tbody></table></div>
          {!data.content.length && <div className="empty-state"><p>선택한 날짜와 조건에 해당하는 알람 기록이 없어요.</p></div>}
          <div className="pagination"><span>{data.totalElements ? data.page * data.size + 1 : 0}–{data.page * data.size + data.content.length} / {data.totalElements}건</span><div><button aria-label="기록 이전 페이지" disabled={page === 0} onClick={() => setPage(value => value - 1)}><ChevronLeft size={16} /></button><span>{data.totalPages ? page + 1 : 0} / {data.totalPages}</span><button aria-label="기록 다음 페이지" disabled={page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}><ChevronRight size={16} /></button></div></div>
        </>}
      </div>
      <p className="alarm-history-note">실행 시작 시각 기준 기록입니다. 서버에 저장된 개인 알람 실행만 표시하며, 이동 중 상태는 이동 시작 후 2분 경과를 기준으로 표시합니다.</p>
    </section>
  </>;
}
