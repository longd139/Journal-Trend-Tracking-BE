# Researcher Overview — API Suggestions

> **Date**: 2026-07-07
> **Status**: Draft — for BE team review
> **Context**: The FE `UserOverviewPage.jsx` renders 3 researcher-specific sections (Research Impact chart, Research Fields pie chart, Recent Publications table) that currently have no matching data from the BE. The existing `GET /api/v1/overview/user` endpoint returns only 5 scalar card values, not the structured arrays the UI expects.

---

## 1. Current State (What Exists)

### BE: `GET /api/v1/overview/user` → `UserOverviewResponse`

| Field | Type | Description |
|-------|------|-------------|
| `totalPapers` | `Long` | Total papers in system |
| `papersViewed` | `Long` | Papers viewed this month |
| `searchesRemaining` | `Integer` | Remaining search quota (academic only) |
| `monthlySearchLimit` | `Integer` | Monthly search limit (academic only) |
| `totalKeywords` | `Long` | Total keywords indexed |

### BE: `GET /api/public/dashboard/overview` → `OverviewStatsResponse`

| Field | Type | Description |
|-------|------|-------------|
| `papersTracked` | `Long` | Total papers |
| `totalCitations` | `Long` | Sum of all citations |
| `paperGrowth` | `Long` | New papers this month |
| `totalAuthors` | `Long` | Total unique authors |

### FE: What `UserOverviewPage.jsx` actually renders for researcher

| Section | FE Component | Data Source (in code) | Current Status |
|---------|-------------|----------------------|----------------|
| Stat Cards (4) | `StatCard` | `getPublicOverview()` | ✅ Works — `pd.totalCitations`, `pd.papersTracked`, `pd.totalAuthors` |
| h-index card | `StatCard` | — | ❌ **Hardcoded** `'—'` on line 172 |
| Research Impact | `BarChart` | `d.citationHistory \|\| d.citationsOverTime` | ❌ **Not in API response** |
| Research Fields | `PieChart` | `d.researchFields \|\| d.fields` | ❌ **Not in API response** |
| Recent Publications | `<table>` | `d.recentPublications \|\| d.publications` | ❌ **Not in API response** |

---

## 2. Proposed API Changes

### Approach: Extend `GET /api/v1/overview/user`

Add 3 new fields to `UserOverviewResponse` (or a new endpoint — see **Option B** below).

### 2.1 Research Impact (BarChart)

**What the FE expects** — an array of year → citation count:

```json
{
  "citationHistory": [
    { "y": 2021, "citations": 45 },
    { "y": 2022, "citations": 78 },
    { "y": 2023, "citations": 112 },
    { "y": 2024, "citations": 156 },
    { "y": 2025, "citations": 203 }
  ]
}
```

**Field** | **Type** | **Required** | **Description**
---|---|---|---
`y` | `int` | Yes | Year (e.g., 2025)
`citations` | `int` | Yes | Total citations received in that year

**BE implementation suggestion:**
- Query the `ResearchPaper` table grouped by `publicationYear`, summing `citationCount`
- Filter by papers authored by (or associated with) the authenticated user
- Return last 5–10 years
- If the user has no papers → return empty array `[]`

**SQL pseudo:**
```sql
SELECT p.publication_year, SUM(p.citation_count)
FROM research_paper p
JOIN paper_author pa ON p.id = pa.paper_id
WHERE pa.author_name = :userName   -- or match by user_id if linked
GROUP BY p.publication_year
ORDER BY p.publication_year
```

**DB tables needed:** `ResearchPaper`, `PaperAuthor` (or equivalent linking table)

---

### 2.2 Research Fields (PieChart / Donut)

**What the FE expects** — an array of field name → percentage + color:

```json
{
  "researchFields": [
    { "name": "Machine Learning",       "value": 35, "color": "#DEDBC8" },
    { "name": "Natural Language Processing", "value": 25, "color": "#A09878" },
    { "name": "Computer Vision",        "value": 20, "color": "#E1E0CC" },
    { "name": "Data Mining",            "value": 12, "color": "#4F8CFF" },
    { "name": "Human-Computer Interaction", "value": 8, "color": "#00D1B2" }
  ]
}
```

**Field** | **Type** | **Required** | **Description**
---|---|---|---
`name` | `String` | Yes | Research field / topic name
`value` | `int`/`float` | Yes | Percentage of total (values should sum to ~100)
`color` | `String` | No | Hex color for the chart slice (FE falls back to built-in palette)

**BE implementation suggestion:**
- Query the user's papers, group by `researchField` / `keyword` / `topic`
- Calculate percentage = `(count_in_field / total_user_papers) * 100`
- Return top 5–8 fields
- `color` can be omitted — the FE chart has a built-in palette (`CHART_COLORS`)

**Two approaches:**
1. **Keyword-based**: Group by keywords on the user's papers (via Neo4j `HAS_KEYWORD` relationship or JPA `Keyword` entity)
2. **Topic-based**: If you have a `ResearchTopic` entity, group by topics assigned to the user's papers

**Neo4j approach:**
```cypher
MATCH (a:Author {name: $userName})-[:AUTHORED]->(p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
RETURN k.name, COUNT(p) AS count
ORDER BY count DESC
LIMIT 8
```

---

### 2.3 Recent Publications (Table)

**What the FE expects** — an array of paper objects:

```json
{
  "recentPublications": [
    {
      "paperId": "W123456789",
      "title": "Deep Learning Approaches for Academic Trend Analysis",
      "journal": "Journal of Informetrics",
      "journalName": "Journal of Informetrics",
      "year": 2025,
      "pubYear": 2025,
      "role": "First Author",
      "citations": 42,
      "citationCount": 42
    }
  ]
}
```

**Field** | **Type** | **Required** | **Description**
---|---|---|---
`paperId` | `String` | Yes | Unique paper identifier (OpenAlex ID or internal ID)
`title` | `String` | Yes | Full paper title
`journal` / `journalName` | `String` | No | Journal/publication venue name (FE checks both keys)
`year` / `pubYear` | `int` | No | Publication year (FE checks both keys)
`role` | `String` | No | Author role: `"First Author"`, `"Co-Author"`, `"Author"`, `"Corresponding Author"`
`citations` / `citationCount` | `int` | No | Citation count (FE checks both keys)

> **Note:** The FE normalizes field names — it checks both `journal`/`journalName`, `year`/`pubYear`, `citations`/`citationCount`. You only need to send one variant per field.

**BE implementation suggestion:**
- Query papers authored by the user, ordered by `publicationYear DESC` then `citationCount DESC`
- Join with `Journal` or `PaperJournal` table for journal name
- Determine `role` from author ordering:
  - If the user is the first author → `"First Author"`
  - If the user is the last author and paper has ≥3 authors → `"Corresponding Author"`
  - Otherwise → `"Co-Author"`
- Return top 10–20 most recent

**SQL pseudo:**
```sql
SELECT p.id, p.title, j.name AS journal, p.publication_year, pa.author_position, p.citation_count
FROM research_paper p
LEFT JOIN paper_journal pj ON p.id = pj.paper_id
LEFT JOIN journal j ON pj.journal_id = j.id
JOIN paper_author pa ON p.id = pa.paper_id
WHERE pa.author_name = :userName   -- or user_id if linked
ORDER BY p.publication_year DESC, p.citation_count DESC
LIMIT 20
```

**DB tables needed:** `ResearchPaper`, `Journal` (or journal name column), `PaperAuthor`

---

### 2.4 h-index (Stat Card)

The h-index card at line 172 is hardcoded to `'—'`. Add this to either:
- `GET /api/public/dashboard/overview` (global h-index of the system) — unlikely to be useful
- `GET /api/v1/overview/user` (the researcher's personal h-index) — **recommended**

```json
{
  "hIndex": 14
}
```

**BE implementation suggestion:**
- h-index = the largest number H such that the user has H papers with at least H citations each
- Algorithm: sort user's papers by citation count DESC, find the largest index `i` where `citations[i] >= i+1`

---

## 3. Full Proposed Response Shape

### Option A: Extend existing `GET /api/v1/overview/user`

New `UserOverviewResponse` DTO:

```json
{
  // ── Existing card fields (keep for backward compatibility) ──
  "totalPapers": 15234,
  "papersViewed": 87,
  "searchesRemaining": null,
  "monthlySearchLimit": null,
  "totalKeywords": 4521,

  // ── NEW: h-index ──
  "hIndex": 14,

  // ── NEW: Research Impact chart ──
  "citationHistory": [
    { "y": 2021, "citations": 45 },
    { "y": 2022, "citations": 78 },
    { "y": 2023, "citations": 112 },
    { "y": 2024, "citations": 156 },
    { "y": 2025, "citations": 203 }
  ],

  // ── NEW: Research Fields pie chart ──
  "researchFields": [
    { "name": "Machine Learning",             "value": 35 },
    { "name": "Natural Language Processing",  "value": 25 },
    { "name": "Computer Vision",              "value": 20 },
    { "name": "Data Mining",                  "value": 12 },
    { "name": "HCI",                          "value": 8 }
  ],

  // ── NEW: Recent Publications table ──
  "recentPublications": [
    {
      "paperId": "W123456789",
      "title": "Deep Learning for Academic Trend Analysis",
      "journal": "Journal of Informetrics",
      "year": 2025,
      "role": "First Author",
      "citations": 42
    }
  ]
}
```

### Option B: New dedicated researcher endpoint

If you prefer to keep the current endpoint simple, create a new one:

```
GET /api/v1/overview/researcher
```

That returns only the researcher-specific sections (`hIndex`, `citationHistory`, `researchFields`, `recentPublications`). The FE would call this in parallel with the existing `/user` endpoint.

**Recommendation:** Option A (extend existing endpoint) — simpler for the FE to consume, fewer HTTP round-trips.

---

## 4. FE Changes Needed (After BE is Ready)

Once the BE returns the new fields, the FE needs only **minor normalization tweaks** — the code already handles these fields (lines 178–195):

```js
// Already works — just needs data in the response:
const chartData = d.citationHistory || d.citationsOverTime || [];
const rawPieData = d.researchFields || d.fields || [];
const tableData = d.recentPublications || d.publications || [];

// h-index stat card (line 172) — change from:
{ label: 'h-index', value: '—', ... }
// to:
{ label: 'h-index', value: (pd.hIndex ?? d.hIndex ?? '—'), ... }
```

---

## 5. Summary — What BE Needs to Build

| Priority | Feature | New Field(s) | Data Source |
|----------|---------|-------------|-------------|
| 🔴 P0 | h-index | `hIndex` (int) | `ResearchPaper` + `PaperAuthor` — calculate from user's papers |
| 🔴 P0 | Research Impact | `citationHistory[]` (`y`, `citations`) | `ResearchPaper` grouped by year |
| 🔴 P0 | Research Fields | `researchFields[]` (`name`, `value`, `color?`) | `Keyword` or `ResearchTopic` grouped by user's papers |
| 🔴 P0 | Recent Publications | `recentPublications[]` (`paperId`, `title`, `journal`, `year`, `role`, `citations`) | `ResearchPaper` + `Journal` + `PaperAuthor` |

**Key question for BE team:** How are users linked to papers? The current `UserOverviewServiceImpl` doesn't appear to have a user→paper mapping. You may need:
1. A `PaperAuthor` join table linking `User` ↔ `ResearchPaper` with `authorPosition`
2. Or match by `authorName` string against the user's display name
3. Or leverage existing Neo4j `(:Author)-[:AUTHORED]->(:Paper)` relationships

---

## 6. Sample DTO (Java)

```java
// Add to UserOverviewResponse.java
private Integer hIndex;

private List<CitationYearEntry> citationHistory;

private List<ResearchFieldEntry> researchFields;

private List<RecentPublicationEntry> recentPublications;

// ── Inner classes ──

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class CitationYearEntry {
    private int y;
    private int citations;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class ResearchFieldEntry {
    private String name;
    private double value;       // percentage
    private String color;       // optional — FE has default palette
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class RecentPublicationEntry {
    private String paperId;
    private String title;
    private String journal;
    private int year;
    private String role;        // "First Author" | "Co-Author" | "Corresponding Author"
    private int citations;
}
```
