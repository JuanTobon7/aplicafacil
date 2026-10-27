package com.aplicafacil.identity.infrastructure.persistence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.aplicafacil.identity.application.port.out.UserRepository;
import com.aplicafacil.identity.domain.AppUser;
import com.aplicafacil.identity.infrastructure.persistence.entity.AppUserEntity;

/** Adaptador JPA de {@link UserRepository}: la entidad no sale de infrastructure. */
@Repository
public class JpaUserRepository implements UserRepository {

    private final SpringDataAppUserRepository jpa;

    public JpaUserRepository(SpringDataAppUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<AppUser> findAll() {
        return jpa.findAll().stream().map(JpaUserRepository::toDomain).toList();
    }

    @Override
    public Optional<AppUser> findById(UUID id) {
        return jpa.findById(id).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<AppUser> findByMemberUrn(String memberUrn) {
        return jpa.findByLinkedinMemberUrn(memberUrn).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<AppUser> findByOidcSub(String oidcSub) {
        return jpa.findByLinkedinOidcSub(oidcSub).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<AppUser> findByVerifiedEmail(String email) {
        return jpa.findByVerifiedEmail(email).map(JpaUserRepository::toDomain);
    }

    @Override
    public AppUser save(AppUser user) {
        AppUserEntity entity = jpa.findById(user.id())
                .orElseGet(() -> AppUserEntity.builder().id(user.id()).createdAt(user.createdAt()).build());
        AppUser.Links links = user.links();
        entity.setLinkedinMemberUrn(links.memberUrn());
        entity.setLinkedinOidcSub(links.oidcSub());
        entity.setEmail(links.email());
        entity.setEmailVerified(links.emailVerified());
        AppUser.Basics basics = user.basics();
        entity.setFirstName(basics.firstName());
        entity.setLastName(basics.lastName());
        entity.setHeadline(basics.headline());
        entity.setPhotoUrl(basics.photoUrl());
        entity.setPublicProfileUrl(basics.publicProfileUrl());
        entity.setLocation(basics.location());
        entity.setEditedFields(new LinkedHashSet<>(user.editedFields()));
        entity.setUpdatedAt(user.activity().updatedAt());
        entity.setLastLoginAt(user.activity().lastLoginAt());
        return toDomain(jpa.save(entity));
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    private static AppUser toDomain(AppUserEntity e) {
        return AppUser.restore(e.getId(), e.getCreatedAt(),
                new AppUser.Links(e.getLinkedinMemberUrn(), e.getLinkedinOidcSub(), e.getEmail(), e.isEmailVerified()),
                new AppUser.Basics(e.getFirstName(), e.getLastName(), e.getHeadline(), e.getPhotoUrl(),
                        e.getPublicProfileUrl(), e.getLocation()),
                e.getEditedFields(),
                new AppUser.Activity(e.getUpdatedAt(), e.getLastLoginAt()));
    }
}
