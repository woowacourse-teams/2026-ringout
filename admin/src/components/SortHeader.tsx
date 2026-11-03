type Props = {
  label: string;
  active: boolean;
  direction: 'asc' | 'desc';
  onSort: () => void;
};

export default function SortHeader({ label, active, direction, onSort }: Props) {
  const nextDirection = active && direction === 'desc' ? '오름차순' : '내림차순';
  return <th aria-sort={active ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}>
    <button className={`sort-header${active ? ' active' : ''}`} onClick={onSort}
      aria-label={`${label} ${nextDirection} 정렬`}>
      {label}{active && <span className="sort-indicator" aria-hidden="true">{direction === 'asc' ? '⌃' : '⌵'}</span>}
    </button>
  </th>;
}
