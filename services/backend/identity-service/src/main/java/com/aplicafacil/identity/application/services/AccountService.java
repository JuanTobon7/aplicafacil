package com.aplicafacil.identity.application.services;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.identity.application.exception.IdentityException;
import com.aplicafacil.identity.application.port.out.AccountEvents;
import com.aplicafacil.identity.application.port.out.LinkedInSessionClient;
import com.aplicafacil.identity.application.port.out.LinkedInSessionClient.SessionCheck;
import com.aplicafacil.identity.application.port.out.UserRepository;
import com.aplicafacil.identity.domain.AppUser;

import lombok.Builder;

/** Cuentas locales: selector "Continuar como…", datos del usuario y borrado completo. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final Comparator<AppUser> MOST_RECENT_FIRST = Comparator.comparing(
            (AppUser u) -> u.activity().lastLoginAt(), Comparator.nullsLast(Comparator.reverseOrder()));

    private final UserRepository users;
    private final AccountEvents events;
    private final LinkedInSessionClient linkedin;
    private final Clock clock;

    public AccountService(UserRepository users, AccountEvents events, LinkedInSessionClient linkedin, Clock clock) {
        this.users = users;
        this.events = events;
        this.linkedin = linkedin;
        this.clock = clock;
    }

    /** Cuentas de esta instalación, la usada más recientemente primero. */
    @Transactional(readOnly = true)
    public List<AccountSummary> listAccounts() {
        List<AppUser> all = users.findAll();
        Map<UUID, String> statuses = all.isEmpty() ? Map.of() : safeStatuses(all.stream().map(AppUser::id).toList());
        return all.stream()
                .sorted(MOST_RECENT_FIRST)
                .map(u -> AccountSummary.builder()
                        .userId(u.id())
                        .displayName(u.displayName())
                        .headline(u.basics().headline())
                        .photoUrl(u.basics().photoUrl())
                        .connectionStatus(statuses.getOrDefault(u.id(), "UNKNOWN"))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public AppUser get(UUID userId) {
        return users.findById(userId).orElseThrow(IdentityException::userNotFound);
    }

    /**
     * "Continuar como…": la sesión de LinkedIn guardada en este equipo es la prueba de identidad.
     * Se valida (headless) antes de emitir tokens.
     */
    @Transactional
    public UUID continueAs(UUID userId) {
        AppUser user = get(userId);
        switch (validateSession(userId)) {
            case VALID -> {
                user.loggedIn(clock.instant());
                users.save(user);
                return userId;
            }
            case EXPIRED -> throw IdentityException.sessionExpired();
            case BUSY -> throw IdentityException.sessionBusy();
            default -> throw IdentityException.noSession();
        }
    }

    @Transactional
    public void markLogin(UUID userId) {
        users.findById(userId).ifPresent(u -> {
            u.loggedIn(clock.instant());
            users.save(u);
        });
    }

    /**
     * "Eliminar mi cuenta y mis datos". Primero se borra la sesión de LinkedIn de forma síncrona (si falla, se
     * aborta y nunca queda una sesión huérfana). Después se borran el usuario (los tokens caen en cascada)
     * y, en la misma transacción, se registra {@code user.deleted} en el outbox para que profile-service y
     * jobs-service borren lo suyo.
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        get(userId);
        deleteLinkedInSession(userId);
        users.deleteById(userId);
        events.userDeleted(userId);
        log.info("Cuenta {} eliminada", userId);
    }

    private SessionCheck validateSession(UUID userId) {
        try {
            return linkedin.validate(userId);
        } catch (RuntimeException e) {
            log.warn("No se pudo validar la sesión de {}: {}", userId, e.getMessage());
            throw IdentityException.linkedinServiceUnavailable();
        }
    }

    private void deleteLinkedInSession(UUID userId) {
        try {
            linkedin.deleteSession(userId);
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar la sesión de LinkedIn de {}: {}", userId, e.getMessage());
            throw IdentityException.linkedinServiceUnavailable();
        }
    }

    private Map<UUID, String> safeStatuses(List<UUID> ids) {
        try {
            return linkedin.connectionStatuses(ids);
        } catch (RuntimeException e) {
            log.warn("linkedin-service no respondió al listar estados: {}", e.getMessage());
            return Map.of();
        }
    }

    /** @param connectionStatus estado de linkedin-service, o UNKNOWN si no respondió */
    @Builder
    public record AccountSummary(UUID userId, String displayName, String headline, String photoUrl,
                                 String connectionStatus) {
    }
}
