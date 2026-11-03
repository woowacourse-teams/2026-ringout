type Metric = {
  label: string;
  emoji: string;
  value: number | undefined;
};

type Props = {
  label: string;
  metrics: Metric[];
  unit: '명' | '개';
  loading: boolean;
  error: string;
  onRetry: () => void;
};

export default function ManagementStats({ label, metrics, unit, loading, error, onRetry }: Props) {
  return (
    <section className="management-summary" aria-label={label} aria-busy={loading}>
      <div className="management-summary-grid">
        {metrics.map(metric => (
          <div className="panel management-summary-card" key={metric.label}>
            <span className="management-summary-label">
              <span className="management-summary-emoji" aria-hidden="true">{metric.emoji}</span>
              {metric.label}
            </span>
            <strong>
              {metric.value === undefined ? '—' : metric.value.toLocaleString('ko-KR')}
              <small>{unit}</small>
            </strong>
          </div>
        ))}
      </div>
      {error ? (
        <div className="management-summary-note" role="alert">
          <span>{error}</span>
          <button className="text-button" onClick={onRetry}>통계 다시 불러오기</button>
        </div>
      ) : loading ? <p className="management-summary-note" role="status">통계 확인 중</p> : null}
    </section>
  );
}
