package com.aplicafacil.profile.application.ai;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.profile.application.exception.ProfileRejectedException;
import com.aplicafacil.profile.application.exception.ProfileRejectedException.Reason;
import com.aplicafacil.profile.application.port.out.AiTaskRepository;
import com.aplicafacil.profile.application.port.out.CvTextExtractor;
import com.aplicafacil.profile.application.port.out.CvTextExtractor.CvFile;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;

/**
 * Importar un CV: se saca el texto aquí (Tika) y la IA, vía mcp-gateway, lo descompone en secciones. Responde enseguida
 * con la tarea; el perfil se llena cuando llega {@code ai.request.completed} ({@link AiResultService}).
 */
@Service
public class CvImportService {

    private static final Set<String> EXTENSIONS = Set.of("pdf", "doc", "docx");
    /** Menos texto que esto no es un CV (un PDF escaneado sin capa de texto, por ejemplo). */
    private static final int MIN_TEXT = 200;

    private final CvTextExtractor extractor;
    private final AiTaskRepository tasks;
    private final AiTaskSubmitter submitter;
    private final ProfileAiPolicy policy;

    public CvImportService(CvTextExtractor extractor, AiTaskRepository tasks, AiTaskSubmitter submitter,
                           ProfileAiPolicy policy) {
        this.extractor = extractor;
        this.tasks = tasks;
        this.submitter = submitter;
        this.policy = policy;
    }

    @Transactional
    public AiTask importCv(UUID userId, UUID configId, CvFile file) {
        validate(file);
        if (tasks.hasPending(userId)) {
            throw new ProfileRejectedException(Reason.AI_TASK_IN_PROGRESS,
                    "Ya hay un CV o una búsqueda en proceso; espera a que termine.");
        }
        String text = extractor.extract(file).strip();
        if (text.length() < MIN_TEXT) {
            throw new ProfileRejectedException(Reason.CV_UNREADABLE,
                    "No se pudo leer texto del CV. Si es un PDF escaneado, expórtalo de nuevo desde Word o Google Docs.");
        }
        String sent = text.length() <= policy.maxCvChars() ? text : text.substring(0, policy.maxCvChars());
        return submitter.submit(new AiTaskSubmitter.Request(userId, configId, AiTaskKind.CV_EXTRACTION,
                file.fileName(), ProfilePrompts.cvExtraction(sent)));
    }

    private void validate(CvFile file) {
        if (file.content() == null || file.content().length == 0) {
            throw new ProfileRejectedException(Reason.CV_UNREADABLE, "El archivo está vacío.");
        }
        if (file.content().length > policy.maxFileBytes()) {
            throw new ProfileRejectedException(Reason.CV_UNSUPPORTED, "El CV supera el tamaño máximo.");
        }
        if (!EXTENSIONS.contains(extension(file.fileName()))) {
            throw new ProfileRejectedException(Reason.CV_UNSUPPORTED, "El CV debe ser PDF, DOC o DOCX.");
        }
    }

    private static String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
