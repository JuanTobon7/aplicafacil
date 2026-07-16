import { useState } from 'react';
import { AccountPanel } from './components/AccountPanel';
import { ProfilePanel } from './components/ProfilePanel';
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
        <AccountPanel
          account={accountFlow.account}
          isSubmittingAccount={accountFlow.isSubmittingAccount}
          mode={accountFlow.mode}
          notice={notice}
          onModeChange={accountFlow.setMode}
          onSubmit={accountFlow.submitAccount}
          onUpdateAccount={accountFlow.updateAccount}
        />

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
