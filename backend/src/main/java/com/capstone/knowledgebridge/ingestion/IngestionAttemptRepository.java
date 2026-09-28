package com.capstone.knowledgebridge.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionAttemptRepository extends JpaRepository<IngestionAttempt, Long> {
	long countByKnowledgeItemId(Long knowledgeItemId);
}
