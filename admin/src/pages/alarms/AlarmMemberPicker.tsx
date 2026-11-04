import { useCallback, useEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { getUsers, type UserSummary } from '../../api/adminUsers';
import { apiErrorMessage } from '../../api/apiClient';
import LoadingIndicator from '../../components/LoadingIndicator';

type Member = Pick<UserSummary, 'id' | 'nickname'>;
type Selection = { selected: Member | null; onSelect: (member: Member | null) => void };

function MemberList({ query, date, selected, onSelect }: Selection & { query: string; date: string }) {
  const [members, setMembers] = useState<UserSummary[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const scrollRoot = useRef<HTMLDivElement>(null);
  const sentinel = useRef<HTMLDivElement>(null);
  // Lock synchronously so repeated scroll notifications cannot skip a page.
  const fetching = useRef(true);

  useEffect(() => {
    const controller = new AbortController();
    fetching.current = true;
    setLoading(true);
    setError('');
    void getUsers({ activityDate: date, search: query, page, size: 20, sortBy: 'id', sortDirection: 'desc' }, controller.signal)
      .then(data => {
        if (controller.signal.aborted) return;
        setMembers(previous => page === 0 ? data.content : [...previous, ...data.content.filter(member => !previous.some(item => item.id === member.id))]);
        setTotal(data.totalElements);
        setHasNext(data.page + 1 < data.totalPages);
      }).catch(failure => {
        if (!controller.signal.aborted) setError(apiErrorMessage(failure));
      }).finally(() => {
        if (!controller.signal.aborted) { fetching.current = false; setLoading(false); }
      });
    return () => controller.abort();
  }, [date, query, page, attempt]);

  const loadMore = useCallback(() => {
    if (fetching.current || !hasNext || error) return;
    fetching.current = true;
    setPage(value => value + 1);
  }, [hasNext, error]);

  useEffect(() => {
    if (!sentinel.current || loading || error || !hasNext) return;
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) loadMore();
    }, { root: scrollRoot.current, rootMargin: '80px' });
    observer.observe(sentinel.current);
    return () => observer.disconnect();
  }, [loadMore, loading, error, hasNext]);

  return <>
    <div className="alarm-member-results" ref={scrollRoot} aria-label="회원 목록" aria-busy={loading}>
      <div className="alarm-member-list">{members.map(member => <button key={member.id} className={`alarm-member ${selected?.id === member.id ? 'selected' : ''}`} aria-pressed={selected?.id === member.id} onClick={() => onSelect(selected?.id === member.id ? null : member)}>
        <span className="avatar small peach">{member.nickname.slice(0, 1)}</span><span className="alarm-member-name"><strong>{member.nickname}</strong>{member.activeOnDate === true && <span className="activity-badge" role="img" aria-label="선택한 날짜에 개인 알람 기록이 있는 회원" title="선택한 날짜에 개인 알람 기록이 있는 회원">🔥</span>}</span><small>#{member.id}</small>
      </button>)}</div>
      {loading && <LoadingIndicator />}
      {error && <div className="alarm-inline-state" role="alert"><p>{error}</p><button className="secondary-button" onClick={() => setAttempt(value => value + 1)}>다시 시도</button></div>}
      {!loading && !error && !members.length && <p className="alarm-inline-state">검색 결과가 없어요.</p>}
      <div ref={sentinel}>{!loading && !error && hasNext && <button className="text-button alarm-load-more" onClick={loadMore}>회원 더 보기</button>}</div>
    </div>
    <p className="alarm-member-count" role="status">검색 결과 {total}명{selected && ' · 선택한 회원을 다시 누르면 해제됩니다.'}</p>
  </>;
}

export default function AlarmMemberPicker({ date, selected, onSelect }: Selection & { date: string }) {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);
  return <section className="panel alarm-members" aria-labelledby="alarm-members-title">
    <div className="alarm-filter-heading"><h2 id="alarm-members-title">회원 선택</h2><button className="text-button" onClick={() => { onSelect(null); setSearch(''); setQuery(''); }}>초기화</button></div>
    <div className="search-input"><Search size={17} /><input aria-label="알람 기록 회원 검색" placeholder="회원 이름 또는 ID 검색" maxLength={100} value={search} onChange={event => setSearch(event.target.value)} /></div>
    {search.trim() !== query ? <div className="alarm-member-results"><LoadingIndicator /></div> : <MemberList key={`${date}-${query}`} date={date} query={query} selected={selected} onSelect={onSelect} />}
  </section>;
}
