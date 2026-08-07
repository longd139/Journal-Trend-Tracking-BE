# SCITRACK — Hệ Thống Phân Tích Học Thuật Thông Minh

> Tài liệu tổng hợp hệ thống — dùng làm slide thuyết trình
> Cập nhật: 2026-07-19

---

## 1. TỔNG QUAN DỰ ÁN

### SCITRACK là gì?

**AI-Powered Academic Research Analytics** — Nền tảng tìm kiếm, phân tích và khám phá xu hướng nghiên cứu học thuật với trực quan hóa đồ thị tri thức.

### Bài toán

- Nhà nghiên cứu mất nhiều thời gian tìm paper phù hợp
- Khó phát hiện **research gap** (khoảng trống nghiên cứu)
- Các công cụ hiện tại (Google Scholar, Scopus) chỉ tìm kiếm — không phân tích gap

### Giải pháp

SCITRACK biến dữ liệu học thuật thành **Knowledge Graph**, cho phép:
- 🔍 **Unified Search** — tìm papers/authors/journals trong 1 thanh search
- 🧠 **Research Gap Explorer** — khám phá khoảng trống nghiên cứu qua Neo4j graph
- 🤖 **MCP Chatbot AI** — trợ lý nghiên cứu gợi ý hướng nghiên cứu mới
- 📊 **Analytics** — thống kê xu hướng, top authors, journals

---

## 2. KIẾN TRÚC HỆ THỐNG

```
┌─────────────────────────────────────────────────────────┐
│                     FRONTEND (React 18)                  │
│  Vite 6 · Tailwind v4 · vis-network · Recharts · Framer│
│  Zustand (state) · react-i18next · shadcn/ui (Radix)   │
└──────────────────────┬──────────────────────────────────┘
                       │ REST API (Axios + JWT)
┌──────────────────────▼──────────────────────────────────┐
│                  BACKEND (Spring Boot 3.5)               │
│  Java 21 · Maven · Spring Security (JWT) · Swagger     │
│                                                         │
│  ┌──────────┐  ┌───────────┐  ┌────────────────────┐   │
│  │ SQL Server│  │ Neo4j     │  │ External APIs      │   │
│  │ (JPA)    │  │ (Graph)   │  │ - OpenAlex         │   │
│  │          │  │           │  │ - Semantic Scholar  │   │
│  │ Users    │  │ Keywords  │  │ - DeepSeek AI      │   │
│  │ Papers   │  │ Papers    │  │ - Gemini AI        │   │
│  │ Authors  │  │ Datasets  │  └────────────────────┘   │
│  │ Journals │  │ Methods   │                            │
│  │ Bookmarks│  │ Metrics   │                            │
│  └──────────┘  └───────────┘                            │
└─────────────────────────────────────────────────────────┘
```

### Dual-Database Strategy

| | SQL Server (JPA) | Neo4j (Graph) |
|---|---|---|
| **Lưu trữ** | Users, papers, authors, journals, bookmarks | Keywords, papers, datasets, methods, metrics |
| **Dùng cho** | Auth, CRUD, full-text data | Graph search, keyword network, gap analysis |
| **Đồng bộ** | Viết khi crawl | Viết đồng thời khi crawl |

---

## 3. TÍNH NĂNG CHÍNH

### 3.1 Unified Search

```
┌───────────────────────────────────────────────┐
│  🔍 [deep learning                    ] [Search]│
│                                               │
│  [📄 Papers (42)] [👤 Authors (5)] [📚 Journals]│
│  ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔                              │
│                                               │
│  Quick Stats: 42 papers · 890 citations       │
│  Keywords Graph · Top Cited Papers            │
│  Advanced Filters · Sort                      │
└───────────────────────────────────────────────┘
```

- **1 thanh search** cho cả keyword, author, journal
- **Tabbed results** — giữ nguyên UI thống kê cũ
- **Autocomplete** + search history
- **Role-based**: Academic (giới hạn) vs Researcher (không giới hạn)

### 3.2 Research Gap Explorer (3 Stages)

```
Stage 1: "Bản đồ tri thức"
┌──────────────────────────────────────────┐
│  Neo4j Graph — Hierarchy View            │
│                                          │
│     ■ 2024      ■ 2025      ■ 2026       │
│      │           │           │           │
│   ⬡ CS        ⬡ AI       ⬡ IoT         │
│      │           │           │           │
│  ▲ DL  ▲ NLP  ▲ CV   ◆ Keywords         │
│                                          │
│  Click node → highlight neighbors        │
└──────────────────────────────────────────┘
         ↓ User mô tả ý tưởng

Stage 2: "Thu hẹp theo ý tưởng"
┌─────────────────┬────────────────────┐
│  Neo4j Graph    │  MCP Chatbot AI    │
│  (đã lọc)      │                    │
│                 │  "ứng dụng AI     │
│  ◆ Wearable    │   trong healthcare"│
│  ◆ Edge AI     │                    │
│  ◆ Federated   │  🤖 Đề xuất 3 cặp:│
│                 │  • Wearable + FL  │
│  Click → chọn  │  • Health + Edge  │
│                 │  • PPG + AI      │
└─────────────────┴────────────────────┘
         ↓ Chọn 1 cặp keyword

Stage 3: "Focus Gap"
┌─────────────────┬────────────────────┐
│  Neo4j Graph    │  Gap Analysis      │
│  ◆ Wearable    │                    │
│  │  ├ ● P1     │  📊 Overlap: 8     │
│  │  ├ ● P2     │  📦 Dataset Gap    │
│  ◆ FL           │  📐 Metric Gap     │
│  │  ├ ● P4     │  🔧 Method Gap     │
│  │  └ ● P5     │                    │
│  🔵 A  🟠 B  ⚪│  💡 AI Roadmap     │
└─────────────────┴────────────────────┘
```

**Tương tác Graph:**
- Click 1 node → highlight node + neighbors (opacity 100%), còn lại mờ (20%)
- Click vùng trống → reset
- Double-click → zoom fit

### 3.3 Multi-Layer Keyword Fallback

Khi user nhập ý tưởng nhưng keyword không có trong hệ thống:

| Layer | Cơ chế | Ví dụ |
|-------|--------|-------|
| 1. Exact + Synonym | Mở rộng từ + kiểm tra Neo4j | "deep learning" ✅ |
| 2. Fuzzy Match | Levenshtein + prefix similarity | "federated lerning" → "Federated learning" (87%) |
| 3. Partial Match | Dùng keyword có sẵn + cảnh báo | "blockchain" không có, hiển thị "distributed systems" |
| 4. Guided Discovery | Hiển thị toàn bộ knowledge map | Danh sách fields, topics, keywords |
| 5. On-demand Crawl | User bấm "Crawl" → fetch OpenAlex live | Progress bar real-time 5 stages |

### 3.4 MCP Chatbot AI

```
┌──────────────────────────────────────┐
│  Research Advisor                    │
│                                      │
│  [___________________________]       │
│  Mô tả ý tưởng nghiên cứu...         │
│  [Explore Gaps]                      │
│                                      │
│  🕐 Recent Ideas                     │
│  ├─ deep learning for CV            │
│  ├─ AI in wearable devices          │
│  └─ federated learning privacy      │
└──────────────────────────────────────┘
```

- DeepSeek AI function calling → tự động gọi Neo4j tools
- Loop ≤ 5 iterations: gọi tool → nhận kết quả → gọi tool tiếp → trả lời
- 4 tools: `list_keywords`, `get_cooccurring`, `get_gap_score`, `match_user_idea`
- History lưu trong SQL Server (`USER_SEARCH_HISTORY`, searchType = "GAP_IDEA")

---

## 4. DATA PIPELINE

### Crawl Flow

```
OpenAlex API
    │
    ▼
Quality Score Filter (≥ 40/100)
    │  • Citation count (0-30đ)
    │  • Journal quartile (0-25đ)
    │  • Article type (10-15đ)
    │  • Open Access (15đ)
    │  • Abstract length (15đ)
    │  • DOI (5đ)
    │
    ├── Lưu SQL Server (JPA)
    │
    └── Enrichment 2-tier:
         ├── Abstract → LUÔN extract (100% coverage)
         │   AI extract: metrics, datasets, methods
         └── Full-text PDF (nếu Open Access)
              AI extract bổ sung
              │
              ▼
         Lưu Neo4j Graph:
         • MERGE Paper node
         • MERGE Keyword nodes + HAS_KEYWORD
         • MERGE Dataset/Metric/Method + USES_*
         • Link Year (PUBLISHED_IN), Field (BELONGS_TO_FIELD)
```

### Data Flow: Search

```
User → FE axiosClient → PaperSearchOrchestrator
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
        OpenAlex API    SQL Server      Neo4j Graph
       (primary)     (paper data)    (keyword network)
                            │
            ▼               ▼               ▼
            Kết quả → 6h cache → trả về FE
```

---

## 5. USER ROLES

| Role | Quyền |
|------|-------|
| **ADMIN** | User CRUD, sync triggers, DB stats, API monitoring |
| **RESEARCHER** | Unlimited search + advanced filter + analytics + gap explorer + bookmarks |
| **ACADEMIC_USER** | Monthly-limited search, bookmarks, reports (no analytics/gap explorer) |

### Auth Flow

```
Login → JWT token → Zustand persist (localStorage)
     → axiosClient interceptor → Authorization: Bearer <token>
     → BE JwtAuthenticationFilter → SecurityContext
```

---

## 6. TECH STACK

| Layer | Technology |
|-------|-----------|
| **Frontend** | React 18, Vite 6, Tailwind CSS v4, vis-network, Recharts, Framer Motion |
| **State** | Zustand + react-i18next (en/vi) |
| **UI** | shadcn/ui (50+ Radix primitives) + custom dark theme |
| **Backend** | Spring Boot 3.5.14, Java 21, Maven |
| **Auth** | Spring Security + JWT (stateless) |
| **Database** | SQL Server (JPA) + Neo4j 5.26 (Docker, Bolt) |
| **External APIs** | OpenAlex, Semantic Scholar, DeepSeek AI, Gemini AI |
| **DevOps** | Docker Compose (SQL Server + Neo4j) |

---

## 7. ƯU ĐIỂM NỔI BẬT

| Tính năng | Khác biệt |
|-----------|----------|
| **Research Gap Discovery** | Phát hiện khoảng trống nghiên cứu qua graph analysis — không công cụ nào có |
| **Knowledge Graph** | Neo4j — keyword network, co-occurrence, cross-dimension gap |
| **AI-Powered** | DeepSeek chatbot tự động gợi ý hướng nghiên cứu |
| **Unified Search** | 1 thanh search cho papers + authors + journals |
| **On-demand Crawl** | User tự crawl keyword mới, không cần đợi admin |
| **Multi-language** | i18n en + vi |
| **Dark Theme** | Custom design system, responsive |

---

## 8. SỐ LIỆU HIỆN TẠI

| Metric | Giá trị |
|--------|---------|
| Keywords trong Neo4j | ~1,400 |
| Papers đã crawl | ~200+ |
| Năm dữ liệu | 2024, 2025, 2026 |
| Lĩnh vực | Computer Science, AI |
| Node types | 9 loại (Year, Field, Topic, Keyword, Paper, Dataset, Metric, Method, Author) |
| Relationship types | 10 loại |
| AI Model | DeepSeek (function calling) |
