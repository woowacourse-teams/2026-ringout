import { ArrowRight, Clock3, LoaderCircle } from 'lucide-react';
import { adminConfig } from '../../config';

type Props = {
  onLogin: () => void;
  onCancelLogin: () => void;
  loggingIn: boolean;
  error: string;
};

export default function ApprovalPendingPage({ onLogin, onCancelLogin, loggingIn, error }: Props) {
  return (
    <main className="approval-pending-page">
      <section className="approval-pending-card" aria-labelledby="approval-pending-title">
        <div className="brand approval-brand">
          <img src="/ringout-logo.png" alt="링아웃 로고" />
          <span>ringout<span className="brand-dot">.</span></span>
          <small>admin</small>
        </div>
        <span className="login-environment">{adminConfig.label} · 관리자 웹</span>
        <div className="approval-pending-icon" aria-hidden="true"><Clock3 size={32} /></div>
        <div role="status">
          <h1 id="approval-pending-title">승인 대기중</h1>
          <p className="approval-pending-description">관리자에게 승인을 요청하세요.</p>
        </div>
        <p className="approval-pending-help">계정이 등록되었습니다.<br />승인이 완료되면 다시 로그인해 주세요.</p>
        <button className="primary-button full-width" onClick={onLogin} disabled={loggingIn}>
          {loggingIn ? <LoaderCircle size={17} className="auth-spinner" /> : <ArrowRight size={17} />}
          {loggingIn ? '구글 로그인 진행 중' : '승인 후 다시 로그인'}
        </button>
        {loggingIn && <button className="logout-button" onClick={onCancelLogin}>로그인 대기 취소</button>}
        {error && <p className="login-error-panel" role="alert">{error}</p>}
      </section>
    </main>
  );
}
