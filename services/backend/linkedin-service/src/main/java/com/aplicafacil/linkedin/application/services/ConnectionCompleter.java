package com.aplicafacil.linkedin.application.services;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.aplicafacil.linkedin.application.port.out.AccountProvisioning;
import com.aplicafacil.linkedin.application.port.out.AccountProvisioning.Provisioned;
import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.LoginResult;
import com.aplicafacil.linkedin.application.port.out.LinkedInSessionRepository;
import com.aplicafacil.linkedin.application.port.out.ProfileSync;
import com.aplicafacil.linkedin.contract.LinkedInIdentity;
import com.aplicafacil.linkedin.contract.LinkedInIdentityProvider;
import com.aplicafacil.linkedin.contract.ProfileSnapshot;
import com.aplicafacil.linkedin.domain.ConnectStep;

/**
 * Lo que sigue a un inicio de sesión exitoso: identidad → usuario (identity-service) → storageState cifrado en la
 * BD → perfil (profile-service).
 */
@Service
public class ConnectionCompleter {

    private static final Logger log = LoggerFactory.getLogger(ConnectionCompleter.class);

    private final LinkedInIdentityProvider<LoginResult> identityProvider;
    private final AccountProvisioning accounts;
    private final ProfileSync profiles;
    private final ConnectionStateService connections;
    private final LinkedInSessionRepository sessions;
    private final TransactionTemplate tx;

    public ConnectionCompleter(LinkedInIdentityProvider<LoginResult> identityProvider, AccountProvisioning accounts,
                               ProfileSync profiles, ConnectionStateService connections,
                               LinkedInSessionRepository sessions, TransactionTemplate tx) {
        this.identityProvider = identityProvider;
        this.accounts = accounts;
        this.profiles = profiles;
        this.connections = connections;
        this.sessions = sessions;
        this.tx = tx;
    }

    /** @return el usuario conectado y si su perfil quedó sincronizado completo */
    public Completed complete(ConnectAttempt attempt, LoginResult login) {
        LinkedInIdentity identity = identityProvider.readIdentity(login);
        ProfileSnapshot profile = identityProvider.readProfile(login);

        attempt.step(ConnectStep.CREATING_ACCOUNT);
        Provisioned provisioned = accounts.provision(attempt.id(), identity);
        UUID userId = provisioned.userId();

        attempt.step(ConnectStep.SAVING_SESSION);
        tx.executeWithoutResult(status -> {
            connections.markConnected(userId, identity.memberUrn());
            sessions.saveStorageState(userId, login.storageState());
        });
        boolean synced = syncProfile(userId, profile);
        return new Completed(provisioned, synced && profile != null && profile.complete());
    }

    /** Un fallo al sincronizar no impide entrar: el perfil queda pendiente para reintentar. */
    private boolean syncProfile(UUID userId, ProfileSnapshot profile) {
        try {
            profiles.sync(userId, profile == null ? emptyProfile() : profile);
            connections.markSynced(userId, profile != null && profile.complete());
            return true;
        } catch (RuntimeException e) {
            log.warn("No se pudo sincronizar el perfil de {} (queda pendiente): {}", userId, e.getMessage());
            connections.markSyncPending(userId);
            return false;
        }
    }

    private static ProfileSnapshot emptyProfile() {
        return new ProfileSnapshot(null, List.of(), List.of(), List.of(), List.of(), false, List.of("all"));
    }

    public record Completed(Provisioned provisioned, boolean profileComplete) {
    }
}
