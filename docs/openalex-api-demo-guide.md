# 🎯 Hướng Dẫn Demo: BE → OpenAlex API → JSON Response

> Tài liệu này giúp bạn trả lời câu hỏi: **"API từ BE gọi đến OpenAlex như thế nào và JSON trả về trông ra sao?"**
>
> ⚠️ Yêu cầu: URL phải **sinh ra từ code của bạn**, không phải URL chuẩn bị sẵn.

---

## CÁCH 1: DEBUG VS CODE (Khuyên dùng — trực quan nhất)

Đây là cách **thuyết phục nhất** vì giảng viên thấy tận mắt code chạy từng dòng, biến `url` được build ra, và `response` JSON được parse.

### Bước 1: Mở file cần debug

Mở `OpenAlexFallbackSearchService.java`:

```
src/main/java/com/sra/journal_tracking/service/OpenAlexFallbackSearchService.java
```

### Bước 2: Đặt 3 breakpoint

| Breakpoint | Dòng | Mục đích |
|------------|------|----------|
| **BP1** | dòng **176** (`String url = withApiKey(builder)...`) | Xem URL được build ra từ code |
| **BP2** | dòng **179** (`OpenAlexResponseDTO response = restTemplate...`) | Xem câu lệnh gọi API |
| **BP3** | dòng **180** (`if (response == null...)`) | Xem JSON response đã parse |

### Bước 3: Cấu hình launch.json

Mở `.vscode/launch.json`, đảm bảo có cấu hình này:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Debug SCITRACK BE",
            "request": "launch",
            "mainClass": "com.sra.journal_tracking.JournalTrackingApplication",
            "projectName": "journal-tracking",
            "console": "internalConsole"
        }
    ]
}
```

### Bước 4: Bắt đầu debug

1. Nhấn **F5** để start debug
2. Đợi app khởi động xong (thấy log `Started JournalTrackingApplication`)
3. Mở Postman hoặc FE, search từ khóa bất kỳ (vd: `machine learning`)

### Bước 5: Trình diễn khi breakpoint hit

**Khi BP1 hit (dòng 176):**

→ Nói với giảng viên: *"Đây là dòng code build URL gọi đến OpenAlex"*

→ Trong **Variables panel** (bên trái), expand biến `builder` → show các query param:
  - `search` = `machine+learning`
  - `sort` = `cited_by_count:desc`
  - `per-page` = `50`

→ Nhấn **F10** (Step Over) để chạy dòng 176

→ Bây giờ biến `url` đã có giá trị → **hover chuột vào biến `url`** để xem URL hoàn chỉnh:

```
https://api.openalex.org/works?search=machine+learning&sort=cited_by_count:desc&per-page=50&select=id,doi,title,...
```

→ Nói: *"URL này được sinh ra từ code, không phải em chuẩn bị sẵn"*

**Khi BP2 hit (dòng 179):**

→ Nói: *"Đây là dòng thực sự gọi API — dùng RestTemplate.getForObject()"*

→ Nhấn **F10** để execute → API được gọi, response về

**Khi BP3 hit (dòng 180):**

→ Trong **Variables panel**, expand biến `response`:
  - `meta` → `count` = 245000 (tổng số papers)
  - `results` → expand từng phần tử → xem `title`, `citedByCount`, `doi`, `keywords`, `authorships`

→ Nói: *"Đây là JSON response từ OpenAlex sau khi được Spring parse thành Java object"*

→ **Click chuột phải vào biến `response`** → chọn **"Copy as JSON"** (nếu có) hoặc expand từng field để giảng viên xem

### Tổng kết debug flow:

```
F5 (start) → search từ FE → BP1 hit (dòng 176) → F10 → xem url
→ BP2 hit (dòng 179) → F10 → gọi API
→ BP3 hit (dòng 180) → xem response JSON
```

---

## CÁCH 2: POSTMAN GỌI BE CỦA BẠN

Cách này chứng minh BE của bạn **thực sự chạy** và gọi được OpenAlex. Giảng viên thấy request → response hoàn chỉnh.

### Bước 1: Lấy JWT token (đăng nhập)

**POST** `http://localhost:8080/api/auth/login`

Body (JSON):
```json
{
    "email": "your-email@example.com",
    "password": "your-password"
}
```

Response chứa `accessToken` → copy token này.

### Bước 2: Gọi search API của BE

**GET** `http://localhost:8080/api/v1/papers/search?query=machine+learning`

Headers:
```
Authorization: Bearer <paste-access-token-ở-đây>
```

### Bước 3: Xem response từ BE

BE của bạn sẽ trả về JSON dạng:

```json
{
    "status": 200,
    "message": "Success",
    "data": {
        "papers": [
            {
                "paperId": "a1b2c3d4-...",
                "title": "Large language models in medicine",
                "abstractText": "Large language models are increasingly being used...",
                "doi": "10.1038/s41586-023-06291-2",
                "pubYear": 2023,
                "citationCount": 1523,
                "journalName": "Nature",
                "authors": [
                    { "fullName": "John Smith", "affiliation": "Stanford University" }
                ],
                "keywords": [
                    { "keywordText": "Large language models", "relevanceScore": 0.98 }
                ],
                "isOpenAccess": true,
                "sourceUrl": "https://doi.org/10.1038/s41586-023-06291-2"
            }
        ],
        "totalElements": 50,
        "currentPage": 0,
        "totalPages": 1
    }
}
```

→ Nói với giảng viên: *"Đây là response từ BE của em. Dữ liệu này được BE lấy từ OpenAlex API, parse, và trả về cho FE."*

### Bước 4: Chứng minh dữ liệu đến từ OpenAlex

Mở **console/terminal** nơi đang chạy BE, bạn sẽ thấy log:

```
INFO  PaperSearchOrchestrator - Fetching from OpenAlex for 'machine learning'
INFO  OpenAlexFallbackSearchService - OpenAlex HIT: 50 papers for 'machine learning'
```

→ Nói: *"Console log cho thấy BE đã gọi OpenAlex API và nhận được 50 papers. Nếu không có OpenAlex, response sẽ rỗng."*

---

## CÁCH 3: THÊM LOG TẠM THỜI (Dễ nhất, không cần debug)

Thêm 3 dòng log vào `OpenAlexFallbackSearchService.java` để khi chạy bình thường cũng thấy được URL và response.

### Code cần sửa:

Mở file `OpenAlexFallbackSearchService.java`, tìm method `searchTopCited` (dòng 153), thêm log:

```java
// TÌM DÒNG 176-179, thay bằng:
String url = withApiKey(builder).build().encode().toUriString();

// 👇 THÊM 3 DÒNG NÀY
log.info("==============================================");
log.info("🔗 OPENALEX URL (sinh từ code BE): {}", url);
log.info("==============================================");

try {
    OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);

    // 👇 THÊM 2 DÒNG NÀY
    log.info("📦 OPENALEX RESPONSE: total papers in OpenAlex = {}",
            response != null && response.getMeta() != null ? response.getMeta().getCount() : 0);
    log.info("📦 Papers fetched this call = {}",
            response != null && response.getResults() != null ? response.getResults().size() : 0);

    if (response == null || response.getResults() == null) return List.of();
    // ... phần còn lại giữ nguyên
```

### Khi chạy, console sẽ hiển thị:

```
==============================================
🔗 OPENALEX URL (sinh từ code BE): https://api.openalex.org/works?search=machine+learning&sort=cited_by_count:desc&per-page=50&select=id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships&mailto=you@example.com
==============================================
📦 OPENALEX RESPONSE: total papers in OpenAlex = 245000
📦 Papers fetched this call = 50
```

→ Mở console cho giảng viên xem là thấy ngay. Không cần debug, không cần Postman.

---

## LUỒNG CHI TIẾT: TỪ FE → BE → OPENALEX → JSON → DB

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. FE (React) hoặc Postman gửi request                             │
│    GET /api/v1/papers/search?query=machine+learning                 │
│    Header: Authorization: Bearer <jwt>                              │
└──────────────────────────┬──────────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. PaperSearchController.java                                       │
│    @GetMapping("/search") → orchestrator.searchByKeyword()          │
└──────────────────────────┬──────────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. PaperSearchOrchestrator.java (dòng 87-154)                       │
│    ├── Check cache 6h (ConcurrentHashMap)                           │
│    ├── Check quota (ACADEMIC_USER)                                  │
│    ├── Gọi OpenAlexFallbackSearchService.searchTopCited()  ←────────│
│    └── Gọi OpenAlexFallbackSearchService.searchNoYearFilter()       │
└──────────────────────────┬──────────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. OpenAlexFallbackSearchService.java (dòng 153-200)                │
│                                                                     │
│    🔗 URL GỌI ĐI (build từ code dòng 159-176):                      │
│    https://api.openalex.org/works?search=machine+learning&          │
│    sort=cited_by_count:desc&per-page=50&select=id,doi,title,...     │
│                                                                     │
│    restTemplate.getForObject(url, OpenAlexResponseDTO.class)  ←─────│
│                                                                     │
│    📦 JSON NHẬN VỀ: { meta: { count: 245000 }, results: [...] }    │
└──────────────────────────┬──────────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. Spring parse JSON → OpenAlexResponseDTO (tự động)                │
│    - results[].id → work.getId()                                    │
│    - results[].title → work.getTitle()                              │
│    - results[].cited_by_count → work.getCitedByCount()              │
│    - results[].abstract_inverted_index → rebuildAbstract()          │
│    - results[].keywords → mapKeywords()                             │
│    - results[].authorships → mapAuthors()                           │
└──────────────────────────┬──────────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 6. Save-on-search (async, fire-and-forget)                          │
│    ├── dataSyncService.saveWorksFromOpenAlexAsync(rawWorks)         │
│    │   ├── Lưu vào SQL Server: ResearchPaper, Author, Journal,      │
│    │   │   Keyword, PaperAuthor, PaperKeyword...                    │
│    │   └── Lưu vào Neo4j: (:Paper)-[:HAS_KEYWORD]->(:Keyword)      │
│    └── searchKeywordService.recordSearch() → SEARCH_KEYWORD table   │
└──────────────────────────┬──────────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 7. Trả về FE: PaperSearchResultDTO                                  │
│    { papers: [...], totalElements: 50, currentPage: 0 }             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## FILE CHAIN (Khi giảng viên hỏi "Dữ liệu đi qua những file nào?")

```
FE SearchPapers.jsx
  → PaperSearchController.java              (@GetMapping("/search"))
  → PaperSearchOrchestrator.java            (dòng 87: searchByKeyword())
  → OpenAlexFallbackSearchService.java      (dòng 153: searchTopCited())
    ├── dòng 159-166: UriComponentsBuilder build URL
    ├── dòng 176: withApiKey() — gắn mailto/api_key vào URL
    ├── dòng 179: restTemplate.getForObject() — GỌI HTTP ĐẾN OPENALEX ← key line
    ├── dòng 188: dataSyncService.saveWorksFromOpenAlexAsync() (async save DB)
    └── dòng 193-196: mapToPaper() → map sang PaperDetailResponseDTO
  → OpenAlexResponseDTO.java                (cấu trúc JSON mapping)
  → DataSyncServiceImpl.java                (lưu vào SQL Server + Neo4j)
  → GraphService.java                       (lưu quan hệ Paper-Keyword vào Neo4j)
```

---

## CẤU TRÚC JSON OPENALEX TRẢ VỀ

```json
{
  "meta": {
    "count": 245000,
    "per_page": 50
  },
  "results": [
    {
      "id": "https://openalex.org/W4295951495",
      "doi": "https://doi.org/10.1038/s41586-023-06291-2",
      "title": "Large language models in medicine",
      "publication_year": 2023,
      "publication_date": "2023-07-12",
      "cited_by_count": 1523,
      "abstract_inverted_index": {
        "Large": [0], "language": [1], "models": [2]
      },
      "open_access": { "is_oa": true, "oa_url": "https://..." },
      "primary_location": {
        "source": {
          "display_name": "Nature",
          "issn_l": "0028-0836",
          "publisher": "Springer Nature"
        }
      },
      "keywords": [
        { "display_name": "Large language models", "score": 0.98 }
      ],
      "authorships": [
        {
          "author": { "display_name": "John Smith" },
          "raw_affiliation_strings": ["Stanford University"],
          "institutions": [
            { "display_name": "Stanford University", "country_code": "US" }
          ]
        }
      ]
    }
  ]
}
```

### Mapping JSON → Database:

| JSON Field | → SQL Table | → Column |
|------------|------------|----------|
| `results[].id` | `ResearchPaper` | `openalex_id` |
| `results[].title` | `ResearchPaper` | `title` |
| `results[].doi` | `ResearchPaper` | `doi` |
| `results[].publication_year` | `ResearchPaper` | `pub_year` |
| `results[].cited_by_count` | `ResearchPaper` | `citation_count` |
| `results[].abstract_inverted_index` | BE reconstruct → | `abstract_text` |
| `results[].primary_location.source.display_name` | `Journal` | `name` |
| `results[].authorships[].author.display_name` | `Author` | `full_name` |
| `results[].keywords[].display_name` | `Keyword` | `keyword_text` |
| `meta.count` | Không lưu DB | Chỉ hiển thị |

### ⚠️ Abstract Inverted Index

OpenAlex **KHÔNG** trả abstract dạng text mà trả dạng "inverted index":

```json
"abstract_inverted_index": {
  "Large": [0],
  "language": [1],
  "models": [2]
}
```

→ BE reconstruct thành text: `"Large language models..."` (code ở dòng 764-775)

---

## CÂU TRẢ LỜI MẪU KHI GIẢNG VIÊN HỎI

### GV: "Em gọi API OpenAlex như thế nào? URL từ đâu ra?"

> **Trả lời:** Dạ URL được build từ code trong `OpenAlexFallbackSearchService.java` dòng 159-176. Em dùng `UriComponentsBuilder` của Spring để build URL với các tham số: `search` (từ khóa người dùng nhập), `sort` (cited_by_count:desc), `per-page` (50). Sau đó gọi bằng `RestTemplate.getForObject(url, OpenAlexResponseDTO.class)` ở dòng 179. Để chứng minh, em có thể debug cho thầy xem biến `url` ngay trong code.

### GV: "Anh muốn xem JSON OpenAlex trả về"

> **Trả lời:** Dạ em debug ở dòng 180, biến `response` chứa toàn bộ JSON đã parse. Có 2 phần: `meta` (tổng số papers = 245000) và `results` (mảng 50 papers). Mỗi paper có `title`, `doi`, `cited_by_count`, `abstract_inverted_index`, `keywords`, `authorships`. Em có thể expand từng field trong Variables panel cho thầy xem.

### GV: "Sao abstract nó lưu kiểu gì lạ vậy?"

> **Trả lời:** Dạ OpenAlex lưu abstract dạng inverted index — key là từ, value là mảng vị trí — để tiết kiệm dung lượng. BE em có hàm `rebuildAbstract()` ở dòng 764 để reconstruct lại thành text hoàn chỉnh trước khi lưu vào DB và trả về FE.

---

## TÓM TẮT NHANH (Để nhớ khi bảo vệ)

| Câu hỏi | Trả lời ngắn |
|---------|-------------|
| BE gọi OpenAlex bằng gì? | `RestTemplate.getForObject(url, OpenAlexResponseDTO.class)` |
| File nào build URL? | `OpenAlexFallbackSearchService.java` dòng 159-176 |
| File nào gọi API? | `OpenAlexFallbackSearchService.java` dòng 179 |
| JSON map vào class nào? | `OpenAlexResponseDTO.java` |
| Abstract lưu thế nào? | Inverted index → reconstruct thành text |
| Lưu vào DB nào? | SQL Server (JPA) + Neo4j (Graph) |
| Lưu sync hay async? | Async (fire-and-forget, không block response) |
| Chứng minh thế nào? | Debug dòng 176 xem URL → dòng 180 xem response |