package com.aplicafacil.profile.infrastructure.persistence.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import com.aplicafacil.profile.domain.Cv;
import com.aplicafacil.profile.domain.Education;
import com.aplicafacil.profile.domain.Experience;
import com.aplicafacil.profile.domain.Profile;
import com.aplicafacil.profile.domain.Project;
import com.aplicafacil.profile.domain.Skill;
import com.aplicafacil.profile.infrastructure.persistence.entity.CvEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.EducationEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.ExperienceEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.ProfileEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.ProjectEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.SkillEntity;

/**
 * Dominio ↔ persistencia del agregado Profile.
 *
 * <p>Hacia el dominio, MapStruct usa los constructores de reconstitución, así
 * que las invariantes se validan también al leer. Hacia JPA, la referencia
 * {@code hijo.profile} no existe en el dominio: se completa en
 * {@link #linkChildren} para que Hibernate escriba {@code profile_id}.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProfilePersistenceMapper {

    // ── Profile ─────────────────────────────────────────────────────────
    Profile toDomain(ProfileEntity entity);

    ProfileEntity toEntity(Profile profile);

    @AfterMapping
    default void linkChildren(@MappingTarget ProfileEntity profile) {
        profile.getSkills().forEach(skill -> skill.setProfile(profile));
        profile.getExperiences().forEach(experience -> experience.setProfile(profile));
        profile.getEducations().forEach(education -> education.setProfile(profile));
        profile.getProjects().forEach(project -> project.setProfile(profile));
    }

    // ── Hijos ───────────────────────────────────────────────────────────
    Skill toDomain(SkillEntity entity);

    @Mapping(target = "profile", ignore = true)
    SkillEntity toEntity(Skill skill);

    Experience toDomain(ExperienceEntity entity);

    @Mapping(target = "profile", ignore = true)
    ExperienceEntity toEntity(Experience experience);

    Education toDomain(EducationEntity entity);

    @Mapping(target = "profile", ignore = true)
    EducationEntity toEntity(Education education);

    Project toDomain(ProjectEntity entity);

    @Mapping(target = "profile", ignore = true)
    ProjectEntity toEntity(Project project);

    @Mapping(target = "location", source = "filePath")
    Cv toDomain(CvEntity entity);

    @Mapping(target = "filePath", source = "location")
    CvEntity toEntity(Cv cv);
}
