import { useCallback, useState } from 'react';
import { CalendarDays, ExternalLink, ImageOff, Users } from 'lucide-react';
import { getRoom, type RoomDetail } from '../../api/adminRooms';
import { displayDate, roomSchedule } from '../../utils/format';
import useApiData from '../../hooks/useApiData';
import Dialog from '../../components/Dialog';
import RoomTodayRecords from './RoomTodayRecords';

export default function RoomDetailDialog({ roomId, onClose }: {
  roomId: number;
  onClose: () => void;
}) {
  const loadRoom = useCallback((signal: AbortSignal) => getRoom(roomId, signal), [roomId]);
  const result = useApiData<RoomDetail>(loadRoom);
  const room = result.data;
  const [failedPhoto, setFailedPhoto] = useState<string | null>(null);
  const hasPhoto = Boolean(room?.imageUrl) && failedPhoto !== room?.imageUrl;

  return (
    <Dialog title="모임 상세" className="room-detail-dialog" onClose={onClose}>
      {result.loading && <p role="status">모임 정보를 불러오는 중이에요.</p>}
      {result.error && <div role="alert">
        <p>{result.error}</p>
        <button className="secondary-button" onClick={result.retry}>다시 시도</button>
      </div>}
      {room && <>
        <div className="room-detail-overview">
          <div className="room-detail-photo">
            {hasPhoto ? <>
              <img src={room.imageUrl!} alt={`${room.name} 모임 사진`} onError={() => setFailedPhoto(room.imageUrl)} />
              <a href={room.imageUrl!} target="_blank" rel="noopener noreferrer">
                원본 보기 <ExternalLink size={12} />
              </a>
            </> : (
              <div className="room-photo-placeholder">
                <ImageOff size={27} strokeWidth={1.5} />
                <span>{room.imageUrl ? '사진을 불러올 수 없어요' : '등록된 사진이 없어요'}</span>
              </div>
            )}
          </div>
          <div className="room-detail-info">
            <div className="room-detail-meta"><span>모임 #{room.id}</span></div>
            <h3>{room.name}</h3>
            <p className="room-host">방장 <strong>{room.host.nickname}</strong> · #{room.host.id}</p>
            <div className="room-introduction">
              <h4>소개</h4>
              <p>{room.description || '등록된 소개가 없어요.'}</p>
            </div>
          </div>
        </div>
        <dl className="room-detail-facts">
          <div><dt><Users size={16} />멤버 수</dt><dd>{room.memberCount}<span>명</span></dd></div>
          <div><dt><CalendarDays size={16} />활동 일정</dt><dd>{roomSchedule(room)}</dd></div>
        </dl>
        <dl className="detail-grid">
          <dt>생성일</dt><dd>{displayDate(room.createdAt)}</dd>
          <dt>최근 활동</dt><dd>{displayDate(room.latestActivityAt)}</dd>
        </dl>
        <RoomTodayRecords roomId={room.id} />
      </>}
      <div className="dialog-actions room-detail-actions">
        <button className="secondary-button" onClick={onClose}>닫기</button>
      </div>
    </Dialog>
  );
}
