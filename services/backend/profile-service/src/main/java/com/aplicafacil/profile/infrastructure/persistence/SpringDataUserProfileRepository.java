package com.aplicafacil.profile.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.aplicafacil.profile.infrastructure.persistence.entity.UserProfileEntity;

public interface SpringDataUserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    /** Sin cargar el perfil ni sus colecciones: las hijas las borra la BD (ON DELETE CASCADE). */
    @Modifying(clearAutomatically = true)
    @Query("delete from UserProfileEntity p where p.userId = :userId")
    int deleteByUserId(UUID userId);
}
