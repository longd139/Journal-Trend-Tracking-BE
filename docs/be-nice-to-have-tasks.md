# BE Nice-to-Have — Task List

> **Ngày:** 2026-07-05
> **Phạm vi:** Backend các tính năng nice-to-have (P2) + 1 mục P1 còn tồn đọng
> **Tổng:** 7 task

---

## 📊 Tổng quan

| # | Task | Mức độ | Công sức | Người làm | Phụ thuộc |
|---|------|--------|----------|-----------|-----------|
| 1 | Reports với AI (P1) | 🟠 Trung bình | 2-3 ngày | — | AI services đã có sẵn |
| 2 | Keyword Comparison Endpoint | 🟢 Dễ | 1-2 ngày | **Thuận** | FE đã có UI sẵn |
| 3 | PublicationTrend + ResearchTopic Controllers | 🟢 Dễ | 1-2 ngày | **Thuận** | Entity + Repository đã có |
| 4 | Country/Institution Breakdown | 🟠 Trung bình | 2-3 ngày | **Triệu** | Author entity đã có country/institution |
| 5 | Research Gap Detection (mở rộng) | 🟠 Trung bình | 2-3 ngày | **Triệu** | Niche discovery đã có, cần mở rộng |
| 6 | Journal Matching / Venue Recommendation | 🔴 Khó | 3-5 ngày | **Long** | Cần AI + journal data |
| 7 | Automated PDF Retrieval | 🔴 Khó | 3-5 ngày | **Triệu** | Cần tích hợp external PDF sources |

### Phân công

| Thành viên | Task | Tổng công sức |
|------------|------|---------------|
| **Thuận** | #2 Keyword Comparison, #3 PublicationTrend + ResearchTopic | 2-4 ngày |
| **Long** | #6 Journal Matching | 3-5 ngày |
| **Triệu** | #4 Country/Institution, #5 Research Gap, #7 Auto PDF Retrieval | 7-11 ngày |
| — | #1 Reports với AI (chưa phân công) | 2-3 ngày |

---

## Task 1: Reports với AI (P1)

### Hiện trạng
- `ReportServiceImpl.java` dùng hardcoded template tiếng Việt (switch/case)
- AI services đã có sẵn: `AISummarizationService`, `DeepSeekClient`, `GeminiService`, `AIClient`
- Chưa tích hợp AI vào report pipeline

### Cần làm
1. Inject `AISummarizationService` hoặc `AIClient` vào `ReportServiceImpl`
2. Thay `generateKeywordInsight()` — gọi AI prompt: "Analyze the trend of keyword X: papers Y, citations Z, growth W%. Write a 3-4 sentence insight in Vietnamese."
3. Thay `generateAuthorInsight()` — prompt: "Evaluate author X with h-index Y in field Z. Write insight in Vietnamese."
4. Thay `generateJournalInsight()` — prompt: "Evaluate journal X (quartile Y, IF Z). Write insight in Vietnamese."
5. Cache AI response (30 phút) — tránh gọi lại cho cùng input
6. Fallback về template cũ nếu AI key unavailable

### Files liên quan
- `service/impl/ReportServiceImpl.java`
- `service/AISummarizationService.java`
- `service/AIClient.java`
- `service/DeepSeekClient.java`

---

## Task 2: Keyword Comparison Endpoint — **Thuận**

### Hiện trạng
- FE `AnalyticsPage.jsx` đã có `KeywordComparison()` component với BarChart
- BE chưa có endpoint so sánh nhiều keyword
- Các endpoint keyword hiện có chỉ xử lý đơn lẻ: `quick-stats`, `related-trends`, `top-papers`

### Cần làm
1. Tạo DTO: `KeywordComparisonRequest` (List<String> keywords) + `KeywordComparisonResponse` (Map<keyword, stats>)
2. Tạo endpoint: `POST /api/search/keywords/compare`
3. Logic: Với mỗi keyword trong list, query Neo4j đếm paper count → query SQL lấy citation count, year range, growth rate
4. Trả về response để FE vẽ BarChart: `{ keywords: [{ name, paperCount, citationCount, growthRate, topYear }] }`
5. Cache 30 phút (key = sorted keyword list hash)

### API Contract
```
POST /api/search/keywords/compare
Body: { "keywords": ["deep-learning", "reinforcement-learning", "nlp"] }

Response:
{
  "keywords": [
    { "name": "deep-learning", "paperCount": 15420, "citationCount": 320000, "growthRate": 12.5, "topYear": 2025 },
    { "name": "reinforcement-learning", "paperCount": 8230, "citationCount": 150000, "growthRate": 8.3, "topYear": 2024 },
    { "name": "nlp", "paperCount": 18900, "citationCount": 410000, "growthRate": 15.2, "topYear": 2025 }
  ]
}
```

### Files cần tạo/sửa
- `dto/search/KeywordComparisonRequest.java` (mới)
- `dto/search/KeywordComparisonResponse.java` (mới)
- `controller/SearchController.java` (thêm endpoint)
- `service/GraphService.java` (thêm batch keyword query)

---

## Task 3: PublicationTrend + ResearchTopic Controllers — **Thuận**

### Hiện trạng
- Entity `PublicationTrend.java` và `ResearchTopic.java` đã có đầy đủ
- Repository `PublicationTrendRepository.java` (findTopGrowthTopic) và `ResearchTopicRepository.java` (countTrendingTopics, countByDateRange, findTopTrendingTopic) đã có
- Chỉ được dùng nội bộ trong `OverviewStatisticsServiceImpl` và `DataSyncServiceImpl`
- **Chưa có REST endpoint public**

### Cần làm
1. **ResearchTopic endpoints:**
   - `GET /api/public/research-topics/trending` — top N trending topics (theo trendScore), phân trang
   - `GET /api/public/research-topics/{id}` — chi tiết 1 topic + linked papers
   - `GET /api/public/research-topics/by-field/{fieldId}` — topics theo research field

2. **PublicationTrend endpoints:**
   - `GET /api/public/publication-trends?periodType=MONTHLY&limit=12` — trend data theo thời gian
   - `GET /api/public/publication-trends/top-growth` — top growth topics (dùng `findTopGrowthTopic()`)

3. Tạo DTOs:
   - `dto/trend/ResearchTopicResponse.java`
   - `dto/trend/PublicationTrendResponse.java`

### Files cần tạo/sửa
- `controller/ResearchTopicController.java` (mới)
- `controller/PublicationTrendController.java` (mới)
- `dto/trend/ResearchTopicResponse.java` (mới)
- `dto/trend/PublicationTrendResponse.java` (mới)

---

## Task 4: Country/Institution Breakdown — **Triệu**

### Hiện trạng
- `Author.java` entity có `affiliation` và `country` fields
- Không có aggregation endpoint nào breakdown theo quốc gia hoặc tổ chức
- FE cần data này cho biểu đồ phân bổ địa lý

### Cần làm
1. **Country breakdown:**
   - `GET /api/v1/analytics/by-country?keyword=&year=` — paper count + citation count theo quốc gia
   - Query: group authors by country → count papers, sum citations

2. **Institution breakdown:**
   - `GET /api/v1/analytics/by-institution?keyword=&year=` — top N institutions
   - Query: group authors by affiliation → count papers, sum citations

3. **Trend by country (nâng cao):**
   - `GET /api/v1/analytics/trend-by-country?keyword=&startYear=&endYear=` — timeline data per country
   - Cho phép so sánh xu hướng nghiên cứu giữa các quốc gia

4. Tạo DTOs:
   - `dto/analytics/CountryBreakdownResponse.java`
   - `dto/analytics/InstitutionBreakdownResponse.java`

5. Tạo Service: `AnalyticsService.java` + `AnalyticsServiceImpl.java`

### API Contract mẫu
```
GET /api/v1/analytics/by-country?keyword=deep-learning&year=2025

Response:
{
  "keyword": "deep-learning",
  "year": 2025,
  "countries": [
    { "country": "United States", "paperCount": 4520, "citationCount": 98000 },
    { "country": "China", "paperCount": 3890, "citationCount": 72000 },
    { "country": "United Kingdom", "paperCount": 1200, "citationCount": 31000 }
  ]
}
```

### Files cần tạo/sửa
- `controller/AnalyticsController.java` (mới)
- `service/AnalyticsService.java` (mới)
- `service/impl/AnalyticsServiceImpl.java` (mới)
- `dto/analytics/CountryBreakdownResponse.java` (mới)
- `dto/analytics/InstitutionBreakdownResponse.java` (mới)

---

## Task 5: Research Gap Detection (mở rộng) — **Triệu**

### Hiện trạng
- Đã có niche topic discovery: `GET /api/search/categories/niches?keyword=`
- Neo4j graph traversal tìm keyword liên quan ít paper
- **Chưa có true gap detection** — chưa so sánh paper count với "expected" dựa trên mức độ quan trọng

### Cần làm
1. **Mở rộng niche endpoint hiện có:**
   - Thêm `minGapScore` param — chỉ trả về topics có gap score cao (ít paper nhưng nhiều citation tiềm năng)
   - Thêm `sortBy=gapScore` option

2. **Gap score formula:**
   ```
   gapScore = log(totalCitations + 1) / (paperCount + 1) * growthRate
   ```
   - Citation cao + paper ít + đang tăng trưởng → gap lớn → cơ hội nghiên cứu

3. **AI-enhanced gap analysis:**
   - Dùng `AISummarizationService` prompt: "Topic X has Y papers but Z citations. Is this an under-researched area? Why? Suggest research directions."
   - Trả về insight text kèm gap data

4. **New endpoint:** `GET /api/v1/research-gaps?field=&minGapScore=&limit=`

### API Contract mẫu
```
GET /api/v1/research-gaps?field=computer-science&minGapScore=1.5&limit=10

Response:
{
  "field": "computer-science",
  "gaps": [
    {
      "keyword": "quantum-neural-networks",
      "paperCount": 45,
      "citationCount": 2300,
      "growthRate": 35.2,
      "gapScore": 2.87,
      "aiInsight": "Quantum neural networks is emerging rapidly with only 45 papers but 2300+ citations..."
    }
  ]
}
```

### Files cần tạo/sửa
- `controller/SearchController.java` (mở rộng niche endpoint)
- `service/GraphService.java` (thêm gap score logic)
- `dto/search/ResearchGapResponse.java` (mới)
- `dto/search/NicheTopicResponse.java` (mở rộng thêm gapScore field)

---

## Task 6: Journal Matching / Venue Recommendation — **Long**

### Hiện trạng
- `JournalController.java` có browse journal theo category/field — không phải recommender
- `TopJournalResponse.java` hiển thị journal nào publish nhiều cho 1 keyword — là thống kê, không phải gợi ý
- `ReportController.java` có journal quality report — đánh giá thủ công

### Cần làm
1. **Input:** User cung cấp paper abstract/title/keywords
2. **Logic matching (2 strategies):**
   - **Strategy A — Keyword overlap:** So sánh keyword của paper với "editorial taste" keywords của journal (`Journal.java` có `keywords` field) → score dựa trên % overlap
   - **Strategy B — AI-powered:** Gửi abstract + list of journals (top 20 trong field) cho AI → AI rank và giải thích lý do
3. **Output:** Ranked list 5-10 journals kèm match score, reasoning
4. **Endpoint:** `POST /api/v1/journals/match`
5. Cache kết quả AI 1h (key = abstract hash + field)

### API Contract mẫu
```
POST /api/v1/journals/match
Body: {
  "title": "Graph Neural Networks for Molecular Property Prediction",
  "abstract": "We propose a novel GNN architecture...",
  "keywords": ["graph-neural-networks", "molecular-property", "drug-discovery"],
  "field": "computer-science"
}

Response:
{
  "matches": [
    {
      "journalId": "...",
      "journalName": "Journal of Cheminformatics",
      "quartile": "Q1",
      "impactFactor": 7.1,
      "matchScore": 0.89,
      "reason": "Strong keyword overlap (GNN, molecular, drug-discovery) + publishes 120+ papers/year in this area. High acceptance rate for computational methods."
    }
  ]
}
```

### Files cần tạo/sửa
- `controller/JournalController.java` (thêm endpoint match)
- `dto/journal/JournalMatchRequest.java` (mới)
- `dto/journal/JournalMatchResponse.java` (mới)
- `service/JournalMatchingService.java` (mới)
- `service/impl/JournalMatchingServiceImpl.java` (mới)

---

## Task 7: Automated PDF Retrieval — **Triệu**

### Hiện trạng
- `PdfRequestController.java` — user gửi request, admin xử lý thủ công
- `AdminPdfRequestController.java` — admin tìm/duyệt/từ chối
- Chưa có automation: không scraper, không background downloader, không tích hợp PDF repo

### Cần làm
**Phase 1 — Semi-automated (nên làm trước):**
1. Khi user gửi PDF request, hệ thống tự động thử các nguồn:
   - **OpenAlex** — check `open_access.is_oa=true` + `best_oa_url`
   - **arXiv** — search bằng title/DOI
   - **CORE API** — đã có `CORE_API_KEY` trong config
   - **Semantic Scholar** — check open access PDF link
2. Nếu tìm thấy → tự động fulfill request, gửi notification cho user
3. Nếu không tìm thấy → chuyển sang admin xử lý thủ công (như cũ)

**Phase 2 — Background scheduled retrieval (làm sau):**
4. Scheduled job chạy hàng ngày quét các pending request
5. Retry các nguồn đã fail trước đó (có thể PDF mới được upload)

### Files cần tạo/sửa
- `service/impl/PdfRequestServiceImpl.java` (thêm auto-retrieval logic)
- `service/PdfRetrievalService.java` (mới — orchestrate multi-source)
- `service/impl/PdfRetrievalServiceImpl.java` (mới)
- `service/impl/DataSyncServiceImpl.java` (reuse OpenAlex/arXiv client code)

---

## 🗓️ Thứ tự ưu tiên

| Thứ tự | Task | Lý do |
|--------|------|-------|
| 1 | Task 2: Keyword Comparison | FE đã có UI, chỉ cần BE endpoint (thắng nhanh) |
| 2 | Task 3: PublicationTrend + ResearchTopic | Entity + Repo đã có, chỉ cần controller (thắng nhanh) |
| 3 | Task 1: Reports với AI (P1) | AI services có sẵn, tác động lớn đến trải nghiệm |
| 4 | Task 4: Country/Institution Breakdown | Author data đã có, chỉ cần aggregation |
| 5 | Task 5: Research Gap Detection | Có sẵn niche discovery, mở rộng thêm gap score |
| 6 | Task 6: Journal Matching | Cần AI + thiết kế matching algorithm |
| 7 | Task 7: Automated PDF Retrieval | Cần tích hợp nhiều external source |

**Tổng công sức ước tính:** 15-23 ngày cho toàn bộ 7 task
