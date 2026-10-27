import type { ReactNode } from "react";
import { Navigate } from "react-router";
import { useAuth } from "./AuthContext";

/** Sin sesión en la plataforma se vuelve a la bienvenida (selector de cuentas). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { ready, user } = useAuth();
  if (!ready) return <p className="muted">Cargando…</p>;
  if (!user) return <Navigate to="/" replace />;
  return <>{children}</>;
}
