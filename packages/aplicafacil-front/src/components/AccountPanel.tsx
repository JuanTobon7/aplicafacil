import { LogIn, UserPlus } from 'lucide-react';
import { BrandBlock } from './BrandBlock';
import { NoticeMessage } from './NoticeMessage';
import type { AccountForm, AccountMode } from '../types/account';
import type { Notice } from '../types/notice';

type AccountPanelProps = {
  account: AccountForm;
  isSubmittingAccount: boolean;
  mode: AccountMode;
  notice: Notice | null;
  onModeChange: (mode: AccountMode) => void;
  onSubmit: (event: any) => void;
  onUpdateAccount: (event: any) => void;
};

export function AccountPanel({
  account,
  isSubmittingAccount,
  mode,
  notice,
  onModeChange,
  onSubmit,
  onUpdateAccount,
}: AccountPanelProps) {
  return (
    <aside className="account-panel">
      <BrandBlock />

      <div className="mode-switch" aria-label="Modo de cuenta">
        <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => onModeChange('register')}>
          <UserPlus size={16} aria-hidden="true" />
          Registro
        </button>
        <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => onModeChange('login')}>
          <LogIn size={16} aria-hidden="true" />
          Login
        </button>
      </div>

      <form className="form-grid compact" onSubmit={onSubmit}>
        {mode === 'register' && (
          <>
            <label>
              Nombre
              <input name="firstName" value={account.firstName} onChange={onUpdateAccount} required />
            </label>
            <label>
              Apellido
              <input name="lastName" value={account.lastName} onChange={onUpdateAccount} required />
            </label>
            <label>
              Email
              <input name="email" type="email" value={account.email} onChange={onUpdateAccount} required />
            </label>
            <label>
              Telefono
              <input name="phone" value={account.phone} onChange={onUpdateAccount} />
            </label>
            <label className="span-all">
              LinkedIn
              <input name="linkedinUrl" type="url" value={account.linkedinUrl} onChange={onUpdateAccount} />
            </label>
          </>
        )}
        <label>
          Usuario
          <input name="username" value={account.username} onChange={onUpdateAccount} required />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            value={account.password}
            onChange={onUpdateAccount}
            minLength={8}
            required
          />
        </label>
        <button className="primary-action span-all" type="submit" disabled={isSubmittingAccount}>
          {mode === 'register' ? <UserPlus size={18} aria-hidden="true" /> : <LogIn size={18} aria-hidden="true" />}
          {isSubmittingAccount ? 'Procesando...' : mode === 'register' ? 'Crear cuenta' : 'Entrar'}
        </button>
      </form>

      <NoticeMessage notice={notice} />
    </aside>
  );
}
