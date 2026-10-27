import { useLocation, useNavigate } from "react-router";
import type { SearchSettings } from "../api/profile";
import { JobSearchForm } from "../components/jobs/JobSearchForm";
import { PageHeader } from "../components/PageHeader";

/** Una búsqueda pedida a mano. Al encolarla vuelve a Vacantes con ella seleccionada. */
export function ManualSearchPage() {
  const navigate = useNavigate();
  // "Buscar vacantes" desde las preferencias trae la búsqueda que eligió la IA
  const prefill = (useLocation().state as { prefill?: SearchSettings } | null)?.prefill;
  return (
    <>
      <PageHeader title="Búsqueda manual" subtitle="Elige tú el portal y las palabras clave." />
      <JobSearchForm prefill={prefill} onQueued={async (id) => navigate("/vacantes", { state: { selected: id } })} />
    </>
  );
}
