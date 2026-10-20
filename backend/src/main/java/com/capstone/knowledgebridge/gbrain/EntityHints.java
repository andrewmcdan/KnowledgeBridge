package com.capstone.knowledgebridge.gbrain;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.capstone.knowledgebridge.gbrain.model.GbrainEntity;

/** Conservative document-declared project/team hints supplement gbrain's person/company extractor. */
final class EntityHints {

	private static final Pattern FIELD = Pattern.compile("(?im)^(department|team|projects?):[ \\t]*(.+)$");

	private EntityHints() {
	}

	static String id(String type, String name) {
		return "declared/"
				+ UUID.nameUUIDFromBytes((type + ":" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
	}

	static String type(String name, String slug) {
		if (name.startsWith("Project ")) {
			return "PROJECT";
		}
		if (name.endsWith(" Team") || name.endsWith(" Department")) {
			return "TEAM";
		}
		return slug.startsWith("people/") ? "PERSON" : "COMPANY";
	}

	static void supplement(String body, Map<String, GbrainEntity> entities) {
		var fields = FIELD.matcher(body);
		while (fields.find()) {
			String type = fields.group(1).toLowerCase(Locale.ROOT).startsWith("project") ? "PROJECT" : "TEAM";
			for (String value : fields.group(2).replace("[", "").replace("]", "").split(",")) {
				String name = value.strip().replaceAll("^[\"']|[\"']$", "").strip();
				if (!name.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} &'()._-]{1,99}")) {
					continue;
				}
				if (type.equals("PROJECT") && !name.startsWith("Project ")) {
					name = "Project " + name;
				}
				String entityName = name;
				boolean alreadyDetected = entities.values().stream()
						.anyMatch(entity -> entity.type().equals(type) && entity.name().equalsIgnoreCase(entityName));
				if (!alreadyDetected) {
					String id = id(type, name);
					entities.put(id, new GbrainEntity(id, name, type, "Declared in the document's "
							+ fields.group(1).toLowerCase(Locale.ROOT) + " field. Not independently verified."));
				}
			}
		}
	}
}
