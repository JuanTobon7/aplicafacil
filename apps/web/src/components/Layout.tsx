import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import type { User } from "../api/auth";
import { useAuth } from "../auth/AuthContext";
import { useComputrabajoSessionCheck } from "../hooks/useComputrabajoSessionCheck";
import { useLinkedInSessionCheck } from "../hooks/useLinkedInSessionCheck";
import { Icon } from "./Icon";
import { Sidebar } from "./Sidebar";
import { ThemeToggle } from "./ThemeToggle";

/** Con sesión: sidebar y la vista elegida. Sin sesión (bienvenida, conectar): solo la marca y el tema. */
export function Layout() {
  const auth = useAuth();
  useLinkedInSessionCheck(Boolean(auth.user));
  if (!auth.user) return <PublicLayout />;
  return <AppShell user={auth.user} onLogout={auth.logout} />;
}

function PublicLayout() {
  return (
    <div className="public">
      <header className="public-bar">
        <NavLink to="/" className="brand">
          <span className="brand-mark" aria-hidden>AF</span>
          AplicaFácil
        </NavLink>
        <ThemeToggle />
      </header>
      <main className="public-main">
        <Outlet />
      </main>
    </div>
  );
}

function AppShell({ user, onLogout }: { user: User; onLogout: () => Promise<unknown> }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const computrabajoExpired = useComputrabajoSessionCheck(true);
  useEffect(() => setMenuOpen(false), [location.pathname]);

  return (
    <div className="app">
      <Sidebar user={user} open={menuOpen} onNavigate={() => setMenuOpen(false)} onLogout={onLogout} />
      {menuOpen && <button className="scrim" aria-label="Cerrar menú" onClick={() => setMenuOpen(false)} />}
      <div className="app-content">
        <header className="mobile-bar">
          <button className="icon-button" aria-label="Abrir menú" aria-expanded={menuOpen} onClick={() => setMenuOpen(true)}>
            <Icon name="menu" size={22} />
          </button>
          <span className="brand"><span className="brand-mark" aria-hidden>AF</span>AplicaFácil</span>
        </header>
        <main>
          {computrabajoExpired && location.pathname !== "/cuentas" && (
            <p className="notice" role="status">
              Tu sesión de Computrabajo venció. <Link to="/cuentas">Inicia sesión de nuevo</Link> para guardarla.
            </p>
          )}
          <Outlet />
        </main>
      </div>
    </div>
  );
}
