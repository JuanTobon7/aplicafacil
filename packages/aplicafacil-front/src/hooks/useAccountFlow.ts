import { useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { createPerson, loginUser, registerUser } from '../api/accountApi';
import type { AccountForm, AccountMode } from '../types/account';
import type { Notice } from '../types/notice';

const initialAccount: AccountForm = {
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  linkedinUrl: '',
  username: '',
  password: '',
};

type UseAccountFlowOptions = {
  onAuthenticated: () => void;
  setNotice: (notice: Notice | null) => void;
};

export function useAccountFlow({ onAuthenticated, setNotice }: UseAccountFlowOptions) {
  const [mode, setMode] = useState<AccountMode>('register');
  const [account, setAccount] = useState<AccountForm>(initialAccount);
  const [isSubmittingAccount, setIsSubmittingAccount] = useState(false);

  const updateAccount = (event: ChangeEvent<HTMLInputElement>) => {
    setAccount((current) => ({
      ...current,
      [event.target.name]: event.target.value,
    }));
  };

  const submitAccount = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setNotice(null);
    setIsSubmittingAccount(true);

    try {
      if (mode === 'register') {
        const person = await createPerson({
          firstName: account.firstName,
          lastName: account.lastName,
          email: account.email,
          phone: account.phone || undefined,
          linkedinUrl: account.linkedinUrl || undefined,
        });

        await registerUser({
          username: account.username,
          password: account.password,
          personId: person.id,
        });
      }

      await loginUser({
        username: account.username,
        password: account.password,
      });

      onAuthenticated();
      setNotice({
        tone: 'success',
        message:
          mode === 'register'
            ? 'Cuenta creada. Ya puedes guardar tu profile.'
            : 'Sesion iniciada. Ya puedes editar tu profile.',
      });
    } catch (error) {
      setNotice({
        tone: 'error',
        message: error instanceof Error ? error.message : 'No fue posible continuar.',
      });
    } finally {
      setIsSubmittingAccount(false);
    }
  };

  return {
    account,
    isSubmittingAccount,
    mode,
    setMode,
    submitAccount,
    updateAccount,
  };
}
