package com.aplicafacil.gateway.local;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Token aleatorio generado en cada arranque. El front lo recibe dentro del {@code index.html} que sirve el
 * gateway y lo envía en {@code X-Local-Token}. Una página externa no puede leer ese HTML (política de mismo
 * origen), así que no puede llamar la API local. Nunca se escribe en disco ni en logs.
 */
@Component
public class LocalToken {

    public static final String HEADER = "X-Local-Token";

    private final String value;

    public LocalToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        this.value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String value() {
        return value;
    }

    boolean matches(String candidate) {
        return candidate != null && MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }
}
