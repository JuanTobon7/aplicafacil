package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un idioma del perfil. */
@Entity
@Table(name = "profile_language")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String proficiency;
}
