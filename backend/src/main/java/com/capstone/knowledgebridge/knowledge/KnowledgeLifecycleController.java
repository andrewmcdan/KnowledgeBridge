package com.capstone.knowledgebridge.knowledge;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.common.ApiError;

@RestController
@RequestMapping("/api/knowledge-items")
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeLifecycleController {
	private final KnowledgeLifecycleService lifecycle;

	public KnowledgeLifecycleController(KnowledgeLifecycleService lifecycle) {
		this.lifecycle = lifecycle;
	}

	@PostMapping("/{id}/deactivate")
	public ResponseEntity<Void> deactivate(@PathVariable long id) {
		lifecycle.change(id, KnowledgeLifecycleService.Action.DEACTIVATE);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/activate")
	public ResponseEntity<Void> activate(@PathVariable long id) {
		lifecycle.change(id, KnowledgeLifecycleService.Action.ACTIVATE);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> trash(@PathVariable long id) {
		lifecycle.change(id, KnowledgeLifecycleService.Action.TRASH);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/restore")
	public ResponseEntity<Void> restore(@PathVariable long id) {
		lifecycle.change(id, KnowledgeLifecycleService.Action.RESTORE);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}/permanent")
	public ResponseEntity<Void> permanentlyDelete(@PathVariable long id) {
		lifecycle.permanentlyDelete(id);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(GbrainException.class)
	public ResponseEntity<ApiError> engineError(GbrainException exception) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError("LIFECYCLE_ENGINE_UNAVAILABLE",
				"The knowledge service could not confirm this change. Refresh the list before retrying."));
	}
}
