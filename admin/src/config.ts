export type AppEnvironment = 'local' | 'production';

export function createAdminConfig(environment: string | undefined, apiBaseUrl: string | undefined) {
  if (environment !== 'local' && environment !== 'production') {
    throw new Error('VITE_APP_ENV는 local 또는 production으로 설정해 주세요.');
  }

  if (!apiBaseUrl?.trim()) {
    throw new Error('VITE_API_BASE_URL을 설정해 주세요.');
  }

  const url = new URL(apiBaseUrl.trim());
  if (!['http:', 'https:'].includes(url.protocol)) {
    throw new Error('API 주소는 http 또는 https를 사용해야 합니다.');
  }

  return {
    environment,
    label: environment === 'local' ? '로컬' : '운영',
    apiBaseUrl: apiBaseUrl.trim().replace(/\/+$/, ''),
  };
}

/** 빌드에 주입된 공개 설정입니다. 토큰·비밀키는 포함하지 않습니다. */
export const adminConfig = createAdminConfig(
  import.meta.env.VITE_APP_ENV,
  import.meta.env.VITE_API_BASE_URL,
);

// 인증은 같은 출처의 /api/v1/admin 경로로 요청합니다. API 서버 주소는 로컬 프록시·배포 원본 설정에 사용합니다.
