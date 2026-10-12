package com.capstone.knowledgebridge.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tracks the single global knowledge_revision counter in system_state. IngestionService bumps this after a successful
 * write so the query cache (not built yet) can key cached answers on it - a knowledge-item change invalidates every
 * answer built on the old content.
 */
@Component
public class KnowledgeRevisionService {

	private final JdbcTemplate jdbcTemplate;

	public KnowledgeRevisionService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Atomically increments and returns the new knowledge_revision. */
	public long bump() {
		return jdbcTemplate.queryForObject(
				"UPDATE system_state SET knowledge_revision = knowledge_revision + 1 WHERE id = 1 RETURNING knowledge_revision",
				Long.class);
	}

}
