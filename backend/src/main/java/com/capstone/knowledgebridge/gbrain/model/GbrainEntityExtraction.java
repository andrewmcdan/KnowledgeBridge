package com.capstone.knowledgebridge.gbrain.model;

import java.util.List;

public record GbrainEntityExtraction(List<GbrainEntity> entities, boolean truncated) {
}
