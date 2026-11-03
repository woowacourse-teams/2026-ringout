import { RotateCcw, X } from 'lucide-react';

type Props = {
  activity: string;
  onClear: () => void;
};

export default function ActiveActivityFilter({ activity, onClear }: Props) {
  if (!activity) return null;
  const label = activity === 'true' ? '🔥 오늘 활동함' : '오늘 활동 없음';
  return <div className="active-filters" role="group" aria-label="적용된 필터">
    <button className="clear-filters" onClick={onClear}>
      <RotateCcw size={15} aria-hidden="true" />전체 해제
    </button>
    <span className="active-filters-divider" aria-hidden="true" />
    <button className="active-filter-chip" onClick={onClear} aria-label={`${label} 필터 해제`}>
      {label}<X size={15} aria-hidden="true" />
    </button>
  </div>;
}
