package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aplicafacil.profile.domain.Seniority;
import com.aplicafacil.profile.domain.WorkProfile;
import com.aplicafacil.profile.domain.WorkRole;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Las columnas {@code work_*} de {@code user_profile}: el perfil laboral. Sin rol principal, no hay perfil. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class WorkProfileColumns {

    @Enumerated(EnumType.STRING)
    @Column(name = "work_primary_role", length = 32)
    private WorkRole primaryRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "work_secondary_roles", nullable = false)
    private List<WorkRole> secondaryRoles = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "work_seniority", length = 16)
    private Seniority seniority;

    @Column(name = "work_title", length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_source", length = 8)
    private WorkProfile.Source source;

    @Column(name = "work_updated_at")
    private Instant updatedAt;
}
