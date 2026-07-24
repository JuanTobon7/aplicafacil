import type { useProfileForm } from '../hooks/useProfileForm'; // ajustá la ruta real
export type SkillForm = {
  id?: string;
  name: string;
  description: string;
  yearsOfExperience: string;
};

export type ExperienceForm = {
  id?: string;
  companyName: string;
  position: string;
  description: string;
  startDate: string;
  endDate: string;
};

export type EducationForm = {
  id?: string;
  institutionName: string;
  description: string;
  startDate: string;
  endDate: string;
};

export type ProfileForm = {
  id?: string;
  title: string;
  summary: string;
  skills: SkillForm[];
  experiences: ExperienceForm[];
  education: EducationForm[];
};

export type CreateSkillPayload = {
  name: string;
  description?: string;
  yearsOfExperience?: number;
};

export type CreateExperiencePayload = {
  companyName: string;
  position: string;
  description?: string;
  startDate: string;
  endDate?: string;
};

export type CreateEducationPayload = {
  institutionName: string;
  description?: string;
  startDate: string;
  endDate?: string;
};

export type CreateProfilePayload = {
  title: string;
  summary: string;
  skills: CreateSkillPayload[];
  experiences: CreateExperiencePayload[];
  education: CreateEducationPayload[];
};

export type ProfileResponse = {
  id: string;
  title: string;
  summary: string;
};


export type ProfileFormApi = ReturnType<typeof useProfileForm>;