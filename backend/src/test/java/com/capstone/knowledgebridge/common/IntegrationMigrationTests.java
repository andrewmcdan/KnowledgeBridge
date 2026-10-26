package com.capstone.knowledgebridge.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Verifies the supported existing V8 history, in addition to clean-database application tests. */
@Testcontainers
class IntegrationMigrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Test
	void videoV8DatabaseUpgradesWithoutLosingItemsAttemptsOrKnowledgeRevision() {
		Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.target("8").load().migrate();
		JdbcTemplate jdbc = new JdbcTemplate(
				new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
		Long id = jdbc.queryForObject("""
				INSERT INTO knowledge_item (title, type, owner_id, status, character_count, body_text)
				VALUES ('Preserved note', 'Note', 1, 'FAILED', 9, 'Keep this') RETURNING id
				""", Long.class);
		jdbc.update(
				"""
						INSERT INTO ingestion_attempt (knowledge_item_id, attempt_number, status, started_at, completed_at, error_code)
						VALUES (?, 1, 'FAILED', now(), now(), 'TIMEOUT')
						""",
				id);
		jdbc.update("UPDATE system_state SET knowledge_revision = 42 WHERE id = 1");

		Flyway upgraded = Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
		assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(4);
		upgraded.validate();

		assertThat(jdbc.queryForObject("SELECT body_text FROM knowledge_item WHERE id = ?", String.class, id))
				.isEqualTo("Keep this");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ingestion_attempt WHERE knowledge_item_id = ?",
				Integer.class, id))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT knowledge_revision FROM system_state WHERE id = 1", Long.class))
				.isEqualTo(42L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM query_cache", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT content_updated_at = created_at FROM knowledge_item WHERE id = ?",
				Boolean.class, id)).isTrue();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM query_usage", Integer.class)).isZero();
		assertThat(
				jdbc.queryForObject("SELECT description FROM flyway_schema_history WHERE version = '8'", String.class))
				.isEqualTo("add video ingestion metadata");
	}
}
