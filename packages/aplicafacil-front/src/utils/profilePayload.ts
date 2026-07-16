import type { CreateProfilePayload, ProfileForm } from '../types/profile';

export function toCreateProfilePayload(profile: ProfileForm): CreateProfilePayload {
  return {
    title: profile.title,
    summary: profile.summary,
    skills: profile.skills
      .filter((skill) => skill.name.trim())
      .map((skill) => ({
        name: skill.name,
        description: skill.description || undefined,
        yearsOfExperience: skill.yearsOfExperience ? Number(skill.yearsOfExperience) : undefined,
      })),
    experiences: profile.experiences
      .filter((experience) => experience.companyName.trim() && experience.position.trim() && experience.startDate)
      .map((experience) => ({
        companyName: experience.companyName,
        position: experience.position,
        description: experience.description || undefined,
        startDate: experience.startDate,
        endDate: experience.endDate || undefined,
      })),
    education: profile.education
      .filter((education) => education.institutionName.trim() && education.startDate)
      .map((education) => ({
        institutionName: education.institutionName,
        description: education.description || undefined,
        startDate: education.startDate,
        endDate: education.endDate || undefined,
      })),
  };
}
