# Dữ liệu cào từ OpenAlex API — Field Mapping cho SCITRACK

Tài liệu này mô tả toàn bộ field dữ liệu được cào từ OpenAlex API, ánh xạ vào schema database của hệ thống SCITRACK. Tool scraper nằm tại `tools/openalex-scraper/scraper.js`.

---

## 1. Research Paper — Bảng `RESEARCH_PAPER`

**Nguồn API**: `GET https://api.openalex.org/works?search=<keyword>`

### 1.1 Core Fields (ánh xạ trực tiếp vào DB)

| # | JSON Output Field | OpenAlex API Source | DB Column | Type | Ghi chú |
|---|-------------------|---------------------|-----------|------|---------|
| 1 | `openAlexId` | `id` | *(reference)* | VARCHAR | VD: `https://openalex.org/W4285710527` |
| 2 | `title` | `display_name` \| `title` | `Title` | `NVARCHAR(1000)` | Cắt về 1000 ký tự nếu dài hơn |
| 3 | `abstract` | `abstract_inverted_index` | `Abstract` | `NVARCHAR(MAX)` | Giải mã từ inverted index → text |
| 4 | `doi` | `doi` | `DOI` | `VARCHAR(200) UNIQUE` | Chuẩn hóa về `https://doi.org/...` |
| 5 | `publicationDate` | `publication_date` | `PubDate` | `DATE` | `YYYY-MM-DD` |
| 6 | `publicationYear` | `publication_year` | `PubYear` | `SMALLINT` | 4 chữ số |
| 7 | `citationCount` | `cited_by_count` | `CitationCount` | `INT DEFAULT 0` | Số lượt trích dẫn |
| 8 | `isOpenAccess` | `open_access.is_oa` | `IsOpenAccess` | `BIT DEFAULT 0` | |
| 9 | `pdfUrl` | `best_oa_location.pdf_url` | `PdfUrl` | `VARCHAR(500)` | Ưu tiên `best_oa_location`, fallback `primary_location` |
| 10 | `landingPageUrl` | `primary_location.landing_page_url` | *(extra)* | VARCHAR | Link đến trang paper trên site publisher |
| 11 | `type` | `type` | *(extra)* | VARCHAR | `"article"`, `"preprint"`, `"book-chapter"`, … |
| 12 | `referencedWorksCount` | `referenced_works_count` | *(extra)* | INT | Số reference paper này trích dẫn |

### 1.2 Nested: Journal → Bảng `JOURNAL`

| # | JSON Output Field | OpenAlex Source | DB Column | Type |
|---|-------------------|-----------------|-----------|------|
| 13 | `journal.openAlexId` | `primary_location.source.id` | *(reference)* | VARCHAR |
| 14 | `journal.name` | `primary_location.source.display_name` | `JournalName` | `NVARCHAR(500)` |
| 15 | `journal.issn` | `primary_location.source.issn_l` | `ISSN` | `VARCHAR(20) UNIQUE` |
| 16 | `journal.publisher` | `primary_location.source.publisher` \| `host_organization_name` | `Publisher` | `NVARCHAR(300)` |

### 1.3 Nested: Authors → Bảng `AUTHOR` + `PAPER_AUTHOR`

| # | JSON Output Field | OpenAlex Source | DB Column | Type |
|---|-------------------|-----------------|-----------|------|
| 17 | `authors[].openAlexId` | `authorships[].author.id` | `AUTHOR.ExternalAuthorID` | `VARCHAR(200)` |
| 18 | `authors[].name` | `authorships[].author.display_name` \| `raw_author_name` | `AUTHOR.FullName` | `NVARCHAR(300)` |
| 19 | `authors[].rawAuthorName` | `authorships[].raw_author_name` | *(extra — original raw string)* | VARCHAR |
| 20 | `authors[].affiliations` | `authorships[].raw_affiliation_strings[]` | `AUTHOR.Affiliation` | `NVARCHAR(500)` |
| 21 | `authors[].authorOrder` | *(index in array, 1-based)* | `PAPER_AUTHOR.AuthorOrder` | INT |
| 22 | `authors[].isCorresponding` | *(không có từ OpenAlex v1)* | `PAPER_AUTHOR.IsCorresponding` | BIT |

> ⚠ Giới hạn tối đa **5 authors** mỗi paper (theo `MAX_AUTHORS_PER_PAPER` trong DataSyncServiceImpl).

### 1.4 Nested: Keywords → Bảng `KEYWORD` + `PAPER_KEYWORD`

| # | JSON Output Field | OpenAlex Source | DB Column | Type |
|---|-------------------|-----------------|-----------|------|
| 23 | `keywords[].openAlexId` | `keywords[].id` | *(reference)* | VARCHAR |
| 24 | `keywords[].keyword` | `keywords[].display_name` | `KEYWORD.KeywordText` | `NVARCHAR(300)` |
| 25 | `keywords[].score` | `keywords[].score` | `PAPER_KEYWORD.RelevanceScore` | FLOAT |
| 26 | `keywords[].source` | *(derived)* | *(phân biệt keyword vs topic)* | `"keyword"` / `"topic"` |

> ⚠ Giới hạn tối đa **8 keywords** mỗi paper. Tool tự động merge keywords + top 3 topics, deduplicate theo lowercase.

### 1.5 Nested: Topics & Research Field

| # | JSON Output Field | OpenAlex Source | DB Column | Type |
|---|-------------------|-----------------|-----------|------|
| 27 | `topics[].openAlexId` | `topics[].id` | *(reference)* | VARCHAR |
| 28 | `topics[].name` | `topics[].display_name` | *(analytics)* | VARCHAR |
| 29 | `topics[].score` | `topics[].score` | *(analytics)* | FLOAT |
| 30 | `topics[].subfield` | `topics[].subfield.display_name` | *(analytics)* | VARCHAR |
| 31 | `topics[].field` | `topics[].field.display_name` | `RESEARCH_FIELD.FieldName` | `NVARCHAR(200)` |
| 32 | `topics[].domain` | `topics[].domain.display_name` | *(analytics)* | VARCHAR |
| 33 | `researchField.name` | `topics[0].field.display_name` | `RESEARCH_FIELD.FieldName` | `NVARCHAR(200)` |
| 34 | `researchField.subfield` | `topics[0].subfield.display_name` | *(analytics)* | VARCHAR |
| 35 | `researchField.domain` | `topics[0].domain.display_name` | *(analytics)* | VARCHAR |

---

## 2. Author — Bảng `AUTHOR`

**Nguồn API**: `GET https://api.openalex.org/authors?filter=display_name.search:<name>`

> ⚠ **Quan trọng**: Sử dụng `filter=display_name.search:` thay vì `search=` để tránh lỗi **504 Gateway Timeout** với tên có dấu chấm / viết tắt (VD: `"Ahmed O. M. Bahageel"`). Xem [AuthorQuickStatsService fix](#related-fix).

### 2.1 Core DB Fields

| # | JSON Output Field | OpenAlex Source | DB Column | Type |
|---|-------------------|-----------------|-----------|------|
| 1 | `openAlexId` | `id` | *(reference)* | VARCHAR |
| 2 | `externalAuthorId` | `id` | `ExternalAuthorID` | `VARCHAR(200)` |
| 3 | `fullName` | `display_name` | `FullName` | `NVARCHAR(300) NOT NULL` |
| 4 | `affiliation` | `last_known_institution.display_name` | `Affiliation` | `NVARCHAR(500)` |
| 5 | `institutionType` | `last_known_institution.type` | *(analytics)* | `"education"`, `"government"`, … |
| 6 | `hIndex` | `summary_stats.h_index` \| `h_index` | `HIndex` | `INT DEFAULT 0` |
| 7 | `totalCitations` | `cited_by_count` | `TotalCitations` | `INT DEFAULT 0` |

### 2.2 Extended Metadata (analytics / charts)

| # | JSON Output Field | OpenAlex Source | Dùng cho |
|---|-------------------|-----------------|----------|
| 8 | `worksCount` | `works_count` | Số paper đã publish |
| 9 | `i10Index` | `summary_stats.i10_index` \| `i10_index` | Chỉ số i10 |
| 10 | `twoYearMeanCitedness` | `summary_stats.2yr_mean_citedness` | Trung bình citation 2 năm gần nhất |
| 11 | `orcid` | `orcid` | ORCID identifier (đã strip prefix URL) |

### 2.3 Timeline Data (Bar + Line Chart)

| # | JSON Output Field | OpenAlex Source | Dùng cho |
|---|-------------------|-----------------|----------|
| 12 | `countsByYear[].year` | `counts_by_year[].year` | Trục X (năm) |
| 13 | `countsByYear[].worksCount` | `counts_by_year[].works_count` | Bar chart (số paper/năm) |
| 14 | `countsByYear[].oaWorksCount` | `counts_by_year[].oa_works_count` | Open access paper count |
| 15 | `countsByYear[].citedByCount` | `counts_by_year[].cited_by_count` | Line chart (citation/năm) |

### 2.4 Research Topics (Pie Chart / Treemap)

| # | JSON Output Field | OpenAlex Source | Dùng cho |
|---|-------------------|-----------------|----------|
| 16 | `topics[].openAlexId` | `topics[].id` | Reference |
| 17 | `topics[].name` | `topics[].display_name` | Label |
| 18 | `topics[].count` | `topics[].count` | Giá trị (% phân bổ) |
| 19 | `topics[].subfield` | `topics[].subfield.display_name` | Category level 2 |
| 20 | `topics[].field` | `topics[].field.display_name` | Category level 1 |
| 21 | `topics[].domain` | `topics[].domain.display_name` | Category level 0 |

---

## 3. Journal — Bảng `JOURNAL`

**Nguồn API**: `GET https://api.openalex.org/sources?search=<keyword>`

> ⚠ `/sources` endpoint trả về tất cả các loại nguồn (journal, repository, conference, ebook platform...). Tool tự động lọc chỉ giữ `type === "journal"`.

### 3.1 Core DB Fields

| # | JSON Output Field | OpenAlex Source | DB Column | Type |
|---|-------------------|-----------------|-----------|------|
| 1 | `openAlexId` | `id` | *(reference)* | VARCHAR |
| 2 | `journalName` | `display_name` | `JournalName` | `NVARCHAR(500) NOT NULL` |
| 3 | `issn` | `issn_l` | `ISSN` | `VARCHAR(20) UNIQUE` |
| 4 | `publisher` | `publisher` \| `host_organization_name` | `Publisher` | `NVARCHAR(300)` |
| 5 | `isActive` | *(derived: `works_count > 0`)* | `IsActive` | `BIT DEFAULT 1` |
| 6 | `type` | `type` | *(filter criteria)* | `"journal"` |
| 7 | `worksCount` | `works_count` | *(analytics)* | INT |
| 8 | `citedByCount` | `cited_by_count` | *(analytics)* | INT |
| 9 | `homePageUrl` | `homepage_url` | *(extra)* | VARCHAR |
| 10 | `alternateTitles` | `alternate_titles[]` | *(extra)* | JSON array |
| 11 | `countries` | `country_codes[]` | *(extra)* | JSON array |

### 3.2 Timeline Data (Bar + Line Chart)

| # | JSON Output Field | OpenAlex Source |
|---|-------------------|-----------------|
| 12 | `countsByYear[].year` | `counts_by_year[].year` |
| 13 | `countsByYear[].worksCount` | `counts_by_year[].works_count` |
| 14 | `countsByYear[].oaWorksCount` | `counts_by_year[].oa_works_count` |
| 15 | `countsByYear[].citedByCount` | `counts_by_year[].cited_by_count` |

### 3.3 Topics (Category Distribution)

| # | JSON Output Field | OpenAlex Source |
|---|-------------------|-----------------|
| 16 | `topics[].openAlexId` | `topics[].id` |
| 17 | `topics[].name` | `topics[].display_name` |
| 18 | `topics[].count` | `topics[].works_count` |
| 19 | `topics[].subfield` | `topics[].subfield.display_name` |
| 20 | `topics[].field` | `topics[].field.display_name` |
| 21 | `topics[].domain` | `topics[].domain.display_name` |

> ⚠ **Thiếu từ OpenAlex**: `ImpactFactor` và `Quartile` — KHÔNG có trong OpenAlex API. Nếu cần, phải scrape thêm từ **Scopus**, **Web of Science**, hoặc **SCImago Journal Rank (SJR)**.

---

## 4. Related tables (hệ thống tự tạo)

Các bảng sau không cần scrape từ OpenAlex — hệ thống tự sinh trong quá trình import:

| Bảng | Cách tạo |
|------|----------|
| `API_SOURCE` | Sinh từ config: `sourceName = "openalex"`, `baseUrl = "https://api.openalex.org"` |
| `PAPER_AUTHOR` | Liên kết M2M giữa `RESEARCH_PAPER` và `AUTHOR`, lưu `AuthorOrder` |
| `PAPER_KEYWORD` | Liên kết M2M giữa `RESEARCH_PAPER` và `KEYWORD`, lưu `RelevanceScore` |
| `RESEARCH_FIELD` | Từ `topics[0].field` của paper, có self-referencing `ParentFieldID` |
| `KEYWORD` | Từ `keywords[]` của paper, có `NormalizedText` (lowercase, trimmed) |
| `SYNC_LOG` | Mỗi lần scrape → 1 record: `SourceID`, `PapersFetched`, `PapersInserted`, `Status` |

---

## 5. Known Issues & Edge Cases

### 5.1 Author name có dấu chấm → 504 Timeout

**Problem**: OpenAlex `search=` param parser xử lý dấu chấm (`.`) trong tên viết tắt như regex operator, gây query quá rộng → `query_timeout`.

```
❌ GET /authors?search=Ahmed+O.+M.+Bahageel       → 504
✅ GET /authors?filter=display_name.search:Ahmed O. M. Bahageel  → 200 (1.3s)
```

**Fix**: `AuthorQuickStatsService.java` và `scraper.js` đều đã dùng `filter=display_name.search:`.

### 5.2 Abstract dạng inverted index

OpenAlex trả abstract dưới dạng `{"word": [positions]}` để tiết kiệm bandwidth. Tool tự động rebuild thành text:

```js
// Input:  { "machine": [0], "learning": [1], "is": [2], "fun": [3] }
// Output: "machine learning is fun"
```

### 5.3 Journal type filter

`/sources` trả về journal, repository, conference, ebook platform. Tool lọc `type === "journal"` — số lượng bị lọc sẽ được log ra console.

### 5.4 Rate Limit

| Pool | Limit |
|------|-------|
| Không email | ~10 req/s |
| Có email (polite pool) | ~100k req/day |

Email được set trong `MAILTO` constant (line 23 của `scraper.js`).

---

## 6. Output JSON Structure

```json
{
  "query": "<search keyword>",
  "total": 50,
  "results": [
    {
      // ===== PAPER =====
      "openAlexId": "https://openalex.org/W4285710527",
      "type": "article",
      "title": "Deep Learning for Image Recognition",
      "abstract": "This paper presents a novel approach...",
      "doi": "https://doi.org/10.1000/xyz123",
      "publicationDate": "2024-06-15",
      "publicationYear": 2024,
      "citationCount": 142,
      "isOpenAccess": true,
      "pdfUrl": "https://example.com/paper.pdf",
      "landingPageUrl": "https://doi.org/10.1000/xyz123",
      "referencedWorksCount": 35,

      "journal": {
        "openAlexId": "https://openalex.org/S1234567890",
        "name": "Journal of Machine Learning Research",
        "issn": "1532-4435",
        "publisher": "MIT Press"
      },

      "authors": [
        {
          "openAlexId": "https://openalex.org/A5093587645",
          "name": "Ahmed O. M. Bahageel",
          "rawAuthorName": "Ahmed Osman. M. Bahageel",
          "affiliations": ["Wuhan University"],
          "authorOrder": 1,
          "isCorresponding": false
        }
      ],

      "keywords": [
        { "openAlexId": "https://openalex.org/keywords/deep-learning", "keyword": "Deep Learning", "score": 0.98 },
        { "openAlexId": null, "keyword": "Image Recognition", "score": 0.85, "source": "topic" }
      ],

      "topics": [
        {
          "openAlexId": "https://openalex.org/T11658",
          "name": "Deep Learning",
          "score": 0.98,
          "subfield": "Artificial Intelligence",
          "field": "Computer Science",
          "domain": "Physical Sciences"
        }
      ],

      "researchField": {
        "openAlexId": "https://openalex.org/fields/17",
        "name": "Computer Science",
        "subfield": "Artificial Intelligence",
        "domain": "Physical Sciences"
      },

      // ===== AUTHOR =====
      "fullName": "Ahmed O. M. Bahageel",
      "externalAuthorId": "https://openalex.org/A5093587645",
      "affiliation": "Wuhan University",
      "institutionType": "education",
      "hIndex": 2,
      "totalCitations": 13,
      "worksCount": 7,
      "i10Index": 0,
      "twoYearMeanCitedness": 2.17,
      "orcid": "0000-0001-2345-6789",
      "countsByYear": [
        { "year": 2023, "worksCount": 1, "oaWorksCount": 1, "citedByCount": 0 },
        { "year": 2024, "worksCount": 5, "oaWorksCount": 1, "citedByCount": 12 }
      ],
      "authorTopics": [
        {
          "openAlexId": "https://openalex.org/T11052",
          "name": "Energy Load and Power Forecasting",
          "count": 6,
          "subfield": "Electrical and Electronic Engineering",
          "field": "Engineering",
          "domain": "Physical Sciences"
        }
      ],

      // ===== JOURNAL =====
      "journalName": "Journal of Machine Learning Research",
      "issn": "1532-4435",
      "publisher": "MIT Press",
      "isActive": true,
      "journalType": "journal",
      "homePageUrl": "https://www.jmlr.org",
      "alternateTitles": ["JMLR", "J. Mach. Learn. Res."],
      "countries": ["US"],
      "journalCountsByYear": [
        { "year": 2023, "worksCount": 250, "oaWorksCount": 180, "citedByCount": 5000 }
      ],
      "journalTopics": [
        {
          "openAlexId": "https://openalex.org/T11658",
          "name": "Deep Learning",
          "count": 1200,
          "subfield": "Artificial Intelligence",
          "field": "Computer Science",
          "domain": "Physical Sciences"
        }
      ]
    }
  ]
}
```

---

## Related Fix

`AuthorQuickStatsService.buildUrl()` — Đã sửa lỗi 504 timeout bằng cách chuyển từ `search=` sang `filter=display_name.search:` (commit trên branch `develop`).

Xem thêm: `tools/openalex-scraper/README.md` để biết cách sử dụng tool.
