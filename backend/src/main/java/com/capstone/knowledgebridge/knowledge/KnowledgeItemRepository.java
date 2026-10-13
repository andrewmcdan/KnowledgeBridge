package com.capstone.knowledgebridge.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, Long> {
}
