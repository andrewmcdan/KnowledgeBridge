package com.capstone.knowledgebridge.extraction;

/**
 * An uploaded file can't be ingested. The reason carries the error code and the message shown to the user, so every
 * rejection explains what went wrong and what to do about it.
 */
public class ExtractionException extends RuntimeException {

	public enum Reason {

		UNREADABLE("FILE_UNREADABLE",
				"The file could not be read. It may be damaged, password-protected, or not the type its name suggests."),

		PASSWORD_PROTECTED("FILE_PASSWORD_PROTECTED",
				"The file is password-protected. Remove the password and upload it again."),

		NO_TEXT("FILE_HAS_NO_TEXT", "The file has no text to ingest."),

		PDF_NO_SELECTABLE_TEXT("PDF_NO_SELECTABLE_TEXT",
				"This PDF has no selectable text, so it is probably scanned or image-based. Run OCR on it (for example"
						+ " with Adobe Acrobat or OCRmyPDF) and upload it again."),

		PDF_MOSTLY_SCANNED("PDF_MOSTLY_SCANNED",
				"Most pages of this PDF are scanned images without selectable text. Run OCR on it (for example with"
						+ " Adobe Acrobat or OCRmyPDF) and upload it again.");

		private final String code;

		private final String message;

		Reason(String code, String message) {
			this.code = code;
			this.message = message;
		}

		public String code() {
			return code;
		}

		public String message() {
			return message;
		}

	}

	private final Reason reason;

	public ExtractionException(Reason reason) {
		this(reason, null);
	}

	public ExtractionException(Reason reason, Throwable cause) {
		super(reason.message(), cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
