export type SkillForm = {
  name: string;
  description: string;
  yearsOfExperience: string;
};

export type ExperienceForm = {
  companyName: string;
  position: string;
  description: string;
  startDate: string;
  endDate: string;
};

export type EducationForm = {
  institutionName: string;
  description: string;
  startDate: string;
  endDate: string;
};

export type ProfileForm = {
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
