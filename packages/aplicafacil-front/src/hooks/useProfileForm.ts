import { useMemo, useState, useEffect } from 'react';
import type { FormEvent } from 'react';
import { createProfile, deleteProfile, getProfileById, getProfiles } from '../api/profileApi';
import type { Notice } from '../types/notice';
import type { EducationForm, ExperienceForm, ProfileForm, ProfileResponse, SkillForm } from '../types/profile';
import { createProfileState, emptyEducation, emptyExperience, emptySkill } from '../utils/profileDefaults';
import { toCreateProfilePayload } from '../utils/profilePayload';

type ProfileCollection = 'skills' | 'experiences' | 'education';

type UseProfileFormOptions = {
  setNotice: (notice: Notice | null) => void;
};

export function useProfileForm({ setNotice }: UseProfileFormOptions) {
  const [profile, setProfile] = useState<ProfileForm>(createProfileState);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [savedProfileId, setSavedProfileId] = useState<string | null>(null);
  const [profileBank, setProfileBank] = useState<ProfileResponse[]>([]);
  const [deletingProfileId, setDeletingProfileId] = useState<string | null>(null);

  useEffect(() => {
    getProfilesBank();
  }, []);

  const profileReady = useMemo(
    () => Boolean(profile.title.trim() && profile.summary.trim()),
    [profile.summary, profile.title],
  );

  const updateBasics = (field: 'title' | 'summary', value: string) => {
    setProfile((current) => ({ ...current, [field]: value }));
  };

  const addSkill = () => {
    setProfile((current) => ({ ...current, skills: [...current.skills, emptySkill()] }));
  };

  const addExperience = () => {
    setProfile((current) => ({ ...current, experiences: [...current.experiences, emptyExperience()] }));
  };

  const addEducation = () => {
    setProfile((current) => ({ ...current, education: [...current.education, emptyEducation()] }));
  };

  const updateSkill = (index: number, key: keyof SkillForm, value: string) => {
    setProfile((current) => ({
      ...current,
      skills: current.skills.map((skill, skillIndex) => (skillIndex === index ? { ...skill, [key]: value } : skill)),
    }));
  };

  const updateExperience = (index: number, key: keyof ExperienceForm, value: string) => {
    setProfile((current) => ({
      ...current,
      experiences: current.experiences.map((experience, experienceIndex) =>
        experienceIndex === index ? { ...experience, [key]: value } : experience,
      ),
    }));
  };

  const updateEducation = (index: number, key: keyof EducationForm, value: string) => {
    setProfile((current) => ({
      ...current,
      education: current.education.map((education, educationIndex) =>
        educationIndex === index ? { ...education, [key]: value } : education,
      ),
    }));
  };

  const removeEntry = (index: number, collection: ProfileCollection) => {
    setProfile((current) => ({
      ...current,
      [collection]: current[collection].filter((_, entryIndex) => entryIndex !== index),
    }));
  };

  const saveProfile = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setNotice(null);
    setIsSavingProfile(true);

    try {
      const created = await createProfile(toCreateProfilePayload(profile));
      setSavedProfileId(created.id);
      await getProfilesBank();
      setNotice({
        tone: 'success',
        message: 'Profile guardado. Ya puedes subir tu CV para analisis con AI.',
      });
    } catch (error) {
      setNotice({
        tone: 'error',
        message: error instanceof Error ? error.message : 'No fue posible guardar el profile.',
      });
    } finally {
      setIsSavingProfile(false);
    }
  };

  const getProfile = async (id: string) => {
    try {
      const response = await getProfileById(id);
      setProfile(response);
    } catch (error) {
      setNotice({
        tone: 'error',
        message: error instanceof Error ? error.message : 'No fue posible cargar los profiles guardados.',
      });
    }
  };

  const getProfilesBank = async () => {
    try {
      const response = await getProfiles();
      setProfileBank(response);
    } catch (error) {
      setNotice({
        tone: 'error',
        message: error instanceof Error ? error.message : 'No fue posible cargar los profiles guardados.',
      });
    }
  };

  const removeProfile = async (id: string) => {
    setNotice(null);
    setDeletingProfileId(id);

    try {
      await deleteProfile(id);
      await getProfilesBank();

      // Si el perfil borrado era el que está cargado en el form, lo limpiamos
      if (savedProfileId === id) {
        setProfile(createProfileState());
        setSavedProfileId(null);
      }

      setNotice({ tone: 'success', message: 'Profile eliminado.' });
    } catch (error) {
      setNotice({
        tone: 'error',
        message: error instanceof Error ? error.message : 'No fue posible eliminar el profile.',
      });
    } finally {
      setDeletingProfileId(null);
    }
  };

  const startNewProfile = () => {
    setProfile(createProfileState());
    setSavedProfileId(null);
    setNotice(null);
  };

  return {
    addEducation,
    addExperience,
    addSkill,
    deletingProfileId,
    getProfile,
    isSavingProfile,
    profile,
    profileBank,
    profileReady,
    removeEntry,
    removeProfile,
    saveProfile,
    savedProfileId,
    startNewProfile,
    updateBasics,
    updateEducation,
    updateExperience,
    updateSkill,
    setProfile,
  };
}