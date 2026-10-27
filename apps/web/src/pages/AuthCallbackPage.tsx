import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { useAuth } from "../auth/AuthContext";

/**
 * Regreso del flujo OAuth 2.0 de LinkedIn (fase desplegada). El backend ya dejó la cookie de refresh;
 * la URL solo trae ?status=ok|error, nunca tokens.
 */
export function AuthCallbackPage() {
  const [params] = useSearchParams();
  const auth = useAuth();
  const navigate = useNavigate();
  const [failed, setFailed] = useState(params.get("status") !== "ok");

  useEffect(() => {
    if (params.get("status") !== "ok") return;
    void auth.refresh().then((ok) => (ok ? navigate("/perfil", { replace: true }) : setFailed(true)));
  }, [params, auth, navigate]);

  if (!failed) return <p className="muted">Completando inicio de sesión…</p>;
  return (
    <section className="card">
      <h1>No se pudo iniciar sesión</h1>
      <p>{params.get("message") ?? "LinkedIn no completó la autorización."}</p>
      <Link className="button" to="/">Volver</Link>
    </section>
  );
}
