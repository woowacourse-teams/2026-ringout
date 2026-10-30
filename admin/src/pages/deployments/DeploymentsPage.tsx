import { ChevronRight, RefreshCw, Package } from 'lucide-react';
import { useCallback, useState } from 'react';
import { deploymentEnvironments, type DeploymentEnvironment } from '../../api/deploymentEnvironment';
import { getDeployments, getDeployment, type Deployment } from '../../api/adminDeployments';
import useApiData from '../../hooks/useApiData';
import Dialog from '../../components/Dialog';
import './deployments.css';
import { formatDeploymentEvents } from './formatDeploymentEvents';
import { serviceState } from './deploymentState';

const date = (value: string | null) => value ? new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value)) : '—';
// Only commit tags represent Git revisions; digest-only or legacy tags stay unknown.
const commit = (image: string) => {
  if (image.includes('@')) return null;
  const tag = image.split('/').pop()?.match(/:([0-9a-f]{7,40})$/i)?.[1];
  return tag ?? null;
};
const imageLabel = (image: string, digest?: string | null, imageId?: string | null, fallback?: string) => {
  const sha = commit(image);
  if (sha) return sha.slice(0, 7);
  const hash = (digest ?? image).match(/@sha256:([0-9a-f]{64})$/i)?.[1];
  if (hash) return `sha256:${hash.slice(0, 12)}`;
  const localId = imageId?.match(/^sha256:([0-9a-f]{64})$/i)?.[1];
  if (localId) return `image:${localId.slice(0, 12)}`;
  return fallback ?? '식별자 미수집';
};
const deploymentLabel = (item: Deployment) => imageLabel(item.image, item.imageDigest, item.imageId, `배포 #${item.id}`);
const duration = (item: Deployment) => `${Math.max(0, Math.round((Date.parse(item.finishedAt) - Date.parse(item.startedAt)) / 1000))}초`;

export default function DeploymentsPage() {
  const [environment, setEnvironment] = useState<DeploymentEnvironment>('production');
  return <><div className="page-heading"><div><div className="eyebrow">OPERATIONS</div><h1>배포 관리<span className="heading-dot">.</span></h1><p>배포·수동 복구 기록과 이벤트 로그를 확인하세요.</p></div></div>
    <div className="deploy-environments" role="group" aria-label="배포 환경">{(['production', 'development'] as const).map(value => <button key={value} aria-pressed={environment === value} onClick={() => setEnvironment(value)}>{deploymentEnvironments[value].label} 환경</button>)}</div>
    <DeploymentWorkspace key={environment} environment={environment} /></>;
}
function DeploymentWorkspace({ environment }: { environment: DeploymentEnvironment }) {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const list = useApiData(useCallback((signal: AbortSignal) => getDeployments(environment, page, signal), [environment, page]));
  const newestRequest = useCallback((signal: AbortSignal) => getDeployments(environment, 0, signal), [environment]);
  const newest = useApiData(page === 0 ? null : newestRequest);
  const newestItem = (page === 0 ? list.data : newest.data)?.content[0];
  const snapshotRequest = useCallback((signal: AbortSignal) => getDeployment(newestItem!.id, signal), [newestItem?.id]);
  const snapshot = useApiData(newestItem ? snapshotRequest : null);
  const state = snapshot.data ? serviceState(snapshot.data) : null;
  const detailRequest = useCallback((signal: AbortSignal) => getDeployment(selected!, signal), [selected]);
  const detail = useApiData(selected === null ? null : detailRequest);
  const label = deploymentEnvironments[environment].label;
  const rowKeys = (event: React.KeyboardEvent, open: () => void) => {
    if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(); }
  };
  return <div className="deploy-workspace">
    <section className="panel deploy-service-card" aria-label="마지막 수집 기준 서비스 상태">
      <div className="deploy-service-heading"><span className="deploy-section-icon"><Package size={20} /></span><div><h2>{label} 서비스 상태</h2><p>마지막 배포 기록 기준 · 실시간 상태와 다를 수 있습니다.</p></div><span className={`deploy-badge ${state?.uncertain || snapshot.error ? 'pending' : 'success'}`}>{state?.uncertain || snapshot.error ? '확인 필요' : '수집 기록'}</span></div>
      <div className="deploy-service-grid"><div><span>서비스 배포 식별자</span><strong><code>{state?.image ? imageLabel(state.image, state.imageDigest, state.imageId) : snapshot.loading || list.loading || newest.loading ? '조회 중…' : '확인 필요'}</code></strong></div><div><span>서비스 포트</span><strong>{state?.port ?? '미수집'}</strong></div><div><span>Flyway 버전</span><strong>{snapshot.data?.deployment.flywayVersion ? `V${snapshot.data.deployment.flywayVersion}` : '미수집'}</strong></div><div><span>마지막 배포 결과</span><strong>{newestItem ? newestItem.status === 'SUCCEEDED' ? '성공' : '실패' : '—'}</strong></div><div><span>기록 시각 · KST</span><strong className="deploy-service-time">{newestItem ? date(newestItem.finishedAt) : '—'}</strong></div></div>
      {state?.retained && <p className="deploy-service-note">새 배포가 실패하여 이전 버전 유지로 기록되었습니다.</p>}
      {(snapshot.error || newest.error) && <p className="deploy-service-note" role="alert">상태 기록을 불러오지 못했습니다. 새로고침해 주세요.</p>}
    </section>
    <section className="panel deploy-history">
      <div className="deploy-history-heading">
        <div className="deploy-section-title"><span className="deploy-section-icon"><Package size={20} /></span><div><h2>{label} 배포 기록</h2><p>배포 식별자와 실행 결과를 확인하세요. 모든 시각은 KST 기준입니다.</p></div></div>
        <button className="secondary-button deploy-refresh" onClick={() => { list.retry(); newest.retry(); snapshot.retry(); }} disabled={list.loading}><RefreshCw size={14} />새로고침</button>
      </div>
        {list.loading ? <p className="deploy-empty" role="status">배포 이력을 불러오는 중입니다.</p> : list.error ? <ErrorMessage message={list.error} retry={list.retry} /> : <>
          <div className="table-scroll"><table className="data-table deploy-compact-table"><thead><tr><th scope="col">배포 식별자</th><th scope="col">작업</th><th scope="col">결과</th><th scope="col">배포 완료 · KST</th><th scope="col">소요 시간</th><th scope="col"><span className="deploy-sr-only">상세보기</span></th></tr></thead>
          <tbody>{list.data?.content.map(item => <tr key={item.id} tabIndex={0} className="deploy-clickable" aria-label={`${deploymentLabel(item)} 배포 상세보기`} aria-haspopup="dialog" onClick={() => setSelected(item.id)} onKeyDown={event => rowKeys(event, () => setSelected(item.id))}>
            <td><div className="deploy-version-cell"><code title={commit(item.image) ?? item.imageDigest ?? item.imageId ?? item.image}>{deploymentLabel(item)}</code>{item.id === list.data?.latestSuccessfulDeploymentId && <span className="deploy-latest">최신 성공</span>}{state?.imageId && item.status === 'SUCCEEDED' && item.imageId === state.imageId && <span className="deploy-serving">서비스 배포 · 기록 기준</span>}</div>{item.status === 'FAILED' && <div className="deploy-failure-version">{item.previousImage ? `실패 시 이전 배포: ${imageLabel(item.previousImage, item.previousImageDigest, item.previousImageId)}` : '이전 배포 미수집'}</div>}</td>
            <td><span className="deploy-kind">{item.actionType === 'ROLLBACK' ? '복구 배포' : '배포'}</span></td>
            <td><StatusBadge status={item.status} /></td><td className="deploy-date-cell">{date(item.finishedAt)}</td><td className="deploy-duration">{duration(item)}</td><td><ChevronRight size={16} aria-hidden="true" /></td>
          </tr>)}</tbody></table></div>
          {!list.data?.content.length && <div className="deploy-empty"><Package size={28} /><strong>배포 기록이 없습니다.</strong><span>첫 배포가 수집되면 이곳에 표시됩니다.</span></div>}
          <Pagination page={page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />
        </>}
    </section>
    {selected !== null && <Dialog title={`${label} 배포 상세`} onClose={() => setSelected(null)} className="deploy-detail-dialog">
      {detail.loading ? <p role="status">불러오는 중…</p> : detail.error ? <ErrorMessage message={detail.error} retry={detail.retry} /> : detail.data && <>
        <div className="deploy-detail-summary"><code title={commit(detail.data.deployment.image) ?? detail.data.deployment.imageDigest ?? detail.data.deployment.imageId ?? detail.data.deployment.image}>{deploymentLabel(detail.data.deployment)}</code><StatusBadge status={detail.data.deployment.status} /></div>
        {detail.data.deployment.status === 'FAILED' && <div className="deploy-failure-box"><strong>실패 후 서비스 상태</strong><p>{serviceState(detail.data).image ? `이전 배포 유지 기록: ${imageLabel(serviceState(detail.data).image!, serviceState(detail.data).imageDigest, serviceState(detail.data).imageId)}` : '실행 중인 버전을 확인할 수 없습니다. 서버 상태를 확인해 주세요.'}</p><span>포트: {serviceState(detail.data).port ?? '미수집'} · 배포 종료 시점의 기록입니다.</span></div>}
        <dl className="deploy-details">
          <div><dt>환경 / 작업</dt><dd>{label} · {detail.data.deployment.actionType === 'ROLLBACK' ? '복구 배포' : '배포'}</dd></div>
          <div><dt>시작 시각</dt><dd>{date(detail.data.deployment.startedAt)}</dd></div>
          <div><dt>완료 / 소요</dt><dd>{date(detail.data.deployment.finishedAt)} · {duration(detail.data.deployment)}</dd></div>
          <div><dt>Flyway 버전</dt><dd>{detail.data.deployment.flywayVersion ? `V${detail.data.deployment.flywayVersion}` : '미수집'}</dd></div>
        </dl>
        <details className="deploy-log-section deploy-reference-section">
          <summary>복구 참고 정보</summary>
          <p>수동 복구에 필요한 이미지 식별자와 이전 배포 정보입니다.</p>
          <dl className="deploy-details">
            <div><dt>배포 ID</dt><dd>{detail.data.deployment.deploymentId}</dd></div>
            <div><dt>전체 커밋 SHA</dt><dd><code>{commit(detail.data.deployment.image) ?? '미수집'}</code></dd></div>
            <div><dt>이미지</dt><dd><code>{detail.data.deployment.image}</code></dd></div>
            <div><dt>이미지 digest</dt><dd><code>{detail.data.deployment.imageDigest ?? '미수집'}</code></dd></div>
            {detail.data.deployment.rollbackSourceId != null && <div><dt>복구 대상 기록 ID</dt><dd>{detail.data.deployment.rollbackSourceId}</dd></div>}
            <div><dt>이미지 ID</dt><dd><code>{detail.data.deployment.imageId ?? '미수집'}</code></dd></div>
            <div><dt>직전 이미지</dt><dd><code>{detail.data.deployment.previousImage ?? '미수집'}</code></dd></div>
            <div><dt>직전 이미지 ID</dt><dd><code>{detail.data.deployment.previousImageId ?? '미수집'}</code></dd></div>
            <div><dt>직전 이미지 digest</dt><dd><code>{detail.data.deployment.previousImageDigest ?? '미수집'}</code></dd></div>
            {detail.data.deployment.actionsRunId != null && <div><dt>Actions 실행 ID</dt><dd>{detail.data.deployment.actionsRunId}</dd></div>}
          </dl>
        </details>
        <details className="deploy-log-section"><summary>배포 이벤트 로그 · KST</summary><pre className="deploy-event-log">{formatDeploymentEvents(detail.data.events)}</pre></details>
        <div className="deploy-detail-footer"><p>복구는 서버의 rollback.sh에 이미지 digest를 전달해 실행합니다. 이 화면은 기록 조회만 제공합니다.</p></div>
      </>}
    </Dialog>}
  </div>;
}
function ErrorMessage({ message, retry }: { message: string; retry: () => void }) { return <div className="deploy-footnote" role="alert"><p>{message}</p><button className="secondary-button" onClick={retry}>다시 조회</button></div>; }
function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) { return <div className="deploy-pagination"><button disabled={page === 0} onClick={() => onChange(page - 1)}>이전</button><span>{totalPages ? page + 1 : 0} / {totalPages}</span><button disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>다음</button></div>; }
function StatusBadge({ status }: { status: string }) {
  return <span className={`deploy-badge ${status === 'SUCCEEDED' ? 'success' : 'failed'}`}><span aria-hidden="true" />{status === 'SUCCEEDED' ? '성공' : '실패'}</span>;
}
