import { BriefcaseBusiness } from 'lucide-react';
import { CollectionHeader } from './CollectionHeader';
import { IconButton } from './IconButton';
import type { ExperienceForm } from '../types/profile';

type ExperienceSectionProps = {
  experiences: ExperienceForm[];
  onAdd: () => void;
  onRemove: (index: number) => void;
  onUpdate: (index: number, key: keyof ExperienceForm, value: string) => void;
};

export function ExperienceSection({ experiences, onAdd, onRemove, onUpdate }: ExperienceSectionProps) {
  return (
    <>
      <CollectionHeader icon={<BriefcaseBusiness size={18} aria-hidden="true" />} title="Experiencia" onAdd={onAdd} />
      {experiences.map((experience, index) => (
        <div className="entry-row two-column" key={`experience-${index}`}>
          <label>
            Empresa
            <input
              value={experience.companyName}
              maxLength={100}
              onChange={(event) => onUpdate(index, 'companyName', event.target.value)}
            />
          </label>
          <label>
            Cargo
            <input
              value={experience.position}
              maxLength={100}
              onChange={(event) => onUpdate(index, 'position', event.target.value)}
            />
          </label>
          <label>
            Inicio
            <input value={experience.startDate} type="date" onChange={(event) => onUpdate(index, 'startDate', event.target.value)} />
          </label>
          <label>
            Fin
            <input value={experience.endDate} type="date" onChange={(event) => onUpdate(index, 'endDate', event.target.value)} />
          </label>
          <label className="span-two">
            Descripcion
            <textarea
              value={experience.description}
              maxLength={500}
              rows={3}
              onChange={(event) => onUpdate(index, 'description', event.target.value)}
            />
          </label>
          <IconButton label="Eliminar experiencia" onClick={() => onRemove(index)} disabled={experiences.length === 1} />
        </div>
      ))}
    </>
  );
}
