import { useCallback, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { getUser, getUserRooms, type UserDetail, type JoinedRoom } from '../../api/adminUsers';
import { displayDate, roomSchedule } from '../../utils/format';
import { providerName } from './userPresentation';
import useApiData from '../../hooks/useApiData';
import Dialog from '../../components/Dialog';

export default function UserDetailDialog({ userId, initialRoomsOpen = false, onClose }: {
  userId: number; initialRoomsOpen?: boolean; onClose: () => void;
}) {
  const [roomsOpen, setRoomsOpen] = useState(initialRoomsOpen);
  const loadUser = useCallback((signal: AbortSignal) => getUser(userId, signal), [userId]);
  const loadRooms = useCallback((signal: AbortSignal) => getUserRooms(userId, signal), [userId]);
  const detail = useApiData<UserDetail>(loadUser);
  const rooms = useApiData<JoinedRoom[]>(roomsOpen ? loadRooms : null);
  const user = detail.data;
  return <Dialog title="회원 상세" onClose={onClose}>
    {detail.loading && <p role="status">회원 정보를 불러오는 중이에요.</p>}
    {detail.error && <div role="alert"><p>{detail.error}</p><button className="secondary-button" onClick={detail.retry}>다시 시도</button></div>}
    {user && <>
      <div className="detail-profile">
        <span className="avatar large peach">{user.nickname.slice(0, 1)}</span>
        <div><h3>{user.nickname}</h3><p>#{user.id} · {providerName(user.provider)}</p></div>
      </div>
      <dl className="detail-grid">
        <dt>이메일</dt><dd>{user.maskedEmail ?? '—'}</dd>
        <dt>가입일</dt><dd>{displayDate(user.joinedAt)}</dd>
        <dt>최근 로그인</dt><dd>{displayDate(user.lastLoginAt)}</dd>
        <dt>역할</dt><dd>{user.role}</dd>
        <dt>참여 모임</dt><dd><button className="member-rooms-toggle" aria-expanded={roomsOpen}
          aria-controls={`user-${userId}-rooms`} onClick={() => setRoomsOpen(value => !value)}>
          {rooms.data?.length ?? user.roomCount}개 <ChevronDown size={15} className={roomsOpen ? 'expanded' : ''} />
        </button></dd>
        <dt>약관 동의</dt><dd>{user.termsAgreements.agreements.map(agreement => (
          <div key={agreement.type}>
            {({ SERVICE: '서비스', PRIVACY: '개인정보' } as Record<string, string>)[agreement.type] ?? agreement.type}
            {' · '}{agreement.needsReagreement ? (agreement.agreedVersion ? '재동의 필요' : '미동의') : '동의 완료'}
            {agreement.agreedAt && <small> ({displayDate(agreement.agreedAt)})</small>}
          </div>
        ))}</dd>
      </dl>
      {roomsOpen && <section className="member-rooms" id={`user-${userId}-rooms`} aria-label="참여 중인 모임">
        <h4>참여 중인 모임</h4>
        {rooms.loading && <p role="status">모임을 불러오는 중이에요.</p>}
        {rooms.error && <div role="alert"><p>{rooms.error}</p><button className="secondary-button" onClick={rooms.retry}>다시 시도</button></div>}
        {rooms.data && (rooms.data.length ? <ul>{rooms.data.map(room => <li key={room.id}>
          <div><strong>{room.name}</strong><p>{roomSchedule(room)} · {room.memberCount}명</p></div>
          <small>{room.host ? '방장' : '멤버'}</small>
        </li>)}</ul> : <p className="member-rooms-empty">현재 참여 중인 모임이 없어요.</p>)}
      </section>}
    </>}
    <div className="dialog-actions">
      <button className="secondary-button" disabled title="개발 중인 기능입니다.">알람 기록 보기 · 개발 중</button>
      <button className="danger-button" disabled title="이용 제한 API 준비 후 제공됩니다.">이용 제한 · 준비 중</button>
      <button className="secondary-button" onClick={onClose}>닫기</button>
    </div>
  </Dialog>;
}
