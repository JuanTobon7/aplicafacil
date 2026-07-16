import type { ProfileForm } from '../types/profile';

type ProfileBasicsProps = {
  profile: ProfileForm;
  onUpdateBasics: (field: 'title' | 'summary', value: string) => void;
};

export function ProfileBasics({ profile, onUpdateBasics }: ProfileBasicsProps) {
  return (
    <div className="form-grid">
      <label className="span-all">
        Titulo profesional
        <input
          value={profile.title}
          onChange={(event) => onUpdateBasics('title', event.target.value)}
          placeholder="Frontend Developer"
          required
        />
      </label>
      <label className="span-all">
        Resumen
        <textarea
          value={profile.summary}
          onChange={(event) => onUpdateBasics('summary', event.target.value)}
          placeholder="Cuentale al sistema quien eres profesionalmente..."
          rows={5}
          required
        />
      </label>
    </div>
  );
}
