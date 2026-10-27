import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

// Sin @types/node en el proyecto: process se lee como global opcional
const env = (globalThis as { process?: { env: Record<string, string | undefined> } }).process?.env ?? {};
const GATEWAY = env.GATEWAY_URL ?? "http://127.0.0.1:8080";

/**
 * Desarrollo con Vite: el gateway es quien inyecta el token local en index.html; aquí se pide a
 * /__local-token (solo responde a procesos locales sin Origin) y se inyecta igual. Si el backend se
 * reinicia, basta con recargar la página.
 */
function localToken(): Plugin {
  return {
    name: "aplicafacil-local-token",
    // Solo en desarrollo: en `vite build` dejaría un token viejo fijo en dist/index.html (el gateway inyecta el suyo)
    apply: "serve",
    async transformIndexHtml(html) {
      try {
        const token = await (await fetch(`${GATEWAY}/__local-token`)).text();
        return html.replace("</head>", `<meta name="af-local-token" content="${token}" />\n</head>`);
      } catch {
        console.warn(`[local-token] El gateway (${GATEWAY}) no responde: la API devolverá 403 hasta recargar.`);
        return html;
      }
    },
  };
}

export default defineConfig({
  plugins: [react(), localToken()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
    // Mismo origen para el navegador: la cookie af_rt y el SSE pasan por aquí
    proxy: { "/api": { target: GATEWAY, changeOrigin: true } },
  },
});
