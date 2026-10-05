package com.capstone.knowledgebridge.gbrain;

import tools.jackson.databind.JsonNode;

/**
 * A successful tool result: the JSON payload gbrain encodes in the first text content block, plus the {@code _meta}
 * object that proves how retrieval ran. Package-private so gbrain response shapes stay inside the adapter.
 */
record GbrainToolResult(JsonNode payload, JsonNode metadata) {
}
