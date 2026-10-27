package com.aplicafacil.jobs.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.aplicafacil.jobs.application.port.out.JobSearchStates;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.JobSearch;
import com.aplicafacil.jobs.domain.JobSearchStatus;
import com.aplicafacil.jobs.domain.SearchCriteria;

/**
 * {@link JobSearchStates} sobre {@code jobs.job_search}. Cada sentencia toca una fila por id y exige el estado
 * esperado: qué búsqueda procesa qué instancia lo decide RabbitMQ, no una consulta a la tabla.
 */
@Repository
public class JdbcJobSearchStates implements JobSearchStates {

    /**
     * Intento nuevo (QUEUED con un intento anterior) o el mismo intento reentregado tras caerse la instancia que lo
     * procesaba (RUNNING con ese intento).
     */
    private static final String START = """
            UPDATE jobs.job_search
               SET status = 'RUNNING', attempts = :attempt, started_at = :now
             WHERE id = :id
               AND ((status = 'QUEUED' AND attempts < :attempt) OR (status = 'RUNNING' AND attempts = :attempt))
            RETURNING id, user_id, platform, keywords, location, max_results, posted_within_days, remote, attempts, requested_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcJobSearchStates(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<JobSearch> start(UUID searchId, int attempt, Instant now) {
        return jdbc.query(START, new MapSqlParameterSource("id", searchId).addValue("attempt", attempt)
                        .addValue("now", ts(now)),
                (rs, i) -> new JobSearch(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        JobPlatform.valueOf(rs.getString("platform")),
                        new SearchCriteria(rs.getString("keywords"), rs.getString("location"), rs.getInt("max_results"),
                                (Integer) rs.getObject("posted_within_days"), rs.getBoolean("remote")),
                        JobSearchStatus.RUNNING,
                        rs.getInt("attempts"),
                        rs.getTimestamp("requested_at").toInstant()))
                .stream().findFirst();
    }

    @Override
    public void complete(UUID searchId, int found, int created, Instant now) {
        jdbc.update("""
                UPDATE jobs.job_search
                   SET status = 'COMPLETED', found_count = :found, new_count = :created, finished_at = :now,
                       error_code = NULL, error_message = NULL
                 WHERE id = :id AND status = 'RUNNING'
                """, new MapSqlParameterSource("id", searchId).addValue("found", found).addValue("created", created)
                .addValue("now", ts(now)));
    }

    @Override
    public boolean retryLater(UUID searchId, String code, String message, Instant nextAttemptAt) {
        return jdbc.update("""
                UPDATE jobs.job_search
                   SET status = 'QUEUED', next_attempt_at = :next, error_code = :code, error_message = :message
                 WHERE id = :id AND status = 'RUNNING'
                """, new MapSqlParameterSource("id", searchId).addValue("next", ts(nextAttemptAt))
                .addValue("code", code).addValue("message", message)) > 0;
    }

    @Override
    public void fail(UUID searchId, String code, String message, Instant now) {
        jdbc.update("""
                UPDATE jobs.job_search
                   SET status = 'FAILED', finished_at = :now, error_code = :code, error_message = :message
                 WHERE id = :id AND status = 'RUNNING'
                """, new MapSqlParameterSource("id", searchId).addValue("now", ts(now))
                .addValue("code", code).addValue("message", message));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
