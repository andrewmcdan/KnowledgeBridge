package com.capstone.knowledgebridge.cache;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Naive exact-match response cache backed by the query_cache table (V8). An entry matches only when the trimmed
 * question text, the mode, and the knowledge revision are all identical, so a knowledge write makes every older entry
 * stale without deleting it.
 *
 * Uses plain SQL rather than JPA because every lookup must repeat the unique index's md5(exact_query_text) expression
 * to use it, and inserts must name the same expression in ON CONFLICT.
 *
 * The cache is best-effort: a failed read is treated as a miss and a failed write is only logged, so a cache problem
 * never fails a query that gbrain could answer.
 */
@Service
public class QueryCacheService {

	private static final Logger log = LoggerFactory.getLogger(QueryCacheService.class);

	// Counts the hit and returns the stored response in one statement, so concurrent hits can't lose an increment.
	private static final String LOOKUP_SQL = """
			UPDATE query_cache
			SET hit_count = hit_count + 1, last_accessed_at = now()
			WHERE query_mode = ? AND knowledge_revision = ?
				AND md5(exact_query_text) = md5(?) AND exact_query_text = ?
			RETURNING response_json::text
			""";

	// DO NOTHING covers two identical questions missing at the same time: the first insert wins.
	private static final String STORE_SQL = """
			INSERT INTO query_cache (exact_query_text, query_mode, knowledge_revision, response_json)
			VALUES (?, ?, ?, ?::jsonb)
			ON CONFLICT (query_mode, knowledge_revision, md5(exact_query_text)) DO NOTHING
			""";

	// current compares against system_state, so admins can see which entries a lookup could still return.
	private static final String LIST_SQL = """
			SELECT id, exact_query_text, query_mode, knowledge_revision, hit_count, created_at, last_accessed_at,
				knowledge_revision = (SELECT knowledge_revision FROM system_state WHERE id = 1) AS current
			FROM query_cache
			ORDER BY last_accessed_at DESC, id DESC
			""";

	/**
	 * One cache row for the admin view, without the stored response body. current is false once a knowledge write has
	 * moved the revision past this entry, meaning no lookup can return it any more.
	 */
	public record CacheEntry(long id, String queryText, QueryMode mode, long knowledgeRevision, int hitCount,
			Instant createdAt, Instant lastAccessedAt, boolean current) {
	}

	private final JdbcTemplate jdbcTemplate;

	private final JsonMapper jsonMapper;

	public QueryCacheService(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Returns the cached response for this exact question at this revision, recording the hit, or empty on a miss. An
	 * entry that no longer deserializes into responseType (for example after the response record changed shape) is
	 * treated as a miss.
	 */
	public <T> Optional<T> lookup(String queryText, QueryMode mode, long knowledgeRevision, Class<T> responseType) {
		String key = normalize(queryText);
		try {
			List<String> matches = jdbcTemplate.queryForList(LOOKUP_SQL, String.class, mode.name(), knowledgeRevision,
					key, key);
			if (matches.isEmpty()) {
				log.info("Query cache miss for {} at revision {} (key {})", mode, knowledgeRevision, keyId(key));
				return Optional.empty();
			}
			T response = jsonMapper.readValue(matches.get(0), responseType);
			log.info("Query cache hit for {} at revision {} (key {}); skipping gbrain", mode, knowledgeRevision,
					keyId(key));
			return Optional.of(response);
		} catch (DataAccessException | JacksonException ex) {
			log.warn("Query cache lookup failed for {} at revision {}; treating as a miss", mode, knowledgeRevision,
					ex);
			return Optional.empty();
		}
	}

	/** Stores a response for this exact question at this revision; an existing entry for the same key is kept. */
	public void store(String queryText, QueryMode mode, long knowledgeRevision, Object response) {
		String key = normalize(queryText);
		try {
			int inserted = jdbcTemplate.update(STORE_SQL, key, mode.name(), knowledgeRevision,
					jsonMapper.writeValueAsString(response));
			if (inserted > 0) {
				log.info("Query cache stored {} at revision {} (key {})", mode, knowledgeRevision, keyId(key));
			}
		} catch (DataAccessException | JacksonException ex) {
			log.warn("Query cache store failed for {} at revision {}", mode, knowledgeRevision, ex);
		}
	}

	/** Every cache entry, most recently used first. */
	public List<CacheEntry> list() {
		return jdbcTemplate.query(LIST_SQL,
				(rs, rowNum) -> new CacheEntry(rs.getLong("id"), rs.getString("exact_query_text"),
						QueryMode.valueOf(rs.getString("query_mode")), rs.getLong("knowledge_revision"),
						rs.getInt("hit_count"), rs.getTimestamp("created_at").toInstant(),
						rs.getTimestamp("last_accessed_at").toInstant(), rs.getBoolean("current")));
	}

	/** Deletes every cache entry and returns how many were removed. */
	public int clear() {
		return jdbcTemplate.update("DELETE FROM query_cache");
	}

	// Trimming is the only normalization: "naive exact match" per the brief, minus stray surrounding whitespace.
	private static String normalize(String queryText) {
		return queryText.strip();
	}

	// Short id so log lines for the same question can be matched without writing the (possibly sensitive) text.
	private static String keyId(String key) {
		return "%08x".formatted(key.hashCode());
	}

}
