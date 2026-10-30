import { useState, type ReactNode } from 'react';
import {
  LayoutDashboard,
  Users,
  UsersRound,
  BellRing,
  History,
  Rocket,
  LockKeyhole,
  Monitor,
  ArrowUpRight,
  Menu,
  X,
  CircleHelp,
  ShieldCheck,
  LogOut,
} from 'lucide-react';
import type { Admin } from '../api/adminAuth';
import type { Page } from '../types';
import Dialog from '../components/Dialog';
import { adminConfig } from '../config';
export const navigation = [{
  id: 'dashboard' as Page,
  inDevelopment: true,
  label: '대시보드',
  icon: LayoutDashboard
}, {
  id: 'users' as Page,
  inDevelopment: false,
  label: '회원 관리',
  icon: Users
}, {
  id: 'rooms' as Page,
  inDevelopment: false,
  label: '모임 관리',
  icon: UsersRound
}, {
  id: 'alarms' as Page,
  inDevelopment: true,
  label: '알람 실행 기록',
  icon: BellRing
}, {
  id: 'deployments' as Page,
  inDevelopment: false,
  label: '배포 관리',
  icon: Rocket
}, {
  id: 'logs' as Page,
  inDevelopment: true,
  label: '변경 이력',
  icon: History
}];

type Props = {
  admin: Admin;
  loggingOut: boolean;
  onLogout: () => void;
  page: Page;
  navigate: (page: Page) => void;
  children: ReactNode;
};

export default function AdminLayout({
  page,
  admin,
  loggingOut,
  onLogout,
  navigate: onNavigate,
  children
}: Props) {
  const [mobile, setMobile] = useState(false);
  const [help, setHelp] = useState(false);
  function navigate(page: Page) {
    setMobile(false);
    onNavigate(page);
  }
  return <div className="app-shell">
    <aside className={`sidebar ${mobile ? 'mobile-open' : ''}`}>
      <a
        href="#dashboard"
        className="brand"
        onClick={() => navigate('dashboard')}
      >
        <img src="/ringout-logo.png" alt="링아웃 로고" />
        <span>ringout<span className="brand-dot">.</span></span>
        <small>admin</small>
      </a>
      <button
        className="mobile-close icon-button"
        aria-label="메뉴 닫기"
        onClick={() => setMobile(false)}
      >
        <X />
      </button>
      <div className="workspace-label">WORKSPACE</div>
      <nav aria-label="관리자 메뉴">
        {navigation.map(n => <button
          key={n.id}
          aria-current={page === n.id ? 'page' : undefined}
          className={page === n.id ? 'nav-item selected' : 'nav-item'}
          title={n.inDevelopment ? '개발 중' : undefined}
          onClick={() => navigate(n.id)}
        >
          <n.icon size={19} />
          <span>{n.label}</span>
          {n.inDevelopment && <small className="nav-development-badge">개발 중</small>}
        </button>)}
      </nav>
      <div className="sidebar-bottom">
        <div className="help-card">
          <span className="help-card-icon">
            <CircleHelp size={21} />
          </span>
          <strong>운영이 처음이신가요?</strong>
          <p>관리자 화면을 가볍게 둘러보세요.</p>
          <button onClick={() => setHelp(true)}>사용 가이드 <ArrowUpRight size={14} /></button>
        </div>
        <div className="sidebar-footer">
          <span className="admin-avatar">R</span>
          <div>
            <strong>{admin.name || admin.email}</strong>
            <small title={admin.email}>{admin.email}</small>
          </div>
          <ShieldCheck size={17} />
        </div>
      </div>
    </aside>
    {mobile && <button
      aria-label="메뉴 배경 닫기"
      className="mobile-overlay"
      onClick={() => setMobile(false)}
    />}
    <div className="main-shell">
      <header className="topbar">
        <div className="breadcrumb">
          <button
            className="mobile-menu icon-button"
            aria-label="메뉴 열기"
            onClick={() => setMobile(true)}
          >
            <Menu size={20} />
          </button>
          <span>워크스페이스</span>
          <span className="slash">/</span>
          <strong>{navigation.find(n => n.id === page)?.label}</strong>
        </div>
        <div className="topbar-right">
          <button className="logout-button" onClick={onLogout} disabled={loggingOut}>
            <LogOut size={14} />{loggingOut ? '로그아웃 중' : '로그아웃'}
          </button>
          <span
            className={`environment-badge ${adminConfig.environment}`}
            title={adminConfig.apiBaseUrl}
          >
            {adminConfig.environment === 'local' ? <Monitor size={14} /> : <LockKeyhole size={14} />}
            {adminConfig.label}
          </span>
        </div>
      </header>
      <main>
        <div className="content-wrap">
          {children}
          <footer className="content-footer">
            <span>© 2026 RingOut. 작은 시작, 새로운 하루.</span>
            <span>관리자 웹 <span className="footer-separator">·</span> v0.1.0</span>
          </footer>
        </div>
      </main>
    </div>
    {help && <Dialog title="링아웃 관리자 둘러보기" onClose={() => setHelp(false)}>
      <div className="guide-list">
        <p><strong>01 · 검색과 상세</strong>회원·모임 메뉴에서 검색, 필터, 상세 조회를 사용할 수 있어요.</p>
        <p><strong>02 · 모임 기록</strong>모임 상세에서 사진·소개·일정과 멤버 오늘 기록을 확인하세요. 모임 숨김·공개 변경은 1차 범위에 포함하지 않습니다.</p>
        <p><strong>03 · 준비 중인 화면</strong>대시보드·알람 실행 기록·변경 이력은 개발 중으로 안내만 표시합니다.</p>
      </div>
      <p className="dialog-description">로그인은 구글 관리자 세션을 사용하며 회원·모임 정보와 모임별 기록은 서버 API에서 조회합니다.</p>
      <button className="primary-button full-width" onClick={() => setHelp(false)}>둘러보기 시작</button>
    </Dialog>}
  </div>;
}
