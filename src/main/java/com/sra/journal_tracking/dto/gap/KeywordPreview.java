package com.sra.journal_tracking.dto.gap;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A brief preview of a keyword shown in the guided discovery landscape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordPreview {

    private String text;
    private String normalizedText;
    private int paperCount;
    private boolean isTrending;

    public KeywordPreview() {}

    public KeywordPreview(String text, String normalizedText, int paperCount, boolean isTrending) {
        this.text = text;
        this.normalizedText = normalizedText;
        this.paperCount = paperCount;
        this.isTrending = isTrending;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getNormalizedText() { return normalizedText; }
    public void setNormalizedText(String normalizedText) { this.normalizedText = normalizedText; }

    public int getPaperCount() { return paperCount; }
    public void setPaperCount(int paperCount) { this.paperCount = paperCount; }

    public boolean isTrending() { return isTrending; }
    public void setTrending(boolean trending) { isTrending = trending; }
}
