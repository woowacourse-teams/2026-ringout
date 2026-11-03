import { useCallback, useEffect, useState } from 'react';
import { ArrowUpRight, Search, ChevronLeft, ChevronRight, SlidersHorizontal, ArrowDownUp, Users } from 'lucide-react';
import { getRooms, getRoomStats, type RoomPage, type RoomSort } from '../../api/adminRooms';
import { roomSchedule } from '../../utils/format';
import useApiData from '../../hooks/useApiData';
import RoomDetailDialog from './RoomDetailDialog';
import ManagementStats from '../../components/ManagementStats';
import SortHeader from '../../components/SortHeader';
import ActiveActivityFilter from '../../components/ActiveActivityFilter';

export default function RoomsPage() {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [sortBy, setSortBy] = useState<RoomSort>('id');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc');
  const [activity, setActivity] = useState('');
  const changeSort = (field: RoomSort) => {
    setSortDirection(sortBy === field && sortDirection === 'desc' ? 'asc' : 'desc');
    setSortBy(field);
    setPage(0);
  };
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const size = 20;

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setQuery(search.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const loadRooms = useCallback((signal: AbortSignal) => getRooms({ search: query, page, size, sortBy, sortDirection, activeToday: activity === '' ? undefined : activity === 'true' }, signal), [query, page, sortBy, sortDirection, activity]);
  const result = useApiData<RoomPage>(loadRooms);
  const loadStats = useCallback((signal: AbortSignal) => getRoomStats(signal), []);
  const stats = useApiData(loadStats);
  const metrics = [
    { label: '총 모임', emoji: '🏡', value: stats.data?.totalRooms },
    { label: '오늘 추가된 모임', emoji: '✨', value: stats.data?.createdToday },
    { label: '오늘 활동한 모임', emoji: '🔥', value: stats.data?.activeToday },
  ];
  const data = result.data;
  const pending = result.loading || search.trim() !== query;

  return <>
    <div className="page-heading">
      <div>
        <div className="eyebrow">MANAGEMENT</div>
        <h1>모임 관리<span className="heading-dot">.</span></h1>
        <p>함께 습관을 만드는 모임을 살펴보세요.</p>
      </div>
    </div>
    <ManagementStats
      label="모임 통계"
      metrics={metrics}
      unit="개"
      loading={stats.loading}
      error={stats.error}
      onRetry={stats.retry}
    />
    <section className="panel records-panel" aria-busy={pending}>
      <div className="records-toolbar">
        <div className="search-input">
          <Search size={18} />
          <input
            aria-label="모임 검색"
            placeholder="모임 이름, ID 또는 방장 검색"
            maxLength={100}
            value={search}
            onChange={event => setSearch(event.target.value)}
          />
        </div>
        <label className="management-control-field">
          <span><ArrowDownUp size={14} aria-hidden="true" />정렬</span>
          <select aria-label="정렬 기준" value={sortBy} onChange={event => {
            setSortBy(event.target.value as RoomSort);
            setSortDirection('desc');
            setPage(0);
          }}>
            <option value="id">기본순</option>
            <option value="memberCount">멤버 수 순</option>
          </select>
        </label>
        <label className="management-control-field">
          <span><SlidersHorizontal size={14} aria-hidden="true" />필터</span>
          <select aria-label="활동 필터" value={activity} onChange={event => { setActivity(event.target.value); setPage(0); }}>
            <option value="">전체 활동</option>
            <option value="true">🔥 오늘 활동함</option>
            <option value="false">오늘 활동 없음</option>
          </select>
        </label>
        <button className="text-button" onClick={() => {
          setSearch('');
          setQuery('');
          setPage(0);
          setActivity('');
          setSortBy('id');
          setSortDirection('desc');
        }}>초기화</button>
      </div>
      <ActiveActivityFilter activity={activity} onClear={() => { setActivity(''); setPage(0); }} />
      {pending ? <p className="empty-state" role="status">모임을 불러오는 중이에요.</p> : result.error ? (
        <div className="empty-state" role="alert">
          <p>{result.error}</p>
          <button className="secondary-button" onClick={result.retry}>다시 시도</button>
        </div>
      ) : data && <>
        <div className="list-count">전체 <strong>{data.totalElements}</strong>개의 결과</div>
        <div className="table-scroll">
          <table className="data-table">
            <thead><tr><th>모임</th><th>모임 ID</th><th>방장</th><SortHeader label="멤버" active={sortBy === 'memberCount'} direction={sortDirection} onSort={() => changeSort('memberCount')} /><th>일정</th><th>상세</th></tr></thead>
            <tbody>
              {data.content.map(room => <tr
                key={room.id}
                className="detail-row"
                tabIndex={0}
                aria-label={`${room.name} 상세 보기`}
                aria-haspopup="dialog"
                onClick={event => {
                  // Keep nested buttons' own actions, including the joined-room shortcut.
                  if (event.target instanceof Element && event.target.closest('button, a, input, select, textarea')) return;
                  setSelectedId(room.id);
                }}
                onKeyDown={event => {
                  if (event.target !== event.currentTarget) return;
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    setSelectedId(room.id);
                  }
                }}
              >
                <td>
                  <button className="person-cell" onClick={() => setSelectedId(room.id)}>
                    <span className="room-emoji"><Users size={22} /></span>
                    <strong>{room.name}</strong>{room.activeToday && <span className="activity-badge" role="img" aria-label="오늘 활동한 모임" title="오늘 활동한 모임">🔥</span>}
                  </button>
                </td>
                <td>#{room.id}</td>
                <td>{room.host.nickname}</td>
                <td>{room.memberCount}명</td>
                <td>{roomSchedule(room)}</td>
                <td>
                  <button className="icon-button" aria-label={`${room.name} 상세`} onClick={() => setSelectedId(room.id)}>
                    <ArrowUpRight size={18} />
                  </button>
                </td>
              </tr>)}
            </tbody>
          </table>
        </div>
        {!data.content.length && <div className="empty-state"><p>검색 결과가 없어요.</p></div>}
        <div className="pagination">
          <span>{data.totalElements ? data.page * data.size + 1 : 0}–{data.page * data.size + data.content.length} / {data.totalElements}개</span>
          <div>
            <button aria-label="이전 페이지" disabled={page === 0} onClick={() => setPage(value => value - 1)}>
              <ChevronLeft size={16} />
            </button>
            <span>{data.totalPages ? page + 1 : 0} / {data.totalPages}</span>
            <button aria-label="다음 페이지" disabled={page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}>
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </>}
    </section>
    {selectedId !== null && <RoomDetailDialog key={selectedId} roomId={selectedId} onClose={() => setSelectedId(null)} />}
  </>;
}
