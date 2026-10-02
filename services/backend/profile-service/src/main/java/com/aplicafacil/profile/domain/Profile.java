package com.aplicafacil.profile.domain;

import static com.aplicafacil.profile.domain.DomainValidation.requireNonNull;
import static com.aplicafacil.profile.domain.DomainValidation.requireText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Perfil profesional: raíz del agregado.
 *
 * <p>Skills, experiencias, educación, proyectos y CV pertenecen al perfil y
 * solo se modifican a través de él; sus colecciones se exponen como vistas
 * de solo lectura.
 *
 * <p>{@code personId} referencia a la persona dueña, que vive en
 * people-service: es solo un identificador, no una relación local.
 */
public final class Profile {

    public static final int MAX_TITLE_LENGTH = 255;

    private final UUID id;
    private final UUID personId;
    private String title;
    private String summary;
    private final List<Skill> skills;
    private final List<Experience> experiences;
    private final List<Education> educations;
    private final List<Project> projects;
    private Cv cv;

    /**
     * Reconstruye un perfil existente (persistencia / mappers).
     * Colecciones {@code null} se interpretan como vacías.
     */
    public Profile(UUID id, UUID personId, String title, String summary,
                   List<Skill> skills, List<Experience> experiences,
                   List<Education> educations, List<Project> projects, Cv cv) {
        this.id = requireNonNull(id, "id");
        this.personId = requireNonNull(personId, "personId");
        updateHeadline(title, summary);
        this.skills = copyWithoutDuplicates(skills, Skill::getId);
        this.experiences = copyWithoutDuplicates(experiences, Experience::getId);
        this.educations = copyWithoutDuplicates(educations, Education::getId);
        this.projects = copyWithoutDuplicates(projects, Project::getId);
        this.cv = cv;
    }

    /** Crea un perfil vacío para una persona. */
    public static Profile create(UUID personId, String title, String summary) {
        return new Profile(UUID.randomUUID(), personId, title, summary, null, null, null, null, null);
    }

    public void updateHeadline(String title, String summary) {
        this.title = requireText(title, "title", MAX_TITLE_LENGTH);
        this.summary = requireText(summary, "summary", Integer.MAX_VALUE);
    }

    // ── Skills ──────────────────────────────────────────────────────────
    public void addSkill(Skill skill) {
        addUnique(skills, requireNonNull(skill, "skill"), Skill::getId);
    }

    public boolean removeSkill(UUID skillId) {
        return skills.removeIf(s -> s.getId().equals(skillId));
    }

    // ── Experiencias ────────────────────────────────────────────────────
    public void addExperience(Experience experience) {
        addUnique(experiences, requireNonNull(experience, "experience"), Experience::getId);
    }

    public boolean removeExperience(UUID experienceId) {
        return experiences.removeIf(e -> e.getId().equals(experienceId));
    }

    // ── Educación ───────────────────────────────────────────────────────
    public void addEducation(Education education) {
        addUnique(educations, requireNonNull(education, "education"), Education::getId);
    }

    public boolean removeEducation(UUID educationId) {
        return educations.removeIf(e -> e.getId().equals(educationId));
    }

    // ── Proyectos ───────────────────────────────────────────────────────
    public void addProject(Project project) {
        addUnique(projects, requireNonNull(project, "project"), Project::getId);
    }

    public boolean removeProject(UUID projectId) {
        return projects.removeIf(p -> p.getId().equals(projectId));
    }

    // ── CV ──────────────────────────────────────────────────────────────
    /** Adjunta o reemplaza la hoja de vida (un perfil tiene como máximo una). */
    public void attachCv(Cv cv) {
        this.cv = requireNonNull(cv, "cv");
    }

    public void removeCv() {
        this.cv = null;
    }

    public boolean hasCv() {
        return cv != null;
    }

    // ── Lectura ─────────────────────────────────────────────────────────
    public UUID getId() {
        return id;
    }

    public UUID getPersonId() {
        return personId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public List<Skill> getSkills() {
        return Collections.unmodifiableList(skills);
    }

    public List<Experience> getExperiences() {
        return Collections.unmodifiableList(experiences);
    }

    public List<Education> getEducations() {
        return Collections.unmodifiableList(educations);
    }

    public List<Project> getProjects() {
        return Collections.unmodifiableList(projects);
    }

    /** CV adjunto o {@code null} si el perfil no tiene. */
    public Cv getCv() {
        return cv;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Profile profile && id.equals(profile.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private static <T> List<T> copyWithoutDuplicates(List<T> source, Function<T, UUID> idOf) {
        List<T> copy = new ArrayList<>();
        if (source != null) {
            source.forEach(item -> addUnique(copy, requireNonNull(item, "elemento del perfil"), idOf));
        }
        return copy;
    }

    private static <T> void addUnique(List<T> target, T item, Function<T, UUID> idOf) {
        UUID id = idOf.apply(item);
        if (target.stream().anyMatch(existing -> idOf.apply(existing).equals(id))) {
            throw new IllegalArgumentException("El perfil ya contiene un elemento con id " + id);
        }
        target.add(item);
    }
}
