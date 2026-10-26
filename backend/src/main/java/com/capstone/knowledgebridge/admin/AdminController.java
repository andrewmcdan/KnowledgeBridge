package com.capstone.knowledgebridge.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.knowledgebridge.cache.QueryCacheService;
import com.capstone.knowledgebridge.cache.QueryCacheService.CacheEntry;
import com.capstone.knowledgebridge.users.UserAdministrationService;
import com.capstone.knowledgebridge.users.UserAdministrationService.ManagedUser;

/**
 * Admin-only management routes. SecurityConfiguration already restricts /api/admin/** to ROLE_ADMIN; the class-level
 * PreAuthorize keeps that true even if the URL rule changes. A thin layer: each route delegates to its owning service.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

	public record CacheClearResponse(int deleted) {
	}

	private final QueryCacheService queryCacheService;
	private final UserAdministrationService users;

	public AdminController(QueryCacheService queryCacheService, UserAdministrationService users) {
		this.queryCacheService = queryCacheService;
		this.users = users;
	}

	@GetMapping("/users")
	public List<ManagedUser> listUsers() {
		return users.list();
	}

	@PostMapping("/users/{id}/promote")
	public ManagedUser promoteUser(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
		return users.promote(id, Long.parseLong(jwt.getSubject()));
	}

	/** Lists exact-match cache entries with hit counts and whether each is still current, for demoing the cache. */
	@GetMapping("/cache")
	public List<CacheEntry> listCache() {
		return queryCacheService.list();
	}

	/** Clears the exact-match response cache. */
	@DeleteMapping("/cache")
	public CacheClearResponse clearCache() {
		return new CacheClearResponse(queryCacheService.clear());
	}

}
