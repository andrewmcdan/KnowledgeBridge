package com.capstone.knowledgebridge.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.knowledgebridge.cache.QueryCacheService;
import com.capstone.knowledgebridge.cache.QueryCacheService.CacheEntry;

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

	public AdminController(QueryCacheService queryCacheService) {
		this.queryCacheService = queryCacheService;
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
