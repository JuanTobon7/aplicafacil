import type { Profile } from "../../api/profile";
import { useProfile } from "../../hooks/useProfile";
import { CvImport } from "./CvImport";
import { ProfileSections } from "./ProfileSections";
import { SearchPreferenceCard } from "./SearchPreferenceCard";
import { WorkProfileCard } from "./WorkProfileCard";

/**
 * Perfil laboral: qué tipo de profesional eres (Backend, Frontend…), importar el CV, la búsqueda que eligió la IA y las
 * secciones (skills, experiencia, estudios…).
 */
export function ProfileWork() {
  const { profile, tasks, providers, pending, error, reload } = useProfile();
  if (profile === undefined && !error) return <p className="muted">Cargando perfil…</p>;

  return (
    <>
      {error && <p className="error" role="alert">{error}</p>}
      <WorkProfileCard workProfile={profile?.workProfile ?? null} onSaved={reload} />
      <CvImport providers={providers} tasks={tasks} pending={pending} onStarted={reload} />
      {profile && <ProfileView profile={profile} configId={providers[0]?.id ?? null} pending={pending} onChanged={reload} />}
    </>
  );
}

type ViewProps = { profile: Profile; configId: string | null; pending: boolean; onChanged: () => Promise<void> };

function ProfileView({ profile, configId, pending, onChanged }: ViewProps) {
  const p = profile.searchPreference;
  return (
    <>
      {hasSearch(profile) && (
        // Una sugerencia nueva vuelve a montar el formulario con los valores que llegaron
        <SearchPreferenceCard key={`${p.suggestedAt ?? ""}|${p.keywords ?? ""}`} preference={p} configId={configId}
          pending={pending} onChanged={onChanged} />
      )}
      <ProfileSections profile={profile} />
    </>
  );
}

/** Hay algo que buscar: la IA ya eligió, la persona escribió una búsqueda o al menos hay skills para pedirla. */
function hasSearch(profile: Profile): boolean {
  return profile.searchPreference.keywords !== null || profile.skills.length > 0;
}
