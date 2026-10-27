import { useCallback, useEffect, useState } from "react";

/** "system" sigue al sistema operativo; "light" y "dark" fijan el tema. */
export type ThemeChoice = "system" | "light" | "dark";

const KEY = "af-theme";

/**
 * Tema elegido por la persona, recordado en este navegador. Se aplica como data-theme en <html> (index.html lo hace
 * antes de pintar para que no parpadee); sin elección, styles.css sigue prefers-color-scheme.
 */
export function useTheme() {
  const [choice, setChoice] = useState<ThemeChoice>(readChoice);

  useEffect(() => {
    applyTheme(choice);
  }, [choice]);

  const choose = useCallback((next: ThemeChoice) => {
    setChoice(next);
    try {
      if (next === "system") localStorage.removeItem(KEY);
      else localStorage.setItem(KEY, next);
    } catch {
      // Sin almacenamiento el tema solo dura esta visita
    }
  }, []);

  return { choice, choose };
}

function readChoice(): ThemeChoice {
  try {
    const stored = localStorage.getItem(KEY);
    return stored === "light" || stored === "dark" ? stored : "system";
  } catch {
    return "system";
  }
}

function applyTheme(choice: ThemeChoice) {
  const root = document.documentElement;
  if (choice === "system") delete root.dataset.theme;
  else root.dataset.theme = choice;
}
