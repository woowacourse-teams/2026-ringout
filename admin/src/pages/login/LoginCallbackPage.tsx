import { useEffect, useRef, useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { loginChannelName, readLoginError, type LoginNotification } from './loginResult';

/** Google에서 돌아온 팝업이 같은 출처의 원래 화면에 결과만 알립니다. 인증 정보는 보내지 않습니다. */
export default function LoginCallbackPage() {
  const channel = useRef<BroadcastChannel | null>(null);
  const notification = useRef<LoginNotification | null>(null);
  const [received, setReceived] = useState(false);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    if (!('BroadcastChannel' in window)) {
      setUnavailable(true);
      return;
    }
    const connection = new BroadcastChannel(loginChannelName);
    channel.current = connection;
    const message: LoginNotification = notification.current ?? {
      type: 'ringout-admin-login-result',
      id: crypto.randomUUID(),
      error: readLoginError(window.location.hash.split('?')[1] ?? ''),
      sentAt: Date.now(),
    };
    notification.current = message;
    connection.onmessage = event => {
      if (event.data?.type === 'ringout-admin-login-received' && event.data.id === message.id) {
        setReceived(true);
        window.close();
      }
    };
    connection.postMessage(message);
    return () => {
      connection.close();
      channel.current = null;
    };
  }, []);

  return (
    <main className="approval-pending-page">
      <section className="approval-pending-card" aria-labelledby="login-callback-title">
        <div className="approval-pending-icon"><CheckCircle2 size={32} /></div>
        <h1 id="login-callback-title">로그인 처리 완료</h1>
        <p className="approval-pending-help" role="status">
          {unavailable ? '이 브라우저에서는 로그인 창 연결을 지원하지 않습니다.' :
            received ? '원래 관리자 화면에서 결과를 확인해 주세요.' : '원래 관리자 화면으로 결과를 전달하고 있어요.'}
          <br />자동으로 닫히지 않으면 결과를 확인한 뒤 이 창을 닫아 주세요.
        </p>
        {!received && !unavailable && (
          <button className="primary-button full-width" onClick={() => {
            if (notification.current) channel.current?.postMessage(notification.current);
          }}>결과 다시 전달</button>
        )}
        <button className="secondary-button full-width" onClick={() => window.close()}>창 닫기</button>
      </section>
    </main>
  );
}
