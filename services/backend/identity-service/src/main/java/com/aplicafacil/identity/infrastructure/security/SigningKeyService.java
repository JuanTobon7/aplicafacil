package com.aplicafacil.identity.infrastructure.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.aplicafacil.identity.infrastructure.persistence.SpringDataSigningKeyRepository;
import com.aplicafacil.identity.infrastructure.persistence.entity.SigningKeyEntity;
import com.aplicafacil.platform.crypto.EncryptedPayload;
import com.aplicafacil.platform.crypto.SessionCryptoService;
import com.aplicafacil.platform.crypto.SessionDecryptionException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Llave RS256 con la que se firman los JWT. Se genera la primera vez y se guarda en
 * {@code signing_key}, con la privada cifrada con {@link SessionCryptoService}. Así los tokens sobreviven
 * a los reinicios. Un advisory lock evita que dos instancias generen dos llaves activas a la vez.
 */
@Component
public class SigningKeyService {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyService.class);
    private static final long GENERATION_LOCK = 0x51_6E_4B_65_79L;

    private final RSAKey activeKey;
    private final JWKSet publicKeys;

    public SigningKeyService(SpringDataSigningKeyRepository keys, SessionCryptoService crypto,
                             JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
        this.activeKey = tx.execute(status -> {
            jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, GENERATION_LOCK);
            SigningKeyEntity active = keys.findByActiveTrue().orElse(null);
            if (active != null) {
                try {
                    return toRsaKey(active, crypto);
                } catch (SessionDecryptionException e) {
                    // Cambió SESSION_ENCRYPTION_KEY: la llave vieja es irrecuperable. Se retira y los
                    // usuarios vuelven a elegir "Continuar como…" (la sesión de LinkedIn también quedará EXPIRED).
                    log.warn("No se pudo descifrar la llave de firma kid={}; se genera una nueva", active.getKid());
                    active.setActive(false);
                    active.setRetiredAt(clock.instant());
                    keys.saveAndFlush(active);
                }
            }
            return toRsaKey(keys.save(generate(crypto, clock)), crypto);
        });
        List<JWK> all = keys.findAllByOrderByCreatedAtDesc().stream()
                .map(SigningKeyService::publicOnly)
                .map(JWK.class::cast)
                .toList();
        this.publicKeys = new JWKSet(all);
        log.info("Llave de firma activa: kid={}", activeKey.getKeyID());
    }

    /** Llave privada activa para firmar. */
    public RSAKey activeKey() {
        return activeKey;
    }

    public RSAPublicKey activePublicKey() {
        try {
            return activeKey.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lo que se publica en {@code /.well-known/jwks.json}: solo llaves públicas. */
    public JWKSet publicJwks() {
        return publicKeys;
    }

    private static SigningKeyEntity generate(SessionCryptoService crypto, Clock clock) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            EncryptedPayload privateKey = crypto.encrypt(
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));

            SigningKeyEntity entity = SigningKeyEntity.builder()
                    .kid(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.RS256.getName())
                    .publicKey(pair.getPublic().getEncoded())
                    .privateKeyEncrypted(privateKey.ciphertext())
                    .privateKeyIv(privateKey.iv())
                    .keyVersion(privateKey.keyVersion())
                    .active(true)
                    .createdAt(clock.instant())
                    .build();
            log.info("Generada una nueva llave de firma RS256: kid={}", entity.getKid());
            return entity;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey toRsaKey(SigningKeyEntity entity, SessionCryptoService crypto) {
        try {
            byte[] pkcs8 = Base64.getDecoder().decode(crypto.decrypt(new EncryptedPayload(
                    entity.getPrivateKeyEncrypted(), entity.getPrivateKeyIv(), entity.getKeyVersion())));
            RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            return new RSAKey.Builder(publicKey(entity))
                    .privateKey(privateKey)
                    .keyID(entity.getKid())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Llave de firma inválida (kid=" + entity.getKid() + ")", e);
        }
    }

    private static RSAKey publicOnly(SigningKeyEntity entity) {
        return new RSAKey.Builder(publicKey(entity))
                .keyID(entity.getKid())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    private static RSAPublicKey publicKey(SigningKeyEntity entity) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(entity.getPublicKey()));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }
}
