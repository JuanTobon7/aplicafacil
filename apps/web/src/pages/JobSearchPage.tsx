import { useLocation } from "react-router";
import type { SearchSettings } from "../api/profile";
import { JobSearchForm } from "../components/jobs/JobSearchForm";
import { JobSearchList } from "../components/jobs/JobSearchList";
import { PostingList } from "../components/jobs/PostingList";
import { useJobSearches } from "../hooks/useJobSearches";

/**
 * Búsqueda de vacantes: se pide aquí, el worker de jobs-service la procesa (abre el navegador, recorre las cards y
 * guarda las ofertas) y esta página consulta el estado mientras hay búsquedas en curso.
 */
export function JobSearchPage() {
  const { searches, selected, setSelected, selectedSearch, postings, loadError, reload } = useJobSearches();
  // "Buscar vacantes" desde el perfil trae la búsqueda que eligió la IA
  const prefill = (useLocation().state as { prefill?: SearchSettings } | null)?.prefill;

  const onQueued = async (searchId: string) => {
    setSelected(searchId);
    await reload();
  };

  return (
    <>
      <JobSearchForm onQueued={onQueued} prefill={prefill} />
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
