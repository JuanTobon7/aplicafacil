import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { authApi, type Session, type User } from "../api/auth";
import { onUnauthorized, setAccessToken } from "../api/http";

type AuthState = {
  /** null mientras se intenta recuperar la sesión con la cookie de refresh. */
  ready: boolean;
  user: User | null;
  start: (session: Session) => void;
  refresh: () => Promise<boolean>;
  logout: () => Promise<void>;
  clear: () => void;
};

const AuthContext = createContext<AuthState | null>(null);

/**
 * Sesión en la plataforma. El access token vive solo en memoria; al recargar la página se recupera con la
 * cookie httpOnly `af_rt` (POST /auth/refresh). Se renueva un minuto antes de vencer.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  const timer = useRef<number | undefined>(undefined);
  const inflight = useRef<Promise<boolean> | null>(null);

  const clear = useCallback(() => {
    window.clearTimeout(timer.current);
    setAccessToken(null);
    setUser(null);
  }, []);

  const start = useCallback((session: Session) => {
    setAccessToken(session.accessToken);
    setUser(session.user);
    window.clearTimeout(timer.current);
    const ms = new Date(session.expiresAt).getTime() - Date.now() - 60_000;
    timer.current = window.setTimeout(() => void refreshRef.current(), Math.max(ms, 5_000));
  }, []);

  const refresh = useCallback(async () => {
    // Varias peticiones con 401 a la vez comparten un solo refresh (el token rota en cada uso)
    inflight.current ??= authApi
      .refresh()
      .then((session) => {
        start(session);
        return true;
      })
      .catch(() => {
        clear();
        return false;
      })
      .finally(() => {
        inflight.current = null;
      });
    return inflight.current;
  }, [start, clear]);

  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;

  const logout = useCallback(async () => {
    await authApi.logout().catch(() => undefined);
    clear();
  }, [clear]);

  useEffect(() => {
    onUnauthorized(() => refreshRef.current());
    void refresh().finally(() => setReady(true));
    return () => onUnauthorized(null);
  }, [refresh]);

  const value = useMemo(() => ({ ready, user, start, refresh, logout, clear }), [ready, user, start, refresh, logout, clear]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth fuera de AuthProvider");
  return ctx;
}
