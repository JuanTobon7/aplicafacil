import { useState } from 'react';
import type { ChangeEvent } from 'react';
import { extractProfileFromCv, uploadProfileCv } from '../api/profileApi';
import type { Notice } from '../types/notice';
import { ProfileForm } from '../types/profile';

type UseCvUploadOptions = {
  profileId: string | null;
  setNotice: (notice: Notice | null) => void;
};

export function useCvUpload({ profileId, setNotice }: UseCvUploadOptions) {
  const [selectedCv, setSelectedCv] = useState<File | null>(null);
  const [isUploadingCv, setIsUploadingCv] = useState(false);
  const [selectedCvToExtract, setSelectedCvToExtract] = useState<File | null>(null);
  const [extractedProfile, setExtractedProfile] = useState<ProfileForm | null>(null);
  const [isExtractingProfile, setIsExtractingProfile] = useState(false);

  const selectCv = (event: ChangeEvent<HTMLInputElement>) => {
    setSelectedCv(event.target.files?.[0] ?? null);
  };

  const selectCvToExtract = (event: ChangeEvent<HTMLInputElement>) => {
    setSelectedCvToExtract(event.target.files?.[0] ?? null);
  }

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

  async function extractProfile() {
    console.log('extractProfile review');
    if (!selectedCvToExtract) return;
    setNotice(null);
    setIsExtractingProfile(true);
    try {
      console.log('extractProfile', selectedCvToExtract);
      const extracted = await extractProfileFromCv(selectedCvToExtract);
      setExtractedProfile(extracted);
      setNotice({
        tone: 'success',
        message: 'CV procesado. El server extrajo la información del CV.',
      });
    } catch (error) {
      setNotice({
        tone: 'error',
        message: error instanceof Error ? error.message : 'No fue posible procesar el CV.',
      });
    }finally {
      setIsExtractingProfile(false);
    }
  }

  return {
    isUploadingCv,
    isExtractingProfile,
    selectedCv,
    selectedCvToExtract,
    selectCv,
    selectCvToExtract,
    uploadCv,
    extractProfile,
  };
}
