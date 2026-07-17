import { useState } from 'react';
import { AccountPanel } from './components/AccountPanel';
import { ProfilePanel } from './components/ProfilePanel';
import { ProfilesList } from './components/ListProfiles';
import { useAccountFlow } from './hooks/useAccountFlow';
import { useCvUpload } from './hooks/useCvUpload';
import { useProfileForm } from './hooks/useProfileForm';
import type { Notice } from './types/notice';

export function App() {
  const [notice, setNotice] = useState<Notice | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  const accountFlow = useAccountFlow({
    onAuthenticated: () => setIsAuthenticated(true),
    setNotice,
  });

  const profileForm = useProfileForm({ setNotice });
  const cvUpload = useCvUpload({
    profileId: profileForm.savedProfileId,
    setNotice,
  });

  return (
    <main className="app-shell">
      <section className="workspace">
        {/* Columna izquierda */}
        <div className="flex flex-col gap-6 sticky top-7 ">
          <AccountPanel
            account={accountFlow.account}
            isSubmittingAccount={accountFlow.isSubmittingAccount}
            mode={accountFlow.mode}
            notice={notice}
            onModeChange={accountFlow.setMode}
            onSubmit={accountFlow.submitAccount}
            onUpdateAccount={accountFlow.updateAccount}
          />

          {/* Sección de perfiles guardados con estilo mejorado */}
          <div className="bg-white/90 border border-[#d9e2df] rounded-lg shadow-[0_24px_60px_rgba(33,45,52,0.08)] p-5">
            <h3 className="text-sm font-semibold text-[#617074] uppercase tracking-wide mb-3">
              📂 Perfiles guardados
            </h3>
            <ProfilesList
              onSelectProfile={profileForm.getProfile}
              profileBank={profileForm.profileBank}
            />
          </div>
        </div>

        {/* Columna derecha */}
        <ProfilePanel
          isAuthenticated={isAuthenticated}
          isSavingProfile={profileForm.isSavingProfile}
          isUploadingCv={cvUpload.isUploadingCv}
          profile={profileForm.profile}
          profileReady={profileForm.profileReady}
          savedProfileId={profileForm.savedProfileId}
          selectedCv={cvUpload.selectedCv}
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