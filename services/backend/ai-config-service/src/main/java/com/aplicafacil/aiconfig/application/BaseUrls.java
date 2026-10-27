package com.aplicafacil.aiconfig.application;

import java.net.URI;
import java.net.URISyntaxException;

import com.aplicafacil.aiconfig.application.exception.AiConfigException;
import com.aplicafacil.aiconfig.domain.ProviderType;

/** Valida la URL base que la persona indica para un proveedor. */
final class BaseUrls {

    private BaseUrls() {
    }

    /** Solo https, o http hacia la propia máquina (Ollama, LM Studio…). {@code null} = la del proveedor. */
    static String validate(ProviderType type, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        if (!type.allowsBaseUrl()) {
            throw AiConfigException.baseUrlNotAllowed();
        }
        URI uri = parse(baseUrl.strip());
        if (!isAcceptable(uri)) {
            throw AiConfigException.invalidBaseUrl();
        }
        return uri.toString();
    }

    /** Con host, sin credenciales en la URL, y https o http local. */
    private static boolean isAcceptable(URI uri) {
        String host = uri.getHost();
        return host != null && uri.getUserInfo() == null && (isHttps(uri) || isLoopbackHttp(uri, host));
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw AiConfigException.invalidBaseUrl();
        }
    }

    private static boolean isHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    private static boolean isLoopbackHttp(URI uri, String host) {
        return "http".equalsIgnoreCase(uri.getScheme())
                && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host));
    }
}
