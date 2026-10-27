import type { ReactNode } from "react";
import { useTheme, type ThemeChoice } from "../hooks/useTheme";

/** Iconos propios: los caracteres ☀/☾ salen como emoji distinto en cada sistema. */
const Sun = (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
  </svg>
);
const Moon = (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden>
    <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
  </svg>
);

const OPTIONS: { value: ThemeChoice; icon: ReactNode; title: string }[] = [
  { value: "light", icon: Sun, title: "Tema claro" },
  { value: "system", icon: "Auto", title: "Igual que el sistema" },
  { value: "dark", icon: Moon, title: "Tema oscuro" },
];

/** Claro, automático (el del sistema) u oscuro. */
export function ThemeToggle() {
  const { choice, choose } = useTheme();
  return (
    <div className="theme-toggle" role="group" aria-label="Tema">
      {OPTIONS.map((o) => (
        <button key={o.value} type="button" title={o.title} aria-label={o.title} aria-pressed={choice === o.value}
          onClick={() => choose(o.value)}>
          {o.icon}
        </button>
      ))}
    </div>
  );
}
