package com.capstone.knowledgebridge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class KnowledgeBridgeApplicationTests {

	private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
			.parse("pgvector/pgvector:pg17")
			.asCompatibleSubstituteFor("postgres");

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
			.withDatabaseName("knowledgebridge")
			.withUsername("knowledgebridge")
			.withPassword("knowledgebridge");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoadsWithPgvector() {
		Boolean vectorExtensionInstalled = jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
				Boolean.class);

		assertThat(vectorExtensionInstalled).isTrue();
	}

}
