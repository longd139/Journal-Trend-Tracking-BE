# SOFTWARE REQUIREMENTS SPECIFICATION (SRS)

## for SCITRACK — AI-Powered Academic Research Analytics

> **Version:** 1.0  
> **Release Date:** July 28, 2026  
> **Reference Standard:** IEEE 830-1998 — IEEE Recommended Practice for Software Requirements Specifications

---

## TABLE OF CONTENTS

1. [Introduction](#1-introduction)
   - 1.1 Purpose
   - 1.2 Scope
   - 1.3 Definitions & Acronyms
   - 1.4 References
   - 1.5 Document Overview
2. [General Description](#2-general-description)
   - 2.1 Product Perspective
   - 2.2 Product Functions
   - 2.3 User Characteristics
   - 2.4 General Constraints
   - 2.5 Assumptions & Dependencies
3. [Specific Requirements](#3-specific-requirements)
   - 3.1 Functional Requirements
   - 3.2 External Interface Requirements
   - 3.3 Non-Functional Requirements
   - 3.4 Design Constraints
   - 3.5 Business Rules
4. [Appendices](#4-appendices)
   - 4.1 Use Case Diagram
   - 4.2 Data Flow Diagrams
   - 4.3 Entity-Relationship Diagram (ERD)
   - 4.4 Requirements Traceability Matrix

---

## 1. INTRODUCTION

### 1.1 Purpose

This document is the **Software Requirements Specification (SRS)** for the **SCITRACK** system — an AI-Powered Academic Research Analytics platform.

**Primary Objectives:**
- Provide a complete and precise description of all functional and non-functional requirements of the SCITRACK system
- Serve as a baseline for system testing and validation
- Provide handover documentation for the operations and maintenance team
- Fulfill academic requirements for the graduation capstone project

**Intended Audience:**
- Graduation project evaluation committee
- Software development and testing team
- System administrators
- End users — researchers, students, and academics

### 1.2 Scope

**SCITRACK** is a full-stack system that enables:

1. **Intelligent search** of academic papers by keyword, author, and journal — using a dual-database architecture (SQL Server for structured data, Neo4j for keyword-paper graph relationships)
2. **Research trend analysis** — visual charts of publication trends, top authors, and high-quality journals
3. **Knowledge graph visualization** — exploring the keyword-paper network via Neo4j graph visualization
4. **AI-powered analysis** — paper summarization, methodology extraction, cross-paper comparative analysis
5. **Personal document management** — bookmarks, collections, reading history, follows, and reports
6. **System administration** — user management, external API data synchronization, system monitoring

**In Scope:**
- Complete backend and frontend system
- Integration with 4 external APIs (OpenAlex, Semantic Scholar, CORE, arXiv)
- Multi-language support (English + Vietnamese)
- Support for 3 user roles (Admin, Researcher, Academic User)

**Out of Scope:**
- Mobile application — the system is web-only
- Payment / billing
- Social media integration beyond Google OAuth

### 1.3 Definitions & Acronyms

| Acronym | Full Definition | Description |
|---------|-----------------|-------------|
| **API** | Application Programming Interface | Interface for software communication |
| **BE** | Backend | Server-side business logic (Spring Boot) |
| **CRUD** | Create, Read, Update, Delete | Basic data operations |
| **CSV** | Comma-Separated Values | Tabular data file format |
| **DOI** | Digital Object Identifier | Unique identifier for academic papers |
| **DTO** | Data Transfer Object | Object for transferring data between layers |
| **ERD** | Entity-Relationship Diagram | Visual representation of database entities |
| **FE** | Frontend | User interface (React) |
| **i18n** | Internationalization | Multi-language support |
| **JPA** | Jakarta Persistence API | Object-relational mapping API for Java |
| **JSON** | JavaScript Object Notation | Data interchange format |
| **JWT** | JSON Web Token | Stateless authentication token |
| **OA** | Open Access | Freely accessible research papers |
| **ORM** | Object-Relational Mapping | Technique for mapping objects to database tables |
| **PDF** | Portable Document Format | Document format |
| **RBAC** | Role-Based Access Control | Access control based on user roles |
| **REST** | Representational State Transfer | Web API architecture style |
| **RPM** | Requests Per Minute | Allowed number of requests per minute |
| **SCImago** | SCImago Journal Rank | Scientific journal ranking system |
| **SMTP** | Simple Mail Transfer Protocol | Email sending protocol |
| **SSE** | Server-Sent Events | Real-time server-to-client push technology |
| **SRS** | Software Requirements Specification | Formal document describing software requirements |
| **TTL** | Time To Live | Cache data lifetime |
| **UI** | User Interface | Visual interface for users |
| **UUID** | Universally Unique Identifier | Globally unique identifier |

### 1.4 References

| # | Document | Description |
|---|----------|-------------|
| [1] | IEEE 830-1998 | IEEE Recommended Practice for Software Requirements Specifications |
| [2] | CLAUDE.md (root) | SCITRACK system architecture overview |
| [3] | SYSTEM-DOCUMENTATION.md | Comprehensive system documentation (architecture, technology, structure) |
| [4] | SYSTEM-OVERVIEW.md | System overview in presentation format |
| [5] | schema.sql | SQL Server database schema (36 tables) |
| [6] | SCITRACK_Presentation_Summary.md | Presentation summary document |
| [7] | RESEARCH-GAP-EXPLORER-PLAN.md | Research Gap Explorer implementation plan |
| [8] | OpenAlex API Documentation | https://docs.openalex.org/ |
| [9] | Semantic Scholar API Documentation | https://api.semanticscholar.org/api-docs/ |
| [10] | Spring Boot 3.5 Reference | https://docs.spring.io/spring-boot/docs/3.5.x/reference/ |
| [11] | React 18 Documentation | https://react.dev/ |

### 1.5 Document Overview

This document is organized according to IEEE 830-1998:

- **Chapter 1 — Introduction**: Purpose, scope, definitions, and references
- **Chapter 2 — General Description**: High-level system perspective, main functions, users, and constraints
- **Chapter 3 — Specific Requirements**: Detailed functional and non-functional requirements — the core of the SRS
- **Chapter 4 — Appendices**: Use Case diagrams, Data Flow diagrams, ERD, and requirements traceability matrix

---

## 2. GENERAL DESCRIPTION

### 2.1 Product Perspective

#### 2.1.1 System Overview

SCITRACK is a web-based system deployed with a **3-tier architecture**:

```
┌────────────────────────────────────────────────────────────────────┐
│                   PRESENTATION TIER                                 │
│  React 18 + Vite 6 + Tailwind CSS v4 + Radix UI + Recharts         │
│  Port: 5173 (development)                                          │
└───────────────────────────────┬────────────────────────────────────┘
                                │ HTTP/REST + SSE (JSON)
┌───────────────────────────────▼────────────────────────────────────┐
│                    APPLICATION TIER                                 │
│  Spring Boot 3.5.14 + Java 21 + Maven                              │
│  Port: 8080                                                        │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────────────┐    │
│  │ Controllers │ │  Services    │ │ Security (JWT + RBAC)    │    │
│  │ (38 files)  │ │  (70+ files) │ │ Bucket4j Rate Limiting   │    │
│  └─────────────┘ └──────────────┘ └──────────────────────────┘    │
└───────┬───────────────────────────────┬────────────────────────────┘
        │                               │
┌───────▼───────────┐   ┌───────────────▼──────────────────────────┐
│    DATA TIER      │   │         EXTERNAL SERVICES                │
│                   │   │                                          │
│ ┌───────────────┐ │   │  • OpenAlex API                          │
│ │ SQL Server    │ │   │  • Semantic Scholar API                  │
│ │ (JPA/Hibernate│ │   │  • CORE API                              │
│ │  36 tables)   │ │   │  • arXiv API                             │
│ │               │ │   │  • DeepSeek AI (ai-box.vn)               │
│ │ Users, Papers,│ │   │  • Google OAuth2                         │
│ │ Authors,      │ │   │  • Cloudinary (image hosting)            │
│ │ Journals,     │ │   │  • Gmail SMTP                            │
│ │ Bookmarks,... │ │   │                                          │
│ └───────────────┘ │   │                                          │
│                   │   │                                          │
│ ┌───────────────┐ │   │                                          │
│ │ Neo4j         │ │   │                                          │
│ │ (Graph DB)    │ │   │                                          │
│ │ AuraDB Cloud  │ │   │                                          │
│ │               │ │   │                                          │
│ │ Paper nodes,  │ │   │                                          │
│ │ Keyword nodes,│ │   │                                          │
│ │ HAS_KEYWORD   │ │   │                                          │
│ │ relationships │ │   │                                          │
│ └───────────────┘ │   │                                          │
└───────────────────┴───┴──────────────────────────────────────────┘
```

#### 2.1.2 Dual-Database Architecture

The system uses **two independent databases**, each serving a distinct purpose:

| Database | Technology | Purpose | Data Stored |
|----------|-----------|---------|-------------|
| **SQL Server** | JPA / Hibernate | Structured data, ACID transactions, complex JOINs, statistical reporting | Users, papers, authors, journals, keywords, bookmarks, follows, notifications, sync logs, audit logs, system config (36 tables) |
| **Neo4j (AuraDB)** | Spring Data Neo4j | Graph traversal for keyword-paper network, 10-100x faster than SQL JOINs for graph queries | Paper nodes, Keyword nodes, `HAS_KEYWORD` relationships |

**Synchronization mechanism:** When data is crawled from external APIs (OpenAlex, Semantic Scholar), it is written simultaneously to both SQL Server and Neo4j via `DataSyncServiceImpl` and `GraphService`.

**Package Isolation:** Packages are clearly separated to prevent Spring Data scanning conflicts:
- `entity.jpa.*` and `repository.jpa.*` for SQL Server
- `entity.neo4j.*` and `repository.neo4j.*` for Neo4j

#### 2.1.3 System Block Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                       WEB BROWSER                             │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌─────────────────┐  │
│  │Landing  │ │ Auth     │ │ Search   │ │ Admin           │  │
│  │Page     │ │ Pages    │ │ Pages    │ │ Dashboard       │  │
│  └─────────┘ └──────────┘ └──────────┘ └─────────────────┘  │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTPS (JWT Bearer Token)
┌──────────────────────────▼───────────────────────────────────┐
│                    SPRING BOOT API GATEWAY                    │
│  ┌──────────────┐ ┌─────────────┐ ┌──────────────────────┐  │
│  │ JWT Filter   │ │ Rate        │ │ CORS                 │  │
│  │ (stateless)  │ │ Limiter     │ │ Configuration        │  │
│  └──────────────┘ └─────────────┘ └──────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  BUSINESS LOGIC LAYER                 │   │
│  │                                                      │   │
│  │  ┌──────────────────┐  ┌────────────────────────┐    │   │
│  │  │ PaperSearch      │  │ DataSync               │    │   │
│  │  │ Orchestrator     │  │ Service                │    │   │
│  │  │ (Neo4j→SQL→API)  │  │ (OpenAlex+Scholar)     │    │   │
│  │  └──────────────────┘  └────────────────────────┘    │   │
│  │                                                      │   │
│  │  ┌──────────────────┐  ┌────────────────────────┐    │   │
│  │  │ AI Summarization │  │ Notification           │    │   │
│  │  │ Service          │  │ Service (SSE Push)     │    │   │
│  │  │ (DeepSeek)       │  │                        │    │   │
│  │  └──────────────────┘  └────────────────────────┘    │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌─────────────────────┐  ┌─────────────────────────────┐   │
│  │ DATA ACCESS LAYER   │  │ EXTERNAL INTEGRATION LAYER  │   │
│  │                     │  │                             │   │
│  │ JPA Repos (29)      │  │ OpenAlex Client             │   │
│  │ Neo4j Repos (3)     │  │ SemanticScholar Client      │   │
│  │ Caffeine Cache      │  │ DeepSeek AI Client          │   │
│  └─────────────────────┘  │ arXiv Client                │   │
│                            │ CORE Client                 │   │
│                            │ Email Service (SMTP)        │   │
│                            │ Cloudinary Service          │   │
│                            └─────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 Product Functions

The following is a summary of the system's main functions. Detailed specifications are provided in Chapter 3.

#### A. User-Facing Functions

| # | Function Group | Summary Description |
|---|---------------|---------------------|
| **F1** | **Authentication & Authorization** | Registration, login (email/password + Google OAuth), forgot password, JWT stateless auth, RBAC with 3 roles |
| **F2** | **Intelligent Search** | Unified search (keyword + author + journal), Neo4j graph search, OpenAlex API fallback, advanced filter, sort |
| **F3** | **Graph Visualization** | Neo4j keyword-paper graph (vis-network), click interaction, graph explorer with depth expansion |
| **F4** | **AI-Powered Analysis** | Paper summarization (DeepSeek), methodology extraction, batch analysis (cross-paper comparative insight) |
| **F5** | **Document Management** | Bookmarks + collections, follows (author/journal/topic/keyword), reading history |
| **F6** | **Reports & Analytics** | Keyword trend report, author impact report, journal quality report, PDF/CSV export |
| **F7** | **Citation Export** | Export citations in BibTeX, RIS, APA (7th), MLA (9th) — single or bulk |
| **F8** | **Real-Time Notifications** | SSE push + REST fallback, 4 notification types (NEW_PAPER, TREND_ALERT, SYSTEM, UPGRADE_PROMPT) |
| **F9** | **Research Idea Analysis** | Analyze research ideas (gap analysis, literature review), extract keywords |
| **F10** | **User Profile** | Update profile, avatar (Cloudinary), change password, account upgrade |
| **F11** | **Multi-Language** | English + Vietnamese UI (i18n, 13 namespaces) |

#### B. Admin-Facing Functions

| # | Function Group | Summary Description |
|---|---------------|---------------------|
| **F12** | **User Management** | User list, search, enable/disable, role change |
| **F13** | **Data Synchronization** | Manual sync (OpenAlex, Semantic Scholar, CORE, arXiv), bulk sync with progress tracking |
| **F14** | **System Monitoring** | Admin dashboard (stats, charts), database stats (SQL + Neo4j), audit logs |
| **F15** | **System Configuration** | Manage system config (rate limit, usage limit, auto-sync toggle) |
| **F16** | **PDF Request Management** | Review PDF requests from users (find candidates, upload, fulfill, reject) |

### 2.3 User Characteristics

The system serves **3 user types** (personas) with different characteristics and needs:

#### 2.3.1 Academic User (Students, PhD Candidates)

| Characteristic | Description |
|----------------|-------------|
| **Technical level** | Basic to intermediate — familiar with web browsers and academic search |
| **Usage frequency** | A few times per week; peaks during thesis/dissertation periods |
| **Primary needs** | Find papers for research, read AI summaries, save bookmarks, export citations |
| **Limitations** | 30 searches/month (configurable), no analytics, no graph explorer |

#### 2.3.2 Researcher (Lecturers, Scientists)

| Characteristic | Description |
|----------------|-------------|
| **Technical level** | Intermediate to advanced — familiar with research tools and citation management |
| **Usage frequency** | Daily |
| **Primary needs** | Unlimited search, advanced filter, trend analysis, graph explorer, reports |
| **Privileges** | Unlimited searches, access to analytics, AI batch analysis |

#### 2.3.3 Admin (System Administrator)

| Characteristic | Description |
|----------------|-------------|
| **Technical level** | Advanced — understands systems, databases, and APIs |
| **Usage frequency** | Daily (monitoring) + on-demand (incident response) |
| **Primary needs** | User management, data sync, system monitoring, PDF request handling |
| **Privileges** | Full access to all functions, audit log viewing, configuration management |

### 2.4 General Constraints

| # | Constraint | Description |
|---|-----------|-------------|
| **C1** | **Web Platform** | System runs only in web browsers (desktop + mobile responsive), no native app |
| **C2** | **Programming Languages** | Backend: Java 21; Frontend: JavaScript (React 18); Database: SQL (SQL Server), Cypher (Neo4j) |
| **C3** | **Database** | SQL Server 2022 (required), Neo4j AuraDB Cloud or Neo4j Local Docker |
| **C4** | **Authentication** | JWT stateless, no server-side sessions |
| **C5** | **External APIs** | Dependent on OpenAlex API key (required since February 2026) |
| **C6** | **Security** | All endpoints require JWT (except auth, health, swagger), rate limiting enforced |
| **C7** | **Environment** | Development: Docker + localhost; Production: AWS RDS + Railway |
| **C8** | **Browser Compatibility** | Chrome, Firefox, Edge, Safari (latest 2 versions) |

### 2.5 Assumptions & Dependencies

#### 2.5.1 Assumptions

| # | Assumption |
|---|-----------|
| **A1** | Users have a stable Internet connection to use the system |
| **A2** | Users have a valid email address for registration and notifications |
| **A3** | OpenAlex API and Semantic Scholar API maintain their current response format |
| **A4** | DeepSeek AI service (ai-box.vn) maintains ≥ 99% availability |
| **A5** | Data crawled from OpenAlex meets quality standards (quality score ≥ 40/100) |
| **A6** | Admins have basic knowledge of system and database administration |

#### 2.5.2 Dependencies

| # | Dependency | Description | Impact Level |
|---|-----------|-------------|--------------|
| **D1** | **OpenAlex API** | Primary paper data source (240M+ works) | **High** — if OpenAlex is unavailable, search fallback will fail |
| **D2** | **DeepSeek AI (ai-box.vn)** | AI summarization, methodology, batch analysis | **Medium** — graceful fallback available (AI = null), does not affect core functionality |
| **D3** | **Neo4j AuraDB** | Graph database for keyword-paper network | **High** — if Neo4j is unavailable, graph search and visualization are affected |
| **D4** | **SQL Server** | Primary database for all structured data | **Very High** — entire system depends on SQL Server |
| **D5** | **Cloudinary** | Image hosting service (avatars, backgrounds) | **Low** — default images used if Cloudinary is unavailable |
| **D6** | **Gmail SMTP** | Email delivery service (verification, password reset) | **Medium** — affects email verification flow |
| **D7** | **Semantic Scholar API** | Secondary paper data source | **Low** — OpenAlex is the primary source |
| **D8** | **Google OAuth2** | Google sign-in | **Low** — email/password login still works |

---

## 3. SPECIFIC REQUIREMENTS

### 3.1 Functional Requirements

---

#### FR-AUTH: Authentication & Authorization

##### FR-AUTH-001: User Registration
- **Description:** New users can create an account using email and password
- **Input:** Email (valid), Password (min 8 characters), FullName (≤ 200 characters), Institution (optional, ≤ 300 characters)
- **Processing:**
  1. Validate that email does not already exist in the system
  2. Hash password using BCrypt
  3. Create User with Role = ACADEMIC_USER
  4. Send verification email (async, non-blocking)
  5. Auto-login after registration → return JWT
- **Output:** `{ accessToken, refreshToken, role, user }`
- **Endpoint:** `POST /api/auth/register`
- **Authentication:** Not required (public)
- **Priority:** 🔴 High (Must-have)

##### FR-AUTH-002: Login
- **Description:** Users log in using email and password
- **Input:** Email, Password
- **Processing:**
  1. Find user by email
  2. Authenticate password using BCrypt
  3. Check that account is active (isActive)
  4. Generate JWT access token (24h TTL) + refresh token (7d TTL, 64-byte random)
  5. Save UserSession
  6. Update LastLoginAt
- **Output:** `{ accessToken, refreshToken, role, user }`
- **Endpoint:** `POST /api/auth/login`
- **Authentication:** Not required (public)
- **Priority:** 🔴 High (Must-have)

##### FR-AUTH-003: Google OAuth Login
- **Description:** Users can log in using their Google account
- **Input:** Google ID Token (from @react-oauth/google)
- **Processing:**
  1. Verify Google ID Token
  2. If email does not exist → create new user with Role = RESEARCHER (3-day trial)
  3. If already exists → check trial expiry → auto-downgrade if expired
  4. Generate JWT
- **Output:** `{ accessToken, refreshToken, role, user }`
- **Endpoint:** `POST /api/auth/google`
- **Authentication:** Not required (public)
- **Priority:** 🟠 Medium (Should-have)

##### FR-AUTH-004: Forgot & Reset Password
- **Description:** Users can request a password reset via email
- **Input:** Email (for forgot-password); Token + New Password (for reset-password)
- **Processing:**
  1. Generate verification token (15 min TTL)
  2. Send email containing reset password link (HTML template)
  3. When user submits new password → validate token → hash new password → invalidate token
- **Output:** Success message
- **Endpoint:** `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`
- **Authentication:** Not required (public)
- **Priority:** 🔴 High (Must-have)

##### FR-AUTH-005: Refresh Token
- **Description:** Client uses refresh token to obtain a new access token when the current one expires
- **Input:** Refresh token (64-byte random)
- **Processing:**
  1. Hash refresh token → find UserSession
  2. Verify refresh token has not expired
  3. Invalidate old refresh token (rotation)
  4. Generate new access token + new refresh token
- **Output:** `{ accessToken, refreshToken }`
- **Endpoint:** `POST /api/auth/refresh-token`
- **Authentication:** Not required (public, uses refresh token for authentication)
- **Priority:** 🔴 High (Must-have)

##### FR-AUTH-006: Email Verification
- **Description:** Users verify their email address via a link sent in email
- **Input:** Verification token
- **Processing:** Validate token (24h TTL) → mark email as verified → invalidate token
- **Output:** Success message
- **Endpoint:** Handled through verification token flow
- **Authentication:** Not required (public)
- **Priority:** 🟠 Medium (Should-have)

##### FR-AUTH-007: Logout
- **Description:** Users log out, invalidating all tokens
- **Input:** Access token (from header)
- **Processing:** Delete UserSession → tokens are no longer valid
- **Output:** Success message
- **Endpoint:** `POST /api/auth/logout`
- **Authentication:** Not required (public, uses token to identify session)
- **Priority:** 🔴 High (Must-have)

---

#### FR-SEARCH: Search

##### FR-SEARCH-001: Keyword Paper Search
- **Description:** User enters a keyword; the system returns matching papers through the orchestrator search pipeline
- **Input:** Query string, Page (0-based), Size (default 50), Sort (optional: relevance/citations/title/date)
- **Processing (PaperSearchOrchestrator pipeline):**
  1. **Quota check:** Academic User — check current month's `UserUsage.searchCount`
  2. **Cache check (6h TTL):** If cache hit → return cached results
  3. **Neo4j graph search:** `MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)` → get paper IDs
  4. **SQL Server fetch:** Get full paper data using ID list from Neo4j
  5. **Neo4j IDs not in SQL:** Delete stale Neo4j nodes → fallback to OpenAlex
  6. **Complete Neo4j miss:** Fallback to OpenAlex API live search + trigger async background sync
  7. **Record history:** Save search keyword (hot keyword tracking) + user search history (async)
  8. **Cache results** (6h TTL)
- **Output:** `{ content: PaperDTO[], totalElements, totalPages, numberOfElements }` + keyword graph data + quick stats
- **Endpoint:** `GET /api/v1/papers/search`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🔴 High (Must-have)

##### FR-SEARCH-002: Author Search
- **Description:** Users search for authors with autocomplete and quick stats
- **Input:** Author name (partial/full name), Page, Size
- **Processing:** Search SQL Server `AUTHOR` table using LIKE query → enrich with h-index, citations, research focus from Neo4j
- **Output:** `{ content: AuthorDTO[], totalElements }` — each author with: hIndex, totalCitations, i10Index, worksCount, country, affiliation, research timeline, co-authors
- **Endpoint:** `GET /api/v1/papers/search/author`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🔴 High (Must-have)

##### FR-SEARCH-003: Journal Search
- **Description:** Users search for academic journals with suggestions and top journals
- **Input:** Journal name (partial/full name), Field (optional), Page, Size
- **Processing:** Search SQL Server `JOURNAL` table → enrich with impact factor, quartile from SCImago data
- **Output:** `{ content: JournalDTO[], totalElements }` — each journal with: ISSN, impactFactor, quartile (Q1-Q4), publisher, top papers
- **Endpoint:** `GET /api/v1/journals/search`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🔴 High (Must-have)

##### FR-SEARCH-004: Advanced Filter
- **Description:** Researcher users can filter papers with multiple criteria
- **Input:** Field, Year range (from-to), Journal quartile (Q1-Q4), Min citations, Open Access only, Article type
- **Processing:** SQL-based query with dynamic WHERE clauses
- **Output:** `{ content: PaperDTO[], totalElements }`
- **Endpoint:** `GET /api/v1/papers/filter/advanced`
- **Authentication:** Required (RESEARCHER)
- **Priority:** 🟠 Medium (Should-have)

##### FR-SEARCH-005: Paper Detail
- **Description:** Display detailed information for a paper with all metadata + AI analysis + citation + similar papers
- **Input:** Paper ID (UUID)
- **Processing:**
  1. Find paper in SQL Server
  2. Load authors, keywords, journal from JPA relationships
  3. If abstract exists → load AI summary (if not cached)
  4. Load similar papers (2 strategies: same field + Neo4j keyword overlap)
  5. Load graph data from Neo4j for keyword visualization
- **Output:** `PaperDetailResponseDTO` — title, abstract, AI summary (4 sections), methodology, authors, journal, keywords, citations, similar papers[], citation options, rating
- **Endpoint:** `GET /api/v1/papers/{paperId}`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🔴 High (Must-have)

---

#### FR-GRAPH: Graph & Visualization

##### FR-GRAPH-001: Keyword-Paper Graph
- **Description:** Display the relationship graph between keywords and papers using Neo4j + vis-network
- **Input:** Keyword text or Paper ID
- **Processing:**
  1. Neo4j Cypher query: `MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)` with keyword/paperID filter
  2. Return nodes (Paper, Keyword) + edges (HAS_KEYWORD with relevanceScore)
  3. Format data for vis-network (nodes[], edges[])
- **Output:** `{ nodes: GraphNodeDTO[], edges: GraphEdgeDTO[] }`
- **Endpoint:** `GET /api/graphs/paper/{paperId}`, `GET /api/graphs/keyword/{keyword}`
- **Authentication:** Not required (public)
- **Priority:** 🔴 High (Must-have)

##### FR-GRAPH-002: Graph Explorer with Depth Expansion
- **Description:** Users explore the keyword graph with depth expansion capability
- **Input:** Root keyword, Depth (1-3), Limit per level
- **Processing:**
  1. Start from keyword node → traverse HAS_KEYWORD → Paper nodes → reverse traverse → related Keyword nodes
  2. Repeat until desired depth is reached
  3. Fallback to OpenAlex if keyword is not in Neo4j
- **Output:** `{ nodes: GraphNodeDTO[], edges: GraphEdgeDTO[], fallbackSource }`
- **Endpoint:** `POST /api/graphs/explore`
- **Authentication:** Not required (public)
- **Priority:** 🟡 Low (Nice-to-have)

##### FR-GRAPH-003: Keyword Quick Stats
- **Description:** Display quick statistics for a keyword in the sidebar during search
- **Input:** Keyword text
- **Processing:** Aggregation queries in Neo4j: COUNT papers, SUM citations, COUNT authors, COUNT journals
- **Output:** `{ paperCount, citationCount, authorCount, journalCount, topJournals[] }`
- **Endpoint:** `GET /api/v1/keywords/stats`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟠 Medium (Should-have)

---

#### FR-AI: AI Analysis

##### FR-AI-001: AI Abstract Summarization
- **Description:** Use DeepSeek AI to summarize a paper's abstract into 2-3 structured sentences
- **Input:** Paper ID
- **Processing:**
  1. Get abstract from SQL Server (or PaperCache, or OpenAlex API)
  2. Call DeepSeek API with prompt: "Summarize this academic abstract in 2-3 sentences with 4 sections: Background, Methods, Results, Conclusion"
  3. Parse response into 4 sections
  4. Cache result (Caffeine, 1h TTL)
  5. If AI fails → aiSummary = null (do not throw exception)
- **Output:** `{ aiSummary (full text), aiSummarySections: { background, methods, results, conclusion } }`
- **Endpoint:** `GET /api/v1/ai/summarize/{paperId}`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟠 Medium (Should-have)

##### FR-AI-002: Methodology Extraction
- **Description:** AI classifies the paper's research methodology
- **Input:** Paper ID
- **Processing:**
  1. Get abstract
  2. Call DeepSeek: "Classify research methodology into: quantitative, qualitative, mixed-methods, RCT, systematic-review, meta-analysis, case-study, survey, experimental, observational, simulation, theoretical"
  3. Cache result (1h TTL)
- **Output:** `{ methodology: String }` — one of 12 categories
- **Endpoint:** `GET /api/v1/ai/methodology/{paperId}`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟡 Low (Nice-to-have)

##### FR-AI-003: Batch Analysis
- **Description:** Cross-compare up to 10 papers, generating comparative insight
- **Input:** `[paperId1, paperId2, ..., paperId10]`
- **Processing:**
  1. For each paper → summarize individually
  2. Aggregate prompt: "Compare these papers. Identify similarities, differences, contradictions, research gaps. Write 2-3 paragraph comparative insight."
  3. Return per-paper summaries + cross-paper insight
- **Output:** `{ paperSummaries: AIAnalysisDTO[], comparativeInsight: String }`
- **Endpoint:** `POST /api/v1/ai/batch-analyze`
- **Authentication:** Required (RESEARCHER)
- **Priority:** 🟡 Low (Nice-to-have)

##### FR-AI-004: Keyword Expansion
- **Description:** Expand search keywords to improve recall
- **Input:** Keyword text
- **Processing:**
  1. Try Gemini API first (prompt: expand keyword)
  2. Fallback to local `ACADEMIC_RELATIONS` map if Gemini is unavailable
- **Output:** `{ expandedKeywords: String[], source: 'gemini' | 'local' }`
- **Endpoint:** Called internally within PaperSearchOrchestrator
- **Authentication:** Required
- **Priority:** 🟡 Low (Nice-to-have)

---

#### FR-BOOKMARK: Bookmarks & Collections

##### FR-BOOKMARK-001: CRUD Bookmarks
- **Description:** Users save papers/keywords to bookmarks and manage their list
- **Input:** Paper ID or Keyword ID, Notes (optional, ≤ 500 characters), Collection ID (optional)
- **Processing:** Validate user → create Bookmark entity → link with Paper/Keyword/Collection
- **Output:** BookmarkDTO
- **Endpoint:** `POST /api/v1/bookmarks`, `GET /api/v1/bookmarks`, `DELETE /api/v1/bookmarks/{id}`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🔴 High (Must-have)

##### FR-BOOKMARK-002: Bulk Operations
- **Description:** Add/remove multiple bookmarks at once
- **Input:** `[paperId1, paperId2, ...]`
- **Processing:** Batch insert/delete
- **Output:** `{ successCount, failedCount }`
- **Endpoint:** `POST /api/v1/bookmarks/bulk`, `DELETE /api/v1/bookmarks/bulk`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟠 Medium (Should-have)

##### FR-BOOKMARK-003: Bookmark Collections
- **Description:** Organize bookmarks into collections (e.g., "Thesis", "AI Research")
- **Input:** Collection name (≤ 200 characters), Description (optional)
- **Processing:** CRUD BookmarkCollection entity
- **Output:** CollectionDTO
- **Endpoint:** `GET/POST/PUT/DELETE /api/v1/collections`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟠 Medium (Should-have)

---

#### FR-FOLLOW: Following

##### FR-FOLLOW-001: Follow/Unfollow
- **Description:** Users follow authors, journals, topics, or keywords to receive notifications about new papers
- **Input:** Type (AUTHOR/JOURNAL/TOPIC/KEYWORD), Target ID, NotifyEnabled (boolean)
- **Processing:** Create/delete Follow entity → link with target
- **Output:** `{ followId, type, targetName, notifyEnabled }`
- **Endpoint:** `POST /api/v1/follows`, `GET /api/v1/follows`, `DELETE /api/v1/follows/{id}`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟠 Medium (Should-have)

##### FR-FOLLOW-002: New Paper Notification
- **Description:** When a followed author publishes a new paper → system automatically sends a notification
- **Input:** (Automatic — triggered when new paper is synced)
- **Processing:** NotificationTriggerService checks Follow table → if author has new paper → create NEW_PAPER notification
- **Output:** Notification pushed via SSE
- **Endpoint:** (Internal trigger)
- **Authentication:** N/A (system trigger)
- **Priority:** 🟠 Medium (Should-have)

---

#### FR-NOTIF: Notifications

##### FR-NOTIF-001: REST Notification CRUD
- **Description:** Users view notification list, mark as read, delete
- **Input:** Page, Size
- **Processing:** Query NOTIFICATION table WHERE userId → order by createdAt DESC
- **Output:** `{ content: NotificationDTO[], totalElements, unreadCount }`
- **Endpoint:** `GET /api/v1/notifications`, `PUT /api/v1/notifications/{id}/read`, `DELETE /api/v1/notifications/bulk`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🔴 High (Must-have)

##### FR-NOTIF-002: SSE Real-Time Push
- **Description:** Server pushes real-time notifications via Server-Sent Events
- **Input:** JWT token (via query parameter)
- **Processing:**
  1. Client opens EventSource connection to `/api/v1/notifications/stream`
  2. Server registers emitter in per-user registry
  3. On event → NotificationEventPublisher → NotificationEventListener (@Async) → save DB + push SSE
  4. Heartbeat every 30s, timeout 5 min → auto-reconnect
- **Output:** SSE event stream: `data: { type, title, message, relatedPaperId, createdAt }`
- **Endpoint:** `GET /api/v1/notifications/stream?token=<jwt>`
- **Authentication:** Required (via query param token)
- **Priority:** 🔴 High (Must-have)

##### FR-NOTIF-003: Notification Types
- **Description:** The system supports 4 notification types
- **Notification types:**
  - **NEW_PAPER** — Triggered when a followed author/journal has a new paper
  - **TREND_ALERT** — Top 5 trending topics (every 12h, from TrendingTopicSyncService)
  - **SYSTEM** — System notifications (trial notification)
  - **UPGRADE_PROMPT** — When Academic User reaches 80% and 100% usage limit
- **Priority:** 🔴 High (Must-have)

---

#### FR-REPORT: Reports

##### FR-REPORT-001: Keyword Trend Report
- **Description:** Generate a publication trend report for a keyword over time
- **Input:** Keyword, Period (start date, end date), Format (PDF/CSV)
- **Processing:**
  1. Query PUBLICATION_TREND table for keyword
  2. Calculate growth rate per period (monthly/quarterly/yearly)
  3. Generate chart (Recharts BarChart on FE)
  4. Export PDF or CSV
- **Output:** ReportDTO + File download
- **Endpoint:** `POST /api/v1/reports/keyword-trend`
- **Authentication:** Required (RESEARCHER)
- **Priority:** 🟠 Medium (Should-have)

##### FR-REPORT-002: Author Impact Report
- **Description:** Report on author impact (h-index, citation timeline, top papers)
- **Input:** Author ID/Name, Period
- **Output:** ReportDTO — hIndex, totalCitations, citationTimeline[], topPapers[]
- **Endpoint:** `POST /api/v1/reports/author-impact`
- **Authentication:** Required (RESEARCHER)
- **Priority:** 🟡 Low (Nice-to-have)

##### FR-REPORT-003: Journal Quality Report
- **Description:** Report on journal quality (impact factor, quartile, publication count)
- **Input:** Journal ID/Name, Period
- **Output:** ReportDTO
- **Endpoint:** `POST /api/v1/reports/journal-quality`
- **Authentication:** Required (RESEARCHER)
- **Priority:** 🟡 Low (Nice-to-have)

##### FR-REPORT-004: Report History
- **Description:** View list of previously generated reports
- **Output:** `[ReportDTO]` — report name, period, status, format, createdAt, fileURL
- **Endpoint:** `GET /api/v1/reports/history`
- **Authentication:** Required (RESEARCHER)
- **Priority:** 🟡 Low (Nice-to-have)

---

#### FR-CITATION: Citation Export

##### FR-CITATION-001: Single Citation Export
- **Description:** Export citation for a single paper in multiple formats
- **Input:** Paper ID, Format (bibtex/ris/apa/mla)
- **Processing:**
  1. Load paper metadata (title, authors, journal, DOI, pubYear)
  2. Format according to selected standard: BibTeX, RIS, APA 7th Edition, MLA 9th Edition
  3. Return text
- **Output:** `{ format, citationText }`
- **Endpoint:** `GET /api/v1/papers/{paperId}/citation?format=bibtex`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🔴 High (Must-have)

##### FR-CITATION-002: Bulk Citation Export
- **Description:** Export citations for multiple papers at once
- **Input:** `[paperId1, paperId2, ...]`, Format
- **Processing:** Iterate through each paper → format citation → combine into single output
- **Output:** `{ format, citationsText }`
- **Endpoint:** `POST /api/v1/papers/citations/export`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟠 Medium (Should-have)

---

#### FR-IDEA: Research Idea Analysis

##### FR-IDEA-001: Extract Keywords from Idea
- **Description:** AI extracts keywords from the user's research idea description
- **Input:** Idea text
- **Processing:**
  1. Call DeepSeek AI: "Extract academic keywords from this research idea"
  2. Normalize keywords (lowercase, trim, dedup)
  3. Match against existing keywords in Neo4j
  4. Return existing keywords + new keywords (not in DB)
- **Output:** `{ existingKeywords: String[], newKeywords: String[] }`
- **Endpoint:** `POST /api/v1/ideas/extract-keywords`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟡 Low (Nice-to-have)

##### FR-IDEA-002: Analyze Research Idea
- **Description:** AI analyzes a research idea (gap analysis, literature review)
- **Input:** Idea text, Keywords[]
- **Processing:**
  1. Find related papers in SQL Server + Neo4j
  2. AI evaluation: novelty score, related works, suggested approach, potential gaps
  3. Save result to IDEA_ANALYSIS table (with idea_hash for dedup)
- **Output:** `IdeaAnalysisDTO` — existingKeywords, newKeywords, noveltyScore, relatedPapers[], suggestedApproach, potentialGaps
- **Endpoint:** `POST /api/v1/ideas/analyze`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟡 Low (Nice-to-have)

##### FR-IDEA-003: Analysis History
- **Description:** View history of previously analyzed ideas
- **Output:** `[IdeaAnalysisDTO]`
- **Endpoint:** `GET /api/v1/ideas/history`, `GET /api/v1/ideas/history/{id}`, `DELETE /api/v1/ideas/history/{id}`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟡 Low (Nice-to-have)

---

#### FR-USER: User Profile Management

##### FR-USER-001: View & Update Profile
- **Description:** Users view and update their personal information
- **Input:** FullName, Institution, Avatar (image file, ≤ 50MB), Background URL
- **Processing:**
  1. If avatar provided → upload to Cloudinary → save URL
  2. Update USER table
- **Output:** UserDTO
- **Endpoint:** `GET /api/users/me`, `PUT /api/users/me`
- **Authentication:** Required
- **Priority:** 🔴 High (Must-have)

##### FR-USER-002: Change Password
- **Description:** Users change their password (requires current password verification)
- **Input:** Current password, New password
- **Processing:** Verify current password → hash new password → save
- **Output:** Success message
- **Endpoint:** `PUT /api/users/me/password`
- **Authentication:** Required
- **Priority:** 🔴 High (Must-have)

##### FR-USER-003: Account Upgrade
- **Description:** Academic User upgrades to Researcher (1-click)
- **Input:** (No input needed — based on current user)
- **Processing:**
  1. Verify user is ACADEMIC_USER
  2. Change Role → RESEARCHER
  3. Disable usage limits
- **Output:** `{ role: 'researcher', message }`
- **Endpoint:** `POST /api/users/me/upgrade`
- **Authentication:** Required (ACADEMIC_USER)
- **Priority:** 🟠 Medium (Should-have)

##### FR-USER-004: Reading History
- **Description:** Automatically save and display paper reading history
- **Processing:** Each time a user opens a paper detail → auto-save to USER_READING_HISTORY (dedup, only save first time per day)
- **Output:** `[ReadingHistoryDTO]` — paper title, viewedAt
- **Endpoint:** `GET /api/v1/reading-history`
- **Authentication:** Required (RESEARCHER, ACADEMIC_USER)
- **Priority:** 🟡 Low (Nice-to-have)

---

#### FR-ADMIN: System Administration

##### FR-ADMIN-001: User Management
- **Description:** Admin views user list, searches, enables/disables, changes roles
- **Input:** Search query (optional), Page, Size
- **Processing:** Query USER table → filter, sort
- **Output:** `{ content: UserDTO[], totalElements }`
- **Endpoint:** `GET /api/v1/admin/users`, `PUT /api/v1/admin/users/{id}/status`, `PUT /api/v1/admin/users/{id}/role`
- **Authentication:** Required (ADMIN) — `@PreAuthorize("hasRole('ADMIN')")`
- **Priority:** 🔴 High (Must-have)

##### FR-ADMIN-002: Admin Dashboard
- **Description:** Overview dashboard for admin with system metrics
- **Output:** `{ totalUsers, totalPapers, totalKeywords, totalSyncs, activeUsers, requestVolumeChart, resourceUsage, visitorTraffic }`
- **Endpoint:** `GET /api/v1/admin/overview`
- **Authentication:** Required (ADMIN)
- **Priority:** 🟠 Medium (Should-have)

##### FR-ADMIN-003: Database Statistics
- **Description:** Display statistics from both SQL Server and Neo4j
- **Output:** `{ sqlStats: { papers, authors, journals, keywords, users }, neo4jStats: { papers, keywords, relationships } }`
- **Endpoint:** `GET /api/v1/admin/database-stats`
- **Authentication:** Required (ADMIN)
- **Priority:** 🟠 Medium (Should-have)

##### FR-ADMIN-004: Manual Data Sync
- **Description:** Admin manually triggers data synchronization from external APIs
- **Input:** Source (openalex/semantic-scholar/arxiv/core), Keywords[], Papers per keyword
- **Processing:**
  1. `DataSyncServiceImpl` fetches papers from the specified API
  2. DOI deduplication
  3. Abstract reconstruction (from OpenAlex inverted index)
  4. Save to SQL Server (JPA entities)
  5. Save to Neo4j (GraphService.savePaperWithKeywords())
  6. Log to SyncLog table
- **Output:** `{ syncLogId, source, papersFetched, papersInserted, status }`
- **Endpoint:** `POST /api/v1/admin/sync/openalex`, `POST /api/v1/admin/sync/semantic-scholar`, `POST /api/v1/admin/sync/bulk`
- **Authentication:** Required (ADMIN)
- **Priority:** 🔴 High (Must-have)

##### FR-ADMIN-005: Bulk Sync with Progress Tracking
- **Description:** Batch synchronize multiple keywords with real-time progress
- **Input:** Keywords[], Source, Papers per keyword
- **Processing:** `BulkSyncProgressTracker` (in-memory) tracks progress → FE polls every 2.5s
- **Output:** `{ taskId, progress: { completed, total, percentage, currentKeyword } }`
- **Endpoint:** `POST /api/v1/admin/sync/bulk`, `GET /api/v1/admin/sync/progress/{taskId}`
- **Authentication:** Required (ADMIN)
- **Priority:** 🟠 Medium (Should-have)

##### FR-ADMIN-006: Audit Log
- **Description:** Record and display the operation history of all admins
- **Processing:** AdminAuditLogInterceptor logs every admin request (action, targetTable, targetId, oldValue, newValue, IP)
- **Output:** `{ content: AuditLogDTO[], totalElements }`
- **Endpoint:** `GET /api/v1/admin/audit-logs`
- **Authentication:** Required (ADMIN)
- **Priority:** 🟠 Medium (Should-have)

##### FR-ADMIN-007: System Configuration
- **Description:** Admin manages system configuration (key-value pairs)
- **Key configs:**
  - `academic_monthly_search_limit` — Monthly search limit for Academic Users
  - `academic_monthly_view_limit` — Monthly paper view limit
  - `app.rate-limit.*` — Rate limiting RPM per tier
  - `app.auto-sync-enabled` — Enable/disable automatic sync
- **Endpoint:** `GET /api/v1/admin/configs`, `PUT /api/v1/admin/configs/{key}`
- **Authentication:** Required (ADMIN)
- **Priority:** 🟠 Medium (Should-have)

##### FR-ADMIN-008: PDF Request Management
- **Description:** Admin reviews and processes PDF requests from users
- **Input:** Status filter (pending/fulfilled/rejected), Page, Size
- **Processing:**
  1. Display PDF request list
  2. Admin finds PDF file from external sources
  3. Upload file or mark as fulfill/reject with notes
- **Output:** `{ content: PdfRequestDTO[], totalElements }`
- **Endpoint:** `GET /api/v1/admin/pdf-requests`, `PUT /api/v1/admin/pdf-requests/{id}`
- **Authentication:** Required (ADMIN)
- **Priority:** 🟡 Low (Nice-to-have)

---

#### FR-SYNC: Data Synchronization (Scheduled)

##### FR-SYNC-001: Scheduled Auto-Sync
- **Description:** System automatically synchronizes data from external APIs on a schedule
- **Schedule:**
  - **Startup sync:** 12 keywords × 30 papers from 4 sources
  - **Hourly sync:** 8 trending keywords × 10 papers
  - **Daily 2 AM:** Full sync from OpenAlex
  - **12-hour cycle:** Trending topic detection
- **Processing:** ScheduledDataSyncService (`@Scheduled` cron jobs)
- **Gated by:** `app.auto-sync-enabled` config (can be disabled from admin config)
- **Output:** SyncLog entries
- **Priority:** 🔴 High (Must-have)

##### FR-SYNC-002: Search Save-on-Sync (Fire-and-Forget)
- **Description:** When users search a new keyword, results are automatically saved to the database (async, non-blocking)
- **Processing:** Background task: save papers + authors + keywords + relationships to SQL Server + Neo4j
- **Priority:** 🟠 Medium (Should-have)

---

#### FR-OVERVIEW: Overview Pages

##### FR-OVERVIEW-001: Public Overview
- **Description:** Landing page displaying overall statistics (no login required)
- **Output:** `{ totalPapers, totalKeywords, totalAuthors, totalJournals }`
- **Endpoint:** `GET /api/v1/overview`
- **Authentication:** Not required (public)
- **Priority:** 🟠 Medium (Should-have)

##### FR-OVERVIEW-002: User Overview Dashboard
- **Description:** Personal dashboard after login
- **Output:** `{ recentPapers, totalCitations, trendingTopics, weeklyBreakout, recommendations }`
- **Endpoint:** `GET /api/v1/user-overview`
- **Authentication:** Required
- **Priority:** 🔴 High (Must-have)

---

### 3.2 External Interface Requirements

#### 3.2.1 User Interfaces

The system has the following main screens:

| # | Screen | Route | Description |
|---|--------|-------|-------------|
| **UI-01** | Landing Page | `/` | Product introduction page with animated hero, featured slider, CTA "Get Started" |
| **UI-02** | Login Page | `/login` | Full-screen split layout, email/password form, Google OAuth button, links to register/forgot password |
| **UI-03** | Register Page | `/register` | Registration form: email, password, full name, institution |
| **UI-04** | Forgot Password | `/reset-password` | Email input form → send reset link → new password form |
| **UI-05** | Overview Dashboard | `/:role/overview` | Stat cards + charts: new papers, citations, trending topics, weekly breakout |
| **UI-06** | Search Papers | `/:role/search` | 3-column layout: paper list (left) + Neo4j graph (center) + quick stats (right). Top bar: search input + weekly breakout + advanced filter |
| **UI-07** | Paper Detail | `/:role/papers/:id` | Dialog/Slide-over: full metadata, AI summary tabs, methodology, citation export (tabbed: BibTeX/RIS/APA), similar papers, rating, bookmark |
| **UI-08** | Search Author | `/:role/search-author` | Search input + autocomplete → author detail: h-index, timeline, co-authors, research focus, top papers |
| **UI-09** | Search Journal | `/:role/journal-search` | Search input + top journals grid → journal detail: impact factor, quartile, papers |
| **UI-10** | Bookmarks | `/:role/bookmarks` | Grid/list bookmarks + collections sidebar + bulk action bar |
| **UI-11** | Follows | `/:role/follows` | Tabbed view: followed authors / journals / topics / keywords |
| **UI-12** | Notifications | `/:role/notifications` | Notification list with unread badge + filter + mark read/delete |
| **UI-13** | Reading History | `/:role/reading-history` | Timeline of read papers |
| **UI-14** | Reports | `/:role/reports` | Generator cards: Keyword Trend / Author Impact / Journal Quality → form → result + export |
| **UI-15** | Ideas | `/:role/ideas` | Text area for idea input → AI analysis → gap analysis + literature review results |
| **UI-16** | Settings | `/:role/settings` | Profile form, avatar upload, change password, notification preferences |
| **UI-17** | Admin Dashboard | `/:role/admin/overview` | Stat cards, request volume chart, resource usage, visitor traffic |
| **UI-18** | User Management | `/:role/users` | User table + search + enable/disable + role change |
| **UI-19** | Database View | `/:role/database` | SQL Server stats + Neo4j stats side by side |
| **UI-20** | Sync Data | `/:role/sync-data` | Source selection + keyword input → manual sync + bulk sync + progress tracking |
| **UI-21** | Audit Logs | `/:role/audit-logs` | Audit log table with filter + detail slide-over |
| **UI-22** | System Config | `/:role/configs` | Key-value config editor |
| **UI-23** | PDF Requests | `/:role/pdf-requests` | PDF request table + status filter + fulfill/reject actions |
| **UI-24** | Analytics | `/:role/analytics` | Country/institution analytics with charts |
| **UI-25** | 404 Not Found | `*` | 404 error page |

**General UI Requirements:**
- **UI-GEN-01:** Dark-only theme with CSS custom properties (`--background: #0B1020`, `--primary: #4F8CFF`, `--accent: #00D1B2`)
- **UI-GEN-02:** Fonts: Outfit (headings), Inter (body), JetBrains Mono (data/code)
- **UI-GEN-03:** Responsive — supports desktop (primary) and mobile/tablet (responsive layout)
- **UI-GEN-04:** Animation: Framer Motion for page transitions, hover effects, loading states
- **UI-GEN-05:** i18n: English (EN) and Vietnamese (VI) support, 13 translation namespaces
- **UI-GEN-06:** Toast notifications: Sonner for success/error/warning messages
- **UI-GEN-07:** Loading states: Skeleton components for all data-fetching views
- **UI-GEN-08:** Empty states: Friendly message displayed when no data is available
- **UI-GEN-09:** Error states: Error display with retry button when API call fails

#### 3.2.2 Hardware Interfaces

No special hardware interface requirements. The system runs on standard cloud infrastructure (AWS RDS + Railway).

#### 3.2.3 Software Interfaces

##### 3.2.3.1 Internal REST API

| Interface | Description | Format | Auth |
|-----------|-------------|--------|------|
| **FE ↔ BE REST API** | Frontend communicates with Backend via REST API | JSON | JWT Bearer Token |
| **SSE Stream** | Backend pushes real-time notifications | text/event-stream | JWT (query param) |

**REST API Technical Specifications:**
- Base URL: `http://localhost:8080` (dev) / Production URL (Railway)
- Content-Type: `application/json`
- Authentication: `Authorization: Bearer <jwt_token>`
- Language: `Accept-Language: en` or `Accept-Language: vi`
- Pagination: `?page=0&size=20&sort=createdAt,desc`
- Error Response Format: `{ errorCode, message, timestamp, path }`

##### 3.2.3.2 External API Integrations

| # | API | Endpoint | Auth | Rate Limit | Usage |
|---|-----|----------|------|------------|-------|
| **API-01** | **OpenAlex** | `https://api.openalex.org/works` | API Key (`OPENALEX_API_KEY`) | Polite pool | Primary paper source (240M+ works) |
| **API-02** | **Semantic Scholar** | `https://api.semanticscholar.org/graph/v1/paper/search` | Not required | Lower than OpenAlex | Secondary paper source, citation data |
| **API-03** | **DeepSeek AI** | `https://api.ai-box.vn/v1/chat/completions` | API Key (`DEEPSEEK_API_KEY`) | Per API key | AI summarization, methodology, batch analysis, keyword expansion |
| **API-04** | **CORE** | `https://api.core.ac.uk/v3/` | API Key (`CORE_API_KEY`) | Standard | Paper full-text access |
| **API-05** | **arXiv** | `https://export.arxiv.org/api/query` | Not required | Standard | Preprint papers (XML Atom feed) |
| **API-06** | **Google OAuth2** | Google Identity Services | Client ID (`GOOGLE_CLIENT_ID`) | Standard | Social login |
| **API-07** | **Cloudinary** | Cloudinary Upload API | API Key + Secret | Per plan | Image hosting (avatars, backgrounds) |
| **API-08** | **Gmail SMTP** | `smtp.gmail.com:587` | Username + App Password | Gmail limits | Email delivery (verification, password reset) |

#### 3.2.4 Communication Interfaces

- **Protocol:** HTTPS (TLS 1.2+)
- **Data Format:** JSON (REST API), HTML/XML (arXiv), text/event-stream (SSE)
- **Timeout:** Connect 30s / Read 120s (for AI calls), 30s (for regular API calls)

### 3.3 Non-Functional Requirements

---

#### NFR-PERF: Performance

| ID | Requirement | Metric | Test Method |
|----|------------|--------|-------------|
| **NFR-PERF-01** | Search response time (cache hit) | < 500ms | JMeter: 100 concurrent users searching same keyword |
| **NFR-PERF-02** | Search response time (cache miss, OpenAlex fallback) | < 3s | JMeter: search new keyword (not cached) |
| **NFR-PERF-03** | Paper detail load time | < 1s | JMeter: GET /api/v1/papers/{id} |
| **NFR-PERF-04** | AI summarization time (including DeepSeek API call) | < 5s | Manual test with long abstract |
| **NFR-PERF-05** | Graph visualization load time (Neo4j + vis-network render) | < 1.5s | Load graph with ~200 nodes |
| **NFR-PERF-06** | System throughput | Support minimum 100 requests/second | JMeter load test |
| **NFR-PERF-07** | Concurrent users | Support minimum 1000 concurrent users | JMeter: ramp-up 1000 users |
| **NFR-PERF-08** | Database connection pool | HikariCP: max 10 connections, min idle 3 | Application properties |
| **NFR-PERF-09** | Cache hit ratio | > 70% for popular search queries | Caffeine stats |

---

#### NFR-SEC: Security

| ID | Requirement | Description |
|----|------------|-------------|
| **NFR-SEC-01** | **JWT Authentication** | All endpoints (except auth, health, swagger) require JWT Bearer Token in Authorization header |
| **NFR-SEC-02** | **Password Encryption** | BCrypt (strength ≥ 10 rounds) |
| **NFR-SEC-03** | **JWT Security** | Secret key ≥ 256-bit, access token TTL = 24h, refresh token TTL = 7d, token rotation |
| **NFR-SEC-04** | **Role-Based Access Control** | 3 roles: ADMIN, RESEARCHER, ACADEMIC_USER. BE: `@PreAuthorize`, FE: `ProtectedRoute` with `allowedRoles` |
| **NFR-SEC-05** | **Rate Limiting** | Bucket4j token-bucket: Public 30 RPM, Authenticated 120 RPM, Admin 120 RPM |
| **NFR-SEC-06** | **CORS** | Only allow origins from configured frontend URL |
| **NFR-SEC-07** | **HTTPS** | All production traffic over HTTPS (TLS 1.2+) |
| **NFR-SEC-08** | **Password Reset Token** | Token TTL = 15 minutes, single-use |
| **NFR-SEC-09** | **SQL Injection Prevention** | Use JPA Parameterized Queries (Prepared Statements) |
| **NFR-SEC-10** | **Input Validation** | All inputs validated on both FE (form validation) and BE (Bean Validation / DTO validation) |
| **NFR-SEC-11** | **Audit Trail** | All admin operations are logged (AdminAuditLogInterceptor) |

---

#### NFR-AVAIL: Availability

| ID | Requirement | Metric |
|----|------------|--------|
| **NFR-AVAIL-01** | System uptime | ≥ 99.5% (downtime < 3.65 hours/month) |
| **NFR-AVAIL-02** | Graceful degradation | When AI service is unavailable → AI fields = null, no crash |
| **NFR-AVAIL-03** | Graceful degradation | When OpenAlex API is unavailable → display friendly error message, still searchable in cache/DB |
| **NFR-AVAIL-04** | Graceful degradation | When Neo4j is unavailable → search falls back entirely to OpenAlex API |
| **NFR-AVAIL-05** | Database backup | Daily SQL Server backup (automated) |

---

#### NFR-RELIABILITY: Reliability

| ID | Requirement | Description |
|----|------------|-------------|
| **NFR-REL-01** | Data integrity | DOI deduplication when syncing from multiple sources |
| **NFR-REL-02** | Transaction integrity | Sync operations: @Transactional — if one part fails → rollback |
| **NFR-REL-03** | Stale data cleanup | Neo4j nodes not existing in SQL Server → automatically deleted |
| **NFR-REL-04** | Refresh token rotation | Old token invalidated when new token created → prevents replay attacks |
| **NFR-REL-05** | Async operations resilience | Email, notification push, save-on-search: fire-and-forget with error logging |

---

#### NFR-SCAL: Scalability

| ID | Requirement | Description |
|----|------------|-------------|
| **NFR-SCAL-01** | Horizontal scaling | Backend is stateless (JWT, no server-side sessions) → can scale horizontally via load balancer |
| **NFR-SCAL-02** | Database scaling | SQL Server: read/write through connection pool; Neo4j: cloud-hosted (AuraDB) |
| **NFR-SCAL-03** | Caching strategy | 3-layer cache: L1 (in-memory ConcurrentHashMap, 6h TTL), L2 (Caffeine, 1h TTL), L3 (Database PaperCache, 7d TTL) |

---

#### NFR-USABILITY: Usability

| ID | Requirement | Description |
|----|------------|-------------|
| **NFR-USA-01** | Multi-language | English + Vietnamese support (13 i18n namespaces) |
| **NFR-USA-02** | Responsive design | Interface works on desktop (1920px), tablet (1024px), mobile (375px) |
| **NFR-USA-03** | Accessibility | WCAG 2.1 Level A compliance (minimum): semantic HTML, ARIA labels on Radix UI components, keyboard navigation, color contrast ratio ≥ 4.5:1 |
| **NFR-USA-04** | Form validation | Real-time validation with clear error messages (React Hook Form) |
| **NFR-USA-05** | Loading & Error states | Skeleton loading, error messages with retry button, empty states with icon + text |
| **NFR-USA-06** | Toast notifications | Success/failure/warning notifications via Sonner (position: bottom-right) |
| **NFR-USA-07** | Keep-alive | Preserve state when switching tabs (form input, scroll position) — LRU cache max 10 routes |

---

#### NFR-MAINTAIN: Maintainability

| ID | Requirement | Description |
|----|------------|-------------|
| **NFR-MAIN-01** | Code organization | BE: package-by-layer (controller/service/repository); FE: feature-based (features/auth/, features/search/, ...) |
| **NFR-MAIN-02** | API Documentation | Swagger UI auto-updated at `/swagger-ui/index.html` (SpringDoc OpenAPI) |
| **NFR-MAIN-03** | Logging | Structured logging for all services (SLF4J + Logback) |
| **NFR-MAIN-04** | Health check | Spring Boot Actuator: `/actuator/health` |
| **NFR-MAIN-05** | Environment configuration | `.env` file for all environment configurations (database, API keys, JWT secret) |

---

#### NFR-DATA: Data Management

| ID | Requirement | Description |
|----|------------|-------------|
| **NFR-DATA-01** | Database versioning | Schema managed via `schema.sql` (ddl-auto: none, sql.init.mode: always) |
| **NFR-DATA-02** | Connection pool | HikariCP: max 10, min idle 3, connection timeout 10s, leak detection 30s |
| **NFR-DATA-03** | Batch fetching | `default_batch_fetch_size: 50` to optimize N+1 queries |
| **NFR-DATA-04** | Open-in-view | `open-in-view: false` to avoid lazy loading issues |
| **NFR-DATA-05** | Primary keys | UUID with `GenerationType.UUID` for all entities |
| **NFR-DATA-06** | Stored procedures | 4 SPs: `SP_CHECK_AND_INCREMENT_USAGE`, `SP_CLEANUP_EXPIRED_SESSIONS`, `SP_RESET_MONTHLY_USAGE`, `SP_REFRESH_TOPIC_TRENDS` |
| **NFR-DATA-07** | Database views | 4 views: `V_USER_DETAIL`, `V_TRENDING_TOPICS`, `V_ACADEMIC_USAGE_CURRENT`, `V_SYNC_HISTORY` |

---

### 3.4 Design Constraints

| ID | Constraint | Description |
|----|-----------|-------------|
| **DC-01** | **Programming Language** | Backend: Java 21; Frontend: JavaScript (React 18) |
| **DC-02** | **Framework** | Backend: Spring Boot 3.5.14; Frontend: Vite 6 |
| **DC-03** | **Database** | SQL Server 2022 (JPA/Hibernate) + Neo4j AuraDB (Spring Data Neo4j) |
| **DC-04** | **Authentication** | JWT stateless (jjwt 0.12.3), no server-side sessions |
| **DC-05** | **Build Tool** | Backend: Maven; Frontend: npm/Vite |
| **DC-06** | **Container** | Docker Compose for local development (SQL Server + Neo4j) |
| **DC-07** | **Deployment** | Production: BE on Railway, SQL Server on AWS RDS, Neo4j on AuraDB Cloud, FE on Vercel |
| **DC-08** | **UI Framework** | shadcn/ui (Radix UI primitives + Tailwind CSS v4) |
| **DC-09** | **State Management** | Zustand (persist middleware), no Redux |
| **DC-10** | **Package Isolation** | `entity.jpa.*` / `entity.neo4j.*`, `repository.jpa.*` / `repository.neo4j.*` to prevent Spring Data scanning conflicts |

### 3.5 Business Rules

| ID | Rule | Description |
|----|------|-------------|
| **BR-01** | **Academic Monthly Limit** | Academic Users have a monthly search limit (configured via `academic_monthly_search_limit`, default 30) |
| **BR-02** | **Usage Warning** | When Academic User reaches 80% limit → UPGRADE_PROMPT notification |
| **BR-03** | **Usage Cap** | When Academic User reaches 100% limit → search blocked + UPGRADE_PROMPT notification |
| **BR-04** | **Monthly Usage Reset** | `SP_RESET_MONTHLY_USAGE` runs at the beginning of each month → reset searchCount to 0 for all Academic Users |
| **BR-05** | **Researcher Unlimited** | Researchers have no search limit |
| **BR-06** | **Auto Upgrade** | Academic User can self-upgrade to Researcher (1-click, no admin approval required) |
| **BR-07** | **Google Trial Auto-Downgrade** | Users registering via Google OAuth get 3-day Researcher trial → auto-downgrade to Academic User when expired |
| **BR-08** | **Paper Quality Filter** | When crawling, only fetch papers with quality score ≥ 40/100 (based on citations, journal quartile, article type, OA, abstract length, DOI) |
| **BR-09** | **DOI Deduplication** | Papers with duplicate DOIs from multiple sources → merge, do not create duplicates |
| **BR-10** | **Stale Neo4j Cleanup** | Neo4j Paper nodes not existing in SQL Server → automatically deleted (stale ID cleanup) |
| **BR-11** | **Session Cleanup** | `SP_CLEANUP_EXPIRED_SESSIONS` runs periodically → delete expired sessions |
| **BR-12** | **Trending Topic Calculation** | `SP_REFRESH_TOPIC_TRENDS` recalculates TrendScore and IsTrending based on paper count and growth rate |
| **BR-13** | **Sync Gating** | Auto-sync only runs when `app.auto-sync-enabled = true` |
| **BR-14** | **Admin Only Actions** | Sync triggers, user management, system config, audit log viewing are reserved for ADMIN |
| **BR-15** | **Researcher Only Features** | Advanced filter, analytics, graph explorer, batch AI analysis are reserved for RESEARCHER |

---

## 4. APPENDICES

### 4.1 Use Case Diagram

```
                              ┌──────────────────────────────────────────┐
                              │              SCITRACK SYSTEM              │
                              │                                          │
    ┌──────────┐              │  ┌──────────────────────────────────┐    │
    │          │  Register    │  │  Auth Module                     │    │
    │          │◄────────────►│  │  ├─ Register (email/password)    │    │
    │          │  Login       │  │  ├─ Login (email + Google)       │    │
    │ Academic │  Forgot Pwd  │  │  ├─ Forgot/Reset Password        │    │
    │  User    │              │  │  └─ Refresh Token                │    │
    │          │  Search Papers│  └──────────────────────────────────┘    │
    │          │◄────────────►│                                          │
    │          │  View Graph  │  ┌──────────────────────────────────┐    │
    │          │  Bookmark    │  │  Research Module                  │    │
    │          │  Follow      │  │  ├─ Search (keyword/author/journal)│   │
    │          │  Notifications│  │  ├─ AI Summarize                 │    │
    │          │  Export Cite │  │  ├─ View Graph                   │    │
    │          │  Upgrade     │  │  ├─ Bookmarks & Collections       │    │
    └──────────┘              │  │  ├─ Follows                      │    │
                              │  │  ├─ Reading History              │    │
    ┌──────────┐              │  │  ├─ Citation Export              │    │
    │          │  All above + │  │  ├─ Idea Analysis                │    │
    │          │  Analytics   │  │  └─ Reports                      │    │
    │Researcher│  Reports     │  └──────────────────────────────────┘    │
    │          │  Adv. Filter │                                          │
    │          │  Batch AI    │  ┌──────────────────────────────────┐    │
    └──────────┘              │  │  Admin Module                    │    │
                              │  │  ├─ User Management              │    │
    ┌──────────┐              │  │  ├─ Data Sync (Manual/Bulk)      │    │
    │          │  User CRUD   │  │  ├─ System Configuration         │    │
    │  Admin   │  Data Sync   │  │  ├─ Database Statistics          │    │
    │          │◄────────────►│  │  ├─ Audit Logs                   │    │
    │          │  System Config│  │  └─ PDF Request Management       │    │
    │          │  Audit Logs  │  └──────────────────────────────────┘    │
    └──────────┘              │                                          │
                              │  ┌──────────────────────────────────┐    │
                              │  │  External Systems                │    │
                              │  │  ├─ OpenAlex API                 │    │
                              │  │  ├─ Semantic Scholar API         │    │
                              │  │  ├─ DeepSeek AI                  │    │
                              │  │  ├─ CORE API / arXiv API         │    │
                              │  │  ├─ Google OAuth2                │    │
                              │  │  ├─ Cloudinary (Image)           │    │
                              │  │  └─ Gmail SMTP (Email)           │    │
                              │  └──────────────────────────────────┘    │
                              └──────────────────────────────────────────┘
```

**Actor Descriptions:**

| Actor | Description |
|-------|-------------|
| **Academic User** | Students, PhD candidates — limited search, bookmarks, follows, reading history, citation export |
| **Researcher** | Lecturers, scientists — full features: unlimited search, analytics, reports, batch AI, advanced filter |
| **Admin** | System administrator — user management, data sync, system config, audit logs, PDF request management |
| **OpenAlex API** | External system — primary paper data provider |
| **Semantic Scholar API** | External system — secondary paper data + citations |
| **DeepSeek AI** | External system — AI summarization, methodology, batch analysis |
| **CORE API** | External system — full-text paper provider |
| **arXiv API** | External system — preprint paper provider |
| **Google OAuth2** | External system — Google sign-in authentication |
| **Cloudinary** | External system — image storage and processing |
| **Gmail SMTP** | External system — email delivery service |

### 4.2 Data Flow Diagrams

#### 4.2.1 Level 0 — Context Diagram

```
                          ┌─────────────────┐
                          │   OpenAlex API  │
                          │  (240M+ works)  │
                          └────────┬────────┘
                                   │ Papers data
                                   ▼
┌──────────┐    Search Query  ┌──────────────┐    Email        ┌──────────┐
│          │◄───────────────►│               │◄───────────────►│  SMTP    │
│  Users   │    Results      │   SCITRACK    │                 │  Server  │
│ (3 roles)│                 │   SYSTEM      │                 └──────────┘
│          │◄───────────────►│               │
└──────────┘  Auth/Profile   └───────┬───────┘
                                     │
                    ┌────────────────┼────────────────┐
                    │                │                │
                    ▼                ▼                ▼
            ┌──────────┐   ┌──────────────┐   ┌──────────────┐
            │  SQL     │   │  Neo4j       │   │ DeepSeek AI  │
            │  Server  │   │  (Graph DB)  │   │  Gemini AI   │
            └──────────┘   └──────────────┘   └──────────────┘
```

#### 4.2.2 Level 1 — Search Pipeline

```
┌──────────┐     keyword      ┌─────────────────────────────┐
│          │──────────────────►│                             │
│  User    │                   │  PaperSearchOrchestrator    │
│          │◄─────────────────│                             │
└──────────┘   SearchResult   └──────────┬──────────────────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    │                    │                    │
                    ▼                    ▼                    ▼
          ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐
          │ 1. Neo4j        │  │ 2. SQL Server   │  │ 3. OpenAlex API  │
          │ Graph Search    │  │ Full Paper Data │  │ Live Fallback    │
          │                 │  │                 │  │                  │
          │ MATCH (p:Paper) │  │ SELECT * FROM   │  │ GET /works?      │
          │ -[:HAS_KEYWORD] │  │ RESEARCH_PAPER  │  │ search=keyword   │
          │ ->(k:Keyword)   │  │ WHERE id IN     │  │                  │
          │                 │  │ (:paperIds)     │  │ + async bg sync  │
          └────────┬────────┘  └────────┬────────┘  └────────┬─────────┘
                   │                    │                    │
                   └────────────────────┼────────────────────┘
                                        │
                                        ▼
                               ┌────────────────┐
                               │  Merge & Rank  │
                               │  Limit top 50  │
                               │  Cache (6h TTL)│
                               └────────────────┘
```

#### 4.2.3 Level 1 — Data Sync Pipeline

```
┌──────────────────────────────────────────────────────┐
│                  DataSyncServiceImpl                  │
│                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │ OpenAlex    │  │ Semantic     │  │ CORE +      │ │
│  │ syncFrom    │  │ Scholar      │  │ arXiv       │ │
│  │ OpenAlex()  │  │ syncFromSS() │  │ syncFrom    │ │
│  │             │  │              │  │ Other()     │ │
│  └──────┬──────┘  └──────┬───────┘  └──────┬──────┘ │
│         │                │                │         │
│         └────────────────┼────────────────┘         │
│                          │                          │
│                          ▼                          │
│            ┌─────────────────────────┐              │
│            │   DOI Deduplication     │              │
│            │   Abstract Reconstruction│             │
│            │   Quality Score Filter  │              │
│            │   (≥ 40/100)           │              │
│            └────────────┬────────────┘              │
│                         │                          │
│           ┌─────────────┼─────────────┐            │
│           ▼             ▼             ▼            │
│   ┌───────────┐  ┌───────────┐  ┌───────────┐     │
│   │ SQL Server │  │  Neo4j    │  │ SyncLog   │     │
│   │ (JPA)     │  │  (Graph)  │  │ (Audit)   │     │
│   └───────────┘  └───────────┘  └───────────┘     │
│                                                    │
│  ┌─────────────────────────────────────────────┐   │
│  │  Scheduled Triggers:                         │   │
│  │  ├─ Startup: 12 keywords × 30 papers         │   │
│  │  ├─ Hourly: 8 trending keywords × 10 papers │   │
│  │  ├─ Daily 2 AM: Full OpenAlex sync          │   │
│  │  └─ 12h cycle: Trending topic detection      │   │
│  └─────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

### 4.3 Entity-Relationship Diagram (ERD)

#### 4.3.1 SQL Server — Core Tables

```
                          ┌──────────┐
                          │   ROLE   │
                          │ RoleID PK│
                          │ RoleName │
                          │ Descript │
                          └────┬─────┘
                               │ 1
                               │
                               │ N
┌──────────────┐         ┌─────┴─────┐         ┌──────────────────┐
│ VERIFICATION │         │   USER    │         │  USER_SESSION     │
│   _TOKEN     │◄────────│ UserID PK │────────►│  SessionID PK    │
└──────────────┘   N:1   │ RoleID FK │   1:N   │  UserID FK        │
                          │ Email     │         │  TokenHash        │
┌──────────────┐         │ FullName  │         │  RefreshTokenHash │
│ USER_USAGE   │◄────────│ Institution│         │  ExpiresAt        │
│ UsageID PK   │  1:N    │ IsActive  │         └──────────────────┘
│ UserID FK    │         └─────┬─────┘
│ SearchCount  │               │
│ ViewCount    │               │ 1
└──────────────┘               │
                               │ N
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
    ┌─────────────┐  ┌──────────────┐  ┌──────────────┐
    │  BOOKMARK   │  │   FOLLOW     │  │ NOTIFICATION │
    │ BookmarkID  │  │  FollowID PK │  │  NotifID PK  │
    │ UserID FK   │  │  UserID FK   │  │  UserID FK   │
    │ PaperID FK  │  │  JournalID   │  │  Type        │
    │ KeywordID   │  │  TopicID     │  │  Title       │
    │ CollectionID│  │  KeywordID   │  │  Message     │
    └──────┬──────┘  │  AuthorID    │  │  IsRead      │
           │         └──────────────┘  └──────────────┘
           │
           ▼
┌──────────────────┐
│ BOOKMARK_COLLECT │
│ CollectionID PK  │
│ UserID FK        │
│ Name             │
└──────────────────┘

                    ┌──────────────────────┐
                    │   RESEARCH_PAPER     │
                    │   PaperID PK         │
                    │   SourceID FK        │
                    │   JournalID FK       │
                    │   FieldID FK         │
                    │   Title              │
                    │   Abstract           │
                    │   DOI (UNIQUE)       │
                    │   PubDate, PubYear   │
                    │   CitationCount      │
                    │   Type               │
                    │   IsOpenAccess       │
                    │   PdfUrl             │
                    │   AiSummary          │
                    │   Methodology        │
                    └──────────┬───────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
          ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│ PAPER_AUTHOR    │  │ PAPER_KEYWORD   │  │  PAPER_RATING       │
│ PaperID FK (PK) │  │ PaperID FK (PK) │  │  RatingID PK        │
│ AuthorID FK (PK)│  │ KeywordID FK(PK)│  │  UserID FK          │
│ AuthorOrder     │  │ RelevanceScore  │  │  PaperID FK         │
│ IsCorresponding │  └─────────────────┘  │  Score (1-5)        │
└────────┬────────┘                        │  UNIQUE(User,Paper) │
         │                                 └─────────────────────┘
         ▼
┌─────────────────┐     ┌─────────────────┐     ┌──────────────────┐
│     AUTHOR      │     │    KEYWORD      │     │    JOURNAL       │
│  AuthorID PK    │     │  KeywordID PK   │     │  JournalID PK    │
│  SourceID FK    │     │  FieldID FK     │     │  SourceID FK     │
│  ExternalAuthID │     │  KeywordText    │     │  FieldID FK      │
│  FullName       │     │  NormalizedText │     │  JournalName     │
│  Affiliation    │     │  PaperCount     │     │  ISSN (UNIQUE)   │
│  Country        │     └─────────────────┘     │  Publisher       │
│  HIndex         │                              │  ImpactFactor    │
│  TotalCitations │                              │  Quartile        │
│  WorksCount     │                              └──────────────────┘
└─────────────────┘

ADMIN & SYSTEM TABLES:
┌────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
│ SYNC_LOG   │ │ AUDIT_LOG    │ │SYSTEM_CONFIG │ │PDF_REQUEST       │
│ LogID PK   │ │ AuditID PK   │ │ConfigID PK   │ │RequestID PK      │
│ SourceID FK│ │ AdminID FK   │ │ConfigKey (UQ)│ │UserID FK         │
│ SyncType   │ │ Action       │ │ConfigValue   │ │PaperID FK        │
│ IsManual   │ │ TargetTable  │ │Description   │ │Status            │
│ Status     │ │ TargetID     │ │UpdatedAt     │ │UserMessage       │
│ PapersFet  │ │ OldValue     │ │UpdatedBy     │ │AdminNote         │
│ PapersIns  │ │ NewValue     │ └──────────────┘ └──────────────────┘
│ StartedAt  │ │ IPAddress    │
│ CompletedAt│ └──────────────┘
└────────────┘

ANALYTICS TABLES:
┌──────────────┐ ┌──────────────┐ ┌──────────────────┐ ┌─────────────────┐
│RESEARCH_TOPIC│ │SEARCH_KEYWORD│ │TRENDING_TOPIC    │ │PUBLICATION_TREND│
│TopicID PK    │ │SearchKWID PK │ │TrendingTopicID PK│ │TrendID PK       │
│FieldID FK    │ │KeywordText   │ │TopicName         │ │PeriodType       │
│TopicName     │ │NormalizedText│ │PaperCount        │ │PeriodValue      │
│IsTrending    │ │SearchCount   │ │Source            │ │TrendTarget      │
│TrendScore    │ │LastSearchAt  │ │DisplayOrder      │ │TargetID         │
│PaperCount    │ └──────────────┘ │UpdatedAt         │ │PaperCount       │
└──────────────┘                  └──────────────────┘ │GrowthRate       │
                                                        └─────────────────┘
```

#### 4.3.2 Neo4j — Graph Model

```
NODES:
  ┌──────────────┐         ┌──────────────┐
  │   :Paper     │         │  :Keyword    │
  │              │         │              │
  │ paperId: STR │         │ keywordText: │
  │ title: STR   │         │   STR        │
  └──────┬───────┘         └──────┬───────┘
         │                        │
         │   HAS_KEYWORD          │
         │◄──────────────────────►│
         │   {relevanceScore:     │
         │    DOUBLE}             │
         │                        │
         └────────────────────────┘

RELATIONSHIP:
  (:Paper)-[:HAS_KEYWORD {relevanceScore}]->(:Keyword)
```

**Current node counts:**
- ~200+ Paper nodes
- ~1,400 Keyword nodes
- ~2,000+ HAS_KEYWORD relationships

### 4.4 Requirements Traceability Matrix

| FR ID | Requirement Name | Endpoint (BE) | Page/Component (FE) | Service (BE) | Priority |
|-------|-----------------|---------------|---------------------|--------------|----------|
| FR-AUTH-001 | Registration | `POST /api/auth/register` | RegisterPage | AuthServiceImpl | 🔴 High |
| FR-AUTH-002 | Login | `POST /api/auth/login` | LoginPage | AuthServiceImpl | 🔴 High |
| FR-AUTH-003 | Google OAuth | `POST /api/auth/google` | LoginPage | AuthServiceImpl | 🟠 Medium |
| FR-AUTH-004 | Forgot Password | `POST /api/auth/forgot-password` | ResetPasswordPage | AuthServiceImpl | 🔴 High |
| FR-AUTH-005 | Refresh Token | `POST /api/auth/refresh-token` | axiosClient interceptor | JwtTokenProvider | 🔴 High |
| FR-SEARCH-001 | Search Papers | `GET /api/v1/papers/search` | SearchPapers | PaperSearchOrchestrator | 🔴 High |
| FR-SEARCH-002 | Search Author | `GET /api/v1/papers/search/author` | SearchAuthor | AuthorSuggestionService | 🔴 High |
| FR-SEARCH-003 | Search Journal | `GET /api/v1/journals/search` | SearchJournal | JournalServiceImpl | 🔴 High |
| FR-SEARCH-004 | Advanced Filter | `GET /api/v1/papers/filter/advanced` | AdvancedFilter | PaperSearchService | 🟠 Medium |
| FR-SEARCH-005 | Paper Detail | `GET /api/v1/papers/{id}` | PaperDetailPage | PaperSearchOrchestrator | 🔴 High |
| FR-GRAPH-001 | Keyword-Paper Graph | `GET /api/graphs/paper/{id}` | Neo4jGraphCard | GraphService | 🔴 High |
| FR-GRAPH-002 | Graph Explorer | `POST /api/graphs/explore` | KeywordGraphExplorer | GraphService | 🟡 Low |
| FR-AI-001 | AI Summarization | `GET /api/v1/ai/summarize/{id}` | PaperDetailPage | AISummarizationService | 🟠 Medium |
| FR-AI-002 | Methodology Extraction | `GET /api/v1/ai/methodology/{id}` | PaperDetailPage | AISummarizationService | 🟡 Low |
| FR-AI-003 | Batch Analysis | `POST /api/v1/ai/batch-analyze` | SearchPapers | AISummarizationService | 🟡 Low |
| FR-BOOKMARK-001| CRUD Bookmarks | `POST/GET/DELETE /api/v1/bookmarks` | BookmarksView | BookmarkServiceImpl | 🔴 High |
| FR-BOOKMARK-002| Bulk Operations | `POST/DELETE /api/v1/bookmarks/bulk` | BulkActionBar | BookmarkServiceImpl | 🟠 Medium |
| FR-FOLLOW-001 | Follow/Unfollow | `POST/DELETE /api/v1/follows` | FollowDialog | FollowServiceImpl | 🟠 Medium |
| FR-NOTIF-001 | REST Notifications | `GET/PUT/DELETE /api/v1/notifications` | NotificationsPage | NotificationServiceImpl | 🔴 High |
| FR-NOTIF-002 | SSE Push | `GET /api/v1/notifications/stream` | NotificationBell | NotificationSseController | 🔴 High |
| FR-REPORT-001 | Keyword Trend Report | `POST /api/v1/reports/keyword-trend` | ReportsViewPage | ReportServiceImpl | 🟠 Medium |
| FR-REPORT-002 | Author Impact Report | `POST /api/v1/reports/author-impact` | ReportsViewPage | ReportServiceImpl | 🟡 Low |
| FR-REPORT-003 | Journal Quality Report | `POST /api/v1/reports/journal-quality` | ReportsViewPage | ReportServiceImpl | 🟡 Low |
| FR-CITATION-001| Single Citation | `GET /api/v1/papers/{id}/citation` | CitationExport | CitationService | 🔴 High |
| FR-CITATION-002| Bulk Citation | `POST /api/v1/papers/citations/export` | BulkActionBar | CitationService | 🟠 Medium |
| FR-IDEA-001 | Extract Keywords | `POST /api/v1/ideas/extract-keywords` | IdeaPage | IdeaAnalysisService | 🟡 Low |
| FR-IDEA-002 | Analyze Idea | `POST /api/v1/ideas/analyze` | IdeaPage | IdeaAnalysisService | 🟡 Low |
| FR-USER-001 | View/Edit Profile | `GET/PUT /api/users/me` | SettingsPage | UserServiceImpl | 🔴 High |
| FR-USER-002 | Change Password | `PUT /api/users/me/password` | ChangePasswordForm | UserServiceImpl | 🔴 High |
| FR-USER-003 | Account Upgrade | `POST /api/users/me/upgrade` | AcademicLimitAlert | UserServiceImpl | 🟠 Medium |
| FR-ADMIN-001 | User Management | `GET/PUT /api/v1/admin/users` | UserManagementPage | AdminServiceImpl | 🔴 High |
| FR-ADMIN-002 | Admin Dashboard | `GET /api/v1/admin/overview` | AdminOverviewPage | AdminOverviewService | 🟠 Medium |
| FR-ADMIN-003 | Database Stats | `GET /api/v1/admin/database-stats` | DatabaseViewPage | AdminOverviewService | 🟠 Medium |
| FR-ADMIN-004 | Manual Sync | `POST /api/v1/admin/sync/*` | SyncDataPage | DataSyncServiceImpl | 🔴 High |
| FR-ADMIN-005 | Bulk Sync | `POST/GET /api/v1/admin/sync/bulk` | SyncFloatingPanel | DataSyncServiceImpl | 🟠 Medium |
| FR-ADMIN-006 | Audit Log | `GET /api/v1/admin/audit-logs` | AdminAuditLogPage | AdminAuditLogInterceptor | 🟠 Medium |
| FR-ADMIN-007 | System Config | `GET/PUT /api/v1/admin/configs` | AdminConfigPage | AdminOverviewService | 🟠 Medium |
| FR-ADMIN-008 | PDF Requests | `GET/PUT /api/v1/admin/pdf-requests` | PdfRequestsPage | PdfRequestServiceImpl | 🟡 Low |
| FR-SYNC-001 | Scheduled Sync | (Cron jobs) | (Admin dashboard) | ScheduledDataSyncService | 🔴 High |
| FR-OVERVIEW-001| Public Overview | `GET /api/v1/overview` | LandingPage | OverviewStatisticsService | 🟠 Medium |

---

## DOCUMENT INFORMATION

| Item | Detail |
|------|--------|
| **Document Title** | Software Requirements Specification (SRS) — SCITRACK |
| **Version** | 1.0 |
| **Release Date** | July 28, 2026 |
| **Author** | SCITRACK Development Team |
| **Reference Standard** | IEEE 830-1998 |
| **Status** | Final |

---

*© 2026 SCITRACK Team. This document was created based on the actual codebase of SCITRACK system version 1.0.*
