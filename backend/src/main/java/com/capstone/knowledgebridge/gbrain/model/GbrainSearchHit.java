package com.capstone.knowledgebridge.gbrain.model;

/**
 * One ranked chunk from a knowledge item. A long item can contribute several hits.
 *
 * @param itemKey
 *            application identity of the matching item
 * @param externalId
 *            gbrain page slug
 * @param title
 *            page title
 * @param documentType
 *            application document type
 * @param snippet
 *            the matching chunk's text
 * @param score
 *            gbrain's fused relevance score; comparable only within one result
 */
public record GbrainSearchHit(String itemKey, String externalId, String title, String documentType, String snippet,
		double score) {
}
