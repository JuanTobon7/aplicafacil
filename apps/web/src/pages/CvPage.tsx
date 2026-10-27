import { CvImport } from "../components/profile/CvImport";
import { PageHeader } from "../components/PageHeader";
import { useProfile } from "../hooks/useProfile";

/** Subir el CV: la IA lo lee y llena el perfil (y sugiere la búsqueda). */
export function CvPage() {
  const { tasks, providers, pending, error, reload } = useProfile();
  return (
    <>
      <PageHeader title="Importar CV" subtitle="PDF o Word. La IA lo lee y arma tu perfil; no se guarda el archivo." />
      {error && <p className="error" role="alert">{error}</p>}
      <CvImport providers={providers} tasks={tasks} pending={pending} onStarted={reload} />
    </>
  );
}
