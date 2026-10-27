package com.aplicafacil.profile.application.ai;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.profile.application.exception.ProfileRejectedException;
import com.aplicafacil.profile.application.exception.ProfileRejectedException.Reason;
import com.aplicafacil.profile.application.port.out.AiTaskRepository;
import com.aplicafacil.profile.domain.AiTask;

/** Estado de las tareas de IA de la persona (el front consulta hasta que dejan de estar PENDING). */
@Service
public class AiTaskQueries {

    private static final int RECENT_LIMIT = 10;

    private final AiTaskRepository tasks;

    public AiTaskQueries(AiTaskRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public AiTask get(UUID userId, UUID taskId) {
        return tasks.findOwned(taskId, userId)
                .orElseThrow(() -> new ProfileRejectedException(Reason.TASK_NOT_FOUND, "La tarea no existe"));
    }

    @Transactional(readOnly = true)
    public List<AiTask> recent(UUID userId) {
        return tasks.recent(userId, RECENT_LIMIT);
    }
}
