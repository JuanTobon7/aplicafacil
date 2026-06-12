async function init() {
    console.log("🚀 popup SCRIPT CARGADO");
    alert("popup SCRIPT CARGADO");
    const response = await chrome.runtime.sendMessage({
        type: "READ_ACTIVE_FORM"
    });

    console.log(response);
}

init();