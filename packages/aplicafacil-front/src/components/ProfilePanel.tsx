import { CheckCircle2, Save } from 'lucide-react';
import { CvExtractSection } from './CvExtractSection';
import { EducationSection } from './EducationSection';
import { ExperienceSection } from './ExperienceSection';
import { ProfileBasics } from './ProfileBasics';
import { SkillsSection } from './SkillsSection';
import type { EducationForm, ExperienceForm, ProfileForm, SkillForm } from '../types/profile';
import { CvUploadSection } from './CvUploadSection';

type ProfilePanelProps = {
  isAuthenticated: boolean;
  isSavingProfile: boolean;
  isUploadingCv: boolean;
  isExtractingProfile: boolean;
  onSelectCvToExtract: (event: any) => void;
  onExtractProfile: () => void;
  profile: ProfileForm;
  profileReady: boolean;
  savedProfileId: string | null;
  selectedCv: File | null;
  extractedProfile: ProfileForm | null;
  selectedCvToExtract: File | null;
  onAddEducation: () => void;
  onAddExperience: () => void;
  onAddSkill: () => void;
  onRemoveEntry: (index: number, collection: 'skills' | 'experiences' | 'education') => void;
  onSaveProfile: (event: any) => void;
  onSelectCv: (event: any) => void;
  onUpdateBasics: (field: 'title' | 'summary', value: string) => void;
  onUpdateEducation: (index: number, key: keyof EducationForm, value: string) => void;
  onUpdateExperience: (index: number, key: keyof ExperienceForm, value: string) => void;
  onUpdateSkill: (index: number, key: keyof SkillForm, value: string) => void;
  onUploadCv: () => void;
};

export function ProfilePanel({
  isAuthenticated,
  isSavingProfile,
  isUploadingCv,
  isExtractingProfile,
  onSelectCvToExtract,
  profile,
  profileReady,
  savedProfileId,
  selectedCv,
  selectedCvToExtract,
  extractedProfile,
  onAddEducation,
  onAddExperience,
  onAddSkill,
  onRemoveEntry,
  onSaveProfile,
  onSelectCv,
  onUpdateBasics,
  onUpdateEducation,
  onUpdateExperience,
  onUpdateSkill,
  onUploadCv,
  onExtractProfile,
}: ProfilePanelProps) {

  return (
    <section className="profile-panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Datos usados por el server</p>
          <h2>Informacion profesional</h2>
        </div>
        {isAuthenticated && (
          <span className="status-pill">
            <CheckCircle2 size={16} aria-hidden="true" />
            Autenticado
          </span>
        )}
      </div>

      <form className="profile-form" onSubmit={onSaveProfile}>
        <CvExtractSection
          isExtractingProfile={isExtractingProfile}
          selectedCv={selectedCvToExtract}
          onSelectCvToExtract={onSelectCvToExtract}
          onExtractProfile={onExtractProfile}
        />
        <ProfileBasics profile={profile} onUpdateBasics={onUpdateBasics} />
        <SkillsSection
          skills={profile.skills}
          onAdd={onAddSkill}
          onRemove={(index) => onRemoveEntry(index, 'skills')}
          onUpdate={onUpdateSkill}
        />
        <ExperienceSection
          experiences={profile.experiences}
          onAdd={onAddExperience}
          onRemove={(index) => onRemoveEntry(index, 'experiences')}
          onUpdate={onUpdateExperience}
        />
        <EducationSection
          education={profile.education}
          onAdd={onAddEducation}
          onRemove={(index) => onRemoveEntry(index, 'education')}
          onUpdate={onUpdateEducation}
        />

        <div className="action-row">
          <button className="primary-action" type="submit" disabled={!isAuthenticated || !profileReady || isSavingProfile}>
            <Save size={18} aria-hidden="true" />
            {isSavingProfile ? 'Guardando...' : 'Guardar profile'}
          </button>
        </div>
      </form>

      <CvUploadSection
        isUploadingCv={isUploadingCv}
        profileId={savedProfileId}
        selectedCv={selectedCv}
        onSelectCv={onSelectCv}
        onUploadCv={onUploadCv}
      />
    </section>
  );
}
