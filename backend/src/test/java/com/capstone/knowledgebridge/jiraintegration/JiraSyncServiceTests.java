package com.capstone.knowledgebridge.jiraintegration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.knowledgebridge.ingestion.IngestionService;
import com.capstone.knowledgebridge.jira.JiraClient;
import com.capstone.knowledgebridge.jira.JiraErrorCode;
import com.capstone.knowledgebridge.jira.JiraException;
import com.capstone.knowledgebridge.jira.model.JiraComment;
import com.capstone.knowledgebridge.jira.model.JiraConnectionStatus;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraSearchPage;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus;
import com.capstone.knowledgebridge.knowledge.KnowledgeLifecycle;
import com.capstone.knowledgebridge.knowledge.KnowledgeSourceKind;

class JiraSyncServiceTests {

	private static final Instant UPDATED = Instant.parse("2026-11-05T22:37:59.818Z");

	private static final String JQL = "project = \"ACME\" ORDER BY updated ASC";

	private final JiraClient jira = mock(JiraClient.class);

	private final JiraSyncRunRepository runs = mock(JiraSyncRunRepository.class);

	private final KnowledgeItemRepository items = mock(KnowledgeItemRepository.class);

	private final IngestionService ingestion = mock(IngestionService.class);

	private final JiraWorkItemRepository workItems = mock(JiraWorkItemRepository.class);

	private final JiraSyncService service = new JiraSyncService(jira, runs, items, workItems, ingestion,
			new SyncTaskExecutor());

	private final JiraSyncRun run = new JiraSyncRun("ACME", 7L);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(run, "id", 1L);
		when(jira.status()).thenReturn(new JiraConnectionStatus(true, true, null));
		when(runs.saveAndFlush(any())).thenReturn(run);
		when(runs.findById(1L)).thenReturn(Optional.of(run));
		when(items.save(any())).thenAnswer(invocation -> {
			KnowledgeItem item = invocation.getArgument(0);
			ReflectionTestUtils.setField(item, "id", 5L);
			return item;
		});
		when(items.findByExternalSourceAndExternalKey(anyString(), anyString())).thenReturn(Optional.empty());
		doAnswer(invocation -> {
			invocation.<KnowledgeItem>getArgument(0).markCompleted("slug");
			return null;
		}).when(ingestion).ingest(any());
	}

	@Test
	void createsAJiraKnowledgeItemForANewWorkItem() {
		onePage(issue("ACME-1", UPDATED, "Task", List.of(), 0));

		assertThat(service.start("ACME", 7L)).contains(run);

		ArgumentCaptor<KnowledgeItem> saved = ArgumentCaptor.forClass(KnowledgeItem.class);
		verify(ingestion).ingest(saved.capture());
		KnowledgeItem item = saved.getValue();
		assertThat(item.getSourceKind()).isEqualTo(KnowledgeSourceKind.JIRA);
		assertThat(item.getExternalSource()).isEqualTo("JIRA");
		assertThat(item.getExternalKey()).isEqualTo("ACME-1");
		assertThat(item.getExternalUpdatedAt()).isEqualTo(UPDATED);
		assertThat(item.getTitle()).isEqualTo("ACME-1: Summary");
		assertThat(item.getType()).isEqualTo("Task");
		assertThat(item.getOwnerId()).isEqualTo(7L);
		assertThat(item.getBodyText()).contains("# ACME-1: Summary");
		ArgumentCaptor<JiraWorkItem> workItem = ArgumentCaptor.forClass(JiraWorkItem.class);
		verify(workItems).save(workItem.capture());
		assertThat(workItem.getValue().getKnowledgeItemId()).isEqualTo(5L);
		assertThat(workItem.getValue().getIssueKey()).isEqualTo("ACME-1");
		assertCounts(1, 0, 0, 0);
		assertThat(run.getStatus()).isEqualTo(JiraSyncRunStatus.COMPLETED);
		assertThat(run.getFinishedAt()).isNotNull();
	}

	@Test
	void defaultsTheTypeWhenJiraOmitsIt() {
		onePage(issue("ACME-1", UPDATED, null, List.of(), 0));

		service.start("ACME", 7L);

		ArgumentCaptor<KnowledgeItem> saved = ArgumentCaptor.forClass(KnowledgeItem.class);
		verify(ingestion).ingest(saved.capture());
		assertThat(saved.getValue().getType()).isEqualTo("work-item");
	}

	@Test
	void skipsWorkItemsThatHaveNotChanged() {
		KnowledgeItem existing = existing(UPDATED);
		existing.markCompleted("slug");
		onePage(issue("ACME-1", UPDATED, "Task", List.of(), 0));

		service.start("ACME", 7L);

		verify(ingestion, never()).ingest(any());
		verify(workItems).save(any());
		assertCounts(0, 0, 1, 0);
	}

	@Test
	void reingestsAChangedWorkItemAtANewRevision() {
		KnowledgeItem existing = existing(UPDATED.minusSeconds(60));
		existing.markCompleted("slug");
		long revision = existing.getRevision();
		onePage(issue("ACME-1", UPDATED, "Task", List.of(), 0));

		service.start("ACME", 7L);

		verify(ingestion).ingest(existing);
		assertThat(existing.getRevision()).isEqualTo(revision + 1);
		assertThat(existing.getExternalUpdatedAt()).isEqualTo(UPDATED);
		assertThat(existing.getBodyText()).contains("# ACME-1: Summary");
		assertCounts(0, 1, 0, 0);
	}

	@Test
	void retriesAnItemWhoseLastIngestionFailedEvenIfJiraDidNotChange() {
		KnowledgeItem existing = existing(UPDATED);
		existing.markFailed();
		onePage(issue("ACME-1", UPDATED, "Task", List.of(), 0));

		service.start("ACME", 7L);

		verify(ingestion).ingest(existing);
		assertCounts(0, 1, 0, 0);
	}

	@Test
	void leavesTrashedItemsAloneEvenWhenJiraChanged() {
		KnowledgeItem existing = existing(UPDATED.minusSeconds(60));
		existing.markCompleted("slug");
		existing.changeLifecycle(KnowledgeLifecycle.TRASH);
		onePage(issue("ACME-1", UPDATED, "Task", List.of(), 0));

		service.start("ACME", 7L);

		verify(ingestion, never()).ingest(any());
		assertCounts(0, 0, 1, 0);
	}

	@Test
	void countsAnIngestionFailureAndKeepsGoing() {
		doAnswer(invocation -> {
			invocation.<KnowledgeItem>getArgument(0).markFailed();
			return null;
		}).doAnswer(invocation -> {
			invocation.<KnowledgeItem>getArgument(0).markCompleted("slug");
			return null;
		}).when(ingestion).ingest(any());
		onePage(issue("ACME-1", UPDATED, "Task", List.of(), 0), issue("ACME-2", UPDATED, "Task", List.of(), 0));

		service.start("ACME", 7L);

		assertCounts(1, 0, 0, 1);
		assertThat(run.getStatus()).isEqualTo(JiraSyncRunStatus.COMPLETED);
	}

	@Test
	void anUnexpectedProblemWithOneItemDoesNotStopTheRun() {
		when(items.findByExternalSourceAndExternalKey("JIRA", "ACME-1")).thenThrow(new IllegalStateException("boom"));
		onePage(issue("ACME-1", UPDATED, "Task", List.of(), 0), issue("ACME-2", UPDATED, "Task", List.of(), 0));

		service.start("ACME", 7L);

		assertCounts(1, 0, 0, 1);
	}

	@Test
	void fetchesAllCommentsWhenTheSearchReturnedOnlySome() {
		JiraComment embedded = new JiraComment("1", "A", UPDATED, "embedded");
		JiraComment complete = new JiraComment("2", "B", UPDATED, "complete list");
		when(jira.listComments("ACME-1")).thenReturn(List.of(embedded, complete));
		onePage(issue("ACME-1", UPDATED, "Task", List.of(embedded), 2));

		service.start("ACME", 7L);

		ArgumentCaptor<KnowledgeItem> saved = ArgumentCaptor.forClass(KnowledgeItem.class);
		verify(ingestion).ingest(saved.capture());
		assertThat(saved.getValue().getBodyText()).contains("complete list");
	}

	@Test
	void usesEmbeddedCommentsWhenTheyAreComplete() {
		onePage(issue("ACME-1", UPDATED, "Task", List.of(new JiraComment("1", "A", UPDATED, "embedded")), 1));

		service.start("ACME", 7L);

		verify(jira, never()).listComments(any());
	}

	@Test
	void followsPageTokensUntilJiraSaysItIsDone() {
		when(jira.searchIssues(JQL, null)).thenReturn(
				new JiraSearchPage(List.of(issue("ACME-1", UPDATED, "Task", List.of(), 0)), "next", false));
		when(jira.searchIssues(JQL, "next"))
				.thenReturn(new JiraSearchPage(List.of(issue("ACME-2", UPDATED, "Task", List.of(), 0)), null, true));

		service.start("ACME", 7L);

		assertCounts(2, 0, 0, 0);
	}

	@Test
	void stopsIfJiraSaysThereIsMoreButGivesNoToken() {
		when(jira.searchIssues(JQL, null)).thenReturn(new JiraSearchPage(List.of(), null, false));

		service.start("ACME", 7L);

		verify(jira, times(1)).searchIssues(any(), any());
		assertThat(run.getStatus()).isEqualTo(JiraSyncRunStatus.COMPLETED);
	}

	@Test
	void aJiraFailureEndsTheRun() {
		when(jira.searchIssues(any(), isNull()))
				.thenThrow(new JiraException(JiraErrorCode.UNAVAILABLE, "Jira is down"));

		service.start("ACME", 7L);

		assertThat(run.getStatus()).isEqualTo(JiraSyncRunStatus.FAILED);
		assertThat(run.getErrorCode()).isEqualTo("UNAVAILABLE");
		assertThat(run.getErrorMessage()).isEqualTo("Jira is down");
	}

	@Test
	void aJiraFailureWhileReadingCommentsEndsTheRun() {
		when(jira.listComments("ACME-1")).thenThrow(new JiraException(JiraErrorCode.RATE_LIMITED, "slow down"));
		onePage(issue("ACME-1", UPDATED, "Task", List.of(), 3));

		service.start("ACME", 7L);

		assertThat(run.getStatus()).isEqualTo(JiraSyncRunStatus.FAILED);
		assertThat(run.getErrorCode()).isEqualTo("RATE_LIMITED");
	}

	@Test
	void anUnexpectedFailureEndsTheRunWithASafeMessage() {
		when(jira.searchIssues(any(), isNull())).thenThrow(new IllegalStateException("secret detail"));

		service.start("ACME", 7L);

		assertThat(run.getStatus()).isEqualTo(JiraSyncRunStatus.FAILED);
		assertThat(run.getErrorCode()).isEqualTo("INTERNAL_ERROR");
		assertThat(run.getErrorMessage()).doesNotContain("secret detail");
	}

	@Test
	void reportsAConflictWhenTheProjectIsAlreadySyncing() {
		when(runs.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThat(service.start("ACME", 7L)).isEmpty();

		verify(jira, never()).searchIssues(any(), any());
	}

	@Test
	void refusesToStartWithoutConfiguration() {
		when(jira.status()).thenReturn(new JiraConnectionStatus(false, false, JiraErrorCode.NOT_CONFIGURED));

		assertThatThrownBy(() -> service.start("ACME", 7L)).isInstanceOfSatisfying(JiraException.class,
				exception -> assertThat(exception.code()).isEqualTo(JiraErrorCode.NOT_CONFIGURED));
		verify(runs, never()).saveAndFlush(any());
	}

	@Test
	void startsWhenConfiguredEvenIfJiraIsCurrentlyUnreachable() {
		when(jira.status()).thenReturn(new JiraConnectionStatus(true, false, JiraErrorCode.UNAVAILABLE));
		when(jira.searchIssues(any(), isNull())).thenThrow(new JiraException(JiraErrorCode.UNAVAILABLE, "down"));

		assertThat(service.start("ACME", 7L)).contains(run);
		assertThat(run.getStatus()).isEqualTo(JiraSyncRunStatus.FAILED);
	}

	@Test
	void marksRunsInterruptedByARestartAsFailed() {
		when(runs.findByStatus(JiraSyncRunStatus.RUNNING)).thenReturn(List.of(run));

		service.failInterruptedRuns();

		assertThat(run.getStatus()).isEqualTo(JiraSyncRunStatus.FAILED);
		assertThat(run.getErrorCode()).isEqualTo("INTERRUPTED");
		verify(runs).save(run);
	}

	@Test
	void exposesRunHistory() {
		when(runs.findTop50ByOrderByStartedAtDescIdDesc()).thenReturn(List.of(run));

		assertThat(service.recentRuns()).containsExactly(run);
		assertThat(service.findRun(1L)).contains(run);
	}

	@Test
	void truncatesLongErrorMessages() {
		JiraSyncRun failed = new JiraSyncRun("ACME", 7L);

		failed.fail("X", "m".repeat(1500));

		assertThat(failed.getErrorMessage()).hasSize(1000);
		assertThat(failed.getTriggeredBy()).isEqualTo(7L);
		assertThat(failed.getStartedAt()).isNotNull();
	}

	private void onePage(JiraIssue... issues) {
		when(jira.searchIssues(JQL, null)).thenReturn(new JiraSearchPage(List.of(issues), null, true));
	}

	private KnowledgeItem existing(Instant externalUpdatedAt) {
		KnowledgeItem item = KnowledgeItem.external("Old", "Task", 7L, "old body", KnowledgeSourceKind.JIRA, "ACME-1",
				"https://example.atlassian.net/browse/ACME-1", externalUpdatedAt);
		when(items.findByExternalSourceAndExternalKey(eq("JIRA"), eq("ACME-1"))).thenReturn(Optional.of(item));
		return item;
	}

	private void assertCounts(int created, int updated, int unchanged, int failed) {
		assertThat(run.getCreatedCount()).isEqualTo(created);
		assertThat(run.getUpdatedCount()).isEqualTo(updated);
		assertThat(run.getUnchangedCount()).isEqualTo(unchanged);
		assertThat(run.getFailedCount()).isEqualTo(failed);
	}

	private static JiraIssue issue(String key, Instant updated, String type, List<JiraComment> comments, int total) {
		return new JiraIssue("1", key, "Summary", "ACME", "Acme Delivery", type, "To Do", "High", List.of(), null, "R",
				null, null, updated, updated, "Description", comments, total, List.of(),
				"https://example.atlassian.net/browse/" + key);
	}

}
