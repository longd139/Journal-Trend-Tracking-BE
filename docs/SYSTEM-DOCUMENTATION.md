# SCITRACK — Tài Liệu Tổng Hợp Hệ Thống (System Documentation)

> **Phiên bản:** 1.0 | **Ngày tạo:** 2026-07-18 | **Ngày cập nhật cuối:** 2026-07-18
>
> Tài liệu này tổng hợp toàn bộ kiến trúc, công nghệ, và cấu trúc của hệ thống SCITRACK — AI-Powered Academic Research Analytics.

---

## MỤC LỤC

1. [Tổng Quan Dự Án](#1-tổng-quan-dự-án)
2. [Tech Stack](#2-tech-stack)
3. [Kiến Trúc Tổng Thể](#3-kiến-trúc-tổng-thể)
4. [Backend Chi Tiết](#4-backend-chi-tiết)
5. [Frontend Chi Tiết](#5-frontend-chi-tiết)
6. [API Endpoints](#6-api-endpoints)
7. [Cơ Sở Dữ Liệu](#7-cơ-sở-dữ-liệu)
8. [Bảo Mật & Xác Thực](#8-bảo-mật--xác-thực)
9. [Luồng Dữ Liệu Chính](#9-luồng-dữ-liệu-chính)
10. [Tích Hợp Bên Ngoài](#10-tích-hợp-bên-ngoài)
11. [Deployment](#11-deployment)
12. [Cấu Hình Môi Trường](#12-cấu-hình-môi-trường)
13. [Tồn Đọng & Kế Hoạch](#13-tồn-đọng--kế-hoạch)

---

## 1. TỔNG QUAN DỰ ÁN

**SCITRACK** — AI-Powered Academic Research Analytics. Ứng dụng full-stack giúp các nhà nghiên cứu tìm kiếm, phân tích, và trực quan hóa các bài báo học thuật với đồ thị từ khóa (keyword-graph exploration).

### Hai dự án con

| Dự án | Công nghệ | Port mặc định |
|-------|-----------|---------------|
| `Journal-Trend-Tracking-BE/` | Spring Boot 3.5.14, Java 21, Maven | `8080` |
| `Journal-Trend-Tracking-FE/` | React 18, Vite 6, Tailwind CSS v4 | `5173` |

### Mục tiêu cốt lõi

- Tìm kiếm bài báo khoa học theo từ khóa, tác giả, tạp chí
- Phân tích xu hướng nghiên cứu qua biểu đồ
- Khám phá mạng lưới từ khóa-paper dạng đồ thị (graph visualization)
- Bookmark bài báo yêu thích, theo dõi tác giả/tạp chí
- Tạo báo cáo phân tích (trend, author impact, journal quality)
- Admin quản lý người dùng, đồng bộ dữ liệu từ API bên ngoài, giám sát hệ thống

---

## 2. TECH STACK

### 2.1 Backend (Spring Boot)

| Loại | Công nghệ | Version |
|------|-----------|---------|
| Framework | Spring Boot | 3.5.14 |
| Ngôn ngữ | Java | 21 |
| Build Tool | Maven | — |
| Database chính | SQL Server (JPA/Hibernate) | 2022 |
| Database graph | Neo4j (Spring Data Neo4j) | AuraDB Cloud |
| Security | Spring Security + JWT (jjwt) | 0.12.3 |
| Cache | Caffeine | — |
| API Docs | SpringDoc OpenAPI (Swagger) | 2.8.13 |
| Email | Spring Boot Starter Mail (SMTP) | — |
| Monitoring | Spring Boot Actuator | — |
| Rate Limiting | Bucket4j | 8.10.1 |
| Image Upload | Cloudinary | 1.38.0 |
| PDF Processing | Apache PDFBox | 3.0.3 |
| Container | Docker + Docker Compose | — |

### 2.2 Frontend (React)

| Loại | Công nghệ | Version |
|------|-----------|---------|
| Framework | React | 18.3.1 |
| Build Tool | Vite | 6.3.5 |
| Styling | Tailwind CSS | 4.1.12 |
| Router | React Router DOM | 7.16.0 |
| State Management | Zustand | 5.0.14 |
| HTTP Client | Axios | 1.16.1 |
| UI Primitives | Radix UI | 50+ components |
| Icons | Lucide React | 0.487.0 |
| Charts | Recharts | 2.15.2 |
| Graph Visualization | vis-network + vis-data | 10.1.0 / 8.0.4 |
| Animation | Motion (Framer Motion) | 12.23.24 |
| Forms | React Hook Form | 7.55.0 |
| i18n | react-i18next + i18next | 26.3.1 |
| Google Auth | @react-oauth/google | 0.13.5 |
| Material UI (phụ) | MUI v7 | 7.3.5 |
| Toast | Sonner | 2.0.3 |
| Drag & Drop | react-dnd | 16.0.1 |
| Carousel | Embla Carousel React | 8.6.0 |
| Confetti | canvas-confetti | 1.9.4 |
| Theme | next-themes | 0.4.6 |

---

## 3. KIẾN TRÚC TỔNG THỂ

```
┌──────────────────────────────────────────────────────────────┐
│                        CLIENT (Browser)                      │
│  React 18 + Vite 6 + Tailwind v4 + shadcn/ui (Radix)        │
│  Port: 5173 (dev)                                            │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP/REST (Axios + JWT Bearer)
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                   SPRING BOOT API (Java 21)                  │
│  Port: 8080                                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌───────────────────┐    │
│  │  Controllers │ │   Services   │ │    Security       │    │
│  │   (38 files) │ │  (70+ files) │ │ JWT + CORS + RBAC │    │
│  └──────────────┘ └──────────────┘ └───────────────────┘    │
│                                                              │
│  ┌──────────────────────────┐ ┌──────────────────────────┐  │
│  │    SQL Server (JPA)      │ │   Neo4j (Graph - AuraDB) │  │
│  │  Users, Papers, Authors, │ │  Paper nodes, Keyword    │  │
│  │  Journals, Bookmarks,    │ │  nodes, HAS_KEYWORD     │  │
│  │  Follows, Sync Logs...   │ │  relationships           │  │
│  └──────────────────────────┘ └──────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              External API Integrations               │   │
│  │  OpenAlex | Semantic Scholar | DeepSeek AI | Gemini  │   │
│  │  CORE API | arXiv | Cloudinary | SMTP Email          │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### Kiến trúc Dual-Database

| Database | Config Class | Entity Package | Repository Package | Vai trò |
|----------|-------------|----------------|--------------------|--------|
| **SQL Server** | `JpaConfig.java` | `entity.jpa.*` | `repository.jpa.*` | Dữ liệu có cấu trúc: users, papers, authors, journals, bookmarks, follows, sync logs, usage tracking |
| **Neo4j** | `Neo4jConfig.java` | `entity.neo4j.*` | `repository.neo4j.*` | Đồ thị: Paper nodes, Keyword nodes, `HAS_KEYWORD` relationships → graph search + visualization |

Hai database độc lập — dữ liệu được đồng bộ vào cả hai khi sync từ API bên ngoài.

---

## 4. BACKEND CHI TIẾT

### 4.1 Cấu Trúc Package

```
src/main/java/com/sra/journal_tracking/
├── JournalTrackingApplication.java    # Entry point
├── config/                            # 14 config classes
│   ├── AppConfig.java                 # RestTemplate beans, DeepSeek config
│   ├── CacheConfig.java               # Caffeine cache config
│   ├── CloudinaryConfig.java          # Cloudinary image upload
│   ├── DatabaseInitializer.java       # DB seeding on startup
│   ├── JpaConfig.java                 # JPA repository scan isolation
│   ├── KeywordMigrationRunner.java    # Keyword migration
│   ├── Neo4jConfig.java               # Neo4j repository scan isolation
│   ├── OpenApiConfig.java             # OpenAPI/Swagger docs
│   ├── RateLimitConfig.java           # Bucket4j rate limit properties
│   ├── RateLimitInterceptor.java      # Per-request rate limit enforcement
│   ├── RequestMetricsCollector.java   # HTTP request metrics
│   ├── SchemaMigrationRunner.java     # Schema migration
│   ├── SwaggerConfig.java             # Swagger UI config
│   └── WebConfig.java                 # CORS, interceptors
├── controller/                        # 38 REST controllers
├── dto/                               # 100+ DTO classes
│   ├── admin/       (13 files)        # Admin-related DTOs
│   ├── ai/          (2 files)         # AI summarization DTOs
│   ├── analytics/   (3 files)         # Analytics DTOs
│   ├── auth/        (6 files)         # Auth DTOs
│   ├── author/      (8 files)         # Author DTOs
│   ├── bookmark/    (3 files)         # Bookmark DTOs
│   ├── collection/  (2 files)         # Collection DTOs
│   ├── common/      (1 file)          # BulkDeleteRequest
│   ├── dashboard/   (3 files)         # Dashboard DTOs
│   ├── follow/      (2 files)         # Follow DTOs
│   ├── history/     (1 file)          # Reading history DTOs
│   ├── journal/     (4 files)         # Journal DTOs
│   ├── keyword/     (1 file)          # Hot keyword DTOs
│   ├── notification/(2 files)         # Notification DTOs
│   ├── overview/    (2 files)         # Overview DTOs
│   ├── paper/       (12 files)        # Paper DTOs (search, filter, detail, etc.)
│   ├── pdf/         (6 files)         # PDF request DTOs
│   ├── rating/      (1 file)          # Paper rating DTOs
│   ├── recommendation/(2 files)       # Recommendation DTOs
│   ├── report/      (4 files)         # Report DTOs
│   ├── response/    (3 files)         # API response wrappers
│   ├── search/      (6 files)         # Search DTOs
│   ├── sync/        (3 files)         # Sync DTOs
│   ├── trend/       (2 files)         # Publication trend DTOs
│   ├── trends/      (1 file)          # Weekly breakout DTOs
│   └── user/        (3 files)         # User DTOs
├── entity/
│   ├── jpa/         (27 entities)     # SQL Server entities
│   └── neo4j/       (2 entities)      # Neo4j entities
├── exception/       (8 files)         # Exception handling
├── repository/
│   ├── jpa/         (29 repositories) # JPA repositories
│   └── neo4j/       (3 repositories)  # Neo4j repositories
├── security/        (7 files)         # JWT + Spring Security
└── service/         (70+ files)       # Business logic layer
```

### 4.2 Danh Sách Controllers (38)

| Controller | Endpoint Prefix | Vai trò |
|-----------|----------------|---------|
| `AuthController` | `/api/auth/**` | Đăng ký, đăng nhập, refresh token, quên mật khẩu |
| `UserController` | `/api/users/**` | CRUD profile, đổi mật khẩu, nâng cấp role |
| `PaperSearchController` | `/api/v1/papers/**` | Tìm kiếm papers, advanced filter, paper detail |
| `SearchController` | `/api/v1/search/**` | Quick search, trending keywords, categories |
| `GraphController` | `/api/graphs/**` | Neo4j graph data (nodes, edges, keyword explorer) |
| `KeywordController` | `/api/v1/keywords/**` | Keyword stats, related keywords, expansion |
| `JournalController` | `/api/v1/journals/**` | Journal search, suggestions, top journals |
| `BookmarkController` | `/api/v1/bookmarks/**` | CRUD bookmarks, bulk operations |
| `BookmarkCollectionController` | `/api/v1/collections/**` | Bookmark collections CRUD |
| `FollowController` | `/api/v1/follows/**` | Follow/unfollow authors/journals |
| `NotificationController` | `/api/v1/notifications/**` | CRUD notifications, mark read, bulk delete |
| `NotificationSseController` | `/api/v1/notifications/stream` | SSE real-time push |
| `ReadingHistoryController` | `/api/v1/reading-history/**` | Reading history CRUD |
| `ReportController` | `/api/v1/reports/**` | Generate reports (keyword trend, author impact, journal quality) |
| `PaperRecommendationController` | `/api/v1/papers/recommendations` | Personalized + similar papers |
| `PaperRatingController` | `/api/v1/papers/ratings/**` | Rate papers |
| `AIController` | `/api/v1/ai/**` | AI summarization, methodology, batch analysis |
| `PdfRequestController` | `/api/v1/pdf-requests/**` | Request paper PDFs |
| `IdeaController` | `/api/v1/ideas/**` | Research idea analysis |
| `AnalyticsController` | `/api/v1/analytics/**` | Country/institution analytics |
| `PublicationTrendController` | `/api/v1/publication-trends/**` | Publication trend data |
| `ResearchTopicController` | `/api/v1/research-topics/**` | Research topic CRUD |
| `WeeklyBreakoutController` | `/api/v1/weekly-breakout/**` | Weekly breakout papers |
| `OverviewController` | `/api/v1/overview/**` | Public overview statistics |
| `UserOverviewController` | `/api/v1/user-overview/**` | User-specific dashboard |
| `DashboardController` | `/api/v1/dashboard/**` | Dashboard widgets |
| `DataSyncController` | `/api/v1/sync/**` | Manual sync trigger (OpenAlex, Semantic Scholar, arXiv, CORE) |
| `AdminSyncController` | `/api/v1/admin/sync/**` | Bulk sync with progress tracking |
| `AdminUserController` | `/api/v1/admin/users/**` | User management (admin) |
| `AdminOverviewController` | `/api/v1/admin/overview/**` | Admin dashboard stats |
| `AdminDataSourceController` | `/api/v1/admin/data-sources/**` | Data source management |
| `AdminConfigController` | `/api/v1/admin/configs/**` | System configuration |
| `AdminAuditLogController` | `/api/v1/admin/audit-logs/**` | Audit trail |
| `AdminPdfRequestController` | `/api/v1/admin/pdf-requests/**` | Admin duyệt PDF requests |
| `HealthController` | `/api/health/**` | Health check |
| `CacheManageController` | `/api/v1/cache/**` | Cache management |
| `BackgroundController` | `/api/v1/backgrounds/**` | User background image |
| `GoogleTestController` | `/api/test/**` | Google OAuth test |

### 4.3 Danh Sách Services Chính (70+)

| Service | Vai trò |
|---------|---------|
| **Core Search** | |
| `PaperSearchOrchestrator` | Điều phối search: Neo4j → SQL → OpenAlex fallback |
| `PaperSearchServiceImpl` | Search implementation + usage limit check |
| `OpenAlexFallbackSearchService` | Live OpenAlex API fallback search |
| `KeywordExpansionService` | Mở rộng từ khóa tìm kiếm |
| `SearchKeywordService` | Ghi nhận lịch sử search → hot keyword ranking |
| `SearchBackfillService` | Backfill dữ liệu thiếu |
| `GraphSearchProcessor` | Neo4j graph search processing |
| `GraphSearchTaskTracker` | Track async graph search tasks |
| **Auth & User** | |
| `AuthServiceImpl` | Register, login, email verification, password reset |
| `UserServiceImpl` | Profile CRUD, role upgrade |
| `CustomUserDetailsService` | Load user for Spring Security |
| **Data Sync** | |
| `DataSyncServiceImpl` | Fetch OpenAlex + Semantic Scholar → write to both DBs |
| `ScheduledDataSyncService` | Cron job: daily 2 AM sync |
| `BulkSyncProgressTracker` | In-memory bulk sync progress tracking |
| **Graph** | |
| `GraphService` | Neo4j Cypher queries: save, search, graph build, stats |
| `KeywordExtractionService` | Extract keywords from text |
| **AI** | |
| `AIClient` (interface) | Abstract AI provider interface |
| `DeepSeekClient` | OpenAI-compatible HTTP client (ai-box.vn) |
| `AISummarizationService` | AI summarization + methodology + batch analysis |
| `GeminiService` | Keyword expansion via AI (with local fallback) |
| `IdeaAnalysisService` | Research idea analysis |
| **Bookmarks & Collections** | |
| `BookmarkServiceImpl` | Bookmark CRUD + bulk operations |
| `BookmarkCollectionServiceImpl` | Collection CRUD |
| **Notifications** | |
| `NotificationServiceImpl` | Notification CRUD + SSE push |
| `NotificationTriggerService` | Trigger NEW_PAPER, TREND_ALERT, SYSTEM, UPGRADE_PROMPT |
| `NotificationEventPublisher` | Spring event publisher for notifications |
| `NotificationEventListener` | Async event listener |
| **Reports** | |
| `ReportServiceImpl` | Report generation (keyword trend, author impact, journal quality) |
| **Recommendations** | |
| `PaperRecommendationServiceImpl` | Hybrid: content-based (Neo4j) + collaborative + cold-start |
| **Other Services** | |
| `CitationService` | BibTeX, RIS, APA, MLA citation export |
| `EmailServiceImpl` | Async HTML email (verification, reset password) |
| `FollowServiceImpl` | Follow/unfollow authors/journals |
| `ReadingHistoryServiceImpl` | Reading history tracking |
| `PaperRatingServiceImpl` | Paper rating + `RatingCalculator` |
| `PdfRequestServiceImpl` | PDF request management |
| `PdfExtractionService` | PDFBox text extraction |
| `AnalyticsServiceImpl` | Country/institution analytics |
| `DashboardServiceImpl` | Dashboard widget data |
| `AdminServiceImpl` | Admin user CRUD |
| `AdminOverviewServiceImpl` | Admin dashboard stats |
| `OverviewStatisticsServiceImpl` | Public overview stats |
| `UserOverviewServiceImpl` | User-specific dashboard |
| `TrendingTopicSyncService` | Trending topic detection (12h cycle) |
| `TrendingKeywordService` | Hot keyword ranking |
| `WeeklyBreakoutServiceImpl` | Weekly breakout papers |
| `JournalServiceImpl` | Journal search + enrichment |
| `JournalQuickStatsServiceImpl` | Journal quick stats |
| `JournalEnrichmentService` | Enrich journal data |
| `AuthorSuggestionServiceImpl` | Author suggestions |
| `AuthorQuickStatsService` | Author quick stats |
| `KeywordQuickStatsServiceImpl` | Keyword quick stats |
| `UserSearchHistoryService` | User search history |
| `PaperCacheService` | Paper cache management |
| `UsageNotificationService` | Usage limit notifications |
| `RateLimitInterceptor` | Bucket4j token-bucket rate limiting |

### 4.4 Danh Sách Entities

#### JPA Entities (SQL Server) — 36 entities

| Entity | Bảng | Mô tả |
|--------|------|-------|
| `User` | `[USER]` | Người dùng (id, email, password, fullName, avatar, institution, role, isActive) |
| `Role` | `ROLE` | Vai trò (ADMIN, RESEARCHER, ACADEMIC_USER) |
| `UserSession` | `USER_SESSION` | Phiên đăng nhập (token hash, refresh token hash) |
| `UserUsage` | `USER_USAGE` | Lượt sử dụng hàng tháng (search/view/chart counts) |
| `VerificationToken` | `VERIFICATION_TOKEN` | Token xác thực email / reset password |
| `ResearchPaper` | `RESEARCH_PAPER` | Bài báo nghiên cứu (title, abstract, DOI, AI summary, methodology) |
| `Author` | `AUTHOR` | Tác giả (hIndex, totalCitations, i10Index, worksCount, country) |
| `PaperAuthor` | `PAPER_AUTHOR` | Liên kết paper-author (composite key, authorOrder, isCorresponding) |
| `Journal` | `JOURNAL` | Tạp chí khoa học (ISSN, impactFactor, quartile Q1-Q4) |
| `Keyword` | `KEYWORD` | Từ khóa (normalized text, paper count) |
| `PaperKeyword` | `PAPER_KEYWORD` | Liên kết paper-keyword (composite key, relevanceScore) |
| `ResearchField` | `RESEARCH_FIELD` | Lĩnh vực nghiên cứu (hierarchical tree: parentFieldId) |
| `Bookmark` | `BOOKMARK` | Bookmark của user (paper/keyword/collection targets) |
| `BookmarkCollection` | `BOOKMARK_COLLECTION` | Bộ sưu tập bookmark |
| `Follow` | `FOLLOW` | Theo dõi tác giả/tạp chí/topic/keyword |
| `Notification` | `NOTIFICATION` | Thông báo |
| `ReadingHistory` | `READING_HISTORY` | Lịch sử đọc |
| `PaperRating` | `PAPER_RATING` | Đánh giá paper (1-5 sao) |
| `Report` | `REPORT` | Báo cáo đã tạo (format: pdf/csv) |
| `PdfRequest` | `PDF_REQUEST` | Yêu cầu PDF (status: pending/fulfilled/rejected) |
| `SyncLog` | `SYNC_LOG` | Log đồng bộ dữ liệu |
| `ApiSource` | `API_SOURCE` | Nguồn API (OpenAlex, Semantic Scholar, arXiv, CORE) |
| `AuditLog` | `AUDIT_LOG` | Nhật ký admin |
| `SystemConfig` | `SYSTEM_CONFIG` | Cấu hình hệ thống (key-value) |
| `SearchKeyword` | `SEARCH_KEYWORD` | Lịch sử từ khóa tìm kiếm (hot keyword tracking) |
| `TrendingTopic` | `TRENDING_TOPIC` | Chủ đề trending |
| `UserSearchHistory` | `USER_SEARCH_HISTORY` | Lịch sử tìm kiếm user |
| `DashboardWidget` | `DASHBOARD_WIDGET` | Widget dashboard (config dạng JSON) |
| `AutoSyncKeyword` | `AUTO_SYNC_KEYWORD` | Từ khóa auto-sync |
| `ResearchTopic` | `RESEARCH_TOPIC` | Chủ đề nghiên cứu (trendScore, isTrending) |
| `PublicationTrend` | `PUBLICATION_TREND` | Xu hướng xuất bản (monthly/quarterly/yearly) |
| `TopicKeyword` | `TOPIC_KEYWORD` | Liên kết topic-keyword (composite key, weight) |
| `PaperCache` | `PAPER_CACHE` | Cache paper từ OpenAlex |
| `KeywordTrendCache` | `KEYWORD_TREND_CACHE` | Cache keyword trend report (JSON) |
| `IdeaAnalysis` | `IDEA_ANALYSIS` | Phân tích ý tưởng nghiên cứu |
| `PaperEvaluationCache` | `PAPER_EVALUATION_CACHE` | Cache đánh giá AI cho idea analysis |

#### Neo4j Entities (Graph) — 2 entities

| Entity | Node Label | Mô tả |
|--------|-----------|-------|
| `Neo4jPaper` | `Paper` | Node paper trong đồ thị |
| `Neo4jKeyword` | `Keyword` | Node keyword trong đồ thị |

### 4.5 Repositories

#### JPA Repositories (29)

`ApiSourceRepository`, `AuditLogRepository`, `AuthorRepository` (JPA), `AutoSyncKeywordRepository`, `BookmarkCollectionRepository`, `BookmarkRepository`, `DashboardWidgetRepository`, `FollowRepository`, `IdeaAnalysisRepository`, `JournalRepository` (JPA), `KeywordRepository` (JPA), `KeywordTrendCacheRepository`, `NotificationRepository`, `PaperAuthorRepository`, `PaperCacheRepository`, `PaperEvalCacheRepository`, `PaperKeywordRepository`, `PaperRatingRepository`, `PdfRequestRepository`, `PublicationTrendRepository`, `ReadingHistoryRepository`, `ReportRepository`, `ResearchFieldRepository`, `ResearchPaperRepository`, `ResearchTopicRepository`, `RoleRepository`, `SearchKeywordRepository`, `SyncLogRepository`, `SystemConfigRepository`, `TopicKeywordRepository`, `TrendingTopicRepository`, `UserRepository`, `UserSearchHistoryRepository`, `UserSessionRepository`, `UserUsageRepository`, `VerificationTokenRepository`

#### Neo4j Repositories (3)

`AuthorRepository` (Neo4j), `JournalRepository` (Neo4j), `KeywordRepository` (Neo4j)

### 4.6 Security

| File | Vai trò |
|------|---------|
| `SecurityConfig.java` | Cấu hình Spring Security: public endpoints, JWT filter, CORS |
| `JwtAuthenticationFilter.java` | Filter trích xuất JWT từ `Authorization: Bearer` header |
| `JwtTokenProvider.java` | Tạo + validate JWT token |
| `JwtAuthenticationEntryPoint.java` | Xử lý 401 Unauthorized |
| `CustomUserDetails.java` | Wrapper User entity cho Spring Security |
| `CustomUserDetailsService.java` | Load user từ DB bằng email |
| `AdminAuditLogInterceptor.java` | Ghi log admin actions |

**Public endpoints** (không cần auth):
- `/api/auth/**` — register, login, refresh token, forgot/reset password
- `/api/test/**` — Google OAuth test
- `/api/public/**` — public data
- `/api/health/**` — health check
- `/swagger-ui/**`, `/v3/api-docs/**` — API docs

### 4.7 Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) xử lý tập trung. Sử dụng `AppException` + `ErrorCode` enum.

| Exception | HTTP Status |
|-----------|-------------|
| `AppException` | Theo ErrorCode |
| `ResourceNotFoundException` | 404 |
| `PaperNotFoundException` | 404 |
| `UnauthorizedAccessException` | 401 |
| `UsageLimitExceededException` | 429 |
| `RateLimitExceededException` | 429 |

---

## 5. FRONTEND CHI TIẾT

### 5.1 Cấu Trúc Thư Mục

```
src/
├── main.jsx                            # Entry: GoogleOAuthProvider + RouterProvider + Toaster
├── app/
│   ├── router.jsx                      # Toàn bộ route definition (React Router v7, lazy loading)
│   └── providers/
│       ├── index.js                    # Provider exports
│       └── RouterProvider.jsx          # Router wrapper
├── features/                           # Tổ chức theo feature (page + api riêng)
│   ├── auth/                           # AuthPage, LoginPage, RegisterPage, ResetPasswordPage, AuthLayout, api.js
│   ├── landing/                        # LandingPage, FeaturedSlider
│   ├── overview/                       # OverviewController, UserOverviewPage, HealthCheckToast, api.js
│   ├── search/                         # SearchPapers, SearchJournal, SearchAuthor, PaperDetailPage
│   │                                   # + Neo4jGraphCard, AuthorSuggestions, WeeklyBreakout, SimilarPapers
│   │                                   # + CitationExport, PaperDetailDialog, AdvancedFilter
│   │                                   # + AuthorCoAuthors, AuthorTimeline, AuthorResearchFocus
│   │                                   # + KeywordGraphExplorer, RelatedTrends, KeywordQuickStats
│   │                                   # + PaperItemCard, TopPapers, AuthorQuickStats
│   │                                   # + paper.api.js, journal.api.js, author.api.js, trend.api.js, graph.api.js
│   ├── bookmarks/                      # BookmarksView, BulkActionBar, CollectionsPanel, api.js, collectionsApi.js
│   ├── follows/                        # FollowsView, FollowCard, FollowDialog, FollowButton, FollowCardSkeleton, api.js
│   ├── history/                        # ReadingHistoryPage
│   ├── notifications/                  # NotificationsPage, NotificationBell, api.js
│   ├── reports/                        # ReportsViewPage, GeneratorCard, ReportHistoryTable
│   │   └── components/                 # KeywordTrendResult, AuthorImpactResult, JournalQualityResult, ExportButtons
│   │   + api.js, config.js
│   ├── settings/                       # SettingsPage, ChangePasswordForm, NotificationSettings
│   ├── analytics/                      # AnalyticsPage, api.js
│   ├── admin/                          # UserManagementPage, DatabaseViewPage, SyncDataPage, AdminConfigPage
│   │                                   # + AdminOverviewPage, AdminAuditLogPage, PdfRequestsPage
│   │                                   # + AnalyticsView, SyncFloatingPanel, api.js, userStore.js
│   ├── idea/                           # IdeaPage, IdeaAnalysisFloatingPanel, api.js
│   └── user/                           # store.js (useAuthStore), api.js, schema.js, AcademicLimitAlert
├── components/
│   ├── ui/                             # 50+ shadcn/ui-style components (Radix + Tailwind)
│   │   ├── utils.js                    # cn() utility (clsx + tailwind-merge)
│   │   └── use-mobile.js              # Mobile detection hook
│   ├── common/                         # LanguageSwitcher, SupportDialog
│   ├── figma/                          # ImageWithFallback (Figma asset bridge)
│   ├── prisma/                         # Animation: AnimatedLetter, ScitrackSLogo, WordsPullUp, WordsPullUpMultiStyle
│   ├── KeepAlive.jsx                   # Session keep-alive
│   ├── SearchWithHistory.jsx           # Search bar with history
│   └── SharedUI.jsx                    # Reusable atoms: GlowBadge, StatusPill, StatCard, SectionBadge
├── shared/
│   └── layouts/
│       └── MainLayout.jsx              # DashboardLayout: sidebar + topbar + <Outlet/>
├── lib/
│   ├── apiClient.js                    # Axios instance + auth interceptor + language header
│   └── api/                            # health.api.js, ai.api.js
├── store/
│   └── useSyncStore.js                 # Bulk sync state (tasks, progress polling)
├── hooks/                              # useLocalization, useTheme, useGraphSearch, useStaleWhileRevalidate
├── utils/                              # localization.js, citationGenerators.js
├── constants/
│   └── mockData.js                     # Mock data + test accounts
├── i18n/
│   ├── index.js                        # i18next config: 2 languages (en, vi), 12 namespaces
│   └── locales/{en,vi}/                # 12 namespaces: common, auth, dashboard, reports, analytics,
│                                       #   search, settings, graph, landing, follow, support, admin
├── styles/
│   └── index.css                       # Tailwind v4 + theme CSS variables
├── pages/                              # HomePage.jsx, NotFoundPage.jsx (legacy wrappers)
└── services/                           # auth.services.js (legacy)
```

### 5.2 Hệ Thống Routes

#### Public Routes

| Path | Component | Mô tả |
|------|-----------|-------|
| `/` | `LandingPage` | Landing page giới thiệu sản phẩm |
| `/login` | `LoginPage` | Full-screen split layout đăng nhập |
| `/auth` | `AuthPage` | Auth page với left panel tĩnh |
| `/register` | `RegisterPage` | Đăng ký tài khoản |
| `/reset-password` | `ResetPasswordPage` | Quên mật khẩu |
| `*` | `NotFoundPage` | 404 Not Found |

#### Dashboard Routes — `/:roleName/:page`

| Path | Allowed Roles | Component |
|------|--------------|-----------|
| `/:roleName/overview` | Tất cả | `OverviewController` → UserOverviewPage / AdminOverviewPage |
| `/:roleName/search` | researcher, academic_user | `SearchPapers` |
| `/:roleName/journal-search` | researcher, academic_user | `SearchJournal` |
| `/:roleName/search-author` | researcher, academic_user | `SearchAuthor` |
| `/:roleName/papers/:paperId` | researcher, academic_user | `PaperDetailPage` |
| `/:roleName/bookmarks` | researcher, academic_user | `BookmarksView` |
| `/:roleName/follows` | researcher, academic_user | `FollowsView` |
| `/:roleName/notifications` | researcher, academic_user | `NotificationsPage` |
| `/:roleName/reading-history` | researcher, academic_user | `ReadingHistoryPage` |
| `/:roleName/reports` | researcher, academic_user | `ReportsViewPage` |
| `/:roleName/ideas` | researcher, academic_user | `IdeaPage` |
| `/:roleName/settings` | Tất cả | `SettingsPage` |
| `/:roleName/users` | admin | `UserManagementPage` |
| `/:roleName/database` | admin | `DatabaseViewPage` |
| `/:roleName/sync-data` | admin | `SyncDataPage` |
| `/:roleName/audit-logs` | admin | `AdminAuditLogPage` |
| `/:roleName/configs` | admin | `AdminConfigPage` |
| `/:roleName/pdf-requests` | admin | `PdfRequestsPage` |

### 5.3 State Management

| Store | File | Công nghệ | Mục đích |
|-------|------|-----------|----------|
| `useAuthStore` | `features/user/store.js` | Zustand + persist (localStorage) | Auth tokens (accessToken, refreshToken), user info, preferredLanguage, backgroundUrl |
| `useSyncStore` | `store/useSyncStore.js` | Zustand | Bulk sync tasks + progress polling (2.5s interval) |
| `useNotificationStore` | `store/useNotificationStore.js` | Zustand | Unread notification count + PDF pending count (polls mỗi 30s) |
| `useIdeaAnalysisStore` | `store/useIdeaAnalysisStore.js` | Zustand | Background idea analysis task (fire-and-forget) |
| `userStore` | `features/admin/userStore.js` | Zustand (local) | Admin user management state (CRUD, filtering) |

Không sử dụng React Context providers — tất cả state management qua Zustand.

### 5.4 UI Components (50+ shadcn/ui-style)

`accordion`, `alert`, `alert-dialog`, `aspect-ratio`, `avatar`, `badge`, `breadcrumb`, `button`, `calendar`, `card`, `carousel`, `chart`, `checkbox`, `collapsible`, `command`, `context-menu`, `dialog`, `drawer`, `dropdown-menu`, `form`, `hover-card`, `input`, `input-otp`, `label`, `menubar`, `navigation-menu`, `pagination`, `popover`, `progress`, `radio-group`, `resizable`, `scroll-area`, `select`, `separator`, `sheet`, `sidebar`, `skeleton`, `slider`, `sonner`, `switch`, `table`, `tabs`, `textarea`, `toggle`, `toggle-group`, `tooltip`

Tất cả đều dùng Radix UI primitives + Tailwind classes. Utility `cn()` trong `utils.js` merge Tailwind classes qua `clsx` + `tailwind-merge`.

### 5.5 Theme & Design System

**Dark-only theme.** CSS custom properties:
- `--background: #0B1020` (deep navy)
- `--card: #1B2235`
- `--primary: #4F8CFF` (blue)
- `--accent: #00D1B2` (teal)
- Fonts: **Outfit** (headings), **Inter** (body), **JetBrains Mono** (data/monospace)
- Animation: **Framer Motion** (`motion/react`) xuyên suốt các trang

### 5.6 i18n (Đa Ngôn Ngữ)

- **2 ngôn ngữ:** English (en) + Vietnamese (vi)
- **13 namespaces:** `common`, `auth`, `dashboard`, `reports`, `analytics`, `search`, `settings`, `graph`, `landing`, `follow`, `support`, `admin`, `idea`
- Detection: `localStorage → navigator → htmlTag`
- Axios gửi `Accept-Language` header từ `localStorage('preferredLanguage')`
- Localization utilities: `formatDate()`, `formatNumber()`, `formatCompactNumber()`, `formatRelativeTime()` qua `Intl` APIs

---

## 6. API ENDPOINTS

### 6.1 Auth (`/api/auth`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `POST` | `/api/auth/register` | No | Đăng ký + auto-login |
| `POST` | `/api/auth/login` | No | Đăng nhập → JWT + refresh token |
| `POST` | `/api/auth/refresh-token` | No | Làm mới access token |
| `POST` | `/api/auth/logout` | No | Đăng xuất, xóa session |
| `POST` | `/api/auth/forgot-password` | No | Gửi email reset password |
| `POST` | `/api/auth/reset-password` | No | Đặt lại mật khẩu |
| `POST` | `/api/auth/google` | No | Google OAuth login |

### 6.2 Users (`/api/users`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `GET` | `/api/users/me` | Yes | Profile người dùng hiện tại |
| `PUT` | `/api/users/me` | Yes | Cập nhật profile |
| `PUT` | `/api/users/me/password` | Yes | Đổi mật khẩu |
| `POST` | `/api/users/me/upgrade` | Yes (ACADEMIC_USER) | Nâng cấp → RESEARCHER |
| `GET` | `/api/users` | Yes (ADMIN) | Danh sách users |
| `GET` | `/api/users/{id}` | Yes (ADMIN) | Chi tiết user |
| `PUT` | `/api/users/{id}/status` | Yes (ADMIN) | Enable/disable user |
| `PUT` | `/api/users/{id}/role` | Yes (ADMIN) | Đổi role |

### 6.3 Papers Search (`/api/v1/papers`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `GET` | `/api/v1/papers/search` | Yes | Full-text search với keyword |
| `GET` | `/api/v1/papers/search/author` | Yes | Search theo tác giả |
| `GET` | `/api/v1/papers/search/journal` | Yes | Search theo journal |
| `GET` | `/api/v1/papers/filter/advanced` | Yes (RESEARCHER+) | Advanced filter |
| `GET` | `/api/v1/papers/sorted` | Yes | Search + sort (relevance/citations/title/date) |
| `GET` | `/api/v1/papers/{paperId}` | Yes | Paper detail + AI summary |
| `GET` | `/api/v1/papers/{paperId}/citation` | Yes | Citation export (BibTeX/RIS/APA/MLA) |
| `POST` | `/api/v1/papers/citations/export` | Yes | Bulk citation export |
| `GET` | `/api/v1/papers/usage` | Yes | Thống kê usage còn lại |
| `GET` | `/api/v1/papers/recommendations` | Yes | Personalized recommendations |
| `GET` | `/api/v1/papers/{paperId}/similar` | Yes | Similar papers |

### 6.4 AI (`/api/v1/ai`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `GET` | `/api/v1/ai/summarize/{paperId}` | Yes | AI tóm tắt abstract |
| `GET` | `/api/v1/ai/methodology/{paperId}` | Yes | Trích xuất methodology |
| `POST` | `/api/v1/ai/batch-analyze` | Yes | Phân tích batch papers + cross-paper insight |

### 6.5 Graph (`/api/graphs`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `GET` | `/api/graphs/paper/{paperId}` | No | Neo4j keyword-paper graph |
| `GET` | `/api/graphs/keyword/{keyword}` | No | Graph từ keyword |
| `GET` | `/api/graphs/explore` | No | Graph explorer |

### 6.6 Bookmarks (`/api/v1/bookmarks`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `GET` | `/api/v1/bookmarks` | Yes | Danh sách bookmarks |
| `POST` | `/api/v1/bookmarks` | Yes | Thêm bookmark |
| `DELETE` | `/api/v1/bookmarks/{id}` | Yes | Xóa bookmark |
| `POST` | `/api/v1/bookmarks/bulk` | Yes | Thêm hàng loạt |
| `DELETE` | `/api/v1/bookmarks/bulk` | Yes | Xóa hàng loạt |

### 6.7 Notifications (`/api/v1/notifications`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `GET` | `/api/v1/notifications` | Yes | Danh sách thông báo |
| `GET` | `/api/v1/notifications/stream` | Yes | SSE real-time stream |
| `PUT` | `/api/v1/notifications/{id}/read` | Yes | Đánh dấu đã đọc |
| `DELETE` | `/api/v1/notifications/bulk` | Yes | Xóa hàng loạt |

### 6.8 Reports (`/api/v1/reports`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `POST` | `/api/v1/reports/keyword-trend` | Yes | Báo cáo xu hướng từ khóa |
| `POST` | `/api/v1/reports/author-impact` | Yes | Báo cáo tác động tác giả |
| `POST` | `/api/v1/reports/journal-quality` | Yes | Báo cáo chất lượng tạp chí |
| `GET` | `/api/v1/reports/history` | Yes | Lịch sử báo cáo |

### 6.9 Idea Analysis (`/api/v1/ideas`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `POST` | `/api/v1/ideas/extract-keywords` | Yes | Trích xuất keywords từ ý tưởng |
| `POST` | `/api/v1/ideas/analyze` | Yes | Phân tích ý tưởng nghiên cứu (gap analysis, literature review) |
| `GET` | `/api/v1/ideas/history` | Yes | Lịch sử phân tích |
| `GET` | `/api/v1/ideas/history/{id}` | Yes | Chi tiết một phân tích |
| `DELETE` | `/api/v1/ideas/history/{id}` | Yes | Xóa lịch sử phân tích |

### 6.10 Admin

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| `GET` | `/api/v1/admin/overview` | ADMIN | Admin dashboard stats |
| `GET` | `/api/v1/admin/users` | ADMIN | Quản lý users |
| `GET` | `/api/v1/admin/database-stats` | ADMIN | SQL Server + Neo4j stats |
| `POST` | `/api/v1/admin/sync/openalex` | ADMIN | Manual sync OpenAlex |
| `POST` | `/api/v1/admin/sync/semantic-scholar` | ADMIN | Manual sync Semantic Scholar |
| `POST` | `/api/v1/admin/sync/bulk` | ADMIN | Bulk sync theo keywords |
| `GET` | `/api/v1/admin/sync/progress/{taskId}` | ADMIN | Bulk sync progress |
| `GET` | `/api/v1/admin/audit-logs` | ADMIN | Audit trail |
| `GET` | `/api/v1/admin/configs` | ADMIN | System configurations |
| `PUT` | `/api/v1/admin/configs/{key}` | ADMIN | Update config |
| `GET` | `/api/v1/admin/pdf-requests` | ADMIN | Duyệt PDF requests |

---

## 7. CƠ SỞ DỮ LIỆU

### 7.1 SQL Server (JPA)

**Connection string:** `jdbc:sqlserver://{host}:{port};databaseName={db};encrypt=false;trustServerCertificate=true;`

**HikariCP Pool config:**
- `maximum-pool-size: 10`
- `minimum-idle: 3`
- `connection-timeout: 10000ms`
- `idle-timeout: 300000ms`
- `max-lifetime: 600000ms`
- `keepalive-time: 120000ms`
- `leak-detection-threshold: 30000ms`

**JPA config:**
- `ddl-auto: none` (schema quản lý qua `schema.sql`)
- `sql.init.mode: always`
- `PhysicalNamingStrategyStandardImpl` — bảo toàn tên cột snake_case
- `open-in-view: false`
- `default_batch_fetch_size: 50`
- Primary keys: `UUID` với `GenerationType.UUID`

**Bảng chính (36 entities + views + stored procedures):** Xem danh sách entities ở section 4.4.

**Database Views (4):**
- `V_USER_DETAIL` — Thông tin user kèm role
- `V_TRENDING_TOPICS` — Danh sách trending topics
- `V_ACADEMIC_USAGE_CURRENT` — Thống kê usage hiện tại của academic users
- `V_SYNC_HISTORY` — Lịch sử đồng bộ dữ liệu

**Stored Procedures (4):**
- `SP_CHECK_AND_INCREMENT_USAGE` — Kiểm tra & tăng usage, cảnh báo 80% limit
- `SP_CLEANUP_EXPIRED_SESSIONS` — Dọn session hết hạn
- `SP_RESET_MONTHLY_USAGE` — Reset usage đầu tháng cho academic users
- `SP_REFRESH_TOPIC_TRENDS` — Tính lại TrendScore và IsTrending

### 7.2 Neo4j (Graph - AuraDB Cloud)

**Connection:** `neo4j+s://{instance}.databases.neo4j.io`

**Node types:**
- `Paper` — đại diện bài báo (paperId, title)
- `Keyword` — đại diện từ khóa (keywordText)

**Relationships:**
- `HAS_KEYWORD` — Paper → Keyword (có `relevanceScore`)

**Sử dụng:** Graph visualization trên FE (vis-network) + keyword-paper search + recommendation engine.

---

## 8. BẢO MẬT & XÁC THỰC

### 8.1 Luồng Auth (End-to-End)

```
FE LoginPage → authAPI.login(email, password)
  → BE AuthController → AuthService → JwtTokenProvider.generate()
  → Response: { accessToken, refreshToken, role }
  → FE useAuthStore (Zustand persist → localStorage "Journal-Tracking-System")
  → FE sessionStorage.setItem('userRole', role)
  → Navigate to /{role}/overview
  → axiosClient interceptor: đọc useAuthStore.getState().accessToken
  → Gắn Authorization: Bearer <token> vào mọi request
  → Gắn Accept-Language từ localStorage('preferredLanguage')
```

### 8.2 JWT Config

| Property | Default | Mô tả |
|----------|---------|-------|
| `app.jwtSecret` | (base64 key) | Secret key ký JWT |
| `app.jwtExpirationInMs` | `86400000` (24h) | Access token TTL |
| `app.refresh-token-expiration-ms` | `604800000` (7d) | Refresh token TTL |
| `app.reset-token-expiration-ms` | `900000` (15m) | Password reset token TTL |
| `app.verification-token-expiration-ms` | `86400000` (24h) | Email verification token TTL |

### 8.3 Phân Quyền (3 Roles)

| Role | SessionStorage value | Quyền hạn |
|------|---------------------|-----------|
| **ADMIN** | `admin` | Quản lý users, trigger sync, database stats, audit logs, cấu hình hệ thống, PDF requests |
| **RESEARCHER** | `researcher` | Search không giới hạn, analytics, bookmarks, reports, follows, notifications, reading history |
| **ACADEMIC_USER** | `academic_user` | Search có giới hạn hàng tháng, bookmarks, reports (không analytics), follows |

Usage limits cho ACADEMIC_USER được cấu hình trong bảng `SYSTEM_CONFIG`:
- `academic_monthly_search_limit`
- `academic_monthly_view_limit`

Khi đạt 80% & 100% limit → trigger `UPGRADE_PROMPT` notification.

### 8.4 Rate Limiting (Bucket4j)

| Tier | RPM | Áp dụng cho |
|------|-----|-------------|
| Public | 30 | Unauthenticated requests |
| Authenticated | 120 | Người dùng đã đăng nhập |
| Admin | 120 | Admin |

Headers: `X-RateLimit-*` được gửi kèm response.

### 8.5 Refresh Token

- 64-byte random token
- 7-day expiry
- Rotation: token cũ bị vô hiệu khi tạo token mới
- Lưu trong `UserSession` table

---

## 9. LUỒNG DỮ LIỆU CHÍNH

### 9.1 Search Pipeline

```
User gõ keyword → FE axiosClient → BE PaperSearchOrchestrator
  ├── 1. Neo4j cache hit:
  │     MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
  │     WHERE k.keywordText = {keyword}
  │     → lấy paper IDs → fetch full data từ SQL Server → trả kết quả (nhanh)
  │
  ├── 2. Neo4j miss / stale IDs:
  │     → Fallback OpenAlex API (live search)
  │     → Trigger async background sync (populate SQL + Neo4j)
  │
  └── 3. Neo4j IDs not found in SQL:
        → Delete stale Neo4j nodes
        → Fallback OpenAlex API
```

### 9.2 Data Sync Pipeline

```
Admin trigger (manual) hoặc Cron (daily 2 AM)
  → DataSyncServiceImpl
    ├── syncFromOpenAlex()        # api.openalex.org — pagination, abstract reconstruction
    ├── syncFromSemanticScholar() # api.semanticscholar.org — DOI-deduplicated
    ├── syncFromArXiv()           # arxiv.org — preprint papers
    └── syncFromCore()            # CORE API — paper full-text
  → Write to SQL Server (JPA entities: ResearchPaper, Author, Journal, Keyword...)
  → Write to Neo4j (GraphService.savePaperWithKeywords())
  → Log to SyncLog table
```

### 9.3 Notification Pipeline

```
Event Source:
  ├── NotificationTriggerService: NEW_PAPER (khi author được follow có paper mới)
  ├── TrendingTopicSyncService: TREND_ALERT (top 5 trending topics, mỗi 12h)
  ├── System triggers: SYSTEM (trial notification)
  └── PaperSearchServiceImpl: UPGRADE_PROMPT (80% & 100% usage limit)

  → NotificationEventPublisher (Spring Event)
  → NotificationEventListener (@Async)
  → NotificationServiceImpl.save() → SQL Server
  → NotificationSseController.push() → SSE stream → FE real-time update
```

---

## 10. TÍCH HỢP BÊN NGOÀI

### 10.1 OpenAlex API
- **URL:** `https://api.openalex.org`
- **Auth:** API key (bắt buộc từ Feb 2026) — cấu hình `OPENALEX_API_KEY`
- **Rate limit:** Polite pool với email (deprecated), nay dùng API key
- **Dữ liệu:** Works (papers), Authors, Sources (journals), Topics, Keywords
- **Abstract:** Lưu dạng inverted index — cần reconstruct khi đọc

### 10.2 Semantic Scholar API
- **URL:** `https://api.semanticscholar.org`
- **Auth:** Không yêu cầu (rate limit thấp hơn)
- **Dữ liệu:** Papers, Authors, Citations
- **Deduplication:** Qua DOI khi sync cùng OpenAlex

### 10.3 DeepSeek AI (qua ai-box.vn)
- **URL:** `https://api.ai-box.vn/v1/chat/completions`
- **Model:** `deepseek-v4-pro`
- **Auth:** `DEEPSEEK_API_KEY`
- **Timeout:** Connect 30s / Read 120s
- **Cache:** Caffeine in-memory, TTL 1h
- **Tính năng:**
  - Abstract summarization (2-3 câu)
  - Methodology extraction (12 categories)
  - Batch analysis (tối đa 10 papers) + cross-paper insight
  - Keyword expansion
- **Graceful fallback:** Khi AI không khả dụng → trường AI = null, không throw lỗi

### 10.4 Gemini API
- **URL:** `https://generativelanguage.googleapis.com`
- **Sử dụng:** Keyword expansion (GeminiService) + AI features
- **Fallback:** Local `ACADEMIC_RELATIONS` map khi Gemini không khả dụng

### 10.5 CORE API
- **Sử dụng:** Paper full-text access
- **Auth:** `CORE_API_KEY`

### 10.6 arXiv
- **Sử dụng:** Preprint paper sync

### 10.7 Cloudinary
- **Sử dụng:** Upload ảnh (avatar, background)
- **Auth:** `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`

### 10.8 Email (SMTP)
- **Host:** `smtp.gmail.com:587` (configurable)
- **Auth:** `MAIL_USERNAME` + `MAIL_PASSWORD` (Gmail App Password)
- **Tính năng:** HTML email template, `@Async` sending
- **Trigger:** Verification email, password reset, notifications

---

## 11. DEPLOYMENT

### 11.1 Local Development

```bash
# Terminal 1 — Backend
cd Journal-Trend-Tracking-BE
docker compose up -d          # Start SQL Server container
mvn spring-boot:run           # http://localhost:8080

# Terminal 2 — Frontend
cd Journal-Trend-Tracking-FE
npm run dev                   # http://localhost:5173
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### 11.2 Docker Compose (Local)

`docker-compose.yml` định nghĩa 2 services:

| Service | Image | Port | Mô tả |
|---------|-------|------|-------|
| `sqlserver` | `mcr.microsoft.com/mssql/server:2022-latest` | `1433` | SQL Server Express + volume data |
| `app` | Build từ `Dockerfile` | `8080` | Spring Boot API |

### 11.3 Railway Deployment

Kiến trúc trên Railway:
```
Railway Project: scitrack
├── Service "api" (Spring Boot) — Dockerfile build, Port 8080
├── Service "sqlserver" (SQL Server Express) — Image, Port 1433 (internal)
└── Internal Network: api ↔ sqlserver (DNS: sqlserver.railway.internal)
```

**Chi phí ước tính:** ~$20-30/tháng (1-2 vCPU, 2-3 GB RAM mỗi service)

### 11.4 Dockerfile

Multi-stage build:
- **Build stage:** `maven:3.9-eclipse-temurin-21-alpine`
- **Runtime stage:** `eclipse-temurin:21-jre-jammy` (Ubuntu-based cho MSSQL JDBC compatibility)
- **Non-root user:** `spring:spring`
- **JVM tuning:** `-XX:MaxRAMPercentage=50.0 -XX:+UseG1GC -XX:+UseStringDeduplication`
- **Health check:** `curl -f http://localhost:${PORT}/actuator/health`

---

## 12. CẤU HÌNH MÔI TRƯỜNG

### 12.1 Backend (`.env`)

```env
# SQL Server
DATABASE_HOST=localhost
DATABASE_PORT=1433
DATABASE_NAME=JournalTrendDB
DATABASE_USERNAME=sa
DATABASE_PASSWORD=YourStrong!Passw0rd

# Neo4j AuraDB (Cloud)
NEO4J_URI=neo4j+s://xxxxxxxx.databases.neo4j.io
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=your-neo4j-password
NEO4J_DATABASE=neo4j

# JWT
JWT_SECRET=your-jwt-secret-key
JWT_EXPIRATION=86400000
REFRESH_TOKEN_EXPIRATION=604800000

# Email (SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Cloudinary (Image Upload)
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret

# AI (DeepSeek via ai-box.vn)
DEEPSEEK_API_KEY=sk-your-key-here
DEEPSEEK_API_URL=https://api.ai-box.vn/v1/chat/completions
DEEPSEEK_MODEL=deepseek-v4-pro

# External APIs
CORE_API_KEY=your-core-api-key
OPENALEX_API_KEY=your-openalex-api-key
OPENALEX_EMAIL=your-email@example.com
GOOGLE_CLIENT_ID=your-google-client-id

# App Config
FRONTEND_URL=http://localhost:5173
APP_AUTO_SYNC_ENABLED=true
```

### 12.2 Frontend (`.env`)

```env
VITE_API_URL=http://localhost:8080
VITE_GOOGLE_CLIENT_ID=your-google-client-id
```

### 12.3 application.properties (Key Config)

| Property | Value | Mô tả |
|----------|-------|-------|
| `spring.datasource.url` | `jdbc:sqlserver://...` | SQL Server connection |
| `spring.jpa.hibernate.ddl-auto` | `none` | Schema quản lý thủ công |
| `spring.sql.init.mode` | `always` | Chạy schema.sql mỗi lần start |
| `spring.cache.type` | `caffeine` | Cache provider |
| `spring.cache.caffeine.spec` | `expireAfterWrite=1h,maximumSize=500,recordStats` | Cache config |
| `spring.servlet.multipart.max-file-size` | `50MB` | Upload limit |
| `app.rate-limit.public-rpm` | `30` | Rate limit cho public |
| `app.rate-limit.authenticated-rpm` | `120` | Rate limit cho authenticated |
| `app.rate-limit.admin-rpm` | `120` | Rate limit cho admin |

---

## 13. TỒN ĐỌNG & KẾ HOẠCH

### 13.1 Hoàn thiện: 90% (26/29 mục)

| Phạm vi | Tổng | Đã xong | Chưa làm | Tỷ lệ |
|---------|------|---------|----------|-------|
| BE P0-P2 | 12 | 11 | 1 | 92% |
| FE P0 | 5 | 5 | 0 | 100% |
| FE P1-P2 | 12 | 10 | 2 | 83% |
| **Tổng** | **29** | **26** | **3** | **90%** |

### 13.2 3 Mục Còn Tồn Đọng

| # | Mục | Bên | Ưu tiên | Công sức |
|---|-----|-----|---------|----------|
| 1 | **Reports với AI** — thay hardcoded template bằng DeepSeek/Gemini | BE | 🟠 P1 | 2-3 ngày |
| 2 | **Reading history UI** — tạo React component (API đã có) | FE | 🟡 P2 | 1-2 ngày |
| 3 | **react-dnd usage** — dùng hoặc gỡ khỏi package.json | FE | 🟡 P2 | 1 ngày |

### 13.3 Các Tính Năng Đã Hoàn Thiện Đáng Chú Ý

- ✅ **Email delivery** — `@Async` HTML email (verification, reset password)
- ✅ **Citation export** — BibTeX, RIS, APA (7th), MLA (9th), single + bulk
- ✅ **4 loại notification** — NEW_PAPER, TREND_ALERT, SYSTEM, UPGRADE_PROMPT — push qua SSE
- ✅ **User-controlled sorting** — 7 lựa chọn (relevance, citations, title, date, etc.)
- ✅ **AI summarization** — DeepSeek-powered: summarize, methodology, batch analysis, 1h cache TTL
- ✅ **Personalized recommendations** — Hybrid: content-based (Neo4j) + collaborative + cold-start
- ✅ **Similar papers** — 2 strategies (same field + Neo4j keyword overlap)
- ✅ **SSE real-time push** — per-user emitter registry, 5-min timeout, heartbeat
- ✅ **Bulk operations** — bookmarks + notifications
- ✅ **Refresh token** — 64-byte random, 7-day expiry, rotation
- ✅ **Rate limiting** — Bucket4j 3 tiers (public/authenticated/admin)
- ✅ **Paper detail page** — 800+ dòng React: AI summary, methodology, citation, similar papers
- ✅ **Citation export UI** — Tab BibTeX/RIS/APA, copy-to-clipboard, download
- ✅ **Keyword comparison chart** — BarChart (Recharts), multi-keyword input
- ✅ **Password change UI** — current/new/confirm, show/hide toggle, validation
- ✅ **Bulk action bar** — floating bottom bar: deselect, export, remove
- ✅ **Profile photo upload** — avatar với Cloudinary, base64 preview

---

## PHỤ LỤC

### A. Các File Tài Liệu Liên Quan

| File | Mô tả |
|------|-------|
| `CLAUDE.md` (root) | Tổng quan hệ thống cho AI assistant |
| `Journal-Trend-Tracking-BE/CLAUDE.md` | Hướng dẫn chi tiết cho backend |
| `Journal-Trend-Tracking-FE/CLAUDE.md` | Hướng dẫn chi tiết cho frontend |
| `Journal-Trend-Tracking-FE/PROJECT_CONTEXT.md` | Context đầy đủ để prompt AI khác |
| `Journal-Trend-Tracking-FE/SYSTEM-CAPABILITY-ASSESSMENT.md` | Đánh giá năng lực hệ thống |
| `Journal-Trend-Tracking-BE/AI_SUMMARIZATION_API.md` | Tài liệu API AI summarization |
| `Journal-Trend-Tracking-BE/RAILWAY_DEPLOY.md` | Hướng dẫn deploy lên Railway |
| `Journal-Trend-Tracking-BE/README.md` | Backend README |
| `Journal-Trend-Tracking-FE/README.md` | Frontend README |

### B. Quick Commands Reference

```bash
# Backend
cd Journal-Trend-Tracking-BE
docker compose up -d              # Start SQL Server
mvn spring-boot:run               # Run backend
mvn clean package                 # Build JAR
mvn test                          # Run tests
mvn test -Dtest=ClassName         # Run single test class

# Frontend
cd Journal-Trend-Tracking-FE
npm run dev                       # Start dev server
npm run build                     # Production build
npm run preview                   # Preview production build
npm run lint                      # ESLint

# Docker (full stack)
cd Journal-Trend-Tracking-BE
docker compose up -d              # Start both SQL Server + Spring Boot
docker compose down               # Stop all
docker compose logs -f app         # Tail API logs
```

### C. Thống Kê Code

| Thành phần | Số lượng |
|-----------|----------|
| BE Controllers | 37 |
| BE Services (interfaces + implementations) | 70+ |
| BE Entities (JPA) | 36 |
| BE Entities (Neo4j) | 2 |
| BE Repositories (JPA) | 36 |
| BE Repositories (Neo4j) | 3 |
| BE DTOs | 100+ |
| BE Config Classes | 14 |
| BE Exception Classes | 8 |
| BE Enums | 3 (ErrorCode, NotificationType, PdfRequestStatus) |
| FE Pages/Views | 20+ |
| FE UI Components (shadcn/ui-style) | 50+ |
| FE Feature Modules | 16 |
| FE Zustand Stores | 5 |
| FE i18n Namespaces | 13 |
| FE Custom Hooks | 4 |
| **Tổng file Java** | **~350** |
| **Tổng file JSX/JS** | **~165** |

---

*Tài liệu được tạo ngày 2026-07-18. Cập nhật khi có thay đổi lớn về kiến trúc.*
