package com.capstone.knowledgebridge.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Exercises the exact maintenance SQL against real migration metadata and application tables. */
@Testcontainers
class LegacyCacheHistoryTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	private JdbcTemplate jdbc;
	private Flyway flyway;
	private String reconciliation;

	@BeforeEach
	void legacyDatabase() throws Exception {
		flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.cleanDisabled(false).target("10").load();
		flyway.clean();
		flyway.migrate();
		jdbc = new JdbcTemplate(
				new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
		assertThat(
				jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version = '10'", Integer.class))
				.isEqualTo(-844063123);
		jdbc.update(
				"UPDATE flyway_schema_history SET version = '7', script = 'V7__create_query_cache.sql' WHERE version = '10'");
		reconciliation = Files.readString(Path.of("../scripts/reconcile-query-cache-history.sql"));
	}

	@Test
	void reconcilesOnlyTheRenameAndPreservesNotesAttemptsCacheAndGlobalRevision() {
		Long id = jdbc.queryForObject("""
				INSERT INTO knowledge_item (title, type, owner_id, status, character_count, body_text)
				VALUES ('Preserved note', 'Note', 1, 'COMPLETED', 4, 'Body') RETURNING id
				""", Long.class);
		jdbc.update(
				"INSERT INTO ingestion_attempt (knowledge_item_id, attempt_number, status, started_at, completed_at) VALUES (?, 1, 'COMPLETED', now(), now())",
				id);
		jdbc.update("UPDATE system_state SET knowledge_revision = 42 WHERE id = 1");
		jdbc.update(
				"INSERT INTO query_cache (exact_query_text, query_mode, knowledge_revision, response_json, hit_count) VALUES ('Preserve cache', 'SYNTHESIS', 42, '{\"answer\":\"Preserved answer\"}', 3)");
		assertThatThrownBy(() -> flyway.validate()).hasMessageContaining("7");
		jdbc.execute(reconciliation);
		jdbc.execute(reconciliation);
		Flyway upgraded = Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
		assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(2);
		upgraded.validate();
		assertThat(jdbc.queryForObject("SELECT body_text FROM knowledge_item WHERE id = ?", String.class, id))
				.isEqualTo("Body");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_attempt WHERE knowledge_item_id = ?", Long.class,
				id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT knowledge_revision FROM system_state WHERE id = 1", Long.class))
				.isEqualTo(42);
		assertThat(jdbc.queryForObject("SELECT response_json->>'answer' FROM query_cache", String.class))
				.isEqualTo("Preserved answer");
		assertThat(jdbc.queryForObject("SELECT hit_count FROM query_cache", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM query_usage", Long.class)).isZero();
	}

	@Test
	void refusesAnUnknownChecksumWithoutChangingHistory() {
		jdbc.update("UPDATE flyway_schema_history SET checksum = 123 WHERE version = '7'");
		assertThatThrownBy(() -> jdbc.execute(reconciliation)).hasMessageContaining("checksums/history");
		assertThat(jdbc.queryForObject("SELECT script FROM flyway_schema_history WHERE version = '7'", String.class))
				.isEqualTo("V7__create_query_cache.sql");
	}

	@Test
	void refusesAnUnexpectedSchemaOrConflictingVersionTen() {
		jdbc.execute("ALTER TABLE query_cache ADD COLUMN unexpected TEXT");
		assertThatThrownBy(() -> jdbc.execute(reconciliation)).hasMessageContaining("Unexpected query_cache schema");
		jdbc.update(
				"INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) VALUES (100, '10', 'wrong', 'SQL', 'wrong.sql', 0, 'test', 0, true)");
		assertThatThrownBy(() -> jdbc.execute(reconciliation))
				.hasMessageContaining("Unexpected cache migration history");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '7'", Long.class))
				.isEqualTo(1);
	}
}
