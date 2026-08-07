# SCITRACK — Hệ Thống Phân Tích Học Thuật Hỗ Trợ AI

> **Tài liệu tổng hợp để làm Slide Thuyết Trình**
>
> Dự án: AI-Powered Academic Research Analytics
> Công nghệ: Spring Boot 3.5 + React 18 + SQL Server + Neo4j

---

## MỤC LỤC

1. [Ngữ Cảnh](#1-ngữ-cảnh-context)
2. [Kiến Trúc](#2-kiến-trúc-architecture)
3. [Flow](#3-flow)
4. [Tính Năng Chi Tiết](#4-tính-năng-chi-tiết)
5. [Công Nghệ Sử Dụng](#5-công-nghệ-sử-dụng)
6. [Điểm Nổi Bật](#6-điểm-nổi-bật)
7. [Demo Kịch Bản](#7-demo-kịch-bản)

---

## 1. NGỮ CẢNH (CONTEXT)

### 1.1 Bài Toán Thực Tế

| Vấn đề | Mô tả |
|--------|-------|
| **Quá tải thông tin** | Hàng triệu bài báo khoa học được xuất bản mỗi năm. Nhà nghiên cứu không thể theo dõi hết. |
| **Tìm kiếm kém hiệu quả** | Google Scholar, Scopus chỉ trả về danh sách phẳng, không thấy được mối quan hệ giữa các chủ đề. |
| **Thiếu phân tích xu hướng** | Không có công cụ nào tự động phát hiện chủ đề đang "hot" hay đang suy giảm. |
| **Rào cản ngôn ngữ & học thuật** | Sinh viên, nghiên cứu viên trẻ gặp khó khăn khi đọc và hiểu paper tiếng Anh chuyên ngành. |
| **Quản lý tài liệu rời rạc** | Bookmark trên trình duyệt, file PDF lưu rải rác, không có hệ thống tổ chức. |

### 1.2 Người Dùng Mục Tiêu (3 Personas)

```
┌──────────────────────────────────────────────────────────────────┐
│  ACADEMIC_USER                    RESEARCHER                     │
│  (Sinh viên, NCS)                 (Giảng viên, Nhà khoa học)      │
│  ┌────────────────────┐           ┌────────────────────┐         │
│  │ • Search giới hạn  │           │ • Search không giới │         │
│  │   30 lần/tháng     │           │   hạn              │         │
│  │ • Bookmark papers   │           │ • Advanced filter  │         │
│  │ • Đọc paper + AI    │           │ • Analytics & Báo  │         │
│  │   tóm tắt           │           │   cáo              │         │
│  │ • Theo dõi chủ đề   │           │ • Xuất citation    │         │
│  │ • Nâng cấp tự động   │           │ • AI phân tích     │         │
│  └────────────────────┘           └────────────────────┘         │
│                                                                  │
│  ADMIN                                                           │
│  (Quản trị viên hệ thống)                                         │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ • Quản lý users (phân quyền, enable/disable)               │  │
│  │ • Đồng bộ dữ liệu từ OpenAlex, Semantic Scholar, arXiv...  │  │
│  │ • Giám sát hệ thống (dashboard, audit log)                  │  │
│  │ • Cấu hình hệ thống (rate limit, usage limit...)            │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### 1.3 Giải Pháp SCITRACK

| Tính năng | Giá trị mang lại |
|-----------|-----------------|
| **Tìm kiếm thông minh** | Gõ 1 keyword → nhận papers + keyword graph + trending topics |
| **AI Tóm tắt** | Paper dài 20 trang → 2-3 câu tóm tắt tiếng Việt/Anh qua AI |
| **Trực quan hóa quan hệ** | Xem được keyword A liên quan đến keyword B, C, D qua graph |
| **Phát hiện xu hướng** | Biết ngay chủ đề nào đang tăng trưởng mạnh trong tuần |
| **Quản lý tập trung** | Bookmark, collection, reading history — tất cả trong 1 nơi |
| **Citation Export** | Xuất citation chuẩn BibTeX, APA, MLA, RIS — 1 click |

---

## 2. KIẾN TRÚC (ARCHITECTURE)

### 2.1 Tổng Quan Hệ Thống

```
                              ┌─────────────────────────┐
                              │   NGƯỜI DÙNG CUỐI       │
                              │   Browser (localhost)    │
                              └────────────┬────────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
                    ▼                      ▼                      ▼
          ┌─────────────┐        ┌─────────────┐        ┌─────────────┐
          │  React 18   │        │  JWT Auth   │        │  Google     │
          │  Vite 6     │◄──────►│  BCrypt     │        │  OAuth 2.0  │
          │  Tailwind 4 │        │  RBAC       │        └─────────────┘
          └──────┬──────┘        └──────┬──────┘
                 │                      │
                 │    HTTP/REST API      │
                 │    JSON + SSE         │
                 │                      │
                 ▼                      ▼
    ┌─────────────────────────────────────────────────────┐
    │              SPRING BOOT 3.5 (Java 21)              │
    │                                                     │
    │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
    │  │ 35 REST  │ │ Security │ │  Cache   │ │  Async │ │
    │  │Controllers│ │  Layer   │ │  Layer   │ │  Tasks │ │
    │  └──────────┘ └──────────┘ └──────────┘ └────────┘ │
    │                                                     │
    │  ┌──────────────────────────────────────────────┐   │
    │  │           SERVICE LAYER (50+ services)        │   │
    │  │  PaperSearchOrchestrator | DataSyncService   │   │
    │  │  AISummarizationService  | GraphService      │   │
    │  │  CitationService         | NotificationSSE   │   │
    │  └──────────────────────────────────────────────┘   │
    │                                                     │
    │  ┌──────────────────┐  ┌──────────────────┐        │
    │  │  REPOSITORY LAYER │  │  EXTERNAL APIs   │        │
    │  │  JPA + Neo4j     │  │  OpenAlex         │        │
    │  │  (dual database) │  │  Semantic Scholar │        │
    │  └──────────────────┘  │  CORE / arXiv     │        │
    │                         │  DeepSeek AI      │        │
    └─────────┬───────────────┴───────────────────┴────────┘
              │
    ┌─────────┴───────────────┐
    │                         │
    ▼                         ▼
┌──────────┐          ┌──────────────┐
│  SQL     │          │   NEO4J      │
│  SERVER  │          │   AuraDB     │
│          │          │              │
│  Users   │          │  (:Paper)    │
│  Papers  │          │  (:Keyword)  │
│  Authors │          │  [:HAS_      │
│  Journals│          │   KEYWORD]   │
│  Bookmarks│         │              │
│  ...35   │          │  Graph       │
│  tables  │          │  Visualization│
└──────────┘          └──────────────┘
```

### 2.2 Dual Database — Tại Sao Cần 2 Database?

| Tiêu chí | SQL Server (JPA) | Neo4j (Graph) |
|----------|-----------------|---------------|
| **Lưu trữ** | Dữ liệu có cấu trúc (users, papers, authors...) | Quan hệ giữa paper và keyword |
| **Truy vấn** | JOIN nhiều bảng, filter phức tạp | Traversal graph: "keyword này liên quan đến keyword nào?" |
| **Bài toán phù hợp** | CRUD, thống kê, báo cáo | Khám phá mạng lưới chủ đề, tìm quan hệ ẩn |
| **Hiệu năng** | Nhanh với indexed queries | Nhanh gấp 10-100x với graph traversal so với SQL JOIN nhiều bảng |

```
Ví dụ: Tìm tất cả keyword liên quan đến "machine learning"

SQL:  SELECT DISTINCT k2.keyword_text
      FROM paper_keyword pk1
      JOIN paper_keyword pk2 ON pk1.paper_id = pk2.paper_id
      JOIN keyword k1 ON pk1.keyword_id = k1.keyword_id
      JOIN keyword k2 ON pk2.keyword_id = k2.keyword_id
      WHERE k1.keyword_text = 'machine learning'
      → 4 JOINs, chậm khi có hàng triệu papers

Neo4j: MATCH (k1:Keyword {text:'machine learning'})<-[:HAS_KEYWORD]-
             (p:Paper)-[:HAS_KEYWORD]->(k2:Keyword)
       RETURN DISTINCT k2.text
       → 1 traversal, nhanh bất kể scale
```

### 2.3 Stack Công Nghệ Chi Tiết

```
FRONTEND                          BACKEND                         DATABASE
─────────                         ────────                         ────────
React 18.3                        Spring Boot 3.5.14              SQL Server (Docker)
├── Vite 6 (build tool)           ├── Java 21                     ├── 35 tables
├── React Router v7 (routing)     ├── Maven (build)               ├── JPA/Hibernate
├── Zustand (state management)    ├── Spring Security             ├── HikariCP pool
├── Axios (HTTP client)           │   ├── JWT (jjwt 0.12.3)       │
├── shadcn/ui (50+ components)    │   ├── BCrypt                  │
├── Radix UI (primitives)         │   └── RBAC (3 roles)          Neo4j AuraDB
├── Tailwind CSS v4 (styling)     ├── Spring Data JPA             ├── 2 node types
├── Recharts (charts)             ├── Spring Data Neo4j           ├── 1 relationship
├── vis-network (graph)           ├── Bucket4j (rate limiting)    ├── Cypher queries
├── Framer Motion (animation)     ├── Caffeine (caching)          └── Graph visualization
├── react-i18next (i18n: EN, VI)  ├── Spring Mail (SMTP)
├── Google OAuth (@react-oauth)   ├── Cloudinary (image upload)
├── Sonner (toast notifications)  ├── Micrometer (metrics)
└── date-fns (date handling)      └── SpringDoc OpenAPI (Swagger)

AI/ML                              EXTERNAL APIs                   DEVOPS
─────                              ─────────────                   ──────
DeepSeek AI                        OpenAlex API                    Docker Compose
├── Paper Summarization            Semantic Scholar API            ├── SQL Server
├── Methodology Extraction         CORE API                        └── (local dev)
├── Batch Analysis                 arXiv API
└── Keyword Expansion              Google OAuth2 API               AWS (production)
                                   Cloudinary API                  ├── RDS (SQL Server)
                                                                   ├── EC2 (BE)
                                                                   └── Vercel (FE)
```

---

## 3. FLOW

### 3.1 Flow Đăng Nhập & Phân Quyền

```
  User mở app
      │
      ▼
  Có token trong localStorage?
      │
      ├── KHÔNG ──► Redirect /login
      │                │
      │                ├── Login email/password ──► POST /api/auth/login
      │                │                              │
      │                │                              ▼
      │                │                    AuthService.login()
      │                │                      ├── Authenticate (BCrypt)
      │                │                      ├── Generate JWT (24h)
      │                │                      ├── Generate Refresh Token (7d)
      │                │                      ├── Save UserSession
      │                │                      └── Return {accessToken, role, user}
      │                │                              │
      │                │                              ▼
      │                │                    FE: useAuthStore.setTokens(token)
      │                │                    FE: sessionStorage.setItem('userRole', role)
      │                │                    FE: Navigate /{role}/overview
      │                │
      │                └── Google OAuth ──► @react-oauth/google
      │                                       │
      │                                       ▼
      │                               POST /api/auth/google
      │                               (Google ID Token)
      │                                       │
      │                                       ▼
      │                               AuthService.googleLogin()
      │                                 ├── Verify Google token
      │                                 ├── Create new user nếu chưa tồn tại
      │                                 │   └── Role = RESEARCHER (3-day trial)
      │                                 └── Return JWT
      │
      └── CÓ ──► Đọc role từ sessionStorage
                     │
                     ▼
               ProtectedRoute check
                     │
                     ├── Role hợp lệ ──► Render DashboardLayout
                     │                      ├── Sidebar (theo role)
                     │                      ├── TopBar (user info + notification)
                     │                      └── <Outlet /> (page content)
                     │
                     └── Role không hợp lệ ──► Redirect /{correctRole}/overview

  Mỗi request sau đó:
      FE axiosClient interceptor
        └── config.headers['Authorization'] = `Bearer ${token}`
      BE JwtAuthenticationFilter
        ├── Extract token from header
        ├── Validate (signature + expiration)
        ├── Check UserSession (chưa logout)
        ├── Load CustomUserDetails
        │     └── Nếu RESEARCHER trial hết hạn → auto-downgrade → ACADEMIC_USER
        └── Set SecurityContext
```

### 3.2 Flow Tìm Kiếm Paper (Core Flow)

```
  User nhập "machine learning" → nhấn Enter
      │
      ▼
  FE: SearchPapers component
      │
      ├── 1. Check quota: GET /api/v1/papers/search/quota?query=machine+learning
      │      │
      │      ├── ACADEMIC_USER: Kiểm tra searchCount tháng này
      │      │   ├── Còn quota → tiếp tục
      │      │   └── Hết quota → Hiển thị AcademicLimitAlert + đề xuất upgrade
      │      │
      │      └── RESEARCHER/ADMIN → Bỏ qua, luôn được phép
      │
      ├── 2. Search: GET /api/v1/papers/search?query=machine+learning
      │      │
      │      ▼
      │  BE: PaperSearchController.searchPapers()
      │      │
      │      ├── Là simple keyword search? (không có author/journal filter)
      │      │   │
      │      │   └── YES → PaperSearchOrchestrator.searchByKeyword()
      │      │              │
      │      │              ├── Cache hit (6h TTL)?
      │      │              │   └── YES → Trả về cached results ngay
      │      │              │
      │      │              └── Cache miss:
      │      │                   │
      │      │                   ├── Record search history (async)
      │      │                   ├── Record search keyword → hot keyword ranking
      │      │                   │
      │      │                   ├── Fetch từ OpenAlex API:
      │      │                   │   ├── searchTopCited(keyword, 50)
      │      │                   │   └── searchNoYearFilter(keyword, 50)
      │      │                   │   → Merge + dedup → top 50 papers
      │      │                   │
      │      │                   ├── Background save-on-search (async):
      │      │                   │   └── Lưu papers + authors + keywords
      │      │                   │       vào SQL Server + Neo4j
      │      │                   │
      │      │                   ├── Cache kết quả (6h TTL)
      │      │                   └── Return PaperSearchResultDTO
      │      │
      │      └── Có filter? → PaperSearchService.searchPapers() (SQL-based)
      │
      ├── 3. Hiển thị kết quả:
      │      ├── Left panel: Danh sách PaperCard (title, authors, abstract, rating)
      │      ├── Right panel: Neo4jGraphCard (keyword graph visualization)
      │      ├── Top bar: WeeklyBreakout (top trending topics)
      │      └── Sidebar: KeywordQuickStats (4 stat cards)
      │
      └── 4. User click paper → PaperDetailDialog
               ├── AI Summary (DeepSeek)

               ├── Methodology extraction
               ├── Citation export (BibTeX, APA, MLA, RIS)
               ├── Rating (1-5 stars)
               ├── Bookmark
               └── Request PDF (nếu không Open Access)
```

### 3.3 Flow Đồng Bộ Dữ Liệu (Data Sync)

```
  NGUỒN DỮ LIỆU:
  ┌────────────┐ ┌──────────────┐ ┌──────────┐ ┌────────────┐
  │ OpenAlex   │ │ Semantic     │ │ CORE API │ │ arXiv API  │
  │ (primary)  │ │ Scholar      │ │          │ │            │
  └─────┬──────┘ └──────┬───────┘ └────┬─────┘ └─────┬──────┘
        │               │             │             │
        └───────────────┼─────────────┼─────────────┘
                        │             │
                        ▼             ▼
              ┌────────────────────────────┐
              │    DataSyncServiceImpl     │
              │                            │
              │  1. Fetch papers từ API    │
              │  2. Reconstruct abstract   │
              │     (inverted index → text)│
              │  3. DOI Deduplication      │
              │  4. Save to SQL Server     │
              │     ├── ResearchPaper      │
              │     ├── Author             │
              │     ├── Keyword            │
              │     ├── Journal            │
              │     └── Relationships      │
              │  5. Save to Neo4j          │
              │     ├── MERGE (:Paper)     │
              │     ├── MERGE (:Keyword)   │
              │     └── MERGE [:HAS_KEYWORD]│
              │  6. Log to SyncLog table   │
              └────────────────────────────┘

  HAI CHẾ ĐỘ SYNC:
  ┌─────────────────────────────────────────────────────────┐
  │  SCHEDULED (Tự động)          │  MANUAL (Admin trigger) │
  │                               │                         │
  │  • Startup sync: 12 keywords  │  • POST /admin/sync/    │
  │    × 30 papers từ 4 nguồn     │    openalex             │
  │  • Hourly sync: 8 trending    │  • POST /admin/sync/    │
  │    keywords × 10 papers       │    bulk (nhiều keyword) │
  │  • Gated by config:           │  • Real-time progress   │
  │    app.auto-sync-enabled      │    qua BulkSyncProgress │
  │                               │    Tracker              │
  └─────────────────────────────────────────────────────────┘
```

### 3.4 Flow AI Tóm Tắt & Phân Tích

```
  User mở Paper Detail → Click "AI Summarize"
      │
      ▼
  FE: GET /api/v1/ai/summarize/{paperId}
      │
      ▼
  BE: AIController.summarizeAbstract()
      │
      ├── 1. Tìm paper trong DB
      │      ├── Có → lấy abstract từ DB
      │      └── Không có abstract:
      │           ├── Thử PaperCache (7-day TTL)
      │           └── Thử OpenAlex API
      │
      ├── 2. Gọi DeepSeek AI:
      │      ├── Prompt: "Summarize this academic abstract
      │      │           in 2-3 sentences with 4 sections:
      │      │           Background, Methods, Results, Conclusion"
      │      ├── Timeout: 120s
      │      └── Fallback: Nếu lỗi → aiSummary = null
      │                    (không throw exception)
      │
      ├── 3. Parse response thành sections
      │      └── aiSummarySections: {background, methods, results, conclusion}
      │
      ├── 4. Extract methodology
      │      └── Prompt: "Classify research methodology:
      │                  quantitative/qualitative/RCT/case study/..."
      │
      └── 5. Cache & Return PaperDetailResponseDTO
               ├── aiSummary (full text)
               ├── aiSummarySections (structured)
               └── methodology (classification)


  BATCH ANALYSIS (so sánh nhiều paper):
      │
      ▼
  POST /api/v1/ai/batch-analyze  (body: [paperId1, paperId2, ..., paperId10])
      │
      ▼
  AISummarizationService.batchAnalyze():
      ├── Với mỗi paper → summarize riêng lẻ
      └── Prompt tổng hợp: "Compare these papers.
          Identify: similarities, differences, contradictions,
          research gaps. Write 2-3 paragraph comparative insight."
      → Trả về BatchAnalysisResponseDTO:
         ├── paperSummaries[] (per-paper)
         └── comparativeInsight (cross-paper analysis)
```

### 3.5 Flow Real-Time Notification (SSE)

```
  BACKEND                          FRONTEND
  ───────                          ────────

  User mở app
      │                                │
      │                                ├── 1. GET /api/v1/notifications
      │                                │      → Load notification history
      │                                │
      │                                ├── 2. new EventSource(
      │                                │      '/api/v1/notifications/stream?token=<jwt>')
      │                                │
      │  ◄─────── SSE Connection ──────│
      │                                │
      │  3. Gửi heartbeat              │
      │  ──── "connected" ───────────►│  4. Connection confirmed
      │                                │
      │  ... system hoạt động ...      │
      │                                │
      │  5. Có sự kiện mới:            │
      │     NotificationEventPublisher │
      │     .publishEvent(event)       │
      │           │                    │
      │           ▼                    │
      │     NotificationEventListener  │
      │     .handle(event)             │
      │           │                    │
      │           ├── Save to DB       │
      │           │   (NOTIFICATION     │
      │           │    table)          │
      │           │                    │
      │           └── Push SSE         │
      │  ──── event:notification ────►│  6. Nhận real-time
      │       data: {type, message}    │     → Cập nhật unread badge
      │                                │     → Hiển thị toast (sonner)
      │                                │
      │  ... 5 phút sau ...            │
      │                                │
      │  7. SSE timeout                │
      │  ──── connection close ──────►│  8. Auto-reconnect
      │                                │     → EventSource mới
      │  ◄─────── SSE Connection ──────│
```

---

## 4. TÍNH NĂNG CHI TIẾT

### 4.1 Tính Năng Người Dùng

| # | Tính năng | Mô tả | Endpoint |
|---|-----------|-------|----------|
| 1 | **Đăng ký/Đăng nhập** | Email/Password + Google OAuth. JWT stateless auth | POST /api/auth/register, /login, /google |
| 2 | **Quên mật khẩu** | Gửi email reset link. Token hết hạn sau 15 phút | POST /api/auth/forgot-password, /reset-password |
| 3 | **Tìm kiếm keyword** | Neo4j graph search + OpenAlex fallback. Cache 6h | GET /api/v1/papers/search |
| 4 | **Advanced Filter** | Lọc theo field, year, journal quartile, min citations, OA | GET /api/v1/papers/filter/advanced |
| 5 | **Search Author** | Autocomplete + quick stats (h-index, citations, timeline, co-authors) | GET /api/search/author/** |
| 6 | **Search Journal** | Top journals theo field, quartile lookup | GET /api/v1/journals/** |
| 7 | **Paper Detail** | Full metadata + AI summary + rating + citation + similar papers | GET /api/v1/papers/{id} |
| 8 | **AI Tóm tắt** | 2-3 câu tóm tắt có cấu trúc (Background → Methods → Results → Conclusion) | GET /api/v1/ai/summarize/{id} |
| 9 | **AI Methodology** | Phân loại phương pháp nghiên cứu (RCT, survey, case study...) | GET /api/v1/ai/methodology/{id} |
| 10 | **Batch Analysis** | So sánh đến 10 papers, cross-paper comparative insight | POST /api/v1/ai/batch-analyze |
| 11 | **Keyword Graph** | Trực quan hóa mạng lưới keyword-paper bằng vis-network | GET /api/graphs/paper/{id} |
| 12 | **Graph Explorer** | Khám phá keyword graph với depth expansion + OpenAlex fallback | POST /api/graphs/keyword/search |
| 13 | **Bookmarks** | Lưu papers + tổ chức theo collections | CRUD /api/v1/bookmarks |
| 14 | **Collections** | Gom nhóm bookmarks theo chủ đề | CRUD /api/v1/bookmark-collections |
| 15 | **Follows** | Theo dõi journal/topic/keyword/author, nhận notification | CRUD /api/v1/follows |
| 16 | **Reading History** | Lịch sử đọc paper, dedup tự động | GET /api/v1/papers/reading-history |
| 17 | **Citation Export** | BibTeX, RIS, APA, MLA — single hoặc bulk | GET /api/v1/papers/{id}/citation |
| 18 | **Notifications** | Real-time SSE + REST fallback. Đánh dấu đã đọc | GET /api/v1/notifications/stream |
| 19 | **Báo cáo** | Keyword trend, journal quality, author impact | GET /api/public/reports/** |
| 20 | **Weekly Breakout** | Top 5 chủ đề tăng trưởng mạnh trong tuần + sparkline | GET /api/public/trends/weekly-breakout |
| 21 | **User Overview** | Dashboard cá nhân: papers mới, citations, trending topics | GET /api/v1/overview/user |
| 22 | **Hồ sơ cá nhân** | Cập nhật avatar (Cloudinary), background, password | PUT /api/users/me |
| 23 | **Nâng cấp tài khoản** | ACADEMIC_USER → RESEARCHER (1-click, không cần admin) | POST /api/users/me/upgrade |
| 24 | **i18n** | Giao diện tiếng Anh + tiếng Việt | react-i18next |
| 25 | **Dark Theme** | Dark-only design system với CSS custom properties | Tailwind v4 |

### 4.2 Tính Năng Admin

| # | Tính năng | Mô tả |
|---|-----------|-------|
| 1 | **User Management** | Danh sách users, tìm kiếm, enable/disable, đổi role |
| 2 | **Database Stats** | Số lượng papers, authors, keywords, journals trong SQL + Neo4j |
| 3 | **Manual Sync** | Trigger sync từ OpenAlex, Semantic Scholar, CORE, arXiv |
| 4 | **Bulk Sync** | Đồng bộ hàng loạt nhiều keywords + real-time progress tracking |
| 5 | **Clear Database** | Xóa toàn bộ papers (SQL + Neo4j) để reset dữ liệu test |
| 6 | **Backfill Author Metrics** | Cập nhật h-index, citations cho authors từ OpenAlex |
| 7 | **Re-extract Keywords** | Chạy lại keyword extraction cho tất cả papers sau khi nâng cấp thuật toán |
| 8 | **System Config** | Chỉnh sửa config: rate limit, usage limit, auto-sync toggle |
| 9 | **Audit Log** | Lịch sử thao tác của tất cả admins |
| 10 | **PDF Requests** | Quản lý yêu cầu PDF từ users (find candidates, upload, fulfill, reject) |
| 11 | **Admin Dashboard** | Stat cards + charts: request volume, resource usage, visitor traffic |
| 12 | **Cache Management** | Clear từng cache hoặc tất cả caches |
| 13 | **SCImago Import** | Upload CSV SCImago để enrich journal metadata (quartile, impact factor) |

---

## 5. CÔNG NGHỆ SỬ DỤNG

### 5.1 Backend (50+ services, 37 controllers, 35 entities)

| Công nghệ | Mục đích |
|-----------|----------|
| **Spring Boot 3.5.14** | Application framework |
| **Java 21** | Ngôn ngữ (virtual threads, pattern matching) |
| **Spring Security** | Authentication + Authorization (JWT + RBAC) |
| **jjwt 0.12.3** | JWT creation, parsing, validation |
| **Spring Data JPA** | ORM cho SQL Server (Hibernate) |
| **Spring Data Neo4j** | Graph database operations (Cypher) |
| **Bucket4j** | Token-bucket rate limiting |
| **Caffeine Cache** | In-memory caching (1h TTL, max 500 entries) |
| **Spring Mail** | SMTP email (verification, password reset) |
| **Cloudinary** | Image hosting (avatars, backgrounds) |
| **Micrometer** | Metrics collection |
| **SpringDoc OpenAPI** | Swagger UI auto-generation |
| **Docker Compose** | SQL Server container cho local dev |

### 5.2 Frontend (126 components, 12 i18n namespaces)

| Công nghệ | Mục đích |
|-----------|----------|
| **React 18.3** | UI framework |
| **Vite 6** | Build tool (HMR siêu nhanh) |
| **React Router v7** | Client-side routing + lazy loading |
| **Zustand** | State management (persist middleware) |
| **Axios** | HTTP client (interceptor auto-attach JWT) |
| **shadcn/ui** | 50+ accessible UI components |
| **Radix UI** | Unstyled accessible primitives |
| **Tailwind CSS v4** | Utility-first CSS (dark theme) |
| **Recharts** | Charts: Area, Bar, Pie, Composed, Line |
| **vis-network** | Interactive graph visualization |
| **Framer Motion** | Page transitions + animations |
| **react-i18next** | Đa ngôn ngữ (EN, VI) |
| **@react-oauth/google** | Google Sign-In |
| **Sonner** | Toast notifications |
| **react-hook-form** | Form validation |
| **date-fns** | Date manipulation |

### 5.3 External APIs

| API | Dùng cho |
|-----|----------|
| **OpenAlex** (api.openalex.org) | Nguồn dữ liệu paper chính (240M+ works) |
| **Semantic Scholar** (api.semanticscholar.org) | Nguồn phụ, citation data |
| **CORE** (api.core.ac.uk) | Full-text paper access |
| **arXiv** (export.arxiv.org) | Preprint papers (XML Atom feed) |
| **DeepSeek AI** (ai-box.vn) | Summarization, methodology, batch analysis |
| **Google OAuth2** | Social login |
| **Cloudinary** | Image hosting |
| **Gmail SMTP** | Email delivery |

---

## 6. ĐIỂM NỔI BẬT

### 🌟 Dual Database Architecture
- **SQL Server** cho dữ liệu có cấu trúc (ACID, JOINs, báo cáo)
- **Neo4j** cho graph traversal (keyword network, nhanh hơn SQL JOIN 10-100x)
- Package isolation ngăn Spring Data scanning conflict

### 🌟 AI-Powered Analysis
- Tóm tắt paper tự động (2-3 câu, 4 sections)
- Trích xuất methodology (RCT, survey, case study...)
- So sánh chéo nhiều papers (comparative insight)
- Keyword expansion gợi ý từ khóa liên quan

### 🌟 Real-Time Notifications (SSE)
- Push notification ngay khi có sự kiện mới
- Fallback REST API nếu user offline
- Multi-tab support (1 user → nhiều SSE connections)

### 🌟 Smart Caching (3-layer)
- **L1**: In-memory ConcurrentHashMap (6h search cache, 1h graph cache)
- **L2**: Spring Caffeine Cache (1h TTL, max 500 entries)
- **L3**: Database (PaperCache 7-day TTL, AI summary persistence)

### 🌟 Multi-Source Data Sync
- 4 nguồn dữ liệu: OpenAlex, Semantic Scholar, CORE, arXiv
- DOI deduplication cross-source
- Abstract reconstruction từ inverted index
- Bulk sync với real-time progress tracking
- Save-on-search (fire-and-forget): Kết quả search được tự động lưu vào DB

### 🌟 Role-Based Usage Control
- ACADEMIC_USER: Giới hạn 30 searches/tháng
- RESEARCHER: Không giới hạn
- Auto-upgrade (1-click)
- Trial auto-downgrade (3 ngày cho Google users)
- Notification khi đạt 80% và 100% limit

### 🌟 KeepAlive Route Caching
- Giữ nguyên state khi chuyển tab (không mất form input, scroll position)
- LRU eviction (max 10 routes)

### 🌟 i18n Ready
- 2 ngôn ngữ: English + Vietnamese
- 12 translation namespaces
- Auto-detect browser language
- Accept-Language header gửi lên BE

---

## 7. DEMO KỊCH BẢN

### Kịch bản 1: Sinh viên tìm tài liệu nghiên cứu

```
1. Mở app → Landing page → Click "Get Started"
2. Đăng ký tài khoản ACADEMIC_USER
   (email, password, institution)
3. Đăng nhập → Overview dashboard
4. Vào Search → Gõ "deep learning"
5. Xem kết quả:
   - Danh sách 50 papers (title, authors, abstract, rating)
   - Weekly Breakout: "transformer", "attention mechanism" đang trending
   - Keyword Quick Stats: 15,432 papers, 234K citations
   - Neo4j graph: "deep learning" kết nối với "neural networks",
     "computer vision", "NLP", "reinforcement learning"
6. Click paper #1 → Paper Detail:
   - AI Summary: "This paper proposes a novel transformer architecture..."
   - Methodology: "Quantitative experiment with benchmark datasets"
   - Rating: 4.5/5 (Q1 journal, 1200+ citations)
   - Citation Export → Chọn BibTeX → Download
7. Bookmark paper → Thêm vào collection "Thesis References"
8. Đọc thêm 3 papers → Reading History tự động lưu
9. Hết tháng → Đạt 30/30 searches → Nhận notification:
   "Upgrade to Researcher for unlimited access?"
   → Click Upgrade → Trở thành RESEARCHER
```

### Kịch bản 2: Giảng viên phân tích xu hướng nghiên cứu

```
1. Đăng nhập RESEARCHER
2. Vào Search Author → Gõ tên đồng nghiệp
   → Xem: h-index, citation timeline, research focus,
     co-author network, top 5 papers
3. Vào Reports → Generate "Keyword Trend Report":
   - Chọn keyword: "quantum computing"
   - Năm: 2020-2025
   - Xuất PDF/Excel
   → Thấy: Publications tăng 340% từ 2020-2025
4. Vào Analytics → Search "federated learning" →
   Advanced Filter: Q1-Q2 journals, min 100 citations
   → Lọc được 23 papers chất lượng cao
5. Chọn 5 papers → Batch Analyze:
   → AI so sánh: "Paper A và C cùng hướng tiếp cận,
      nhưng Paper B đề xuất phương pháp khác biệt..."
6. Export tất cả citations dạng BibTeX → Import vào Zotero
```

### Kịch bản 3: Admin quản trị hệ thống

```
1. Đăng nhập ADMIN
2. Admin Dashboard:
   - 15,234 papers trong DB
   - 2,891 users (56% researcher, 44% academic)
   - Request volume chart: peak 10AM-2PM
3. Vào Sync Data → Bulk Sync:
   - Keywords: ["blockchain", "IoT", "edge computing", "5G"]
   - Source: OpenAlex
   - Papers/keyword: 100
   → Xem real-time progress: "3/4 keywords done, 287 papers synced"
4. Vào Users → Tìm user vi phạm → Disable account
5. Vào Audit Log → Xem lịch sử: "Admin A disabled User B at 14:32"
6. Vào Config → Chỉnh academic_monthly_search_limit: 30 → 50
```

---

## PHỤ LỤC: SỐ LIỆU DỰ ÁN

| Chỉ số | Con số |
|--------|--------|
| **Tổng file source code** | 333 Java files (BE) + 126 JSX files (FE) |
| **REST Controllers** | 37 |
| **Service classes** | 50+ |
| **JPA Entities** | 35 tables |
| **API Endpoints** | 56+ |
| **UI Components** | 50+ (shadcn/ui) + 30+ (custom) |
| **External APIs** | 7 (OpenAlex, Semantic Scholar, CORE, arXiv, DeepSeek, Google, Cloudinary) |
| **Ngôn ngữ hỗ trợ** | 2 (English, Vietnamese) |
| **User roles** | 3 (ADMIN, RESEARCHER, ACADEMIC_USER) |
| **Data sources** | 4 (OpenAlex, Semantic Scholar, CORE, arXiv) |
| **Cache layers** | 3 (in-memory, Caffeine, DB) |
| **Database** | 2 (SQL Server + Neo4j) |
