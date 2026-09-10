import { useState, useEffect } from 'react';
import { NavBar } from './components/NavBar';
import { NoticeMessage } from './components/NoticeMessage';
import { useAccountFlow } from './hooks/useAccountFlow';
import { useCvUpload } from './hooks/useCvUpload';
import { useProfileForm } from './hooks/useProfileForm';
import { AppRoutes } from './routes';
import { Notice } from './types/notice';

export default function App() {
  // ... tus hooks: accountFlow, profileForm, cvUpload, notice, isAuthenticated
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const accountFlow = useAccountFlow({
    onAuthenticated: () => setIsAuthenticated(true),
    setNotice,
  });
  const profileForm = useProfileForm({ setNotice });
  const cvUpload = useCvUpload({
    profileId: profileForm.savedProfileId,
    setNotice,
  });
  useEffect(() => {
    if (cvUpload.extractedProfile) {
      profileForm.setProfile(cvUpload.extractedProfile);
    }
  }, [cvUpload.extractedProfile]);
  return (
    <>
      <NavBar
        isAuthenticated={isAuthenticated}
        userName={'Juan'}
      />
      <NoticeMessage notice={notice} />
      <AppRoutes
        isAuthenticated={isAuthenticated}
        account={accountFlow.account}
        isSubmittingAccount={accountFlow.isSubmittingAccount}
        mode={accountFlow.mode}
        notice={notice}
        onModeChange={accountFlow.setMode}
        onSubmit={accountFlow.submitAccount}
        onUpdateAccount={accountFlow.updateAccount}
        profileForm={profileForm}
        cvUpload={cvUpload}
      />
    </>
  );
}