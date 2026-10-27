package com.aplicafacil.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.aplicafacil.identity.infrastructure.persistence.entity.AppUserEntity;

public interface SpringDataAppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByLinkedinMemberUrn(String memberUrn);

    Optional<AppUserEntity> findByLinkedinOidcSub(String oidcSub);

    /** Usa el índice parcial {@code uk_app_user_verified_email}. */
    @Query("select u from AppUserEntity u where u.emailVerified = true and lower(u.email) = lower(:email)")
    Optional<AppUserEntity> findByVerifiedEmail(String email);
}
