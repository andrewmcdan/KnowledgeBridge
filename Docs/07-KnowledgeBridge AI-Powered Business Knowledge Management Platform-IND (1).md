# KnowledgeBridge — AI-Powered Business Knowledge Management Platform

---

## Sponsor Information

**Primary Sponsor:** Pravin Khandke ([pravin.khandke@ieee.org](mailto:pravin.khandke@ieee.org)) — Department of Information Technology  
**Project Duration:** One semester (Fall 2026\)  
**Team Size:** 3–5 undergraduate computing students  
**Project Type:** Applied AI · Knowledge Management · Enterprise Knowledge Retrieval

---

## Market Context & Industry Relevance

The enterprise knowledge management market is undergoing a fundamental shift. Organizations across every sector — from startups to Fortune 500 companies — are racing to make their internal knowledge accessible through AI-powered interfaces. Products like **Glean**, **Notion AI**, **Microsoft Copilot**, and **Google Vertex AI Search** have validated a clear demand: *employees want to ask questions and get answers from their company's data, not just from the public internet.*

However, a critical gap remains. **General-purpose LLMs are excellent at general knowledge but know nothing about how a specific business operates.** They cannot tell a new hire which internal process to follow, explain why a particular architectural decision was made, or surface insights buried in months of meeting recordings. This "last mile" problem — bridging the gap between general AI capability and **business-specific context** — is where the next wave of innovation is happening.

KnowledgeBridge positions students at the center of this trend. They will build a platform that ingests a company's proprietary knowledge and makes it queryable through natural language. This is not a theoretical exercise — it mirrors what well-funded startups and enterprise teams are building right now. Students will emerge from this project with directly marketable skills in **RAG architecture**, **hybrid search**, and **AI cost optimization** — some of the most sought-after competencies in the 2026 job market.

---

## Project Background

Every organization accumulates vast amounts of institutional knowledge — decisions made in meetings, processes documented in files, insights exchanged over email, and expertise locked inside employees' heads. This knowledge is typically:

- **Fragmented** across different systems (email inboxes, shared drives, chat threads, video recordings)  
- **Hard to discover** — new team members spend weeks or months ramping up  
- **Dependent on humans** — when an experienced employee leaves, their knowledge leaves with them  
- **Inaccessible on demand** — you can't ask a shared drive a question and get an answer in seconds

Existing solutions fall short. General-purpose LLMs such as ChatGPT or Claude have no access to proprietary business context. Traditional knowledge bases require manual curation and quickly become stale. Enterprise search tools return keyword-matched documents, not synthesized answers.

KnowledgeBridge addresses this by creating a **business-specific AI knowledge layer**. The platform ingests business content, integrates with a knowledge engine for hybrid search and synthesis, and provides a natural-language Q\&A interface that delivers **cited, synthesized answers** from an organization's own data — while honestly flagging what it does not yet know.

This is both a practical application and an exploratory AI research exercise. Students will evaluate where AI-assisted knowledge retrieval succeeds, where it becomes unreliable, and where human oversight remains essential.

---

## Learning Objectives

By completing this project, students will:

- **Design and implement** a full-stack web application integrating AI and knowledge retrieval  
- **Apply Retrieval-Augmented Generation (RAG)** patterns to real-world business data  
- **Evaluate AI reliability** in domain-specific question-answering scenarios  
- **Implement cost-conscious AI architectures** with caching, token budgeting, and call minimization  
- **Process and structure** unstructured business content (text, documents) for AI consumption  
- **Analyze ethical implications** of AI systems accessing proprietary business data  
- **Collaborate using industry-standard tools** including Git, Claude Code, and modern LLM APIs  
- **Communicate feasibility findings** — clearly documenting where AI succeeds and where it fails

---

## Prerequisite Knowledge

Students are expected to have foundational knowledge in the following areas:

| Area | Expected Proficiency |
| :---- | :---- |
| **Java & Spring Boot** | Building REST APIs, dependency injection, JPA/Hibernate |
| **React & JavaScript** | Component-based UI development, state management, API integration |
| **PostgreSQL** | Schema design, basic queries, and indexing concepts |
| **RESTful API Design** | Endpoint design, request/response patterns, error handling |
| **Git & GitHub** | Branching, commits, pull requests, code review |
| **Basic AI/ML Concepts** | Understanding of LLMs, embeddings, and vector search at a conceptual level |

*Exposure to TypeScript and Docker is beneficial but not required — students will learn these during the project.*

---

## Project Goal

Students will design and implement a web-based application that enables organizations to:

- **Ingest** business knowledge through manual text entry and structured document uploads  
- **Store and index** that knowledge using a knowledge engine with hybrid search  
- **Query** the knowledge base using natural language and receive **cited, synthesized answers**  
- **Identify knowledge gaps** — areas where the system acknowledges insufficient information  
- **Track and optimize** estimated LLM usage costs for sustainable operation

The project combines:

- Multi-format knowledge ingestion and processing  
- AI-powered semantic search and answer synthesis  
- Guided evaluation of AI reliability in business-context Q\&A  
- Cost-conscious architecture design for sustainable AI usage

The project is intentionally exploratory. Students are expected to evaluate both the **strengths and limitations** of AI in business knowledge management environments.

---

## Core Features — MVP vs. Stretch

Each feature area is split into **MVP (Required)** — must be delivered within the semester — and **Stretch (Optional)** — attempted only after MVP is stable.

---

### 1\. Knowledge Ingestion

**MVP (Required)**

- **Manual text entry** — users will get a chat-like interface to ask questions and get answers.   
- **Single text-based document upload** — support upload of **one** document format (e.g., Markdown `.md`, `.txt`, or simple `.docx`). *Note: PDF parsing is notoriously complex and is explicitly reserved for Stretch to avoid stalling development.*  
- **Ingestion status display** — each item shows its state: pending, processing, completed, or failed  
- **Automatic embedding generation** — on successful ingestion, content is chunked and embeddings generated via the knowledge engine

**Stretch (Optional)**

- PDF document extraction and parsing  
- Email content entry (paste or forward email text into the platform — no real SMTP integration)  
- Batch import of multiple documents with progress display  
- Video/meeting recording upload with LLM-powered transcription

---

### 2\. Intelligent Query & Answer Synthesis

**MVP (Required)**

- **Natural language Q\&A** — users ask questions in plain language and receive synthesized answers drawn from the knowledge base  
- **Source citations** — every answer includes links or references to the underlying source documents  
- **Two query modes:**  
  - **Search mode** — returns a ranked list of relevant documents (no LLM cost)  
  - **Synthesis mode** — returns a coherent answer generated by the LLM  
- **Simple response caching** — implement a *naive exact-match cache* by query string in PostgreSQL. (While understood to have a low hit rate for natural language, it is required to demonstrate the architectural concept of caching to bypass redundant LLM calls).

**Stretch (Optional)**

- Semantic caching using vector similarity  
- Multi-turn conversation with context (follow-up questions referencing prior answers)  
- Confidence indicators on answers (low / medium / high) based on relevance signals  
- Automatic query classification — routing simple lookups to search, complex questions to synthesis

---

### 3\. Knowledge Graph & Entity Awareness

**MVP (Required)**

- **Entity listing** — retrieve and display a *filtered or limited list* of detected entities (people, projects, teams) from the knowledge engine. (Students must acknowledge that automated extraction may be noisy and design the UI to handle imperfect entity data gracefully).  
- **Entity-query linking** — when browsing a document, show related top entities; when viewing an entity, show related documents

**Stretch (Optional)**

- Interactive graph visualization (node-link diagram) showing entity relationships  
- Multi-hop query support (e.g., "Who was involved in the database migration?")  
- Entity and relationship filtering in the UI

---

### 4\. Knowledge Gap Handling

**MVP (Required)**

- **Gap awareness in Q\&A** — when the knowledge engine indicates low confidence or insufficient evidence, the UI clearly communicates this to the user  
- **Basic gap messaging** — show a message such as "The system does not have enough information to answer this confidently" along with any partial matches

**Stretch (Optional)**

- Coverage summary dashboard showing topic areas with strong vs. weak document coverage  
- Suggestions for what types of documents would improve coverage  
- Staleness indicators for knowledge that has not been updated recently

---

### 5\. Knowledge Management & Cost Dashboard

**MVP (Required)**

- **Knowledge item list** — admin view listing all ingested items with title, type, owner, date, and status  
- **Basic role-based access** — two roles: admin (manage content, view costs) and user (query only)  
- **Content management** — admins can soft-delete items (hide from search) and permanently delete (including associated embeddings)  
- **Estimated LLM usage tracking** — since the external gbrain API may not return exact token metrics, track query/ingestion counts and display an *estimated* cost based on average character or word counts in a simple table.

**Stretch (Optional)**

- Charts for query volume over time and usage by topic  
- LLM cost dashboard with breakdown by operation type (ingestion vs. synthesis)  
- More granular roles (viewer, contributor, admin) with audit logs

---

## AI Research & Feasibility Component

Students are expected to explore:

- **What AI can realistically support** in business knowledge management — where does RAG excel, and where does it break down?  
- **Where AI feedback becomes unreliable** — hallucination patterns, contradictory source handling, out-of-date knowledge conflicts  
- **Where human oversight remains necessary** — knowledge verification, sensitive business decisions, compliance-bound information

Students must:

- **Document observed limitations** with specific examples from their testing on a synthetic business dataset  
- **Identify unreliable behaviors** — patterns of hallucination, misattribution, or overconfidence  
- **Produce a final feasibility/research summary** that honestly assesses what KnowledgeBridge can and cannot do in a real business environment

This project is both a **development project** and an **exploratory applied-AI research exercise**.

---

## Cost-Conscious Architecture Requirement

Students must design with **operational sustainability** in mind. LLM API calls incur real financial costs.

### Token Economics & Budgeting

Students should:

- **Establish a token budget** — define a projected monthly cost ceiling for LLM API usage  
- **Estimate per-operation costs** — measure estimated tokens consumed per query and per ingestion based on character metrics  
- **Differentiate cost tiers** — classify operations as low-cost (embedding) or medium-cost (answer synthesis)  
- **Project scaling costs** — estimate how costs grow with user count and document volume

### Cost Transparency

Students must:

- **Document all cost assumptions** — chosen LLM provider, pricing tier, estimated monthly usage  
- **Track estimated costs** during development and testing  
- **Provide a cost projection** for a hypothetical production deployment with 50–100 users

---

## Ethical Considerations

This project involves processing business data through AI systems, raising important ethical considerations:

### Data Privacy & Confidentiality

- **Business data sensitivity** — ingested knowledge may contain proprietary strategies, financial information, or personal data. Implement appropriate access controls.  
- **Data minimization** — only process and store what is necessary. Avoid retaining unnecessary metadata.  
- **Data deletion** — support permanent deletion of knowledge entries, including associated embeddings and indexed content.

### AI Hallucination & Misinformation

- **Transparency** — users must understand they are interacting with an AI system that can produce incorrect information. The UI should visibly indicate AI-generated responses.  
- **No fabricated authority** — the system must not present AI-synthesized answers as authoritative business decisions. Source citations are mandatory.  
- **Gap honesty over fluency** — the system must admit ignorance rather than generate plausible-sounding but incorrect answers.

---

## Risk Assessment & Mitigation

| Risk | Likelihood | Impact | Mitigation Strategy |
| :---- | :---- | :---- | :---- |
| **LLM hallucination produces incorrect business answers** | High | High | Source citation requirement, gap honesty in UI, documented failure examples in the final report |
| **Knowledge engine setup and operational complexity** | Medium–High | High | Treat knowledge engine as an external HTTP API. Sponsor will provide pre-tested Docker Compose configurations explicitly verified on both Windows (WSL2) and Apple Silicon. |
| **Lack of cohesive testing data for RAG evaluation** | High | High | Students must generate a synthetic "dummy corporation" dataset (via AI tools) in Milestone 1 to ensure interconnected business context for testing. |
| **PDF parsing derailing development timelines** | High | Medium | PDFs explicitly moved to Stretch goals. MVP uses `.md`, `.txt`, or simple `.docx`. |
| **Scope creep — team tries to build too many features** | High | Medium | Clear MVP vs. Stretch separation in every feature area; milestone checkpoints enforce MVP-first delivery |
| **Token tracking blocked by external API abstraction** | High | Low | Token tracking downgraded to "estimated tracking" based on query counts and character lengths. |
| **Integration complexity between Spring Boot and knowledge engine** | Medium | Medium | Define clear HTTP API boundaries; provide sample request/response contracts |

---

## Explicitly Out of Scope

The following are **NOT** required:

- Full enterprise production deployment  
- Real-time multi-user collaboration  
- Native mobile applications (iOS/Android)  
- SSO / LDAP / OAuth enterprise identity integration  
- Guaranteed 100% answer accuracy  
- Real-time meeting transcription (pre-recorded only, and only as stretch)  
- Replacement for human decision-making or management oversight  
- Compliance with specific regulatory frameworks (GDPR, HIPAA, SOC 2\)  
- Multi-language support beyond English  
- Integration with third-party SaaS platforms (Slack, Teams, Salesforce, etc.)  
- Production-grade RBAC with fine-grained permissions (basic admin/user roles only)  
- MCP (Model Context Protocol) agent integration (stretch only — not core)

---

## Suggested Technical Stack

### Frontend

- **React** — component-based UI framework

### Backend

- **Spring Boot** — primary API server, authentication, file processing, business logic

### Knowledge Layer

- **gbrain** — open-source knowledge management engine  
  - Hybrid search: vector (pgvector HNSW) \+ BM25 keyword \+ reciprocal-rank fusion  
  - Answer synthesis with explicit source citations  
  - Self-wiring knowledge graph with zero-additional-LLM-call entity extraction  
  - Gap analysis: explicitly surfaces what the system does not know

>   
> **Important:** Students are **not expected to modify gbrain internals**. They will integrate it as an external service via a limited set of HTTP APIs. The sponsor will provide a pre-configured Docker Compose setup tested across operating systems.

### Database

- **PostgreSQL** with **pgvector** extension — primary data store and vector search

### AI & LLM

- **LLM API subscription** — OpenAI, Anthropic, or equivalent for answer synthesis  
- **Embedding provider** — OpenAI, Voyage AI, or equivalent for semantic chunking and vector generation  
- **Claude Code** — AI-assisted development tool for the engineering team

### Development & DevOps

- **Git & GitHub** — version control and collaboration  
- **Docker & Docker Compose** — local development environment (pre-configured template provided)

---

## Milestones & Deliverables — MVP vs. Stretch

Milestones are structured so **MVP is fully achievable in one semester**. Stretch items are attempted **only after the MVP portions of each milestone are complete**.

---

### Milestone 1 — Design & Environment Setup (Weeks 1–3)

**MVP Deliverables (Required)**

- **Synthetic test dataset generation** — 50 cohesive synthetic business documents (generated via Claude/ChatGPT) representing a "dummy corporation" for realistic RAG testing  
- Business knowledge workflow diagram — how knowledge flows from ingestion to query, including the knowledge engine as a separate service  
- User-flow diagrams — admin flow (adding/managing knowledge) and user flow (asking questions, viewing answers)  
- System architecture diagram — Spring Boot, React, PostgreSQL/pgvector, knowledge engine, and LLM APIs with clear service boundaries  
- AI interaction design summary — initial prompt strategy for synthesis, chunking approach, search vs. synthesis routing concept  
- Cost projection spreadsheet — estimating costs based on usage patterns  
- Risk assessment matrix  
- GitHub repository setup — README with project overview and setup steps, contributing guidelines, project board  
- **Running development environment** — Docker Compose that starts Spring Boot, PostgreSQL, and the knowledge engine (even with stub endpoints initially)

**Milestone 1 Required Outcomes**

- Architecture and workflows approved by sponsor  
- Dummy corporation dataset generated  
- All core services start successfully in local environment

---

### Milestone 2 — Core Platform MVP (Weeks 4–8)

**MVP Deliverables (Required)**

- **Authentication & basic roles** — login with two roles (admin, user) enforced in the backend  
- **Knowledge ingestion MVP:**  
  - Manual text note creation and editing in the UI  
  - Upload and processing of one text-based format (`.md`, `.txt`, or `.docx`)  
  - Ingestion status display  
- **Knowledge engine integration (core):**  
  - Endpoint(s) to send ingested content to the knowledge engine for storage and indexing  
  - Endpoint to query the knowledge engine and return citations with synthesized answers  
- **Q\&A interface** — simple form-based UI where users ask questions and see answers with source citations  
- **Knowledge management view** — list all ingested items with basic metadata  
- **Estimated LLM usage tracking** — calculate estimated tokens used per query and display totals  
- **Response caching** — exact-match query string caching in PostgreSQL  
- **Test data ingestion** — the 50 synthetic knowledge entries from M1 successfully ingested

**Milestone 2 Required Outcomes**

- End-to-end workflow: add document → ingestion → indexing → user query → synthesized answer with citations  
- Stable core workflows with basic error handling and logging  
- System is testable against the synthetic company data

---

### Milestone 3 — Evaluation & Final Demonstration (Weeks 9–12)

**MVP Deliverables (Required)**

- **Reliability and limitations study** — systematic testing of Q\&A behavior against the synthetic dataset; documented examples of successes, failures, and hallucinations  
- **Gap handling in UI** — clear messaging when the system has low confidence  
- **Cost observations report** — reflection on estimated costs and most expensive operations  
- **Feasibility report** — what the system can realistically support, where human oversight remains necessary, recommendations for next steps  
- **Final demo** — live demonstration showing ingestion, Q\&A, citations, and gap behavior  
- **Final GitHub repository** — cleaned code, complete documentation, deployment instructions, final reports

**Stretch (Optional — team must aim to deliver at least one, not all)**

- **Knowledge graph visualization** — retrieve entity/relationship data and render a basic interactive graph view  
- **Knowledge coverage dashboard** — simple charts showing coverage across topics  
- **Multi-turn conversational interface** — maintain conversation history and context  
- **Advanced cost dashboard** — break costs down by operation type with charts over time

**Milestone 3 Required Outcomes**

- Working proof-of-concept showing complete ingestion-to-insight workflow  
- Clear, honest documentation of system capabilities, limitations, and cost characteristics

---

## Student Résumé-Ready Outcomes

Students completing this project can state they:

- Built a working **RAG (Retrieval-Augmented Generation)** platform for business knowledge management  
- Integrated an **AI knowledge engine** as a service within a full-stack application  
- Designed and applied **cost-conscious AI architecture** with caching, token budgeting, and query routing  
- Evaluated **AI reliability and limitations** in domain-specific business Q\&A with documented findings  
- Engineered with **Spring Boot**, **React**, **PostgreSQL \+ pgvector**, and modern LLM APIs  
- Developed using **Claude Code** — AI-assisted software engineering at the forefront of industry practice  
- Produced an actionable **feasibility analysis** on where AI can and cannot replace human knowledge dependency in organizations

