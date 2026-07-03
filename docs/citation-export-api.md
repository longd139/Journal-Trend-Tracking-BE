# Citation Export API

> Tài liệu dành cho FE team làm UI. 2 API export citation (trích dẫn học thuật) cho bài báo.

---

## 1. Export Citation 1 Bài Báo

Lấy citation của 1 paper theo định dạng chỉ định. Trả về file text để download.

### Request

```http
GET /api/v1/papers/{paperId}/citation?format=bibtex
Authorization: Bearer <jwt-token>
```

### Path Parameters

| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| `paperId` | UUID | **Yes** | ID của bài báo |

### Query Parameters

| Param | Type | Required | Default | Mô tả |
|-------|------|----------|---------|-------|
| `format` | String | No | `bibtex` | Định dạng citation. Hỗ trợ: `bibtex`, `ris`, `apa`, `mla` |

### Response

**Success (200):** File text download với `Content-Disposition: attachment`.

```
Content-Type: text/plain
Content-Disposition: attachment; filename="citation-{paperId}.bib"
```

Ví dụ nội dung file với `format=bibtex`:
```bibtex
@article{jiang2024the,
  title = {The Influence of Social Networks on Tourism Support Behaviors Among Ethnic Village Residents},
  author = {Jiang, Yajun and Wu, Wei and Yu, Guo and Zhou, Huiling and Wu, Ke},
  journal = {Sustainability},
  year = {2024},
  doi = {10.3390/su162310787},
  url = {https://doi.org/10.3390/su162310787},
}
```

Ví dụ `format=apa`:
```
Jiang, Y., Wu, W., Yu, G., Zhou, H., & Wu, K. (2024). The Influence of Social Networks on Tourism Support Behaviors Among Ethnic Village Residents. <i>Sustainability</i>. https://doi.org/10.3390/su162310787
```

Ví dụ `format=mla`:
```
Jiang, Yajun, et al. "The Influence of Social Networks on Tourism Support Behaviors Among Ethnic Village Residents." <i>Sustainability</i>, 2024, doi:10.3390/su162310787.
```

Ví dụ `format=ris`:
```
TY  - JOUR
AU  - Jiang, Yajun
AU  - Wu, Wei
AU  - Yu, Guo
TI  - The Influence of Social Networks on Tourism Support Behaviors Among Ethnic Village Residents
JO  - Sustainability
PY  - 2024
DA  - 2024-12-09
DO  - 10.3390/su162310787
UR  - https://doi.org/10.3390/su162310787
SN  - 2071-1050
ER  - 
```

**Error (500):** Paper không tồn tại — server trả về HTTP 500 với message trong response body dạng text:
```
Paper not found: 550e8400-e29b-41d4-a716-446655440000
```

### File Extension Theo Format

| format | File Extension |
|--------|---------------|
| `bibtex` | `.bib` |
| `ris` | `.ris` |
| `apa` | `.txt` |
| `mla` | `.txt` |

---

## 2. Export Citations Nhiều Bài Báo (Bulk)

Gửi danh sách paper IDs, nhận về 1 file chứa tất cả citations nối tiếp nhau.

### Request

```http
POST /api/v1/papers/citations/export?format=bibtex
Authorization: Bearer <jwt-token>
Content-Type: application/json

["550e8400-e29b-41d4-a716-446655440000", "660e8400-e29b-41d4-a716-446655440001", "770e8400-..."]
```

### Query Parameters

| Param | Type | Required | Default | Mô tả |
|-------|------|----------|---------|-------|
| `format` | String | No | `bibtex` | Định dạng citation. Hỗ trợ: `bibtex`, `ris`, `apa`, `mla` |

### Request Body

Một JSON array chứa danh sách UUID (String) của các paper cần export.

```json
[
  "550e8400-e29b-41d4-a716-446655440000",
  "660e8400-e29b-41d4-a716-446655440001"
]
```

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| `[0..n]` | String (UUID) | **Yes** | ID của paper |

### Response

**Success (200):** File text download. Các citation được nối tiếp nhau, phân cách bởi 1 dòng trống.

```
Content-Type: text/plain
Content-Disposition: attachment; filename="citations-{count}papers.bib"
```

Ví dụ nội dung file với `format=bibtex` (2 papers):
```bibtex
@article{jiang2024the,
  title = {The Influence of Social Networks on Tourism Support Behaviors Among Ethnic Village Residents},
  author = {Jiang, Yajun and Wu, Wei and Yu, Guo and Zhou, Huiling and Wu, Ke},
  journal = {Sustainability},
  year = {2024},
  doi = {10.3390/su162310787},
  url = {https://doi.org/10.3390/su162310787},
}

@article{barral2024the,
  title = {The Nexus between Trade and Investment, ESG, and SDG},
  author = {Barral, Mark Anthony},
  year = {2024},
  doi = {10.62986/dp2024.28},
  url = {https://doi.org/10.62986/dp2024.28},
}
```

**Paper không tìm thấy:** Paper nào không tồn tại sẽ được thay bằng dòng comment:
```bibtex
% Paper not found: 550e8400-e29b-41d4-a716-446655440000
```

---

## Flow cho UI

```
┌──────────────────────────────────────────────────┐
│  Paper Detail Page                               │
│                                                  │
│  Title: Machine Learning Applications...         │
│  Authors: John Smith, Jane Doe                   │
│  Journal: Journal of AI Research, 2023           │
│  DOI: 10.1234/example                            │
│                                                  │
│  [Copy Citation ▼]  ← dropdown chọn format       │
│   ├ BibTeX                                       │
│   ├ RIS                                          │
│   ├ APA                                          │
│   └ MLA                                          │
│                                                  │
│  → Gọi GET /api/v1/papers/{paperId}/citation     │
│    ?format=<format-đã-chọn>                      │
│  → Nhận file text → tải về hoặc copy clipboard   │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│  Bookmarks / Search Results Page                 │
│                                                  │
│  [☑] Paper 1   ← checkbox chọn                   │
│  [☑] Paper 2                                     │
│  [☐] Paper 3                                     │
│                                                  │
│  [Export Citations ▼]  ← button dropdown format   │
│   ├ BibTeX                                       │
│   ├ RIS                                          │
│   ├ APA                                          │
│   └ MLA                                          │
│                                                  │
│  → Gọi POST /api/v1/papers/citations/export      │
│    ?format=<format-đã-chọn>                      │
│    body: ["id1", "id2"]                          │
│  → Nhận 1 file chứa tất cả citations             │
└──────────────────────────────────────────────────┘
```

## Lưu ý cho FE

1. **Response không phải JSON** — 2 API này trả về `Content-Type: text/plain`, FE cần xử lý như file download (dùng `blob` hoặc `<a download>`).

2. **Ví dụ fetch trong JS:**
```javascript
const response = await fetch(`/api/v1/papers/${paperId}/citation?format=bibtex`, {
  headers: { Authorization: `Bearer ${token}` }
});
const blob = await response.blob();
const url = URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url;
a.download = `citation-${paperId}.bib`;
a.click();
```

3. **Format mặc định là `bibtex`** — nếu không truyền `format` param.

4. **APA/MLA output chứa thẻ `<i>`** cho journal name (italic trong academic writing). FE có thể render HTML hoặc strip tag tùy nhu cầu.

5. **RIS có đầy đủ metadata** — bao gồm `DA` (publication date), `SN` (ISSN), `UR` (DOI link) nếu paper có dữ liệu. Không có volume/issue/pages vì schema DB hiện tại chưa lưu các trường này.
