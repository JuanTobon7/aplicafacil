import { useState } from "react";
import type { WorkProfile } from "../../api/profile";
import { WorkProfileForm } from "./WorkProfileForm";
import { roleLabel, seniorityLabel } from "./workRoles";

type Props = { workProfile: WorkProfile | null; onSaved: () => Promise<void> };

/**
 * "Perfil laboral": qué tipo de profesional es la persona (Backend, Frontend, Fullstack…). Lo deduce la IA del CV al
 * sugerir la búsqueda; la persona lo puede definir a mano y desde ahí la IA ya no lo cambia.
 */
export function WorkProfileCard({ workProfile, onSaved }: Props) {
  const [editing, setEditing] = useState(false);

  if (editing) {
    return (
      <WorkProfileForm
        initial={workProfile}
        onCancel={() => setEditing(false)}
        onSaved={async () => {
          setEditing(false);
          await onSaved();
        }}
      />
    );
  }

  return (
    <section className="card">
      <div className="section-head">
        <h2>Perfil laboral</h2>
        <button className="secondary" onClick={() => setEditing(true)}>{workProfile ? "Cambiar" : "Elegir mi perfil"}</button>
      </div>
      {workProfile ? <WorkProfileSummary profile={workProfile} /> : (
        <p className="muted">
          Todavía no sabemos qué tipo de profesional eres. Importa tu CV y pide la búsqueda sugerida (la IA lo deduce), o
          elígelo tú.
        </p>
      )}
    </section>
  );
}

function WorkProfileSummary({ profile: w }: { profile: WorkProfile }) {
  const seniority = seniorityLabel(w.seniority);
  return (
    <>
      <p className="work-role">
        <strong>{w.primaryRole === "OTHER" ? w.title : roleLabel(w.primaryRole)}</strong>
        {seniority && <span className="badge">{seniority}</span>}
      </p>
      {w.title && w.primaryRole !== "OTHER" && <p>{w.title}</p>}
      {w.secondaryRoles.length > 0 && (
        <p className="muted small">También: {w.secondaryRoles.map(roleLabel).join(" · ")}</p>
      )}
      <p className="muted small">
        {w.source === "AI"
          ? "Lo dedujo la IA de tu perfil. Si no es así, cámbialo: la IA no volverá a tocarlo."
          : "Definido por ti: la IA no lo cambia."}
      </p>
    </>
  );
}
