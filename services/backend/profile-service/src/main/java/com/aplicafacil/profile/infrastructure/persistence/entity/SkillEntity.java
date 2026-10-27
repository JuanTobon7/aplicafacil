package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.aplicafacil.profile.domain.SkillCategory;
import com.aplicafacil.profile.domain.SkillLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una skill del perfil; el orden lo lleva {@code position} ({@code @OrderColumn} del perfil). */
@Entity
@Table(name = "profile_skill")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private SkillCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private SkillLevel level;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;
}
