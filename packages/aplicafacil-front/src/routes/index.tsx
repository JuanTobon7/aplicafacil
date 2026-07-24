import { Routes, Route } from 'react-router';
import { HomeView } from '../pages/HomeView';
import { AuthView } from '../pages/AuthView';
import type { AccountForm, AccountMode, CvUploadApi } from '../types/account';
import type { Notice } from '../types/notice';
import { ProfileFormApi } from '../types/profile';

type AppRoutesProps = {
  isAuthenticated: boolean;
  account: AccountForm;
  isSubmittingAccount: boolean;
  mode: AccountMode;
  notice: Notice | null;
  onModeChange: (mode: AccountMode) => void;
  onSubmit: (event: any) => void;
  onUpdateAccount: (event: any) => void;
  profileForm: ProfileFormApi;
  cvUpload: CvUploadApi;
};

export function AppRoutes({
  isAuthenticated,
  account,
  isSubmittingAccount,
  mode,
  notice,
  onModeChange,
  onSubmit,
  onUpdateAccount,
  profileForm,
  cvUpload,
}: AppRoutesProps) {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <HomeView
            isAuthenticated={isAuthenticated}
            profileForm={profileForm}
            cvUpload={cvUpload}
          />
        }
      />
      <Route
        path="/auth"
        element={
          <AuthView
            isAuthenticated={isAuthenticated}
            account={account}
            isSubmittingAccount={isSubmittingAccount}
            mode={mode}
            notice={notice}
            onModeChange={onModeChange}
            onSubmit={onSubmit}
            onUpdateAccount={onUpdateAccount}
          />
        }
      />
      {/* rutas futuras van acá, en un solo lugar */}
    </Routes>
  );
}