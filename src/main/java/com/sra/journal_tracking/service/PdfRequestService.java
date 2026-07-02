package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.pdf.PdfCandidateSearchResponse;
import com.sra.journal_tracking.dto.pdf.PdfRequestCreateRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestFulfillRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestRejectRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestResponse;

import java.util.List;
import java.util.UUID;

public interface PdfRequestService {
    PdfRequestResponse requestPdf(UUID paperId, String userEmail, PdfRequestCreateRequest request);
    List<PdfRequestResponse> getAdminRequests(String status, int page, int size);
    PdfCandidateSearchResponse findCandidates(UUID requestId);
    PdfRequestResponse fulfillRequest(UUID requestId, String adminEmail, PdfRequestFulfillRequest request);
    PdfRequestResponse rejectRequest(UUID requestId, String adminEmail, PdfRequestRejectRequest request);
}
