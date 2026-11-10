package com.capstone.knowledgebridge.jiraintegration;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Only items that are indexed and active are visible, matching what signed-in users see in the knowledge base. */
public interface JiraWorkItemRepository extends JpaRepository<JiraWorkItem, Long> {

	String VISIBLE = """
			select w from JiraWorkItem w, KnowledgeItem k
			where k.id = w.knowledgeItemId
			and k.status = com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus.COMPLETED
			and k.lifecycleState = com.capstone.knowledgebridge.knowledge.KnowledgeLifecycle.ACTIVE
			""";

	// The casts give PostgreSQL a type for parameters that are null.
	@Query(VISIBLE + """
			and (cast(:project as String) is null or w.projectKey = :project)
			and (cast(:type as String) is null or w.issueType = :type)
			and (cast(:status as String) is null or w.status = :status)
			and (cast(:assignee as String) is null or w.assignee = :assignee)
			order by w.jiraUpdatedAt desc, w.issueKey
			""")
	List<JiraWorkItem> findVisible(String project, String type, String status, String assignee);

	@Query(VISIBLE + "and w.issueKey = :key")
	Optional<JiraWorkItem> findVisibleByIssueKey(String key);

}
