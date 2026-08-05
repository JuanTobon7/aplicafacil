import { postRecommendations } from "../api/recommendations";
import { JobForm } from "../core/types/forms";

// Sitios donde el autocompletado automático está habilitado por defecto
const AUTO_FILL_HOSTS = ["linkedin.com"];
const AUTO_APPLY_KEY = "autoApplyEnabled";

chrome.runtime.onMessage.addListener(
    async (message, sender, sendResponse) => {

        console.log("[Background]", message);

        if (message.type === "FORM_DETECTED") {
            console.log("Formulario detectado automáticamente");
            console.log("metadata:", message.form?.metadata);
            console.log("fields count:", message.form?.fields?.length ?? 0);

            // Autocompletado automático (solo en sitios conocidos y si está activado)
            if (message.form?.fields?.length &&
                sender.tab?.url && AUTO_FILL_HOSTS.some(h => sender.tab!.url!.includes(h))) {
                const stored = await chrome.storage.local.get(AUTO_APPLY_KEY);
                const enabled = stored[AUTO_APPLY_KEY] !== false; // default true

                if (enabled) {
                    // IMPORTANTE: se espera (await) para que el service worker de MV3
                    // no se apague a mitad de la petición HTTP al servidor.
                    await autoApply(message.form, sender.tab.id!);
                }
            }
            return;
        }

        if (message.type !== "READ_ACTIVE_FORM") return;

        const [tab] = await chrome.tabs.query({
                active: true,
                currentWindow: true
            });

        if (!tab?.id) {
            sendResponse({
                success: false,
                message: "No active tab."
            });

            return;
        }

        try {

            const response = await chrome.tabs.sendMessage(
                    tab.id,
                    {
                        type: "READ_FORM"
                    }
                );

            sendResponse(response);

        } catch {

            sendResponse({
                success: false,
                message:
                    "Content script unavailable."
            });

        }

        return true;
    }
);

// ------------------------------------------------------------------
// Obtiene recomendaciones del servidor y las aplica en la pestaña.
// Es "fire and forget": los errores se loguean sin romper el listener.
// ------------------------------------------------------------------
async function autoApply(form: JobForm, tabId: number): Promise<void> {
    try {
        if (!form?.fields?.length) return;

        const recommendations = await postRecommendations(form);

        if (!recommendations.length) {
            console.log("[Background] Sin recomendaciones para aplicar");
            return;
        }

        console.log(`[Background] Aplicando ${recommendations.length} recomendaciones en tab ${tabId}`);
        await chrome.tabs.sendMessage(tabId, {
            type: "APPLY_RECOMMENDATIONS",
            recommendations,
        });
    } catch (error) {
        console.error("[Background] Error en autocompletado automático:", error);
    }
}