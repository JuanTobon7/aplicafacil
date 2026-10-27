package com.aplicafacil.identity.infrastructure.persistence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.aplicafacil.identity.infrastructure.persistence.entity.LoginCodeEntity;

public interface SpringDataLoginCodeRepository extends JpaRepository<LoginCodeEntity, String> {

    @Modifying
    @Query("delete from LoginCodeEntity c where c.expiresAt < :now")
    int deleteExpired(Instant now);
}
