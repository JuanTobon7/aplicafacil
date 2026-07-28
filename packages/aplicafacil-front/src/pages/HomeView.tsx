import { ProfilePanel } from '../components/ProfilePanel';
import { ProfilesList } from '../components/ListProfiles';
import { ProfileFormApi } from '../types/profile';
import { CvUploadApi } from '../types/account';

type HomeViewProps = {
  isAuthenticated: boolean;
  profileForm: ProfileFormApi;
  cvUpload: CvUploadApi;
};

export function HomeView({ isAuthenticated, profileForm, cvUpload }: HomeViewProps) {
  return (
    <main className="app-shell">
      <section className="workspace">
        <div className="bg-white/90 border border-[#d9e2df] rounded-lg shadow-[0_24px_60px_rgba(33,45,52,0.08)] p-5">
          <h3 className="text-sm font-semibold text-[#617074] uppercase tracking-wide mb-3">
            📂 Perfiles guardados
          </h3>
          <ProfilesList
            onSelectProfile={profileForm.getProfile}
            onDeleteProfile={profileForm.removeProfile}
            onCreateProfile={profileForm.startNewProfile}
            profileBank={profileForm.profileBank}
            deletingProfileId={profileForm.deletingProfileId}
          />
        </div>
        <ProfilePanel
          isAuthenticated={isAuthenticated}
          isSavingProfile={profileForm.isSavingProfile}
          isUploadingCv={cvUpload.isUploadingCv}
          isExtractingProfile={cvUpload.isExtractingProfile}
          onSelectCvToExtract={cvUpload.selectCvToExtract}
          onExtractProfile={cvUpload.extractProfile}
          profile={profileForm.profile}
          profileReady={profileForm.profileReady}
          savedProfileId={profileForm.savedProfileId}
          selectedCv={cvUpload.selectedCv}
          selectedCvToExtract={cvUpload.selectedCvToExtract}
          onAddEducation={profileForm.addEducation}
          onAddExperience={profileForm.addExperience}
          onAddSkill={profileForm.addSkill}
          onRemoveEntry={profileForm.removeEntry}
          onSaveProfile={profileForm.saveProfile}
          onSelectCv={cvUpload.selectCv}
          onUpdateBasics={profileForm.updateBasics}
          onUpdateEducation={profileForm.updateEducation}
          onUpdateExperience={profileForm.updateExperience}
          onUpdateSkill={profileForm.updateSkill}
          onUploadCv={cvUpload.uploadCv}
        />
      </section>
    </main>
  );
}