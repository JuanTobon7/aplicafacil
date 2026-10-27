package com.aplicafacil.profile.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.aplicafacil.profile.domain.Certification;
import com.aplicafacil.profile.domain.Education;
import com.aplicafacil.profile.domain.Experience;
import com.aplicafacil.profile.domain.Language;
import com.aplicafacil.profile.domain.ProfileContent;
import com.aplicafacil.profile.domain.ProfileSection;
import com.aplicafacil.profile.domain.SearchPreference;
import com.aplicafacil.profile.domain.Skill;
import com.aplicafacil.profile.domain.UserProfile;
import com.aplicafacil.profile.domain.WorkProfile;

/** {@code Profile} de contracts/openapi/profile-api.yaml: fechas como "YYYY-MM". */
record ProfileResponse(String headline, String summary, List<Skill> skills, List<ExperienceView> experiences,
                       List<EducationView> education, List<Language> languages, List<Certification> certifications,
                       Set<ProfileSection> manuallyEditedFields, boolean complete, List<String> missingSections,
                       Instant lastSyncedAt, Instant cvImportedAt, SearchPreference searchPreference,
                       WorkProfile workProfile) {

    static ProfileResponse from(UserProfile p) {
        ProfileContent c = p.content();
        UserProfile.SyncState sync = p.syncState();
        return new ProfileResponse(c.headline(), c.summary(), c.skills(),
                c.experiences().stream().map(ExperienceView::of).toList(),
                c.education().stream().map(EducationView::of).toList(),
                c.languages(), c.certifications(), sync.manuallyEditedFields(), sync.complete(),
                sync.missingSections(), sync.lastSyncedAt(), sync.cvImportedAt(), p.searchPreference(),
                p.workProfile());
    }

    record ExperienceView(String id, String title, String company, String location, String startDate,
                          String endDate, String description, List<String> technologies) {

        static ExperienceView of(Experience e) {
            return new ExperienceView(e.id().toString(), e.title(), e.company(), e.location(),
                    month(e.period().start()), month(e.period().end()), e.description(), e.technologies());
        }
    }

    record EducationView(String id, String institution, String degree, String fieldOfStudy, String startDate,
                         String endDate, String description) {

        static EducationView of(Education e) {
            return new EducationView(e.id().toString(), e.institution(), e.degree(), e.fieldOfStudy(),
                    month(e.period().start()), month(e.period().end()), e.description());
        }
    }

    private static String month(java.time.YearMonth value) {
        return value == null ? null : value.toString();
    }
}
