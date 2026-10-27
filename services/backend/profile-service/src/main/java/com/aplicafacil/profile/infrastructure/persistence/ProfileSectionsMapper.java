package com.aplicafacil.profile.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

import com.aplicafacil.profile.domain.Certification;
import com.aplicafacil.profile.domain.Education;
import com.aplicafacil.profile.domain.Experience;
import com.aplicafacil.profile.domain.Language;
import com.aplicafacil.profile.domain.Period;
import com.aplicafacil.profile.domain.Skill;
import com.aplicafacil.profile.infrastructure.persistence.entity.CertificationEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.EducationEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.ExperienceEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.LanguageEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.SkillEntity;

/** Entidades hijas del perfil ⇄ dominio (fabricación pura: el dominio no sabe guardarse). */
final class ProfileSectionsMapper {

    private ProfileSectionsMapper() {
    }

    static SkillEntity toEntity(Skill s) {
        return new SkillEntity(s.id(), s.name(), s.category(), s.level(), s.yearsOfExperience());
    }

    static void update(SkillEntity e, Skill s) {
        e.setName(s.name());
        e.setCategory(s.category());
        e.setLevel(s.level());
        e.setYearsOfExperience(s.yearsOfExperience());
    }

    static Skill toDomain(SkillEntity e) {
        return new Skill(e.getId(), e.getName(), e.getCategory(), e.getLevel(), e.getYearsOfExperience());
    }

    static ExperienceEntity toEntity(Experience x) {
        return new ExperienceEntity(x.id(), x.title(), x.company(), x.location(), x.period().start(),
                x.period().end(), x.description(), new ArrayList<>(x.technologies()));
    }

    static void update(ExperienceEntity e, Experience x) {
        e.setTitle(x.title());
        e.setCompany(x.company());
        e.setLocation(x.location());
        e.setStartMonth(x.period().start());
        e.setEndMonth(x.period().end());
        e.setDescription(x.description());
        e.setTechnologies(new ArrayList<>(x.technologies()));
    }

    static Experience toDomain(ExperienceEntity e) {
        return new Experience(e.getId(), e.getTitle(), e.getCompany(), e.getLocation(),
                new Period(e.getStartMonth(), e.getEndMonth()), e.getDescription(),
                e.getTechnologies() == null ? List.of() : e.getTechnologies());
    }

    static EducationEntity toEntity(Education x) {
        return new EducationEntity(x.id(), x.institution(), x.degree(), x.fieldOfStudy(), x.period().start(),
                x.period().end(), x.description());
    }

    static void update(EducationEntity e, Education x) {
        e.setInstitution(x.institution());
        e.setDegree(x.degree());
        e.setFieldOfStudy(x.fieldOfStudy());
        e.setStartMonth(x.period().start());
        e.setEndMonth(x.period().end());
        e.setDescription(x.description());
    }

    static Education toDomain(EducationEntity e) {
        return new Education(e.getId(), e.getInstitution(), e.getDegree(), e.getFieldOfStudy(),
                new Period(e.getStartMonth(), e.getEndMonth()), e.getDescription());
    }

    static LanguageEntity toEntity(Language l) {
        return new LanguageEntity(l.id(), l.name(), l.proficiency());
    }

    static void update(LanguageEntity e, Language l) {
        e.setName(l.name());
        e.setProficiency(l.proficiency());
    }

    static Language toDomain(LanguageEntity e) {
        return new Language(e.getId(), e.getName(), e.getProficiency());
    }

    static CertificationEntity toEntity(Certification c) {
        return new CertificationEntity(c.id(), c.name(), c.issuer(), c.issuedOn());
    }

    static void update(CertificationEntity e, Certification c) {
        e.setName(c.name());
        e.setIssuer(c.issuer());
        e.setIssuedOn(c.issuedOn());
    }

    static Certification toDomain(CertificationEntity e) {
        return new Certification(e.getId(), e.getName(), e.getIssuer(), e.getIssuedOn());
    }
}
