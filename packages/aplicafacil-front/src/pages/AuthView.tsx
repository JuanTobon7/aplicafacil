import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { AccountPanel } from '../components/AccountPanel';
import type { AccountForm, AccountMode } from '../types/account';
import type { Notice } from '../types/notice';

type AuthViewProps = {
  account: AccountForm;
  isAuthenticated: boolean;
  isSubmittingAccount: boolean;
  mode: AccountMode;
  notice: Notice | null;
  onModeChange: (mode: AccountMode) => void;
  onSubmit: (event: any) => void;
  onUpdateAccount: (event: any) => void;
};

export function AuthView({ isAuthenticated, ...accountProps }: AuthViewProps) {
  const navigate = useNavigate();

  // Si ya está logueado, no tiene sentido quedarse en /auth
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  return (
    <main className="auth-shell">
      <AccountPanel {...accountProps} />
    </main>
  );
}