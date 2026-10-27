import { Link, useLocation } from "react-router";
import { AiSearchCard } from "../components/jobs/AiSearchCard";
import { JobSearchList } from "../components/jobs/JobSearchList";
import { PostingList } from "../components/jobs/PostingList";
import { PageHeader } from "../components/PageHeader";
import { useJobSearches } from "../hooks/useJobSearches";

/**
 * Vacantes: la IA elige y lanza las búsquedas desde el perfil ("Buscar con IA"); debajo, el estado de cada búsqueda y
 * las ofertas de la que se elija. El worker de jobs-service las procesa en segundo plano.
 */
export function JobSearchPage() {
  // Al volver de la búsqueda manual llega la que se acaba de pedir, ya seleccionada
  const justQueued = (useLocation().state as { selected?: string } | null)?.selected ?? null;
  const { searches, selected, setSelected, selectedSearch, postings, loadError, reload } = useJobSearches(justQueued);

  return (
    <>
      <PageHeader
        title="Vacantes"
        subtitle="Búsquedas en LinkedIn y Computrabajo hechas por un navegador en segundo plano."
        actions={<Link className="button secondary" to="/vacantes/manual">Búsqueda manual</Link>}
      />
      <AiSearchCard onLaunched={reload} />
      <JobSearchList
        searches={searches}
        loadError={loadError}
        selected={selected}
        onToggle={(id) => setSelected(id === selected ? null : id)}
      />
      {selectedSearch?.status === "COMPLETED" && <PostingList search={selectedSearch} postings={postings} />}
    </>
  );
}
