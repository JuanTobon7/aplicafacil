package com.aplicafacil.jobs.infrastructure.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aplicafacil.jobs.application.RetryPolicy;
import com.aplicafacil.jobs.infrastructure.config.JobsProperties;

/**
 * Topología de las búsquedas de vacantes (contracts/events/asyncapi.yaml, ADR 0006).
 *
 * <pre>
 *  jobs-service --[aplicafacil.commands]-- jobs.search.requested --> jobs-service.job-searches --> worker
 *  jobs-service --[aplicafacil.commands]-- jobs.search.retry.{n} --> jobs-service.job-searches.wait.{n}
 *                    (TTL = espera antes del intento n) --dead-letter jobs.search.requested--> jobs-service.job-searches
 *  jobs-service --[aplicafacil.commands]-- jobs.search.redeliver --> jobs-service.job-searches.wait.redeliver (30 s)
 *  mensaje ilegible --> aplicafacil.dlx --> jobs-service.job-searches.dlq
 * </pre>
 */
@Configuration
public class JobSearchMessaging {

    public static final String COMMANDS_EXCHANGE = "aplicafacil.commands";
    public static final String DEAD_LETTER_EXCHANGE = "aplicafacil.dlx";
    public static final String REQUESTED = "jobs.search.requested";
    public static final String RETRY_PREFIX = "jobs.search.retry.";
    /** El mismo intento otra vez, más tarde: la BD u otra dependencia no respondió al recibirlo. */
    public static final String REDELIVER = "jobs.search.redeliver";
    static final Duration REDELIVER_DELAY = Duration.ofSeconds(30);
    public static final String QUEUE = "jobs-service.job-searches";
    static final String WAIT_PREFIX = QUEUE + ".wait.";
    static final String LISTENER_FACTORY = "jobSearchListenerFactory";

    @Bean
    Declarables jobSearchTopology(RetryPolicy retries) {
        TopicExchange commands = new TopicExchange(COMMANDS_EXCHANGE);
        TopicExchange dlx = new TopicExchange(DEAD_LETTER_EXCHANGE);
        Queue work = QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QUEUE)
                .build();
        Queue dlq = QueueBuilder.durable(QUEUE + ".dlq").build();

        List<Declarable> topology = new ArrayList<>(List.of(commands, dlx, work, dlq,
                bind(work, commands, REQUESTED), bind(dlq, dlx, QUEUE)));
        for (int attempt = 2; attempt <= retries.maxAttempts(); attempt++) {
            Queue wait = waitQueue(WAIT_PREFIX + attempt, retries.delayBefore(attempt));
            topology.add(wait);
            topology.add(bind(wait, commands, RETRY_PREFIX + attempt));
        }
        Queue redeliver = waitQueue(WAIT_PREFIX + "redeliver", REDELIVER_DELAY);
        topology.add(redeliver);
        topology.add(bind(redeliver, commands, REDELIVER));
        return new Declarables(topology);
    }

    /** Sin consumidores: al vencer el TTL el mensaje vuelve a la cola de trabajo. */
    private static Queue waitQueue(String name, Duration delay) {
        return QueueBuilder.durable(name)
                .ttl(Math.toIntExact(delay.toMillis()))
                .deadLetterExchange(COMMANDS_EXCHANGE)
                .deadLetterRoutingKey(REQUESTED)
                .build();
    }

    /**
     * Un mensaje a la vez por consumidor (cada búsqueda tarda minutos) y {@code concurrency} consumidores por instancia.
     * Lo que no se puede leer va a la DLQ en vez de volver a la cola.
     */
    @Bean(LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory jobSearchListenerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
                                                                   ConnectionFactory connections,
                                                                   JobsProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connections);
        JobsProperties.Worker worker = properties.worker();
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(worker.concurrency());
        factory.setMaxConcurrentConsumers(worker.concurrency());
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(worker.enabled());
        return factory;
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
