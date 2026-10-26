package com.capstone.knowledgebridge.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RegistrationMigrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	private JdbcTemplate jdbc;
	private Flyway upgraded;

	@BeforeEach
	void versionElevenDatabase() {
		Flyway previous = Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.target("11").cleanDisabled(false).load();
		previous.clean();
		previous.migrate();
		jdbc = new JdbcTemplate(
				new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
		upgraded = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.load();
	}

	@Test
	void upgradePreservesLegacyIdentityAndOwnershipAndRejectsNormalizedDuplicates() {
		Long id = jdbc.queryForObject(
				"INSERT INTO app_user (email, password_hash, display_name, role) VALUES (' Legacy@Example.Test ', 'preserved-hash', 'Legacy', 'USER') RETURNING id",
				Long.class);
		jdbc.update(
				"INSERT INTO knowledge_item (title, type, owner_id, status, character_count, body_text) VALUES ('Owned note', 'Note', ?, 'COMPLETED', 4, 'Body')",
				id);
		assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
		upgraded.validate();
		assertThat(jdbc.queryForMap("SELECT id, email, password_hash FROM app_user WHERE id = ?", id))
				.containsEntry("id", id).containsEntry("email", " Legacy@Example.Test ")
				.containsEntry("password_hash", "preserved-hash");
		assertThat(jdbc.queryForObject("SELECT owner_id FROM knowledge_item WHERE title = 'Owned note'", Long.class))
				.isEqualTo(id);
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO app_user (email, password_hash, display_name, role) VALUES ('legacy@example.test', 'new-hash', 'Other', 'USER')"))
				.hasMessageContaining("uq_app_user_normalized_email");
	}

	@Test
	void collisionsFailTheMigrationWithoutMergingOrDeletingAccounts() {
		jdbc.update(
				"INSERT INTO app_user (email, password_hash, display_name, role) VALUES ('COLLISION@example.test', 'hash-one', 'First', 'USER'), (' collision@example.test ', 'hash-two', 'Second', 'USER')");
		assertThatThrownBy(() -> upgraded.migrate()).hasMessageContaining("existing accounts share a normalized email");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE password_hash IN ('hash-one', 'hash-two')",
				Long.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '12'", Long.class))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT to_regclass('uq_app_user_normalized_email') IS NULL", Boolean.class))
				.isTrue();
	}
}
