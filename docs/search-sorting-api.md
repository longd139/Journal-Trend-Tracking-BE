# Search & Sorting API

> Tài liệu dành cho FE team. Các API search bài báo có hỗ trợ **user-controlled sorting** (người dùng tự chọn cách sắp xếp kết quả).

---

## Tính năng Sorting

Tất cả endpoint search/browse đều hỗ trợ 2 param mới:

| Param | Type | Default | Mô tả |
|-------|------|---------|-------|
| `sortBy` | String | `relevance` (search) / `date` (browse) | Trường sắp xếp |
| `sortDirection` | String | `desc` | Hướng sắp xếp: `asc` hoặc `desc` |

### Giá trị `sortBy`:

| sortBy | Ý nghĩa | Cột DB |
|--------|---------|--------|
| `relevance` | Độ liên quan (mặc định khi search) | Neo4j rank / full-text score |
| `citations` | Số lượng trích dẫn | `citationCount` |
| `title` | Tên bài báo (A-Z hoặc Z-A) | `title` |
| `date` | Ngày xuất bản (mặc định khi browse) | `pubDate` |

---

## 1. Browse All Papers

Lấy danh sách tất cả papers, không cần search keyword. Hỗ trợ phân trang + sorting.

### Request

```http
GET /api/v1/papers?page=0&size=20&sortBy=date&sortDirection=desc
Authorization: Bearer <jwt-token>
```

### Query Parameters

| Param | Type | Default | Mô tả |
|-------|------|---------|-------|
| `page` | int | `0` | Số trang (0-indexed) |
| `size` | int | `20` | Số lượng / trang (max 50) |
| `sortBy` | String | `date` | `date`, `citations`, `title` |
| `sortDirection` | String | `desc` | `asc` hoặc `desc` |

### Response

```json
{
  "code": 200,
  "message": "Papers retrieved",
  "data": {
    "papers": [
      {
        "paperId": "99c6a74b-b71d-4dc9-ae6d-8ca7bf7f51e3",
        "title": "The Influence of Social Networks...",
        "abstractText": "...",
        "doi": "10.3390/su162310787",
        "pubYear": 2024,
        "pubDate": "2024-12-09",
        "citationCount": 15,
        "isOpenAccess": true,
        "journalName": "Sustainability",
        "sourceUrl": "https://doi.org/10.3390/su162310787",
        "pdfAvailable": true,
        "pdfUrl": null,
        "keywords": [...],
        "createdAt": "2026-07-03T08:41:46"
      }
    ],
    "totalElements": 1250,
    "totalPages": 63,
    "currentPage": 0,
    "pageSize": 20,
    "hasNext": true,
    "hasPrev": false
  }
}
```

---

## 2. Search Papers

Tìm kiếm papers theo keyword. Mặc định dùng Neo4j graph search (nhanh, sort theo relevance). Khi chọn `sortBy` khác `relevance`, tự động chuyển sang SQL search (hỗ trợ Pageable Sort).

### Request

```http
GET /api/v1/papers/search?query=machine+learning&page=0&size=20&sortBy=citations&sortDirection=desc
Authorization: Bearer <jwt-token>
```

### Query Parameters

| Param | Type | Default | Mô tả |
|-------|------|---------|-------|
| `query` | String | **Required** | Từ khóa tìm kiếm |
| `authorName` | String | — | Lọc theo tên tác giả (optional) |
| `journalId` | String | — | Lọc theo journal UUID (optional) |
| `page` | int | `0` | Số trang |
| `size` | int | `10` | Số lượng / trang (max 50) |
| `sortBy` | String | `relevance` | `relevance`, `citations`, `title`, `date` |
| `sortDirection` | String | `desc` | `asc` hoặc `desc` |

### Response

Giống format `PaperSearchResultDTO` ở trên.

### Ví dụ:

```http
# Search "AI", sắp xếp theo citations nhiều nhất
GET /api/v1/papers/search?query=AI&sortBy=citations&sortDirection=desc

# Search "deep learning", sắp xếp theo title A-Z
GET /api/v1/papers/search?query=deep+learning&sortBy=title&sortDirection=asc

# Search "neural", relevance mặc định (Neo4j graph — nhanh nhất)
GET /api/v1/papers/search?query=neural
```

---

## 3. Search by Author

Tìm papers theo tên tác giả.

### Request

```http
GET /api/v1/papers/search/author?authorName=Smith&page=0&size=20&sortBy=date&sortDirection=desc
Authorization: Bearer <jwt-token>
```

### Query Parameters

| Param | Type | Default | Mô tả |
|-------|------|---------|-------|
| `authorName` | String | **Required** | Tên tác giả |
| `page` | int | `0` | Số trang |
| `size` | int | `10` | Số lượng / trang (max 50) |
| `sortBy` | String | `relevance` | `relevance`, `citations`, `title`, `date` |
| `sortDirection` | String | `desc` | `asc` hoặc `desc` |

---

## 4. Search by Journal

Tìm papers trong 1 journal cụ thể.

### Request

```http
GET /api/v1/papers/search/journal?journalId=<uuid>&page=0&size=20&sortBy=citations&sortDirection=desc
Authorization: Bearer <jwt-token>
```

### Query Parameters

| Param | Type | Default | Mô tả |
|-------|------|---------|-------|
| `journalId` | String | **Required** | UUID của journal |
| `page` | int | `0` | Số trang |
| `size` | int | `10` | Số lượng / trang (max 50) |
| `sortBy` | String | `relevance` | `relevance`, `citations`, `title`, `date` |
| `sortDirection` | String | `desc` | `asc` hoặc `desc` |

---

## 5. Advanced Filter (RESEARCHER+)

Tìm papers với nhiều bộ lọc nâng cao.

### Request

```http
GET /api/v1/papers/filter/advanced?pubYearFrom=2023&pubYearTo=2026&isOpenAccess=true&minCitations=10&page=0&size=20&sortBy=citations&sortDirection=desc
Authorization: Bearer <jwt-token>
```

### Query Parameters

| Param | Type | Default | Mô tả |
|-------|------|---------|-------|
| `pubYearFrom` | Short | — | Năm bắt đầu |
| `pubYearTo` | Short | — | Năm kết thúc |
| `fieldId` | String | — | UUID của research field |
| `journalId` | String | — | UUID của journal |
| `isOpenAccess` | Boolean | — | Chỉ lấy open access |
| `minCitations` | int | — | Số citations tối thiểu |
| `page` | int | `0` | Số trang |
| `size` | int | `10` | Số lượng / trang (max 50) |
| `sortBy` | String | `relevance` | `relevance`, `citations`, `title`, `date` |
| `sortDirection` | String | `desc` | `asc` hoặc `desc` |

> **Quyền:** Chỉ RESEARCHER và ADMIN mới dùng được advanced filter.

---

## Flow cho UI

```
┌──────────────────────────────────────────────────┐
│  Search Results Page                              │
│                                                   │
│  ┌─────────────────────────────────────────────┐  │
│  │ 🔍 [machine learning          ] [Search]    │  │
│  └─────────────────────────────────────────────┘  │
│                                                   │
│  Sort by: [Relevance ▼]  ← dropdown               │
│           ├ Relevance                             │
│           ├ Citations (most first)                │
│           ├ Title (A-Z)                           │
│           ├ Title (Z-A)                           │
│           ├ Date (newest first)                   │
│           └ Date (oldest first)                   │
│                                                   │
│  ┌─────────────────────────────────────────────┐  │
│  │ Result 1: Paper Title...    15 citations    │  │
│  │ Authors: Smith, J.  |  Journal: AI Rev     │  │
│  │ [Copy Citation ▼] [View Detail]             │  │
│  └─────────────────────────────────────────────┘  │
│  ...                                              │
│  ◀ Page 1 of 63  ▶                               │
└──────────────────────────────────────────────────┘
```

### Mapping UI → API:

| UI Sort Option | sortBy | sortDirection |
|----------------|--------|---------------|
| Relevance | `relevance` | `desc` |
| Citations (most first) | `citations` | `desc` |
| Citations (least first) | `citations` | `asc` |
| Title (A-Z) | `title` | `asc` |
| Title (Z-A) | `title` | `desc` |
| Date (newest first) | `date` | `desc` |
| Date (oldest first) | `date` | `asc` |

---

## Lưu ý cho FE

1. **`relevance` sort dùng Neo4j** — nhanh, không phân trang được trong 1 số trường hợp (trả về 1 page duy nhất từ orchestrator). Các sort khác dùng SQL — có phân trang đầy đủ.

2. **Khi chọn sort khác `relevance`**, search tự động chuyển từ Neo4j sang SQL. Kết quả có thể khác (SQL dùng LIKE, Neo4j dùng graph match).

3. **Browse (`GET /`) mặc định `date DESC`** — không có option `relevance` vì không search.

4. **Tất cả param đều optional trừ `query`** (với `/search`) và `authorName`/`journalId` (với endpoint tương ứng).
