import { useState } from 'react';
import type { ChangeEvent } from 'react';
import { uploadProfileCv } from '../api/profileApi';
import type { Notice } from '../types/notice';

type UseCvUploadOptions = {
  profileId: string | null;
  setNotice: (notice: Notice | null) => void;
};

export function useCvUpload({ profileId, setNotice }: UseCvUploadOptions) {
  const [selectedCv, setSelectedCv] = useState<File | null>(null);
  const [isUploadingCv, setIsUploadingCv] = useState(false);

  const selectCv = (event: ChangeEvent<HTMLInputElement>) => {
    setSelectedCv(event.target.files?.[0] ?? null);
  };

  const uploadCv = async () => {
    if (!profileId || !selectedCv) return;
    setNotice(null);
    setIsUploadingCv(true);

    try {
      await uploadProfileCv(profileId, selectedCv);
      setNotice({
        tone: 'success',
        message: 'CV enviado. El server ya puede procesarlo con AI.',
      });
    } catch (error) {
      setNotice({
        tone: 'error',
        message: error instanceof Error ? error.message : 'No fue posible subir el CV.',
      });
    } finally {
      setIsUploadingCv(false);
    }
  };

  return {
    isUploadingCv,
    selectedCv,
    selectCv,
    uploadCv,
  };
}
