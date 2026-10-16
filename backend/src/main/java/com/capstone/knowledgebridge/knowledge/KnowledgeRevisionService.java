package com.capstone.knowledgebridge.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tracks the single global knowledge_revision counter in system_state. IngestionService bumps this after a successful
 * write so the query cache can key cached answers on it - a knowledge-item change invalidates every answer built on the
 * old content.
 */
@Component
public class KnowledgeRevisionService {

	private final JdbcTemplate jdbcTemplate;

	public KnowledgeRevisionService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Returns the current knowledge_revision without changing it; the query cache keys entries on this value. */
	public long current() {
		return jdbcTemplate.queryForObject("SELECT knowledge_revision FROM system_state WHERE id = 1", Long.class);
	}

	/** Atomically increments and returns the new knowledge_revision. */
	public long bump() {
		return jdbcTemplate.queryForObject(
				"UPDATE system_state SET knowledge_revision = knowledge_revision + 1 WHERE id = 1 RETURNING knowledge_revision",
				Long.class);
	}

}
