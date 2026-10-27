package com.aplicafacil.jobs.infrastructure.messaging;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.aplicafacil.jobs.application.JobSearchWorker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor de {@code jobs-service.job-searches}. Confirma el mensaje al terminar el intento; si la instancia muere
 * antes, RabbitMQ lo reentrega a otra. Si la BD no responde, el mismo intento se reintenta en 30 s (cola de espera);
 * solo un mensaje ilegible va a la DLQ.
 */
@Component
public class JobSearchListener {

    private static final Logger log = LoggerFactory.getLogger(JobSearchListener.class);

    private final JobSearchWorker worker;
    private final RabbitTemplate rabbit;
    private final JsonMapper json;

    public JobSearchListener(JobSearchWorker worker, RabbitTemplate rabbit, JsonMapper json) {
        this.worker = worker;
        this.rabbit = rabbit;
        this.json = json;
    }

    @RabbitListener(queues = JobSearchMessaging.QUEUE, containerFactory = JobSearchMessaging.LISTENER_FACTORY)
    public void onRequested(Message message) {
        Command command = parse(message.getBody());
        try {
            worker.handle(command.searchId(), command.attempt());
        } catch (RuntimeException e) {
            // El worker ya resuelve los fallos del portal; esto es BD caída o similar: el mismo intento, más tarde
            log.warn("No se pudo procesar la búsqueda {} (intento {}); se reintenta en 30 s: {}", command.searchId(),
                    command.attempt(), e.getMessage());
            redeliverLater(message);
        }
    }

    private void redeliverLater(Message message) {
        try {
            rabbit.send(JobSearchMessaging.COMMANDS_EXCHANGE, JobSearchMessaging.REDELIVER, message);
        } catch (RuntimeException e) {
            // Tampoco se pudo publicar: que RabbitMQ lo devuelva a la cola tal cual
            throw new ImmediateRequeueAmqpException("No se pudo reprogramar la búsqueda", e);
        }
    }

    private Command parse(byte[] body) {
        try {
            JsonNode node = json.readTree(body);
            int attempt = node.path("attempt").asInt(0);
            if (attempt < 1) {
                throw new IllegalArgumentException("attempt inválido");
            }
            return new Command(UUID.fromString(node.path("searchId").asString()), attempt);
        } catch (RuntimeException e) {
            log.warn("jobs.search.requested ilegible; va a la DLQ: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Mensaje ilegible", e);
        }
    }

    private record Command(UUID searchId, int attempt) {
    }
}
