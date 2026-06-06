# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Commands

```bash
# Build
mvn clean package

# Run (requires Docker running and application-local.properties)
mvn spring-boot:run

# Run tests
mvn test

# Docker management
docker compose up -d       # Start SQL Server + Neo4j
docker compose down        # Stop containers (keep data)
docker compose down -v     # Stop and delete all data
docker ps                  # Check running containers
```

## Architecture Overview

### Tech Stack
- Java 21, Spring Boot 3.5.14, Maven
- **Primary DB:** SQL Server 2022 via Spring Data JPA
- **Graph DB:** Neo4j via Spring Data Neo4j (for paper-keyword network visualization)
- **Auth:** Spring Security + JWT (jjwt 0.12.3), stateless sessions, BCrypt password hashing
- **Email:** Spring Boot Mail (SMTP) for password reset
- **API Docs:** SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`)

### Dual Database Design
The project uses two databases simultaneously with explicit package separation to avoid conflicts:

- **JPA (`repository.jpa`, `entity.jpa`):** Users, roles, sessions, papers, journals, authors, keywords, bookmarks, follows, research fields, API sources, sync logs
- **Neo4j (`repository.neo4j`, `entity.neo4j`):** Paper and keyword nodes for graph visualization
- `JpaConfig` and `Neo4jConfig` limit component scanning to their respective packages — **never mix JPA annotations with Neo4j entities or vice versa**

### Package Structure

```
com.sra.journal_tracking
├── config/          # JPA, Neo4j, Web (CORS), OpenAPI/Swagger configs
├── controller/      # REST controllers (Auth, User, Bookmark, Follow, Graph, Trend, Test)
├── service/         # Service interfaces
│   └── impl/        # Service implementations
├── repository/
│   ├── jpa/         # Spring Data JPA repositories (SQL Server)
│   └── neo4j/       # Spring Data Neo4j repositories (graph DB)
├── entity/
│   ├── jpa/         # JPA entities (@Entity, @Table)
│   └── neo4j/       # Neo4j nodes (@Node)
├── dto/             # DTOs organized by domain: auth, bookmark, follow, user, response
├── exception/       # AppException, ErrorCode enum, GlobalExceptionHandler
├── security/        # JWT filter, token provider, user details, security config
└── test/            # DatabaseTestRunner (dev/debug utility)
```

### Security Model

- **Stateless JWT:** Every request is authenticated via `JwtAuthenticationFilter` (extends `OncePerRequestFilter`)
- **Session tracking:** `USER_SESSION` table stores SHA-256 hash of JWT — logout deletes the hash, making the token invalid even if not expired
- **Roles:** `ADMIN`, `RESEARCHER`, `ACADEMIC_USER` (default for new registrations)
- **Public endpoints:** `/api/auth/**`, `/api/trends/**`, `/api/test/**`, `/api/public/**`, Swagger UI paths
- **Method security:** Admin-only endpoints use `@PreAuthorize("hasRole('ADMIN')")`; user upgrade uses `@PreAuthorize("hasRole('ACADEMIC_USER')")`
- **Username = Email:** `CustomUserDetails.getUsername()` returns the user's email — code that calls `authentication.getName()` receives an email, not a username

### Error Handling Pattern

1. Define error codes in `ErrorCode` enum (HTTP status + message)
2. Throw `AppException(ErrorCode.XYZ)` from services
3. `GlobalExceptionHandler` (@RestControllerAdvice) catches all exceptions and returns `ErrorResponse` with consistent JSON structure
4. Do NOT throw `ResourceNotFoundException` in new code — it's a legacy handler; use `AppException(ErrorCode.RESOURCE_NOT_FOUND)` instead

### Current User Pattern

Services that need the current user obtain it from `SecurityContextHolder`:

```java
String email = SecurityContextHolder.getContext().getAuthentication().getName();
User user = userRepository.findByEmail(email)
    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
```

### Feature Limits

- `ACADEMIC_USER` role: max 20 bookmarks, max 20 follows (enforced in `BookmarkServiceImpl` and `FollowServiceImpl`)
- `RESEARCHER` and `ADMIN` roles have no limits

### Data Model Key Relationships

- `User` → `Role` (ManyToOne, EAGER)
- `ResearchPaper` → `ApiSource`, `Journal`, `ResearchField` (ManyToOne, LAZY)
- `Bookmark` / `Follow` → `User` (ManyToOne, LAZY) — polymorphic: `Type` + `TargetID` columns
- `ResearchField` → self-referencing `ParentFieldID` for hierarchy
- `UserSession` → `User` (ManyToOne, LAZY) — stores `TokenHash` for logout invalidation

### Configuration Files

- `application.properties` — references `.env` variables via `${VARIABLE_NAME}` placeholders
- `application-local.properties` — developer-specific overrides (gitignored)
- `.env` — secrets and database credentials (gitignored)
- Neo4j runs at `bolt://localhost:7687`; SQL Server at `localhost:1434`

### Git Conventions

- Branch naming: `feature/<name>`, `fix/<name>`
- Base branch: `develop` (not `main`)
- Commit prefixes: `feat:`, `fix:`, `refactor:`, `docs:`, `style:`
- Never commit: `.env`, `application-local.properties`, `application-secret.properties`
