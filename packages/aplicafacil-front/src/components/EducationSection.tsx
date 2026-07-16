import { GraduationCap } from 'lucide-react';
import { CollectionHeader } from './CollectionHeader';
import { IconButton } from './IconButton';
import type { EducationForm } from '../types/profile';

type EducationSectionProps = {
  education: EducationForm[];
  onAdd: () => void;
  onRemove: (index: number) => void;
  onUpdate: (index: number, key: keyof EducationForm, value: string) => void;
};

export function EducationSection({ education, onAdd, onRemove, onUpdate }: EducationSectionProps) {
  return (
    <>
      <CollectionHeader icon={<GraduationCap size={18} aria-hidden="true" />} title="Educacion" onAdd={onAdd} />
      {education.map((educationItem, index) => (
        <div className="entry-row two-column" key={`education-${index}`}>
          <label className="span-two">
            Institucion
            <input
              value={educationItem.institutionName}
              maxLength={100}
              onChange={(event) => onUpdate(index, 'institutionName', event.target.value)}
            />
          </label>
          <label>
            Inicio
            <input
              value={educationItem.startDate}
              type="date"
              onChange={(event) => onUpdate(index, 'startDate', event.target.value)}
            />
          </label>
          <label>
            Fin
            <input
              value={educationItem.endDate}
              type="date"
              onChange={(event) => onUpdate(index, 'endDate', event.target.value)}
            />
          </label>
          <label className="span-two">
            Descripcion
            <textarea
              value={educationItem.description}
              maxLength={250}
              rows={3}
              onChange={(event) => onUpdate(index, 'description', event.target.value)}
            />
          </label>
          <IconButton label="Eliminar educacion" onClick={() => onRemove(index)} disabled={education.length === 1} />
        </div>
      ))}
    </>
  );
}
