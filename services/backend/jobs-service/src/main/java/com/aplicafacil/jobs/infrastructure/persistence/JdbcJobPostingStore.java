package com.aplicafacil.jobs.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.jobs.application.port.out.JobPostingStore;
import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobPlatform;

/**
 * Upsert por (platform, external_id): la primera vez inserta, después actualiza los datos y {@code last_seen_at}.
 * {@code xmax = 0} en el RETURNING distingue una fila insertada de una actualizada (PostgreSQL).
 *
 * <p>Dos idas a la BD por búsqueda, no dos por card: un solo {@code INSERT … VALUES (…), (…)} para todas las ofertas
 * y un lote JDBC para los vínculos. El id de cada oferta se cruza en memoria (mapa por {@code external_id}), sin
 * subconsultas.
 */
@Repository
public class JdbcJobPostingStore implements JobPostingStore {

    private static final String UPSERT = """
            INSERT INTO jobs.job_posting (id, platform, external_id, url, title, company, location, posted_text,
                                          salary_text, quick_apply, first_seen_at, last_seen_at)
            VALUES %s
            ON CONFLICT (platform, external_id) DO UPDATE
               SET url = EXCLUDED.url, title = EXCLUDED.title, company = EXCLUDED.company,
                   location = EXCLUDED.location, posted_text = EXCLUDED.posted_text,
                   salary_text = EXCLUDED.salary_text, quick_apply = EXCLUDED.quick_apply,
                   last_seen_at = EXCLUDED.last_seen_at
            RETURNING id, external_id, (xmax = 0) AS inserted
            """;

    private static final String ROW = "(:id%1$d, :platform, :externalId%1$d, :url%1$d, :title%1$d, :company%1$d, "
            + ":location%1$d, :postedText%1$d, :salaryText%1$d, :quickApply%1$d, :now, :now)";

    private static final String LINK = """
            INSERT INTO jobs.job_search_result (search_id, posting_id, position, is_new)
            VALUES (:searchId, :postingId, :position, :isNew)
            ON CONFLICT (search_id, posting_id) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcJobPostingStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int saveAll(UUID searchId, JobPlatform platform, List<JobCard> cards) {
        List<JobCard> unique = uniqueByExternalId(cards);
        if (unique.isEmpty()) {
            return 0;
        }
        Map<String, Saved> saved = upsert(platform, unique);
        List<SqlParameterSource> links = new ArrayList<>(unique.size());
        int created = 0;
        for (int position = 0; position < unique.size(); position++) {
            Saved posting = saved.get(externalId(unique.get(position)));
            created += posting.inserted() ? 1 : 0;
            links.add(new MapSqlParameterSource("searchId", searchId).addValue("postingId", posting.id())
                    .addValue("position", position).addValue("isNew", posting.inserted()));
        }
        jdbc.batchUpdate(LINK, links.toArray(SqlParameterSource[]::new));
        return created;
    }

    /** Un solo INSERT multi-fila; devuelve id e inserted por external_id. */
    private Map<String, Saved> upsert(JobPlatform platform, List<JobCard> cards) {
        MapSqlParameterSource params = new MapSqlParameterSource("platform", platform.name())
                .addValue("now", Timestamp.from(clock.instant()));
        StringJoiner rows = new StringJoiner(", ");
        for (int i = 0; i < cards.size(); i++) {
            addRow(params, i, cards.get(i));
            rows.add(ROW.formatted(i));
        }
        Map<String, Saved> saved = new HashMap<>(cards.size() * 2);
        jdbc.query(UPSERT.formatted(rows), params, rs -> {
            saved.put(rs.getString("external_id"), new Saved(rs.getObject("id", UUID.class), rs.getBoolean("inserted")));
        });
        return saved;
    }

    private static void addRow(MapSqlParameterSource params, int i, JobCard card) {
        params.addValue("id" + i, UUID.randomUUID())
                .addValue("externalId" + i, externalId(card))
                .addValue("url" + i, truncate(card.url(), 2048))
                .addValue("title" + i, truncate(card.title(), 500))
                .addValue("company" + i, truncate(card.company() == null ? "—" : card.company(), 255))
                .addValue("location" + i, truncate(card.location(), 255))
                .addValue("postedText" + i, truncate(card.postedText(), 100))
                .addValue("salaryText" + i, truncate(card.salaryText(), 255))
                .addValue("quickApply" + i, card.quickApply());
    }

    /** Postgres rechaza un upsert multi-fila que toca dos veces la misma fila: se deja la primera aparición. */
    private static List<JobCard> uniqueByExternalId(List<JobCard> cards) {
        Map<String, JobCard> unique = new LinkedHashMap<>();
        for (JobCard card : cards) {
            unique.putIfAbsent(externalId(card), card);
        }
        return List.copyOf(unique.values());
    }

    private static String externalId(JobCard card) {
        return truncate(card.externalId(), 64);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private record Saved(UUID id, boolean inserted) {
    }
}
