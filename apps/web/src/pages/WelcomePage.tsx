import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { authApi, type Account } from "../api/auth";
import { ApiError } from "../api/http";
import { useAuth } from "../auth/AuthContext";

const STATUS_LABEL: Record<Account["connectionStatus"], string> = {
  CONNECTED: "Conectada",
  CONNECTING: "Conectando",
  EXPIRED: "Sesión expirada",
  DISCONNECTED: "Desconectada",
  UNKNOWN: "Estado desconocido",
};

/**
 * Bienvenida: "Continuar como…" para cada perfil de LinkedIn de esta instalación y
 * "Agregar otra cuenta de LinkedIn". El botón OAuth solo aparece si está activado en el backend.
 */
const BADGE: Partial<Record<Account["connectionStatus"], string>> = { CONNECTED: "active", EXPIRED: "expired" };

function AccountItem({ account: a, busy, onContinue }: { account: Account; busy: string | null; onContinue: () => void }) {
  return (
    <li>
      {a.photoUrl ? (
        <img src={a.photoUrl} alt="" width={48} height={48} referrerPolicy="no-referrer" />
      ) : (
        <span className="avatar" aria-hidden>{a.displayName.charAt(0)}</span>
      )}
      <div className="grow">
        <strong>{a.displayName}</strong>
        {a.headline && <div className="muted small">{a.headline}</div>}
        <span className={`badge ${BADGE[a.connectionStatus] ?? ""}`}>{STATUS_LABEL[a.connectionStatus]}</span>
      </div>
      <div className="actions">
        <button onClick={onContinue} disabled={busy !== null}>
          {busy === a.userId ? "Verificando sesión…" : "Continuar"}
        </button>
      </div>
    </li>
  );
}

type LoginFailure = { message: string; reconnect?: boolean };

/** Si la sesión guardada venció o no existe, se ofrece reconectar. */
function loginError(e: unknown): LoginFailure {
  if (!(e instanceof ApiError)) return { message: "No se pudo iniciar sesión." };
  return { message: e.message, reconnect: e.code === "SESSION_EXPIRED" || e.code === "NOT_CONNECTED" };
}

function LoginError({ error }: { error: LoginFailure }) {
  return (
    <div role="alert">
      <p className="error">{error.message}</p>
      {error.reconnect && <Link to="/conectar" className="button secondary">Reconectar LinkedIn</Link>}
    </div>
  );
}

function EntryActions({ hasAccounts, oauthEnabled }: { hasAccounts: boolean; oauthEnabled: boolean }) {
  return (
    <div className="actions">
      <Link to="/conectar" className={hasAccounts ? "button secondary" : "button"}>
        {hasAccounts ? "Agregar otra cuenta de LinkedIn" : "Conectar mi cuenta de LinkedIn"}
      </Link>
      {oauthEnabled && (
        // Fase desplegada: el backend redirige a LinkedIn y vuelve a /auth/callback sin tokens en la URL
        <a className="button secondary" href="/oauth2/authorization/linkedin">Iniciar sesión con LinkedIn</a>
      )}
    </div>
  );
}

export function WelcomePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState<Account[] | null>(null);
  const [oauthEnabled, setOauthEnabled] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<LoginFailure | null>(null);

  useEffect(() => {
    authApi.accounts().then(setAccounts).catch((e) => {
      setAccounts([]);
      setError({ message: e instanceof ApiError ? e.message : "No se pudieron cargar las cuentas." });
    });
    authApi.config().then((c) => setOauthEnabled(c.oauthEnabled)).catch(() => undefined);
  }, []);

  if (auth.ready && auth.user) return <Navigate to="/perfil" replace />;

  const continueAs = async (account: Account) => {
    setBusy(account.userId);
    setError(null);
    try {
      auth.start(await authApi.continueAs(account.userId));
      navigate("/perfil");
    } catch (e) {
      setError(loginError(e));
    } finally {
      setBusy(null);
    }
  };

  return (
    <section className="card">
      <h1>Postúlate a empleos sin repetir formularios</h1>
      <p>Tu cuenta de LinkedIn es tu cuenta en AplicaFácil. No hay contraseñas propias.</p>

      {accounts === null && <p className="muted">Cargando cuentas…</p>}

      {!!accounts?.length && (
        <>
          <h2 className="small muted">Continuar como…</h2>
          <ul className="accounts">
            {accounts.map((a) => (
              <AccountItem key={a.userId} account={a} busy={busy} onContinue={() => void continueAs(a)} />
            ))}
          </ul>
        </>
      )}

      {error && <LoginError error={error} />}
      <EntryActions hasAccounts={(accounts?.length ?? 0) > 0} oauthEnabled={oauthEnabled} />
    </section>
  );
}
