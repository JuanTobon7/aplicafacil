package com.aplicafacil.identity.application.services;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.identity.application.exception.IdentityException;
import com.aplicafacil.identity.application.port.out.LoginCodeRepository;
import com.aplicafacil.identity.domain.LoginCode;

/**
 * Código de un solo uso que une el intento de conexión (linkedin-service) con la emisión de tokens.
 * El front lo recibe por SSE y lo canjea junto con el {@code attemptId}; solo se guarda su hash.
 */
@Service
public class LoginCodeService {

    private final LoginCodeRepository codes;
    private final TokenPolicy policy;
    private final Clock clock;

    public LoginCodeService(LoginCodeRepository codes, TokenPolicy policy, Clock clock) {
        this.codes = codes;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public String create(UUID userId, UUID attemptId) {
        String code = SecureTokens.random();
        codes.save(new LoginCode(SecureTokens.sha256Hex(code), userId, attemptId,
                clock.instant().plus(policy.loginCodeTtl()), null));
        return code;
    }

    /** @return el usuario al que pertenece el código; lo marca usado */
    @Transactional
    public UUID consume(UUID attemptId, String code) {
        if (code == null || code.isBlank()) {
            throw IdentityException.invalidLoginCode();
        }
        Instant now = clock.instant();
        LoginCode found = codes.findByHash(SecureTokens.sha256Hex(code))
                .filter(c -> c.redeemableFor(attemptId, now))
                .orElseThrow(IdentityException::invalidLoginCode);
        codes.markConsumed(found.codeHash(), now);
        return found.userId();
    }

    @Scheduled(fixedDelay = 600_000)
    @Transactional
    public void purgeExpired() {
        codes.deleteExpired(clock.instant());
    }
}
