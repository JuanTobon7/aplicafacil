package com.aplicafacil.people.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de entrada para registrar o actualizar una persona.
 * Las anotaciones son un primer filtro (400 con detalle por campo);
 * las invariantes reales las aplica el dominio.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PeopleDtoRegister {

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String phone;

    @Size(max = 255)
    private String linkedinUrl;

    @Size(max = 255)
    private String githubUrl;

    @Size(max = 255)
    private String portfolioUrl;

    @Size(max = 255)
    private String resumeUrl;
}
