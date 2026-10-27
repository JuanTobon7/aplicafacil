package com.aplicafacil.identity.application.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.identity.application.port.in.UserProvisioningService;
import com.aplicafacil.identity.application.port.out.UserRepository;
import com.aplicafacil.identity.domain.AppUser;
import com.aplicafacil.linkedin.contract.IdentitySource;
import com.aplicafacil.linkedin.contract.LinkedInIdentity;

/**
 * Ver {@link UserProvisioningService}: único punto que crea, actualiza o vincula usuarios. Cómo se actualizan los
 * datos lo decide {@link AppUser#apply}.
 */
@Service
public class UserProvisioningServiceImpl implements UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningServiceImpl.class);

    private final UserRepository users;
    private final Clock clock;

    public UserProvisioningServiceImpl(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProvisioningResult provision(LinkedInIdentity identity) {
        Instant now = clock.instant();

        Optional<AppUser> existing = findByPrimaryId(identity).or(() -> findBySecondaryId(identity));
        if (existing.isPresent()) {
            AppUser user = update(existing.get(), identity, now);
            log.info("Usuario {} actualizado desde {}", user.id(), identity.source());
            return new ProvisioningResult(user.id(), Outcome.UPDATED);
        }

        Optional<AppUser> byEmail = linkableByEmail(identity);
        if (byEmail.isPresent()) {
            AppUser user = update(byEmail.get(), identity, now);
            log.info("Usuario {} vinculado por correo verificado con una identidad {}", user.id(), identity.source());
            return new ProvisioningResult(user.id(), Outcome.LINKED);
        }

        AppUser user = AppUser.create(UUID.randomUUID(), now);
        user.apply(identity, now);
        AppUser.Links links = user.links();
        if (links.emailVerified() && users.findByVerifiedEmail(links.email()).isPresent()) {
            // El correo verificado ya pertenece a otra cuenta con otro miembro de LinkedIn: no se vincula ni se duplica
            user.unverifyEmail();
        }
        users.save(user);
        log.info("Usuario {} creado desde {}", user.id(), identity.source());
        return new ProvisioningResult(user.id(), Outcome.CREATED);
    }

    private AppUser update(AppUser user, LinkedInIdentity identity, Instant now) {
        user.apply(identity, now);
        return users.save(user);
    }

    private Optional<AppUser> findByPrimaryId(LinkedInIdentity identity) {
        return identity.source() == IdentitySource.SESSION
                ? users.findByMemberUrn(identity.memberUrn())
                : users.findByOidcSub(identity.oidcSub());
    }

    /** El otro identificador solo cuenta si la identidad lo trae. */
    private Optional<AppUser> findBySecondaryId(LinkedInIdentity identity) {
        if (identity.source() == IdentitySource.SESSION && identity.oidcSub() != null) {
            return users.findByOidcSub(identity.oidcSub());
        }
        if (identity.source() == IdentitySource.OIDC && identity.memberUrn() != null) {
            return users.findByMemberUrn(identity.memberUrn());
        }
        return Optional.empty();
    }

    /** Vincula una cuenta creada en el otro modo, solo si ambos correos están verificados. */
    private Optional<AppUser> linkableByEmail(LinkedInIdentity identity) {
        if (!identity.emailVerified() || identity.email() == null || identity.email().isBlank()) {
            return Optional.empty();
        }
        return users.findByVerifiedEmail(identity.email().strip())
                .filter(user -> user.linkableFrom(identity.source()));
    }
}
