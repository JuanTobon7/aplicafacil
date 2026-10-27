package com.aplicafacil.identity.application.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.identity.application.port.in.UserProvisioningService.Outcome;
import com.aplicafacil.identity.application.port.in.UserProvisioningService.ProvisioningResult;
import com.aplicafacil.identity.infrastructure.persistence.JpaUserRepository;
import com.aplicafacil.identity.infrastructure.persistence.SpringDataAppUserRepository;
import com.aplicafacil.identity.infrastructure.persistence.entity.AppUserEntity;
import com.aplicafacil.linkedin.contract.IdentitySource;
import com.aplicafacil.linkedin.contract.LinkedInIdentity;
import com.aplicafacil.linkedin.contract.ProfileBasics;

/** Provisión de usuarios contra PostgreSQL real: nuevo, existente y vinculación por correo. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserProvisioningServiceImpl.class, JpaUserRepository.class, UserProvisioningServiceImplTest.ClockConfig.class})
@Testcontainers
class UserProvisioningServiceImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Autowired
    UserProvisioningServiceImpl provisioning;

    @Autowired
    SpringDataAppUserRepository users;

    @Autowired
    TestEntityManager em;

    @Test
    void createsANewUserFromASession() {
        ProvisioningResult result = provisioning.provision(session("urn:li:fsd_profile:ANA", "ana@example.com", "Backend"));

        assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
        AppUserEntity user = reload(result);
        assertThat(user.getLinkedinMemberUrn()).isEqualTo("urn:li:fsd_profile:ANA");
        assertThat(user.getLinkedinOidcSub()).isNull();
        assertThat(user.getFirstName()).isEqualTo("Ana");
        assertThat(user.getHeadline()).isEqualTo("Backend");
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void reconnectingTheSameMemberUpdatesInsteadOfDuplicating() {
        ProvisioningResult first = provisioning.provision(session("urn:li:fsd_profile:ANA", null, "Backend"));
        ProvisioningResult second = provisioning.provision(session("urn:li:fsd_profile:ANA", null, "Tech Lead"));

        assertThat(second.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(users.count()).isEqualTo(1);
        assertThat(reload(second).getHeadline()).isEqualTo("Tech Lead");
    }

    @Test
    void manualEditsSurviveASync() {
        ProvisioningResult created = provisioning.provision(session("urn:li:fsd_profile:ANA", null, "Backend"));
        AppUserEntity user = reload(created);
        user.setHeadline("Mi titular editado");
        user.setEditedFields(Set.of("headline"));
        em.flush();

        provisioning.provision(session("urn:li:fsd_profile:ANA", null, "Tech Lead"));

        assertThat(reload(created).getHeadline()).isEqualTo("Mi titular editado");
    }

    @Test
    void anotherMemberGetsItsOwnUser() {
        ProvisioningResult ana = provisioning.provision(session("urn:li:fsd_profile:ANA", null, "Backend"));
        ProvisioningResult luis = provisioning.provision(session("urn:li:fsd_profile:LUIS", null, "Frontend"));

        assertThat(luis.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(luis.userId()).isNotEqualTo(ana.userId());
    }

    @Test
    void oidcLoginLinksToTheSessionAccountByVerifiedEmail() {
        ProvisioningResult local = provisioning.provision(verifiedSession("urn:li:fsd_profile:ANA", "Ana@Example.com"));

        ProvisioningResult oidc = provisioning.provision(oidc("oidc-ana", "ana@example.com", true));

        assertThat(oidc.outcome()).isEqualTo(Outcome.LINKED);
        assertThat(oidc.userId()).isEqualTo(local.userId());
        AppUserEntity user = reload(oidc);
        assertThat(user.getLinkedinMemberUrn()).isEqualTo("urn:li:fsd_profile:ANA");
        assertThat(user.getLinkedinOidcSub()).isEqualTo("oidc-ana");
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void unverifiedEmailNeverLinks() {
        ProvisioningResult local = provisioning.provision(verifiedSession("urn:li:fsd_profile:ANA", "ana@example.com"));

        ProvisioningResult oidc = provisioning.provision(oidc("oidc-x", "ana@example.com", false));

        assertThat(oidc.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(oidc.userId()).isNotEqualTo(local.userId());
    }

    @Test
    void doesNotLinkToAnAccountThatAlreadyHasAnotherMember() {
        provisioning.provision(verifiedSession("urn:li:fsd_profile:ANA", "shared@example.com"));

        ProvisioningResult other = provisioning.provision(verifiedSession("urn:li:fsd_profile:OTRA", "shared@example.com"));

        assertThat(other.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(reload(other).isEmailVerified()).isFalse();
    }

    private AppUserEntity reload(ProvisioningResult result) {
        em.flush();
        em.clear();
        return users.findById(result.userId()).orElseThrow();
    }

    private static LinkedInIdentity session(String urn, String email, String headline) {
        return new LinkedInIdentity(IdentitySource.SESSION, urn, null, email, false,
                new ProfileBasics("Ana", "Pérez", headline, null, null, "Medellín"));
    }

    private static LinkedInIdentity verifiedSession(String urn, String email) {
        return new LinkedInIdentity(IdentitySource.SESSION, urn, null, email, true,
                new ProfileBasics("Ana", "Pérez", "Backend", null, null, null));
    }

    private static LinkedInIdentity oidc(String sub, String email, boolean verified) {
        return new LinkedInIdentity(IdentitySource.OIDC, null, sub, email, verified,
                new ProfileBasics("Ana", "Pérez", null, null, null, null));
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
