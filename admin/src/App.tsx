import { useEffect, useState } from 'react';
import type { Page } from './types';
import AdminLayout, { navigation } from './layouts/AdminLayout';
import UsersPage from './pages/users/UsersPage';
import RoomsPage from './pages/rooms/RoomsPage';
import useAdminAuth from './hooks/useAdminAuth';
import type { Admin } from './api/adminAuth';
import ApprovalPendingPage from './pages/login/ApprovalPendingPage';
import LoginCallbackPage from './pages/login/LoginCallbackPage';
import LoginPage from './pages/login/LoginPage';
import DevelopmentPreview from './components/DevelopmentPreview';

function readRoute() {
  const [path, query = ''] = location.hash.slice(1).split('?');
  const page: Page = navigation.some(item => item.id === path) ? path as Page : 'dashboard';
  return {
    page,
    member: new URLSearchParams(query).get('member') ?? ''
  };
}

function AdminWorkspace({ admin, onLogout, loggingOut, authError }: {
  admin: Admin;
  onLogout: () => void;
  loggingOut: boolean;
  authError: string;
}) {
  const [route, setRoute] = useState(readRoute);
  useEffect(() => {
    const onHashChange = () => setRoute(readRoute());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);
  function navigate(page: Page, member = '') {
    const query = member ? `?${new URLSearchParams({
      member
    })}` : '';
    location.hash = `${page}${query}`;
    setRoute({
      page,
      member
    });
  }
  let content;
  switch (route.page) {
    case 'users':
      content = <UsersPage />;
      break;
    case 'rooms':
      content = <RoomsPage />;
      break;
    default:
      content = <DevelopmentPreview title={navigation.find(item => item.id === route.page)!.label} />;
  }

  return <>
    <AdminLayout
      admin={admin}
      onLogout={onLogout}
      loggingOut={loggingOut}
      page={route.page}
      navigate={navigate}
    >
      {authError && <p className="login-error-panel" role="alert">{authError}</p>}
      <div key={`${route.page}-${route.member}`}>
        {content}
      </div>
    </AdminLayout>
  </>;
}

function AdminApp() {
  const auth = useAdminAuth();

  if (!auth.admin && auth.approvalPending) {
    return (
      <ApprovalPendingPage
        onLogin={() => void auth.login()}
        onCancelLogin={auth.cancelLogin}
        loggingIn={auth.loggingIn}
        error={auth.error}
      />
    );
  }

  if (!auth.admin) {
    return (
      <LoginPage
        onLogin={() => void auth.login()}
        onRetry={() => void auth.retry()}
        checking={auth.checking}
        onCancelLogin={auth.cancelLogin}
        loggingIn={auth.loggingIn}
        error={auth.error}
      />
    );
  }

  return (
    <AdminWorkspace
      admin={auth.admin}
      onLogout={() => void auth.logout()}
      loggingOut={auth.loggingOut}
      authError={auth.error}
    />
  );
}

export default function App() {
  if (window.location.hash.split('?')[0] === '#auth-result') {
    return <LoginCallbackPage />;
  }
  return <AdminApp />;
}
