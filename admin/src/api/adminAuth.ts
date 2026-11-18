import { apiRequest, ApiError } from './apiClient';
export { ApiError as AdminAuthError } from './apiClient';
export type Admin = { id: number; email: string; name: string | null };
export const adminAuthPath = '/api/v1/admin/auth';

const AdminAuthError = ApiError;

function request(path: string, options: RequestInit = {}) {
  return apiRequest(`/auth${path}`, options, false);
}

export async function getAdmin(signal?: AbortSignal): Promise<Admin | null> {
  try {
    const response = await request('/me', { signal });
    const body = await response.json();
    if (typeof body.id !== 'number' || typeof body.email !== 'string') {
      throw new Error('관리자 정보를 확인할 수 없습니다. API 프록시 설정을 확인해 주세요.');
    }
    return { id: body.id, email: body.email, name: body.name ?? null };
  } catch (error) {
    if (error instanceof AdminAuthError && error.status === 401) return null;
    throw error;
  }
}

export async function logoutAdmin() {
  const response = await request('/csrf');
  const csrf = await response.json();
  if (typeof csrf.headerName !== 'string' || typeof csrf.token !== 'string') {
    throw new Error('로그아웃 보안 토큰을 확인할 수 없습니다.');
  }
  await request('/logout', { method: 'POST', headers: { [csrf.headerName]: csrf.token } });
}

export function authErrorMessage(error: unknown): string {
  if (error instanceof AdminAuthError) {
    switch (error.code) {
      case 'ADMIN_APPROVAL_PENDING': return '관리자 승인 대기 중입니다. 승인 후 다시 로그인해 주세요.';
      case 'ADMIN_ACCESS_REVOKED':
      case 'ADMIN_ACCESS_DENIED': return '관리자 접근 권한이 없습니다. 관리자에게 승인을 확인해 주세요.';
      case 'ADMIN_GOOGLE_LOGIN_FAILED':
      case 'ADMIN_INVALID_GOOGLE_IDENTITY': return '구글 로그인에 실패했습니다. 다시 시도해 주세요.';
    }
    if (error.status === 503) return '서버의 구글 관리자 로그인 설정이 준비되지 않았습니다.';
  }
  return '인증 서버에 연결할 수 없습니다. 서버 실행 상태와 API 프록시 설정을 확인해 주세요.';
}
