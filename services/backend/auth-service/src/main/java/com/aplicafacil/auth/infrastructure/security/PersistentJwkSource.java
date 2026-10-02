package com.aplicafacil.auth.infrastructure.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Llaves RSA de firma guardadas en {@code signing_keys}.
 *
 * <ul>
 *   <li>Al primer uso, si no hay ninguna activa, genera una (RSA 2048).</li>
 *   <li>Un advisory lock de Postgres evita que dos instancias generen a la vez.</li>
 *   <li>Publica todas las llaves (para validar tokens firmados antes de una
 *       rotación) y la activa más reciente va primero: es la que firma.</li>
 * </ul>
 *
 * <p>Nota: la llave privada se guarda sin cifrar en la BD. En producción
 * conviene cifrarla (KMS / Vault) o cargarla desde un secreto.
 */
public class PersistentJwkSource implements JWKSource<SecurityContext> {

    private static final Logger log = LoggerFactory.getLogger(PersistentJwkSource.class);
    private static final String ALGORITHM = "RS256";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private volatile JWKSet jwkSet;

    public PersistentJwkSource(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) {
        return selector.select(keys());
    }

    /** Fuerza recargar desde la BD (p. ej. después de rotar llaves). */
    public void reload() {
        jwkSet = null;
    }

    private JWKSet keys() {
        JWKSet current = jwkSet;
        if (current == null) {
            synchronized (this) {
                if (jwkSet == null) {
                    jwkSet = transactions.execute(status -> loadOrCreate());
                }
                current = jwkSet;
            }
        }
        return current;
    }

    private JWKSet loadOrCreate() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext('auth.signing_keys'))", Object.class);
        Integer active = jdbc.queryForObject("SELECT count(*) FROM signing_keys WHERE active", Integer.class);
        if (active == null || active == 0) {
            insertNewKey();
        }
        List<JWK> keys = jdbc.query(
                "SELECT kid, public_key, private_key FROM signing_keys ORDER BY active DESC, created_at DESC",
                (rs, row) -> toJwk(rs.getString("kid"), rs.getString("public_key"), rs.getString("private_key")));
        return new JWKSet(keys);
    }

    private void insertNewKey() {
        KeyPair pair = generateRsaKeyPair();
        String kid = UUID.randomUUID().toString();
        Base64.Encoder base64 = Base64.getEncoder();
        jdbc.update("INSERT INTO signing_keys (kid, algorithm, public_key, private_key, active) VALUES (?, ?, ?, ?, TRUE)",
                kid, ALGORITHM,
                base64.encodeToString(pair.getPublic().getEncoded()),
                base64.encodeToString(pair.getPrivate().getEncoded()));
        log.info("Generada nueva llave de firma RSA (kid={})", kid);
    }

    private static JWK toJwk(String kid, String publicKey, String privateKey) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            Base64.Decoder base64 = Base64.getDecoder();
            var publicRsa = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(base64.decode(publicKey)));
            var privateRsa = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(base64.decode(privateKey)));
            return new RSAKey.Builder(publicRsa)
                    .privateKey(privateRsa)
                    .keyID(kid)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Llave de firma inválida (kid=" + kid + ")", e);
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
