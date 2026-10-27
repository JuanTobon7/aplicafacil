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
 * Computrabajo: el listado es público, así que no hay sesión ni pasa por linkedin-service. Va directo al scraper
 * ({@code POST /portals/computrabajo/jobs/search}) sin storageState.
 */
@Component
class ComputrabajoJobPortalGateway extends HttpJobPortalGateway {

    ComputrabajoJobPortalGateway(RestClient.Builder builder, JobsProperties properties, ServiceTokenProvider tokens,
                                 JsonMapper json) {
        super(builder, properties.scraperUrl(), properties.searchTimeout(), tokens, json);
    }

    @Override
    public JobPlatform platform() {
        return JobPlatform.COMPUTRABAJO;
    }

    @Override
    protected String path(UUID userId) {
        return "/portals/computrabajo/jobs/search";
    }

    @Override
    protected ObjectNode body(SearchCriteria criteria, JsonMapper json) {
        ObjectNode body = json.createObjectNode();
        criteria(body.putObject("criteria"), criteria);
        return body;
    }
}
