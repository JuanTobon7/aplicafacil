import { Link } from "react-router";
import { displayName } from "../api/auth";
import { useAuth } from "../auth/AuthContext";
import { PageHeader } from "../components/PageHeader";
import { ProfileSections } from "../components/profile/ProfileSections";
import { WorkProfileCard } from "../components/profile/WorkProfileCard";
import { useProfile } from "../hooks/useProfile";

/** Qué tipo de profesional eres y lo que la IA sabe de ti (skills, experiencia, estudios…). */
export function WorkProfilePage() {
  const user = useAuth().user!;
  const { profile, error, reload } = useProfile();
  return (
    <>
      <PageHeader title={displayName(user)} subtitle={user.headline ?? "Tu perfil laboral"} />
      {error && <p className="error" role="alert">{error}</p>}
      {profile === undefined && !error && <p className="muted">Cargando perfil…</p>}
      {profile !== undefined && <WorkProfileCard workProfile={profile?.workProfile ?? null} onSaved={reload} />}
      {profile ? <ProfileSections profile={profile} /> : profile === null && (
        <section className="card empty">
          <h2>Todavía no hay perfil</h2>
          <p className="muted">Importa tu CV y la IA arma tu perfil: skills, experiencia y estudios.</p>
          <Link className="button" to="/perfil/cv">Importar CV</Link>
        </section>
      )}
    </>
  );
}
