# 🎯 CÂU HỎI SINH TỬ: "Số liệu trên màn hình search đến từ đâu? OpenAlex lỗi thì sao?"

> Đây là câu hỏi khiến bạn trượt lần trước. Đọc kỹ và học thuộc cách trả lời.

---

## Màn hình search hiển thị những gì?

Khi user gõ "deep learning" và bấm Search, FE gọi **NHIỀU API CÙNG LÚC**:

```
┌──────────────────────────────────────────────────────────┐
│  🔍 [deep learning                              ] [Search]│
│                                                          │
│  [📄 Papers (42)] [👤 Authors (5)] [📚 Journals (3)]     │  ← Tab counts
│  ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔│
│                                                          │
│  📊 Quick Stats                     🔗 Keywords Graph    │
│  ┌─────────────────────┐           ┌──────────────────┐  │
│  │ 42 papers            │           │   [Neo4j vis]    │  │
│  │ 890 citations        │           │                  │  │
│  │ +12% growth          │           │  deep learning   │  │
│  │ 21.2 avg citations   │           │  ├─ neural net   │  │
│  │ 156 this year        │           │  ├─ transformer  │  │
│  └─────────────────────┘           │  └─ CNN          │  │
│                                     └──────────────────┘  │
│  📝 Top Cited Papers                🕐 Recent Searches    │
│  ┌─────────────────────┐           ┌──────────────────┐  │
│  │ 1. Attention Is All  │           │ • machine learning│  │
│  │ 2. Deep Residual...  │           │ • neural network │  │
│  │ 3. BERT: Pre-train..│           │ • computer vision│  │
│  └─────────────────────┘           └──────────────────┘  │
│                                                          │
│  🏷️ Categories: [AI] [ML] [NLP] [CV] [Data Science]     │
└──────────────────────────────────────────────────────────┘
```

---

## TỪNG CON SỐ ĐẾN TỪ ĐÂU?

### 1. 📄 DANH SÁCH PAPERS (42 papers) — QUAN TRỌNG NHẤT

```
API: GET /api/v1/papers/search?query=deep+learning
→ PaperSearchController.searchPapers()
   │
   ├── Keyword search mặc định → PaperSearchOrchestrator.searchByKeyword()
   │     │
   │     ├── B1: Cache check (6 tiếng) → nếu có → TRẢ VỀ NGAY
   │     ├── B2: Check usage limit
   │     ├── B3: Gọi OpenAlex API (2 lần)
   │     │     GET api.openalex.org/works?search=deep+learning&sort=cited_by_count:desc
   │     │     GET api.openalex.org/works?search=deep+learning&sort=publication_date:desc
   │     ├── B4: Merge + deduplicate 2 kết quả
   │     └── B5: Cache kết quả (6h) → return
   │
   └── Search có filter/sort → PaperSearchServiceImpl.searchPapers()
         │
         ├── B1: Keyword expansion (mở rộng từ khóa)
         ├── B2: Query SQL Server (ResearchPaperRepository.findPrimaryCandidates...)
         ├── B3: Nếu SQL có kết quả → tính relevance score → return
         └── B4: Nếu SQL không có → fallback OpenAlex API
```

**Nguồn dữ liệu:** `OpenAlex API` (đường chính) hoặc `SQL Server` (đường phụ)

**Nếu OpenAlex lỗi:**
- Đường chính: `catch (Exception e)` → `buildEmptyResult()` → **TRẮNG, không có paper nào**
- Đường phụ: Vẫn hiện nếu SQL Server có dữ liệu
- **NGOẠI LỆ:** Nếu keyword đã được search trong 6 tiếng qua → cache vẫn trả về kết quả cũ

---

### 2. 📊 QUICK STATS (42 papers, 890 citations, +12% growth...)

```
API: GET /api/search/keyword/quick-stats?keyword=deep+learning
→ SearchController → KeywordQuickStatsServiceImpl.getKeywordQuickStats()

   B1: Gọi OpenAlex API → GET /works?search=deep+learning&sort=cited_by_count:desc
       → Lấy totalPapers, totalCitations, papersThisYear, papersLastYear...

   B2: NẾU OpenAlex thất bại → FALLBACK Local DB
       → graphService.countPapersByKeyword()        [Neo4j]
       → graphService.getAllPaperIdsByKeyword()     [Neo4j]
       → researchPaperRepository.findAllById()      [SQL Server]
       → Tính toán stats từ dữ liệu local

   B3: Nếu cả 2 đều không có → buildEmptyResponse() → tất cả = 0
```

**Nguồn dữ liệu:** `OpenAlex API` → fallback `Neo4j + SQL Server`

**Nếu OpenAlex lỗi:** **VẪN HIỆN SỐ** nếu đã từng sync data về local DB trước đó. Nếu chưa từng sync → hiện 0.

---

### 3. 🔗 KEYWORDS GRAPH (sơ đồ từ khóa liên quan)

```
API: GET /api/search/keyword/related-trends?keyword=deep+learning
→ SearchController → KeywordQuickStatsServiceImpl.getRelatedKeywords()
   → graphService.getCooccurringKeywords()  [Neo4j]
      MATCH (k:Keyword)-[:HAS_KEYWORD]-(p:Paper)-[:HAS_KEYWORD]-(related:Keyword)
      WHERE k.normalizedText = 'deep learning'
      RETURN related, count(p) ...
```

**Nguồn dữ liệu:** `Neo4j` (100% độc lập với OpenAlex)

**Nếu OpenAlex lỗi:** **VẪN HIỆN BÌNH THƯỜNG** — Neo4j không phụ thuộc OpenAlex

---

### 4. 📝 TOP CITED PAPERS (danh sách 5 paper được cite nhiều nhất)

```
API: GET /api/search/keyword/top-papers?keyword=deep+learning
→ SearchController → KeywordQuickStatsServiceImpl.getTopPapers()
   → openAlexSearchService.searchTopCited(keyword, 5)
      GET api.openalex.org/works?search=deep+learning&sort=cited_by_count:desc&per_page=5
```

**Nguồn dữ liệu:** `OpenAlex API`

**Nếu OpenAlex lỗi:** **KHÔNG HIỆN** — trả về empty list

---

### 5. 🕐 RECENT SEARCHES (lịch sử tìm kiếm gần đây)

```
API: GET /api/search/history/recent
→ SearchController → UserSearchHistoryService.getRecentSearches()
   → userSearchHistoryRepository.findByUser_UserIdOrderBySearchedAtDesc()
      SELECT * FROM USER_SEARCH_HISTORY WHERE UserID = ? ORDER BY SearchedAt DESC
```

**Nguồn dữ liệu:** `SQL Server` (bảng USER_SEARCH_HISTORY)

**Nếu OpenAlex lỗi:** **VẪN HIỆN BÌNH THƯỜNG** — data từ SQL Server, không liên quan OpenAlex

---

### 6. 🏷️ CATEGORIES (nút phân loại: AI, ML, NLP, CV...)

```
API: GET /api/search/categories
→ SearchController → graphService.getCategoryKeywords() [Neo4j]
   Hoặc researchFieldRepository.findAll() [SQL Server]
```

**Nguồn dữ liệu:** `Neo4j` hoặc `SQL Server` (bảng RESEARCH_FIELD)

**Nếu OpenAlex lỗi:** **VẪN HIỆN BÌNH THƯỜNG**

---

### 7. 👤 AUTHORS TAB (5 authors)

```
API: GET /api/v1/papers/search/author?authorName=...
→ PaperSearchController.searchByAuthor()
   → openAlexFallbackSearchService.searchByAuthorOnOpenAlex()
      GET api.openalex.org/authors?search=...
```

**Nguồn dữ liệu:** `OpenAlex API`

**Nếu OpenAlex lỗi:** **KHÔNG HIỆN**

---

### 8. 📚 JOURNALS TAB (3 journals)

```
API: GET /api/v1/papers/search/journal?journalId=...
→ PaperSearchController.searchByJournal()
   → researchPaperRepository.findByJournal_JournalIdAndPubYearBetween()
      SELECT * FROM RESEARCH_PAPER WHERE JournalID = ? ...
```

**Nguồn dữ liệu:** `SQL Server`

**Nếu OpenAlex lỗi:** **VẪN HIỆN BÌNH THƯỜNG**

---

## ⚡ TÓM TẮT: KHI OPENALEX LỖI THÌ SAO?

| Thành phần trên màn hình | Có hiện không? | Lý do |
|--------------------------|----------------|-------|
| **Danh sách papers** | ❌ TRẮNG (trừ khi có cache 6h) | Orchestrator gọi thẳng OpenAlex, catch exception → empty |
| **Quick Stats** | ⚠️ CÓ THỂ (nếu có data local) | Fallback Neo4j + SQL Server |
| **Keywords Graph** | ✅ VẪN HIỆN | Neo4j — độc lập |
| **Top Cited Papers** | ❌ KHÔNG HIỆN | Gọi thẳng OpenAlex |
| **Recent Searches** | ✅ VẪN HIỆN | SQL Server — độc lập |
| **Categories** | ✅ VẪN HIỆN | SQL Server / Neo4j — độc lập |
| **Authors tab** | ❌ KHÔNG HIỆN | Gọi thẳng OpenAlex |
| **Journals tab** | ✅ VẪN HIỆN | SQL Server — độc lập |

---

## 🎤 CÁCH TRẢ LỜI KHI GIẢNG VIÊN HỎI

### Câu hỏi: "Em search 'deep learning', con số 42 papers ở đâu ra?"

> **Trả lời:**
> "Dạ, con số 42 papers đến từ OpenAlex API ạ. Khi user search, `PaperSearchController` gọi `PaperSearchOrchestrator.searchByKeyword()`. Orchestrator này gọi OpenAlex API 2 lần — 1 lần lấy top cited, 1 lần lấy relevance — sau đó merge và deduplicate lại. Kết quả được cache 6 tiếng trong memory. Nếu search lại trong 6 tiếng thì lấy từ cache, không gọi API nữa."

### Câu hỏi: "Nếu OpenAlex bị lỗi thì sao?"

> **Trả lời:**
> "Dạ, có 2 trường hợp ạ:
>
> **1. Với danh sách papers chính:** Nếu OpenAlex lỗi thì sẽ trả về danh sách rỗng, vì Orchestrator có try-catch bọc quanh lời gọi API — khi lỗi thì catch exception và trả về `buildEmptyResult()`. Tuy nhiên nếu keyword đó đã được search trong 6 tiếng trước đó, kết quả cache vẫn được trả về.
>
> **2. Với Quick Stats:** Có cơ chế fallback 2 lớp — đầu tiên gọi OpenAlex, nếu thất bại thì query Neo4j và SQL Server để lấy số liệu từ database local. Nên nếu đã từng sync dữ liệu về local DB trước đó thì Quick Stats vẫn hiển thị được.
>
> **3. Với Keywords Graph, Recent Searches, Categories:** Những thành phần này lấy từ Neo4j hoặc SQL Server, hoàn toàn không phụ thuộc OpenAlex nên vẫn hiện bình thường."

### Câu hỏi: "Tại sao có chỗ thì lấy từ OpenAlex, chỗ thì lấy từ SQL Server?"

> **Trả lời:**
> "Dạ, thiết kế của tụi em là dual-database + external API:
> - **OpenAlex** là nguồn dữ liệu chính vì họ có 250M+ papers, cập nhật real-time
> - **SQL Server** lưu papers đã crawl về để truy vấn nhanh và offline được
> - **Neo4j** lưu keyword graph để tìm research gap và related keywords
>
> Khi search, ưu tiên OpenAlex vì dữ liệu mới nhất. Nhưng các dữ liệu không thay đổi thường xuyên (lịch sử search, categories, graph) thì lưu trong database của mình để không phụ thuộc vào API bên ngoài."

---

## 🔍 CODE CHỨNG MINH

### Bằng chứng Orchestrator catch exception khi OpenAlex lỗi:

**File:** `PaperSearchOrchestrator.java`, dòng 123-152

```java
// Dòng 123: try block bọc quanh lời gọi OpenAlex
try {
    List<PaperDetailResponseDTO> papers = openAlexFallbackSearchService.searchTopCited(...);
    List<PaperDetailResponseDTO> relevancePapers = openAlexFallbackSearchService.searchNoYearFilter(...);
    // ... merge và return nếu có kết quả ...
} catch (Exception e) {
    // Dòng 149-150: BẮT LỖI — không throw, chỉ log warning
    log.warn("OpenAlex search failed for '{}': {}", trimmedKeyword, e.getMessage());
}
// Dòng 153: Trả về empty result nếu OpenAlex lỗi
return buildEmptyResult();
```

### Bằng chứng Quick Stats fallback về local DB:

**File:** `KeywordQuickStatsServiceImpl.java`, dòng 97-106

```java
// Dòng 97-99: Primary — OpenAlex API
String openAlexFilter = buildOpenAlexFilter(pubYearFrom, pubYearTo, isOpenAccess);
KeywordQuickStatsResponse response = getStatsFromOpenAlex(trimmedKeyword, openAlexFilter);
if (response != null && response.getTotalPapers() > 0) {
    return response;  // ← OpenAlex có data → return ngay
}
// Dòng 104-106: Fallback — Local DB
log.info("OpenAlex returned no data for '{}', falling back to local DB", trimmedKeyword);
return getStatsFromLocalDb(trimmedKeyword);  // ← Query Neo4j + SQL Server
```