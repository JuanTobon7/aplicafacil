package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Fila de {@code profiles} y raíz del agregado persistido.
 *
 * <ul>
 *   <li>Hijos con {@code cascade = ALL} + {@code orphanRemoval}: se guardan y
 *       eliminan junto con el perfil; quitar uno del agregado lo borra.</li>
 *   <li>Todo es LAZY (no se copia el {@code eager} de TypeORM): las colecciones
 *       se cargan por lotes ({@code hibernate.default_batch_fetch_size}).</li>
 *   <li>{@code personId} es solo un id: la persona vive en people-service.</li>
 * </ul>
 */
@Entity
@Table(name = "profiles")
public class ProfileEntity {

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<SkillEntity> skills = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startDate DESC")
    private List<ExperienceEntity> experiences = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startDate DESC")
    private List<EducationEntity> educations = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<ProjectEntity> projects = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "cv_id", unique = true)
    private CvEntity cv;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPersonId() {
        return personId;
    }

    public void setPersonId(UUID personId) {
        this.personId = personId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<SkillEntity> getSkills() {
        return skills;
    }

    public void setSkills(List<SkillEntity> skills) {
        this.skills = skills;
    }

    public List<ExperienceEntity> getExperiences() {
        return experiences;
    }

    public void setExperiences(List<ExperienceEntity> experiences) {
        this.experiences = experiences;
    }

    public List<EducationEntity> getEducations() {
        return educations;
    }

    public void setEducations(List<EducationEntity> educations) {
        this.educations = educations;
    }

    public List<ProjectEntity> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectEntity> projects) {
        this.projects = projects;
    }

    public CvEntity getCv() {
        return cv;
    }

    public void setCv(CvEntity cv) {
        this.cv = cv;
    }
}
