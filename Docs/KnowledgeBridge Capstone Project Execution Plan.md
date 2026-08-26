# KnowledgeBridge Capstone Project Execution Plan

## 1\. Project Overview

KnowledgeBridge is a one-semester senior capstone project for a team of three to five undergraduate computing students. The project combines applied artificial intelligence, enterprise knowledge retrieval, knowledge management, and full-stack software development. Our five-person team will design and implement a web-based platform that allows an organization to ingest internal business information, search that information, and ask natural-language questions that produce synthesized answers with citations.

The central problem being addressed is that organizational knowledge is often fragmented across files, email, meeting notes, shared drives, and individual employees. Traditional search tools may locate documents containing matching keywords, but they do not usually synthesize a direct answer. General-purpose large language models also do not automatically know the policies, processes, decisions, projects, and organizational structure of a specific business. KnowledgeBridge is intended to bridge that gap by creating a business-specific AI knowledge layer.

The required system will:

* Accept manually entered business knowledge.

* Accept at least one text-based document format.

* Process and index submitted content through the gbrain knowledge engine.

* Provide keyword and semantic hybrid search.

* Provide AI-generated answers based on the indexed business knowledge.

* Include citations that identify the source documents used.

* Recognize when it does not have enough information to answer confidently.

* Display detected entities and relationships between entities and documents.

* Provide basic administrative knowledge management.

* Track estimated AI usage and cost.

* Support basic admin and user roles.

* Support a systematic evaluation of AI reliability and limitations.

The project is both a software development effort and an applied AI feasibility study. The final result must demonstrate not only that the platform works, but also where its AI-assisted knowledge retrieval is reliable, where it fails, and where human review remains necessary.

---

## 2\. Recommended Project Decisions

The sponsor brief leaves several implementation choices to the team. The following decisions are recommended to keep the project achievable within one semester.

### 2.1 MVP Document Format

The team should select Markdown, .md, as the official MVP document format.

Markdown is recommended because:

* It is significantly easier to parse than PDF or DOCX.

* Headings provide natural boundaries for document chunking.

* It supports realistic business content such as procedures, meeting notes, project summaries, and policies.

* It can be generated easily for the synthetic company dataset.

* It avoids spending excessive time on file-format edge cases.

* It can be previewed in the React interface if time permits.

Plain text, .txt, could be added later with minimal additional work, but it should not be included in the initial commitment. PDF parsing is explicitly identified as a stretch feature because of its potential to delay core development.

### 2.2 Primary Stretch Goal

The team’s official stretch goal should be the **advanced cost dashboard**.

This is the safest stretch goal because the MVP already requires query tracking, ingestion tracking, and estimated cost calculations. The stretch dashboard can build on data that the system must already collect.

The dashboard could show:

* Queries per day or week.

* Search requests versus synthesis requests.

* Cache hits versus new LLM requests.

* Estimated ingestion cost.

* Estimated synthesis cost.

* Average estimated cost per query.

* Highest-cost query categories.

* Projected monthly cost for 50, 75, and 100 users.

A basic knowledge graph visualization should be treated as a secondary stretch candidate. The team should only attempt it if the gbrain entity and relationship APIs are proven stable during Milestone 1\. The sponsor expects the team to aim for at least one stretch feature, not all available stretch features.

### 2.3 Deployment Goal

The semester goal should be a stable proof-of-concept deployment, not a production enterprise platform.

The deployment should consist of:

* React frontend.

* Spring Boot backend.

* PostgreSQL with pgvector.

* gbrain knowledge engine.

* Docker Compose orchestration.

* External LLM and embedding provider configuration.

* A documented local startup process.

* Optionally, one shared cloud or university-hosted demonstration environment.

The project should not attempt enterprise identity integration, native mobile applications, real-time collaboration, regulatory certification, third-party SaaS integrations, or production-grade fine-grained permissions. These items are explicitly outside the required scope.

### 2.4 Architecture Boundary

The team should treat gbrain as an external service and should not modify its internal code.

All gbrain communication should pass through a dedicated Spring Boot adapter or client module. This isolates the rest of the application from changes to the gbrain API and allows the team to use mock responses while the external service is unavailable.

The sponsor brief specifically states that students are expected to integrate gbrain through a limited HTTP API rather than modify the knowledge engine itself. The sponsor will provide a preconfigured Docker Compose environment.

---

## 3\. Project Success Criteria

The project will be considered successful when the team can demonstrate the following complete workflow:

1. An administrator logs in.

2. The administrator creates a manual knowledge note or uploads a Markdown document.

3. The knowledge item appears with a pending or processing status.

4. The Spring Boot backend extracts and validates the content.

5. The backend submits the content to gbrain.

6. The item status changes to completed.

7. A normal user logs in.

8. The user enters a question in Search mode.

9. The system returns a ranked list of relevant documents without requesting an LLM-generated answer.

10. The user enters a question in Synthesis mode.

11. The system returns a synthesized answer with citations.

12. The user can open or identify the documents cited by the answer.

13. Repeating the exact same question demonstrates the PostgreSQL response cache.

14. The user can browse detected entities and related documents.

15. A question unsupported by the knowledge base produces a clear knowledge-gap warning.

16. The administrator can view all knowledge items and their statuses.

17. The administrator can soft-delete and permanently delete an item.

18. Permanent deletion removes the associated indexed or embedded content.

19. The administrator can view estimated query and ingestion usage.

20. The team can show documented examples of successful answers, failed answers, hallucinations, contradictory-source behavior, and appropriate gap detection.

The sponsor’s required Milestone 2 outcome is an end-to-end ingestion-to-answer workflow with citations, stable core behavior, basic error handling, and the complete synthetic dataset available for testing.

---

# 4\. Proposed Technical Architecture

## 4.1 Major Components

### React Frontend

The React application will provide:

* Login interface.

* User Q\&A interface.

* Search and Synthesis mode selection.

* Answer citation display.

* Knowledge-gap messaging.

* Entity list and entity detail views.

* Admin knowledge management interface.

* Manual knowledge entry.

* Markdown upload.

* Ingestion status display.

* Soft-delete and permanent-delete controls.

* Estimated usage and cost summary.

* Loading, failure, and retry states.

### Spring Boot Backend

The Spring Boot application will provide:

* Authentication.

* Admin and user authorization.

* REST API endpoints.

* Knowledge item metadata management.

* File validation and text extraction.

* Ingestion workflow coordination.

* gbrain HTTP integration.

* Search and synthesis request coordination.

* Exact-match response caching.

* Usage event recording.

* Estimated token and cost calculations.

* Soft-delete and permanent-delete coordination.

* Logging and error handling.

### PostgreSQL and pgvector

PostgreSQL will store the application’s structured data, including:

* Users and roles.

* Knowledge item metadata.

* Ingestion processing records.

* Query history.

* Response cache entries.

* Estimated usage events.

* System configuration.

* Knowledge-base revision information.

The sponsor-provided gbrain configuration may also use PostgreSQL and pgvector for vector indexing. The team should confirm during Milestone 1 whether gbrain shares the same PostgreSQL instance, uses a separate database, or manages its storage independently.

### gbrain Knowledge Engine

gbrain will be responsible for the knowledge retrieval layer, including:

* Content chunking and indexing.

* Embedding generation.

* Vector similarity search.

* BM25 keyword search.

* Reciprocal-rank fusion.

* Answer synthesis.

* Source citation generation.

* Entity detection.

* Entity-to-document relationships.

* Knowledge-gap or insufficient-evidence signals.

The proposed stack calls for hybrid search using pgvector HNSW, BM25 keyword retrieval, and reciprocal-rank fusion.

### External AI Providers

The project will require:

* An LLM provider for answer synthesis.

* An embedding provider for semantic indexing and retrieval.

Provider access should be configured through environment variables. API keys must never be committed to GitHub.

---

## 4.2 Recommended Request Flow

### Knowledge Ingestion Flow

1. The admin submits manual text or uploads a Markdown document.

2. Spring Boot validates the title, file type, file size, and content.

3. Spring Boot creates a knowledge\_item record.

4. The item’s status is set to pending.

5. An ingestion operation begins.

6. The item’s status is set to processing.

7. The backend extracts normalized text and metadata.

8. The gbrain adapter submits the content to the knowledge engine.

9. gbrain chunks, embeds, and indexes the content.

10. Spring Boot records the external gbrain identifier.

11. The item’s status becomes completed.

12. If any operation fails, the item’s status becomes failed, and a safe error message is stored for administrator review.

The MVP requires visible pending, processing, completed, and failed states.

### Query Flow

1. The user enters a natural-language question.

2. The user selects Search mode or Synthesis mode.

3. Spring Boot validates the request.

4. For Synthesis mode, the backend checks the exact-match cache.

5. If a valid cached response exists, it is returned without another LLM call.

6. If there is no cached response, the gbrain adapter submits the query.

7. In Search mode, gbrain returns ranked source documents.

8. In Synthesis mode, gbrain returns an answer, citations, and available evidence signals.

9. Spring Boot records estimated usage information.

10. The response is returned to React.

11. React displays the answer, citations, AI disclosure, and any knowledge-gap message.

The two required modes are Search mode, which returns relevant documents without LLM synthesis cost, and Synthesis mode, which returns a coherent AI-generated response.

---

## 4.3 Recommended Backend Modules

The Spring Boot codebase should be divided into modules or packages with clear responsibilities:

auth  
users  
knowledge  
ingestion  
gbrain  
query  
cache  
entities  
usage  
admin  
common  
configuration

Suggested responsibilities:

* auth: Login, logout, session or token validation, authorization.

* users: User records and roles.

* knowledge: Knowledge item CRUD and metadata.

* ingestion: File validation, text extraction, and status management.

* gbrain: All gbrain HTTP request and response handling.

* query: Search and synthesis orchestration.

* cache: Exact-match response storage and invalidation.

* entities: Entity listing and related-document endpoints.

* usage: Query counts, character counts, token estimates, and cost estimates.

* admin: Administrative summaries and management operations.

* common: Shared exceptions, response types, and utility classes.

* configuration: Environment configuration, HTTP clients, security, and CORS settings.

No React component or unrelated Spring service should call gbrain directly. All knowledge-engine calls should go through the gbrain adapter.

---

## 4.4 Suggested Database Tables

### app\_user

id  
email  
password\_hash  
display\_name  
role  
enabled  
created\_at  
updated\_at

The MVP role field only needs:

* ADMIN

* USER

### knowledge\_item

id  
title  
item\_type  
original\_filename  
content\_type  
owner\_user\_id  
status  
external\_engine\_id  
character\_count  
created\_at  
updated\_at  
soft\_deleted\_at

Suggested item types:

* MANUAL\_NOTE

* MARKDOWN\_DOCUMENT

Suggested statuses:

* PENDING

* PROCESSING

* COMPLETED

* FAILED

### ingestion\_attempt

id  
knowledge\_item\_id  
attempt\_number  
status  
started\_at  
completed\_at  
error\_code  
safe\_error\_message  
input\_character\_count

### query\_event

id  
user\_id  
query\_text  
query\_mode  
cache\_hit  
input\_character\_count  
output\_character\_count  
estimated\_input\_tokens  
estimated\_output\_tokens  
estimated\_cost  
success  
created\_at

### query\_cache

id  
exact\_query\_text  
query\_mode  
knowledge\_revision  
response\_json  
created\_at  
last\_accessed\_at  
hit\_count

### system\_state

id  
knowledge\_revision  
updated\_at

The knowledge\_revision should increase whenever content is added, edited, soft-deleted, restored, or permanently deleted. Including the revision in the cache key prevents the system from returning an answer based on an outdated version of the knowledge base.

---

## 4.5 Suggested REST API

The exact routes may change, but the team should agree on API contracts during Milestone 1\.

### Authentication

POST /api/auth/login  
POST /api/auth/logout  
GET  /api/auth/me

### Knowledge Management

GET    /api/knowledge-items  
GET    /api/knowledge-items/{id}  
POST   /api/knowledge-items/manual  
PUT    /api/knowledge-items/{id}  
POST   /api/knowledge-items/upload  
DELETE /api/knowledge-items/{id}  
DELETE /api/knowledge-items/{id}/permanent  
POST   /api/knowledge-items/{id}/restore  
POST   /api/knowledge-items/{id}/retry-ingestion

### Query

POST /api/query/search  
POST /api/query/synthesize

### Entities

GET /api/entities  
GET /api/entities/{id}  
GET /api/entities/{id}/documents  
GET /api/knowledge-items/{id}/entities

### Usage and Administration

GET /api/admin/usage/summary  
GET /api/admin/usage/events  
GET /api/admin/knowledge-summary

---

# 5\. Five-Person Team Structure

Because the other four team members’ names and strengths are not yet known, they are identified below as Members 2 through 5\. Assignments should be adjusted after the team compares experience with Java, Spring Boot, React, databases, AI APIs, testing, and technical writing.

Every person should have:

* A primary technical area.

* A secondary area where they serve as backup.

* Required coding responsibilities.

* Required testing responsibilities.

* Required documentation responsibilities.

* At least one feature that can be demonstrated during the final presentation.

No member should be assigned only documentation, presentations, or project management.

---

## 5.1 Andrew: Technical Lead and Platform Integration Engineer

### Primary Responsibilities

Andrew should serve as the technical lead and platform integration engineer.

Primary ownership:

* Overall system architecture.

* Spring Boot project structure.

* Authentication and role enforcement.

* API conventions and shared request/response types.

* Docker Compose validation.

* GitHub repository configuration.

* Continuous integration setup.

* Environment variable and configuration strategy.

* Cross-component integration.

* Exact-match response caching.

* Final release assembly.

* Deployment and startup documentation.

### Milestone 1 Work

Andrew will:

* Create or organize the GitHub repository.

* Configure branch protection and pull request rules.

* Create the initial Spring Boot application structure.

* Validate the sponsor-provided Docker Compose environment.

* Confirm that Spring Boot, PostgreSQL, React, and gbrain can start together.

* Lead the system architecture diagram.

* Define the service boundaries between React, Spring Boot, PostgreSQL, gbrain, and external AI providers.

* Coordinate API contract development.

* Write the main project README and local setup instructions.

* Work with Member 3 to define the gbrain adapter interface.

* Work with Member 2 to define the database schema.

* Work with Member 4 to define frontend/backend request and response contracts.

### Milestone 2 Work

Andrew will:

* Implement Spring Security.

* Implement login and logout behavior.

* Implement ADMIN and USER authorization.

* Ensure that authorization is enforced in backend endpoints rather than only hidden in the frontend.

* Implement the exact-match query cache.

* Implement cache invalidation using a knowledge revision.

* Integrate backend modules into the complete application.

* Resolve cross-component integration defects.

* Maintain shared error-response conventions.

* Ensure that configuration secrets are not committed.

* Coordinate the Milestone 2 end-to-end demonstration.

### Milestone 3 Work

Andrew will:

* Coordinate code freeze and release preparation.

* Verify Docker-based startup on multiple team computers.

* Lead final integration testing.

* Coordinate deployment instructions.

* Organize the final live demonstration sequence.

* Review the final feasibility report for technical consistency.

* Ensure all required artifacts are present in the final GitHub repository.

* Lead final presentation integration, while each member presents their own technical work.

### Secondary Responsibilities

Andrew should serve as backup for:

* Member 2’s Spring Boot ingestion and database work.

* Member 3’s knowledge-engine adapter.

* Member 5’s automated backend integration tests.

### Reason for This Assignment

This role benefits from broad software engineering experience and requires someone who can understand all major components without personally implementing every feature. The primary goal is to prevent the frontend, backend, knowledge engine, and evaluation work from becoming disconnected subprojects.

---

## 5.2 Member 2: Knowledge Ingestion and Data Engineer

### Primary Responsibilities

Member 2 will own:

* PostgreSQL application schema.

* JPA entities and repositories.

* Database migrations.

* Knowledge item CRUD.

* Manual note creation and editing.

* Markdown upload and text extraction.

* Ingestion status state machine.

* Ingestion retry behavior.

* Knowledge item metadata.

* Soft deletion.

* Permanent deletion.

* Synchronization between application records and gbrain records.

### Milestone 1 Work

Member 2 will:

* Design the initial relational schema.

* Create database migration scripts.

* Define the knowledge-item lifecycle.

* Define allowed status transitions.

* Research and select a Markdown parsing approach.

* Define file size and content validation rules.

* Help document the ingestion sequence.

* Create sample database records for local development.

* Work with Member 3 to define what content and metadata gbrain needs.

### Milestone 2 Work

Member 2 will:

* Implement manual note creation.

* Implement manual note editing.

* Implement Markdown upload.

* Normalize uploaded document text.

* Create and update ingestion status records.

* Invoke Member 3’s gbrain adapter.

* Save the external knowledge-engine identifier.

* Handle ingestion failures.

* Implement retry behavior.

* Implement the admin knowledge item list backend.

* Implement soft deletion.

* Implement permanent deletion.

* Ensure permanent deletion requests removal of associated indexed content.

* Increment the knowledge revision after content changes.

* Support ingestion of all 50 synthetic documents.

### Milestone 3 Work

Member 2 will:

* Improve ingestion error handling.

* Test malformed and empty documents.

* Test duplicate filenames and duplicate content.

* Test deletion consistency.

* Support performance and reliability testing.

* Document known ingestion limitations.

* Help prepare the final data model documentation.

### Secondary Responsibilities

Member 2 should serve as backup for:

* Andrew’s Spring Boot architecture and configuration.

* Member 3’s ingestion-related gbrain calls.

* Member 5’s cost event persistence.

---

## 5.3 Member 3: AI Retrieval and Knowledge Engine Engineer

### Primary Responsibilities

Member 3 will own:

* gbrain API investigation.

* gbrain HTTP adapter.

* Search mode.

* Synthesis mode.

* Citation parsing and normalization.

* Entity retrieval.

* Entity-to-document linking.

* Knowledge-gap signal interpretation.

* Prompt and query strategy documentation.

* Mock gbrain responses for development and testing.

### Milestone 1 Work

Member 3 will:

* Obtain or confirm the available gbrain API documentation.

* Identify required ingestion, search, synthesis, entity, and deletion endpoints.

* Record sample requests and responses.

* Determine how citations are represented.

* Determine how insufficient-evidence or confidence information is represented.

* Determine whether answer synthesis is performed inside gbrain or through a separate provider call.

* Create a Spring interface for the gbrain adapter.

* Create mock JSON responses so frontend and backend development can proceed independently.

* Write the initial AI interaction design summary.

* Document the proposed search-versus-synthesis behavior.

* Participate in synthetic dataset design so the dataset supports meaningful retrieval tests.

### Milestone 2 Work

Member 3 will:

* Implement the real gbrain HTTP client.

* Implement ingestion submission.

* Implement indexed-content deletion.

* Implement Search mode.

* Implement Synthesis mode.

* Normalize citations into a stable application response.

* Implement entity listing.

* Implement related-document retrieval.

* Provide evidence or confidence signals to the backend.

* Add timeouts and safe error handling.

* Add logging that avoids recording confidential document content.

* Build adapter-level tests using mock HTTP responses.

### Milestone 3 Work

Member 3 will:

* Implement or refine knowledge-gap decision rules.

* Investigate hallucination and citation failures.

* Test contradictory source behavior.

* Test questions that require multiple documents.

* Help Member 5 categorize failure patterns.

* Tune retrieval or prompt configuration only when supported by measured results.

* Document gbrain integration limitations.

* Support the optional graph visualization if the stretch feature is attempted.

### Secondary Responsibilities

Member 3 should serve as backup for:

* Member 5’s AI evaluation methodology.

* Member 2’s ingestion integration.

* Member 4’s citation and entity UI integration.

---

## 5.4 Member 4: Frontend and User Experience Engineer

### Primary Responsibilities

Member 4 will own the React user interface.

Primary areas:

* Application layout and navigation.

* Login experience.

* Admin and user route separation.

* Manual note interface.

* Markdown upload interface.

* Ingestion status display.

* Q\&A interface.

* Search and Synthesis mode controls.

* Citation display.

* Knowledge-gap messaging.

* Entity list and entity detail interfaces.

* Knowledge management table.

* Delete and retry confirmation workflows.

* Loading, empty, error, and permission-denied states.

* Accessibility and responsive layout.

### Milestone 1 Work

Member 4 will:

* Create admin and user flow diagrams.

* Produce low-fidelity wireframes.

* Define the initial component hierarchy.

* Define frontend route structure.

* Create the React application shell.

* Establish API service utilities.

* Create reusable loading, alert, and error components.

* Work with Andrew to define authentication behavior.

* Work with Member 3 to define citation presentation.

* Design a UI that communicates that answers are AI-generated and may be incorrect.

### Milestone 2 Work

Member 4 will:

* Implement the login interface.

* Implement protected routes.

* Implement the admin knowledge list.

* Implement manual note creation and editing.

* Implement Markdown upload.

* Display ingestion statuses.

* Implement the Q\&A interface.

* Add Search and Synthesis mode selection.

* Display ranked search results.

* Display synthesized answers.

* Render citations clearly.

* Implement entity listing and related-document navigation.

* Implement delete, permanent-delete, restore, and retry controls as supported by the backend.

* Implement usage summary views.

### Milestone 3 Work

Member 4 will:

* Implement knowledge-gap messages.

* Ensure partial matches remain visible when the system lacks enough evidence.

* Improve citation navigation.

* Add empty, loading, timeout, and failure states.

* Test the UI with long answers, many citations, and noisy entity data.

* Address accessibility issues.

* Support the selected stretch dashboard.

* Prepare screenshots and interface explanations for the final presentation.

The sponsor specifically warns that automated entity extraction may be noisy, so the interface should not imply that all detected entities are guaranteed to be correct.

### Secondary Responsibilities

Member 4 should serve as backup for:

* Member 5’s dashboard user interface.

* Andrew’s authentication integration.

* Member 3’s entity and citation response validation.

---

## 5.5 Member 5: Evaluation, Quality Assurance, and Cost Analytics Engineer

### Primary Responsibilities

Member 5 will own:

* Synthetic company dataset coordination.

* Ground-truth question set.

* AI reliability evaluation.

* Automated test strategy.

* End-to-end tests.

* Usage estimation logic.

* Cost dashboard.

* Reliability findings.

* Cost observations report.

* Feasibility report coordination.

This must remain a technical development role, not only a reporting role.

### Milestone 1 Work

Member 5 will:

* Define the dummy corporation’s structure and business domain.

* Define the document categories.

* Coordinate creation of 50 cohesive synthetic documents.

* Ensure every team member contributes to the dataset.

* Maintain a dataset catalog.

* Create an initial ground-truth question set.

* Design the reliability scoring rubric.

* Create the initial cost projection spreadsheet.

* Define estimated token and cost formulas.

* Define usage-event requirements with Member 2\.

* Define the automated testing plan.

* Help create the project risk matrix.

The sponsor requires a cohesive 50-document synthetic business dataset during Milestone 1 so that the project has realistic, interconnected knowledge for RAG evaluation.

### Milestone 2 Work

Member 5 will:

* Implement the usage estimation service.

* Record query and ingestion character counts.

* Estimate token usage using documented assumptions.

* Store estimated cost events.

* Implement usage summary endpoints with support from Member 2\.

* Add backend automated tests.

* Add frontend or end-to-end tests for critical workflows.

* Test the 50-document ingestion process.

* Verify citation links.

* Verify exact-match cache behavior.

* Build an evaluation runner or structured test worksheet.

* Record initial baseline results.

### Milestone 3 Work

Member 5 will:

* Run the formal reliability study.

* Categorize successes, failures, hallucinations, misattributions, and overconfidence.

* Measure knowledge-gap behavior.

* Analyze cost by operation type.

* Create the production projection for 50 to 100 users.

* Implement the advanced cost dashboard stretch feature.

* Lead the cost observations report.

* Coordinate the feasibility report.

* Ensure conclusions are supported by documented test examples.

* Prepare evaluation charts and tables for the final presentation.

### Secondary Responsibilities

Member 5 should serve as backup for:

* Member 3’s gap-detection evaluation.

* Member 4’s cost dashboard UI.

* Andrew’s integration and release testing.

---

# 6\. Shared Work Distribution

Several tasks should be shared rather than assigned to one person.

## 6.1 Synthetic Dataset

All five members should contribute approximately 10 documents each.

Member 5 will define the dataset rules and review consistency, but should not be expected to write all 50 documents.

Suggested dummy corporation document categories:

* Organization overview.

* Employee directory.

* Team responsibilities.

* Company policies.

* Onboarding procedures.

* Security procedures.

* Product descriptions.

* Project plans.

* Architecture decision records.

* Meeting notes.

* Incident reports.

* Customer support procedures.

* Vendor information.

* Release notes.

* Budget or cost summaries.

* Frequently asked questions.

The documents should reference common people, teams, systems, decisions, and projects so the knowledge base contains interconnected context rather than 50 unrelated files.

## 6.2 Code Reviews

Every pull request should have at least one reviewer other than the author.

Recommended review pairs:

* Andrew and Member 2 review backend platform work.

* Member 2 and Member 3 review ingestion integration.

* Member 3 and Member 5 review AI evaluation logic.

* Member 4 and Andrew review frontend API integration.

* Member 5 and the relevant feature owner review tests and acceptance criteria.

## 6.3 Final Presentation

Each member should present the technical area they primarily developed:

* Andrew: architecture, security, integration, and deployment.

* Member 2: ingestion pipeline, database, and deletion behavior.

* Member 3: gbrain integration, hybrid retrieval, citations, and gap detection.

* Member 4: React interface and user workflows.

* Member 5: reliability evaluation, cost analysis, and feasibility findings.

---

# 7\. Milestone and Week-by-Week Plan

The sponsor divides the semester into three major milestones: design and setup during Weeks 1 through 3, core MVP development during Weeks 4 through 8, and evaluation and final demonstration during Weeks 9 through 12\.

## Week 1: Requirements, Scope, and Team Organization

### Team Objectives

* Review the sponsor document together.

* Convert every MVP requirement into GitHub issues.

* Separate MVP, stretch, and out-of-scope work.

* Confirm the five primary team roles.

* Choose Markdown as the MVP upload format.

* Select the official stretch target.

* Establish communication and meeting practices.

* Establish repository and branch rules.

* Begin gbrain API investigation.

* Define the dummy corporation.

### Deliverables

* Project charter.

* Team responsibility matrix.

* Initial GitHub project board.

* Initial risk register.

* Initial list of sponsor questions.

* Agreed MVP boundary.

* Dummy corporation concept.

### Primary Owners

* Andrew: project structure and scope tracking.

* Member 5: dataset concept and risk register.

* Member 3: gbrain questions.

* Member 4: initial user workflows.

* Member 2: initial data requirements.

---

## Week 2: Architecture and Development Environment

### Team Objectives

* Start the sponsor-provided Docker Compose environment.

* Create Spring Boot and React application shells.

* Verify PostgreSQL connectivity.

* Verify gbrain service availability.

* Create architecture and workflow diagrams.

* Define database tables.

* Define initial REST API contracts.

* Define mock gbrain responses.

* Begin generating the synthetic dataset.

* Configure GitHub Actions or another CI workflow.

### Deliverables

* Running local environment.

* System architecture diagram.

* Knowledge ingestion workflow diagram.

* Query workflow diagram.

* Admin user-flow diagram.

* Normal user-flow diagram.

* Database model draft.

* API contract draft.

* Initial dataset documents.

* Initial CI checks.

### Milestone Gate

Every team member should be able to clone the repository and start the core environment. Setup should not depend on one person’s machine.

---

## Week 3: Milestone 1 Completion

### Team Objectives

* Complete all 50 synthetic documents.

* Complete the dataset catalog.

* Create the initial ground-truth question set.

* Finish the AI interaction design summary.

* Finish the cost projection spreadsheet.

* Finalize the risk matrix.

* Finalize architecture and workflow diagrams.

* Confirm gbrain API boundaries.

* Demonstrate that all services start.

* Present Milestone 1 artifacts to the sponsor.

### Deliverables

* 50-document synthetic company dataset.

* Dataset catalog.

* Initial evaluation question set.

* Approved architecture.

* Approved user flows.

* AI interaction design summary.

* Cost projection spreadsheet.

* Risk assessment matrix.

* Repository README.

* Contribution guidelines.

* Running Docker Compose environment.

---

## Week 4: Authentication and Application Foundation

### Backend Work

* Implement user table.

* Seed one admin and one normal user.

* Implement Spring Security.

* Implement login and logout.

* Implement role checks.

* Create shared error-response format.

* Create initial knowledge item schema.

### Frontend Work

* Implement login page.

* Implement authenticated application shell.

* Implement admin and user navigation.

* Implement protected routes.

* Create placeholder pages for all MVP views.

### AI and Testing Work

* Complete mock gbrain service.

* Add initial backend tests.

* Add authentication acceptance tests.

* Confirm API error and timeout behavior.

### Week 4 Exit Criteria

* Admin and user can log in.

* Admin-only backend endpoints reject normal users.

* React routes reflect authenticated role.

* CI passes.

---

## Week 5: Knowledge Ingestion MVP

### Backend Work

* Implement manual text note creation.

* Implement manual note editing.

* Implement Markdown upload.

* Implement file validation.

* Implement text extraction.

* Implement ingestion status transitions.

* Connect ingestion service to the gbrain adapter.

### Frontend Work

* Create manual note form.

* Create Markdown upload form.

* Display item list.

* Display ingestion status.

* Display safe failure messages.

* Add retry control.

### Testing Work

* Test valid Markdown.

* Test empty files.

* Test incorrect extensions.

* Test oversized files.

* Test gbrain timeout.

* Test failed ingestion.

* Test retry behavior.

### Week 5 Exit Criteria

* An admin can create a note.

* An admin can upload a Markdown document.

* Content reaches the mocked or real gbrain endpoint.

* Status changes are visible.

---

## Week 6: Search, Synthesis, and Citations

### Backend Work

* Implement Search mode endpoint.

* Implement Synthesis mode endpoint.

* Normalize gbrain responses.

* Normalize citation objects.

* Record query events.

* Add query-level logging and errors.

### Frontend Work

* Implement Q\&A form.

* Implement mode selector.

* Display ranked Search results.

* Display synthesized answers.

* Display citations.

* Display loading and failure states.

### Testing Work

* Test a direct factual question.

* Test a question requiring multiple documents.

* Test a query with no relevant sources.

* Test citation formatting.

* Test malformed gbrain responses.

* Test provider timeout behavior.

### Week 6 Exit Criteria

* A user can ask a question.

* Search mode returns documents.

* Synthesis mode returns an answer.

* Citations are visible and traceable.

---

## Week 7: Management, Caching, and Cost Tracking

### Backend Work

* Implement exact-match cache.

* Implement cache hit tracking.

* Implement knowledge-revision invalidation.

* Implement soft deletion.

* Implement permanent deletion.

* Implement estimated token calculations.

* Implement estimated cost calculations.

* Implement usage summary endpoint.

### Frontend Work

* Add delete and permanent-delete controls.

* Add confirmation dialogs.

* Add usage summary table.

* Show whether a response was served from cache when appropriate.

* Improve admin item filtering.

### Testing Work

* Repeat an exact query and verify a cache hit.

* Change the knowledge base and verify cache invalidation.

* Soft-delete a document and verify it no longer appears in retrieval.

* Permanently delete a document and verify associated knowledge-engine content is removed.

* Verify normal users cannot access cost or management endpoints.

### Week 7 Exit Criteria

* Required cache behavior works.

* Admin content management works.

* Usage estimates are recorded.

* Deletion behavior is consistent.

The MVP requires both soft deletion and permanent deletion, including removal of associated embeddings or indexed content.

---

## Week 8: Entity Awareness and Milestone 2 Hardening

### Backend Work

* Implement entity listing.

* Implement entity-related documents.

* Implement document-related entities.

* Finish missing MVP endpoints.

* Improve error handling and logging.

### Frontend Work

* Implement entity list.

* Implement entity details.

* Implement related-document navigation.

* Handle noisy or missing entity information gracefully.

* Finish all core admin and user interfaces.

### Integration Work

* Ingest all 50 synthetic documents.

* Run the complete acceptance workflow.

* Fix integration defects.

* Verify setup on all team machines.

* Demonstrate Milestone 2 to the sponsor.

### Week 8 Exit Criteria

* Complete ingestion-to-answer workflow works.

* Citations work.

* Entity awareness works.

* Cache works.

* Admin management works.

* Usage tracking works.

* The synthetic dataset is fully ingested.

* No critical workflow depends on mock responses.

---

## Week 9: Knowledge-Gap Handling and Evaluation Baseline

### Team Objectives

* Implement backend gap-decision behavior.

* Implement frontend gap messaging.

* Finalize the evaluation question set.

* Run an initial baseline evaluation.

* Record latency and estimated costs.

* Identify the most serious retrieval and synthesis failures.

### Recommended Question Categories

* Direct factual lookup.

* Multi-document synthesis.

* Process and policy questions.

* Entity questions.

* Ambiguous questions.

* Questions with no supporting documents.

* Questions based on contradictory documents.

* Questions involving stale or superseded information.

* Questions phrased differently but seeking the same answer.

* Questions designed to test overconfidence.

### Week 9 Exit Criteria

* Unsupported questions produce honest gap messaging.

* Partial matches remain available.

* Evaluation results can be recorded consistently.

* Initial failure categories have been identified.

The interface must clearly communicate when there is insufficient evidence rather than allowing the model to produce a confident but unsupported answer.

---

## Week 10: Reliability Study and Corrective Work

### Team Objectives

* Run the formal question set.

* Score retrieval quality.

* Score answer accuracy.

* Score citation quality.

* Score gap detection.

* Categorize hallucinations and misattributions.

* Test contradictory-source behavior.

* Fix high-impact defects.

* Rerun affected tests.

* Analyze cost and latency.

### Week 10 Exit Criteria

* Reliability results are documented.

* Specific successful and failed examples are preserved.

* Critical implementation defects are distinguished from inherent AI limitations.

* The team has enough evidence to begin the feasibility report.

---

## Week 11: Stretch Feature and Final Documentation

### Team Objectives

* Freeze new MVP functionality.

* Implement the advanced cost dashboard.

* Complete the cost projection for 50 to 100 users.

* Draft the feasibility report.

* Draft the reliability report.

* Complete architecture and API documentation.

* Prepare the demonstration dataset.

* Prepare presentation slides.

* Assign final presentation sections.

### Stretch Gate

The stretch feature should only proceed if:

* All MVP acceptance criteria pass.

* No critical defects remain.

* All 50 documents are ingestible.

* Search and synthesis work reliably enough for demonstration.

* Citations and gap messaging work.

* The evaluation study is underway.

---

## Week 12: Code Freeze, Final Testing, and Demonstration

### Team Objectives

* Establish final code freeze.

* Run the complete regression suite.

* Verify a clean installation from the README.

* Verify API keys and secrets are excluded.

* Rehearse the live demonstration.

* Prepare a fallback recorded demonstration.

* Complete the feasibility and cost reports.

* Clean the GitHub repository.

* Tag the final release.

* Submit all required artifacts.

### Final Deliverables

* Working proof-of-concept.

* Final source code.

* Docker Compose configuration.

* Complete README.

* Installation and startup instructions.

* Architecture documentation.

* API documentation.

* Synthetic dataset.

* Evaluation question set.

* Reliability and limitations study.

* Cost observations report.

* Production cost projection.

* Feasibility report.

* Final presentation.

* Live demonstration.

The sponsor requires the final repository to contain cleaned code, complete documentation, deployment instructions, and the final reports.

---

# 8\. Synthetic Dataset Plan

## 8.1 Dummy Corporation Recommendation

The team should invent a medium-sized technology or consulting company with approximately:

* 75 to 150 fictional employees.

* Five to eight departments.

* Three to five active products or service lines.

* Several completed and active internal projects.

* Written policies and operational procedures.

* A small set of known incidents and architectural decisions.

* Some intentionally incomplete knowledge areas.

* A small number of intentionally contradictory or outdated documents.

The corporation should be complex enough to require cross-document retrieval but simple enough for the team to understand thoroughly.

## 8.2 Suggested Document Distribution

| Category | Document Count |
| :---- | ----: |
| Company overview and department descriptions | 5 |
| Employee and team profiles | 5 |
| Policies and procedures | 8 |
| Project plans and status reports | 7 |
| Meeting notes | 8 |
| Architecture decision records | 5 |
| Incident reports and retrospectives | 4 |
| Product or service documentation | 4 |
| Onboarding and training material | 2 |
| Vendor, budget, or operational notes | 2 |
| **Total** | **50** |

## 8.3 Dataset Quality Rules

Each document should include:

* Unique identifier.

* Title.

* Document type.

* Author or owner.

* Creation date.

* Last-updated date.

* Department or project.

* Body content.

* Related people, teams, or systems.

* Whether it is current, outdated, incomplete, or intentionally contradictory.

Member 5 should maintain a dataset catalog containing the expected facts in each document. This catalog will support later evaluation without being ingested into the knowledge base.

---

# 9\. AI Reliability Evaluation Plan

## 9.1 Evaluation Question Set

The team should create approximately 60 evaluation questions.

Suggested distribution:

| Question Type | Count |
| :---- | ----: |
| Direct factual lookup | 15 |
| Multi-document synthesis | 10 |
| Process or policy questions | 10 |
| Entity relationship questions | 5 |
| Unsupported knowledge-gap questions | 8 |
| Ambiguous questions | 4 |
| Contradictory-source questions | 4 |
| Outdated or superseded information questions | 4 |
| **Total** | **60** |

## 9.2 Ground-Truth Record

Each evaluation question should include:

* Question identifier.

* Question text.

* Question category.

* Expected answer.

* Required supporting documents.

* Acceptable alternate wording.

* Whether the system should answer or declare a gap.

* Whether multiple documents are required.

* Expected cited sources.

* Actual system answer.

* Actual citations.

* Human evaluation scores.

* Notes about failures.

## 9.3 Recommended Metrics

### Retrieval Success

Determine whether the correct supporting document appears within the top results.

Possible measurements:

* Top-1 retrieval success.

* Top-3 retrieval success.

* Top-5 retrieval success.

### Answer Groundedness

Score whether the answer is supported by the retrieved documents.

Suggested scale:

* 0: Unsupported or contradicted.

* 1: Partially supported.

* 2: Fully supported.

### Citation Accuracy

Score whether the cited documents actually support the claims made.

* 0: Citations absent or unrelated.

* 1: Some citations relevant.

* 2: All material claims supported.

### Answer Completeness

* 0: Misses the essential answer.

* 1: Partially answers.

* 2: Completely answers.

### Gap Honesty

For questions with no valid answer:

* Correct gap response.

* Incorrect attempted answer.

* Partial-match response without sufficient warning.

### Additional Operational Metrics

* Response latency.

* Search versus synthesis latency.

* Cache-hit rate during repeated tests.

* Estimated tokens per synthesis request.

* Estimated cost per request.

* Failure rate.

* Timeout rate.

The final report must document specific examples of hallucination, misattribution, overconfidence, contradictory-source handling, and other unreliable behaviors.

---

# 10\. Cost-Conscious Architecture Plan

The sponsor requires the team to establish a cost budget, estimate per-operation usage, distinguish cost tiers, project scaling costs, document assumptions, and produce a projection for a 50 to 100-user deployment.

## 10.1 Development Budget

During Milestone 1, the team should agree with the sponsor on:

* LLM provider.

* Embedding provider.

* Available API credits.

* Development spending ceiling.

* Maximum daily or weekly test budget.

* Whether automated evaluation may use the live provider.

* Whether lower-cost models may be used during development.

* Which model will be used for the final demonstration.

## 10.2 Usage Estimation

Because the external API may not provide exact token data, the application should record:

* Input character count.

* Output character count.

* Estimated input tokens.

* Estimated output tokens.

* Operation type.

* Cache hit.

* Model or provider configuration.

* Estimated unit price.

* Estimated total operation cost.

A configurable estimation formula should be used rather than hard-coding one provider’s behavior. For example:

estimated\_input\_tokens \=  
    ceiling(input\_character\_count / configured\_characters\_per\_token)

estimated\_output\_tokens \=  
    ceiling(output\_character\_count / configured\_characters\_per\_token)

estimated\_cost \=  
    input\_token\_cost \+ output\_token\_cost \+ embedding\_cost

All assumptions should be visible in the cost report.

## 10.3 Cost Reduction Measures

The MVP should include:

* Search mode for questions that do not require synthesis.

* Exact-match response caching.

* Maximum document size.

* Maximum query length.

* Controlled retrieval count.

* Controlled synthesis context size.

* Request timeouts.

* No automatic repeated LLM calls.

* Batch ingestion where supported.

* Development use of synthetic rather than sensitive data.

## 10.4 Production Projection

The final model should estimate monthly cost using variables such as:

* Number of users.

* Queries per user per day.

* Percentage using Search mode.

* Percentage using Synthesis mode.

* Average input size.

* Average output size.

* Cache-hit rate.

* Documents added per month.

* Average document size.

* Embedding cost.

* Synthesis cost.

At minimum, projections should be shown for:

* 50 users.

* 75 users.

* 100 users.

---

# 11\. Security and Ethical Requirements

## 11.1 Authentication and Authorization

* Passwords must be securely hashed.

* Admin operations must be enforced by Spring Boot.

* The frontend must not be treated as the security boundary.

* Normal users must not access ingestion, deletion, or cost-management endpoints.

* API responses should not expose password hashes, provider keys, or internal error traces.

## 11.2 File Upload Security

* Accept only the selected MVP file extension.

* Verify content type where practical.

* Apply a file size limit.

* Generate server-side storage identifiers.

* Do not execute uploaded content.

* Reject empty files.

* Sanitize display filenames.

* Avoid logging complete document contents.

* Return safe user-facing errors.

## 11.3 Data Deletion

Permanent deletion must remove:

* The application knowledge item.

* Ingestion records where appropriate.

* Associated knowledge-engine records.

* Associated chunks or embeddings.

* Cached answers that may depend on the removed content.

## 11.4 AI Transparency

The user interface should:

* Label synthesized answers as AI-generated.

* Display citations.

* Avoid presenting AI output as an authoritative business decision.

* Warn users when evidence is insufficient.

* Preserve partial search results when useful.

* Explain that entity detection can be noisy.

* Avoid hiding uncertainty behind fluent wording.

The sponsor emphasizes that the platform should favor admitting insufficient knowledge over producing plausible but unsupported answers.

## 11.5 Synthetic Data Only

The team should use only the synthetic company dataset unless the sponsor explicitly approves another source.

The project should not ingest:

* Real employee personal information.

* Real proprietary business documents.

* Student records.

* Medical data.

* Financial account information.

* Authentication credentials.

* Confidential university information.

---

# 12\. Testing Strategy

## 12.1 Unit Tests

Each developer is responsible for unit tests covering their own code.

Examples:

* Authentication service tests.

* Role authorization tests.

* File validation tests.

* Markdown extraction tests.

* Status transition tests.

* Cache key tests.

* Cache invalidation tests.

* Cost estimation tests.

* Citation normalization tests.

* Gap decision tests.

## 12.2 Integration Tests

Recommended integration tests:

* Spring Boot with PostgreSQL.

* Knowledge item creation and persistence.

* Knowledge upload through ingestion.

* Mock gbrain success.

* Mock gbrain failure.

* Query and citation response.

* Permanent deletion.

* Cache hit.

* Cache invalidation.

* Authorization enforcement.

A mock HTTP server should be used for gbrain adapter tests so automated tests do not depend on a paid API or live external service.

## 12.3 Frontend Tests

Frontend tests should cover:

* Login form.

* Role-specific navigation.

* Manual knowledge form.

* Upload validation.

* Status display.

* Search results.

* Synthesized answer display.

* Citation display.

* Gap warning.

* Delete confirmation.

* Permission-denied state.

## 12.4 End-to-End Tests

At minimum, automate these critical paths:

1. Admin login and manual note creation.

2. Admin Markdown upload.

3. User Search mode question.

4. User Synthesis mode question.

5. Citation display.

6. Exact query repetition and cache hit.

7. Unsupported question and gap warning.

8. Admin soft deletion.

9. Admin permanent deletion.

10. Normal user blocked from admin endpoint.

## 12.5 Manual AI Evaluation

AI output cannot be validated exclusively with ordinary unit tests. The team must also perform structured human review using the ground-truth question set.

Automated tests should validate system behavior and response structure. Human evaluation should validate meaning, evidence, accuracy, completeness, and citation support.

---

# 13\. GitHub and Collaboration Workflow

## 13.1 Repository Structure

A recommended repository structure is:

knowledgebridge/  
  backend/  
  frontend/  
  synthetic-data/  
  evaluation/  
  docs/  
  docker/  
  scripts/  
  README.md  
  CONTRIBUTING.md  
  docker-compose.yml

## 13.2 Branch Strategy

Use short-lived feature branches:

feature/authentication  
feature/manual-ingestion  
feature/gbrain-search  
feature/query-cache  
feature/entity-ui  
fix/cache-invalidation  
docs/architecture

Avoid permanent individual branches and avoid waiting until the end of a milestone to merge.

## 13.3 Pull Request Rules

Every pull request should:

* Reference a GitHub issue.

* Describe the change.

* Describe how it was tested.

* Include screenshots for visible UI changes.

* Include automated tests when appropriate.

* Receive at least one approval.

* Pass automated checks.

* Avoid unrelated changes.

The main branch should be protected from direct pushes.

## 13.4 Recommended Meeting Cadence

### Weekly Planning Meeting

Approximately 45 to 60 minutes:

* Review milestone status.

* Select work for the week.

* Identify blockers.

* Reassign work when needed.

* Review sponsor feedback.

* Confirm upcoming demonstrations.

### Midweek Integration Check

Approximately 20 to 30 minutes:

* Confirm branches are being merged.

* Demonstrate partially completed features.

* Catch API contract problems early.

* Review external service issues.

### Asynchronous Updates

Each member posts:

* What was completed.

* What is in progress.

* What is blocked.

* What pull request needs review.

### Weekly Internal Demo

At least once per week, the team should demonstrate the integrated application from main. This prevents components from remaining disconnected until the end of a milestone.

---

# 14\. Definition of Done

A feature is not complete merely because code has been written.

A GitHub issue is complete only when:

* Acceptance criteria are satisfied.

* The feature works in the integrated application.

* Authorization is enforced where required.

* Errors are handled.

* Logging is appropriate.

* Tests are added and passing.

* No credentials or sensitive content are committed.

* API documentation is updated.

* User-facing behavior is documented.

* A teammate has reviewed the code.

* The change has been merged into main.

* The feature can be demonstrated from Docker Compose.

---

# 15\. Risk Management

## 15.1 gbrain Integration Risk

**Risk:** The knowledge engine may be difficult to configure or its response contracts may be unclear.

**Mitigation:**

* Investigate the API in Week 1\.

* Create a dedicated adapter.

* Use mock responses.

* Keep gbrain behind a stable internal interface.

* Escalate missing API details to the sponsor immediately.

* Do not modify gbrain internals.

## 15.2 Dataset Quality Risk

**Risk:** Fifty unrelated AI-generated documents will not produce meaningful evaluation results.

**Mitigation:**

* Define the dummy corporation first.

* Reuse people, projects, teams, systems, and decisions.

* Maintain a dataset catalog.

* Add cross-document references.

* Create the ground-truth questions while generating documents.

* Have Member 5 review consistency.

## 15.3 Scope Creep

**Risk:** The team attempts PDF parsing, multi-turn chat, graph visualization, SaaS integrations, or enterprise authentication before the MVP is stable.

**Mitigation:**

* Label every issue as MVP, stretch, or out of scope.

* Require a milestone gate before stretch work.

* Select one official stretch feature.

* Decline new features that do not support a required outcome.

* Review scope every week.

The sponsor identifies scope creep as a high-likelihood risk and requires MVP-first delivery.

## 15.4 AI Hallucination Risk

**Risk:** The model produces plausible but incorrect business answers.

**Mitigation:**

* Require citations.

* Display AI disclosure.

* Implement gap messaging.

* Preserve retrieved evidence.

* Evaluate unsupported questions.

* Record hallucination examples.

* Avoid claiming guaranteed accuracy.

* Document where human oversight is required.

## 15.5 Cache Staleness Risk

**Risk:** A cached response remains available after the knowledge base changes.

**Mitigation:**

* Include the knowledge revision in the cache key.

* Increment the revision after every content change.

* Do not reuse cache entries from an older revision.

* Test add, edit, soft-delete, and permanent-delete invalidation.

## 15.6 Team Bottleneck Risk

**Risk:** One member becomes the only person capable of working on a critical component.

**Mitigation:**

* Assign a backup owner to every workstream.

* Require code review.

* Document setup and interfaces.

* Rotate integration tasks.

* Keep branches short-lived.

* Demonstrate work frequently.

* Store all decisions in the repository.

## 15.7 External API Cost Risk

**Risk:** Automated tests or repeated development requests create unexpected charges.

**Mitigation:**

* Use mocks for routine development.

* Use Search mode when synthesis is unnecessary.

* Establish a development budget.

* Track request estimates.

* Limit automated live-provider tests.

* Cache exact repeated questions.

* Keep API keys centralized and revocable.

---

# 16\. Questions to Resolve With the Sponsor

The team should obtain answers to the following during the first sponsor meeting:

1. What exact gbrain version and configuration will be provided?

2. What ingestion endpoints are available?

3. What Search and Synthesis endpoints are available?

4. Does gbrain perform answer synthesis internally?

5. How are citations represented?

6. What confidence or insufficient-evidence signals are available?

7. How are entities and relationships returned?

8. How should indexed content be permanently deleted?

9. Does gbrain provide an update operation, or must edited content be deleted and re-ingested?

10. Does gbrain share the application’s PostgreSQL instance?

11. Who provides the LLM and embedding API credentials?

12. Is there a development cost limit?

13. Is the team expected to deploy to a shared server, or is Docker Compose demonstration sufficient?

14. What authentication method is preferred?

15. Does the sponsor expect manual note editing after ingestion?

16. How should citations link to manually entered notes?

17. What specific evidence signal should trigger a knowledge-gap response?

18. Is there a required final-report format?

19. How frequently does the sponsor expect progress demonstrations?

20. What behavior is expected if a cited document has been soft-deleted?

---

# 17\. Recommended Final Demonstration

The team should rehearse a demonstration that takes approximately 10 to 12 minutes.

## Demonstration Sequence

### Part 1: Architecture

Andrew briefly explains:

* React frontend.

* Spring Boot backend.

* PostgreSQL.

* gbrain.

* LLM and embedding providers.

* Docker Compose.

### Part 2: Knowledge Ingestion

Member 2:

* Logs in as admin.

* Creates a manual note.

* Uploads a Markdown document.

* Shows status changes.

* Opens the knowledge item list.

### Part 3: Search and AI Synthesis

Member 3:

* Logs in as a normal user.

* Runs a Search mode query.

* Shows ranked documents.

* Runs a Synthesis mode query.

* Shows the answer and citations.

* Explains hybrid retrieval.

### Part 4: User Interface and Knowledge Gap

Member 4:

* Opens citations.

* Browses an entity and related documents.

* Asks an unsupported question.

* Shows the system’s knowledge-gap response.

### Part 5: Cost and Reliability

Member 5:

* Shows usage estimates.

* Demonstrates a cache hit.

* Shows evaluation results.

* Presents one successful example.

* Presents one hallucination or failure example.

* Summarizes where human oversight remains necessary.

### Part 6: Management and Conclusion

Andrew:

* Demonstrates soft deletion.

* Demonstrates permanent deletion or explains the verified deletion test.

* Summarizes completed MVP features.

* Identifies the stretch feature.

* Concludes with feasibility findings.

A recorded fallback demonstration should be prepared in case the external knowledge engine or LLM provider is unavailable during the live presentation.

---

# 18\. Immediate Next Actions

The team should complete the following actions first:

1. Assign the four unnamed member roles based on skills.

2. Create the GitHub repository and project board.

3. Schedule the first sponsor meeting.

4. Obtain the gbrain API documentation and Docker Compose configuration.

5. Confirm the LLM and embedding providers.

6. Confirm the development budget.

7. Approve Markdown as the MVP document format.

8. Approve the advanced cost dashboard as the primary stretch goal.

9. Define the dummy corporation.

10. Divide the 50 synthetic documents among the five team members.

11. Create the architecture diagram.

12. Create the first API contract draft.

13. Validate the environment on every team member’s computer.

14. Establish weekly planning and integration meetings.

15. Begin the Milestone 1 deliverables immediately.

This structure gives every team member substantial technical ownership while preserving shared responsibility for integration, testing, documentation, and the final demonstration. It also follows the sponsor’s MVP-first milestone structure and protects the team from the major identified risks: scope creep, poor evaluation data, gbrain integration complexity, hallucination, and uncontrolled AI costs.