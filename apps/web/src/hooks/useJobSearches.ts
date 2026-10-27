import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/http";
import { isPending, jobsApi, type JobPosting, type JobSearch } from "../api/jobs";

const POLL_MS = 3000;

/**
 * Búsquedas recientes, la seleccionada y sus ofertas. Mientras haya búsquedas en cola o corriendo, consulta cada 3 s.
 */
export function useJobSearches() {
  const [searches, setSearches] = useState<JobSearch[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setSearches(await jobsApi.recent());
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof ApiError ? e.message : "No se pudieron cargar las búsquedas.");
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const pending = searches?.some(isPending) ?? false;
  useEffect(() => {
    if (!pending) return;
    const timer = window.setInterval(() => void reload(), POLL_MS);
    return () => window.clearInterval(timer);
  }, [pending, reload]);

  const selectedSearch = searches?.find((s) => s.id === selected) ?? null;
  const postings = usePostings(selectedSearch);

  return { searches, selected, setSelected, selectedSearch, postings, loadError, reload };
}

/** Las ofertas de una búsqueda completada; {@code null} mientras cargan o si no hay una completada. */
function usePostings(search: JobSearch | null): JobPosting[] | null {
  const [postings, setPostings] = useState<JobPosting[] | null>(null);
  const id = search?.status === "COMPLETED" ? search.id : null;
  useEffect(() => {
    setPostings(null);
    if (!id) return;
    let cancelled = false;
    jobsApi.postings(id).then(
      (list) => !cancelled && setPostings(list),
      () => !cancelled && setPostings([]),
    );
    return () => {
      cancelled = true;
    };
  }, [id]);
  return postings;
}
