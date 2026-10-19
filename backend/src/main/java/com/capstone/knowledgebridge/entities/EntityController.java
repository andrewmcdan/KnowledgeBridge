package com.capstone.knowledgebridge.entities;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.capstone.knowledgebridge.entities.EntityService.DocumentEntities;
import com.capstone.knowledgebridge.entities.EntityService.EntitySummary;
import com.capstone.knowledgebridge.entities.EntityService.RelatedDocument;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemService;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus;

@RestController
public class EntityController {

	private final EntityService entities;

	private final KnowledgeItemService items;

	public EntityController(EntityService entities, KnowledgeItemService items) {
		this.entities = entities;
		this.items = items;
	}

	@GetMapping("/api/entities")
	public List<EntitySummary> list(@RequestParam(defaultValue = "") String name,
			@RequestParam(defaultValue = "") String type, @RequestParam(defaultValue = "25") int limit) {
		String normalized = type.toUpperCase(Locale.ROOT);
		if (name.length() > 255 || !Set.of("", "PERSON", "COMPANY", "PROJECT", "TEAM").contains(normalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid entity filter");
		}
		return entities.list(name.strip(), normalized, limit(limit));
	}

	@GetMapping("/api/entities/{id}")
	public ResponseEntity<EntitySummary> get(@PathVariable long id) {
		return ResponseEntity.of(entities.find(id));
	}

	@GetMapping("/api/entities/{id}/documents")
	public List<RelatedDocument> documents(@PathVariable long id, @RequestParam(defaultValue = "25") int limit) {
		if (entities.find(id).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return entities.documents(id, limit(limit));
	}

	@GetMapping("/api/knowledge-items/{id}/entities")
	public DocumentEntities forDocument(@PathVariable long id, @RequestParam(defaultValue = "10") int limit) {
		return entities.forDocument(item(id), limit(limit));
	}

	@PostMapping("/api/knowledge-items/{id}/extract-entities")
	@PreAuthorize("hasRole('ADMIN')")
	public DocumentEntities extract(@PathVariable long id) {
		KnowledgeItem item = item(id);
		if (item.getStatus() != KnowledgeItemStatus.COMPLETED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Index the document before extracting entities");
		}
		entities.extract(item);
		return entities.forDocument(item, 100);
	}

	private KnowledgeItem item(long id) {
		return items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static int limit(int limit) {
		if (limit < 1 || limit > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100");
		}
		return limit;
	}
}
