# Reading History API — Frontend Integration Guide

## Overview

Reading History tracks every paper a user views (when they open paper detail). The BE automatically records each view — FE just needs to display the list.

**Flow:** User opens paper detail → BE auto-records → User visits Reading History page → FE calls API → displays list.

---

## API Endpoint

### `GET /api/v1/reading-history`

| | |
|---|---|
| **Method** | `GET` |
| **Auth** | Required (Bearer JWT) |
| **Rate Limit** | 60 req/min (authenticated tier) |

**Query params:**

| Param | Type | Default | Range | Mô tả |
|-------|------|---------|-------|-------|
| `limit` | int | `20` | `1–50` | Số lượng bài báo trả về (đã deduplicate) |

**Example request:**
```
GET /api/v1/reading-history?limit=20
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

---

## Response

### Success (200)

```json
{
  "status": 200,
  "message": "Reading history retrieved",
  "timestamp": "2026-07-05T18:00:00.123",
  "data": [
    {
      "readingHistoryId": "a1b2c3d4-...",
      "paperId": "e5f6a7b8-...",
      "paperTitle": "Deep Learning for Natural Language Processing",
      "pubYear": 2025,
      "journalName": "Nature Machine Intelligence",
      "doi": "10.1038/s42256-025-00123-4",
      "citationCount": 42,
      "isOpenAccess": true,
      "viewedAt": "2026-07-05T17:55:00"
    },
    {
      "readingHistoryId": "b2c3d4e5-...",
      "paperId": "f6a7b8c9-...",
      "paperTitle": "Graph Neural Networks: A Survey",
      "pubYear": 2024,
      "journalName": "IEEE Transactions on Pattern Analysis",
      "doi": "10.1109/TPAMI.2024.3456789",
      "citationCount": 128,
      "isOpenAccess": false,
      "viewedAt": "2026-07-05T16:30:00"
    }
  ]
}
```

### Field descriptions

| Field | Type | Nullable | Mô tả |
|-------|------|----------|-------|
| `readingHistoryId` | UUID | No | ID của bản ghi (dùng cho delete nếu cần sau này) |
| `paperId` | UUID | No | ID của paper — dùng để navigate sang paper detail |
| `paperTitle` | String | No | Tiêu đề bài báo |
| `pubYear` | Integer | Yes | Năm xuất bản |
| `journalName` | String | Yes | Tên tạp chí |
| `doi` | String | Yes | DOI link |
| `citationCount` | Integer | Yes | Số lượt trích dẫn |
| `isOpenAccess` | Boolean | Yes | Có phải Open Access không |
| `viewedAt` | DateTime | No | Thời điểm user xem paper này |

### Error responses

**401 — Unauthorized:**
```json
{
  "status": 401,
  "message": "Full authentication is required to access this resource",
  "errors": null,
  "timestamp": "2026-07-05T18:00:00"
}
```

**429 — Rate Limited:**
```json
{
  "status": 429,
  "message": "Too many requests. Please wait 18 seconds before retrying.",
  "errors": null,
  "timestamp": "2026-07-05T18:00:00"
}
```
Header: `Retry-After: 18`

---

## Frontend Integration

### 1. API call

```js
import { paperAPI } from '@/features/search/paper.api.js';

// Đã có sẵn trong paper.api.js:
const res = await paperAPI.getReadingHistory(20);
const history = res.data; // ReadingHistoryResponse[]
```

### 2. Component structure (gợi ý)

```
src/features/history/
├── ReadingHistoryPage.jsx    ← trang chính
└── ReadingHistoryItem.jsx    ← 1 item trong list (optional)
```

### 3. Trạng thái UI

| State | Hiển thị |
|-------|----------|
| **Loading** | Skeleton / spinner |
| **Empty** | "No papers viewed yet" + icon + CTA "Start exploring" → link sang search |
| **Error** | Toast / inline error + nút Retry |
| **Data** | Danh sách paper đã xem, mới nhất trên cùng |

### 4. Thiết kế mỗi item (gợi ý)

```
┌─────────────────────────────────────────────────────┐
│ 📄 Deep Learning for Natural Language Processing    │  ← paperTitle (click → navigate paper detail)
│    Nature Machine Intelligence · 2025               │  ← journalName + pubYear
│    DOI: 10.1038/...  ·  Cited: 42  ·  Open Access   │  ← doi + citationCount + isOpenAccess badge
│    Viewed: July 5, 2026 at 5:55 PM                  │  ← viewedAt (relative time: "2 hours ago")
└─────────────────────────────────────────────────────┘
```

### 5. Các action có thể làm

| Action | Cách làm |
|--------|----------|
| Click vào title | `navigate('/papers/' + paperId)` |
| Click vào DOI | `window.open('https://doi.org/' + doi)` |
| Click vào journal | `navigate('/journals/' + journalId)` (nếu có journal page) |

### 6. Edge cases cần xử lý

| Case | Cách xử lý |
|------|------------|
| `pubYear` = null | Không hiển thị năm |
| `journalName` = null | Hiển thị "Unknown Journal" hoặc ẩn |
| `doi` = null | Ẩn dòng DOI |
| `citationCount` = null | Hiển thị "—" |
| `isOpenAccess` = null/false | Không hiển thị badge Open Access |
| Deduplicate | API đã dedup sẵn — mỗi paper chỉ xuất hiện 1 lần |
| API lỗi mạng | Hiển thị trạng thái lỗi + nút Retry |
| Token hết hạn | Axios interceptor tự redirect về login |

### 7. i18n keys cần thêm (gợi ý)

```json
// en/history.json
{
  "title": "Reading History",
  "empty": "No papers viewed yet",
  "emptyCTA": "Start exploring",
  "viewed": "Viewed",
  "retry": "Retry",
  "error": "Failed to load reading history"
}
```

---

## Notes

- **Tự động ghi nhận**: Mỗi lần user gọi `GET /api/v1/papers/{paperId}` → BE tự lưu, FE không cần làm gì thêm
- **Deduplicate**: API đã dedup theo `paperId` — user xem 1 paper 5 lần → list chỉ hiện 1 lần (lần mới nhất)
- **Prune**: Hệ thống tự xóa entry cũ khi vượt quá 200 paper/user
- **Không cần pagination**: API trả về list phẳng, FE cứ hiển thị hết
- **Refresh**: Nên gọi lại API mỗi khi user vào trang (để hiển thị dữ liệu mới nhất)
