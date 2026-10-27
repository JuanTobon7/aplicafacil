package com.aplicafacil.identity.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aplicafacil.identity.infrastructure.persistence.entity.SigningKeyEntity;

public interface SpringDataSigningKeyRepository extends JpaRepository<SigningKeyEntity, String> {

    Optional<SigningKeyEntity> findByActiveTrue();

    /** Activa + retiradas recientes: todas se publican en el JWKS mientras haya tokens firmados con ellas. */
    List<SigningKeyEntity> findAllByOrderByCreatedAtDesc();
}
