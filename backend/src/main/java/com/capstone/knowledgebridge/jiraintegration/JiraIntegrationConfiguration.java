package com.capstone.knowledgebridge.jiraintegration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class JiraIntegrationConfiguration {

	/** One thread per run is bounded by the rule of one active sync per project, and can't delay video ingestion. */
	@Bean("jiraSyncExecutor")
	TaskExecutor jiraSyncExecutor() {
		return new SimpleAsyncTaskExecutor("jira-sync-");
	}

}
