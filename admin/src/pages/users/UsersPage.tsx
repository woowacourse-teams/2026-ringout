import { useCallback, useEffect, useState } from 'react';
import { ArrowUpRight, Search, ChevronLeft, ChevronRight } from 'lucide-react';
import { getUsers, getUserStats, type UserPage } from '../../api/adminUsers';
import { displayDate } from '../../utils/format';
import { providerName } from './userPresentation';
import useApiData from '../../hooks/useApiData';
import UserDetailDialog from './UserDetailDialog';
import ManagementStats from '../../components/ManagementStats';
import LoadingIndicator from '../../components/LoadingIndicator';

export default function UsersPage() {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<{ id: number; roomsOpen: boolean } | null>(null);
  const size = 20;
  useEffect(() => {
    const timer = window.setTimeout(() => { setQuery(search.trim()); setPage(0); }, 300);
    return () => window.clearTimeout(timer);
  }, [search]);
  const loadUsers = useCallback((signal: AbortSignal) => getUsers({ search: query, page, size }, signal), [query, page]);
  const result = useApiData<UserPage>(loadUsers);
  const loadStats = useCallback((signal: AbortSignal) => getUserStats(signal), []);
  const stats = useApiData(loadStats);
  const metrics = [
    { label: '총 회원', emoji: '👥', value: stats.data?.totalUsers },
    { label: '오늘 가입한 회원', emoji: '🌱', value: stats.data?.joinedToday },
    { label: '오늘 활동한 회원', emoji: '🔥', value: stats.data?.activeToday },
  ];
  const data = result.data;
  const pending = result.loading || search.trim() !== query;
  const open = (id: number, roomsOpen = false) => setSelected({ id, roomsOpen });

  return <>
    <div className="page-heading">
      <div><div className="eyebrow">MANAGEMENT</div><h1>회원 관리<span className="heading-dot">.</span></h1>
        <p>링아웃과 함께하는 회원을 확인하세요.</p></div>
    </div>
    <ManagementStats
      label="회원 통계"
      metrics={metrics}
      unit="명"
      loading={stats.loading}
      error={stats.error}
      date={stats.data?.date}
      excluded="탈퇴 회원 제외"
      activityNote="활동: 로그인·알람 이벤트 기록 기준"
      onRetry={stats.retry}
    />
    <section className="panel records-panel member-records-panel" aria-busy={pending}>
      <div className="records-toolbar">
        <div className="search-input"><Search size={18} />
          <input aria-label="회원 검색" placeholder="회원 이름 또는 ID 검색" maxLength={100}
            value={search} onChange={event => setSearch(event.target.value)} />
        </div>
        <select aria-label="상태 필터" disabled title="이용 제한 API 준비 후 제공됩니다."><option>상태 필터 · 준비 중</option></select>
        <button className="text-button" onClick={() => { setSearch(''); setQuery(''); setPage(0); }}>초기화</button>
      </div>
      <div className="member-list-results">
      {pending ? <LoadingIndicator /> : result.error ? (
        <div className="empty-state" role="alert"><p>{result.error}</p>
          <button className="secondary-button" onClick={result.retry}>다시 시도</button></div>
      ) : data && <>
        <div className="list-count">전체 <strong>{data.totalElements}</strong>개의 결과</div>
        <div className="table-scroll"><table className="data-table">
          <thead><tr><th>회원</th><th>회원 ID</th><th>로그인</th><th>가입일</th><th>모임</th><th>상세</th></tr></thead>
          <tbody>{data.content.map(user => <tr
                key={user.id}
                className="detail-row"
                tabIndex={0}
                aria-label={`${user.nickname} 상세 보기`}
                aria-haspopup="dialog"
                onClick={event => {
                  // Keep nested buttons' own actions, including the joined-room shortcut.
                  if (event.target instanceof Element && event.target.closest('button, a, input, select, textarea')) return;
                  open(user.id);
                }}
                onKeyDown={event => {
                  if (event.target !== event.currentTarget) return;
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    open(user.id);
                  }
                }}
              >
            <td><button className="person-cell" onClick={() => open(user.id)}>
              <span className="avatar small peach">{user.nickname.slice(0, 1)}</span><strong>{user.nickname}</strong>
            </button></td>
            <td>#{user.id}</td><td>{providerName(user.provider)}</td><td>{displayDate(user.joinedAt).slice(0, 10)}</td>
            <td><button className="member-rooms-toggle" aria-label={`${user.nickname} 참여 모임 보기`}
              onClick={() => open(user.id, true)}>{user.roomCount}개</button></td>
            <td><button className="icon-button" aria-label={`${user.nickname} 상세`} onClick={() => open(user.id)}><ArrowUpRight size={18} /></button></td>
          </tr>)}</tbody>
        </table></div>
        {!data.content.length && <div className="empty-state"><p>검색 결과가 없어요.</p></div>}
        <div className="pagination">
          <span>{data.totalElements ? data.page * data.size + 1 : 0}–{data.page * data.size + data.content.length} / {data.totalElements}개</span>
          <div>
            <button aria-label="이전 페이지" disabled={page === 0} onClick={() => setPage(value => value - 1)}><ChevronLeft size={16} /></button>
            <span>{data.totalPages ? page + 1 : 0} / {data.totalPages}</span>
            <button aria-label="다음 페이지" disabled={page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}><ChevronRight size={16} /></button>
          </div>
        </div>
      </>}
      </div>
    </section>
    {selected && <UserDetailDialog key={selected.id} userId={selected.id} initialRoomsOpen={selected.roomsOpen} onClose={() => setSelected(null)} />}
  </>;
}
