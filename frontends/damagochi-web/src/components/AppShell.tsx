import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../store/auth";

export function AppShell() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const onLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/app" className="brand">
          Damagochi
        </Link>
        <div className="topbar-right">
          <span className="nickname">{user?.nickname} 님</span>
          <button onClick={onLogout} className="ghost-btn">
            로그아웃
          </button>
        </div>
      </header>

      <div className="layout">
        <aside className="sidebar">
          <NavLink to="/app" end className={({ isActive }) => (isActive ? "active" : "")}>생활</NavLink>
          <NavLink to="/app/creatures" className={({ isActive }) => (isActive ? "active" : "")}>크리처</NavLink>
          <NavLink to="/app/battle" className={({ isActive }) => (isActive ? "active" : "")}>배틀</NavLink>
          <NavLink to="/app/settings" className={({ isActive }) => (isActive ? "active" : "")}>설정</NavLink>
        </aside>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
