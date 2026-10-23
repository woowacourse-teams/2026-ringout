export class ApiError extends Error {
  constructor(public status: number, public code: string, message = code) {
    super(message);
  }
}

type SessionListener = (error: ApiError) => void;
const sessionListeners = new Set<SessionListener>();

export function onSessionFailure(listener: SessionListener) {
  sessionListeners.add(listener);
  return () => { sessionListeners.delete(listener); };
}

/** Same-origin proxy preserves the server session; authentication is decided by the server. */
export async function apiRequest(path: string, options: RequestInit = {}, reportSession = true): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  const response = await fetch(`/api/v1/admin${path}`, {
    ...options, headers, credentials: 'include', cache: 'no-store',
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const error = new ApiError(response.status, body.code ?? 'ADMIN_REQUEST_FAILED', body.message);
    if (reportSession && !options.signal?.aborted && [401, 403].includes(response.status)) {
      sessionListeners.forEach(listener => listener(error));
    }
    throw error;
  }
  return response;
}

export async function apiResult<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await apiRequest(path, { signal });
  const body = await response.json();
  if (body.isSuccess !== true || !Object.hasOwn(body, 'result')) {
    throw new Error('서버 응답 형식을 확인할 수 없습니다.');
  }
  return body.result as T;
}

export function apiErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return '세션이 만료되었습니다. 다시 로그인해 주세요.';
    if (error.status === 403) return '조회 권한이 없습니다. 관리자 승인 상태를 확인해 주세요.';
    if (error.status === 404) return '요청한 데이터를 찾을 수 없습니다.';
    if (error.status === 400) return '검색 조건을 확인해 주세요.';
  }
  return '데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}
