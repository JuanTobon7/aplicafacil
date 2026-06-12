chrome.runtime.onMessage.addListener(
    async (message, sender, sendResponse) => {

        console.log("[Background]",message);
        
        if (message.type === "FORM_DETECTED") {
        console.log("Formulario detectado automáticamente");
        console.log("metadata:", message.form.metadata);
        console.log("fields count:", message.form.fields.length);
        return;
        }

        if (message.type !== "READ_ACTIVE_FORM")return;

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