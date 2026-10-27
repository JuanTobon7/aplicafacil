/** Perfil profesional leído de un portal; misma forma para LinkedIn, Computrabajo, etc. */
export type ProfileSnapshot = {
  summary: string | null;
  experience: unknown[];
  education: unknown[];
  skills: string[];
  languages: unknown[];
  complete: boolean;
  missingSections: string[];
};

export const PROFILE_SECTIONS = ["summary", "experience", "education", "skills", "languages"];

export const incompleteProfile = (missingSections: string[]): ProfileSnapshot => ({
  summary: null,
  experience: [],
  education: [],
  skills: [],
  languages: [],
  complete: false,
  missingSections,
});
