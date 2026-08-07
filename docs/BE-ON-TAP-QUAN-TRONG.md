# 🎯 SCITRACK BE — Ôn Tập Các Luồng & File Quan Trọng

> Tài liệu ôn tập nhanh để trình bày cho giảng viên
> Tập trung vào: **số liệu từ đâu ra**, **file nào xử lý**, **luồng đi như thế nào**

---

## 1. LUỒNG AUTH (Đăng Nhập / Đăng Ký)

### File chain:
```
FE gửi request
    → SecurityConfig.java           (public endpoints: /api/auth/**, /api/health/**, /swagger-ui/**)
    → JwtAuthenticationFilter.java  (chặn mọi request, extract token từ header Authorization)
    → AuthController.java           (POST /api/auth/login, /register, /google, /refresh-token...)
    → AuthServiceImpl.java          (business logic)
    → UserRepository.java           (JPA - tìm user trong SQL Server)
    → JwtTokenProvider.java         (tạo JWT: secret key, 24h TTL, refresh token 7d)
    → UserSessionRepository.java    (lưu session vào SQL Server)
```

### Dữ liệu trả về:
| Field | Nguồn |
|-------|-------|
| `accessToken` | `JwtTokenProvider.generateToken()` — ký bằng HMAC-SHA256 |
| `refreshToken` | 64-byte random, lưu hash vào `USER_SESSION` |
| `role` | `User.role` → `ROLE` table (ADMIN / RESEARCHER / ACADEMIC_USER) |
| `user` | `User` entity → `[USER]` table |

### Giảng viên hỏi: "Token lưu ở đâu?"
- **BE không lưu token**, chỉ lưu **hash** của token trong `USER_SESSION` table
- Token là stateless → BE validate bằng chữ ký (signature), không cần DB lookup
- Khi logout → xóa dòng trong `USER_SESSION` → token cũ vô hiệu

---

## 2. LUỒNG SEARCH PAPER (Luồng QUAN TRỌNG NHẤT)

### File chain:
```
FE: GET /api/v1/papers/search?query=machine+learning
    → PaperSearchController.java
    → PaperSearchOrchestrator.java  ← ĐÂY LÀ TRÁI TIM CỦA HỆ THỐNG
        │
        ├── [1] Check cache (ConcurrentHashMap, 6h TTL)
        │       → Cache hit → trả ngay (nhanh nhất)
        │
        ├── [2] Check usage quota (ACADEMIC_USER)
        │       → UserUsageRepository → SP_CHECK_AND_INCREMENT_USAGE
        │       → Nếu hết quota → UsageLimitExceededException
        │
        ├── [3] Gọi OpenAlex API (primary source)
        │       → OpenAlexFallbackSearchService.java
        │       → searchTopCited(keyword, 50) + searchNoYearFilter(keyword, 50)
        │       → Merge + dedup → top 50 papers
        │
        ├── [4] Save-on-search (async, fire-and-forget)
        │       → DataSyncServiceImpl.java
        │       → Lưu vào SQL Server (ResearchPaper, Author, Keyword, Journal...)
        │       → GraphService.savePaperWithKeywords() → Neo4j
        │
        ├── [5] Record search history (async)
        │       → SearchKeywordService → SEARCH_KEYWORD table (hot keyword ranking)
        │       → UserSearchHistoryService → USER_SEARCH_HISTORY table
        │
        └── [6] Cache result → return PaperSearchResultDTO
```

### Số liệu trả ra từ đâu?

| Dữ liệu | Nguồn |
|---------|-------|
| Danh sách papers | **OpenAlex API** (`api.openalex.org/works?search=keyword`) — live search |
| Title, abstract, DOI | OpenAlex API → `ResearchPaper` table |
| Authors | OpenAlex API → `Author` + `PaperAuthor` tables |
| Keywords | OpenAlex API → `Keyword` + `PaperKeyword` tables |
| Citations count | OpenAlex: `cited_by_count` field |
| Journal info | OpenAlex: `primary_location.source` → `Journal` table |
| Graph data (Neo4j) | `GraphService` → Neo4j Cypher query `MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)` |
| Quick stats | `KeywordQuickStatsServiceImpl` → aggregate từ Neo4j + SQL |

### Giảng viên hỏi: "Tại sao dùng 2 database?"
- **SQL Server**: Lưu dữ liệu có cấu trúc (users, papers, authors, journals...), hỗ trợ JOIN, filter, phân trang
- **Neo4j**: Lưu quan hệ graph (Paper ↔ Keyword), tìm kiếm quan hệ nhanh gấp 10-100x SQL JOIN
- Khi sync: ghi đồng thời vào cả 2 DB

---

## 3. LUỒNG DATA SYNC (Đồng Bộ Dữ Liệu)

### File chain:
```
Trigger:
  - Manual: AdminSyncController → POST /api/v1/admin/sync/openalex
  - Auto: ScheduledDataSyncService → @Scheduled(cron = "0 0 2 * * ?") (2 AM daily)
  - Startup: 12 keywords × 30 papers
  - Hourly: 8 trending keywords × 10 papers

    → DataSyncServiceImpl.java
        ├── syncFromOpenAlex()          → api.openalex.org/works?search=keyword
        ├── syncFromSemanticScholar()   → api.semanticscholar.org/graph/v1/paper/search
        ├── syncFromArXiv()             → export.arxiv.org/api/query
        └── syncFromCore()              → api.core.ac.uk/v3/
        │
        ├── DOI Deduplication (tránh trùng lặp giữa các nguồn)
        ├── Abstract Reconstruction (OpenAlex lưu dạng inverted index)
        ├── Quality Score Filter (≥ 40/100)
        │
        ├── Save to SQL Server:
        │   ResearchPaper, Author, Journal, Keyword, PaperAuthor, PaperKeyword...
        │
        ├── Save to Neo4j:
        │   GraphService.savePaperWithKeywords()
        │   → MERGE (:Paper), MERGE (:Keyword), MERGE [:HAS_KEYWORD]
        │
        └── Log to SyncLog table
```

### Giảng viên hỏi: "Dữ liệu từ đâu ra?"
- **OpenAlex** (chính): 240M+ works, cần API key từ Feb 2026
- **Semantic Scholar** (phụ): Citation data, không cần API key
- **CORE**: Full-text paper
- **arXiv**: Preprint papers

---

## 4. LUỒNG AI (Tóm Tắt & Phân Tích)

### File chain:
```
FE: GET /api/v1/ai/summarize/{paperId}
    → AIController.java
    → AISummarizationService.java
        ├── [1] Lấy abstract từ SQL Server (ResearchPaper.abstract)
        │       → Nếu không có → thử PaperCache (7d TTL) → thử OpenAlex API
        │
        ├── [2] Gọi DeepSeek AI (qua ai-box.vn)
        │       → DeepSeekClient.java (implements AIClient)
        │       → Prompt: "Summarize in 2-3 sentences with 4 sections:
        │                  Background, Methods, Results, Conclusion"
        │       → Timeout: 120s, Temp: 0.3
        │
        ├── [3] Cache result (Caffeine, 1h TTL)
        │
        └── [4] Fallback: nếu AI lỗi → aiSummary = null (không throw exception)
```

### Các tính năng AI:
| Tính năng | Endpoint | Service | AI Model |
|-----------|----------|---------|----------|
| Summarize | `GET /api/v1/ai/summarize/{id}` | AISummarizationService | DeepSeek v4-pro |
| Methodology | `GET /api/v1/ai/methodology/{id}` | AISummarizationService | DeepSeek v4-pro |
| Batch Analysis | `POST /api/v1/ai/batch-analyze` | AISummarizationService | DeepSeek v4-pro |
| Idea Analysis | `POST /api/v1/ideas/analyze` | IdeaAnalysisService | DeepSeek v4-pro |
| Keyword Expansion | (internal) | GeminiService / KeywordExpansionService | Gemini / Local fallback |

---

## 5. LUỒNG NOTIFICATION (Thông Báo Real-Time)

### File chain:
```
Event Source → NotificationEventPublisher (Spring Event)
    → NotificationEventListener (@Async)
    → NotificationServiceImpl.save() → SQL Server (NOTIFICATION table)
    → NotificationSseController.push() → SSE stream → FE

4 loại notification:
  NEW_PAPER       → NotificationTriggerService (khi author được follow có paper mới)
  TREND_ALERT     → TrendingTopicSyncService (mỗi 12h, top 5 trending topics)
  SYSTEM          → Hệ thống (trial notification)
  UPGRADE_PROMPT  → PaperSearchServiceImpl (khi đạt 80% và 100% limit)
```

### SSE Flow:
```
FE: new EventSource('/api/v1/notifications/stream?token=<jwt>')
    → NotificationSseController.java
    → Per-user emitter registry (ConcurrentHashMap)
    → Heartbeat mỗi 30s, timeout 5 phút
    → Auto-reconnect
```

---

## 6. BẢNG TỔNG HỢP: MỖI SỐ LIỆU TỪ ĐÂU RA

| Trang FE | Endpoint BE | Controller | Service | Data Source |
|----------|------------|------------|---------|-------------|
| **Landing Page** | `GET /api/v1/overview` | OverviewController | OverviewStatisticsServiceImpl | SQL (count từ các bảng) |
| **User Dashboard** | `GET /api/v1/user-overview` | UserOverviewController | UserOverviewServiceImpl | SQL (UserUsage, ResearchPaper) |
| **Search Papers** | `GET /api/v1/papers/search` | PaperSearchController | PaperSearchOrchestrator | OpenAlex API → SQL + Neo4j |
| **Paper Detail** | `GET /api/v1/papers/{id}` | PaperSearchController | PaperSearchOrchestrator | SQL (ResearchPaper) + AI (DeepSeek) |
| **AI Summary** | `GET /api/v1/ai/summarize/{id}` | AIController | AISummarizationService | DeepSeek AI (ai-box.vn) |
| **Graph** | `GET /api/graphs/paper/{id}` | GraphController | GraphService | Neo4j (Cypher query) |
| **Bookmarks** | `GET/POST/DELETE /api/v1/bookmarks` | BookmarkController | BookmarkServiceImpl | SQL (BOOKMARK table) |
| **Follows** | `GET/POST/DELETE /api/v1/follows` | FollowController | FollowServiceImpl | SQL (FOLLOW table) |
| **Notifications** | `GET /api/v1/notifications` | NotificationController | NotificationServiceImpl | SQL (NOTIFICATION table) |
| **SSE Stream** | `GET /api/v1/notifications/stream` | NotificationSseController | NotificationServiceImpl | In-memory emitter registry |
| **Reports** | `POST /api/v1/reports/keyword-trend` | ReportController | ReportServiceImpl | SQL (PUBLICATION_TREND, ResearchPaper) |
| **Citation** | `GET /api/v1/papers/{id}/citation` | PaperSearchController | CitationService | SQL (ResearchPaper, Author, Journal) |
| **Idea Analysis** | `POST /api/v1/ideas/analyze` | IdeaController | IdeaAnalysisService | OpenAlex + DeepSeek AI + PDFBox |
| **Admin Dashboard** | `GET /api/v1/admin/overview` | AdminOverviewController | AdminOverviewServiceImpl | SQL (aggregate queries) |
| **Admin Sync** | `POST /api/v1/admin/sync/openalex` | AdminSyncController | DataSyncServiceImpl | OpenAlex API → SQL + Neo4j |
| **Admin Users** | `GET /api/v1/admin/users` | AdminUserController | AdminServiceImpl | SQL (USER table) |

---

## 7. CẤU TRÚC PACKAGE (Để bạn biết tìm file ở đâu)

```
com.sra.journal_tracking/
├── config/          ← 14 files: CORS, Cache, Rate Limit, JPA/Neo4j scan config
├── controller/      ← 38 files: REST endpoints
├── dto/             ← 100+ files: request/response objects
│   ├── paper/       ← PaperSearchResultDTO, PaperDetailResponseDTO...
│   ├── auth/        ← LoginRequest, RegisterRequest...
│   ├── search/      ← Search-related DTOs
│   └── ...
├── entity/
│   ├── jpa/         ← 36 entities: ResearchPaper, User, Author, Keyword...
│   └── neo4j/       ← 2 entities: Neo4jPaper, Neo4jKeyword
├── exception/       ← 8 files: AppException, ErrorCode enum...
├── repository/
│   ├── jpa/         ← 36 repositories: ResearchPaperRepository...
│   └── neo4j/       ← 3 repositories: PaperRepository, KeywordRepository...
├── security/        ← 7 files: JWT, SecurityConfig, CustomUserDetails...
└── service/         ← 70+ files: business logic
    └── impl/        ← Implementations
```

---

## 8. CÂU HỎI GIẢNG VIÊN THƯỜNG HỎI & CÁCH TRẢ LỜI

### Q1: "Dữ liệu paper từ đâu ra?"
> **Trả lời:** Từ **OpenAlex API** — một open database 240M+ academic works. Khi user search, hệ thống gọi OpenAlex live search, sau đó lưu kết quả vào SQL Server và Neo4j để cache cho lần sau. Ngoài ra còn có Semantic Scholar, CORE, arXiv làm nguồn phụ.

### Q2: "Tại sao cần 2 database?"
> **Trả lời:** SQL Server cho dữ liệu có cấu trúc (CRUD, JOIN, filter, báo cáo). Neo4j cho graph traversal (tìm mối quan hệ keyword-paper). Ví dụ: tìm "keyword A liên quan đến keyword nào?" — SQL cần 4 JOINs, Neo4j chỉ cần 1 traversal, nhanh gấp 10-100x.

### Q3: "Luồng search hoạt động thế nào?"
> **Trả lời:** `PaperSearchOrchestrator` là trung tâm. Flow: Check cache → Check quota → Gọi OpenAlex API → Save-on-search (async lưu vào SQL + Neo4j) → Record history → Cache → Return. Có 3 layer cache: L1 (RAM 6h), L2 (Caffeine 1h), L3 (DB PaperCache 7d).

### Q4: "AI hoạt động thế nào?"
> **Trả lời:** Dùng **DeepSeek v4-pro** qua ai-box.vn (OpenAI-compatible API). 4 tính năng: summarize (2-3 câu, 4 sections), methodology (12 categories), batch analysis (so sánh cross-paper), keyword expansion. Có Caffeine cache 1h, graceful fallback nếu AI lỗi (trả về null, không crash).

### Q5: "Bảo mật thế nào?"
> **Trả lời:** JWT stateless (jjwt 0.12.3) + BCrypt password + RBAC 3 roles. Token 24h TTL, refresh token 7d rotation. Rate limiting Bucket4j (public 30 RPM, authenticated 120 RPM). Admin audit log mọi thao tác. `@PreAuthorize("hasRole('ADMIN')")` cho endpoint admin.

### Q6: "System có bao nhiêu bảng?"
> **Trả lời:** 36 bảng SQL Server (JPA entities) + 2 Neo4j node types (Paper, Keyword) + 1 relationship (HAS_KEYWORD). 4 stored procedures, 4 database views, 3 database indexes.

---

## 9. CÁC CON SỐ QUAN TRỌNG CẦN NHỚ

| Metric | Con số |
|--------|--------|
| BE Controllers | 38 |
| BE Services | 70+ |
| JPA Entities (SQL Server) | 36 bảng |
| Neo4j Entities | 2 nodes + 1 relationship |
| API Endpoints | 56+ |
| External APIs | 8 (OpenAlex, Semantic Scholar, CORE, arXiv, DeepSeek, Google, Cloudinary, SMTP) |
| Cache layers | 3 (RAM 6h, Caffeine 1h, DB 7d) |
| User roles | 3 (ADMIN, RESEARCHER, ACADEMIC_USER) |
| Notification types | 4 (NEW_PAPER, TREND_ALERT, SYSTEM, UPGRADE_PROMPT) |
| Citation formats | 4 (BibTeX, RIS, APA, MLA) |
| i18n languages | 2 (EN, VI) |
| Keywords in Neo4j | ~1,400 |
| Papers in DB | ~200+ |