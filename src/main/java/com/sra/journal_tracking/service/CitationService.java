package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.PaperAuthor;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates academic citations in multiple formats: BibTeX, RIS, APA, MLA.
 * Uses paper metadata (title, authors, journal, year, doi) available in the database.
 */
@Slf4j
@Service
public class CitationService {

    /**
     * Generate citation for a paper in the requested format.
     */
    public String generate(ResearchPaper paper, String format) {
        return switch (format.toLowerCase()) {
            case "bibtex" -> toBibTeX(paper);
            case "ris" -> toRIS(paper);
            case "apa" -> toAPA(paper);
            case "mla" -> toMLA(paper);
            default -> throw new IllegalArgumentException("Unsupported format: " + format + ". Use: bibtex, ris, apa, mla");
        };
    }

    // ═══════════════════════════════════════════════
    //  BibTeX
    // ═══════════════════════════════════════════════

    private String toBibTeX(ResearchPaper paper) {
        String citeKey = buildCiteKey(paper);
        StringBuilder sb = new StringBuilder();
        sb.append("@article{").append(citeKey).append(",\n");
        sb.append("  title = {").append(escapeBibTeX(paper.getTitle())).append("},\n");

        String authorBib = formatAuthorsBibTeX(paper);
        if (!authorBib.isEmpty()) {
            sb.append("  author = {").append(authorBib).append("},\n");
        }

        String journal = getJournalName(paper);
        if (!journal.isEmpty()) {
            sb.append("  journal = {").append(escapeBibTeX(journal)).append("},\n");
        }

        if (paper.getPubYear() != null) {
            sb.append("  year = {").append(paper.getPubYear()).append("},\n");
        }

        if (paper.getDoi() != null && !paper.getDoi().isBlank()) {
            sb.append("  doi = {").append(paper.getDoi()).append("},\n");
            sb.append("  url = {https://doi.org/").append(paper.getDoi()).append("},\n");
        }

        sb.append("}");
        return sb.toString();
    }

    private String buildCiteKey(ResearchPaper paper) {
        String firstAuthor = getFirstAuthorLastName(paper);
        String year = paper.getPubYear() != null ? String.valueOf(paper.getPubYear()) : "n.d.";
        String firstWord = "";
        if (paper.getTitle() != null && !paper.getTitle().isBlank()) {
            String[] words = paper.getTitle().trim().split("\\s+");
            if (words.length > 0) {
                firstWord = words[0].toLowerCase().replaceAll("[^a-z0-9]", "");
            }
        }
        return firstAuthor + year + firstWord;
    }

    private String formatAuthorsBibTeX(ResearchPaper paper) {
        List<String> authors = getSortedAuthorNames(paper);
        return authors.stream()
                .map(name -> toLastNameFirst(name))
                .collect(Collectors.joining(" and "));
    }

    private String escapeBibTeX(String text) {
        if (text == null) return "";
        return text.replace("{", "\\{")
                .replace("}", "\\}")
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("$", "\\$")
                .replace("#", "\\#")
                .replace("_", "\\_")
                .replace("~", "\\~{}");
    }

    // ═══════════════════════════════════════════════
    //  RIS
    // ═══════════════════════════════════════════════

    private String toRIS(ResearchPaper paper) {
        StringBuilder sb = new StringBuilder();
        sb.append("TY  - JOUR\n");

        // Authors
        for (String author : getSortedAuthorNames(paper)) {
            sb.append("AU  - ").append(toLastNameFirst(author)).append("\n");
        }

        // Title
        if (paper.getTitle() != null) {
            sb.append("TI  - ").append(paper.getTitle()).append("\n");
        }

        // Journal
        String journal = getJournalName(paper);
        if (!journal.isEmpty()) {
            sb.append("JO  - ").append(journal).append("\n");
        }

        // Year
        if (paper.getPubYear() != null) {
            sb.append("PY  - ").append(paper.getPubYear()).append("\n");
        }

        // Publication date
        if (paper.getPubDate() != null) {
            sb.append("DA  - ").append(paper.getPubDate()).append("\n");
        }

        // DOI
        if (paper.getDoi() != null && !paper.getDoi().isBlank()) {
            sb.append("DO  - ").append(paper.getDoi()).append("\n");
            sb.append("UR  - https://doi.org/").append(paper.getDoi()).append("\n");
        }

        // ISSN
        String issn = getIssn(paper);
        if (!issn.isEmpty()) {
            sb.append("SN  - ").append(issn).append("\n");
        }

        sb.append("ER  - \n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════
    //  APA (7th edition)
    // ═══════════════════════════════════════════════

    private String toAPA(ResearchPaper paper) {
        StringBuilder sb = new StringBuilder();

        // Authors: Last, F. M., Last, F. M., & Last, F. M.
        List<String> authorNames = getSortedAuthorNames(paper);
        if (!authorNames.isEmpty()) {
            sb.append(formatAuthorsAPA(authorNames));
            sb.append(" ");
        }

        // Year
        if (paper.getPubYear() != null) {
            sb.append("(").append(paper.getPubYear()).append("). ");
        } else {
            sb.append("(n.d.). ");
        }

        // Title (sentence case, italicized in real APA — we approximate)
        if (paper.getTitle() != null) {
            sb.append(toSentenceCase(paper.getTitle())).append(". ");
        }

        // Journal (italicized in real APA)
        String journal = getJournalName(paper);
        if (!journal.isEmpty()) {
            sb.append("<i>").append(journal).append("</i>.");
        }

        // DOI or URL
        if (paper.getDoi() != null && !paper.getDoi().isBlank()) {
            sb.append(" https://doi.org/").append(paper.getDoi());
        } else if (paper.getPdfUrl() != null && !paper.getPdfUrl().isBlank()) {
            sb.append(" ").append(paper.getPdfUrl());
        }

        return sb.toString();
    }

    private String formatAuthorsAPA(List<String> authors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < authors.size(); i++) {
            if (i > 0) {
                sb.append(i == authors.size() - 1 ? ", & " : ", ");
            }
            sb.append(toAPAInitials(authors.get(i)));
        }
        return sb.toString();
    }

    private String toAPAInitials(String fullName) {
        // "John Smith" → "Smith, J."
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0) return fullName;
        String lastName = parts[parts.length - 1];
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(Character.toUpperCase(parts[i].charAt(0))).append(". ");
            }
        }
        return lastName + ", " + initials.toString().trim();
    }

    // ═══════════════════════════════════════════════
    //  MLA (9th edition)
    // ═══════════════════════════════════════════════

    private String toMLA(ResearchPaper paper) {
        StringBuilder sb = new StringBuilder();

        // Authors: Last, First, and First Last.
        List<String> authorNames = getSortedAuthorNames(paper);
        if (!authorNames.isEmpty()) {
            String mlaAuthors = formatAuthorsMLA(authorNames);
            sb.append(mlaAuthors);
            sb.append(mlaAuthors.endsWith(".") ? " " : ". ");
        }

        // "Title."
        if (paper.getTitle() != null) {
            sb.append("\"").append(paper.getTitle()).append(".\" ");
        }

        // Journal (italicized)
        String journal = getJournalName(paper);
        if (!journal.isEmpty()) {
            sb.append("<i>").append(journal).append("</i>");
        }

        // Year
        if (paper.getPubYear() != null) {
            sb.append(", ").append(paper.getPubYear());
        }

        // DOI
        if (paper.getDoi() != null && !paper.getDoi().isBlank()) {
            sb.append(", doi:").append(paper.getDoi()).append(".");
        } else {
            sb.append(".");
        }

        return sb.toString();
    }

    private String formatAuthorsMLA(List<String> authors) {
        if (authors.size() == 1) {
            return authors.get(0); // "Smith, John"
        } else if (authors.size() == 2) {
            return toLastNameFirst(authors.get(0)) + ", and " + authors.get(1);
        } else {
            return toLastNameFirst(authors.get(0)) + ", et al.";
        }
    }

    // ═══════════════════════════════════════════════
    //  Shared helpers
    // ═══════════════════════════════════════════════

    private List<String> getSortedAuthorNames(ResearchPaper paper) {
        if (paper.getAuthors() == null || paper.getAuthors().isEmpty()) {
            return List.of();
        }
        return paper.getAuthors().stream()
                .filter(pa -> pa.getAuthor() != null && pa.getAuthor().getFullName() != null)
                .sorted(Comparator.comparingInt(pa -> pa.getAuthorOrder() != null ? pa.getAuthorOrder() : 99))
                .map(pa -> pa.getAuthor().getFullName().trim())
                .collect(Collectors.toList());
    }

    private String getFirstAuthorLastName(ResearchPaper paper) {
        List<String> authors = getSortedAuthorNames(paper);
        if (authors.isEmpty()) return "unknown";
        String[] parts = authors.get(0).split("\\s+");
        return parts.length > 0 ? parts[parts.length - 1].toLowerCase() : "unknown";
    }

    private String toLastNameFirst(String fullName) {
        // "John Smith" → "Smith, John"
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0) return fullName;
        String lastName = parts[parts.length - 1];
        StringBuilder firstNames = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) firstNames.append(" ");
            firstNames.append(parts[i]);
        }
        return lastName + ", " + firstNames;
    }

    private String getJournalName(ResearchPaper paper) {
        if (paper.getJournal() != null && paper.getJournal().getJournalName() != null) {
            return paper.getJournal().getJournalName().trim();
        }
        return "";
    }

    private String getIssn(ResearchPaper paper) {
        if (paper.getJournal() != null && paper.getJournal().getIssn() != null) {
            return paper.getJournal().getIssn().trim();
        }
        return "";
    }

    private String toSentenceCase(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
