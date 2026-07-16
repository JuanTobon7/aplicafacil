import { FileUp } from 'lucide-react';

type CvUploadSectionProps = {
  isUploadingCv: boolean;
  profileId: string | null;
  selectedCv: File | null;
  onSelectCv: (event: any) => void;
  onUploadCv: () => void;
};

export function CvUploadSection({
  isUploadingCv,
  profileId,
  selectedCv,
  onSelectCv,
  onUploadCv,
}: CvUploadSectionProps) {
  return (
    <section className="cv-band" aria-label="Subir CV">
      <div>
        <p className="eyebrow">Analisis AI</p>
        <h3>Sube tu CV</h3>
      </div>
      <label className="file-picker">
        <FileUp size={18} aria-hidden="true" />
        <span>{selectedCv ? selectedCv.name : 'Seleccionar archivo'}</span>
        <input type="file" accept=".pdf,.doc,.docx" onChange={onSelectCv} />
      </label>
      <button className="secondary-action" type="button" disabled={!profileId || !selectedCv || isUploadingCv} onClick={onUploadCv}>
        <FileUp size={18} aria-hidden="true" />
        {isUploadingCv ? 'Subiendo...' : 'Enviar CV'}
      </button>
    </section>
  );
}
