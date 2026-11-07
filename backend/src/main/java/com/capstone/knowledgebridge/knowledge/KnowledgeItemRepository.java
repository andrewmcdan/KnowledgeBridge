package com.capstone.knowledgebridge.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select k from KnowledgeItem k where k.id = :id")
	Optional<KnowledgeItem> findForLifecycle(long id);

	List<KnowledgeItem> findBySourceKindAndStatusIn(KnowledgeSourceKind sourceKind,
			Collection<KnowledgeItemStatus> statuses);
}
