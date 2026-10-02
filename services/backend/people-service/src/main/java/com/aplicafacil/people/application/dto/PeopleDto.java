package com.aplicafacil.people.application.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/** Vista de salida de una persona. Inmutable. */
@Getter
@Builder
public class PeopleDto {
    private final UUID id;
    private final UUID userId;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String linkedinUrl;
    private final String githubUrl;
    private final String portfolioUrl;
    private final String resumeUrl;
    private final Instant createdAt;
    private final Instant updatedAt;
}
