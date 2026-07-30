import { Recommendation } from "./api/recommendations";
import { FormScanner } from "./readers/helpers/FormScanner";
import { WriterFactory } from "./writers/FactoryWriter";

// ------------------------------------------------------------------
console.log("[AutoApply] Content script loaded");
const scanner = FormScanner.getInstance();
console.log("[AutoApply] FormScanner instance created:", scanner);

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  console.log("[AutoApply] Message received:", message);

  if (message.type === "READ_FORM") {
    const form = scanner.scanOnDemand();
    sendResponse(form ? { success: true, form } : { success: false, message: "No form fields detected." });
    return true;
  }

  if (message.type === "APPLY_RECOMMENDATIONS") {
    const recommendations: Recommendation[] = message.recommendations ?? [];
    console.log("[AutoApply] Applying recommendations:", recommendations.length);

    try {
      const writer = WriterFactory.getWriter(window.location.hostname);

      if (!writer.available()) {
        console.log("[AutoApply] Writer no disponible para este sitio");
        sendResponse({ success: false, message: "Writer not available for this site." });
        return true;
      }

      writer.writeRecommendations(recommendations);
      sendResponse({ success: true });
    } catch (error) {
      console.error("[AutoApply] Error writing recommendations:", error);
      sendResponse({
        success: false,
        message: error instanceof Error ? error.message : "Unknown error writing recommendations.",
      });
    }

    return true;
  }
});

window.addEventListener("load", () => {
  console.log("[AutoApply] Page loaded, starting scanner");
  console.log("[AutoApply] Starting FormScanner");
  scanner.start();
});