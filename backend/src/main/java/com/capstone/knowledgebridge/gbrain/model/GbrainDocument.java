package com.capstone.knowledgebridge.gbrain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;

/**
 * A complete knowledge item as the adapter indexes it. gbrain replaces the whole page on every write, so every field is
 * required. The body is normalized deterministically so retries send identical content, and {@link #contentDigest()}
 * identifies exactly what was sent for later reconciliation.
 *
 * @param itemKey
 *            immutable application identity (for example the knowledge-item UUID); never the editable title
 * @param title
 *            display title
 * @param documentType
 *            lower-case document type such as {@code policy} or {@code note}
 * @param ownerId
 *            identifier of the owning user
 * @param revision
 *            application revision of this item, incremented on every content change
 * @param createdAt
 *            original creation time
 * @param updatedAt
 *            last update time
 * @param body
 *            Markdown body; any frontmatter in it is treated as body text, not engine metadata
 */
public record GbrainDocument(String itemKey, String title, String documentType, String ownerId, long revision,
		Instant createdAt, Instant updatedAt, String body) {

	/** gbrain refuses pages over 5 MB; the adapter keeps well below that and the response-size limit. */
	public static final int MAX_BODY_BYTES = 1024 * 1024;

	public static final int MAX_TITLE_LENGTH = 255;

	/** Written as code points so editors cannot silently strip or normalize the characters. */
	private static final int LINE_SEPARATOR = 0x2028;

	private static final int PARAGRAPH_SEPARATOR = 0x2029;

	private static final String BYTE_ORDER_MARK = Character.toString(0xFEFF);

	private static final Pattern ITEM_KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

	private static final Pattern DOCUMENT_TYPE = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

	/**
	 * Changing the digest inputs or how a document is rendered for gbrain requires bumping this, so a page written in
	 * an older format never reconciles as current. Version 2 namespaced the gbrain page type.
	 */
	private static final String DIGEST_VERSION = "2";

	public GbrainDocument {
		requireItemKey(itemKey);
		requireText("title", title, MAX_TITLE_LENGTH);
		documentType = requireDocumentType(documentType);
		requireText("owner id", ownerId, MAX_TITLE_LENGTH);
		if (revision < 0) {
			throw invalid("revision must not be negative");
		}
		if (createdAt == null || updatedAt == null) {
			throw invalid("creation and update times are required");
		}
		body = normalizeBody(body);
	}

	/** Validates an item key, the only part of a gbrain slug that comes from the application. */
	public static String requireItemKey(String itemKey) {
		if (!isItemKey(itemKey)) {
			throw invalid("item key must be 1-64 lower-case letters, digits, or hyphens");
		}
		return itemKey;
	}

	public static boolean isItemKey(String itemKey) {
		return itemKey != null && ITEM_KEY.matcher(itemKey).matches();
	}

	/** Lower-cases and validates a document type, which also appears in search type filters. */
	public static String requireDocumentType(String documentType) {
		String normalized = documentType == null ? null : documentType.toLowerCase(Locale.ROOT);
		if (normalized == null || !DOCUMENT_TYPE.matcher(normalized).matches()) {
			throw invalid("document type must be 1-64 letters, digits, underscores, or hyphens");
		}
		return normalized;
	}

	/** SHA-256 over every indexed field, stored with the page so a later read can prove which version gbrain holds. */
	public String contentDigest() {
		String canonical = String.join("\u0000", DIGEST_VERSION, itemKey, title, documentType, ownerId,
				Long.toString(revision), createdAt.toString(), updatedAt.toString(), body);
		return hexDigest("SHA-256", canonical);
	}

	static String hexDigest(String algorithm, String value) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(algorithm + " is not available", exception);
		}
	}

	/**
	 * Converts line endings to LF, drops a leading byte-order mark, and ends the body with exactly one newline.
	 * Markdown content is otherwise left untouched.
	 */
	private static String normalizeBody(String body) {
		if (body == null) {
			throw invalid("body is required");
		}
		String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
		if (normalized.startsWith(BYTE_ORDER_MARK)) {
			normalized = normalized.substring(1);
		}
		normalized = normalized.stripTrailing();
		if (normalized.isBlank()) {
			throw invalid("body must not be empty");
		}
		if (normalized.indexOf('\u0000') >= 0) {
			throw invalid("body must not contain NUL characters");
		}
		normalized = normalized + "\n";
		if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
			throw invalid("body exceeds " + MAX_BODY_BYTES + " bytes");
		}
		return normalized;
	}

	/** Frontmatter values are single-line quoted strings, so control and line-separator characters are rejected. */
	private static void requireText(String name, String value, int maxLength) {
		if (value == null || value.isBlank() || value.length() > maxLength) {
			throw invalid(name + " must be 1-" + maxLength + " characters");
		}
		if (value.codePoints().anyMatch(GbrainDocument::isUnsafe)) {
			throw invalid(name + " must not contain control or line-separator characters");
		}
	}

	private static boolean isUnsafe(int codePoint) {
		return Character.isISOControl(codePoint) || codePoint == LINE_SEPARATOR || codePoint == PARAGRAPH_SEPARATOR
				|| codePoint == BYTE_ORDER_MARK.codePointAt(0);
	}

	private static GbrainException invalid(String message) {
		return new GbrainException(GbrainErrorCode.VALIDATION, "gbrain document " + message);
	}

}
