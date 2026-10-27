package com.aplicafacil.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.identity.domain.AppUser;

/** Usuarios ({@code app_user}). */
public interface UserRepository {

    List<AppUser> findAll();

    Optional<AppUser> findById(UUID id);

    Optional<AppUser> findByMemberUrn(String memberUrn);

    Optional<AppUser> findByOidcSub(String oidcSub);

    /** Sin distinguir mayúsculas, solo entre correos verificados. */
    Optional<AppUser> findByVerifiedEmail(String email);

    AppUser save(AppUser user);

    /** Los tokens del usuario caen en cascada. */
    void deleteById(UUID id);
}
