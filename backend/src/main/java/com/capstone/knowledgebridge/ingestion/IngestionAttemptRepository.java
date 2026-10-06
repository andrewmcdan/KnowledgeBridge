package com.capstone.knowledgebridge.ingestion;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestionAttemptRepository extends JpaRepository<IngestionAttempt, Long> {
	long countByKnowledgeItemId(Long knowledgeItemId);

	/** The highest-numbered attempt for each of the given items, in one query so listing items isn't N+1. */
	@Query("""
			SELECT a FROM IngestionAttempt a
			WHERE a.knowledgeItemId IN :itemIds
			  AND a.attemptNumber = (SELECT MAX(b.attemptNumber) FROM IngestionAttempt b
			                         WHERE b.knowledgeItemId = a.knowledgeItemId)
			""")
	List<IngestionAttempt> findLatestByKnowledgeItemIdIn(@Param("itemIds") Collection<Long> itemIds);
}
