# Search Keyword — API Documentation

## Tổng quan

Khi researcher gõ keyword vào ô search, FE gọi các API song song để hiển thị 4 component trên trang kết quả. Mỗi component phục vụ 1 mục đích khác nhau giúp researcher hiểu toàn diện về chủ đề đang tìm.

---

## 1. Trước khi search (page load)

| API | Mục đích | Ý nghĩa với người dùng |
|-----|----------|------------------------|
| `GET /api/public/keywords/hot` | WeeklyBreakout — trending keywords tuần này | Gợi ý chủ đề đang hot để researcher khám phá ngay khi chưa biết search gì. Giống "Trending Now" trên Twitter. |

---

## 2. Sau khi search (4 API song song)

### 2.1 KeywordQuickStats

| | |
|---|---|
| **API** | `GET /api/search/keyword/quick-stats?keyword=` |
| **Component** | `KeywordQuickStats` |
| **Mục đích** | Thống kê tổng quan về keyword |
| **Dữ liệu trả về** | `totalPapers`, `totalCitations`, `recentGrowth`, `topAuthors`, `topJournals` |
| **Ý nghĩa** | Trả lời câu hỏi: **"Chủ đề này lớn cỡ nào? Có đáng quan tâm không?"**. Researcher nhìn 1 cái biết ngay: có bao nhiêu paper, tổng citations, trend tăng/giảm, ai đang dẫn đầu, journal nào publish nhiều nhất. |

### 2.2 KeywordGraphExplorer

| | |
|---|---|
| **API** | `POST /api/graphs/keyword/search?keyword=&depth=3` |
| **Component** | `KeywordGraphExplorer` |
| **Mục đích** | Đồ thị mạng lưới keyword liên quan |
| **Dữ liệu trả về** | Nodes (keywords) + Edges (mối quan hệ) — dạng graph |
| **Ý nghĩa** | Trả lời câu hỏi: **"Còn chủ đề nào liên quan mà tôi chưa nghĩ ra?"**. Hiển thị mạng lưới keyword dưới dạng đồ thị trực quan, giúp researcher khám phá các nhánh nghiên cứu liên quan. Depth=3 nghĩa là mở rộng 3 cấp độ sâu. |

### 2.3 RelatedTrends

| | |
|---|---|
| **API** | `GET /api/search/keyword/related-trends?keyword=` |
| **Component** | `RelatedTrends` |
| **Mục đích** | Trend của keyword theo thời gian |
| **Dữ liệu trả về** | Sparkline chart data (citations theo từng tháng/năm) |
| **Ý nghĩa** | Trả lời câu hỏi: **"Chủ đề này đang lên hay đang xuống?"**. Biểu đồ trend giúp researcher quyết định có nên đầu tư thời gian vào chủ đề này không. |

### 2.4 TopPapers

| | |
|---|---|
| **API** | `GET /api/search/keyword/top-papers?keyword=` |
| **Component** | `TopPapers` |
| **Mục đích** | Top 5 paper được cite nhiều nhất về keyword này |
| **Dữ liệu trả về** | List 5 `PaperDetailResponseDTO` (title, authors, citations, journal, abstract...) |
| **Ý nghĩa** | Trả lời câu hỏi: **"Đâu là những paper quan trọng nhất về chủ đề này?"**. Researcher có thể click vào từng paper để xem chi tiết, đọc abstract, hoặc bookmark. Đây là "bắt đầu từ đâu" cho 1 chủ đề mới. |

---

## 3. Luồng dữ liệu

```
Researcher gõ "machine learning" → Enter
    │
    ├── quick-stats    → OpenAlex API → trả stats tổng quan
    ├── keyword/search → Neo4j graph  → trả mạng lưới keyword
    ├── related-trends → OpenAlex API → trả sparkline chart
    └── top-papers     → OpenAlex API → trả top 5 paper
```

Tất cả 4 API gọi song song → FE hiển thị đồng thời 4 component trong ~1-2 giây.

---

## 4. Nguồn dữ liệu

| API | Nguồn chính | Cache |
|-----|------------|-------|
| `quick-stats` | OpenAlex `/works?search=` | searchCacheManager (7 ngày) |
| `keyword/search` | Neo4j graph database | Không cache |
| `related-trends` | OpenAlex `/works?search=` | searchCacheManager (7 ngày) |
| `top-papers` | OpenAlex `/works?search=` + PAPER_CACHE | PAPER_CACHE (7 ngày) |
