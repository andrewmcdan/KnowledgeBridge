package com.capstone.knowledgebridge.gbrain.model;

/** An unverified detection from gbrain, with its source document's evidence. */
public record GbrainEntity(String externalId, String name, String type, String summary) {
}
