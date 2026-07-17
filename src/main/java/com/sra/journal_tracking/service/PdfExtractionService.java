package com.sra.journal_tracking.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * Downloads PDFs into RAM as byte arrays, extracts text using PDFBox,
 * then discards everything — no filesystem or database persistence.
 * <p>
 * Architecture: <b>In-Memory Stream Processing (Stateless)</b>
 * <pre>
 *   PDF URL → HTTP GET byte[] → PDDocument.load(byte[]) → PDFTextStripper.getText()
 *       ↓                          ↓                           ↓
 *   ~200ms                    exists ~2-3s                 Text → AI prompt
 *                                                            ↓
 *                                               byte[] & PDDocument GC'd
 * </pre>
 */
@Slf4j
@Service
public class PdfExtractionService {

    private final RestTemplate pdfRestTemplate;

    public PdfExtractionService(@Qualifier("pdfRestTemplate") RestTemplate pdfRestTemplate) {
        this.pdfRestTemplate = pdfRestTemplate;
    }

    /**
     * Download a PDF from the given URL into a byte array (RAM only, no disk I/O).
     *
     * @param pdfUrl  the URL of the PDF file
     * @return raw PDF bytes, or null if download fails (timeout, 403, 404, etc.)
     */
    public byte[] downloadPdf(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.isBlank()) {
            log.debug("PDF URL is null or blank, skipping download");
            return null;
        }

        try {
            log.debug("Downloading PDF: {}", pdfUrl);
            byte[] bytes = pdfRestTemplate.getForObject(pdfUrl, byte[].class);
            if (bytes == null || bytes.length == 0) {
                log.warn("Downloaded PDF is empty: {}", pdfUrl);
                return null;
            }
            log.info("Downloaded PDF: {} bytes from {}", bytes.length, pdfUrl);
            return bytes;
        } catch (RestClientException e) {
            log.warn("Failed to download PDF from {}: {}", pdfUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Extract plain text from a PDF byte array using PDFBox.
     * The byte array and resulting PDDocument are discarded after extraction.
     *
     * @param pdfBytes  raw PDF content (from {@link #downloadPdf(String)})
     * @return extracted text, or null if parsing fails (e.g. scanned/image-only PDF)
     */
    public String extractText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
            if (document.isEncrypted()) {
                log.warn("PDF is encrypted, cannot extract text");
                return null;
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(false);

            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                log.debug("PDFBox extracted empty text (possibly scanned/image-based PDF)");
                return null;
            }

            log.info("Extracted {} characters from PDF", text.length());
            return text.trim();
        } catch (IOException e) {
            log.warn("PDFBox failed to parse PDF: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convenience: download + extract in one call.
     *
     * @param pdfUrl  the URL of the PDF file
     * @return extracted text, or null if any step fails
     */
    public String downloadAndExtract(String pdfUrl) {
        byte[] pdfBytes = downloadPdf(pdfUrl);
        if (pdfBytes == null) return null;
        return extractText(pdfBytes);
    }
}
