package com.aplicafacil.people.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import com.aplicafacil.people.domain.model.People;
import com.aplicafacil.people.persistence.entity.PeopleEntity;

/**
 * Dominio ↔ persistencia. MapStruct construye {@link People} con su
 * constructor de reconstitución, así que las invariantes del dominio se
 * validan también al leer de la base de datos.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PeoplePersistenceMapper {

    People toDomain(PeopleEntity entity);

    PeopleEntity toEntity(People people);
}
