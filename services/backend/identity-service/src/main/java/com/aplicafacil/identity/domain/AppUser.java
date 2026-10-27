package com.aplicafacil.identity.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.aplicafacil.linkedin.contract.IdentitySource;
import com.aplicafacil.linkedin.contract.LinkedInIdentity;
import com.aplicafacil.linkedin.contract.ProfileBasics;

/**
 * Usuario de la plataforma. Es el experto en cómo una identidad de LinkedIn actualiza sus datos (GRASP): los
 * campos editados a mano no se pisan y un correo verificado nunca se cambia por uno sin verificar.
 */
public final class AppUser {

    private final UUID id;
    private final Instant createdAt;
    private Links links = new Links(null, null, null, false);
    private Basics basics = new Basics(null, null, null, null, null, null);
    private final Set<String> editedFields = new LinkedHashSet<>();
    private Instant updatedAt;
    private Instant lastLoginAt;

    private AppUser(UUID id, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static AppUser create(UUID id, Instant now) {
        AppUser user = new AppUser(id, now);
        user.updatedAt = now;
        return user;
    }

    /** Reconstruye un usuario guardado (lo usa el adaptador de persistencia). */
    public static AppUser restore(UUID id, Instant createdAt, Links links, Basics basics, Set<String> editedFields,
                                  Activity activity) {
        AppUser user = new AppUser(id, createdAt);
        user.links = links;
        user.basics = basics;
        user.editedFields.addAll(editedFields);
        user.updatedAt = activity.updatedAt();
        user.lastLoginAt = activity.lastLoginAt();
        return user;
    }

    /** Aplica lo leído de LinkedIn y cuenta como un inicio de sesión. */
    public void apply(LinkedInIdentity identity, Instant now) {
        applyLinks(identity);
        applyBasics(identity.basics());
        updatedAt = now;
        lastLoginAt = now;
    }

    private void applyLinks(LinkedInIdentity identity) {
        String memberUrn = identity.memberUrn() != null ? identity.memberUrn() : links.memberUrn();
        String oidcSub = identity.oidcSub() != null ? identity.oidcSub() : links.oidcSub();
        String email = links.email();
        boolean verified = links.emailVerified();
        boolean hasEmail = identity.email() != null && !identity.email().isBlank();
        if (hasEmail && (identity.emailVerified() || !verified)) {
            email = identity.email().strip();
            verified = identity.emailVerified();
        }
        links = new Links(memberUrn, oidcSub, email, verified);
    }

    private void applyBasics(ProfileBasics read) {
        basics = new Basics(
                pick("firstName", read.firstName(), basics.firstName()),
                pick("lastName", read.lastName(), basics.lastName()),
                pick("headline", read.headline(), basics.headline()),
                pick("photoUrl", read.photoUrl(), basics.photoUrl()),
                pick("publicProfileUrl", read.publicProfileUrl(), basics.publicProfileUrl()),
                pick("location", read.location(), basics.location()));
    }

    /** Los campos editados a mano y los valores que no se pudieron leer ({@code null}) no se tocan. */
    private String pick(String field, String read, String current) {
        return read != null && !read.isBlank() && !editedFields.contains(field) ? read.strip() : current;
    }

    /** El correo pertenece a otra cuenta: se conserva sin marcarlo verificado (no se vincula ni se duplica). */
    public void unverifyEmail() {
        links = new Links(links.memberUrn(), links.oidcSub(), links.email(), false);
    }

    /**
     * Se puede vincular por correo con una identidad de {@code source} solo si aún no tiene un identificador de ese
     * tipo (si lo tuviera, sería otro miembro de LinkedIn).
     */
    public boolean linkableFrom(IdentitySource source) {
        return source == IdentitySource.SESSION ? links.memberUrn() == null : links.oidcSub() == null;
    }

    public void loggedIn(Instant now) {
        lastLoginAt = now;
    }

    public String displayName() {
        String name = ((basics.firstName() == null ? "" : basics.firstName()) + " "
                + (basics.lastName() == null ? "" : basics.lastName())).strip();
        return name.isEmpty() ? "Cuenta de LinkedIn" : name;
    }

    public UUID id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Links links() {
        return links;
    }

    public Basics basics() {
        return basics;
    }

    public Set<String> editedFields() {
        return Set.copyOf(editedFields);
    }

    public Activity activity() {
        return new Activity(updatedAt, lastLoginAt);
    }

    /** Identificadores de LinkedIn y correo. */
    public record Links(String memberUrn, String oidcSub, String email, boolean emailVerified) {
    }

    /** Datos básicos del perfil. */
    public record Basics(String firstName, String lastName, String headline, String photoUrl, String publicProfileUrl,
                         String location) {
    }

    public record Activity(Instant updatedAt, Instant lastLoginAt) {
    }
}
