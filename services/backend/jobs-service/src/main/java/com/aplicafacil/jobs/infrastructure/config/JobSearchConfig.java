package com.aplicafacil.jobs.infrastructure.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.aplicafacil.jobs.application.JobPortalGateways;
import com.aplicafacil.jobs.application.JobSearchFacade;
import com.aplicafacil.jobs.application.JobSearchOutcomes;
import com.aplicafacil.jobs.application.JobSearchWorker;
import com.aplicafacil.jobs.application.RetryPolicy;
import com.aplicafacil.jobs.application.port.out.JobSearchStates;

/**
 * Arma el worker de búsquedas. Las búsquedas llegan por RabbitMQ ({@code JobSearchMessaging}); el scheduling queda para
 * el publicador del outbox.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobsProperties.class)
public class JobSearchConfig {

    private static final Logger log = LoggerFactory.getLogger(JobSearchConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RetryPolicy jobSearchRetryPolicy(JobsProperties properties) {
        JobsProperties.Worker w = properties.worker();
        return new RetryPolicy(w.maxAttempts(), w.retryBackoff(), w.maxRetryBackoff());
    }

    @Bean
    JobSearchWorker jobSearchWorker(JobSearchStates states, JobSearchFacade facade, JobSearchOutcomes outcomes,
                                    RetryPolicy retries, JobsProperties properties, JobPortalGateways gateways,
                                    Clock clock) {
        JobsProperties.Worker w = properties.worker();
        if (w.enabled()) {
            log.info("Worker de búsquedas activo: portales {}, {} a la vez, {} intentos (consume "
                    + "jobs-service.job-searches de RabbitMQ)", gateways.supported(), w.concurrency(), w.maxAttempts());
        } else {
            log.warn("Worker de búsquedas DESACTIVADO (JOB_SEARCH_WORKER_ENABLED=false): las búsquedas quedan en la "
                    + "cola de RabbitMQ");
        }
        return new JobSearchWorker(states, facade, outcomes, retries, clock);
    }
}
