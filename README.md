# SCITRACK — Backend

> **AI-Powered Academic Research Analytics**
>
> Backend cho hệ thống theo dõi xu hướng nghiên cứu khoa học — tìm kiếm, phân tích và trực quan hóa bài báo học thuật với đồ thị từ khóa.

---

## 🧱 Công nghệ

| Lĩnh vực | Công nghệ |
|----------|-----------|
| Ngôn ngữ | Java 21 |
| Framework | Spring Boot 3.5.14 |
| Build Tool | Maven |
| Database chính | SQL Server 2022 (JPA / Hibernate) |
| Graph Database | Neo4j (AuraDB — cloud-hosted) |
| Auth | Spring Security + JWT (stateless) |
| API Docs | OpenAPI 3 (Swagger UI) |
| Real-time Sync | OpenAlex API + Semantic Scholar API |
| AI | Google Gemini (tùy chọn) |

---

## ⚙️ Yêu cầu môi trường

| Công cụ | Phiên bản | Bắt buộc |
|---------|-----------|:--------:|
| JDK | 21+ | ✅ |
| Maven | 3.x | ✅ |

---

## 🚀 Cài đặt & Chạy

### 1. Clone & JDK

```bash
git clone <repository-url>
cd Journal-Trend-Tracking-BE
```

Cấu hình JDK 21:
- Tạo biến môi trường `JAVA_HOME` trỏ đến thư mục cài JDK 21
- Thêm `%JAVA_HOME%\bin` vào `Path`
- Kiểm tra: `java -version` → hiện `21.x`

**Trong IntelliJ IDEA**: File → Project Structure (Ctrl+Alt+Shift+S) → SDK & Language level = 21. Settings → Maven → Runner → JRE = 21.

### 2. Cấu hình môi trường

Database (SQL Server) và Neo4j đã được host trên cloud — **không cần Docker**.

Tạo file `application-local.properties` trong `src/main/resources/`:

```properties
spring.datasource.url=jdbc:sqlserver://<cloud-host>:1434;databaseName=JournalTrendDB;encrypt=true;trustServerCertificate=false
spring.datasource.username=<your_db_user>
spring.datasource.password=<your_db_password>
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

> Lấy thông tin kết nối từ file `.env` tại gốc project hoặc hỏi team lead.

File `.env` tại gốc project chứa các biến nhạy cảm (datasource, Neo4j, JWT, SMTP, API keys,...) và được Spring Boot load qua `spring.config.import=optional:file:.env[.properties]`.

> ⚠️ Cả 2 file này đều nằm trong `.gitignore` — không push lên GitHub.

### 3. Chạy

```bash
mvn spring-boot:run
```

Backend chạy tại: **http://localhost:8080**
Swagger UI: **http://localhost:8080/swagger-ui/index.html**

---

## 📦 Cấu trúc dự án

```
src/main/java/com/sra/journal_tracking/
├── config/                # Security, CORS, JPA config, Neo4j config
├── controller/            # REST API endpoints (18 controllers)
├── service/               # Interface business logic
│   └── impl/              # Implementations
├── repository/
│   ├── jpa/               # Spring Data JPA repositories (SQL Server)
│   └── neo4j/             # Spring Data Neo4j repositories (graph)
├── entity/
│   ├── jpa/               # JPA entities → SQL Server tables
│   └── neo4j/             # Neo4j entities → graph nodes/edges
├── dto/
│   ├── request/           # Request DTOs
│   ├── response/          # Response DTOs
│   └── follow/            # Follow feature DTOs
├── exception/             # AppException, ErrorCode, GlobalExceptionHandler
├── utils/                 # JWT, helpers, constants
└── enums/                 # Role, TokenType, SyncStatus,...
```

---

## 🗄️ Kiến trúc Dual Database

Hệ thống sử dụng **2 database riêng biệt** với package isolation:

| | SQL Server (JPA) | Neo4j (Graph) |
|---|---|---|
| **Vai trò** | Lưu trữ chính: users, papers, journals, authors, keywords, bookmarks, follows, sync logs | Lưu trữ đồ thị: Paper nodes, Keyword nodes, `HAS_KEYWORD` relationships |
| **Config** | `JpaConfig.java` | `Neo4jConfig.java` |
| **Entity Package** | `entity.jpa.*` | `entity.neo4j.*` |
| **Repository Package** | `repository.jpa.*` | `repository.neo4j.*` |

Hai database hoạt động độc lập — khi sync dữ liệu từ OpenAlex, paper được ghi đồng thời vào cả hai. Package isolation ngăn Spring Data scanning conflict.

---

## 🔍 Luồng Tìm kiếm (Search Pipeline)

```
User nhập keyword → PaperSearchOrchestrator
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
    Neo4j Graph     SQL Server      OpenAlex API
  (keyword→paper   (full paper      (live fallback
   ID lookup)       data by ID)      + background sync)
```

1. **Neo4j cache hit** — Query `(p:Paper)-[:HAS_KEYWORD]->(k:Keyword)` → lấy paper IDs → fetch full data từ SQL Server → trả kết quả.
2. **Neo4j miss** — Fallback sang OpenAlex API, trigger background sync vào SQL Server + Neo4j.
3. **Stale IDs** — Neo4j có ID nhưng SQL không có → xóa Neo4j node, fallback sang OpenAlex.

Mọi search đều được ghi nhận qua `SearchKeywordService` để tracking hot-keyword.

---

## 🔐 Auth & Phân quyền

### Luồng xác thực

```
FE Login → AuthController.login() → AuthService → JwtTokenProvider tạo JWT
       → Response: { accessToken, role }
       → FE lưu token (localStorage) + role (sessionStorage)
       → Mọi request sau đó: axiosClient gắn Authorization: Bearer <token>
       → BE: JwtAuthenticationFilter trích xuất & validate → set SecurityContext
```

- **Stateless JWT** — không server-side session (trừ `UserSession` table để track logout)
- **BCrypt** mã hóa password
- **CORS** mở `*` cho dev (cần thắt chặt trước production)

### Public endpoints (không cần auth)

- `/api/auth/**` — đăng ký, đăng nhập, xác thực email
- `/api/public/**`
- `/swagger-ui/**`, `/v3/api-docs/**`

### Hệ thống Role

| Role | Storage | Quyền hạn |
|------|---------|-----------|
| `ADMIN` | `sessionStorage` → `admin` | Quản lý user, trigger data sync, dashboard stats, audit log |
| `RESEARCHER` | `sessionStorage` → `researcher` | Không giới hạn search, advanced filter, analytics, bookmarks, follows |
| `ACADEMIC_USER` | `sessionStorage` → `academic_user` | Bị giới hạn search/view hàng tháng, bookmarks, follows (max 20) |

ACADEMIC_USER có thể tự nâng cấp lên RESEARCHER qua `POST /api/users/me/upgrade`.

Usage limits được lưu trong bảng `UserUsage`, cấu hình qua bảng `SystemConfig`.

---

## 📡 API Endpoints

### Auth
| Method | Endpoint | Auth | Mô tả |
|--------|----------|:----:|-------|
| `POST` | `/api/auth/register` | ❌ | Đăng ký + auto-login |
| `POST` | `/api/auth/login` | ❌ | Đăng nhập → JWT |
| `POST` | `/api/auth/logout` | ❌ | Đăng xuất (vô hiệu token) |

### User (Profile)
| Method | Endpoint | Auth | Mô tả |
|--------|----------|:----:|-------|
| `GET` | `/api/users/me` | ✅ | Thông tin cá nhân |
| `PUT` | `/api/users/me` | ✅ | Cập nhật profile |
| `PUT` | `/api/users/me/password` | ✅ | Đổi mật khẩu |
| `POST` | `/api/users/me/upgrade` | ✅ | Nâng cấp lên RESEARCHER |

### Papers & Search
| Method | Endpoint | Auth | Mô tả |
|--------|----------|:----:|-------|
| `GET` | `/api/v1/papers` | ✅ | Danh sách papers (cơ bản) |
| `GET` | `/api/v1/papers/search` | ✅ | Tìm kiếm toàn văn (qua `PaperSearchOrchestrator`) |
| `GET` | `/api/v1/papers/search/author` | ✅ | Tìm theo tác giả |
| `GET` | `/api/v1/papers/search/journal` | ✅ | Tìm theo journal |
| `GET` | `/api/v1/papers/filter/advanced` | ✅ | Advanced filter (RESEARCHER+) |
| `GET` | `/api/v1/papers/{id}` | ✅ | Chi tiết paper |

### Follows
| Method | Endpoint | Auth | Mô tả |
|--------|----------|:----:|-------|
| `POST` | `/api/v1/follows` | ✅ | Follow journal/topic/keyword |
| `GET` | `/api/v1/follows` | ✅ | Danh sách đang follow |
| `PUT` | `/api/v1/follows/{id}?notifyEnabled=` | ✅ | Bật/tắt thông báo |
| `DELETE` | `/api/v1/follows/{id}` | ✅ | Unfollow |

> Chi tiết: [docs/follow-api.md](docs/follow-api.md)

### Bookmarks
| Method | Endpoint | Auth | Mô tả |
|--------|----------|:----:|-------|
| `POST` | `/api/v1/bookmarks` | ✅ | Lưu bookmark |
| `GET` | `/api/v1/bookmarks` | ✅ | Danh sách bookmark |
| `DELETE` | `/api/v1/bookmarks/{id}` | ✅ | Xóa bookmark |

### Graph (Neo4j)
| Method | Endpoint | Auth | Mô tả |
|--------|----------|:----:|-------|
| `GET` | `/api/graphs/paper/{id}` | ❌ | Đồ thị từ khóa của paper |

### Admin
| Method | Endpoint | Auth | Mô tả |
|--------|----------|:----:|-------|
| `GET` | `/api/users` | ADMIN | Danh sách user |
| `GET` | `/api/users/{id}` | ADMIN | Chi tiết user |
| `PUT` | `/api/users/{id}/status` | ADMIN | Enable/disable user |
| `PUT` | `/api/users/{id}/role` | ADMIN | Đổi role |
| `POST` | `/api/v1/admin/sync/openalex` | ADMIN | Trigger manual sync |
| `GET` | `/api/v1/dashboard/**` | ADMIN | Dashboard stats |
| `GET` | `/api/admin/**` | ADMIN | Data sources, audit logs, config |

---

## 🔄 Data Sync

`DataSyncServiceImpl` đồng bộ bài báo từ 2 nguồn:

| Nguồn | API | Cơ chế |
|-------|-----|--------|
| **OpenAlex** | `api.openalex.org` | Pagination, abstract từ inverted index, "polite pool" email |
| **Semantic Scholar** | `api.semanticscholar.org` | Bổ sung, deduplicate theo DOI |

- **Scheduled**: Tự động chạy hàng ngày lúc 2:00 AM (`@Scheduled`)
- **Manual**: Admin trigger qua `POST /api/v1/admin/sync/openalex`
- **Deduplication**: Theo DOI giữa 2 nguồn
- **Tracking**: `BulkSyncProgressTracker` (in-memory) + `SyncLog` table

---

## 🧪 Build & Test

```bash
# Build JAR
mvn clean package

# Chạy toàn bộ test
mvn test

# Chạy 1 test class
mvn test -Dtest=JournalTrackingApplicationTests

# Chạy 1 test method
mvn test -Dtest=JournalTrackingApplicationTests#methodName
```

---

## 🌿 Git Workflow

- **Main branch**: `main` (production)
- **Dev branch**: `develop` (default)
- **Feature branch**: `feature/<tên-chức-năng>`
- **Fix branch**: `fix/<mô-tả>`

### Commit convention

```
feat: mô tả tính năng mới
fix: mô tả bug đã sửa
refactor: mô tả refactor
docs: mô tả cập nhật tài liệu
style: mô tả format code
```

### Quy tắc

- ❌ Không push trực tiếp lên `main`
- ❌ Không push `application-local.properties` hay `.env`
- ❌ Không sửa branch của người khác
- ✅ Luôn tạo Pull Request để merge
- ✅ Luôn pull `develop` mới nhất trước khi code

```bash
# Tạo branch mới
git checkout develop
git pull
git checkout -b feature/ten-chuc-nang
```

---

## 📚 Tài liệu liên quan

- [CLAUDE.md](CLAUDE.md) — Hướng dẫn chi tiết cho AI assistant
- [docs/follow-api.md](docs/follow-api.md) — API docs cho tính năng Follow
- Swagger UI: http://localhost:8080/swagger-ui/index.html

---

## 👥 Contributors

Backend Team — SCITRACK
