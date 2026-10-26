package com.capstone.knowledgebridge.usage;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usage")
public class UsageController {

	private final UsageService usage;

	public UsageController(UsageService usage) {
		this.usage = usage;
	}

	@GetMapping
	public UsageService.Totals totals(@AuthenticationPrincipal Jwt jwt) {
		return usage.totals(Long.parseLong(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")));
	}
}
