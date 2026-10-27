package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.time.YearMonth;
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

/** Un estudio del perfil. */
@Entity
@Table(name = "profile_education")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String institution;

    @Column(length = 200)
    private String degree;

    @Column(name = "field_of_study", length = 200)
    private String fieldOfStudy;

    @Column(name = "start_month", length = 7)
    private YearMonth startMonth;

    @Column(name = "end_month", length = 7)
    private YearMonth endMonth;

    private String description;
}
