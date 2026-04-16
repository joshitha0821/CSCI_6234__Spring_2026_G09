# GenAI Integrated Conversational Business Intelligence Assistant

This repository now contains a runnable full-stack baseline implementation in Java:

- Backend: Spring Boot REST API
- Frontend: Static web app (chat + chart + admin panel) served by Spring Boot
- Demo BI Data Source: H2 in-memory business database (`sales`, `inventory`)

## Implemented Features

1. Upload training documents (`/api/training/documents`)
2. Maintain training documents and question-SQL examples
3. O(1) direct cache lookup by normalized-question hash
4. Cache refresh/clear APIs and scheduled stale cleanup
5. Semantic similarity match using in-memory vector search (cosine on token vectors)
6. Feedback API (approve/disapprove) tied to cache trust score
7. Audit logging for interactions and admin actions
8. NL question -> SQL generation -> SQL validation -> execution -> chart + summary
9. Chat interface with SQL, summary, chart, data table, feedback, CSV export, and chart toggle
10. Per-user question history and session stats (cache hit rate, average latency)
11. Explainability trail and follow-up question suggestions in each response
12. Quick suggested questions panel in UI
13. Saved prompt chips (client-side favorites), slash-focus shortcut, and autosizing composer
14. Search/filter on history and audit logs
15. Tabbed side workspace (Session, Training, System) for cleaner navigation
16. Interactive Chart.js visualizations with tooltips, responsive scaling, and axis formatting
17. Visible per-answer feedback state in UI (not rated, approved, disapproved)

## Tech Stack

- Java 17
- Spring Boot 3.3.5
- Spring Web + Validation + JDBC
- H2 Database
- Plain HTML/CSS/JS frontend

## Run

1. Ensure Java 17+ and Maven are installed.
2. Start the app:

```powershell
mvn spring-boot:run
```

3. Open:

- UI: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:bizdb`
  - User: `sa`
  - Password: *(empty)*

## Main API Endpoints

- `POST /api/chat/ask`
- `GET /api/chat/history?userId=<id>&limit=20`
- `GET /api/chat/stats?userId=<id>`
- `GET /api/chat/suggestions?limit=8`
- `GET /api/chat/export/{cacheKey}`
- `POST /api/feedback`
- `GET /api/feedback`
- `POST /api/training/documents` (multipart)
- `GET /api/training/documents`
- `POST /api/training/examples`
- `GET /api/training/examples`
- `GET /api/audit?limit=20`
- `POST /api/admin/cache/refresh`
- `POST /api/admin/cache/clear`

## Request Examples

### Ask Question

```json
POST /api/chat/ask
{
  "userId": "demo-user",
  "question": "What were last month sales by region?"
}
```

### Submit Feedback

```json
POST /api/feedback
{
  "cacheKey": "<cache-key-from-answer>",
  "vote": "APPROVE",
  "userId": "demo-user",
  "comment": "Looks good"
}
```

## Notes

- This is a production-style scaffold with in-memory stores for cache, vectors, feedback, and audit.
- For production deployment, replace in-memory services with persistent stores (Redis, pgvector/Weaviate, RDBMS tables, managed LLM integration, auth/RBAC, and stricter SQL governance).

## Training File Example

- Example upload file: `examples/training-data-upload-example.md`
