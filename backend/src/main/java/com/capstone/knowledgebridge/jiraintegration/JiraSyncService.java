package com.capstone.knowledgebridge.jiraintegration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.ingestion.IngestionService;
import com.capstone.knowledgebridge.jira.JiraClient;
import com.capstone.knowledgebridge.jira.JiraErrorCode;
import com.capstone.knowledgebridge.jira.JiraException;
import com.capstone.knowledgebridge.jira.model.JiraComment;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraSearchPage;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus;
import com.capstone.knowledgebridge.knowledge.KnowledgeLifecycle;
import com.capstone.knowledgebridge.knowledge.KnowledgeSourceKind;

/**
 * Copies a Jira project's work items into the knowledge base through the normal ingestion pipeline, so revisions, cache
 * invalidation, retries, and attempt history work the same as for any other item.
 */
@Service
public class JiraSyncService {

	private static final Logger log = LoggerFactory.getLogger(JiraSyncService.class);

	private static final String SOURCE = KnowledgeSourceKind.JIRA.name();

	private final JiraClient jiraClient;

	private final JiraSyncRunRepository runs;

	private final KnowledgeItemRepository items;

	private final JiraWorkItemRepository workItems;

	private final IngestionService ingestionService;

	private final TaskExecutor executor;

	public JiraSyncService(JiraClient jiraClient, JiraSyncRunRepository runs, KnowledgeItemRepository items,
			JiraWorkItemRepository workItems, IngestionService ingestionService,
			@Qualifier("jiraSyncExecutor") TaskExecutor executor) {
		this.jiraClient = jiraClient;
		this.runs = runs;
		this.items = items;
		this.workItems = workItems;
		this.ingestionService = ingestionService;
		this.executor = executor;
	}

	/** Records and starts a run, or returns empty when the project already has one running. */
	public Optional<JiraSyncRun> start(String projectKey, long triggeredBy) {
		if (!jiraClient.status().configured()) {
			throw new JiraException(JiraErrorCode.NOT_CONFIGURED, "Jira integration is not configured");
		}
		JiraSyncRun run;
		try {
			// The partial unique index on active runs decides any race between two requests.
			run = runs.saveAndFlush(new JiraSyncRun(projectKey, triggeredBy));
		} catch (DataIntegrityViolationException exception) {
			return Optional.empty();
		}
		long runId = run.getId();
		executor.execute(() -> execute(runId));
		return Optional.of(run);
	}

	public List<JiraSyncRun> recentRuns() {
		return runs.findTop50ByOrderByStartedAtDescIdDesc();
	}

	public Optional<JiraSyncRun> findRun(long id) {
		return runs.findById(id);
	}

	/** A run that was active when the application stopped would otherwise block its project forever. */
	@EventListener(ApplicationReadyEvent.class)
	public void failInterruptedRuns() {
		for (JiraSyncRun run : runs.findByStatus(JiraSyncRunStatus.RUNNING)) {
			run.fail("INTERRUPTED", "The application restarted before this sync finished.");
			runs.save(run);
		}
	}

	void execute(long runId) {
		JiraSyncRun run = runs.findById(runId).orElseThrow();
		try {
			String jql = "project = \"" + run.getProjectKey() + "\" ORDER BY updated ASC";
			String token = null;
			JiraSearchPage page;
			do {
				page = jiraClient.searchIssues(jql, token);
				for (JiraIssue issue : page.issues()) {
					run.record(sync(issue, run.getTriggeredBy()));
				}
				runs.save(run);
				token = page.nextPageToken();
			} while (!page.last() && token != null);
			run.complete();
		} catch (JiraException exception) {
			log.warn("Jira sync {} stopped: {}", runId, exception.code());
			run.fail(exception.code().name(), exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Jira sync {} failed unexpectedly", runId, exception);
			run.fail("INTERNAL_ERROR", "The sync stopped unexpectedly.");
		}
		runs.save(run);
	}

	/** A Jira failure stops the run; anything wrong with one work item only fails that item. */
	private JiraSyncOutcome sync(JiraIssue issue, long ownerId) {
		try {
			Optional<KnowledgeItem> existing = items.findByExternalSourceAndExternalKey(SOURCE, issue.key());
			Optional<KnowledgeItem> unchanged = existing.filter(item -> isUnchanged(item, issue));
			if (unchanged.isPresent()) {
				workItems.save(JiraWorkItem.from(unchanged.get().getId(), issue));
				return JiraSyncOutcome.UNCHANGED;
			}
			String title = JiraMarkdownConverter.title(issue);
			String type = Objects.requireNonNullElse(issue.type(), "work-item");
			String body = JiraMarkdownConverter.toMarkdown(issue, comments(issue));
			KnowledgeItem item = existing.orElse(null);
			if (item == null) {
				item = KnowledgeItem.external(title, type, ownerId, body, KnowledgeSourceKind.JIRA, issue.key(),
						issue.browseUrl(), issue.updated());
			} else {
				item.applyExternalUpdate(title, type, body, issue.browseUrl(), issue.updated());
			}
			item = items.save(item);
			workItems.save(JiraWorkItem.from(item.getId(), issue));
			ingestionService.ingest(item);
			item = items.save(item);
			if (item.getStatus() != KnowledgeItemStatus.COMPLETED) {
				return JiraSyncOutcome.FAILED;
			}
			return existing.isPresent() ? JiraSyncOutcome.UPDATED : JiraSyncOutcome.CREATED;
		} catch (JiraException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			log.warn("Could not sync Jira work item {}", issue.key(), exception);
			return JiraSyncOutcome.FAILED;
		}
	}

	/** Trashed or deactivated items stay out of gbrain, and a failed item is always worth another attempt. */
	private static boolean isUnchanged(KnowledgeItem item, JiraIssue issue) {
		return item.getLifecycleState() != KnowledgeLifecycle.ACTIVE
				|| (item.getStatus() == KnowledgeItemStatus.COMPLETED
						&& issue.updated().equals(item.getExternalUpdatedAt()));
	}

	private List<JiraComment> comments(JiraIssue issue) {
		return issue.commentTotal() > issue.comments().size() ? jiraClient.listComments(issue.key()) : issue.comments();
	}

}
