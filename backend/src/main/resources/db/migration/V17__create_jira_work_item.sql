-- Structured Jira fields for the Jira View, one row per synced knowledge item. The page text stays on knowledge_item.
CREATE TABLE jira_work_item (
    knowledge_item_id BIGINT PRIMARY KEY REFERENCES knowledge_item(id) ON DELETE CASCADE,
    issue_key VARCHAR(64) NOT NULL UNIQUE,
    summary TEXT NOT NULL,
    project_key VARCHAR(64),
    project_name TEXT,
    issue_type TEXT,
    status TEXT,
    priority TEXT,
    assignee TEXT,
    reporter TEXT,
    parent_key VARCHAR(64),
    parent_summary TEXT,
    jira_created_at TIMESTAMPTZ NOT NULL,
    jira_updated_at TIMESTAMPTZ NOT NULL,
    browse_url TEXT NOT NULL
);
