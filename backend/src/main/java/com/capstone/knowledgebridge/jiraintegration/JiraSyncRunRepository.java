package com.capstone.knowledgebridge.jiraintegration;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JiraSyncRunRepository extends JpaRepository<JiraSyncRun, Long> {

	List<JiraSyncRun> findTop50ByOrderByStartedAtDescIdDesc();

	List<JiraSyncRun> findByStatus(JiraSyncRunStatus status);

}
