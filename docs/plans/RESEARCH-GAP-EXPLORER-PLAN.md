# SCITRACK — Research Gap Explorer: Implementation Plan

> **Ngày:** 2026-07-19 | **Cập nhật cuối:** 2026-07-19
> **Mục tiêu:** Biến SCITRACK từ "OpenAlex mirror" thành nền tảng khám phá research gap qua Neo4j graph + MCP Chatbot
> **Trạng thái:** PLAN — đã thống nhất kiến trúc, chưa code

---

## 1. VẤN ĐỀ & GIẢI PHÁP

### Vấn đề cốt lõi
Hệ thống hiện tại là OpenAlex mirror — fetch data, lưu DB, hiển thị. Không có value-add.

### Giải pháp: Research Gap Explorer

**Crawl trước** 2 lĩnh vực × 3 năm → lưu SQL + Neo4j Local. Người dùng khám phá research gap qua **3-stage flow**, được dẫn dắt bởi **MCP Chatbot** và trực quan hóa qua **Neo4j Graph**:

| Stage | Tên | Graph hiển thị | Người dùng làm gì |
|-------|-----|---------------|-------------------|
| 1 | **"Bản đồ tri thức"** | Hierarchy: Year → Field → Topic → Keywords (node size = paper count) | Khám phá tự do, click node để highlight neighbors |
| 2 | **"Thu hẹp theo ý tưởng"** | Chỉ keywords liên quan đến ý tưởng + papers của chúng | Mô tả ý tưởng trong Chatbot → AI gợi ý cặp keyword (chỉ từ data có sẵn) |
| 3 | **"Focus Gap"** | 2 keywords + toàn bộ Papers/Datasets/Metrics/Methods/Authors | Xem cross-dimension gap analysis + AI Roadmap |

### Click Interaction (mọi stage)
- **Click 1 node** → node đó + neighbors sáng lên (opacity 100%), phần còn lại mờ đi (opacity 20%)
- **Click vùng trống** → reset về trạng thái cũ
- **Double-click** → zoom fit vào node + neighbors

---

## 2. KIẾN TRÚC DỮ LIỆU

### 2.1 Nguồn dữ liệu

- Crawl **2 lĩnh vực**: Computer Science + AI
- **3 năm gần đây** (2024, 2025, 2026) từ OpenAlex
- **Chỉ lấy paper chất lượng** qua Quality Score Filter
- ~300-500 papers chất lượng cao → dữ liệu sạch, gap analysis chính xác hơn

### 2.2 Quality Score Filter

**Vấn đề:** OpenAlex trả về hàng nghìn papers, nhiều paper rác — 0 citation, tạp chí lạ, abstract rỗng. Không nên enrich hết.

**Giải pháp:** Mỗi paper được chấm 1 điểm chất lượng (0-100). Chỉ lấy paper ≥ 40 điểm.

| Tiêu chí | Điểm | Giải thích |
|----------|------|------------|
| **Citation ≥ 50** | +30 | Paper có impact lớn |
| **Citation 20-49** | +20 | Impact trung bình |
| **Citation 5-19** | +10 | Có được cite |
| **Citation < 5** | 0 | Chưa có impact |
| **Journal Q1** | +25 | Đăng tạp chí top (dùng SCImago data) |
| **Journal Q2** | +15 | |
| **Journal Q3** | +5 | |
| **"journal-article"** | +15 | Bài báo chính thức |
| **"proceedings-article"** | +10 | Conference paper |
| **Open Access** | +15 | Lấy được full-text để enrich |
| **Có abstract đầy đủ** (>200 từ) | +15 | Đủ text để AI extract |
| **Có DOI** | +5 | Paper thật, có thể verify |

**Phân loại:**
| Score | Nhãn | Tỉ lệ | Dùng cho |
|-------|------|-------|----------|
| 70-100 | ⭐⭐⭐ Influential | ~25% | Gap analysis chính |
| 40-69 | ⭐⭐ Solid | ~50% | Gap analysis bổ trợ |
| <40 | ⭐ Low-quality | ~25% | **Bỏ qua** |

Ví dụ cụ thể:
```
Paper A: 120 cites (30) + Q1 (25) + journal (15) + OA (15) + abstract (15) + DOI (5)
         = 105 điểm ✅⭐⭐⭐ Influential → LẤY, enrich full-text

Paper C: 0 cites (0) + unknown journal (0) + no DOI (0)
         = 0 điểm ❌ → BỎ
```

Điểm sàn 40 có thể điều chỉnh sau khi test thực tế.

### 2.3 Enrichment: 2-tier (Abstract + Full-text)

```
Với mỗi paper đã qua quality filter:

1. Abstract → LUÔN extract (100% coverage)
   ↓
2. Nếu is_oa = true VÀ có oa_url
   → Download PDF → PDFBox extract full-text
   → Extract BỔ SUNG (metrics/datasets/methods chi tiết hơn)
   ↓
3. Nếu is_oa = false
   → Dùng kết quả từ abstract
   → Vẫn tham gia gap analysis bình thường
```

**Prompt nghiêm ngặt — không cho AI "bịa":**
```
Extract ONLY what is EXPLICITLY stated in the abstract.
If something is not clearly mentioned, return empty array.
DO NOT guess or infer.

Examples:
  "evaluated on CIFAR-10, achieving 95.2% accuracy"
  → ✅ datasets: [{"name":"CIFAR-10"}], metrics: [{"name":"Accuracy","value":"95.2%"}]
  
  "our method outperforms existing approaches"
  → ❌ datasets: [], metrics: []

Respond with ONLY: {"metrics":[...], "datasets":[...], "methods":[...]}
```

**Kết quả dự kiến (~400 papers chất lượng cao):**

| | Abstract | + Full-text (nếu OA) |
|------|---------|---------------------|
| Papers có dataset cụ thể | ~240 (60%) | ~320+ (80%) |
| Papers có metric cụ thể | ~200 (50%) | ~300+ (75%) |
| Papers có method cụ thể | ~280 (70%) | ~340+ (85%) |

AI/CS papers thường mention method trong abstract → coverage method luôn cao. Full-text chủ yếu giúp bắt thêm dataset và metric từ experiment section.

### 2.2 Neo4j Model

```
NODES:
  ■ Year             — year: Integer
  ⬡ ResearchField   — fieldId, fieldName
  ▲ Topic            — topicId, topicName, trendScore, isTrending
  ◆ Keyword          — keywordId, text, normalizedText
  ● Paper            — paperId, title, doi, pubYear, fieldId
  □ Dataset          — datasetId, datasetName, normalizedName
  △ Metric           — metricId, metricName, normalizedName, category
  ◇ Method           — methodId, methodName, normalizedName, category
  ★ Author           — authorId, fullName, hIndex, country

RELATIONSHIPS:
  (Paper)-[:PUBLISHED_IN]→(Year)
  (Paper)-[:BELONGS_TO_FIELD]→(ResearchField)
  (Paper)-[:HAS_KEYWORD]→(Keyword)
  (Paper)-[:USES_DATASET {context}]→(Dataset)
  (Paper)-[:USES_METRIC {value, context}]→(Metric)
  (Paper)-[:USES_METHOD {context}]→(Method)
  (Topic)-[:BELONGS_TO]→(ResearchField)
  (Topic)-[:COVERS {weight}]→(Keyword)
  (Author)-[:AUTHORED]→(Paper)
```

### 2.3 SQL Server (JPA)

Giữ nguyên schema hiện tại. Thêm cột:
- `RESEARCH_PAPER.enriched_data` — JSON lưu metrics/datasets/methods đã extract

### 2.4 Tại sao Neo4j Local thay vì AuraDB Cloud?

| Tiêu chí | AuraDB Free | Local Docker |
|----------|------------|--------------|
| Node limit | ~200 | Không giới hạn |
| Với ~3000+ nodes | ❌ Vượt limit | ✅ Thoải mái |
| Latency | Network | <1ms localhost |

Thêm service vào `docker-compose.yml`:
```yaml
neo4j:
  image: neo4j:5.26-community
  ports:
    - "7474:7474"   # Neo4j Browser
    - "7687:7687"   # Bolt
  environment:
    NEO4J_AUTH: neo4j/password123
    NEO4J_server_memory_heap_max__size: 1g
  volumes:
    - neo4j-data:/data
```

---

## 3. FLOW 3 STAGES CHI TIẾT

### Stage 1: "Bản đồ tri thức"

```
┌──────────────────────────────────────────────────────────┐
│                    NEO4J GRAPH                           │
│                                                          │
│          ■ 2024              ■ 2025          ■ 2026      │
│           │                   │               │         │
│       ┌───┴───┐           ┌───┴───┐       ┌───┴───┐    │
│      ⬡ CS    ⬡ IoT       ⬡ CS  ⬡ IoT     ⬡ CS ⬡ IoT  │
│       │                   │                     │       │
│     ▲ DL   ▲ NLP        ▲ Smart Home        ▲ Edge AI  │
│      │                   │                     │       │
│     ◆ CNN(67)           ◆ BLE(45)            ◆ FL(22)  │
│     ◆ LSTM(32)          ◆ Zigbee(15)         ◆ TF(15)  │
│     ◆ Transformer(41)   ◆ Energy(28)                    │
│                                                          │
│  ═══════════════════════════════════════════════════════ │
│  Tổng nodes: ~80-200 → LUÔN NHANH                       │
│  Papers ẨN trong node size của keywords                  │
│  Keyword có paper ở nhiều năm → hub kết nối các năm      │
└──────────────────────────────────────────────────────────┘
```

- Chỉ hiển thị: Year, ResearchField, Topic, **Keyword** (có size = paper count)
- Papers/Datasets/Metrics/Methods/Authors: **chưa hiển thị**
- Keyword node nằm ở trung tâm, kết nối với papers → papers kết nối với years
- Click keyword → expand ra papers của keyword đó

### Stage 2: "Thu hẹp theo ý tưởng"

```
┌──────────────────────────────┬──────────────────────────────┐
│  NEO4J GRAPH (đã lọc)       │  MCP CHATBOT                 │
│                              │                              │
│  ◆ Wearable(34)              │  💬 "ứng dụng AI trong      │
│  ◆ Edge AI(28)               │      thiết bị đeo cho       │
│  ◆ Federated L.(22)          │      healthcare"            │
│  ◆ Health Mon.(18)           │                              │
│  ◆ PPG Signal(12)            │  🤖 Dựa trên data hiện có,  │
│  ◆ Transfer L.(15)           │     tôi đề xuất 3 cặp:      │
│                              │                              │
│  (BLE, Zigbee, MQTT... ẨN)  │  ┌──────────────────────┐   │
│                              │  │ 🔥 Wearable + FL     │   │
│  Click node → highlight     │  │    Gap: 85/100       │[Chọn]│
│  neighbors, dim rest        │  └──────────────────────┘   │
│                              │  ┌──────────────────────┐   │
│                              │  │    Health + Transfer  │   │
│                              │  │    Gap: 68/100       │[Chọn]│
│                              │  └──────────────────────┘   │
│                              │  ┌──────────────────────┐   │
│                              │  │    PPG + Edge AI      │   │
│                              │  │    Gap: 72/100       │[Chọn]│
│                              │  └──────────────────────┘   │
└──────────────────────────────┴──────────────────────────────┘
```

- User mô tả ý tưởng bằng ngôn ngữ tự nhiên
- MCP Chatbot gọi Neo4j tools → AI gợi ý cặp keyword **chỉ từ data có sẵn**
- Graph ẩn keywords không liên quan, giữ lại keywords được AI gợi ý
- Keyword nào không có trong Neo4j → AI báo "không có trong hệ thống"

### Stage 3: "Focus Gap"

```
┌──────────────────────────────┬──────────────────────────────┐
│  NEO4J GRAPH (focus)        │  GAP ANALYSIS PANEL          │
│                              │                              │
│  ◆ Wearable Computing       │  📊 Overlap: 8 papers       │
│  │  ├── ● P1 □ UCI △ Acc ◇ CNN ★ Kim                     │
│  │  ├── ● P2 □ HAR △ F1  ◇ LSTM                          │
│  │  └── ● P3 (shared) ──────────┐                         │
│  │                              │  📦 Dataset Gap          │
│  ◆ Federated Learning           │  🔴 UCI-HAR (34 papers   │
│  │  ├── ● P4 □ FEMNIST △ F1 ◇ FL│     Wearable) chưa      │
│  │  └── ● P5 □ CIFAR  △ Acc ◇ CNN│     được dùng cho FL   │
│  │                               │                         │
│  🔵 A-only  🟠 B-only  ⚪ Shared│  📐 Metric Gap           │
│  □ Dataset  △ Metric  ◇ Method  │  🔴 F1-score ít dùng    │
│  ★ Author                       │     cho FL papers        │
│                              │                              │
│  Click node → highlight      │  🔧 Method Gap             │
│  neighbors, dim rest        │  🔴 CNN (50% Wearable)      │
│                              │     chưa thử cho FL         │
│                              │                              │
│                              │  💡 AI Research Gap         │
│                              │  🤖 "Kết hợp CNN + UCI-HAR │
│                              │     cho FL là hướng mới..." │
│                              │                              │
│                              │  [Generate Roadmap]          │
└──────────────────────────────┴──────────────────────────────┘
```

- Graph chỉ còn 2 keywords + papers + dimensions + authors → ~100-300 nodes
- Phân biệt màu: 🔵 A-only, 🟠 B-only, ⚪ Shared (overlap)
- Panel hiển thị cross-dimension gap analysis
- Dataset/Metric/Method nào chỉ có 1 bên → highlight 🔴 cơ hội

---

## 4. KIẾN TRÚC HỆ THỐNG

### 4.1 Component Tree

```
GapExplorerLayout.jsx (NEW — layout 2 cột: graph + right panel)
├── GapNeo4jGraph.jsx (NEW — vis-network, 3 stages, click interaction)
└── RightPanel.jsx (NEW — thay đổi nội dung theo stage)
    ├── Stage 1-2: MCPChatbot.jsx (NEW — MCP-powered chatbot)
    └── Stage 3:   GapAnalysisPanel.jsx (NEW — cross-dimension analysis)
```

### 4.2 State Management (Zustand)

`store/useGapExplorerStore.js`:

```js
{
  stage: 'overview',           // overview | filtered | focused
  ideaText: '',                // user input cho chatbot
  suggestions: [],             // MCP trả về [{kwA, kwB, reason, gapScore}]
  selectedPair: null,          // {kwA, kwB} khi user chọn
  relevantKeywords: [],        // keywords liên quan → dùng filter graph
  gapAnalysis: null,           // cross-dimension stats
  aiGapResult: null,           // AI analysis
  roadmapResult: null,         // AI roadmap
  loading: {}, error: {}
}
```

### 4.3 Graph & Chatbot đồng bộ

| Hành động | Store thay đổi | Graph phản ứng |
|-----------|---------------|----------------|
| Page load | `stage: 'overview'` | Hiển thị hierarchy view (Year→Field→Topic→Keywords) |
| User gõ idea → MCP trả keywords | `stage: 'filtered'`, `relevantKeywords: [...]` | Ẩn node không trong list, animation fade out |
| User chọn 1 cặp | `stage: 'focused'`, `selectedPair: {kwA, kwB}` | Thu về 2 keywords + expand papers/dimensions |
| Gap analysis xong | `gapAnalysis: {...}` | Highlight gap nodes (dataset chưa dùng chung → flash) |

### 4.4 MCP Tools (BE → Neo4j)

| Tool | Query | Purpose |
|------|-------|---------|
| `list_all_keywords()` | `MATCH (k:Keyword) RETURN k.text, k.normalizedText` | Danh sách keywords có sẵn |
| `get_keyword_cooccurrence(kw)` | `getCooccurringKeywords()` (hiện có) | Keywords liên quan |
| `get_gap_score(kwA, kwB)` | Papers with A-only, B-only, both → tính gap score | Xếp hạng cặp keyword |
| `get_papers_by_keyword(kw, limit)` | Paper nodes connected to keyword | Papers cho keyword |
| `get_dimensions_by_keyword(kw)` | Datasets/Metrics/Methods connected | Dimensions cho gap analysis |
| `get_top_authors(kw, limit)` | Authors with most papers for keyword | Top authors |

### 4.5 Neo4j Query Strategy cho Hierarchy View

**Stage 1 — Hierarchy (không load papers):**
```cypher
// Chỉ lấy Year, Field, Topic, Keyword nodes + relationships phân cấp
// Keywords có paper count làm node size
MATCH (y:Year)
OPTIONAL MATCH (p:Paper)-[:PUBLISHED_IN]->(y)
OPTIONAL MATCH (p)-[:BELONGS_TO_FIELD]->(f:ResearchField)
OPTIONAL MATCH (t:Topic)-[:BELONGS_TO]->(f)
OPTIONAL MATCH (t)-[:COVERS]->(k:Keyword)
OPTIONAL MATCH (k)<-[:HAS_KEYWORD]-(kp:Paper)
RETURN y, f, t, k, COUNT(DISTINCT kp) AS keywordPaperCount
// < 200 nodes, < 100ms
```

---

## 5. PHASE TRIỂN KHAI

### Phase 0: Neo4j Local Setup
- Thêm Neo4j service vào `docker-compose.yml`
- Sửa `.env` + `application.properties` (neo4j+s://cloud → bolt://localhost:7687)
- Test connection, tạo indexes

### Phase 1: Crawl + Enrich + Neo4j Model
- **BE**: PaperEnrichmentService (DeepSeek extract metrics/datasets/methods)
- **BE**: Mở rộng GraphService — thêm methods cho hierarchy view + gap analysis
- **BE**: Migration script tạo Year, ResearchField, Topic nodes + relationships
- **BE**: GapExplorerController — 6 endpoints cho MCP tools + gap analysis

### Phase 2: Frontend
- **FE**: GapNeo4jGraph.jsx — 3-stage graph với click interaction
- **FE**: MCPChatbot.jsx — chatbot kết nối MCP server
- **FE**: GapAnalysisPanel.jsx — cross-dimension gap display
- **FE**: useGapExplorerStore — Zustand shared state

### Phase 3: AI Pipeline
- **BE**: ResearchGapService — cross-dimension gap analysis + AI prompt
- **BE**: RoadmapService — AI research roadmap generator
- Cache: all AI results 1h TTL, precompute gap scores

### Phase 4: Polish
- i18n namespace `graphExplorer` (en + vi)
- Responsive: desktop 2 cột, mobile graph full-width + chatbot bottom sheet
- Error states, loading skeletons, empty states

---

## 6. HIỆU NĂNG

| Giai đoạn | Nodes hiển thị | Thời gian dự kiến |
|-----------|---------------|-------------------|
| Stage 1 — Hierarchy | ~80-200 | **< 1 giây** |
| Stage 2 — Filtered | ~50-150 | **< 500ms** |
| Stage 3 — Focused | ~100-300 | **< 1 giây** |
| MCP Chatbot (có cache) | — | **2-4 giây** |
| AI Gap Analysis | — | **3-5 giây** (có loading) |
| Click highlight | — | **< 100ms** (client-side) |

**Không chậm vì:**
- Stage 1 không load papers — chỉ hierarchy nodes (~200 nodes)
- Gap scores được precompute khi crawl xong
- Neo4j Local = <1ms latency
- Papers/Dimensions chỉ load khi drill-down đến Stage 3 (2 keywords)

---

## 7. ƯỚC LƯỢNG

| Phase | Effort |
|-------|--------|
| Phase 0: Neo4j Local Setup | 0.5 ngày |
| Phase 1: BE Crawl + Enrich + Neo4j Model + APIs | 3-4 ngày |
| Phase 2: FE 3 components + Zustand + vis-network | 3-4 ngày |
| Phase 3: BE AI Pipeline (gap + roadmap + MCP tools) | 2-3 ngày |
| Phase 4: i18n + responsive + polish + test | 1-2 ngày |
| **Tổng** | **10-14 ngày** (1 dev) / **7-9 ngày** (2 devs) |

---

## 8. DECISIONS — Tất cả đã chốt

| # | Câu hỏi | Quyết định |
|---|---------|------------|
| 1 | Lĩnh vực crawl | **Computer Science + AI** |
| 2 | Enrichment | **2-tier: Abstract (bắt buộc) + Full-text (nếu Open Access)** |
| 3 | Paper quality | **Quality Score ≥ 40**, chỉ lấy paper chất lượng |
| 4 | Ngôn ngữ | **Tiếng Anh** (prompt + response) |
| 5 | Route | **Thay thẳng `/overview` bằng Neo4j graph** |
| 6 | MCP Server | **Cách A: Tự build** (`McpOrchestrator.java`, < 300 dòng) |

### 6.1 MCP — Cách A: Tự build orchestrator

```
┌──────────┐       ┌───────────────────┐       ┌──────────┐
│   FE     │──SSE──│   Spring Boot     │──HTTP──│ DeepSeek │
│ Chatbot  │       │                   │       │   AI     │
└──────────┘       │ McpOrchestrator   │       └──────────┘
                   │  while (!done) {  │
                   │    aiResp = AI    │
                   │    if (toolCall)  │
                   │      result =     │
                   │      queryNeo4j() │
                   │  }               │
                   │  return answer    │
                   └──────────────────┘
```

**DeepSeek function calling** (OpenAI-compatible) — AI tự quyết định gọi Neo4j tool nào, BE thực thi và trả kết quả về. Loop ≤ 5 lần. Không dependency mới.

---

*Plan finalized 2026-07-19. Sẵn sàng code sau khi trả lời 6 câu hỏi trên.*
