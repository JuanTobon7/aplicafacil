import type { Education, Experience, Profile, Skill, SkillCategory } from "../../api/profile";

const CATEGORY_LABEL: Record<SkillCategory | "NONE", string> = {
  LANGUAGE: "Lenguajes",
  FRAMEWORK: "Frameworks",
  DATABASE: "Bases de datos",
  CLOUD: "Nube",
  TOOL: "Herramientas",
  PRACTICE: "Prácticas",
  SOFT: "Habilidades blandas",
  OTHER: "Otras",
  NONE: "Sin clasificar",
};

/** El perfil armado (desde LinkedIn o el CV): skills agrupadas por tipo, experiencia, estudios, idiomas. */
export function ProfileSections({ profile: p }: { profile: Profile }) {
  return (
    <section className="card">
      <h2>{p.headline ?? "Mi perfil laboral"}</h2>
      {p.summary && <p>{p.summary}</p>}
      <SkillGroups skills={p.skills} />
      {p.experiences.length > 0 && <h3>Experiencia</h3>}
      <ul className="plain">{p.experiences.map((e) => <ExperienceItem key={e.id} experience={e} />)}</ul>
      {p.education.length > 0 && <h3>Estudios</h3>}
      <ul className="plain">{p.education.map((e) => <EducationItem key={e.id} education={e} />)}</ul>
      {p.languages.length > 0 && (
        <p><strong>Idiomas:</strong> {p.languages.map((l) => (l.proficiency ? `${l.name} (${l.proficiency})` : l.name)).join(" · ")}</p>
      )}
      {p.certifications.length > 0 && (
        <p><strong>Certificaciones:</strong> {p.certifications.map((c) => c.name).join(" · ")}</p>
      )}
    </section>
  );
}

/** Agrupa en una sola pasada con un Map por categoría (no un filter por cada categoría). */
function SkillGroups({ skills }: { skills: Skill[] }) {
  if (skills.length === 0) return null;
  const groups = new Map<SkillCategory | "NONE", Skill[]>();
  for (const skill of skills) {
    const key = skill.category ?? "NONE";
    groups.set(key, [...(groups.get(key) ?? []), skill]);
  }
  return (
    <>
      <h3>Skills</h3>
      {[...groups].map(([category, list]) => (
        <p key={category}>
          <span className="muted small">{CATEGORY_LABEL[category]}: </span>
          {list.map((s) => <span key={s.id} className="badge">{s.yearsOfExperience ? `${s.name} · ${s.yearsOfExperience} a` : s.name}</span>)}
        </p>
      ))}
    </>
  );
}

function period(start: string | null, end: string | null): string {
  return `${start ?? "?"} – ${end ?? "actual"}`;
}

function ExperienceItem({ experience: e }: { experience: Experience }) {
  return (
    <li>
      <strong>{e.title}</strong> · {e.company}
      <span className="muted small"> ({period(e.startDate, e.endDate)})</span>
      {e.technologies.length > 0 && <p className="muted small">{e.technologies.join(", ")}</p>}
    </li>
  );
}

function EducationItem({ education: e }: { education: Education }) {
  return (
    <li>
      <strong>{e.degree ?? e.fieldOfStudy ?? "Estudio"}</strong> · {e.institution}
      <span className="muted small"> ({period(e.startDate, e.endDate)})</span>
    </li>
  );
}
