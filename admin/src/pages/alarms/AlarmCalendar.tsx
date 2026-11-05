import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { getSeoulToday } from '../../utils/format';

export default function AlarmCalendar({ date, onChange }: { date: string; onChange: (date: string) => void }) {
  const [month, setMonth] = useState(() => date.slice(0, 7));
  useEffect(() => setMonth(date.slice(0, 7)), [date]);
  const [year, monthNumber] = month.split('-').map(Number);
  const offset = new Date(Date.UTC(year, monthNumber - 1, 1)).getUTCDay();
  const days = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  const today = getSeoulToday();
  const move = (delta: number) => {
    const next = new Date(Date.UTC(year, monthNumber - 1 + delta, 1));
    setMonth(next.toISOString().slice(0, 7));
  };
  return <section className="panel alarm-calendar" aria-label="기록 날짜 선택">
    <div className="alarm-filter-heading"><h2>조회 날짜</h2><button className="text-button" onClick={() => { setMonth(today.slice(0, 7)); onChange(today); }}>오늘</button></div>
    <div className="alarm-month"><button className="icon-button" aria-label="이전 달" onClick={() => move(-1)}><ChevronLeft size={18} /></button><strong>{year}년 {monthNumber}월</strong><button className="icon-button" aria-label="다음 달" onClick={() => move(1)}><ChevronRight size={18} /></button></div>
    <div className="alarm-calendar-grid">
      {['일', '월', '화', '수', '목', '금', '토'].map(day => <span className="alarm-weekday" key={day}>{day}</span>)}
      {Array.from({ length: offset }, (_, index) => <span key={`empty-${index}`} />)}
      {Array.from({ length: days }, (_, index) => {
        const value = `${month}-${String(index + 1).padStart(2, '0')}`;
        return <button key={value} className={`${value === date ? 'selected' : ''} ${value === today ? 'today' : ''}`} aria-label={`${value} 기록 조회`} aria-pressed={value === date} aria-current={value === today ? 'date' : undefined} onClick={() => onChange(value)}>{index + 1}</button>;
      })}
    </div>
    <p className="alarm-date-note">선택 날짜 {date} · 한국 시간 기준</p>
  </section>;
}
