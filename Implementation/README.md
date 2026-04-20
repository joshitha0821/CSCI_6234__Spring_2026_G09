# GenAI Integrated Conversational Business Intelligence Assistant

This project is a runnable Java full-stack app for conversational BI:

- Backend: Spring Boot REST API
- Frontend: static chat UI served by Spring Boot
- Local DB: H2 file database with seeded data
- SQL generation: Gemini API with live schema introspection context

## Key Features

1. Natural language question -> Gemini SQL generation -> SQL validation -> execution -> chart + summary
2. Dynamic schema introspection from the selected database connection
3. Runtime DB connection management (`local` + external JDBC connections)
4. Direct cache + semantic cache reuse
5. Feedback capture (approve/disapprove), audit logging, and chat history
6. Interactive Chart.js charts and CSV export

## Tech Stack

- Java 17
- Spring Boot 3.3.5
- Spring Web + Validation + JDBC
- H2 + PostgreSQL + MySQL JDBC drivers
- HTML/CSS/JavaScript frontend

## Run

1. Ensure Java 17+ and Maven are installed.
2. Set Gemini API key:

```powershell
$env:GEMINI_API_KEY="your_api_key_here"
```

3. Start:

```powershell
mvn spring-boot:run
```

4. Open:

- App UI: `http://localhost:8080`
- H2 console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:file:./localdb/genai_bi`
  - User: `sa`
  - Password: *(empty)*

Seed files:
- `src/main/resources/schema.sql`
- `src/main/resources/data.sql`

## Main APIs

- `POST /api/chat/ask`
- `GET /api/chat/history?userId=<id>&limit=20`
- `GET /api/chat/stats?userId=<id>`
- `GET /api/chat/suggestions?limit=8`
- `GET /api/chat/export/{cacheKey}`
- `POST /api/feedback`
- `GET /api/audit?limit=20`
- `GET /api/connections`
- `GET /api/connections/active`
- `POST /api/connections`
- `POST /api/connections/{connectionId}/activate`
- `GET /api/connections/{connectionId}/schema`
- `GET /api/connections/schema/prompt`

## Ask Example

```json
POST /api/chat/ask
{
  "userId": "demo-user",
  "connectionId": "local",
  "question": "What were top 3 regions by sales in the last 4 months?"
}
```

## Training Upload Example

- `examples/training-data-upload-example.md`
- `examples/external-db-connection-example.json` (for `POST /api/connections`)
