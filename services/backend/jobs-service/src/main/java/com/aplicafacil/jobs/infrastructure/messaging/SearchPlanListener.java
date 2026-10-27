package com.aplicafacil.jobs.infrastructure.messaging;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aplicafacil.jobs.application.SearchPlanService;
import com.aplicafacil.jobs.application.SearchPlanService.SearchPlan;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code profile.search-plan.ready} (contracts/schemas/search-plan-ready-event.schema.json): la IA eligió las búsquedas
 * del perfil y jobs-service las encola. Un mensaje que no cumple el contrato va a la DLQ.
 */
@Configuration
public class SearchPlanListener {

    static final String QUEUE = "jobs-service.search-plans";
    static final String READY = "profile.search-plan.ready";
    private static final String EVENTS_EXCHANGE = "aplicafacil.events";
    private static final Logger log = LoggerFactory.getLogger(SearchPlanListener.class);

    private final SearchPlanService plans;
    private final JsonMapper json;

    public SearchPlanListener(SearchPlanService plans, JsonMapper json) {
        this.plans = plans;
        this.json = json;
    }

    @Bean
    Declarables searchPlanTopology() {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        TopicExchange dlx = new TopicExchange(JobSearchMessaging.DEAD_LETTER_EXCHANGE);
        Queue queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange(JobSearchMessaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QUEUE)
                .build();
        Queue dlq = QueueBuilder.durable(QUEUE + ".dlq").build();
        return new Declarables(events, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(events).with(READY),
                BindingBuilder.bind(dlq).to(dlx).with(QUEUE));
    }

    @RabbitListener(queues = QUEUE)
    void onSearchPlan(byte[] body) {
        SearchPlan plan = parse(body);
        int queued = plans.launch(plan);
        log.info("Plan {} recibido: {} búsquedas encoladas", plan.planId(), queued);
    }

    private SearchPlan parse(byte[] body) {
        try {
            JsonNode node = json.readTree(body);
            List<String> searches = node.required("searches").valueStream().map(JsonNode::asString).toList();
            JsonNode days = node.path("postedWithinDays");
            JsonNode location = node.path("location");
            return new SearchPlan(UUID.fromString(node.required("planId").asString()),
                    UUID.fromString(node.required("userId").asString()), searches,
                    node.path("remoteOnly").asBoolean(false), days.isNumber() ? days.asInt() : null,
                    location.isString() ? location.asString() : null);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("Mensaje que no cumple el contrato: " + e.getMessage(), e);
        }
    }
}
