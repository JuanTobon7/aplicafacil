package com.aplicafacil.people.application.services;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.people.application.dto.PeopleDto;
import com.aplicafacil.people.application.dto.PeopleDtoRegister;
import com.aplicafacil.people.application.exception.EmailAlreadyUsedException;
import com.aplicafacil.people.application.exception.PeopleAccessDeniedException;
import com.aplicafacil.people.application.exception.PeopleAlreadyRegisteredException;
import com.aplicafacil.people.application.exception.PeopleNotFoundException;
import com.aplicafacil.people.application.mapper.PeopleDtoMapper;
import com.aplicafacil.people.application.port.in.PeopleService;
import com.aplicafacil.people.application.port.out.PeopleRepository;
import com.aplicafacil.people.domain.model.People;

@Service
@Transactional(readOnly = true)
public class PeopleServiceImpl implements PeopleService {

    private final PeopleRepository peopleRepository;
    private final PeopleDtoMapper mapper;

    public PeopleServiceImpl(PeopleRepository peopleRepository, PeopleDtoMapper mapper) {
        this.peopleRepository = peopleRepository;
        this.mapper = mapper;
    }

    @Override
    public PeopleDto getPeople(UUID peopleId, UUID userId) {
        return mapper.toDto(findOwned(peopleId, userId));
    }

    @Override
    public PeopleDto getPeopleByUserId(UUID userId) {
        return peopleRepository.findByUserId(userId)
                .map(mapper::toDto)
                .orElseThrow(() -> PeopleNotFoundException.byUserId(userId));
    }

    @Override
    @Transactional
    public PeopleDto registerPeople(UUID userId, PeopleDtoRegister peopleDto) {
        if (peopleRepository.existsByUserId(userId)) {
            throw new PeopleAlreadyRegisteredException(userId);
        }
        People people = People.create(userId, peopleDto.getFirstName(), peopleDto.getLastName(), peopleDto.getEmail());
        updateContactDetails(people, peopleDto);
        if (peopleRepository.existsByEmail(people.getEmail())) {
            throw new EmailAlreadyUsedException(people.getEmail());
        }
        return mapper.toDto(peopleRepository.save(people));
    }

    @Override
    @Transactional
    public PeopleDto updatePeople(UUID peopleId, PeopleDtoRegister peopleDto, UUID userId) {
        People people = findOwned(peopleId, userId);
        String previousEmail = people.getEmail();
        people.rename(peopleDto.getFirstName(), peopleDto.getLastName());
        people.changeEmail(peopleDto.getEmail());
        updateContactDetails(people, peopleDto);
        if (!people.getEmail().equals(previousEmail) && peopleRepository.existsByEmail(people.getEmail())) {
            throw new EmailAlreadyUsedException(people.getEmail());
        }
        return mapper.toDto(peopleRepository.save(people));
    }

    @Override
    @Transactional
    public void deletePeople(UUID peopleId, UUID userId) {
        peopleRepository.deleteById(findOwned(peopleId, userId).getId());
    }

    /** Solo el dueño de la persona puede verla o modificarla. */
    private People findOwned(UUID peopleId, UUID userId) {
        People people = peopleRepository.findById(peopleId)
                .orElseThrow(() -> PeopleNotFoundException.byId(peopleId));
        if (!people.isOwnedBy(userId)) {
            throw new PeopleAccessDeniedException(peopleId);
        }
        return people;
    }

    private static void updateContactDetails(People people, PeopleDtoRegister peopleDto) {
        people.updateContactDetails(peopleDto.getPhone(), peopleDto.getLinkedinUrl(),
                peopleDto.getGithubUrl(), peopleDto.getPortfolioUrl(), peopleDto.getResumeUrl()
        );
    }
}
