package com.aplicafacil.profile.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.aplicafacil.profile.application.exception.ProfileRejectedException;

/** Errores como ProblemDetail (RFC 9457) con un {@code code} estable que el front puede mostrar o interpretar. */
@RestControllerAdvice
class ProfileExceptionHandler {

    @ExceptionHandler
    ResponseEntity<ProblemDetail> rejected(ProfileRejectedException e) {
        HttpStatus status = switch (e.reason()) {
            case PROFILE_NOT_FOUND, TASK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case AI_TASK_IN_PROGRESS -> HttpStatus.CONFLICT;
            case CV_UNSUPPORTED, CV_UNREADABLE, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
        };
        return problem(status, e.reason().name(), e.getMessage());
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "CV_UNSUPPORTED", "El CV supera el tamaño máximo (10 MB).");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
