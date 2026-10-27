import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router";
import { authApi, displayName } from "../api/auth";
import { ApiError } from "../api/http";
import { linkedinApi, type StatusView } from "../api/linkedin";
import type { User } from "../api/auth";
import { useAuth } from "../auth/AuthContext";
import { ComputrabajoCard } from "../components/profile/ComputrabajoCard";
import { ProfileWork } from "../components/profile/ProfileWork";

/**
 * Datos básicos y estado de la conexión con LinkedIn (SSE con polling de respaldo), con desconectar y eliminar cuenta;
 * la conexión con Computrabajo; debajo, el
 * perfil laboral (importar CV, búsqueda sugerida por la IA y secciones).
 */
export function ProfilePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const user = auth.user!;
  const [status, setStatus] = useState<StatusView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => linkedinApi.followStatus(setStatus), []);

  const disconnect = async () => {
    try {
      await linkedinApi.disconnect();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudo desconectar.");
    }
  };

  const deleteAccount = async () => {
    if (!window.confirm("Se borrarán tu usuario, perfil, sesiones de LinkedIn y Computrabajo, y postulaciones. ¿Continuar?")) return;
    try {
      await authApi.deleteMe();
      auth.clear();
      navigate("/", { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudo eliminar la cuenta.");
    }
  };

  return (
    <>
      <section className="card">
        <UserHeader user={user} />
        <ConnectionState status={status} />
        {error && <p className="error" role="alert">{error}</p>}
        <div className="actions">
          <ConnectionAction status={status} onDisconnect={() => void disconnect()} />
          <button className="danger" onClick={() => void deleteAccount()}>Eliminar mi cuenta y mis datos</button>
        </div>
      </section>
      <ComputrabajoCard />
      <ProfileWork />
    </>
  );
}

function UserHeader({ user }: { user: User }) {
  return (
    <>
      <h1>{displayName(user)}</h1>
      {user.headline && <p>{user.headline}</p>}
      {user.publicProfileUrl && (
        <p><a href={user.publicProfileUrl} target="_blank" rel="noreferrer">Ver perfil en LinkedIn</a></p>
      )}
    </>
  );
}

function ConnectionState({ status }: { status: StatusView | null }) {
  return (
    <>
      <h2 className="small muted">Conexión con LinkedIn</h2>
      <p>
        Estado: <strong>{status?.status ?? "…"}</strong>
        {status?.lastSyncAt && <span className="muted small"> · última sincronización {new Date(status.lastSyncAt).toLocaleString()}</span>}
      </p>
      {status?.lastError === "PROFILE_INCOMPLETE" && <p className="muted small">El perfil quedó incompleto; se completará al sincronizar de nuevo.</p>}
    </>
  );
}

/** Reconectar si venció o no hay conexión; desconectar si está conectada. */
function ConnectionAction({ status, onDisconnect }: { status: StatusView | null; onDisconnect: () => void }) {
  if (status?.status === "EXPIRED" || status?.status === "DISCONNECTED") {
    return <Link className="button" to="/conectar">Reconectar LinkedIn</Link>;
  }
  if (status?.status === "CONNECTED") return <button className="secondary" onClick={onDisconnect}>Desconectar</button>;
  return null;
}
