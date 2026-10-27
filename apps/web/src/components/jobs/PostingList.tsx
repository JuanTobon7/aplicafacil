import type { JobPosting, JobSearch } from "../../api/jobs";

/** Las ofertas de una búsqueda completada, en el orden del portal. */
export function PostingList({ search, postings }: { search: JobSearch; postings: JobPosting[] | null }) {
  return (
    <section className="card">
      <h2>Ofertas: {search.keywords}</h2>
      {postings === null && <p className="muted">Cargando ofertas…</p>}
      {postings?.length === 0 && <p className="muted">La búsqueda no encontró ofertas.</p>}
      <ul className="providers">
        {postings?.map((p) => <PostingItem key={p.id} posting={p} />)}
      </ul>
    </section>
  );
}

function PostingItem({ posting: p }: { posting: JobPosting }) {
  return (
    <li>
      <div className="provider-head">
        <a href={p.url} target="_blank" rel="noreferrer"><strong>{p.title}</strong></a>
        <span>
          {p.isNew && <span className="badge status-active">Nueva</span>}{" "}
          {p.quickApply && <span className="badge">Solicitud sencilla</span>}
        </span>
      </div>
      <p className="small">{p.company}{p.location && <> · {p.location}</>}</p>
      <p className="muted small">{[p.postedText, p.salaryText].filter(Boolean).join(" · ")}</p>
    </li>
  );
}
