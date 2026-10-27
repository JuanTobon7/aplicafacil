package com.aplicafacil.identity.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.identity.infrastructure.security.SigningKeyService;

/** Llaves públicas con las que los demás servicios validan los JWT. */
@RestController
class JwksController {

    private final SigningKeyService keys;

    JwksController(SigningKeyService keys) {
        this.keys = keys;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return keys.publicJwks().toJSONObject(true);
    }
}
