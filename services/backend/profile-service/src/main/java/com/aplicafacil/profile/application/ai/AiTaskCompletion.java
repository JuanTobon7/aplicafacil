package com.aplicafacil.profile.application.ai;

import java.time.Instant;

import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;

/**
 * Qué hacer con la respuesta de la IA según el tipo de tarea (Strategy). Un tipo nuevo es una implementación nueva;
 * {@link AiResultService} no cambia. Una respuesta ilegible sale como {@code InvalidAiOutputException}.
 */
interface AiTaskCompletion {

    AiTaskKind kind();

    void apply(AiTask task, String output, Instant now);
}
