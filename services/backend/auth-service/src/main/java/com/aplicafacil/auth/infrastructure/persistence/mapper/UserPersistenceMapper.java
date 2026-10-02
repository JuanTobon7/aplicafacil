package com.aplicafacil.auth.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import com.aplicafacil.auth.domain.User;
import com.aplicafacil.auth.infrastructure.persistence.entity.UserEntity;

/** Dominio ↔ persistencia. Hacia el dominio usa el constructor de reconstitución (valida invariantes). */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserPersistenceMapper {

    User toDomain(UserEntity entity);

    UserEntity toEntity(User user);
}
