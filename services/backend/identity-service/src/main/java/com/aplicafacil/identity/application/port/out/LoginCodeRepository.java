package com.aplicafacil.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.aplicafacil.identity.domain.LoginCode;

/** Códigos de login ({@code login_code}). */
public interface LoginCodeRepository {

    void save(LoginCode code);

    Optional<LoginCode> findByHash(String codeHash);

    void markConsumed(String codeHash, Instant now);

    void deleteExpired(Instant now);
}
