package com.capstone.knowledgebridge.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.capstone.knowledgebridge.jira.MockJiraMcpServer.Response;
import com.capstone.knowledgebridge.jira.model.JiraComment;
import com.capstone.knowledgebridge.jira.model.JiraConnectionStatus;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraIssueLink;
import com.capstone.knowledgebridge.jira.model.JiraSearchPage;

import tools.jackson.databind.JsonNode;

class McpJiraClientTests {

	private static final String PROJECTS = MockJiraMcpServer.fixture("projects.json");

	private final MockJiraMcpServer server = new MockJiraMcpServer();

	private final McpJiraClient client = server.client();

	private final List<Duration> sleeps = new ArrayList<>();

	@AfterEach
	void stopServer() {
		server.close();
	}

	@Test
	void opensOneSessionAndAuthenticatesEveryRequest() {
		server.onTool("listJiraProjects", arguments -> PROJECTS);

		assertThat(client.listProjects()).extracting("key").containsExactly("ACME", "KAN");
		client.listProjects();

		assertThat(server.requests).extracting("method").containsExactly("initialize", "notifications/initialized",
				"tools/call", "tools/call");
		String expected = "Basic " + Base64.getEncoder()
				.encodeToString("user@example.com:secret-token".getBytes(StandardCharsets.UTF_8));
		assertThat(server.requests).allSatisfy(request -> assertThat(request.authorization()).isEqualTo(expected));
		assertThat(server.requests.get(0).session()).isNull();
		assertThat(server.requests.subList(1, 4))
				.allSatisfy(request -> assertThat(request.session()).isEqualTo("session-1"));
		JsonNode call = server.requests.get(2).body().path("params");
		assertThat(call.path("name").asString()).isEqualTo("executeRead");
		assertThat(call.path("arguments").path("cloudId").asString()).isEqualTo("cloud-1");
		assertThat(call.path("arguments").path("inputs").path("maxResults").asInt()).isEqualTo(10);
	}

	@Test
	void searchRequestsFullMarkdownAndMapsTheWorkItem() {
		server.onTool("searchJiraIssuesUsingJql", arguments -> MockJiraMcpServer.fixture("search-full-page-1.json"));

		JiraSearchPage page = client.searchIssues("project = ACME", null);

		JsonNode arguments = server.requests.get(2).body().path("params").path("arguments");
		assertThat(arguments.path("view").asString()).isEqualTo("full");
		assertThat(arguments.path("responseContentFormat").asString()).isEqualTo("markdown");
		assertThat(arguments.path("jql").asString()).isEqualTo("project = ACME");
		assertThat(arguments.has("nextPageToken")).isFalse();
		assertThat(arguments.toString()).contains("description", "comment", "issuelinks", "parent", "project");
		assertThat(page.issues()).hasSize(10);
		assertThat(page.last()).isFalse();
		assertThat(page.nextPageToken()).isNotBlank();
		JiraIssue issue = page.issues().get(4);
		assertThat(issue.key()).isEqualTo("ACME-5");
		assertThat(issue.description()).contains("The August 27 restore drill").contains("* Update the runbook");
		assertThat(issue.status()).isEqualTo("In Progress");
		assertThat(issue.type()).isEqualTo("Task");
		assertThat(issue.priority()).isEqualTo("Highest");
		assertThat(issue.labels()).contains("cedar", "release-gate");
		assertThat(issue.assignee()).isNull();
		assertThat(issue.reporter()).isEqualTo("Test User");
		assertThat(issue.parentKey()).isEqualTo("ACME-1");
		assertThat(issue.parentSummary()).isEqualTo("Project Cedar: Atlas approval reliability");
		assertThat(issue.created()).isEqualTo(Instant.parse("2026-11-05T22:37:59.818Z"));
		assertThat(issue.commentTotal()).isEqualTo(1);
		assertThat(issue.comments()).singleElement().satisfies(comment -> {
			assertThat(comment.author()).isEqualTo("Test User");
			assertThat(comment.body()).startsWith("Runbook updated");
		});
		assertThat(issue.links()).containsExactly(new JiraIssueLink("is blocked by", "ACME-6"));
		assertThat(issue.browseUrl()).isEqualTo("https://example.atlassian.net/browse/ACME-5");
	}

	@Test
	void searchSendsThePageTokenAndMapsOutwardLinks() {
		server.onTool("searchJiraIssuesUsingJql",
				arguments -> """
						{"data":{"isLast":true,"issues":[{"id":"1","key":"ACME-1","fields":{"summary":"S",
						"created":"2026-11-05T17:38:23.757-0500","updated":"2026-11-05T17:38:23.757-0500",
						"issuelinks":[{"type":{"inward":"is blocked by","outward":"blocks"},"outwardIssue":{"key":"ACME-2"}}]}}]}}
						""");

		JiraSearchPage page = client.searchIssues("project = ACME", "token-2");

		assertThat(server.requests.get(2).body().path("params").path("arguments").path("nextPageToken").asString())
				.isEqualTo("token-2");
		assertThat(page.last()).isTrue();
		assertThat(page.nextPageToken()).isNull();
		JiraIssue issue = page.issues().get(0);
		assertThat(issue.links()).containsExactly(new JiraIssueLink("blocks", "ACME-2"));
		assertThat(issue.description()).isEmpty();
		assertThat(issue.commentTotal()).isZero();
	}

	@Test
	void getIssueMapsTheFullView() {
		server.onTool("getJiraIssue", arguments -> MockJiraMcpServer.fixture("issue-full-ACME-22.json"));

		JiraIssue issue = client.getIssue("ACME-22").orElseThrow();

		assertThat(issue.projectKey()).isEqualTo("ACME");
		assertThat(issue.projectName()).isEqualTo("Acme Delivery");
		assertThat(issue.description()).contains("## Expected");
		assertThat(server.requests.get(2).body().path("params").path("arguments").path("issueIdOrKey").asString())
				.isEqualTo("ACME-22");
	}

	@Test
	void getIssueTreatsNotFoundAsEmptyButPropagatesOtherFailures() {
		server.onToolError("getJiraIssue", "{\"error\":true,\"message\":\"Issue not found\",\"statusCode\":404}");
		assertThat(client.getIssue("ACME-9999")).isEmpty();

		server.onToolError("getJiraIssue", "{\"error\":true,\"statusCode\":500}");
		assertFails(() -> client.getIssue("ACME-1"), JiraErrorCode.UNAVAILABLE);
	}

	@Test
	void listCommentsPagesUntilJiraSaysItIsDone() {
		AtomicInteger calls = new AtomicInteger();
		server.onTool("listJiraIssueComments", arguments -> calls.incrementAndGet() == 1
				? comments(arguments.path("startAt").asInt(), false)
				: comments(arguments.path("startAt").asInt(), true));

		List<JiraComment> comments = client.listComments("ACME-5");

		assertThat(comments).extracting("id").containsExactly("c0", "c1");
		assertThat(comments.get(0).created()).isEqualTo(Instant.parse("2026-11-05T22:38:00.498Z"));
		assertThat(server.requests.get(3).body().path("params").path("arguments").path("startAt").asInt()).isEqualTo(1);
	}

	@Test
	void pagingStopsOnAnEmptyPageEvenIfJiraSaysThereIsMore() {
		server.onTool("listJiraProjects", arguments -> "{\"data\":{\"isLast\":false,\"values\":[]}}");

		assertThat(client.listProjects()).isEmpty();
	}

	@Test
	void statusReportsAWorkingConnection() {
		server.onTool("listJiraProjects", arguments -> PROJECTS);

		assertThat(client.status()).isEqualTo(new JiraConnectionStatus(true, true, null));
	}

	@Test
	void statusReportsARefusedToolEvenThoughTheSessionOpened() {
		server.onToolError("listJiraProjects",
				"{\"error\":true,\"message\":\"Insufficient scopes\",\"statusCode\":403}");

		assertThat(client.status()).isEqualTo(new JiraConnectionStatus(true, false, JiraErrorCode.UNAUTHORIZED));
	}

	@Test
	void unconfiguredClientFailsWithoutCallingJira() {
		JiraProperties configured = server.properties();
		JiraProperties disabled = new JiraProperties(false, null, configured.mcpUrl(), configured.siteUrl(),
				configured.email(), configured.apiToken(), configured.cloudId(), configured.connectTimeout(),
				configured.readTimeout(), configured.maxResponseSize(), configured.pageSize(), 1,
				Duration.ofSeconds(1));
		McpJiraClient unconfigured = MockJiraMcpServer.client(disabled);

		assertFails(unconfigured::listProjects, JiraErrorCode.NOT_CONFIGURED);
		assertThat(unconfigured.status())
				.isEqualTo(new JiraConnectionStatus(false, false, JiraErrorCode.NOT_CONFIGURED));
		assertThat(server.requests).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {"400|VALIDATION", "401|UNAUTHORIZED", "403|UNAUTHORIZED", "404|NOT_FOUND",
			"429|RATE_LIMITED", "503|UNAVAILABLE", "418|PROTOCOL"})
	void classifiesToolFailuresByStatus(int status, JiraErrorCode expected) {
		server.onToolError("listJiraProjects", "{\"error\":true,\"statusCode\":" + status + "}");

		assertFails(client::listProjects, expected);
	}

	@Test
	void classifiesTheDisabledApiTokenRefusal() {
		server.onToolError("listJiraProjects",
				"{\"error\":true,\"message\":\"You don't have permission to connect via API token.\"}");

		assertFails(client::listProjects, JiraErrorCode.UNAUTHORIZED);
	}

	@Test
	void treatsAnUnreadableToolFailureAsAProtocolError() {
		server.onToolError("listJiraProjects", "not json");
		assertFails(client::listProjects, JiraErrorCode.PROTOCOL);

		server.onToolError("listJiraProjects", "{\"error\":true,\"message\":\"Something else\"}");
		assertFails(client::listProjects, JiraErrorCode.PROTOCOL);
	}

	@ParameterizedTest
	@CsvSource({"401,UNAUTHORIZED", "429,RATE_LIMITED", "500,UNAVAILABLE", "404,NOT_FOUND"})
	void classifiesHttpFailuresBeforeASessionExists(int status, JiraErrorCode expected) {
		server.onMethod("initialize", request -> Response.status(status));

		assertFails(client::listProjects, expected);
	}

	@Test
	void classifiesHttpFailuresOnAnOpenSession() {
		server.onMethod("tools/call", request -> Response.status(401));

		assertFails(client::listProjects, JiraErrorCode.UNAUTHORIZED);
	}

	@ParameterizedTest
	@CsvSource({"400", "404"})
	void reopensARejectedSessionOnce(int status) {
		server.onTool("listJiraProjects", arguments -> PROJECTS);
		client.listProjects();
		AtomicInteger calls = new AtomicInteger();
		server.onMethod("tools/call", request -> calls.incrementAndGet() == 1
				? Response.status(status)
				: Response.sse(MockJiraMcpServer.toolResult(request, PROJECTS, false)));

		assertThat(client.listProjects()).hasSize(2);
		assertThat(server.requests.getLast().session()).isEqualTo("session-2");
	}

	@Test
	void givesUpWhenTheNewSessionIsAlsoRejected() {
		server.onMethod("tools/call", request -> Response.status(404));

		assertFails(client::listProjects, JiraErrorCode.PROTOCOL);
	}

	@Test
	void requiresASessionFromInitialize() {
		server.onMethod("initialize", request -> Response.sse(MockJiraMcpServer.result(request, "{}")));

		assertFails(client::listProjects, JiraErrorCode.PROTOCOL);
	}

	@Test
	void acceptsPlainJsonResponses() {
		server.onMethod("tools/call", request -> new Response(200, "application/json",
				MockJiraMcpServer.toolResult(request, PROJECTS, false), null));

		assertThat(client.listProjects()).hasSize(2);
	}

	@Test
	void ignoresOtherMessagesOnTheStream() {
		server.onMethod("tools/call", request -> new Response(200, "text/event-stream",
				"event: ping\ndata: {\"method\":\"notifications/progress\"}\n\ndata: "
						+ MockJiraMcpServer.toolResult(request, PROJECTS, false) + "\n\n",
				null));

		assertThat(client.listProjects()).hasSize(2);
	}

	@Test
	void rejectsMalformedEnvelopes() {
		Map<String, java.util.function.Function<MockJiraMcpServer.Request, Response>> malformed = Map.of(
				"invalid JSON", request -> Response.sse("not json"),
				"another request id", request -> Response.sse("{\"jsonrpc\":\"2.0\",\"id\":999,\"result\":{}}"),
				"a JSON-RPC error", request -> Response.sse("{\"jsonrpc\":\"2.0\",\"id\":"
						+ request.body().path("id").asLong() + ",\"error\":{\"code\":-32600}}"),
				"a tool result with no data",
				request -> Response.sse(MockJiraMcpServer.toolResult(request, "{}", false)),
				"a tool result that is not JSON",
				request -> Response.sse(MockJiraMcpServer.toolResult(request, "oops", false)));

		malformed.forEach((name, response) -> {
			server.onMethod("tools/call", response);
			assertFails(client::listProjects, JiraErrorCode.PROTOCOL);
		});
	}

	@Test
	void rejectsPayloadsThatWouldLoseContent() {
		String issue = """
				{"data":{"id":"1","key":"ACME-1","fields":{"summary":"S","created":"%s",
				"updated":"2026-11-05T17:38:23.757-0500","description":%s}}}
				""";
		String valid = "2026-11-05T17:38:23.757-0500";

		server.onTool("getJiraIssue", arguments -> issue.formatted(valid, "{\"type\":\"doc\"}"));
		assertFails(() -> client.getIssue("ACME-1"), JiraErrorCode.PROTOCOL);

		server.onTool("getJiraIssue", arguments -> issue.formatted("yesterday", "\"text\""));
		assertFails(() -> client.getIssue("ACME-1"), JiraErrorCode.PROTOCOL);

		server.onTool("getJiraIssue", arguments -> "{\"data\":{\"id\":\"1\",\"fields\":{}}}");
		assertFails(() -> client.getIssue("ACME-1"), JiraErrorCode.PROTOCOL);
	}

	@Test
	void rejectsOversizedResponses() {
		server.onTool("listJiraProjects", arguments -> PROJECTS);

		assertFails(MockJiraMcpServer.client(server.properties(64, Duration.ofSeconds(2)))::listProjects,
				JiraErrorCode.PROTOCOL);
	}

	@Test
	void retriesARateLimitAfterTheDelayJiraAskedFor() {
		server.onTool("listJiraProjects", arguments -> PROJECTS);
		AtomicInteger calls = new AtomicInteger();
		server.onMethod("tools/call", request -> calls.incrementAndGet() == 1
				? new Response(429, "application/json", "{}", null, "7")
				: Response.sse(MockJiraMcpServer.toolResult(request, PROJECTS, false)));

		assertThat(retrying(3, Duration.ofSeconds(30)).listProjects()).hasSize(2);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(7));
	}

	@Test
	void backsOffExponentiallyWithoutARetryAfterHeader() {
		server.onTool("listJiraProjects", arguments -> PROJECTS);
		AtomicInteger calls = new AtomicInteger();
		server.onMethod("tools/call", request -> calls.incrementAndGet() <= 2
				? Response.sse(MockJiraMcpServer.toolResult(request, "{\"statusCode\":429}", true))
				: Response.sse(MockJiraMcpServer.toolResult(request, PROJECTS, false)));

		assertThat(retrying(3, Duration.ofSeconds(30)).listProjects()).hasSize(2);
		assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
	}

	@Test
	void retriesOutagesAndIgnoresAnHttpDateRetryAfter() {
		AtomicInteger calls = new AtomicInteger();
		server.onMethod("tools/call", request -> calls.incrementAndGet() == 1
				? new Response(503, "application/json", "{}", null, "Wed, 21 Oct 2026 07:28:00 GMT")
				: Response.sse(MockJiraMcpServer.toolResult(request, PROJECTS, false)));

		assertThat(retrying(3, Duration.ofSeconds(30)).listProjects()).hasSize(2);
		assertThat(sleeps).containsExactly(Duration.ofMillis(500));
	}

	@Test
	void givesUpAfterTheConfiguredAttempts() {
		server.onMethod("tools/call", request -> Response.status(429));

		assertFails(retrying(3, Duration.ofSeconds(30))::listProjects, JiraErrorCode.RATE_LIMITED);
		assertThat(sleeps).hasSize(2);
	}

	@Test
	void failsRatherThanWaitLongerThanAllowed() {
		server.onMethod("tools/call", request -> new Response(429, "application/json", "{}", null, "120"));

		assertFails(retrying(3, Duration.ofSeconds(30))::listProjects, JiraErrorCode.RATE_LIMITED);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void doesNotRetryPermanentFailures() {
		server.onMethod("tools/call", request -> Response.status(401));

		assertFails(retrying(3, Duration.ofSeconds(30))::listProjects, JiraErrorCode.UNAUTHORIZED);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void stopsRetryingWhenInterrupted() {
		server.onMethod("tools/call", request -> Response.status(429));
		JiraProperties properties = server.retryProperties(3, Duration.ofSeconds(30));
		McpJiraClient interrupted = new McpJiraClient(
				new JiraMcpTransport(properties, MockJiraMcpServer.JSON, delay -> {
					throw new InterruptedException();
				}), properties);

		assertFails(interrupted::listProjects, JiraErrorCode.UNAVAILABLE);
		assertThat(Thread.interrupted()).isTrue();
	}

	@Test
	void reportsTimeouts() {
		server.onMethod("initialize", request -> {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			return Response.status(200);
		});

		assertFails(MockJiraMcpServer.client(server.properties(1024, Duration.ofMillis(100)))::listProjects,
				JiraErrorCode.TIMEOUT);
	}

	@Test
	void reportsAnUnreachableServer() {
		JiraProperties properties = server.properties();
		server.close();

		assertFails(MockJiraMcpServer.client(properties)::listProjects, JiraErrorCode.UNAVAILABLE);
	}

	private McpJiraClient retrying(int attempts, Duration maxBackoff) {
		JiraProperties properties = server.retryProperties(attempts, maxBackoff);
		return new McpJiraClient(new JiraMcpTransport(properties, MockJiraMcpServer.JSON, sleeps::add), properties);
	}

	private static String comments(int startAt, boolean last) {
		return "{\"data\":{\"isLast\":" + last + ",\"comments\":[{\"id\":\"c" + startAt
				+ "\",\"author\":{\"displayName\":\"A\"},\"body\":\"b\",\"created\":\"2026-11-05T17:38:00.498-0500\"}]}}";
	}

	private static void assertFails(ThrowingCallable call, JiraErrorCode code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(JiraException.class,
				exception -> assertThat(exception.code()).isEqualTo(code));
	}

}
