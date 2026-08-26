# KnowledgeBridge

KnowledgeBridge is a capstone proof-of-concept for an AI-powered business knowledge management platform. It will ingest synthetic business knowledge, provide keyword and semantic search, synthesize answers with source citations, identify knowledge gaps, expose detected entities, and track estimated AI usage costs.

## Planned architecture

- `frontend/` - React web application for user Q&A and administration.
- `backend/` - Spring Boot REST API, application logic, persistence, authentication, and the gbrain adapter.
- `synthetic-data/` - Cohesive Markdown documents for the fictional company used in development and evaluation.
- `evaluation/` - Ground-truth questions, reliability results, and feasibility findings.
- `docker/` - Supporting container configuration used by the root Docker Compose stack.
- `scripts/` - Repeatable development, data-loading, and validation utilities.
- `Docs/` - Project brief and execution-plan source documents.

The gbrain knowledge engine is an external service boundary. Application code must access it through the backend adapter rather than modify or call its internals directly from the frontend.

## Getting started

The repository currently contains the initial architecture scaffold. Build and startup commands will be added when the React, Spring Boot, PostgreSQL/pgvector, and sponsor-provided gbrain configurations are introduced.

## MVP scope

The MVP targets Markdown ingestion, admin and user roles, search and synthesis query modes, cited answers, exact-match caching, entity browsing, knowledge-gap messaging, content lifecycle management, and estimated usage reporting. Only synthetic business data should be used unless the sponsor approves another source.

