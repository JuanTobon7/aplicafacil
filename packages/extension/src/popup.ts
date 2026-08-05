import { postRecommendations, Recommendation } from "./api/recommendations";
import { JobForm } from "./core/types/forms";

// ------------------------------------------------------------------
// Popup: flujo manual "Escanear y autocompletar".
//   1. Pide el formulario al content script (vía background)
//   2. Envía el formulario a la API para obtener recomendaciones
//   3. Aplica las recomendaciones en la pestaña activa
// ------------------------------------------------------------------

const statusEl = document.getElementById("status")!;
const scanBtn = document.getElementById("scan") as HTMLButtonElement;
const autoApplyCheckbox = document.getElementById("autoApply") as HTMLInputElement;

const AUTO_APPLY_KEY = "autoApplyEnabled";

function setStatus(message: string, kind: "info" | "ok" | "error" = "info"): void {
  statusEl.textContent = message;
  if (kind === "error") statusEl.className = "error";
  else if (kind === "ok") statusEl.className = "ok";
  else statusEl.className = "";
}

async function getActiveTabId(): Promise<number> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id) throw new Error("No hay pestaña activa.");
  return tab.id;
}

async function scanForm(): Promise<JobForm> {
  const response = await chrome.runtime.sendMessage({ type: "READ_ACTIVE_FORM" });

  if (!response?.success) {
    throw new Error(response?.message || "No se detectó ningún formulario.");
  }
  if (!response.form?.fields?.length) {
    throw new Error("El formulario no tiene campos detectables.");
  }

  return response.form;
}

async function applyRecommendations(tabId: number, recommendations: Recommendation[]): Promise<void> {
  const response = await chrome.tabs.sendMessage(tabId, {
    type: "APPLY_RECOMMENDATIONS",
    recommendations,
  });

  if (!response?.success) {
    throw new Error(response?.message || "No se pudieron aplicar las recomendaciones.");
  }
}

async function handleScan(): Promise<void> {
  scanBtn.disabled = true;
  setStatus("Escaneando formulario…");

  try {
    const tabId = await getActiveTabId();
    const form = await scanForm();

    setStatus(`Formulario detectado (${form.fields.length} campos). Generando recomendaciones…`);

    const recommendations = await postRecommendations(form);

    if (!recommendations.length) {
      setStatus("No se recibieron recomendaciones de la API.", "error");
      return;
    }

    setStatus(`Aplicando ${recommendations.length} recomendaciones…`);
    await applyRecommendations(tabId, recommendations);

    const applied = recommendations.filter(r => r.value !== null && r.value !== undefined).length;
    setStatus(`✅ Autocompletado: ${applied} campos.\nRevisa el formulario antes de enviar.`, "ok");
  } catch (error) {
    console.error("[Popup] Error:", error);
    setStatus(`❌ ${error instanceof Error ? error.message : "Error desconocido"}`, "error");
  } finally {
    scanBtn.disabled = false;
  }
}

async function init(): Promise<void> {
  scanBtn.addEventListener("click", handleScan);

  // Persistir el toggle de autocompletado automático
  const stored = await chrome.storage.local.get(AUTO_APPLY_KEY);
  autoApplyCheckbox.checked = stored[AUTO_APPLY_KEY] !== false; // default true

  autoApplyCheckbox.addEventListener("change", () => {
    chrome.storage.local.set({ [AUTO_APPLY_KEY]: autoApplyCheckbox.checked });
  });
}

init();