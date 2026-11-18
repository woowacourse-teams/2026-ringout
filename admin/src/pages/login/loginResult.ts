export const loginChannelName = 'ringout-admin-login';
const loginErrors = new Set([
  'ADMIN_APPROVAL_PENDING',
  'ADMIN_ACCESS_REVOKED',
  'ADMIN_ACCESS_DENIED',
  'ADMIN_GOOGLE_LOGIN_FAILED',
  'ADMIN_INVALID_GOOGLE_IDENTITY',
  'ADMIN_LOGIN_UNAVAILABLE',
]);

export type LoginNotification = {
  type: 'ringout-admin-login-result';
  id: string;
  error: string;
  sentAt: number;
};

export function readLoginError(query: string) {
  const error = new URLSearchParams(query).get('error') ?? '';
  return error && !loginErrors.has(error) ? 'ADMIN_GOOGLE_LOGIN_FAILED' : error;
}

export function isLoginNotification(value: unknown): value is LoginNotification {
  if (!value || typeof value !== 'object') return false;
  const message = value as Partial<LoginNotification>;
  return message.type === 'ringout-admin-login-result'
    && typeof message.id === 'string'
    && typeof message.sentAt === 'number'
    && Number.isFinite(message.sentAt)
    && typeof message.error === 'string'
    && (message.error === '' || loginErrors.has(message.error));
}
