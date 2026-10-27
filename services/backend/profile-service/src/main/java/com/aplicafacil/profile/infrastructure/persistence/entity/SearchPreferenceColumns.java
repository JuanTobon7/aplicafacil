package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Las columnas {@code search_*} de {@code user_profile}: la búsqueda de empleo del perfil. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class SearchPreferenceColumns {

    @Column(name = "search_keywords", length = 200)
    private String keywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "search_alternatives", nullable = false)
    private List<String> alternatives = new ArrayList<>();

    @Column(name = "search_rationale", length = 500)
    private String rationale;

    @Column(name = "search_remote_only", nullable = false)
    private boolean remoteOnly;

    @Column(name = "search_posted_within_days")
    private Integer postedWithinDays;

    @Column(name = "search_location", length = 120)
    private String location;

    @Column(name = "search_suggested_at")
    private Instant suggestedAt;
}
