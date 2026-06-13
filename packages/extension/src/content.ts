import { FormScanner } from "./readers/helpers/FormScanner";

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
});

window.addEventListener("load", () => {
  console.log("[AutoApply] Page loaded, starting scanner");
  console.log("[AutoApply] Starting FormScanner");
  scanner.start();
});