package com.capstone.knowledgebridge.gbrain.model;

import java.util.Set;

/**
 * Server identity and the tool surface visible to the backend's credential. The surface is filtered by token scope and
 * server configuration, so it must be rediscovered after a gbrain upgrade or credential change.
 */
public record GbrainCapabilities(String serverName, String serverVersion, String protocolVersion, Set<String> tools,
		Set<String> missingRequiredTools) {

	public GbrainCapabilities {
		tools = Set.copyOf(tools);
		missingRequiredTools = Set.copyOf(missingRequiredTools);
	}

	/** True when every tool the adapter depends on is available. */
	public boolean ready() {
		return missingRequiredTools.isEmpty();
	}

}
