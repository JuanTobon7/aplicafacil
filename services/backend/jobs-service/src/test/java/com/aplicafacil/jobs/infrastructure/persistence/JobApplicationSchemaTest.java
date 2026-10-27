package com.aplicafacil.jobs.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.jobs.domain.ApplicationProviderType;
import com.aplicafacil.jobs.domain.ApplicationStatus;
import com.aplicafacil.jobs.infrastructure.persistence.entity.JobApplicationEntity;

/** Flyway + validación de la entidad, y el conteo que usa el límite diario. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class JobApplicationSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Autowired
    SpringDataJobApplicationRepository applications;

    @Test
    void countsOnlySubmittedApplicationsSinceTheGivenInstant() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        applications.save(application(userId, "1", ApplicationStatus.SUBMITTED, now));
        applications.save(application(userId, "2", ApplicationStatus.FAILED, now));
        applications.save(application(userId, "3", ApplicationStatus.SUBMITTED, now.minus(2, ChronoUnit.DAYS)));
        applications.flush();

        long today = applications.countByUserIdAndStatusAndAttemptedAtGreaterThanEqual(
                userId, ApplicationStatus.SUBMITTED, now.minus(1, ChronoUnit.HOURS));

        assertThat(today).isEqualTo(1);
    }

    private static JobApplicationEntity application(UUID userId, String jobId, ApplicationStatus status,
                                                    Instant attemptedAt) {
        JobApplicationEntity application = new JobApplicationEntity();
        application.setId(UUID.randomUUID());
        application.setUserId(userId);
        application.setJobExternalId(jobId);
        application.setJobUrl("https://www.linkedin.com/jobs/view/" + jobId);
        application.setCompany("ACME");
        application.setProvider(ApplicationProviderType.LINKEDIN_EASY_APPLY);
        application.setStatus(status);
        application.setAttemptedAt(attemptedAt);
        application.setCreatedAt(attemptedAt);
        application.setUpdatedAt(attemptedAt);
        return application;
    }
}
