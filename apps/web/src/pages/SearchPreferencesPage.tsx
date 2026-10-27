import { Link } from "react-router";
import type { ProviderConfig } from "../api/ai";
import type { Profile } from "../api/profile";
import { PageHeader } from "../components/PageHeader";
import { SearchPreferenceCard } from "../components/profile/SearchPreferenceCard";
import { useProfile } from "../hooks/useProfile";

/** La búsqueda que eligió la IA y cómo filtrarla (remoto, fecha, ciudad). "Buscar con IA" la usa. */
export function SearchPreferencesPage() {
  const { profile, providers, pending, error, reload } = useProfile();
  return (
    <>
      <PageHeader title="Preferencias de búsqueda" subtitle="Lo que la IA busca por ti y cómo filtrarlo." />
      {error && <p className="error" role="alert">{error}</p>}
      {profile === undefined && !error && <p className="muted">Cargando…</p>}
      {profile !== undefined && <Preferences profile={profile} providers={providers} pending={pending} onChanged={reload} />}
    </>
  );
}

type Props = { profile: Profile | null; providers: ProviderConfig[]; pending: boolean; onChanged: () => Promise<void> };

/** Hay algo que buscar cuando la IA ya eligió, la persona escribió una búsqueda o al menos hay skills. */
function Preferences({ profile, providers, pending, onChanged }: Props) {
  const p = profile?.searchPreference;
  if (!profile || !p || (p.keywords === null && profile.skills.length === 0)) {
    return (
      <section className="card empty">
        <p className="muted">Primero importa tu CV: con tu perfil la IA elige qué buscar.</p>
        <Link className="button" to="/perfil/cv">Importar CV</Link>
      </section>
    );
  }
  // Una sugerencia nueva vuelve a montar el formulario con los valores que llegaron
  return (
    <SearchPreferenceCard key={`${p.suggestedAt ?? ""}|${p.keywords ?? ""}`} preference={p}
      configId={providers[0]?.id ?? null} pending={pending} onChanged={onChanged} />
  );
}
