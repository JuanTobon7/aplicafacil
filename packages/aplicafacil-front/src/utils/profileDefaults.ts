import type { EducationForm, ExperienceForm, ProfileForm, SkillForm } from '../types/profile';

export const emptySkill = (): SkillForm => ({
  name: '',
  description: '',
  yearsOfExperience: '',
});

export const emptyExperience = (): ExperienceForm => ({
  companyName: '',
  position: '',
  description: '',
  startDate: '',
  endDate: '',
});

export const emptyEducation = (): EducationForm => ({
  institutionName: '',
  description: '',
  startDate: '',
  endDate: '',
});

export const createProfileState = (): ProfileForm => ({
  title: '',
  summary: '',
  skills: [emptySkill()],
  experiences: [emptyExperience()],
  education: [emptyEducation()],
});
