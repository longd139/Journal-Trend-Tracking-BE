# 🔬 Research Idea AI — Luồng nghiệp vụ

> **Base path:** `/api/v1/ideas`
> **Auth:** Bearer Authentication
> **AI Engine:** DeepSeek API (OpenAI-compatible)
> **Paper Source:** OpenAlex API
> **PDF Extraction:** Apache PDFBox (in-memory)

---

## 🎯 5 Endpoints

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| `POST` | `/extract-keywords` | AI trích xuất + gợi ý keywords từ idea text |
| `POST` | `/analyze` | Pipeline 4 bước: search → evaluate → gap → lit review |
| `GET` | `/history` | Lịch sử phân tích (phân trang) |
| `GET` | `/history/{analysisId}` | Chi tiết 1 analysis (không gọi AI) |
| `DELETE` | `/history/{analysisId}` | Xóa analysis (owner-only) |

---

## 🔄 Luồng chi tiết

### Bước 0: User nhập ý tưởng

Researcher gõ idea text, ví dụ:
> *"Security vulnerabilities and mitigation strategies in smart door lock systems using biometric and Bluetooth authentication."*

---

### Bước 1: `POST /api/v1/ideas/extract-keywords` — Trích xuất từ khóa

```
FE gửi { ideaText }
  → IdeaAnalysisService.extractKeywords()
    → Check L1 RAM cache (MD5 hash, TTL 24h)
    → Cache miss → gọi DeepSeek AI:
        "Extract 5 specific, searchable terms from this idea.
         Do NOT add generic AI/ML terms unless the idea is about them.
         Suggest 5 related academic terms."
    → Post-process: filter bỏ "suggestedkeywords", "extractedkeywords" artifacts
    → Cache miss + AI fail → fallback: extractPhrasesFromText()
        (bigram + trigram detection từ idea text)
    → Lưu L1 cache
  → Response: { extractedKeywords: [...], suggestedKeywords: [...] }
```

**Kết quả mong đợi:**
```json
{
  "extractedKeywords": [
    "security vulnerabilities",
    "smart door lock systems",
    "biometric authentication",
    "Bluetooth authentication",
    "mitigation strategies"
  ],
  "suggestedKeywords": [
    "iot security",
    "biometric spoofing",
    "ble security",
    "embedded systems",
    "access control"
  ]
}
```

---

### Bước 2: User chọn keywords → `POST /api/v1/ideas/analyze`

User gửi: `{ ideaText, selectedKeywords: [...] }` (thường là merge của extracted + vài suggested)

#### 2a. Paper Search (OpenAlex API)

```
Với mỗi keyword:
  ┌─ Primary: searchByRelevance(kw, 5, 2015-nay)
  │     Gọi OpenAlex: ?search=keyword&sort=relevance_score:desc
  │     → Papers thực sự liên quan về mặt ngữ nghĩa
  │
  └─ Supplementary: searchTopCited(kw, 3, recent 3 years)
        Gọi OpenAlex: ?search=keyword&sort=cited_by_count:desc
        → Papers impact cao gần đây (bổ sung)
```

→ Deduplicate (by paper ID)
→ Sort: ưu tiên paper có PDF URL → rồi theo citation count
→ Giới hạn max 5 papers (`deepseek.idea.max-papers`)

#### 2b. Paper Evaluation (4 tiêu chí × từng paper, 3-tier cache)

```
Với mỗi paper:
  ┌─ L1 RAM (ConcurrentHashMap, TTL 7 ngày)
  ├─ L2 DB (PAPER_EVALUATION_CACHE, cross-user, unique paper_id+idea_hash)
  └─ L3 DeepSeek AI:
       1. Tải PDF (nếu có pdfUrl) → extract text bằng PDFBox (in-memory)
       2. Gửi prompt: IDEA + Abstract (1500 chars) + PDF excerpt (3000 chars)
       3. AI đánh giá 4 tiêu chí:
          - TOPIC_MATCH: Có cùng chủ đề không?
          - METHOD_RELEVANT: Phương pháp có áp dụng được không?
          - GAP_ADDRESSED: Paper đã giải quyết gap nào chưa?
          - CITE_WORTHY: Có đáng cite không?
       4. Mỗi tiêu chí → Boolean + evidenceQuote (trích dẫn làm bằng chứng)
       5. Lưu kết quả vào L1 + L2
```

**3-tier caching:**

| Level | Storage | TTL | Scope |
|-------|---------|-----|-------|
| L1 | RAM `ConcurrentHashMap` | 7 ngày | Per JVM instance |
| L2 | DB `PAPER_EVALUATION_CACHE` | Vĩnh viễn | Cross-user (unique `paper_id` + `idea_hash`) |
| L3 | DeepSeek AI API | Không cache | — |

#### 2c. Gap Analysis

```
Gửi toàn bộ kết quả evaluation cho DeepSeek AI:
  → Phân tích cross-paper:
     - solvedAreas: Những gì đã được giải quyết
     - partiallyAddressed: Giải quyết một phần (kèm limitation)
     - researchGaps: Khoảng trống (gap + rationale + suggestedDirection)
     - suggestedDirections: Định hướng nghiên cứu
     - noveltyScore: 0-100
     - noveltyExplanation: Giải thích điểm

  AI fail → fallback programmatic:
     Dựa trên criteria của papers
     noveltyScore = 80 - papers.size() * 10 (capped 20-85)
```

#### 2d. Literature Review

```
Gửi papers + relevance flags cho DeepSeek AI:
  → Mỗi paper có isTopicallyRelevant flag (từ TOPIC_MATCH criteria)
  → Nếu 0 papers relevant:
     "This research direction occupies a novel niche...
      A comprehensive search across specialized databases is recommended."
     → KHÔNG cite papers off-topic
  → Nếu có papers relevant:
     Viết Related Work section, chỉ cite papers có TOPIC_MATCH=true
     Kèm REFERENCES list

  AI fail → fallback programmatic:
     Filter papers: chỉ cite paper có ít nhất 1 criteria = true
     Nếu 0 papers relevant → honest statement về niche novelty
```

#### 2e. Persistence

```
Toàn bộ kết quả (papers + gapAnalysis + literatureReview)
  → serialize JSON → lưu IDEA_ANALYSIS.result_json
  → User có thể xem lại qua GET /history/{analysisId}
```

---

### Bước 3: `GET /api/v1/ideas/history` — Xem lịch sử

```
Phân trang, sắp xếp mới nhất trước
  → Parse noveltyScore từ result_json
  → Response: { items: [...], totalItems, totalPages, currentPage }
```

### Bước 4: `GET /api/v1/ideas/history/{id}` — Xem chi tiết

```
Load IDEA_ANALYSIS từ DB → verify ownership → deserialize result_json
  → KHÔNG gọi AI lại, trả về ngay
```

### Bước 5: `DELETE /api/v1/ideas/history/{id}` — Xóa

```
deleteByAnalysisIdAndUser_UserId() → 204 No Content
```

---

## 📊 Sơ đồ tổng thể

```
User idea text
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│  [1] extract-keywords                                   │
│  DeepSeek AI → keywords + suggested                     │
│  Cache: L1 RAM (24h)                                    │
└─────────────────────────────────────────────────────────┘
     │
     ▼ User chọn keywords
┌─────────────────────────────────────────────────────────┐
│  [2] analyze (4-step pipeline)                          │
│                                                         │
│  Step 1: Paper Search                                   │
│    OpenAlex search=keyword (relevance + top-cited)      │
│    → Max 5 papers                                       │
│                                                         │
│  Step 2: Paper Evaluation (3-tier cache)                │
│    L1 RAM → L2 DB → L3 DeepSeek AI + PDF extraction     │
│    → 4 criteria: Topic, Method, Gap, Cite               │
│                                                         │
│  Step 3: Gap Analysis                                   │
│    DeepSeek AI → cross-paper synthesis                  │
│    → Novelty Score 0-100 + research gaps + directions   │
│                                                         │
│  Step 4: Literature Review                              │
│    DeepSeek AI (with relevance flags)                   │
│    → Only cites relevant papers                         │
│    → Honest when no relevant papers found               │
│                                                         │
│  → Persist to IDEA_ANALYSIS                             │
└─────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│  [3-5] History CRUD                                     │
│  GET /history → paginated list                          │
│  GET /history/{id} → full detail (no AI)                │
│  DELETE /history/{id} → owner-only                      │
└─────────────────────────────────────────────────────────┘
```

---

## 🗄️ Database Schema

### `IDEA_ANALYSIS`

| Column | Type | Mô tả |
|--------|------|-------|
| `analysis_id` | UUID PK | |
| `user_id` | UUID FK → USER | |
| `idea_text` | NVARCHAR(MAX) | Ý tưởng gốc |
| `idea_hash` | VARCHAR(64) | MD5 để cache dedup |
| `keywords` | NVARCHAR(MAX) | JSON array keywords |
| `result_json` | NVARCHAR(MAX) | **Toàn bộ** kết quả serialize |
| `paper_count` | INT | Số paper đã phân tích |
| `created_at` | DATETIME2 | |
| `updated_at` | DATETIME2 | |

### `PAPER_EVALUATION_CACHE` (L2 cache)

| Column | Type | Mô tả |
|--------|------|-------|
| `cache_id` | UUID PK | |
| `paper_id` | UUID | |
| `idea_hash` | VARCHAR(64) | MD5 |
| `criteria_json` | NVARCHAR(MAX) | JSON 4 tiêu chí |
| `created_at` | DATETIME2 | |

Unique constraint: `(paper_id, idea_hash)` → **cross-user dedup**

---

## 🤖 AI Configuration

```properties
deepseek.api.key=${DEEPSEEK_API_KEY}
deepseek.api.url=${DEEPSEEK_API_URL:https://api.ai-box.vn/v1/chat/completions}
deepseek.model=${DEEPSEEK_MODEL:deepseek-v4-pro}
deepseek.idea.max-papers=5
```

| Constant | Value | Purpose |
|----------|-------|---------|
| `MAX_PAPERS` | 5 | Max papers to analyze |
| `KEYWORD_MAX_TOKENS` | 512 | Keyword extraction |
| `EVAL_MAX_TOKENS` | 1024 | Per-paper evaluation |
| `GAP_MAX_TOKENS` | 2048 | Gap analysis |
| `LIT_REVIEW_MAX_TOKENS` | 2048 | Literature review |
| `LOW_TEMP` | 0.1 | Evaluation (deterministic) |
| `MEDIUM_TEMP` | 0.3 | Gap + Lit Review (creative) |

---

## 📁 Các file liên quan

| Category | File |
|----------|------|
| Controller | `controller/IdeaController.java` |
| Main Service | `service/IdeaAnalysisService.java` |
| AI Client Interface | `service/AIClient.java` |
| AI Client Impl | `service/DeepSeekClient.java` |
| PDF Extraction | `service/PdfExtractionService.java` |
| Paper Search | `service/OpenAlexFallbackSearchService.java` |
| Entity | `entity/jpa/IdeaAnalysis.java` |
| Entity | `entity/jpa/PaperEvaluationCache.java` |
| Repository | `repository/jpa/IdeaAnalysisRepository.java` |
| Repository | `repository/jpa/PaperEvalCacheRepository.java` |
| DTOs | `dto/idea/*.java` |
| Config | `config/AppConfig.java` |
| Properties | `application.properties` |
| DB Schema | `schema.sql` (lines 1150-1204) |

---

## ⚠️ Error Codes

| Code | HTTP | Message |
|------|------|---------|
| `IDEA_ANALYSIS_NOT_FOUND` | 404 | Analysis not found |
| `IDEA_ANALYSIS_UNAUTHORIZED` | 403 | You do not have permission to access this analysis |
| `IDEA_KEYWORDS_EMPTY` | 400 | At least one keyword is required |
| `IDEA_TEXT_EMPTY` | 400 | Idea text cannot be empty |
| `IDEA_AI_FAILED` | 503 | AI analysis is temporarily unavailable. Please try again later |

---

## 🔑 Key Design Decisions

1. **3-tier caching** cho paper evaluation — L1 RAM (7 ngày) + L2 DB (cross-user, vĩnh viễn) + L3 AI. Giúp giảm đáng kể AI cost khi nhiều user phân tích cùng idea.

2. **Always fallback** — mọi bước AI đều có fallback programmatic. Nếu DeepSeek fail ở bất kỳ bước nào, service vẫn trả về kết quả thay vì lỗi hoàn toàn.

3. **PDF extraction in-memory** — PDF được tải và extract text trong memory, không ghi disk. Tối đa 3000 chars được gửi cho AI.

4. **Search strategy** — Relevance-first (tìm papers thực sự liên quan) + Top-cited supplementary (bổ sung papers impact cao). Dùng `search=` parameter thay vì `filter=fulltext.search:` để tránh encoding issues.

5. **Literature Review aware of relevance** — Prompt AI được cấp `isTopicallyRelevant` flag cho từng paper. Khi không có papers nào relevant, AI sẽ viết honest review thay vì cite papers off-topic.

6. **Side effect khi search** — `OpenAlexFallbackSearchService` trigger async save papers vào local DB (SQL Server + Neo4j) mỗi lần search, giúp populate database dần dần.
