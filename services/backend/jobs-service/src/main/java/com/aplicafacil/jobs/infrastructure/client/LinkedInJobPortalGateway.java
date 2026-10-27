package com.aplicafacil.jobs.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.SearchCriteria;
import com.aplicafacil.jobs.infrastructure.config.JobsProperties;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * LinkedIn: la búsqueda necesita la sesión del usuario, que solo maneja linkedin-service (cifrada, con lease por
 * usuario). jobs-service nunca ve el storageState: pide {@code POST /internal/sessions/{userId}/job-search} y
 * linkedin-service le pide al scraper que abra el navegador, busque y recorra las cards.
 */
@Component
class LinkedInJobPortalGateway extends HttpJobPortalGateway {

    LinkedInJobPortalGateway(RestClient.Builder builder, JobsProperties properties, ServiceTokenProvider tokens,
                             JsonMapper json) {
        super(builder, properties.linkedinServiceUrl(), properties.searchTimeout(), tokens, json);
    }

    @Override
    public JobPlatform platform() {
        return JobPlatform.LINKEDIN;
    }

    @Override
    protected String path(UUID userId) {
        return "/internal/sessions/" + userId + "/job-search";
    }

    @Override
    protected ObjectNode body(SearchCriteria criteria, JsonMapper json) {
        return criteria(json.createObjectNode(), criteria);
    }
}
