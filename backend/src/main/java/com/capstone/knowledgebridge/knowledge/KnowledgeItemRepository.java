package com.capstone.knowledgebridge.knowledge;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, Long> {

	List<KnowledgeItem> findBySourceKindAndStatusIn(KnowledgeSourceKind sourceKind,
			Collection<KnowledgeItemStatus> statuses);
}
