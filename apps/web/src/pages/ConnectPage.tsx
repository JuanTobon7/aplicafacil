import { useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { ConnectionProgress } from "../components/ConnectionProgress";
import type { ReconnectReason } from "../hooks/useLinkedInSessionCheck";

const REASON: Record<ReconnectReason, string> = {
  EXPIRED: "Tu sesión de LinkedIn venció. Inicia sesión de nuevo para guardarla y seguir buscando y postulando.",
  NOT_CONNECTED: "No hay una sesión de LinkedIn guardada en este equipo. Inicia sesión para guardarla.",
};

/**
 * Pantalla de consentimiento (texto exacto de la especificación, sección 10) y, al aceptar, el avance
 * de la conexión. Sirve para el primer ingreso, "Agregar otra cuenta" y la reconexión.
 */
export function ConnectPage() {
  const navigate = useNavigate();
  const [connecting, setConnecting] = useState(false);
  const reason = (useLocation().state as { reason?: ReconnectReason } | null)?.reason;

  return (
    <section className="card">
      <h1>Conectar tu cuenta de LinkedIn</h1>
      {reason && <p className="warning" role="status">{REASON[reason]}</p>}
      {connecting ? (
        <ConnectionProgress onCancel={() => setConnecting(false)} />
      ) : (
        <>
          <p>
            Tu cuenta de LinkedIn será tu cuenta en esta plataforma. Se abrirá la página oficial de LinkedIn para que
            inicies sesión allí: nunca vemos ni guardamos tu contraseña.
          </p>
          <p>
            Leeremos la información de tu perfil (nombre, foto, correo, experiencia, educación y habilidades) para crear
            tu cuenta y llenar tus postulaciones.
          </p>
          <p>
            Tu sesión se guarda cifrada en la base de datos de este equipo y no se envía a ningún servidor. Puedes
            desconectarte o eliminar tus datos cuando quieras desde Configuración.
          </p>
          <p className="warning">
            Ten en cuenta: LinkedIn no permite la automatización en sus términos de uso, por lo que existe el riesgo de
            que restrinja tu cuenta. Al continuar, aceptas ese riesgo.
          </p>
          <div className="actions">
            <button className="secondary" onClick={() => navigate(-1)}>Cancelar</button>
            <button onClick={() => setConnecting(true)}>Abrir LinkedIn</button>
          </div>
        </>
      )}
    </section>
  );
}
