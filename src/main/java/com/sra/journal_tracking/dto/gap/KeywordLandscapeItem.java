package com.sra.journal_tracking.dto.gap;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A topic area in the available knowledge landscape (for Layer 4: Guided Discovery).
 * Groups top keywords under their field + topic hierarchy.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordLandscapeItem {

    private String fieldName;
    private String topicName;
    private List<KeywordPreview> topKeywords;

    public KeywordLandscapeItem() {}

    public KeywordLandscapeItem(String fieldName, String topicName, List<KeywordPreview> topKeywords) {
        this.fieldName = fieldName;
        this.topicName = topicName;
        this.topKeywords = topKeywords;
    }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }

    public List<KeywordPreview> getTopKeywords() { return topKeywords; }
    public void setTopKeywords(List<KeywordPreview> topKeywords) { this.topKeywords = topKeywords; }
}
