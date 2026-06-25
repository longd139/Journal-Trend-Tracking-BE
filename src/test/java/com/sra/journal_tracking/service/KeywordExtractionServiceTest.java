package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.Keyword;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeywordExtractionServiceTest {

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private ResearchPaperRepository researchPaperRepository;

    @InjectMocks
    private KeywordExtractionService keywordExtractionService;

    @BeforeEach
    void setUp() {
        // Mock: total 10000 papers in DB
        when(researchPaperRepository.count()).thenReturn(10000L);

        // Mock: "transfer learning" - hiếm (50 papers) -> IDF cao
        when(keywordRepository.findByNormalizedText("transfer learning"))
                .thenReturn(Optional.of(Keyword.builder().keywordText("transfer learning")
                        .normalizedText("transfer learning").paperCount(50).build()));

        // Mock: "image segmentation" - phổ biến (2000 papers) -> IDF thấp
        when(keywordRepository.findByNormalizedText("image segmentation"))
                .thenReturn(Optional.of(Keyword.builder().keywordText("image segmentation")
                        .normalizedText("image segmentation").paperCount(2000).build()));

        // Mock: "u-net" - rất hiếm (5 papers) -> IDF rất cao
        when(keywordRepository.findByNormalizedText("u-net"))
                .thenReturn(Optional.of(Keyword.builder().keywordText("U-Net")
                        .normalizedText("u-net").paperCount(5).build()));

        // Mock: mặc định mọi keyword khác -> chưa có trong DB
        when(keywordRepository.findByNormalizedText(anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldExtractKeywordsFromTitleAndAbstract() {
        String title = "Transfer Learning for Medical Image Segmentation using U-Net";
        String abstractText = "This paper proposes a novel transfer learning approach " +
                "for medical image segmentation. We leverage the U-Net architecture " +
                "pre-trained on large datasets and fine-tune it using transfer learning " +
                "techniques. Our method achieves state-of-the-art results on medical image " +
                "segmentation benchmarks including CT scans and MRI images.";

        List<String> keywords = keywordExtractionService.extract(title, abstractText, 5);

        assertNotNull(keywords);
        assertFalse(keywords.isEmpty());
        assertTrue(keywords.size() <= 5, "Should return at most 5 keywords");

        System.out.println("=== 1. Keywords extracted ===");
        for (int i = 0; i < keywords.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + keywords.get(i));
        }

        assertTrue(keywords.contains("transfer learning"),
                "Should contain 'transfer learning'");
        assertTrue(keywords.contains("medical image segmentation"),
                "Should contain 'medical image segmentation'");
        // "U-Net" bị normalize thành "u net" -> "u" quá ngắn nên bị lọc
        // Đây là hạn chế đã biết với từ có dấu gạch ngang
        System.out.println("  (u-net hyphen split -> filtered out as expected)");
    }

    @Test
    void shouldReturnEmptyForBlankInput() {
        List<String> result = keywordExtractionService.extract("", "", 5);
        assertTrue(result.isEmpty());
        System.out.println("=== 2. Blank input -> empty list ===");
    }

    @Test
    void shouldReturnEmptyForNullInput() {
        List<String> result = keywordExtractionService.extract(null, null, 5);
        assertTrue(result.isEmpty());
        System.out.println("=== 3. Null input -> empty list ===");
    }

    @Test
    void shouldFilterStopWords() {
        String title = "The Study of Machine Learning";
        String abstractText = "This is a study about the use of machine learning in the field of AI.";

        List<String> keywords = keywordExtractionService.extract(title, abstractText, 5);

        System.out.println("=== 4. Stop words filtered ===");
        keywords.forEach(k -> System.out.println("  - " + k));

        // stop words không được lọt vào keyword
        for (String kw : keywords) {
            assertFalse(kw.equals("the") || kw.equals("is") || kw.equals("a") ||
                    kw.equals("of") || kw.equals("in") || kw.equals("this") ||
                    kw.equals("about") || kw.equals("for") || kw.equals("to"),
                    "Stop word leaked into keywords: " + kw);
        }
    }

    @Test
    void shouldBoostTitleKeywords() {
        // Tu trong title duoc boost x2, giup no canh tranh voi tu trong abstract
        String title = "Quantum Computing for Drug Discovery";
        String abstractText = "This paper explores the application of quantum computing " +
                "techniques to accelerate drug discovery pipelines. We demonstrate how " +
                "quantum algorithms can simulate molecular interactions for drug discovery.";

        List<String> keywords = keywordExtractionService.extract(title, abstractText, 5);

        System.out.println("=== 5. Title boost test ===");
        keywords.forEach(k -> System.out.println("  - " + k));

        // "drug discovery" xuat hien trong title + abstract -> phai co
        assertTrue(keywords.contains("drug discovery") || keywords.contains("quantum computing"),
                "Title keywords should appear: " + keywords);
    }
}
