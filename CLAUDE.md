# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Start SQL Server via Docker (required before running the app)
docker compose up -d

# Run the application
mvn spring-boot:run

# Build JAR (without running)
mvn clean package

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=JournalTrackingApplicationTests

# Run a single test method
mvn test -Dtest=JournalTrackingApplicationTests#methodName
```

Backend runs on `http://localhost:8080`. Swagger UI at `/swagger-ui.html`.

## Architecture Overview

**Package:** `com.sra.journal_tracking` (Spring Boot 3.5.14, Java 21, Maven)

### Dual-Database Design (JPA + Neo4j)

The project uses **two separate databases** with explicit package isolation to avoid scanning conflicts:

| Database | Config | Entity Package | Repository Package |
|----------|--------|---------------|--------------------|
| **SQL Server** (JPA) | `JpaConfig.java` | `entity.jpa.*` | `repository.jpa.*` |
| **Neo4j** (graph) | `Neo4jConfig.java` | `entity.neo4j.*` | `repository.neo4j.*` |

- `JpaConfig` and `Neo4jConfig` each restrict Spring Data scanning (`@EnableJpaRepositories`, `@EnableNeo4jRepositories`) to their respective packages to prevent conflicts.
- SQL Server stores structured data (users, papers, journals, authors, keywords, sync logs).
- Neo4j stores graph representations of papers and keywords for network visualization (`GraphService` queries nodes/edges).

### Security & Auth Flow

- **JWT stateless authentication** with Spring Security (`SecurityConfig.java`).
- `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter` — extracts JWT from `Authorization: Bearer <token>`, validates via `JwtTokenProvider`, sets `SecurityContext`.
- `CustomUserDetailsService` loads `User` by email from SQL Server, wraps into `CustomUserDetails`.
- User sessions are tracked in `UserSession` table (token hash stored, not raw token). Logout deletes the session row.
- BCrypt password encoding.
- CORS is wide open (`*`) — meant for dev; tighten for production.

**Public endpoints** (no auth required):
- `/api/auth/**` (register, login)
- `/api/test/**`
- `/api/public/**`
- `/swagger-ui/**`, `/v3/api-docs/**`

Protected endpoints require a valid JWT. Admin-only endpoints use `@PreAuthorize("hasRole('ADMIN')")`.

### Role System & Usage Limits

Three roles stored in the `ROLE` table:
- **ACADEMIC_USER** — default tier. Has monthly search/view limits (configurable in `SystemConfig` table via keys `academic_monthly_search_limit` and `academic_monthly_view_limit`). Can upgrade to RESEARCHER via `/api/users/me/upgrade`.
- **RESEARCHER** — unlimited access + advanced filter.
- **ADMIN** — user management + manual data sync triggers.

Usage tracking: `UserUsage` table stores per-month counts. `PaperSearchServiceImpl.checkAndIncrementUsage()` checks limits before each search/view operation. Throws `UsageLimitExceededException` when limit is hit.

### API Endpoints Summary

| Endpoint | Auth | Description |
|----------|------|-------------|
| `POST /api/auth/register` | No | Register + auto-login |
| `POST /api/auth/login` | No | Login, returns JWT |
| `POST /api/auth/logout` | No | Invalidate token |
| `GET /api/users/me` | Yes | Current user profile |
| `PUT /api/users/me` | Yes | Update profile |
| `PUT /api/users/me/password` | Yes | Change password |
| `POST /api/users/me/upgrade` | Yes (ACADEMIC_USER) | Upgrade to RESEARCHER |
| `GET /api/users` | Yes (ADMIN) | List all users |
| `GET /api/users/{id}` | Yes (ADMIN) | Get user by ID |
| `PUT /api/users/{id}/status` | Yes (ADMIN) | Enable/disable user |
| `PUT /api/users/{id}/role` | Yes (ADMIN) | Change role |
| `GET /api/v1/papers/search` | Yes | Full-text search |
| `GET /api/v1/papers/search/author` | Yes | Search by author name |
| `GET /api/v1/papers/search/journal` | Yes | Search by journal ID |
| `GET /api/v1/papers/filter/advanced` | Yes (RESEARCHER+) | Advanced filter |
| `GET /api/v1/papers/{paperId}` | Yes | Paper detail |
| `GET /api/v1/papers/usage` | Yes | Remaining usage |
| `GET /api/graphs/paper/{paperId}` | No (unsecured) | Neo4j keyword graph |
| `POST /api/v1/admin/sync/openalex` | Yes (ADMIN) | Manual data sync |

### Data Sync Pipeline

`DataSyncServiceImpl` fetches papers from two external APIs:
- **OpenAlex** (`api.openalex.org`) — `syncFromOpenAlex()` with pagination. Reconstructs abstracts from inverted index.
- **Semantic Scholar** (`api.semanticscholar.org`) — `syncFromSemanticScholar()`.

Both methods deduplicate by DOI. Scheduled sync runs daily at 2 AM via `@Scheduled(cron = "0 0 2 * * ?")`.

### Exception Handling

`GlobalExceptionHandler` is the centralized `@RestControllerAdvice`. The project uses a custom `AppException` wrapping an `ErrorCode` enum that carries both HTTP status and message. All exception handlers return `ErrorResponse` DTO (status code + message + optional field errors).

### Key Config Properties (from `application.properties`)

All sensitive values are externalized to a `.env` file via `spring.config.import=optional:file:.env[.properties]`. Local overrides go in `application-local.properties` (gitignored). The `.env` must define: `DATABASE_NAME`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`, and optionally `JWT_SECRET`, `FRONTEND_URL`, SMTP credentials.

### Naming Convention Notes

- Database uses `snake_case` column and table names. JPA `PhysicalNamingStrategyStandardImpl` preserves exact column names from `@Column(name = "...")` annotations (no auto snake-case conversion).
- All entity primary keys use `UUID` with `GenerationType.UUID`.
- DTOs follow the pattern: `XxxRequestDTO`, `XxxResponseDTO`, `XxxDetailResponseDTO`.
