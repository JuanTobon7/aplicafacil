import { Sparkles } from 'lucide-react';
import { CollectionHeader } from './CollectionHeader';
import { IconButton } from './IconButton';
import type { SkillForm } from '../types/profile';

type SkillsSectionProps = {
  skills: SkillForm[];
  onAdd: () => void;
  onRemove: (index: number) => void;
  onUpdate: (index: number, key: keyof SkillForm, value: string) => void;
};

export function SkillsSection({ skills, onAdd, onRemove, onUpdate }: SkillsSectionProps) {
  return (
    <>
      <CollectionHeader icon={<Sparkles size={18} aria-hidden="true" />} title="Skills" onAdd={onAdd} />
      {skills.map((skill, index) => (
        <div className="entry-row skill-row" key={`skill-${index}`}>
          <label>
            Skill
            <input value={skill.name} maxLength={100} onChange={(event) => onUpdate(index, 'name', event.target.value)} />
          </label>
          <label>
            Experiencia
            <input
              value={skill.yearsOfExperience}
              type="number"
              min={0}
              max={80}
              onChange={(event) => onUpdate(index, 'yearsOfExperience', event.target.value)}
            />
          </label>
          <label>
            Descripcion
            <input
              value={skill.description}
              maxLength={255}
              onChange={(event) => onUpdate(index, 'description', event.target.value)}
            />
          </label>
          <IconButton label="Eliminar skill" onClick={() => onRemove(index)} disabled={skills.length === 1} />
        </div>
      ))}
    </>
  );
}
