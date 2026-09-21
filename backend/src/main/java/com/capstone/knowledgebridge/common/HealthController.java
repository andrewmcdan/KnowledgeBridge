package com.capstone.knowledgebridge.common;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

	public record HealthResponse(String status, String database, Instant timestamp) {}

	private final JdbcTemplate jdbcTemplate;

	public HealthController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping
	public ResponseEntity<HealthResponse> health() {
		try {
			jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return ResponseEntity.ok(new HealthResponse("UP", "UP", Instant.now()));
		}
		catch (RuntimeException exception) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(new HealthResponse("DOWN", "DOWN", Instant.now()));
		}
	}

}
