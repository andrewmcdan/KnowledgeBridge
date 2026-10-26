package com.capstone.knowledgebridge.common;

/** The error envelope shared by MVC handlers and the security filter chain. */
public record ApiError(String code, String message) {

	public static ApiError forStatus(int status) {
		return switch (status) {
			case 400 -> new ApiError("VALIDATION_ERROR", "The request is invalid.");
			case 401 -> new ApiError("UNAUTHENTICATED", "Sign in with valid credentials to continue.");
			case 403 -> new ApiError("FORBIDDEN", "You do not have permission to perform this operation.");
			case 404 -> new ApiError("NOT_FOUND", "The requested resource was not found.");
			case 405 -> new ApiError("METHOD_NOT_ALLOWED", "This HTTP method is not supported for this resource.");
			case 409 -> new ApiError("CONFLICT", "The resource is not in a state that allows this operation.");
			case 413 -> new ApiError("CONTENT_TOO_LARGE", "The submitted content exceeds the allowed size.");
			case 415 ->
				new ApiError("UNSUPPORTED_MEDIA_TYPE", "The submitted content type or file format is not supported.");
			case 500 -> new ApiError("INTERNAL_ERROR", "The request could not be completed. Please try again.");
			default -> new ApiError("REQUEST_FAILED", "The request could not be completed.");
		};
	}
}
