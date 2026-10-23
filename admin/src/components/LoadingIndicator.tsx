import { LoaderCircle } from 'lucide-react';

export default function LoadingIndicator() {
  return (
    <div className="loading-indicator" role="status" aria-label="로딩 중">
      <LoaderCircle className="auth-spinner" size={24} strokeWidth={2} aria-hidden="true" />
    </div>
  );
}
