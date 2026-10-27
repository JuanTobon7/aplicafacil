import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { displayName } from "../api/auth";
import { useAuth } from "../auth/AuthContext";
import { useComputrabajoSessionCheck } from "../hooks/useComputrabajoSessionCheck";
import { useLinkedInSessionCheck } from "../hooks/useLinkedInSessionCheck";

export function Layout() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  useLinkedInSessionCheck(Boolean(auth.user));
  const computrabajoExpired = useComputrabajoSessionCheck(Boolean(auth.user));
  return (
    <>
      <header className="topbar">
        <NavLink to="/" className="brand">AplicaFácil</NavLink>
        {auth.user && (
          <nav>
            <NavLink to="/perfil">Mi perfil</NavLink>
            <NavLink to="/vacantes">Vacantes</NavLink>
            <NavLink to="/ia">Proveedores de IA</NavLink>
            <span className="muted">{displayName(auth.user)}</span>
            <button className="link" onClick={() => void auth.logout().then(() => navigate("/"))}>Cambiar de cuenta</button>
          </nav>
        )}
      </header>
      <main>
        {computrabajoExpired && location.pathname !== "/perfil" && (
          <p className="card warning" role="status">
            Tu sesión de Computrabajo venció. <Link to="/perfil">Inicia sesión de nuevo</Link> para guardarla.
          </p>
        )}
        <Outlet />
      </main>
    </>
  );
}
