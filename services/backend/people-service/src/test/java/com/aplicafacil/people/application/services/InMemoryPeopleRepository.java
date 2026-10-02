package com.aplicafacil.people.application.services;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.people.application.port.out.PeopleRepository;
import com.aplicafacil.people.domain.model.People;

/** Doble del puerto de salida: los servicios se prueban sin base de datos. */
final class InMemoryPeopleRepository implements PeopleRepository {

    final Map<UUID, People> byId = new HashMap<>();

    @Override
    public People save(People people) {
        byId.put(people.getId(), people);
        return people;
    }

    @Override
    public Optional<People> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<People> findByUserId(UUID userId) {
        return byId.values().stream().filter(p -> p.getUserId().equals(userId)).findFirst();
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return findByUserId(userId).isPresent();
    }

    @Override
    public Optional<People> findByEmail(String email) {
        return byId.values().stream().filter(p -> p.getEmail().equalsIgnoreCase(email.strip())).findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public void deleteById(UUID id) {
        byId.remove(id);
    }
}
