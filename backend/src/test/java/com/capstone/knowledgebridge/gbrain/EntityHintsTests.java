package com.capstone.knowledgebridge.gbrain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.gbrain.model.GbrainEntity;

class EntityHintsTests {

	@Test
	void classifiesNativeNamesAndSupplementsOnlyExplicitProjectAndTeamFields() {
		assertThat(EntityHints.type("Project Cedar", "people/project-cedar")).isEqualTo("PROJECT");
		assertThat(EntityHints.type("Atlas Team", "people/atlas-team")).isEqualTo("TEAM");
		assertThat(EntityHints.type("Finance Department", "people/finance-department")).isEqualTo("TEAM");
		assertThat(EntityHints.type("Nimbus Labs", "companies/nimbus-labs")).isEqualTo("COMPANY");
		assertThat(EntityHints.type("Alice Example", "people/alice-example")).isEqualTo("PERSON");
		var entities = new LinkedHashMap<String, GbrainEntity>();
		entities.put("person", new GbrainEntity("person", "Alice Example", "PERSON", ""));
		EntityHints.supplement(
				"projects: [\"Cedar\", \"Project Aurora\", \"\"]\ndepartment: \"IT Operations\"\nteam: Atlas Team\nproject: cedar\nteam: invalid<script>\nowner: Alice Example",
				entities);
		assertThat(entities.values()).extracting(GbrainEntity::name).containsExactly("Alice Example", "Project Cedar",
				"Project Aurora", "IT Operations", "Atlas Team");
		assertThat(EntityHints.id("PROJECT", "Project Cedar")).isEqualTo(EntityHints.id("PROJECT", "project cedar"));
	}
}
