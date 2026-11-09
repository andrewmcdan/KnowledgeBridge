package com.capstone.knowledgebridge.common;

import static org.assertj.core.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class LifecycleMigrationTests {
	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Test
	void upgradePreservesContentOwnershipAndPreviouslyTrashedRows() {
		Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.target("13").load().migrate();
		JdbcTemplate jdbc = new JdbcTemplate(
				new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
		long id = jdbc.queryForObject(
				"INSERT INTO knowledge_item(title,type,owner_id,status,character_count,body_text,soft_deleted_at) VALUES ('Retained','Note',1,'COMPLETED',4,'Body',now()) RETURNING id",
				Long.class);
		Flyway upgraded = Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
		assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(3);
		upgraded.validate();
		assertThat(jdbc.queryForMap(
				"SELECT owner_id,body_text,lifecycle_state,trashed_from FROM knowledge_item WHERE id = ?", id))
				.containsEntry("owner_id", 1L).containsEntry("body_text", "Body")
				.containsEntry("lifecycle_state", "TRASH").containsEntry("trashed_from", "ACTIVE");
		assertThatThrownBy(() -> jdbc.update("UPDATE knowledge_item SET trashed_from = NULL WHERE id = ?", id))
				.hasMessageContaining("knowledge_item_lifecycle_check");
		assertThatThrownBy(() -> jdbc.update("UPDATE knowledge_item SET lifecycle_state = 'ACTIVE' WHERE id = ?", id))
				.hasMessageContaining("knowledge_item_lifecycle_check");
	}
}
