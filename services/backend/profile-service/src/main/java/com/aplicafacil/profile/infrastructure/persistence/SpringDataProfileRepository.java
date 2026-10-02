package com.aplicafacil.profile.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aplicafacil.profile.infrastructure.persistence.entity.ProfileEntity;

interface SpringDataProfileRepository extends JpaRepository<ProfileEntity, UUID> {

    List<ProfileEntity> findByPersonId(UUID personId);
}
