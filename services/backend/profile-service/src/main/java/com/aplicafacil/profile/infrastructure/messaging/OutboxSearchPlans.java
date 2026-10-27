package com.aplicafacil.profile.infrastructure.messaging;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aplicafacil.profile.application.port.out.SearchPlans;
import com.aplicafacil.profile.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.aplicafacil.profile.infrastructure.persistence.entity.OutboxEventEntity;

import tools.jackson.databind.json.JsonMapper;

/** {@code profile.search-plan.ready} (contracts/schemas/search-plan-ready-event.schema.json) por el outbox. */
@Component
public class OutboxSearchPlans implements SearchPlans {

    static final int SCHEMA_VERSION = 1;
    static final String READY = "profile.search-plan.ready";

    private final SpringDataOutboxEventRepository outbox;
    private final JsonMapper json;

    public OutboxSearchPlans(SpringDataOutboxEventRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Override
    public void publish(SearchPlan plan) {
        UUID eventId = UUID.randomUUID();
        SearchPlanReady event = new SearchPlanReady(SCHEMA_VERSION, eventId, plan.planId(), plan.userId(),
                plan.searches(), plan.remoteOnly(), plan.postedWithinDays(), plan.location(),
                plan.occurredAt().toString());
        outbox.save(OutboxEventEntity.builder()
                .id(eventId)
                .aggregateType("ai_task")
                .aggregateId(plan.planId())
                .exchange(ProfileAiMessaging.EVENTS_EXCHANGE)
                .eventType(READY)
                .payload(json.writeValueAsString(event))
                .createdAt(plan.occurredAt())
                .build());
    }

    record SearchPlanReady(int schemaVersion, UUID eventId, UUID planId, UUID userId, List<String> searches,
                           boolean remoteOnly, Integer postedWithinDays, String location, String occurredAt) {
    }
}
