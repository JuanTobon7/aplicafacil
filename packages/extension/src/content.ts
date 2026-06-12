import { ReaderFactory } from "./readers/FactoryReader";

let lastScannedUrl = "";

function scanCurrentPage() {

    const reader =
        ReaderFactory.getReader(
            window.location.hostname
        );

    console.log(
        "[AutoApply] Reader:",
        reader.constructor.name
    );

    if (!reader.available()) {
        return null;
    }

    const jobForm = reader.readContent();

    if (!jobForm.fields.length) {
        return null;
    }

    console.log(
        "[AutoApply] Formulario detectado:",
        jobForm
    );

    return jobForm;
}

function executeScan() {

    const form = scanCurrentPage();

    if (!form) {
        return;
    }

    chrome.runtime.sendMessage({
        type: "FORM_DETECTED",
        form
    });
}

/**
 * Mantiene compatibilidad con popup/background
 */
chrome.runtime.onMessage.addListener(
    (message, sender, sendResponse) => {

        console.log(
            "[AutoApply] Mensaje recibido:",
            message
        );

        if (message.type !== "READ_FORM") {
            return;
        }

        const form = scanCurrentPage();

        if (!form) {

            sendResponse({
                success: false,
                message: "No form fields detected."
            });

            return;
        }

        sendResponse({
            success: true,
            form
        });

        return true;
    }
);

/**
 * Primera ejecución
 */
window.addEventListener("load", () => {

    console.log(
        "[AutoApply] Page loaded"
    );

    executeScan();
});

/**
 * Detectar formularios renderizados dinámicamente
 */
const observer =
    new MutationObserver(() => {

        const form =
            document.querySelector(
                "form,input,textarea,select"
            );

        if (!form) {
            return;
        }

        executeScan();

    });

observer.observe(
    document.documentElement,
    {
        childList: true,
        subtree: true
    }
);

/**
 * Detectar cambios de URL en SPA
 * LinkedIn, Workable, Greenhouse, etc.
 */
setInterval(() => {

    if (lastScannedUrl === location.href) {
        return;
    }

    lastScannedUrl = location.href;

    console.log(
        "[AutoApply] URL cambió:",
        location.href
    );

    executeScan();

}, 1000);