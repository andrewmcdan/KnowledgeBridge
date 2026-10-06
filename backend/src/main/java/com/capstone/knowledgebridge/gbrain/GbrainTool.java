package com.capstone.knowledgebridge.gbrain;

/**
 * The gbrain tools the adapter depends on. Keeping names and call policy here prevents tool strings and retry decisions
 * from spreading into service code.
 */
enum GbrainTool {

	WHOAMI("whoami", true, false), PUT_PAGE("put_page", false, false), GET_PAGE("get_page", true,
			false), SEARCH("search", true, false),
	// Side-effect free, but an expensive LLM call: never repeat it automatically.
	SYNTHESIZE("synthesize", false, true), DELETE_PAGE("delete_page", false, false), RESTORE_PAGE("restore_page", false,
			false);

	private final String toolName;

	private final boolean safeToRetry;

	private final boolean longRunning;

	GbrainTool(String toolName, boolean safeToRetry, boolean longRunning) {
		this.toolName = toolName;
		this.safeToRetry = safeToRetry;
		this.longRunning = longRunning;
	}

	String toolName() {
		return toolName;
	}

	/** Whether transient failures may be retried without risking a duplicate side effect or cost. */
	boolean safeToRetry() {
		return safeToRetry;
	}

	/** Whether the call uses the larger synthesis timeout. */
	boolean longRunning() {
		return longRunning;
	}

}
