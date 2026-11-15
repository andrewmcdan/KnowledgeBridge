package com.capstone.knowledgebridge.jiraintegration;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;

/** Reads synced work items for the Jira View. Serves local rows only, so users see exactly what the AI knows. */
@Service
public class JiraWorkItemService {

	public record Detail(JiraWorkItem workItem, String body) {
	}

	private final JiraWorkItemRepository workItems;

	private final KnowledgeItemRepository items;

	public JiraWorkItemService(JiraWorkItemRepository workItems, KnowledgeItemRepository items) {
		this.workItems = workItems;
		this.items = items;
	}

	/** Each filter is an exact match; a null or blank filter is ignored. */
	public List<JiraWorkItem> list(String project, String type, String status, String assignee) {
		return workItems.findVisible(blankToNull(project), blankToNull(type), blankToNull(status),
				blankToNull(assignee));
	}

	public Optional<Detail> find(String key) {
		return workItems.findVisibleByIssueKey(key)
				.flatMap(workItem -> items.findById(workItem.getKnowledgeItemId())
						.map(KnowledgeItem::getBodyText)
						.map(body -> new Detail(workItem, body)));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

}
