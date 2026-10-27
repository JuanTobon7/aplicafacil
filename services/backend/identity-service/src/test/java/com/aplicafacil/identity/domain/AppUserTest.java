package com.aplicafacil.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.linkedin.contract.IdentitySource;
import com.aplicafacil.linkedin.contract.LinkedInIdentity;
import com.aplicafacil.linkedin.contract.ProfileBasics;

/** Reglas de actualización del usuario, sin Spring ni base de datos. */
class AppUserTest {

    private final Instant now = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void aVerifiedEmailIsNeverReplacedByAnUnverifiedOne() {
        AppUser user = AppUser.create(UUID.randomUUID(), now);
        user.apply(identity("ana@example.com", true, "Backend"), now);

        user.apply(identity("otra@example.com", false, "Backend"), now);

        assertThat(user.links().email()).isEqualTo("ana@example.com");
        assertThat(user.links().emailVerified()).isTrue();
    }

    @Test
    void editedFieldsAndUnreadValuesAreKept() {
        AppUser user = AppUser.restore(UUID.randomUUID(), now,
                new AppUser.Links("urn:li:member:1", null, null, false),
                new AppUser.Basics("Ana", "Pérez", "Mi titular", null, null, "Medellín"),
                Set.of("headline"), new AppUser.Activity(now, now));

        user.apply(identity(null, false, "Tech Lead"), now.plusSeconds(1));

        assertThat(user.basics().headline()).isEqualTo("Mi titular");
        assertThat(user.basics().location()).isEqualTo("Medellín");
        assertThat(user.activity().lastLoginAt()).isEqualTo(now.plusSeconds(1));
    }

    @Test
    void linkableOnlyWhenItLacksThatKindOfIdentifier() {
        AppUser user = AppUser.create(UUID.randomUUID(), now);
        user.apply(identity("ana@example.com", true, "Backend"), now);

        assertThat(user.linkableFrom(IdentitySource.SESSION)).isFalse();
        assertThat(user.linkableFrom(IdentitySource.OIDC)).isTrue();
        assertThat(user.displayName()).isEqualTo("Ana Pérez");
    }

    private static LinkedInIdentity identity(String email, boolean verified, String headline) {
        return new LinkedInIdentity(IdentitySource.SESSION, "urn:li:member:1", null, email, verified,
                new ProfileBasics("Ana", "Pérez", headline, null, null, null));
    }
}
