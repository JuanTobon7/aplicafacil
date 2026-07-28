import { FileUp } from 'lucide-react';

type CvExtractSectionProps = {
  isExtractingProfile: boolean;
  selectedCv: File | null;
  onSelectCvToExtract: (event: any) => void;
  onExtractProfile: () => void;
};

export function CvExtractSection({
  isExtractingProfile,
  selectedCv,
  onSelectCvToExtract,
  onExtractProfile,
}: CvExtractSectionProps) {
  return (
    <section className="cv-band" aria-label="Subir CV">
      <div>
        <p className="eyebrow">Completaremos tu perfil</p>
        <h3>Sube tu CV</h3>
      </div>
      <label className="file-picker">
        <FileUp size={18} aria-hidden="true" />
        <span>{selectedCv ? selectedCv.name : 'Seleccionar archivo'}</span>
        <input type="file" accept=".pdf,.doc,.docx" onChange={onSelectCvToExtract} />
      </label>
      <button className="secondary-action" type="button" disabled={!selectedCv || isExtractingProfile} onClick={onExtractProfile}>
        <FileUp size={18} aria-hidden="true" />
        {isExtractingProfile ? 'Procesando...' : 'Enviar CV'}
      </button>
    </section>
  );
}
