package com.aplicafacil.computrabajo.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aplicafacil.computrabajo.infrastructure.persistence.entity.ComputrabajoSessionEntity;

public interface SpringDataComputrabajoSessionRepository extends JpaRepository<ComputrabajoSessionEntity, UUID> {
}
