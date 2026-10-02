package com.aplicafacil.automation.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ entre automation-service (Java) y el scraper (Node).
 *
 * <pre>
 *  automation ──[aplicafacil.commands]── scraper.apply-job  ──▶ scraper
 *                                       scraper.search-jobs ──▶ scraper
 *  scraper    ──[aplicafacil.events]──── automation.job-events ──▶ automation
 * </pre>
 *
 * Los payloads están definidos en contracts/schemas (JSON Schema).
 * Mensajes que fallan van a la dead-letter queue correspondiente.
 */
@Configuration
public class MessagingConfig {

    public static final String COMMANDS_EXCHANGE = "aplicafacil.commands";
    public static final String EVENTS_EXCHANGE = "aplicafacil.events";
    public static final String DEAD_LETTER_EXCHANGE = "aplicafacil.dlx";

    public static final String APPLY_JOB_QUEUE = "scraper.apply-job";
    public static final String SEARCH_JOBS_QUEUE = "scraper.search-jobs";
    public static final String JOB_EVENTS_QUEUE = "automation.job-events";

    public static final String APPLY_JOB_ROUTING_KEY = "apply.job";
    public static final String SEARCH_JOBS_ROUTING_KEY = "search.jobs";
    public static final String JOB_EVENTS_ROUTING_KEY = "job.#";

    @Bean
    Declarables messagingTopology() {
        TopicExchange commands = new TopicExchange(COMMANDS_EXCHANGE);
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        TopicExchange dlx = new TopicExchange(DEAD_LETTER_EXCHANGE);

        Queue applyJob = durableWithDlq(APPLY_JOB_QUEUE);
        Queue searchJobs = durableWithDlq(SEARCH_JOBS_QUEUE);
        Queue jobEvents = durableWithDlq(JOB_EVENTS_QUEUE);

        Queue applyJobDlq = QueueBuilder.durable(APPLY_JOB_QUEUE + ".dlq").build();
        Queue searchJobsDlq = QueueBuilder.durable(SEARCH_JOBS_QUEUE + ".dlq").build();
        Queue jobEventsDlq = QueueBuilder.durable(JOB_EVENTS_QUEUE + ".dlq").build();

        return new Declarables(
                commands, events, dlx,
                applyJob, searchJobs, jobEvents,
                applyJobDlq, searchJobsDlq, jobEventsDlq,
                bind(applyJob, commands, APPLY_JOB_ROUTING_KEY),
                bind(searchJobs, commands, SEARCH_JOBS_ROUTING_KEY),
                bind(jobEvents, events, JOB_EVENTS_ROUTING_KEY),
                bind(applyJobDlq, dlx, APPLY_JOB_QUEUE),
                bind(searchJobsDlq, dlx, SEARCH_JOBS_QUEUE),
                bind(jobEventsDlq, dlx, JOB_EVENTS_QUEUE));
    }

    /** JSON en los mensajes: el scraper (Node) los lee/escribe como JSON plano. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    private static Queue durableWithDlq(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(name)
                .build();
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
