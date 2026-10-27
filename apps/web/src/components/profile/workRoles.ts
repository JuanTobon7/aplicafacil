import type { Seniority, WorkRole } from "../../api/profile";

/** El catálogo de perfiles laborales, en el orden en que se muestran. */
export const WORK_ROLES: { value: WorkRole; label: string; hint: string }[] = [
  { value: "BACKEND", label: "Backend", hint: "APIs, servicios, bases de datos" },
  { value: "FRONTEND", label: "Frontend", hint: "Interfaces web: React, Angular, Vue…" },
  { value: "FULLSTACK", label: "Fullstack", hint: "Backend y frontend" },
  { value: "MOBILE", label: "Móvil", hint: "Android, iOS, Flutter, React Native" },
  { value: "DEVOPS", label: "DevOps / Cloud / SRE", hint: "Infraestructura, CI/CD, nube" },
  { value: "DATA_ENGINEERING", label: "Ingeniería de datos", hint: "Pipelines, ETL, data warehouses" },
  { value: "DATA_SCIENCE", label: "Ciencia de datos / IA", hint: "Machine learning, modelos, IA" },
  { value: "DATA_ANALYSIS", label: "Análisis de datos / BI", hint: "SQL, dashboards, Power BI" },
  { value: "QA", label: "QA / Testing", hint: "Pruebas manuales y automatizadas" },
  { value: "UX_UI", label: "Diseño UX/UI", hint: "Experiencia e interfaces de usuario" },
  { value: "SECURITY", label: "Ciberseguridad", hint: "Seguridad, pentesting, SOC" },
  { value: "EMBEDDED", label: "Embebidos / IoT", hint: "Firmware, hardware, IoT" },
  { value: "GAME_DEV", label: "Videojuegos", hint: "Unity, Unreal, Godot" },
  { value: "PRODUCT", label: "Producto / Proyectos", hint: "Product owner, project manager, scrum master" },
  { value: "IT_SUPPORT", label: "Soporte TI", hint: "Mesa de ayuda, redes, sistemas" },
  { value: "OTHER", label: "Otro", hint: "Un perfil fuera de tecnología: escribe cuál" },
];

export const SENIORITIES: { value: Seniority; label: string }[] = [
  { value: "INTERN", label: "Practicante" },
  { value: "JUNIOR", label: "Junior" },
  { value: "MID", label: "Semi-senior" },
  { value: "SENIOR", label: "Senior" },
  { value: "LEAD", label: "Líder" },
];

export const MAX_SECONDARY_ROLES = 3;

export function roleLabel(role: WorkRole): string {
  return WORK_ROLES.find((r) => r.value === role)?.label ?? role;
}

export function seniorityLabel(seniority: Seniority | null): string | null {
  return seniority ? (SENIORITIES.find((s) => s.value === seniority)?.label ?? seniority) : null;
}
