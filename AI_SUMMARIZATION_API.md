# AI Summarization APIs

> **Module:** Paper | **Base URL:** `/api/v1/ai` | **Auth:** Bearer Token (JWT) | **AI Provider:** DeepSeek (via ai-box.vn)

## Overview

3 AI-powered endpoints giúp tóm tắt abstract, trích xuất methodology, và phân tích batch papers. Tất cả đều có **graceful fallback** — khi AI provider không khả dụng, các trường AI sẽ là `null` và bị omit khỏi JSON response (không throw lỗi).

### Architecture

```
AIController → AISummarizationService → AIClient (interface)
                                            └── DeepSeekClient → ai-box.vn API
                        
GraphController → GraphSearchProcessor → GeminiService → AIClient
                                              └── local fallback (ACADEMIC_RELATIONS map)
```

- **Interface:** `AIClient` — trừu tượng hóa AI provider, dễ swap sau này
- **Default:** `DeepSeekClient` — gọi OpenAI-compatible API (`https://api.ai-box.vn/v1/chat/completions`)
- **Model:** `deepseek-v4-pro`
- **Cache:** In-memory 1h TTL (cùng pattern với keyword expansion)
- **Timeout:** 30s connect / 120s read

---

## 1. Summarize Paper Abstract

Tóm tắt abstract của một paper thành 2-3 câu, đồng thời trích xuất methodology.

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **Path** | `/api/v1/ai/summarize/{paperId}` |
| **Auth** | Bearer Token |
| **Content-Type** | N/A |

### Path Parameters

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `paperId` | UUID | Yes | ID của paper cần tóm tắt |

### Success Response (HTTP 200)

```json
{
  "status": 200,
  "message": "Paper details with AI summary",
  "data": {
    "paperId": "72bff577-62c1-47b8-a5c3-d0507fc23cdf",
    "title": "From grey to blue: an ocean economy fit for the future",
    "abstractText": "The 'blue economy' or the 'ocean economy' are terms...",
    "doi": "10.5258/soton/p1173",
    "pubYear": 2024,
    "pubDate": "2024-01-01",
    "citationCount": 0,
    "isOpenAccess": true,
    "journalName": "ePrints Soton (University of Southampton)",
    "journalId": "8c6df00c-7898-4fa3-a564-feb21c950dc7",
    "fieldName": "Environmental Science",
    "fieldId": "77eded9b-c11d-44a0-873b-19acdb8fd98c",
    "authors": [
      {
        "fullName": "Gourvenec, Susan",
        "affiliation": null,
        "totalCitations": 0,
        "authorOrder": 1,
        "isCorresponding": null,
        "hindex": 0
      }
    ],
    "keywords": [
      {
        "keywordText": "Coastal and Marine Management",
        "relevanceScore": 0.8644
      }
    ],
    "sourceUrl": "https://doi.org/10.5258/soton/p1173",
    "pdfAvailable": true,
    "downloadUrl": "https://eprints.soton.ac.uk/506709/1/2024_SMMI_OceanEconomyFitForFuture.pdf",
    "pdfUrl": "https://eprints.soton.ac.uk/506709/1/2024_SMMI_OceanEconomyFitForFuture.pdf",
    "rating": 0.0,
    "downloadCount": 0,
    "commentCount": 0,
    "createdAt": "2026-07-03T14:13:58",

    "aiSummary": "The research addresses the urgent need to transition the global ocean economy from an unsustainable grey model to a sustainable blue economy by the mid-21st century to avoid climate change and irreversible damage to marine ecosystems. The report analyzes current economic activities connected to the ocean and presents potential future scenarios and pathways for this transition. Key contributions include defining the ocean economy as all ocean-connected economic activities while framing the blue economy as a social construct aimed at sustainable ocean use.",
    "methodology": "scenario analysis"
  },
  "timestamp": "2026-07-04T00:47:10"
}
```

### Graceful Fallback (AI unavailable)

Khi AI provider không khả dụng (thiếu API key, timeout, network error), `aiSummary` và `methodology` sẽ **vắng mặt** hoàn toàn trong response:

```json
{
  "status": 200,
  "message": "Paper details with AI summary",
  "data": {
    "paperId": "72bff577-62c1-47b8-a5c3-d0507fc23cdf",
    "title": "From grey to blue: an ocean economy fit for the future",
    "abstractText": "The 'blue economy'...",
    "doi": "10.5258/soton/p1173",
    "...": "..."
  },
  "timestamp": "2026-07-03T23:47:58"
}
```

### Error Response (HTTP 404)

```json
{
  "status": 404,
  "message": "Resource not found!",
  "timestamp": "2026-07-03T23:51:21"
}
```

---

## 2. Extract Methodology

Trích xuất phương pháp nghiên cứu (methodology) từ abstract của paper.

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **Path** | `/api/v1/ai/methodology/{paperId}` |
| **Auth** | Bearer Token |
| **Content-Type** | N/A |

### Path Parameters

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `paperId` | UUID | Yes | ID của paper cần trích xuất |

### Success Response (HTTP 200)

```json
{
  "status": 200,
  "message": "Methodology extracted",
  "data": {
    "paperId": "72bff577-62c1-47b8-a5c3-d0507fc23cdf",
    "title": "From grey to blue: an ocean economy fit for the future",
    "methodology": "scenario analysis"
  },
  "timestamp": "2026-07-04T00:47:10"
}
```

### Methodology Categories

Model được prompt để trả về một trong các category sau (kết quả thực tế có thể linh hoạt hơn):

| Category |
|----------|
| `quantitative survey` |
| `qualitative interview` |
| `randomized controlled trial (RCT)` |
| `systematic review` |
| `meta-analysis` |
| `case study` |
| `experimental design` |
| `mixed methods` |
| `computational modeling` |
| `theoretical analysis` |
| `literature review` |
| `unknown` |

### Graceful Fallback (AI unavailable)

```json
{
  "status": 200,
  "message": "Methodology extracted",
  "data": {
    "paperId": "72bff577-62c1-47b8-a5c3-d0507fc23cdf",
    "title": "From grey to blue: an ocean economy fit for the future"
  },
  "timestamp": "2026-07-03T23:48:45"
}
```

---

## 3. Batch Analyze Papers

Phân tích nhiều papers cùng lúc: tóm tắt từng paper + so sánh cross-paper insight.

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **Path** | `/api/v1/ai/batch-analyze` |
| **Auth** | Bearer Token |
| **Content-Type** | `application/json` |

### Request Body

```json
[
  "72bff577-62c1-47b8-a5c3-d0507fc23cdf",
  "3f627446-ad83-4ded-a33d-faf356baf0df"
]
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `[0..n]` | UUID[] | Yes | Danh sách paper IDs (tối đa 10, trùng lặp tự động loại bỏ) |

### Success Response (HTTP 200)

```json
{
  "status": 200,
  "message": "Batch analysis completed",
  "data": {
    "paperSummaries": [
      {
        "paperId": "72bff577-62c1-47b8-a5c3-d0507fc23cdf",
        "title": "From grey to blue: an ocean economy fit for the future",
        "summary": "This paper clarifies the distinction between the ocean economy (all ocean-related economic activities) and the blue economy (a social construct for sustainable ocean use), arguing that transitioning from grey to blue is imperative to avoid climate change and marine ecosystem damage. It presents a current snapshot of the ocean economy, potential future scenarios, and pathways for this transition by the mid-21st century. The significance lies in its comprehensive overview of economic, societal, and environmental possibilities and threats facing the ocean.",
        "methodology": null
      },
      {
        "paperId": "3f627446-ad83-4ded-a33d-faf356baf0df",
        "title": "Internationalisation or Europeanisation? Capturing Dynamic Concepts in Higher Education Institutions",
        "summary": "This conceptual analysis examines how internationalisation and Europeanisation, as dynamic and contested concepts, shape higher education institutions and policies. It explores the interplay and potential convergence or divergence between these two forces. The work contributes to a deeper understanding of how global and regional pressures are reconfiguring the higher education landscape.",
        "methodology": null
      }
    ],
    "comparativeInsight": "Though focused on entirely different domains—ocean sustainability and higher education—both papers grapple with contested, evolving concepts (blue economy vs. ocean economy; internationalisation vs. Europeanisation) and the complex interplay between global pressures, regional or sectoral dynamics, and future pathways. They share a common analytical approach of mapping current states against potential futures, highlighting the need for strategic transition and adaptation.",
    "papersAnalyzed": 2
  },
  "timestamp": "2026-07-04T00:49:00"
}
```

### Graceful Fallback (AI unavailable)

```json
{
  "status": 200,
  "message": "Batch analysis completed",
  "data": {
    "paperSummaries": [
      {
        "paperId": "72bff577-62c1-47b8-a5c3-d0507fc23cdf",
        "title": "From grey to blue: an ocean economy fit for the future"
      },
      {
        "paperId": "3f627446-ad83-4ded-a33d-faf356baf0df",
        "title": "Internationalisation or Europeanisation?"
      }
    ],
    "papersAnalyzed": 0
  },
  "timestamp": "2026-07-03T23:50:40"
}
```

### Empty Request

```json
// Request: []
// Response:
{
  "status": 200,
  "message": "No papers provided",
  "data": {
    "paperSummaries": [],
    "papersAnalyzed": 0
  },
  "timestamp": "2026-07-03T23:51:22"
}
```

---

## Caching Behavior

Tất cả kết quả AI được cache in-memory với TTL **1 giờ**:

| Cache Key Pattern | Ví dụ |
|-------------------|-------|
| `summary:{paperId}` | `summary:72bff577-62c1-47b8-a5c3-d0507fc23cdf` |
| `methodology:{paperId}` | `methodology:72bff577-62c1-47b8-a5c3-d0507fc23cdf` |
| `batch:{sortedIds}` | `batch:3f627446...,72bff577...` |

Gọi lại cùng endpoint với cùng input trong vòng 1 giờ → trả về kết quả cache ngay lập tức, không gọi AI API.

---

## Error Codes

| HTTP Status | Scenario |
|-------------|----------|
| `200` | Thành công (có hoặc không có AI fields — tùy AI availability) |
| `404` | Paper ID không tồn tại |
| `401` | Thiếu hoặc sai JWT token |

**Lưu ý:** Không có HTTP 500 cho lỗi AI — tất cả lỗi AI được graceful fallback về `null`.

---

## Configuration

### application.properties

```properties
# DeepSeek AI — powers keyword expansion, summarization, methodology, batch analysis
deepseek.api.key=${DEEPSEEK_API_KEY:}
deepseek.api.url=${DEEPSEEK_API_URL:https://api.ai-box.vn/v1/chat/completions}
deepseek.model=${DEEPSEEK_MODEL:deepseek-v4-pro}
```

### .env

```env
DEEPSEEK_API_KEY=sk-your-key-here
```

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DEEPSEEK_API_KEY` | Yes | (empty) | API key từ ai-box.vn |
| `DEEPSEEK_API_URL` | No | `https://api.ai-box.vn/v1/chat/completions` | OpenAI-compatible endpoint |
| `DEEPSEEK_MODEL` | No | `deepseek-v4-pro` | Model name |

### Timeout

| Phase | Duration | Config |
|-------|----------|--------|
| Connect | 30s | `AppConfig.deepseekRestTemplate()` |
| Read | 120s | `AppConfig.deepseekRestTemplate()` |
| Cache TTL | 1h | `AISummarizationService.CACHE_TTL_MS` |

---

## Prompt Templates

### Summarization Prompt
```
You are an academic research assistant.
Summarize the following research paper abstract in exactly 2-3 concise sentences.
Focus on: (1) the research problem/question, (2) the key findings or contributions,
and (3) the significance or implications.
Return ONLY the summary text, no headings, no bullet points, no extra commentary.

Abstract: "{abstractText}"
```
**Config:** `temperature=0.3`, `max_tokens=512`

### Methodology Prompt
```
You are an academic research assistant. Read the following research paper abstract
and identify the research methodology used.
Respond with ONLY one of these exact categories:
- quantitative survey / qualitative interview / RCT / systematic review
- meta-analysis / case study / experimental design / mixed methods
- computational modeling / theoretical analysis / literature review / unknown
If not clearly identifiable, respond with "unknown".
Return ONLY the category label, no additional text.

Abstract: "{abstractText}"
```
**Config:** `temperature=0.2`, `max_tokens=64`

### Batch Analysis Prompt
```
You are an academic research assistant. Analyze the following set of research papers:
1. A 2-3 sentence summary for EACH paper (labeled as "Paper N: ...")
2. A comparative insight section ("COMPARATIVE INSIGHT:") identifying common themes,
   complementary findings, contradictions, or research gaps.

Papers:
Paper 1 (Title: "..."): Abstract: "..."
Paper 2 (Title: "..."): Abstract: "..."

Format:
Paper 1: [summary]
Paper 2: [summary]
COMPARATIVE INSIGHT: [cross-paper analysis]
```
**Config:** `temperature=0.4`, `max_tokens=2048`

---

## Implementation Files

| File | Role |
|------|------|
| `service/AIClient.java` | Interface — `call(prompt, maxTokens, temperature)` |
| `service/DeepSeekClient.java` | OpenAI-compatible HTTP client (ai-box.vn) |
| `service/AISummarizationService.java` | Caching, prompt building, response parsing |
| `service/GeminiService.java` | Keyword expansion via AIClient + local fallback |
| `service/BatchAnalysisResult.java` | Internal result holder |
| `controller/AIController.java` | REST endpoints at `/api/v1/ai` |
| `dto/ai/BatchAnalysisResponseDTO.java` | Batch response with `PaperSummaryItem` |
| `dto/ai/MethodologyResponseDTO.java` | Methodology response |
| `dto/paper/PaperDetailResponseDTO.java` | Added `aiSummary` + `methodology` fields |
| `config/AppConfig.java` | `deepseekRestTemplate` bean (30s/120s timeout) |

---

## Notes

- **API Provider:** ai-box.vn (`https://api.ai-box.vn/v1/chat/completions`) — OpenAI-compatible
- **Model:** `deepseek-v4-pro` — reasoning model, response có thể chứa `reasoning_content` (đã handled)
- **Batch limit:** Tối đa 10 papers mỗi request
- **Abstract truncation:** Abstract > 1500 ký tự sẽ bị cắt trước khi gửi lên AI
- **Swap provider:** Để đổi AI provider khác (OpenAI, DeepSeek chính chủ, OpenRouter...), chỉ cần thay `deepseek.api.url` và `deepseek.model` trong config, không cần đổi code
