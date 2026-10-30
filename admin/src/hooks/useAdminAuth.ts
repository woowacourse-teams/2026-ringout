import { useCallback, useEffect, useRef, useState } from 'react';
import { onSessionFailure } from '../api/apiClient';
import { adminAuthPath, AdminAuthError, authErrorMessage, getAdmin, logoutAdmin, type Admin } from '../api/adminAuth';
import { isLoginNotification, loginChannelName, readLoginError } from '../pages/login/loginResult';

type LoginAttempt = {
  startedAt: number;
  channel: BroadcastChannel;
  timer: number;
  controller: AbortController;
  handling: boolean;
};

function readLoginResult() {
  const [page, query = ''] = window.location.hash.slice(1).split('?');
  const code = page === 'login' ? readLoginError(query) : '';
  return {
    pending: code === 'ADMIN_APPROVAL_PENDING',
    message: code && code !== 'ADMIN_APPROVAL_PENDING'
      ? authErrorMessage(new AdminAuthError(code === 'ADMIN_LOGIN_UNAVAILABLE' ? 503 : 403, code))
      : '',
  };
}

/** 같은 출처의 결과 알림은 계기로만 사용하고 인증은 서버 세션으로 검증합니다. */
export default function useAdminAuth() {
  const loginResult = useRef(readLoginResult());
  const [admin, setAdmin] = useState<Admin | null>(null);
  const [checking, setChecking] = useState(true);
  const [loggingIn, setLoggingIn] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [error, setError] = useState(loginResult.current.message);
  const [approvalPending, setApprovalPending] = useState(loginResult.current.pending);
  const sessionVersion = useRef(0);
  const loginAttempt = useRef<LoginAttempt | null>(null);
  const logoutInFlight = useRef(false);

  const closeAttempt = useCallback(() => {
    const attempt = loginAttempt.current;
    if (!attempt) return;
    loginAttempt.current = null;
    window.clearTimeout(attempt.timer);
    attempt.controller.abort();
    attempt.channel.close();
  }, []);

  const checkSession = useCallback(async (signal?: AbortSignal) => {
    if (loginAttempt.current || logoutInFlight.current) return;
    const version = ++sessionVersion.current;
    try {
      const current = await getAdmin(signal);
      if (!signal?.aborted && version === sessionVersion.current) {
        setAdmin(current);
        if (current) {
          setApprovalPending(false);
          loginResult.current = { pending: false, message: '' };
          if (window.location.hash.startsWith('#login')) window.location.hash = 'dashboard';
        }
        setError(current ? '' : loginResult.current.message);
      }
    } catch (failure) {
      if (!signal?.aborted && version === sessionVersion.current) {
        // Transport/server errors do not prove that an existing session is invalid.
        if (failure instanceof AdminAuthError && [401, 403].includes(failure.status)) {
          setAdmin(null);
        }
        setError(authErrorMessage(failure));
      }
    } finally {
      if (!signal?.aborted && version === sessionVersion.current) setChecking(false);
    }
  }, []);

  useEffect(() => onSessionFailure(() => {
    if (loginAttempt.current || logoutInFlight.current) return;
    // A resource endpoint can return 401/403 for reasons other than session expiry.
    // Only the session endpoint decides whether the current admin is still signed in.
    void checkSession();
  }), [checkSession]);

  useEffect(() => {
    const controller = new AbortController();
    void checkSession(controller.signal);
    const onFocus = () => {
      if (document.visibilityState === 'visible') void checkSession(controller.signal);
    };
    window.addEventListener('focus', onFocus);
    return () => {
      controller.abort();
      closeAttempt();
      window.removeEventListener('focus', onFocus);
    };
  }, [checkSession, closeAttempt]);

  function cancelLogin() {
    closeAttempt();
    setLoggingIn(false);
    setError('로그인 대기를 취소했습니다. 다시 로그인할 수 있습니다.');
  }

  function login() {
    if (loginAttempt.current) return;
    if (!('BroadcastChannel' in window)) {
      setError('이 브라우저는 팝업 로그인 연결을 지원하지 않습니다. 최신 브라우저를 사용해 주세요.');
      return;
    }
    const channel = new BroadcastChannel(loginChannelName);
    const attempt: LoginAttempt = {
      startedAt: Date.now(), channel, timer: 0, controller: new AbortController(), handling: false,
    };
    loginAttempt.current = attempt;
    ++sessionVersion.current;
    setError('');
    setLoggingIn(true);
    channel.onmessage = async event => {
      const message: unknown = event.data;
      if (!isLoginNotification(message) || message.sentAt < attempt.startedAt ||
          loginAttempt.current !== attempt || attempt.handling) return;
      attempt.handling = true;
      try {
        // 같은 출처 메시지도 로그인 증명으로 신뢰하지 않습니다.
        const current = await getAdmin(attempt.controller.signal);
        if (attempt.controller.signal.aborted || loginAttempt.current !== attempt) return;
        if (current) {
          setApprovalPending(false);
          loginResult.current = { pending: false, message: '' };
          window.location.hash = 'dashboard';
          setAdmin(current);
          setError('');
        } else if (message.error === 'ADMIN_APPROVAL_PENDING') {
          setApprovalPending(true);
          setError('');
        } else {
          setError(message.error
            ? authErrorMessage(new AdminAuthError(message.error === 'ADMIN_LOGIN_UNAVAILABLE' ? 503 : 403, message.error))
            : '로그인 세션을 확인하지 못했습니다. 다시 로그인해 주세요.');
        }
      } catch (failure) {
        if (!attempt.controller.signal.aborted) setError(authErrorMessage(failure));
      } finally {
        if (loginAttempt.current === attempt) {
          channel.postMessage({ type: 'ringout-admin-login-received', id: message.id });
          closeAttempt();
          setLoggingIn(false);
        }
      }
    };
    // 팝업 핸들의 주소·문서·closed에 접근하지 않습니다. 결과 페이지가 스스로 닫힙니다.
    const popup = window.open(`${adminAuthPath}/login`, 'ringout-admin-google', 'popup,width=520,height=720');
    if (!popup) {
      closeAttempt();
      setLoggingIn(false);
      setError('이 사이트의 팝업을 허용한 뒤 다시 로그인해 주세요.');
      return;
    }
    attempt.timer = window.setTimeout(() => {
      if (loginAttempt.current === attempt) {
        closeAttempt();
        setLoggingIn(false);
        setError('로그인 대기 시간이 지났습니다. 다시 시도해 주세요.');
      }
    }, 180_000);
  }

  async function logout() {
    if (logoutInFlight.current) return;
    logoutInFlight.current = true;
    ++sessionVersion.current;
    setLoggingOut(true);
    setError('');
    try {
      await logoutAdmin();
      loginResult.current = { pending: false, message: '' };
      setApprovalPending(false);
      setAdmin(null);
      window.location.hash = 'login';
    } catch (failure) {
      logoutInFlight.current = false;
      if (failure instanceof AdminAuthError && [401, 403].includes(failure.status)) await checkSession();
      setError('로그아웃하지 못했습니다. 다시 시도해 주세요.');
    } finally {
      logoutInFlight.current = false;
      setLoggingOut(false);
    }
  }

  return { admin, approvalPending, checking, loggingIn, loggingOut, error, login, cancelLogin, logout, retry: checkSession };
}
