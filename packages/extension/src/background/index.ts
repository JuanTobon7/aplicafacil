import { postRecommendations } from "../api/recommendations";
import { getJobsToApply, updateJobStatus, JobToApply } from "../api/jobs";
import { JobForm } from "../core/types/forms";

// Sitios donde el autocompletado automático está habilitado por defecto
const AUTO_FILL_HOSTS = ["linkedin.com"];
const AUTO_APPLY_KEY = "autoApplyEnabled";
const AUTO_APPLY_JOBS_KEY = "autoApplyJobsEnabled";

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

        // Disparo manual de la auto-aplicación de vacantes pendientes
        if (message.type === "AUTO_APPLY_JOBS") {
            await autoApplyJobs();
            sendResponse({ success: true });
            return true;
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

// ------------------------------------------------------------------
// Auto-aplicación de vacantes pendientes (MATCHED) en LinkedIn.
// Consulta el servidor, abre cada vacante y espera a que el flujo de
// autocompletado la aplique. Luego notifica el resultado al servidor.
// ------------------------------------------------------------------
async function autoApplyJobs(): Promise<void> {
    try {
        const stored = await chrome.storage.local.get(AUTO_APPLY_JOBS_KEY);
        const enabled = stored[AUTO_APPLY_JOBS_KEY] !== false; // default true
        if (!enabled) {
            console.log("[Background] Auto-aplicación de vacantes desactivada");
            return;
        }

        const jobs = await getJobsToApply();
        if (!jobs.length) {
            console.log("[Background] No hay vacantes pendientes de aplicar");
            return;
        }

        console.log(`[Background] Procesando ${jobs.length} vacantes para auto-aplicar`);

        for (const job of jobs) {
            await processJobApplication(job);
        }
    } catch (error) {
        console.error("[Background] Error en auto-aplicación de vacantes:", error);
    }
}

async function processJobApplication(job: JobToApply): Promise<void> {
    if (!job.url) {
        console.log(`[Background] Vacante ${job.id} sin URL, se omite`);
        await updateJobStatus(job.id, "APPLICATION_FAILED", undefined, "Sin URL de vacante");
        return;
    }

    try {
        // Marcar como en proceso de aplicación
        await updateJobStatus(job.id, "APPLYING", undefined, "Iniciando auto-aplicación");

        // Abrir la vacante en una pestaña nueva
        const tab = await chrome.tabs.create({ url: job.url, active: false });

        // Esperar a que la página cargue y el content script detecte el formulario.
        // El flujo de autocompletado (FORM_DETECTED → autoApply) se encarga de
        // rellenar el formulario. Aquí esperamos un tiempo razonable.
        await new Promise((resolve) => setTimeout(resolve, 8000));

        // Verificar si el formulario fue completado
        try {
            const response = await chrome.tabs.sendMessage(tab.id!, { type: "READ_FORM" });
            if (response?.success) {
                // El formulario sigue presente → la aplicación no se completó
                await updateJobStatus(job.id, "APPLICATION_FAILED", undefined, "Formulario aún presente tras el intento");
            } else {
                // No hay formulario → la aplicación se completó (o no había formulario)
                await updateJobStatus(job.id, "APPLIED", { url: job.url }, "Auto-aplicación completada");
            }
        } catch {
            // El content script no respondió → asumimos que la aplicación se completó
            await updateJobStatus(job.id, "APPLIED", { url: job.url }, "Auto-aplicación completada");
        }

        // Cerrar la pestaña
        if (tab.id) {
            await chrome.tabs.remove(tab.id);
        }
    } catch (error) {
        console.error(`[Background] Error aplicando vacante ${job.id}:`, error);
        await updateJobStatus(job.id, "APPLICATION_FAILED", undefined, error instanceof Error ? error.message : "Error desconocido");
    }
}