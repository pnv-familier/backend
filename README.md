# Familier — Backend

**Familier** helps modern families stay emotionally connected through an AI companion that listens, remembers, and gently encourages real human interactions — not replaces them.

---

## What it does (Business View)

- **AI Family Companion** — A conversational AI that knows the user's family context (relationships, ongoing conflicts, milestones) and responds with empathy.
- **Long-term Memory** — Learns from every conversation. Extracts facts and summaries, stores them, and recalls them semantically when relevant (RAG).
- **Family Nudges** — When the AI senses loneliness, stress, or a big win, it quietly notifies family members so they can reach out.
- **Task Coordination** — Users can ask the AI to create care tasks for specific family members.

---

## Architecture

| Service | Purpose | Port |
|---|---|---|
| `gateway` | API Gateway — entry point for all clients | 8080 |
| `core_service` | User auth, family management, profiles (MySQL) | 8081 |
| `ai_service` | AI chat, memory, RAG, summarization (MongoDB + Qdrant) | 8082 |

**Infrastructure:**
- MySQL — core relational data (users, families, tasks)
- MongoDB — AI session data, user context, extracted facts
- Qdrant — vector embeddings for semantic memory (RAG)
- Redis — session caching
- Google Gemini — LLM for chat + summarization + embeddings

---

## Running Locally

### Prerequisites
- Java 17+
- Maven
- Docker & Docker Compose

### 1. Set environment variables

Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
```

Key variables:
```
GEMINI_API_KEY=your_google_gemini_api_key
JWT_SECRET=your_jwt_secret
INTERNAL_SHARED_SECRET=your_internal_secret
```

### 2. Start infrastructure

```bash
docker compose up -d mysql mongodb
```

Start Qdrant (required for AI memory):
```bash
docker run -d -p 6333:6333 -p 6334:6334 -v "${PWD}\qdrant_storage:/qdrant/storage" qdrant/qdrant
```

### 3. Run services

```bash
# Core service
cd core_service && mvn spring-boot:run

# AI service (separate terminal)
cd ai_service && mvn spring-boot:run

# Gateway (separate terminal)
cd gateway && mvn spring-boot:run
```

Or run everything with Docker:
```bash
docker compose up --build
```

### 4. Verify

| Check | URL |
|---|---|
| Gateway health | http://localhost:8080/actuator/health |
| Core service health | http://localhost:8081/actuator/health |
| AI service health | http://localhost:8082/actuator/health |
| Qdrant dashboard | http://localhost:6333/dashboard |

---

## Database Migrations

MongoDB field migration scripts are stored in:
```
ai_service/src/main/resources/db/migration/
```

Run them in order before deploying a new build to a cloud environment:
```bash
mongosh <your_mongodb_uri> --file V1__rename_user_context_global_context.js
mongosh <your_mongodb_uri> --file V2__rename_fact_indexed_status.js
mongosh <your_mongodb_uri> --file V3__rename_session_summary_indexed_status.js
```