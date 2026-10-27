package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un cargo del perfil. */
@Entity
@Table(name = "profile_experience")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(length = 200)
    private String location;

    @Column(name = "start_month", length = 7)
    private YearMonth startMonth;

    /** {@code null} = cargo actual. */
    @Column(name = "end_month", length = 7)
    private YearMonth endMonth;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<String> technologies = new ArrayList<>();
}
