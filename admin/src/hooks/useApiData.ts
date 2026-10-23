import { useEffect, useState } from 'react';
import { apiErrorMessage } from '../api/apiClient';

type Request<T> = (signal: AbortSignal) => Promise<T>;

/** Pass a useCallback request; null pauses loading. Old requests are cancelled and discarded. */
export default function useApiData<T>(request: Request<T> | null) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{
    request: Request<T> | null; attempt: number; data: T | null; loading: boolean; error: string;
  }>({ request: null, attempt: -1, data: null, loading: false, error: '' });

  useEffect(() => {
    if (!request) return;
    const controller = new AbortController();
    setState({ request, attempt, data: null, loading: true, error: '' });
    void request(controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ request, attempt, data, loading: false, error: '' });
    }).catch(error => {
      if (!controller.signal.aborted) {
        setState({ request, attempt, data: null, loading: false, error: apiErrorMessage(error) });
      }
    });
    return () => controller.abort();
  }, [request, attempt]);

  const current = state.request === request && state.attempt === attempt;
  return {
    data: current ? state.data : null,
    error: current ? state.error : '',
    loading: Boolean(request) && (!current || state.loading),
    retry: () => setAttempt(value => value + 1),
  };
}
