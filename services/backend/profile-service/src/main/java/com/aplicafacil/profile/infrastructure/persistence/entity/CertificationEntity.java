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

/** Una certificación del perfil. */
@Entity
@Table(name = "profile_certification")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificationEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 200)
    private String issuer;

    @Column(name = "issued_on", length = 7)
    private YearMonth issuedOn;
}
