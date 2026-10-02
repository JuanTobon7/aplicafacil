package com.aplicafacil.people.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import com.aplicafacil.people.application.dto.PeopleDto;
import com.aplicafacil.people.domain.model.People;

/** Dominio → DTO de salida. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PeopleDtoMapper {

    PeopleDto toDto(People people);
}
