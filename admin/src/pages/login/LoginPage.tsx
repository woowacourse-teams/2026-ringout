import { ArrowRight, LoaderCircle, ShieldCheck } from 'lucide-react';
import { adminConfig } from '../../config';

type Props = {
  onLogin: () => void;
  onCancelLogin: () => void;
  onRetry: () => void;
  checking: boolean;
  loggingIn: boolean;
  error: string;
};

export default function LoginPage({ onLogin, onCancelLogin, onRetry, checking, loggingIn, error }: Props) {
  return (
    <main className="login-page">
      <section className="login-brand-panel" aria-label="링아웃 관리자 소개">
        <a href="#" className="brand login-brand">
          <img src="/ringout-logo.png" alt="링아웃 로고" />
          <span>ringout<span className="brand-dot">.</span></span>
          <small>admin</small>
        </a>
        <div className="login-brand-message">
          <span className="login-eyebrow">RINGOUT WORKSPACE</span>
          <h1>좋은 하루의 시작을,<br />함께 관리해요.</h1>
          <p>작은 습관이 모이는 곳.<br />링아웃의 회원과 모임을 한눈에 살펴보세요.</p>
          <div className="login-art" aria-hidden="true"><span>r</span><i /><b>좋은 아침!</b></div>
        </div>
        <small>© 2026 RingOut</small>
      </section>
      <section className="login-form-panel" aria-labelledby="login-title">
        <div className="login-form-wrap">
          <span className="login-environment">{adminConfig.label} · 관리자 웹</span>
          <h2 id="login-title">관리자 로그인</h2>
          <p className="login-description">링아웃 워크스페이스에 오신 걸 환영해요.</p>
          <button
            type="button"
            className="google-login-button"
            disabled={checking || loggingIn}
            onClick={onLogin}
          >
            {checking || loggingIn ? <LoaderCircle size={20} className="auth-spinner" /> : (
              <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
                <path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.39-.18-2.05H12v3.88h5.38a4.6 4.6 0 0 1-2 3.02v2.51h3.24c1.89-1.74 2.98-4.3 2.98-7.36Z" />
                <path fill="#34A853" d="M12 22c2.7 0 4.96-.9 6.62-2.41l-3.24-2.51c-.9.6-2.05.96-3.38.96-2.61 0-4.82-1.76-5.61-4.13H3.04v2.59A10 10 0 0 0 12 22Z" />
                <path fill="#FBBC05" d="M6.39 13.91a6 6 0 0 1 0-3.82V7.5H3.04a10 10 0 0 0 0 9l3.35-2.59Z" />
                <path fill="#EA4335" d="M12 5.96c1.47 0 2.79.5 3.82 1.49l2.87-2.87A9.6 9.6 0 0 0 12 2a10 10 0 0 0-8.96 5.5l3.35 2.59C7.18 7.72 9.39 5.96 12 5.96Z" />
              </svg>
            )}
            <span>{checking ? '로그인 상태 확인 중' : loggingIn ? '구글 로그인 진행 중' : 'Google 계정으로 로그인'}</span>
            {!checking && !loggingIn && <ArrowRight size={17} />}
          </button>
          {loggingIn && <p className="login-description" role="status">새 창에서 구글 로그인을 완료해 주세요.</p>}
          {loggingIn && <button className="logout-button" onClick={onCancelLogin}>로그인 창을 닫았다면 대기 취소</button>}
          {error && (
            <div className="login-error-panel" role="alert">
              <p>{error}</p>
              <button type="button" onClick={onRetry} disabled={checking || loggingIn}>연결 다시 확인</button>
            </div>
          )}
          <aside className="login-access-note">
            <ShieldCheck size={18} />
            <div>
              <strong>승인된 관리자만 접속할 수 있어요</strong>
              <p>처음 로그인하면 관리자 승인이 필요합니다.<br />승인 완료 후 다시 로그인해 주세요.</p>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
