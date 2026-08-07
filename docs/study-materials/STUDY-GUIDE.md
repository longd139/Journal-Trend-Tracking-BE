# 🎓 SCITRACK — Study Guide: Từ Database Đi Lên

> **Mục tiêu:** Trả lời được mọi câu hỏi của giảng viên về DB và luồng code
> **Cách dùng:** Đọc theo thứ tự: DB → Repository → Service → Controller → Frontend

---

## PHẦN 1: TỔNG QUAN DATABASE (39 BẢNG)

SCITRACK dùng **2 database song song**:

| Database | Công nghệ | Lưu trữ gì? |
|----------|-----------|-------------|
| **SQL Server** | JPA / Hibernate | Users, Papers, Authors, Journals, Bookmarks, Notifications, Sync logs... |
| **Neo4j** | Spring Data Neo4j | Keywords, Papers (dạng node), quan hệ HAS_KEYWORD, USES_DATASET... |

> ⚡ **Tại sao 2 DB?** SQL Server lưu dữ liệu có cấu trúc (users, CRUD). Neo4j lưu đồ thị tri thức (keyword network) để tìm research gap — SQL không làm được graph traversal nhanh.

---

## PHẦN 2: 39 BẢNG — CHIA THEO NHÓM CHỨC NĂNG

### 🔴 NHÓM 1: CORE DOMAIN (Nền tảng — 10 bảng)

Đây là nhóm **QUAN TRỌNG NHẤT**. Giảng viên sẽ hỏi nhóm này đầu tiên.

---

#### Bảng 1: `ROLE` (Độc lập — không có FK)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **RoleID** | UUID | **PRIMARY KEY** |
| RoleName | VARCHAR(50) | UNIQUE, NOT NULL |
| Description | VARCHAR(500) | |

**Dữ liệu thực tế:** 3 role — `ADMIN`, `RESEARCHER`, `ACADEMIC_USER`

**Mối quan hệ:** Được tham chiếu bởi `USER.RoleID` (1 ROLE → N USER)

---

#### Bảng 2: `USER` (Người dùng)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **UserID** | UUID | **PRIMARY KEY** |
| **RoleID** | UUID | **FOREIGN KEY → ROLE(RoleID)** |
| Email | VARCHAR(255) | UNIQUE, NOT NULL |
| PasswordHash | VARCHAR(255) | NOT NULL |
| FullName | VARCHAR(200) | NOT NULL |
| Institution | VARCHAR(300) | |
| BackgroundUrl | VARCHAR(500) | |
| IsActive | BOOLEAN | DEFAULT 1 |
| CreatedAt | TIMESTAMP | NOT NULL |
| LastLoginAt | TIMESTAMP | |
| RoleExpiryAt | TIMESTAMP | |

**FK relationships:**
- `RoleID → ROLE.RoleID` — Mỗi user có 1 role
- Được tham chiếu bởi: USER_SESSION, VERIFICATION_TOKEN, ROLE_UPGRADE_REQUEST (x2), BOOKMARK, BOOKMARK_COLLECTION, FOLLOW, USER_READING_HISTORY, PAPER_RATING, NOTIFICATION, USER_SEARCH_HISTORY, USER_USAGE, REPORT, AUDIT_LOG, PDF_REQUEST (x2), DASHBOARD_WIDGET, PAPER_REPORT, USER_REPORT (x2), IDEA_ANALYSIS

> ⚡ **Đây là bảng trung tâm của toàn hệ thống** — 20+ bảng khác trỏ đến nó!

---

#### Bảng 3: `API_SOURCE` (Nguồn API bên ngoài)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **SourceID** | UUID | **PRIMARY KEY** |
| SourceName | VARCHAR(100) | UNIQUE, NOT NULL |
| BaseURL | VARCHAR(500) | NOT NULL |
| IsActive | BOOLEAN | DEFAULT 1 |
| RateLimitRPM | INTEGER | |
| LastSyncedAt | TIMESTAMP | |

**Dữ liệu thực tế:** `OpenAlex` (api.openalex.org), `Semantic Scholar` (api.semanticscholar.org)

**FK relationships:** Được tham chiếu bởi JOURNAL, AUTHOR, RESEARCH_PAPER, SYNC_LOG

---

#### Bảng 4: `RESEARCH_FIELD` (Lĩnh vực nghiên cứu — TỰ THAM CHIẾU)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **FieldID** | UUID | **PRIMARY KEY** |
| **ParentFieldID** | UUID | **FOREIGN KEY → RESEARCH_FIELD(FieldID)** — NULL nếu là field gốc |
| FieldName | VARCHAR(200) | UNIQUE, NOT NULL |
| IsTracked | BOOLEAN | DEFAULT 1 |
| Description | VARCHAR(500) | |

> ⚡ **Self-referencing FK:** `ParentFieldID → RESEARCH_FIELD.FieldID` — tạo cấu trúc cây phân cấp. VD: "Computer Science" là cha của "Artificial Intelligence" là cha của "Deep Learning"

**FK relationships:** Được tham chiếu bởi chính nó (ParentFieldID), JOURNAL, KEYWORD, RESEARCH_PAPER, RESEARCH_TOPIC, REPORT

---

#### Bảng 5: `JOURNAL` (Tạp chí khoa học)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **JournalID** | UUID | **PRIMARY KEY** |
| **SourceID** | UUID | **FOREIGN KEY → API_SOURCE(SourceID)** |
| **FieldID** | UUID | **FOREIGN KEY → RESEARCH_FIELD(FieldID)** — NULL được |
| JournalName | VARCHAR(500) | NOT NULL |
| ISSN | VARCHAR(20) | UNIQUE |
| Publisher | VARCHAR(300) | |
| ImpactFactor | DECIMAL(8,3) | |
| Quartile | VARCHAR(2) | Q1, Q2, Q3, Q4 |
| IsActive | BOOLEAN | DEFAULT 1 |

**FK relationships:**
- `SourceID → API_SOURCE.SourceID` — Journal này được crawl từ nguồn nào
- `FieldID → RESEARCH_FIELD.FieldID` — Thuộc lĩnh vực nào
- Được tham chiếu bởi RESEARCH_PAPER, FOLLOW, NOTIFICATION

---

#### Bảng 6: `AUTHOR` (Tác giả)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **AuthorID** | UUID | **PRIMARY KEY** |
| **SourceID** | UUID | **FOREIGN KEY → API_SOURCE(SourceID)** |
| ExternalAuthorID | VARCHAR(200) | |
| FullName | VARCHAR(300) | NOT NULL |
| Affiliation | VARCHAR(500) | |
| Country | VARCHAR(100) | |
| HIndex | INTEGER | DEFAULT 0 |
| TotalCitations | INTEGER | DEFAULT 0 |
| I10Index | INTEGER | DEFAULT 0 |
| WorksCount | INTEGER | DEFAULT 0 |

**UNIQUE:** `(SourceID, ExternalAuthorID)` — cùng 1 author có thể có ID khác nhau trên các nguồn khác nhau

**FK relationships:**
- `SourceID → API_SOURCE.SourceID`
- Được tham chiếu bởi PAPER_AUTHOR, FOLLOW

---

#### Bảng 7: `KEYWORD` (Từ khóa)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **KeywordID** | UUID | **PRIMARY KEY** |
| **FieldID** | UUID | **FOREIGN KEY → RESEARCH_FIELD(FieldID)** — NULL được |
| KeywordText | VARCHAR(300) | NOT NULL |
| NormalizedText | VARCHAR(300) | UNIQUE, NOT NULL |
| PaperCount | INTEGER | DEFAULT 0 |

> ⚡ `NormalizedText` là UNIQUE — keyword sau khi chuẩn hóa (lowercase, bỏ dấu...). VD: "Deep Learning" và "deep learning" → cùng NormalizedText

**FK relationships:**
- `FieldID → RESEARCH_FIELD.FieldID`
- Được tham chiếu bởi PAPER_KEYWORD, BOOKMARK, FOLLOW, NOTIFICATION, TOPIC_KEYWORD

---

#### Bảng 8: `RESEARCH_PAPER` (Bài báo — BẢNG QUAN TRỌNG NHẤT)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **PaperID** | UUID | **PRIMARY KEY** |
| **SourceID** | UUID | **FOREIGN KEY → API_SOURCE(SourceID)** |
| **JournalID** | UUID | **FOREIGN KEY → JOURNAL(JournalID)** — NULL được |
| **FieldID** | UUID | **FOREIGN KEY → RESEARCH_FIELD(FieldID)** — NULL được |
| Title | VARCHAR(1000) | NOT NULL |
| Abstract | NVARCHAR(MAX) | |
| DOI | VARCHAR(200) | UNIQUE |
| PubDate | DATE | |
| PubYear | SMALLINT | |
| CitationCount | INTEGER | DEFAULT 0 |
| Type | VARCHAR(50) | article, conference, review... |
| OpenAlexWorkId | VARCHAR(500) | |
| IsOpenAccess | BOOLEAN | DEFAULT 0 |
| PdfUrl | VARCHAR(500) | |
| AiSummary | NVARCHAR(MAX) | |
| AiSummarySections | NVARCHAR(MAX) | |
| Methodology | VARCHAR(100) | |
| CreatedAt | TIMESTAMP | NOT NULL |

**FK relationships:**
- `SourceID → API_SOURCE.SourceID` — Crawl từ nguồn nào
- `JournalID → JOURNAL.JournalID` — Đăng trên tạp chí nào (có thể NULL)
- `FieldID → RESEARCH_FIELD.FieldID` — Thuộc lĩnh vực nào (có thể NULL)
- Được tham chiếu bởi: PAPER_AUTHOR, PAPER_KEYWORD, BOOKMARK, USER_READING_HISTORY, PAPER_RATING, NOTIFICATION, PDF_REQUEST, PAPER_REPORT

> ⚡ **Đây là bảng dữ liệu chính của hệ thống** — mọi thứ xoay quanh RESEARCH_PAPER!

---

#### Bảng 9: `PAPER_AUTHOR` (Bảng nối N-N: Paper ↔ Author)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **PaperID** | UUID | **PRIMARY KEY (composite)** + **FK → RESEARCH_PAPER(PaperID)** |
| **AuthorID** | UUID | **PRIMARY KEY (composite)** + **FK → AUTHOR(AuthorID)** |
| AuthorOrder | INTEGER | DEFAULT 1 — thứ tự tác giả (1 = first author) |
| IsCorresponding | BOOLEAN | DEFAULT 0 — có phải corresponding author? |

> ⚡ **Composite Primary Key:** (PaperID, AuthorID) — 1 paper có nhiều author, 1 author có nhiều paper

---

#### Bảng 10: `PAPER_KEYWORD` (Bảng nối N-N: Paper ↔ Keyword)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **PaperID** | UUID | **PRIMARY KEY (composite)** + **FK → RESEARCH_PAPER(PaperID)** |
| **KeywordID** | UUID | **PRIMARY KEY (composite)** + **FK → KEYWORD(KeywordID)** |
| RelevanceScore | DOUBLE | Độ liên quan giữa keyword và paper |

> ⚡ **Composite Primary Key:** (PaperID, KeywordID) — 1 paper có nhiều keyword, 1 keyword xuất hiện trong nhiều paper

---

### 🟠 NHÓM 2: AUTH & SESSION (Xác thực — 3 bảng)

---

#### Bảng 11: `USER_SESSION` (Phiên đăng nhập)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **SessionID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| TokenHash | VARCHAR(500) | NOT NULL — JWT access token đã hash |
| RefreshTokenHash | VARCHAR(500) | |
| ExpiresAt | TIMESTAMP | NOT NULL |
| RefreshExpiresAt | TIMESTAMP | |
| CreatedAt | TIMESTAMP | NOT NULL |

---

#### Bảng 12: `VERIFICATION_TOKEN` (Token xác thực email)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **TokenID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| Token | VARCHAR(255) | UNIQUE, NOT NULL |
| TokenType | VARCHAR(20) | NOT NULL — EMAIL_VERIFY, PASSWORD_RESET |
| ExpiresAt | TIMESTAMP | NOT NULL |
| CreatedAt | TIMESTAMP | NOT NULL |
| IsUsed | BOOLEAN | DEFAULT 0 |

---

#### Bảng 13: `ROLE_UPGRADE_REQUEST` (Yêu cầu nâng cấp role)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **RequestID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** — Ai yêu cầu |
| FullName | VARCHAR(200) | NOT NULL |
| Institution | VARCHAR(300) | NOT NULL |
| ResearchField | VARCHAR(200) | NOT NULL |
| Position | VARCHAR(100) | NOT NULL |
| Orcid | VARCHAR(50) | |
| Reason | NVARCHAR(MAX) | NOT NULL |
| PaperLinks | NVARCHAR(MAX) | |
| PaperFileUrls | NVARCHAR(MAX) | |
| Status | VARCHAR(20) | DEFAULT 'PENDING' |
| AdminNote | VARCHAR(500) | |
| **ReviewedBy** | UUID | **FOREIGN KEY → USER(UserID)** — Admin nào duyệt |
| ReviewedAt | TIMESTAMP | |
| CreatedAt | TIMESTAMP | NOT NULL |

> ⚡ **2 FK đến cùng bảng USER:** `UserID` (người gửi) và `ReviewedBy` (admin duyệt)

---

### 🟡 NHÓM 3: USER FEATURES (Tính năng người dùng — 8 bảng)

---

#### Bảng 14: `BOOKMARK_COLLECTION` (Bộ sưu tập bookmark)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **CollectionID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| Name | VARCHAR(200) | NOT NULL |
| Description | VARCHAR(500) | |
| LastNotifiedMilestone | INTEGER | DEFAULT 0 |
| CreatedAt | TIMESTAMP | NOT NULL |
| UpdatedAt | TIMESTAMP | NOT NULL |

---

#### Bảng 15: `BOOKMARK` (Đánh dấu)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **BookmarkID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| **PaperID** | UUID | **FOREIGN KEY → RESEARCH_PAPER(PaperID)** — NULL được |
| **KeywordID** | UUID | **FOREIGN KEY → KEYWORD(KeywordID)** — NULL được |
| **CollectionID** | UUID | **FOREIGN KEY → BOOKMARK_COLLECTION(CollectionID)** — NULL được |
| Notes | VARCHAR(500) | |
| LastNotifiedMilestone | INTEGER | DEFAULT 0 |
| CreatedAt | TIMESTAMP | NOT NULL |

> ⚡ Bookmark có thể đánh dấu Paper HOẶC Keyword HOẶC cả hai. CollectionID để nhóm lại.

---

#### Bảng 16: `FOLLOW` (Theo dõi)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **FollowID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| **JournalID** | UUID | **FOREIGN KEY → JOURNAL(JournalID)** — NULL được |
| **TopicID** | UUID | **FOREIGN KEY → RESEARCH_TOPIC(TopicID)** — NULL được |
| **KeywordID** | UUID | **FOREIGN KEY → KEYWORD(KeywordID)** — NULL được |
| **AuthorID** | UUID | **FOREIGN KEY → AUTHOR(AuthorID)** — NULL được |
| NotifyEnabled | BOOLEAN | DEFAULT 1 |
| CreatedAt | TIMESTAMP | NOT NULL |

> ⚡ **4 FK tùy chọn:** User có thể follow Journal, Topic, Keyword, hoặc Author — ít nhất 1 trong 4

---

#### Bảng 17: `USER_READING_HISTORY` (Lịch sử đọc)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **ReadingHistoryID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| **PaperID** | UUID | **FOREIGN KEY → RESEARCH_PAPER(PaperID)** |
| ViewedAt | TIMESTAMP | NOT NULL |

---

#### Bảng 18: `PAPER_RATING` (Đánh giá paper)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **RatingID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| **PaperID** | UUID | **FOREIGN KEY → RESEARCH_PAPER(PaperID)** |
| Score | INTEGER | NOT NULL — 1 đến 5 |
| RatedAt | TIMESTAMP | NOT NULL |

**UNIQUE:** `(UserID, PaperID)` — Mỗi user chỉ đánh giá 1 paper 1 lần

---

#### Bảng 19: `NOTIFICATION` (Thông báo)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **NotifID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| Type | VARCHAR(20) | NOT NULL |
| Title | VARCHAR(300) | NOT NULL |
| Message | NVARCHAR(MAX) | |
| **RelatedPaperID** | UUID | **FOREIGN KEY → RESEARCH_PAPER(PaperID)** — NULL được |
| **RelatedJournalID** | UUID | **FOREIGN KEY → JOURNAL(JournalID)** — NULL được |
| **RelatedTopicID** | UUID | **FOREIGN KEY → RESEARCH_TOPIC(TopicID)** — NULL được |
| **RelatedKeywordID** | UUID | **FOREIGN KEY → KEYWORD(KeywordID)** — NULL được |
| IsRead | BOOLEAN | DEFAULT 0 |
| CreatedAt | TIMESTAMP | NOT NULL |

> ⚡ **4 FK tùy chọn** để link notification đến đối tượng liên quan

---

#### Bảng 20: `USER_SEARCH_HISTORY` (Lịch sử tìm kiếm)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **SearchHistoryID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| SearchText | VARCHAR(500) | NOT NULL |
| SearchType | VARCHAR(20) | NOT NULL — KEYWORD, AUTHOR, JOURNAL, GAP_IDEA |
| SearchedAt | TIMESTAMP | NOT NULL |

---

#### Bảng 21: `USER_USAGE` (Thống kê sử dụng — giới hạn Academic User)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **UsageID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| UsageMonth | VARCHAR(7) | NOT NULL — VD: "2026-08" |
| SearchCount | INTEGER | DEFAULT 0 |
| ViewCount | INTEGER | DEFAULT 0 |
| ChartViewCount | INTEGER | DEFAULT 0 |
| LastUpdated | TIMESTAMP | NOT NULL |

> ⚡ **Giới hạn Academic User:** SearchCount, ViewCount bị giới hạn theo tháng. Researcher thì không giới hạn.

---

### 🟢 NHÓM 4: ANALYTICS (Phân tích — 7 bảng)

---

#### Bảng 22: `RESEARCH_TOPIC` (Chủ đề nghiên cứu)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **TopicID** | UUID | **PRIMARY KEY** |
| **FieldID** | UUID | **FOREIGN KEY → RESEARCH_FIELD(FieldID)** — NULL được |
| TopicName | VARCHAR(300) | NOT NULL |
| IsTrending | BOOLEAN | DEFAULT 0 |
| TrendScore | DECIMAL(10,4) | DEFAULT 0 |
| PaperCount | INTEGER | DEFAULT 0 |
| UpdatedAt | TIMESTAMP | NOT NULL |

---

#### Bảng 23: `TOPIC_KEYWORD` (N-N: Topic ↔ Keyword)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **TopicID** | UUID | **PRIMARY KEY (composite)** + **FK → RESEARCH_TOPIC(TopicID)** |
| **KeywordID** | UUID | **PRIMARY KEY (composite)** + **FK → KEYWORD(KeywordID)** |
| Weight | DECIMAL(5,4) | DEFAULT 1.0 — trọng số của keyword trong topic |

---

#### Bảng 24: `SEARCH_KEYWORD` (Từ khóa được tìm kiếm nhiều)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **SearchKeywordID** | UUID | **PRIMARY KEY** |
| KeywordText | VARCHAR(500) | NOT NULL |
| NormalizedText | VARCHAR(500) | UNIQUE, NOT NULL |
| SearchCount | INTEGER | DEFAULT 1 |
| LastSearchedAt | TIMESTAMP | NOT NULL |
| CreatedAt | TIMESTAMP | NOT NULL |

> ⚡ **Độc lập — không có FK.** Dùng để track hot keywords từ phía người dùng.

---

#### Bảng 25: `TRENDING_TOPIC` (Chủ đề trending)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **TrendingTopicID** | UUID | **PRIMARY KEY** |
| TopicName | VARCHAR(300) | NOT NULL |
| PaperCount | INTEGER | DEFAULT 0 |
| Source | VARCHAR(50) | NOT NULL |
| DisplayOrder | INTEGER | DEFAULT 0 |
| UpdatedAt | TIMESTAMP | NOT NULL |

> ⚡ **Độc lập — không có FK.** Cache trending topics để hiển thị nhanh.

---

#### Bảng 26: `PUBLICATION_TREND` (Xu hướng xuất bản)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **TrendID** | UUID | **PRIMARY KEY** |
| PeriodType | VARCHAR(10) | NOT NULL — MONTH, YEAR |
| PeriodValue | VARCHAR(15) | NOT NULL — "2026-08", "2026" |
| TrendTarget | VARCHAR(10) | NOT NULL — FIELD, JOURNAL, KEYWORD |
| TargetID | UUID | NOT NULL — ID của đối tượng |
| PaperCount | INTEGER | DEFAULT 0 |
| CitationCount | INTEGER | DEFAULT 0 |
| GrowthRate | DECIMAL(10,4) | |
| CalculatedAt | TIMESTAMP | NOT NULL |

> ⚡ **Độc lập — không có FK.** `TargetID` là UUID nhưng không ràng buộc FK vì có thể trỏ đến nhiều bảng khác nhau (polymorphic).

---

#### Bảng 27: `KEYWORD_TREND_CACHE` (Cache xu hướng keyword)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **CacheID** | UUID | **PRIMARY KEY** |
| Keyword | VARCHAR(500) | NOT NULL |
| NormalizedKeyword | VARCHAR(500) | UNIQUE, NOT NULL |
| ReportData | NVARCHAR(MAX) | NOT NULL — JSON string |
| CreatedAt | TIMESTAMP | NOT NULL |
| UpdatedAt | TIMESTAMP | NOT NULL |

> ⚡ **Độc lập — không có FK.** Cache kết quả phân tích trend để không phải tính lại.

---

#### Bảng 28: `REPORT` (Báo cáo người dùng tạo)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **ReportID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| **FieldID** | UUID | **FOREIGN KEY → RESEARCH_FIELD(FieldID)** — NULL được |
| ReportName | VARCHAR(300) | NOT NULL |
| PeriodStart | DATE | NOT NULL |
| PeriodEnd | DATE | NOT NULL |
| Status | VARCHAR(20) | DEFAULT 'generating' |
| Format | VARCHAR(5) | DEFAULT 'pdf' |
| FileURL | VARCHAR(1000) | |
| CreatedAt | TIMESTAMP | NOT NULL |

---

### 🔵 NHÓM 5: ADMIN & SYSTEM (Quản trị — 9 bảng)

---

#### Bảng 29: `SYNC_LOG` (Nhật ký đồng bộ dữ liệu)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **LogID** | UUID | **PRIMARY KEY** |
| **SourceID** | UUID | **FOREIGN KEY → API_SOURCE(SourceID)** |
| SyncType | VARCHAR(15) | NOT NULL — FULL, INCREMENTAL, KEYWORD |
| IsManual | BOOLEAN | DEFAULT 0 |
| Status | VARCHAR(20) | DEFAULT 'running' |
| PapersFetched | INTEGER | DEFAULT 0 |
| PapersInserted | INTEGER | DEFAULT 0 |
| ErrorMessage | NVARCHAR(MAX) | |
| StartedAt | TIMESTAMP | NOT NULL |
| CompletedAt | TIMESTAMP | |

---

#### Bảng 30: `AUDIT_LOG` (Nhật ký hoạt động admin)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **AuditID** | UUID | **PRIMARY KEY** |
| **AdminID** | UUID | **FOREIGN KEY → USER(UserID)** |
| Action | VARCHAR(200) | NOT NULL |
| TargetTable | VARCHAR(100) | |
| TargetID | VARCHAR(100) | |
| OldValue | NVARCHAR(MAX) | |
| NewValue | NVARCHAR(MAX) | |
| IPAddress | VARCHAR(45) | |
| CreatedAt | TIMESTAMP | NOT NULL |

---

#### Bảng 31: `SYSTEM_CONFIG` (Cấu hình hệ thống)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **ConfigID** | UUID | **PRIMARY KEY** |
| ConfigKey | NVARCHAR | UNIQUE, NOT NULL |
| ConfigValue | NVARCHAR | NOT NULL |
| Description | VARCHAR(500) | |
| UpdatedAt | TIMESTAMP | NOT NULL |
| UpdatedBy | UUID | |

> ⚡ **Độc lập — key-value store.** Lưu các cấu hình như rate limit, sync interval...

---

#### Bảng 32: `AUTO_SYNC_KEYWORD` (Từ khóa tự động sync)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **KeywordID** | UUID | **PRIMARY KEY** |
| Keyword | VARCHAR(500) | NOT NULL |
| IntervalMinutes | INTEGER | DEFAULT 60 |
| Enabled | BOOLEAN | DEFAULT 1 |
| LastSyncedAt | TIMESTAMP | |
| CreatedAt | TIMESTAMP | NOT NULL |

> ⚡ **Độc lập — không có FK.** Admin cấu hình keyword nào được tự động sync định kỳ.

---

#### Bảng 33: `PDF_REQUEST` (Yêu cầu PDF)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **RequestID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| **PaperID** | UUID | **FOREIGN KEY → RESEARCH_PAPER(PaperID)** |
| Status | VARCHAR(20) | DEFAULT 'PENDING' |
| UserMessage | VARCHAR(1000) | |
| AdminNote | VARCHAR(1000) | |
| RequestedAt | TIMESTAMP | NOT NULL |
| ResolvedAt | TIMESTAMP | |
| **ResolvedByAdminID** | UUID | **FOREIGN KEY → USER(UserID)** |

> ⚡ **2 FK đến USER:** `UserID` (người yêu cầu) và `ResolvedByAdminID` (admin xử lý)

---

#### Bảng 34: `DASHBOARD_WIDGET` (Widget dashboard)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **WidgetID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| WidgetType | VARCHAR(30) | NOT NULL |
| Title | VARCHAR(200) | NOT NULL |
| Config | NVARCHAR(MAX) | — JSON config |
| PositionX | INTEGER | DEFAULT 0 |
| PositionY | INTEGER | DEFAULT 0 |
| Width | INTEGER | DEFAULT 4 |
| Height | INTEGER | DEFAULT 3 |
| CreatedAt | TIMESTAMP | NOT NULL |
| UpdatedAt | TIMESTAMP | NOT NULL |

---

#### Bảng 35: `PAPER_CACHE` (Cache paper)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **PaperID** | UUID | **PRIMARY KEY** (không phải FK — đây là cache độc lập) |
| Title | VARCHAR(1000) | |
| Abstract | NVARCHAR(MAX) | |
| Doi | VARCHAR(200) | |
| PubYear | SMALLINT | |
| CitationCount | INTEGER | |
| JournalName | VARCHAR(500) | |
| SourceUrl | VARCHAR(500) | |
| OpenAlexWorkId | VARCHAR(500) | |
| DataJson | NVARCHAR(MAX) | — Toàn bộ JSON response từ API |
| UpdatedAt | TIMESTAMP | NOT NULL |

> ⚡ **Độc lập — không FK.** Cache paper từ API bên ngoài. PaperID match với RESEARCH_PAPER nhưng không ràng buộc FK.

---

#### Bảng 36: `PAPER_REPORT` (Báo cáo paper — người dùng report paper lỗi)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **ReportID** | UUID | **PRIMARY KEY** |
| **PaperID** | UUID | NOT NULL |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| Reason | VARCHAR(50) | NOT NULL |
| Description | NVARCHAR(MAX) | |
| ImageUrls | NVARCHAR(MAX) | |
| Status | VARCHAR(20) | DEFAULT 'PENDING' |
| CreatedAt | TIMESTAMP | NOT NULL |
| UpdatedAt | TIMESTAMP | |

> ⚡ **PaperID không có FK constraint** — có thể là lỗi thiết kế hoặc cố ý

---

#### Bảng 37: `USER_REPORT` (Báo cáo người dùng — tính năng mới)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **ReportID** | UUID | **PRIMARY KEY** |
| **UserID** | UUID | **FOREIGN KEY → USER(UserID)** |
| ReportType | VARCHAR(50) | NOT NULL |
| TargetType | VARCHAR(50) | |
| TargetID | UUID | |
| Title | VARCHAR(300) | NOT NULL |
| Description | NVARCHAR(MAX) | |
| Status | VARCHAR(20) | DEFAULT 'PENDING' |
| AdminNote | NVARCHAR(MAX) | |
| **ResolvedByAdminID** | UUID | **FOREIGN KEY → USER(UserID)** |
| CreatedAt | TIMESTAMP | NOT NULL |
| ResolvedAt | TIMESTAMP | |

> ⚡ **Tính năng CHƯA CODE** — có trong `plan-admin-notification.md`

---

### 🟣 NHÓM 6: GAP EXPLORER & AI (2 bảng)

---

#### Bảng 38: `IDEA_ANALYSIS` (Phân tích ý tưởng nghiên cứu)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **analysis_id** | UUID | **PRIMARY KEY** |
| **user_id** | UUID | **FOREIGN KEY → USER(UserID)** |
| idea_text | NVARCHAR(MAX) | NOT NULL |
| idea_hash | VARCHAR(64) | NOT NULL |
| keywords | NVARCHAR(MAX) | |
| result_json | NVARCHAR(MAX) | NOT NULL — JSON kết quả phân tích |
| paper_count | INTEGER | DEFAULT 0 |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | |

> ⚡ **Chú ý:** Bảng này dùng snake_case (analysis_id, user_id...) — khác với các bảng khác dùng PascalCase. Đây là bảng cho tính năng Research Gap Explorer.

---

#### Bảng 39: `PAPER_EVALUATION_CACHE` (Cache đánh giá paper)

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| **cache_id** | UUID | **PRIMARY KEY** |
| paper_id | UUID | NOT NULL |
| idea_hash | VARCHAR(64) | NOT NULL |
| criteria_json | NVARCHAR(MAX) | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |

**UNIQUE:** `(paper_id, idea_hash)` — Mỗi cặp paper+idea chỉ cache 1 lần

> ⚡ **Không có FK → RESEARCH_PAPER** — paper_id là UUID nhưng không ràng buộc

---

## PHẦN 3: SƠ ĐỒ QUAN HỆ TỔNG THỂ

```
                    ┌──────────────┐
                    │    ROLE      │
                    └──────┬───────┘
                           │ 1:N
                    ┌──────▼───────┐
        ┌───────────│     USER     │──────────────────────┐
        │           └──────┬───────┘                      │
        │                  │                              │
        │     ┌────────────┼────────────┐                 │
        │     │            │            │                 │
        │  ┌──▼───┐   ┌────▼────┐  ┌───▼────┐           │
        │  │SESSION│  │VERIFY   │  │UPGRADE │           │
        │  │       │  │TOKEN    │  │REQUEST │           │
        │  └──────┘  └─────────┘  └────────┘           │
        │                                               │
        │  ┌────────────────────────────────────────┐   │
        │  │         USER FEATURES                   │   │
        │  │  BOOKMARK, FOLLOW, HISTORY, RATING,    │   │
        │  │  NOTIFICATION, SEARCH_HISTORY, USAGE   │   │
        │  └────────────────────────────────────────┘   │
        │                                               │
        │  ┌──────────┐                                 │
        │  │API_SOURCE│────┐                            │
        │  └──────────┘    │                            │
        │                  │                            │
        │  ┌───────────────▼───────────────────────┐    │
        │  │           CORE RESEARCH                │    │
        │  │                                        │    │
        │  │  RESEARCH_FIELD ◄── self-ref           │    │
        │  │       │                                │    │
        │  │  ┌────▼────┐  ┌────────┐  ┌───────┐  │    │
        │  │  │ JOURNAL  │  │AUTHOR  │  │KEYWORD│  │    │
        │  │  └────┬─────┘  └───┬────┘  └───┬───┘  │    │
        │  │       │            │            │       │    │
        │  │  ┌────▼────────────▼────────────▼───┐  │    │
        │  │  │         RESEARCH_PAPER            │  │    │
        │  │  └────┬────────────┬────────────┬────┘  │    │
        │  │       │            │            │       │    │
        │  │  ┌────▼────┐  ┌───▼────┐       │       │    │
        │  │  │PAPER_   │  │PAPER_  │       │       │    │
        │  │  │AUTHOR   │  │KEYWORD │       │       │    │
        │  │  └─────────┘  └────────┘       │       │    │
        │  └────────────────────────────────┘       │    │
        │                                           │    │
        │  ┌──────────────────────────────────┐     │    │
        │  │    ANALYTICS & ADMIN              │     │    │
        │  │  TOPIC, SYNC_LOG, AUDIT_LOG,     │     │    │
        │  │  REPORT, PDF_REQUEST, etc.       │     │    │
        │  └──────────────────────────────────┘     │    │
        └───────────────────────────────────────────┘    │
                                                         │
        ┌────────────────────────────────────────────────┘
        │
        │  ┌──────────────────────────┐
        │  │  GAP EXPLORER & AI       │
        │  │  IDEA_ANALYSIS,          │
        │  │  PAPER_EVALUATION_CACHE  │
        │  └──────────────────────────┘
```

---

## PHẦN 4: CÂU HỎI GIẢNG VIÊN THƯỜNG HỎI VỀ DB

### Q1: "Bảng RESEARCH_PAPER có những khóa ngoại nào?"

**Trả lời:** 3 FK:
- `SourceID → API_SOURCE.SourceID` (paper được crawl từ OpenAlex hay Semantic Scholar)
- `JournalID → JOURNAL.JournalID` (đăng trên tạp chí nào, có thể NULL)
- `FieldID → RESEARCH_FIELD.FieldID` (thuộc lĩnh vực nào, có thể NULL)

### Q2: "Mối quan hệ giữa Paper và Author là gì?"

**Trả lời:** N-N (nhiều-nhiều) qua bảng trung gian `PAPER_AUTHOR`.
- 1 paper có thể có nhiều author
- 1 author có thể viết nhiều paper
- Bảng PAPER_AUTHOR có composite PK (PaperID, AuthorID)
- Có thêm AuthorOrder (thứ tự tác giả) và IsCorresponding

### Q3: "Mối quan hệ giữa Paper và Keyword là gì?"

**Trả lời:** N-N qua bảng `PAPER_KEYWORD`.
- Composite PK (PaperID, KeywordID)
- Có thêm RelevanceScore (độ liên quan)

### Q4: "Tại sao RESEARCH_FIELD có khóa ngoại đến chính nó?"

**Trả lời:** Self-referencing FK `ParentFieldID → FieldID` để tạo cấu trúc cây phân cấp.
- "Computer Science" → "AI" → "Deep Learning"
- Field gốc có ParentFieldID = NULL
- Đây là pattern phổ biến cho danh mục phân cấp

### Q5: "Sự khác biệt giữa SEARCH_KEYWORD và KEYWORD?"

**Trả lời:**
- `KEYWORD` — keyword gắn với paper (có FK đến RESEARCH_FIELD, liên kết qua PAPER_KEYWORD)
- `SEARCH_KEYWORD` — keyword người dùng đã tìm kiếm (độc lập, dùng để track hot keywords)

### Q6: "Tại sao cần cả SQL Server và Neo4j?"

**Trả lời:**
- SQL Server: Lưu dữ liệu có cấu trúc (users, papers, CRUD operations)
- Neo4j: Lưu đồ thị tri thức (keyword network, paper-keyword relationships)
- Graph traversal (tìm research gap, co-occurring keywords) trong SQL rất chậm, Neo4j làm trong mili-giây
- 2 DB được sync đồng thời khi crawl dữ liệu

### Q7: "Bảng nào có composite primary key?"

**Trả lời:** 3 bảng:
- `PAPER_AUTHOR` — (PaperID, AuthorID)
- `PAPER_KEYWORD` — (PaperID, KeywordID)
- `TOPIC_KEYWORD` — (TopicID, KeywordID)

### Q8: "Bảng USER_USAGE dùng để làm gì?"

**Trả lời:** Giới hạn lượt sử dụng cho ACADEMIC_USER. Mỗi tháng, SearchCount và ViewCount bị giới hạn. Researcher không bị giới hạn. Được enforce trong `PaperSearchServiceImpl.checkAndIncrementUsage()`.

---

## PHẦN 5: CÁC BẢNG ĐỘC LẬP (KHÔNG CÓ FK)

6 bảng **không có bất kỳ FK nào**:

| Bảng | Lý do |
|------|-------|
| `ROLE` | Bảng danh mục gốc — được tham chiếu bởi USER |
| `SEARCH_KEYWORD` | Chỉ để track hot keywords — không cần FK |
| `TRENDING_TOPIC` | Cache trending topics — độc lập |
| `PUBLICATION_TREND` | TargetID polymorphic (có thể trỏ đến nhiều bảng khác nhau) |
| `KEYWORD_TREND_CACHE` | Cache kết quả phân tích |
| `SYSTEM_CONFIG` | Key-value store — không cần FK |
| `AUTO_SYNC_KEYWORD` | Cấu hình sync — độc lập |
| `PAPER_CACHE` | Cache paper từ API — PaperID match nhưng không FK |

---

---

## PHẦN 6: BACKEND CODE MAP — "SỐ LIỆU NÀY TỪ FILE NÀO?"

Đây là **câu hỏi giảng viên hay hỏi nhất**. Cách trả lời: **trace từ Controller → Service → Repository → Entity**.

### 🗺️ Cấu trúc thư mục BE

```
src/main/java/com/sra/journal_tracking/
├── config/          (14 files) — Cấu hình Spring, JPA, Neo4j, Security, Rate Limit
├── constants/       (1 file)  — Hằng số keyword
├── controller/      (43 files) — REST API endpoints (đây là nơi nhận request từ FE)
├── dto/             (120+ files) — Data Transfer Objects (request/response)
├── entity/
│   ├── jpa/         (39 files) — JPA Entities (ánh xạ 1-1 với bảng SQL)
│   └── neo4j/       (9 files)  — Neo4j Entities (node trong graph)
├── exception/       (7 files)  — Custom exceptions, global handler
├── repository/
│   ├── jpa/         (39 files) — JPA Repository (truy vấn SQL)
│   └── neo4j/       (3 files)  — Neo4j Repository (Cypher queries)
├── security/        (7 files)  — JWT, Spring Security, CustomUserDetails
├── service/         (60+ files) — Business logic (interface)
│   └── impl/        (25+ files) — Business logic (implementation)
└── test/            (1 file)   — Database test runner
```

### 🎯 3 LUỒNG CODE QUAN TRỌNG NHẤT

---

### LUỒNG 1: SEARCH — "Khi user gõ 'deep learning' vào ô search"

> ⚡ **Có 2 đường search khác nhau, tùy vào loại request:**

```
FE (SearchPapers.jsx)
  │  Gọi: GET /api/v1/papers/search?query=deep+learning&page=0&size=10
  │  (axiosClient tự động gắn JWT token vào header)
  ▼
PaperSearchController.java  ← searchPapers()
  │
  │  KIỂM TRA: Là simple keyword search (không authorName, không journalId)
  │  VÀ sort = "relevance" (default)?
  │
  ├── YES → ĐƯỜNG A: PaperSearchOrchestrator.searchByKeyword()
  │          (Graph-based, data từ OpenAlex API)
  │
  └── NO  → ĐƯỜNG B: PaperSearchServiceImpl.searchPapers()
             (SQL-based, data từ SQL Server + keyword expansion)
```

---

#### ĐƯỜNG A: PaperSearchOrchestrator.searchByKeyword() (keyword search mặc định)

```
PaperSearchOrchestrator.java  ← ⚡ ĐÂY LÀ NÃO CỦA SEARCH
  │
  │  BƯỚC 1: Cache check (6 tiếng)
  │    ConcurrentHashMap cache → nếu hit → trả về kết quả cached ngay
  │
  │  BƯỚC 2: Check usage limit (chỉ cho ACADEMIC_USER)
  │    UserUsageRepository → SELECT SearchCount FROM USER_USAGE
  │    Nếu vượt limit (default 30/tháng) → throw UsageLimitExceededException
  │    Nếu chưa → incrementSearchCount()
  │
  │  BƯỚC 3: Ghi search history (non-blocking try/catch)
  │    searchKeywordService.recordSearch(keyword)      → SEARCH_KEYWORD
  │    userSearchHistoryService.recordSearch(...)      → USER_SEARCH_HISTORY
  │
  │  BƯỚC 4: Gọi OpenAlex API TRỰC TIẾP (2 lần gọi)
  │    openAlexFallbackSearchService.searchTopCited(keyword, limit)
  │      → GET api.openalex.org/works?search=...&sort=cited_by_count:desc
  │    openAlexFallbackSearchService.searchNoYearFilter(keyword, limit)
  │      → GET api.openalex.org/works?search=...&sort=publication_date:desc
  │    → Merge 2 kết quả, deduplicate theo paperId
  │
  │  BƯỚC 5: Async save vào DB
  │    dataSyncService.saveWorksFromOpenAlexAsync(papers)
  │    → Lưu vào RESEARCH_PAPER + Neo4j (background, không block response)
  │
  │  BƯỚC 6: Build PaperSearchResultDTO → cache → return
  ▼
Trả về JSON → FE hiển thị danh sách papers
```

#### ĐƯỜNG B: PaperSearchServiceImpl.searchPapers() (search có filter hoặc sort khác)

```
PaperSearchServiceImpl.java
  │
  │  BƯỚC 1: Check usage limit (giống Đường A)
  │
  │  BƯỚC 2: Keyword expansion
  │    keywordExpansionService.expand(query, 6)
  │    → Mở rộng từ khóa: synonyms, token variants, co-occurring terms
  │
  │  BƯỚC 3: Query SQL Server (ResearchPaperRepository)
  │    findPrimaryCandidatesWithoutFilters(term, startYear, endYear, pageable)
  │      → SELECT * FROM RESEARCH_PAPER WHERE title LIKE '%deep learning%'
  │        OR abstract LIKE '%deep learning%' ...
  │    Hoặc findPrimaryCandidates(term, authorName, journalId, ...)
  │      → (nếu có lọc author/journal)
  │
  │  BƯỚC 4: Tính relevance score cho mỗi paper
  │    calculateRelevanceScore(paper, query)
  │      → exact match +20, token match +8, expanded match +5...
  │    Lọc paper có score < 8.0, sắp xếp giảm dần
  │
  │  BƯỚC 5: Nếu không có kết quả → fallback OpenAlex
  │    openAlexFallbackSearchService.searchNoYearFilter(query, size)
  │    searchBackfillService.requestBackfill(query, size) → async sync
  │
  │  BƯỚC 6: Build PaperSearchResultDTO → return
  ▼
Trả về JSON → FE hiển thị danh sách papers
```

**Các endpoint search khác trong PaperSearchController:**

| Endpoint | Method | Dùng khi nào |
|----------|--------|-------------|
| `GET /search` | `searchPapers()` | Search chính |
| `GET /search/openalex` | `searchOpenAlex()` | Search trực tiếp OpenAlex |
| `GET /search/author` | `searchByAuthor()` | Lọc theo tác giả (gọi OpenAlex API) |
| `GET /search/journal` | `searchByJournal()` | Lọc theo journal (query SQL) |
| `GET /filter/advanced` | `advancedFilter()` | Advanced filter (RESEARCHER only) |
| `GET /{paperId}` | `getPaperDetails()` | Chi tiết 1 paper |
| `GET /search/graph` | `graphSearch()` | Graph-based search (Neo4j) |

**Files involved (theo thứ tự):**

| Layer | File | Vai trò |
|-------|------|---------|
| **Controller** | `controller/PaperSearchController.java` | Nhận HTTP request, routing |
| **Orchestrator** | `service/PaperSearchOrchestrator.java` | Cache + OpenAlex + usage check |
| **Service** | `service/impl/PaperSearchServiceImpl.java` | SQL search + keyword expansion |
| **Service** | `service/OpenAlexFallbackSearchService.java` | Gọi OpenAlex API |
| **Service** | `service/GraphService.java` | Neo4j queries (cho graphSearch endpoint) |
| **Service** | `service/KeywordExpansionService.java` | Mở rộng từ khóa |
| **Service** | `service/SearchKeywordService.java` | Ghi hot keywords |
| **Service** | `service/UserSearchHistoryService.java` | Ghi lịch sử search |
| **Service** | `service/SearchBackfillService.java` | Async backfill |
| **Repository** | `repository/jpa/ResearchPaperRepository.java` | `findPrimaryCandidates`, `findByIdWithDetails` |
| **Repository** | `repository/jpa/UserUsageRepository.java` | `incrementSearchCount`, `findByUser_UserIdAndUsageMonth` |
| **Repository** | `repository/jpa/SystemConfigRepository.java` | `findByConfigKey("academic_monthly_search_limit")` |
| **Repository** | `repository/jpa/PaperKeywordRepository.java` | `findRelatedKeywordTexts` (co-occurring terms) |
| **Entity** | `entity/jpa/ResearchPaper.java` | Ánh xạ bảng RESEARCH_PAPER |
| **DTO** | `dto/paper/PaperSearchRequestDTO.java` | Request: query, page, size, sortBy, pubYearFrom, pubYearTo |
| **DTO** | `dto/paper/PaperSearchResultDTO.java` | Response: papers[], totalElements, totalPages |
| **DTO** | `dto/paper/PaperDetailResponseDTO.java` | 1 paper: title, abstract, doi, authors[], keywords[], citationCount...

---

### LUỒNG 2: AUTH — "Khi user đăng nhập"

```
FE (LoginPage.jsx)
  │  Gọi: POST /api/v1/auth/login
  │  Body: { email, password }
  ▼
AuthController.java
  │  @PostMapping("/login")
  │  Nhận LoginRequest (email, password)
  │  Gọi: authService.login(loginRequest)
  ▼
AuthServiceImpl.java
  │  public AuthResponse login(LoginRequest request)
  │
  │  BƯỚC 1: Tìm user theo email
  │    User user = userRepository.findByEmail(email)
  │    → SELECT * FROM [USER] WHERE Email = ?
  │    Nếu không tìm thấy → throw AppException("Invalid credentials")
  │
  │  BƯỚC 2: Kiểm tra password
  │    passwordEncoder.matches(rawPassword, user.getPasswordHash())
  │    Nếu sai → throw AppException("Invalid credentials")
  │
  │  BƯỚC 3: Kiểm tra email đã verified chưa
  │    Nếu chưa → throw AppException("Email not verified")
  │
  │  BƯỚC 4: Tạo JWT token
  │    String accessToken = jwtTokenProvider.generateToken(user)
  │    String refreshToken = jwtTokenProvider.generateRefreshToken(user)
  │    → Token chứa: userId, email, role (dưới dạng claims)
  │
  │  BƯỚC 5: Lưu session
  │    UserSession session = new UserSession(...)
  │    userSessionRepository.save(session)
  │    → INSERT INTO USER_SESSION (...)
  │
  │  BƯỚC 6: Cập nhật LastLoginAt
  │    user.setLastLoginAt(now)
  │    userRepository.save(user)
  │
  │  BƯỚC 7: Trả về AuthResponse
  │    return AuthResponse(accessToken, refreshToken, role, email, fullName)
  ▼
Trả về JSON → FE lưu token vào localStorage (Zustand persist)
```

**Các endpoint Auth:**

| Endpoint | Method | Controller method | Chức năng |
|----------|--------|-------------------|-----------|
| `/api/v1/auth/register` | POST | `AuthController.register()` | Đăng ký tài khoản mới |
| `/api/v1/auth/login` | POST | `AuthController.login()` | Đăng nhập |
| `/api/v1/auth/refresh-token` | POST | `AuthController.refreshToken()` | Làm mới access token |
| `/api/v1/auth/verify-email` | GET | `AuthController.verifyEmail()` | Xác thực email (có ?token=) |
| `/api/v1/auth/forgot-password` | POST | `AuthController.forgotPassword()` | Gửi email reset password |
| `/api/v1/auth/reset-password` | POST | `AuthController.resetPassword()` | Đặt lại password |
| `/api/v1/auth/google` | POST | `AuthController.googleLogin()` | Đăng nhập bằng Google |

**JWT Filter chain (mỗi request đều đi qua):**

```
Request → JwtAuthenticationFilter.java
  │  Lấy token từ header "Authorization: Bearer <token>"
  │  Giải mã token → lấy userId, email, role
  │  Set Authentication vào SecurityContext
  │  → Controller có thể dùng @AuthenticationPrincipal để lấy user
  ▼
SecurityConfig.java
  │  Cấu hình: public paths (login, register, swagger) vs protected paths
  │  Cấu hình: role-based access (@PreAuthorize)
  │  CORS, CSRF disabled (REST API)
```

**Files involved:**

| Layer | File | Vai trò |
|-------|------|---------|
| **Controller** | `controller/AuthController.java` | Login, register, verify email, reset password |
| **Service** | `service/impl/AuthServiceImpl.java` | Business logic xác thực |
| **Security** | `security/JwtTokenProvider.java` | Tạo & validate JWT token |
| **Security** | `security/JwtAuthenticationFilter.java` | Intercept request, extract token |
| **Security** | `security/SecurityConfig.java` | Cấu hình Spring Security |
| **Security** | `security/CustomUserDetailsService.java` | Load user từ DB cho Spring Security |
| **Repository** | `repository/jpa/UserRepository.java` | `findByEmail(String)`, `existsByEmail(String)` |
| **Repository** | `repository/jpa/UserSessionRepository.java` | Lưu session |
| **Repository** | `repository/jpa/VerificationTokenRepository.java` | Lưu email verification token |
| **Entity** | `entity/jpa/User.java` | Ánh xạ bảng USER |
| **Entity** | `entity/jpa/Role.java` | Ánh xạ bảng ROLE |
| **DTO** | `dto/auth/LoginRequest.java` | `{ email, password }` |
| **DTO** | `dto/auth/RegisterRequest.java` | `{ email, password, fullName, institution }` |
| **DTO** | `dto/auth/AuthResponse.java` | `{ accessToken, refreshToken, role, ... }` |

---

### LUỒNG 3: DATA SYNC — "Khi admin bấm Sync"

```
FE (SyncDataPage.jsx) hoặc Scheduled Cron
  │  Admin: POST /api/v1/admin/sync/trigger
  │  Cron: @Scheduled(cron = "0 0 2 * * *") — 2AM mỗi ngày
  ▼
AdminSyncController.java / ScheduledDataSyncService.java
  │  Gọi: dataSyncService.syncFromOpenAlex(...)
  ▼
DataSyncServiceImpl.java  ← ⚡ ĐÂY LÀ NÃO CỦA SYNC
  │
  │  BƯỚC 1: Tạo SyncLog (status = RUNNING)
  │    SyncLog log = syncLogRepository.save(new SyncLog(...))
  │
  │  BƯỚC 2: Gọi OpenAlex API
  │    GET api.openalex.org/works?search=<keyword>&per_page=200
  │    → Parse JSON → OpenAlexResponseDTO
  │
  │  BƯỚC 3: Quality Score Filter
  │    qualityScoreFilter.calculateScore(paper) >= 40
  │    → Lọc paper chất lượng thấp (citation thấp, journal kém...)
  │
  │  BƯỚC 4: DOI Deduplication
  │    Kiểm tra DOI đã tồn tại trong DB chưa
  │    → Nếu có rồi thì skip
  │
  │  BƯỚC 5: Lưu vào SQL Server (JPA)
  │    researchPaperRepository.save(paper)         → RESEARCH_PAPER
  │    authorRepository.save(author)               → AUTHOR
  │    journalRepository.save(journal)             → JOURNAL
  │    keywordRepository.save(keyword)             → KEYWORD
  │    paperAuthorRepository.save(paperAuthor)     → PAPER_AUTHOR
  │    paperKeywordRepository.save(paperKeyword)   → PAPER_KEYWORD
  │
  │  BƯỚC 6: Lưu vào Neo4j (Graph)
  │    graphService.savePaperWithKeywords(paper, keywords)
  │    → MERGE (p:Paper {paperId: ...})
  │    → MERGE (k:Keyword {name: ...})
  │    → MERGE (p)-[:HAS_KEYWORD]->(k)
  │
  │  BƯỚC 7: Enrichment (AI extract)
  │    paperEnrichmentService.enrichPaper(paper)
  │    → AI extract: datasets, methods, metrics từ abstract
  │    → Lưu thêm vào Neo4j: (p)-[:USES_DATASET]->(d), etc.
  │
  │  BƯỚC 8: Cập nhật SyncLog (status = COMPLETED)
  │    log.setStatus("COMPLETED")
  │    log.setPapersFetched(...)
  │    syncLogRepository.save(log)
  │
  │  BƯỚC 9: Gửi notification cho admin
  │    adminNotificationService.broadcastToAdmins(SYNC_COMPLETED, ...)
  │
  ▼
BulkSyncProgressTracker (in-memory)
  │  Theo dõi tiến độ sync (để FE poll)
  │  GET /api/v1/admin/sync/progress → { current: 150, total: 200, percent: 75 }
```

**Files involved:**

| Layer | File | Vai trò |
|-------|------|---------|
| **Controller** | `controller/AdminSyncController.java` | Trigger sync (admin) |
| **Controller** | `controller/DataSyncController.java` | Check sync status |
| **Service** | `service/impl/DataSyncServiceImpl.java` | Logic sync chính |
| **Service** | `service/ScheduledDataSyncService.java` | Cron job 2AM |
| **Service** | `service/GraphService.java` | Lưu paper+keyword vào Neo4j |
| **Service** | `service/PaperEnrichmentService.java` | AI extract metadata |
| **Service** | `service/QualityScoreFilter.java` | Lọc paper chất lượng |
| **Service** | `service/BulkSyncProgressTracker.java` | Track tiến độ |
| **Service** | `service/impl/AdminNotificationServiceImpl.java` | Gửi notification |
| **Repository** | `repository/jpa/ResearchPaperRepository.java` | CRUD paper |
| **Repository** | `repository/jpa/SyncLogRepository.java` | CRUD sync log |
| **Entity** | `entity/jpa/SyncLog.java` | Ánh xạ bảng SYNC_LOG |
| **DTO** | `dto/sync/BulkSyncProgress.java` | `{ current, total, percent }` |
| **DTO** | `dto/sync/OpenAlexResponseDTO.java` | Parse JSON từ OpenAlex |

---

### 🔑 CÁC CONTROLLER CHÍNH — TRA CỨU NHANH

| Controller | Base URL | Chức năng |
|-----------|----------|-----------|
| `PaperSearchController` | `/api/v1/papers` | **Search papers** (quan trọng nhất) |
| `AuthController` | `/api/v1/auth` | Login, register, verify email |
| `UserController` | `/api/v1/users` | Profile, update, change password |
| `BookmarkController` | `/api/v1/bookmarks` | CRUD bookmarks |
| `FollowController` | `/api/v1/follows` | Follow journals/authors/keywords |
| `NotificationController` | `/api/v1/notifications` | List, mark read, delete |
| `NotificationSseController` | `/api/v1/notifications/sse` | Real-time SSE stream |
| `IdeaController` | `/api/v1/ideas` | Research idea analysis |
| `GapExplorerController` | `/api/v1/gap-explorer` | Research gap exploration |
| `GraphController` | `/api/v1/graph` | Neo4j graph data |
| `AnalyticsController` | `/api/v1/analytics` | Charts, trends, stats |
| `ReportController` | `/api/v1/reports` | Generate reports (PDF) |
| `AdminSyncController` | `/api/v1/admin/sync` | Trigger data sync |
| `AdminOverviewController` | `/api/v1/admin/overview` | Admin dashboard stats |
| `AdminUserController` | `/api/v1/admin/users` | User management |
| `DashboardController` | `/api/v1/dashboard` | User dashboard widgets |

---

### 🔑 CÁC SERVICE LÕI — TRA CỨU NHANH

| Service | File | Trách nhiệm |
|---------|------|-------------|
| **PaperSearchOrchestrator** | `service/PaperSearchOrchestrator.java` | Điều phối search: Neo4j → SQL → OpenAlex |
| **AuthService** | `service/impl/AuthServiceImpl.java` | Login, register, JWT |
| **DataSyncService** | `service/impl/DataSyncServiceImpl.java` | Crawl OpenAlex + Semantic Scholar |
| **GraphService** | `service/GraphService.java` | Neo4j Cypher queries |
| **NotificationService** | `service/impl/NotificationServiceImpl.java` | CRUD notification |
| **BookmarkService** | `service/impl/BookmarkServiceImpl.java` | CRUD bookmark |
| **IdeaAnalysisService** | `service/IdeaAnalysisService.java` | AI phân tích ý tưởng |
| **PaperSearchService** | `service/impl/PaperSearchServiceImpl.java` | Check usage, query SQL |
| **UserService** | `service/impl/UserServiceImpl.java` | Profile, role upgrade |

---

## PHẦN 7: CÂU HỎI GIẢNG VIÊN THƯỜNG HỎI VỀ CODE

### Q1: "Khi tôi search 'deep learning', dữ liệu lấy từ đâu?"

**Trả lời:**
1. Đầu tiên, `PaperSearchOrchestrator` gọi `GraphService.searchPapersByKeywords("deep learning")` để query Neo4j — tìm tất cả Paper nodes có quan hệ `HAS_KEYWORD` với Keyword "deep learning"
2. Nếu Neo4j trả về danh sách PaperID → gọi `ResearchPaperRepository.findByPaperIdIn(ids)` để lấy full data từ SQL Server
3. Nếu Neo4j không có → gọi OpenAlex API (`api.openalex.org/works?search=deep+learning`) để lấy dữ liệu mới, sau đó lưu vào cả SQL Server và Neo4j

### Q2: "JWT token được tạo và xác thực như thế nào?"

**Trả lời:**
- **Tạo:** `JwtTokenProvider.generateToken(User)` — dùng thư viện `io.jsonwebtoken`, nhúng userId, email, role vào claims, ký bằng secret key, set hạn 24h
- **Xác thực:** `JwtAuthenticationFilter` — mỗi request, lấy token từ header `Authorization: Bearer <token>`, giải mã, lấy thông tin user, set vào `SecurityContext` để Spring Security biết user là ai

### Q3: "Làm sao để giới hạn lượt search của Academic User?"

**Trả lời:**
- `PaperSearchServiceImpl.checkAndIncrementUsage(userEmail)` — kiểm tra bảng `USER_USAGE`, đếm `SearchCount` trong tháng hiện tại
- Nếu vượt quá limit → throw `UsageLimitExceededException`
- Nếu chưa vượt → tăng `SearchCount` lên 1

### Q4: "Neo4j và SQL Server được sync như thế nào?"

**Trả lời:**
- Khi crawl dữ liệu từ OpenAlex, `DataSyncServiceImpl` lưu paper vào SQL Server trước (qua JPA)
- Sau đó gọi `GraphService.savePaperWithKeywords()` để lưu paper + keywords vào Neo4j
- 2 thao tác này trong cùng 1 transaction flow — nếu 1 cái fail thì cả 2 rollback

### Q5: "Controller nào xử lý search? Nó gọi những service nào?"

**Trả lời:**
- `PaperSearchController.java` (`GET /api/v1/papers/search`)
- Gọi `PaperSearchOrchestrator.searchPapers()` → orchestrator gọi:
  - `SearchKeywordService.recordSearch()` — ghi log
  - `PaperSearchService.checkAndIncrementUsage()` — check limit
  - `GraphService.searchPapersByKeywords()` — query Neo4j
  - `PaperSearchService.findPapersByIds()` — query SQL Server (nếu cache hit)
  - `OpenAlexFallbackSearchService.searchAndSync()` — gọi API (nếu cache miss)

---

## PHẦN 8: LỘ TRÌNH ÔN TẬP GẤP (3-5 NGÀY)

### Ngày 1: Database (ĐỌC PHẦN 1-5 của file này)
- [ ] Học thuộc 39 bảng, chia 6 nhóm
- [ ] Vẽ lại sơ đồ quan hệ trên giấy (chỉ cần Core Domain)
- [ ] Trả lời được 8 câu hỏi DB ở Phần 4
- [ ] Tự trả lời: "Bảng X có những khóa gì? FK đến đâu?"

### Ngày 2: 3 Luồng Code Chính (ĐỌC PHẦN 6)
- [ ] Search flow: vẽ lại flow trên giấy, nhớ tên các file
- [ ] Auth flow: vẽ lại flow, nhớ JWT filter chain
- [ ] Data Sync flow: vẽ flow, nhớ các bước

### Ngày 3: Code Walkthrough
- [ ] Mở BE code, trace 1 request từ Controller → Response
- [ ] Đọc `PaperSearchOrchestrator.java` — đây là file quan trọng nhất
- [ ] Đọc `AuthServiceImpl.java` — hiểu flow login/register
- [ ] Đọc `DataSyncServiceImpl.java` — hiểu flow crawl

### Ngày 4: Frontend + Integration
- [ ] Mở FE code, xem `SearchPapers.jsx` gọi API nào
- [ ] Hiểu axiosClient interceptor (gắn JWT token)
- [ ] Hiểu Zustand store (auth state)
- [ ] Test: chạy cả BE + FE, tự search 1 keyword

### Ngày 5: Mock Q&A
- [ ] Tự trả lời 10 câu hỏi trong file này
- [ ] Nhờ bạn hỏi bất kỳ câu nào về DB hoặc code
- [ ] Tập vẽ lại kiến trúc hệ thống lên bảng

---

> 💡 **Mẹo:** Khi bị hỏi "số liệu này từ đâu ra", luôn trả lời theo pattern:
> **"Controller X → Service Y → Repository Z → Bảng ABC"**
> VD: "Số liệu search papers đến từ `PaperSearchController` → `PaperSearchOrchestrator` → `GraphService` (Neo4j) hoặc `ResearchPaperRepository` (SQL Server) → bảng `RESEARCH_PAPER`"