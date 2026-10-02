package com.aplicafacil.people.domain.model;

import static com.aplicafacil.people.domain.model.DomainValidation.optionalText;
import static com.aplicafacil.people.domain.model.DomainValidation.requireNonNull;
import static com.aplicafacil.people.domain.model.DomainValidation.requireText;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.aplicafacil.people.domain.exception.InvalidEmailException;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Persona (candidato): datos personales y de contacto.
 *
 * <p>No conoce perfiles profesionales ni cuentas: los perfiles referencian a
 * la persona por su {@code id}, y la persona referencia a su cuenta de
 * auth-service por {@code userId}.
 *
 * <p>Sin setters: los cambios pasan por métodos que aplican las invariantes.
 * Los timestamps los asigna la persistencia; son {@code null} mientras no se ha guardado.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class People {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 255;
    public static final int MAX_CONTACT_LENGTH = 255;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @EqualsAndHashCode.Include
    private final UUID id;
    private final UUID userId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;
    private String resumeUrl;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Reconstruye una persona existente (persistencia / mappers).
     * Aplica las mismas invariantes que {@link #create}.
     */
    public People(UUID id, UUID userId, String firstName, String lastName, String email,
                  String phone, String linkedinUrl, String githubUrl,
                  String portfolioUrl, String resumeUrl,
                  Instant createdAt, Instant updatedAt) {
        this.id = requireNonNull(id, "id");
        this.userId = requireNonNull(userId, "userId");
        rename(firstName, lastName);
        changeEmail(email);
        updateContactDetails(phone, linkedinUrl, githubUrl, portfolioUrl, resumeUrl);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Registra la persona de un usuario. {@code userId} es el {@code sub} de
     * los tokens (cuenta en auth-service): una persona por usuario.
     */
    public static People create(UUID userId, String firstName, String lastName, String email) {
        return new People(UUID.randomUUID(), userId, firstName, lastName, email,
                null, null, null, null, null, null, null);
    }

    public void rename(String firstName, String lastName) {
        this.firstName = requireText(firstName, "firstName", MAX_NAME_LENGTH);
        this.lastName = requireText(lastName, "lastName", MAX_NAME_LENGTH);
    }

    /** El email se normaliza a minúsculas: es único entre todas las personas. */
    public void changeEmail(String email) {
        String normalized = requireText(email, "email", MAX_EMAIL_LENGTH).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) {
            throw new InvalidEmailException();
        }
        this.email = normalized;
    }

    /** Reemplaza todos los datos de contacto opcionales ({@code null} o vacío = sin dato). */
    public void updateContactDetails(String phone, String linkedinUrl, String githubUrl,
                                     String portfolioUrl, String resumeUrl) {
        this.phone = optionalText(phone, "phone", MAX_CONTACT_LENGTH);
        this.linkedinUrl = optionalText(linkedinUrl, "linkedinUrl", MAX_CONTACT_LENGTH);
        this.githubUrl = optionalText(githubUrl, "githubUrl", MAX_CONTACT_LENGTH);
        this.portfolioUrl = optionalText(portfolioUrl, "portfolioUrl", MAX_CONTACT_LENGTH);
        this.resumeUrl = optionalText(resumeUrl, "resumeUrl", MAX_CONTACT_LENGTH);
    }

    /** La persona pertenece a la cuenta {@code userId} de auth-service. */
    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
