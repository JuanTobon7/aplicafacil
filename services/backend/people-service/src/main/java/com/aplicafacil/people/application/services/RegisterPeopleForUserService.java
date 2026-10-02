package com.aplicafacil.people.application.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.people.application.dto.PeopleDto;
import com.aplicafacil.people.application.dto.RegisterPeopleForUserCommand;
import com.aplicafacil.people.application.exception.EmailAlreadyUsedException;
import com.aplicafacil.people.application.mapper.PeopleDtoMapper;
import com.aplicafacil.people.application.port.in.RegisterPeopleForUser;
import com.aplicafacil.people.application.port.out.PeopleRepository;
import com.aplicafacil.people.domain.model.People;

@Service
public class RegisterPeopleForUserService implements RegisterPeopleForUser {

    private final PeopleRepository peopleRepository;
    private final PeopleDtoMapper mapper;

    public RegisterPeopleForUserService(PeopleRepository peopleRepository, PeopleDtoMapper mapper) {
        this.peopleRepository = peopleRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PeopleDto register(RegisterPeopleForUserCommand command) {
        People people = peopleRepository.findByUserId(command.userId()).orElseGet(() -> {
            People created = People.create(command.userId(), command.firstName(), command.lastName(), command.email());
            if (peopleRepository.existsByEmail(created.getEmail())) {
                throw new EmailAlreadyUsedException(created.getEmail());
            }
            return peopleRepository.save(created);
        });
        return mapper.toDto(people);
    }
}
