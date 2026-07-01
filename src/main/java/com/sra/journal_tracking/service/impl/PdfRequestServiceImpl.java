package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.pdf.PdfCandidateResponse;
import com.sra.journal_tracking.dto.pdf.PdfCandidateSearchResponse;
import com.sra.journal_tracking.dto.pdf.PdfRequestCreateRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestFulfillRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestRejectRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestResponse;
import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.entity.jpa.Notification;
import com.sra.journal_tracking.entity.jpa.NotificationType;
import com.sra.journal_tracking.entity.jpa.PdfRequest;
import com.sra.journal_tracking.entity.jpa.PdfRequestStatus;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.exception.PaperNotFoundException;
import com.sra.journal_tracking.repository.jpa.NotificationRepository;
import com.sra.journal_tracking.repository.jpa.PdfRequestRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.PdfRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfRequestServiceImpl implements PdfRequestService {

    private static final String OPEN_ALEX_WORKS_URL = "https://api.openalex.org/works";
    private static final String OPEN_ALEX_SELECT_FIELDS =
            "id,doi,title,display_name,open_access,primary_location,best_oa_location";

    private final PdfRequestRepository pdfRequestRepository;
    private final ResearchPaperRepository researchPaperRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final RestTemplate restTemplate;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    @Override
    @Transactional
    public PdfRequestResponse requestPdf(UUID paperId, String userEmail, PdfRequestCreateRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        ResearchPaper paper = researchPaperRepository.findByIdWithDetails(paperId)
                .orElseThrow(() -> new PaperNotFoundException("Paper not found with ID: " + paperId));

        if (hasPdf(paper)) {
            throw new IllegalArgumentException("PDF is already available for this paper.");
        }

        return pdfRequestRepository
                .findByUser_UserIdAndPaper_PaperIdAndStatus(user.getUserId(), paperId, PdfRequestStatus.PENDING)
                .map(this::mapToResponse)
                .orElseGet(() -> {
                    PdfRequest pdfRequest = PdfRequest.builder()
                            .user(user)
                            .paper(paper)
                            .status(PdfRequestStatus.PENDING)
                            .userMessage(clean(request != null ? request.getMessage() : null))
                            .build();
                    return mapToResponse(pdfRequestRepository.save(pdfRequest));
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PdfRequestResponse> getAdminRequests(String status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)));
        Page<PdfRequest> result;
        if (status != null && !status.isBlank()) {
            result = pdfRequestRepository.findByStatusOrderByRequestedAtDesc(parseStatus(status), pageRequest);
        } else {
            result = pdfRequestRepository.findAllByOrderByRequestedAtDesc(pageRequest);
        }
        return result.getContent().stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PdfCandidateSearchResponse findCandidates(UUID requestId) {
        PdfRequest request = pdfRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        ResearchPaper paper = request.getPaper();

        List<PdfCandidateResponse> candidates = new ArrayList<>();
        if (paper.getDoi() != null && !paper.getDoi().isBlank()) {
            candidates.addAll(findOpenAlexCandidatesByDoi(paper.getDoi()));
        }
        if (candidates.isEmpty() && paper.getTitle() != null && !paper.getTitle().isBlank()) {
            candidates.addAll(findOpenAlexCandidatesByTitle(paper.getTitle()));
        }

        return PdfCandidateSearchResponse.builder()
                .requestId(request.getRequestId())
                .paperId(paper.getPaperId())
                .paperTitle(paper.getTitle())
                .doi(paper.getDoi())
                .candidates(dedupeCandidates(candidates))
                .build();
    }

    @Override
    @Transactional
    public PdfRequestResponse fulfillRequest(UUID requestId, String adminEmail, PdfRequestFulfillRequest request) {
        PdfRequest pdfRequest = pdfRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (pdfRequest.getStatus() != PdfRequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending PDF requests can be fulfilled.");
        }

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        ResearchPaper paper = pdfRequest.getPaper();
        String pdfUrl = clean(request.getPdfUrl());
        if (!isHttpUrl(pdfUrl)) {
            throw new IllegalArgumentException("pdfUrl must start with http:// or https://.");
        }

        paper.setPdfUrl(pdfUrl);
        paper.setIsOpenAccess(true);
        researchPaperRepository.save(paper);

        pdfRequest.setStatus(PdfRequestStatus.FULFILLED);
        pdfRequest.setAdminNote(clean(request.getAdminNote()));
        pdfRequest.setResolvedAt(LocalDateTime.now());
        pdfRequest.setResolvedByAdmin(admin);

        notifyRequester(pdfRequest,
                "PDF is now available",
                "The requested PDF for \"" + paper.getTitle() + "\" is now available.");

        return mapToResponse(pdfRequestRepository.save(pdfRequest));
    }

    @Override
    @Transactional
    public PdfRequestResponse rejectRequest(UUID requestId, String adminEmail, PdfRequestRejectRequest request) {
        PdfRequest pdfRequest = pdfRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (pdfRequest.getStatus() != PdfRequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending PDF requests can be rejected.");
        }

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        pdfRequest.setStatus(PdfRequestStatus.REJECTED);
        pdfRequest.setAdminNote(clean(request != null ? request.getAdminNote() : null));
        pdfRequest.setResolvedAt(LocalDateTime.now());
        pdfRequest.setResolvedByAdmin(admin);

        notifyRequester(pdfRequest,
                "PDF request could not be completed",
                "We could not find a suitable public PDF for \"" + pdfRequest.getPaper().getTitle() + "\".");

        return mapToResponse(pdfRequestRepository.save(pdfRequest));
    }

    private List<PdfCandidateResponse> findOpenAlexCandidatesByDoi(String doi) {
        String normalizedDoi = normalizeDoi(doi);
        if (normalizedDoi == null) {
            return List.of();
        }
        String doiUrl = "https://doi.org/" + normalizedDoi;
        String url = baseOpenAlexBuilder()
                .queryParam("filter", "doi:" + doiUrl)
                .queryParam("per-page", 3)
                .build()
                .encode()
                .toUriString();
        return fetchOpenAlexCandidates(url);
    }

    private List<PdfCandidateResponse> findOpenAlexCandidatesByTitle(String title) {
        String url = baseOpenAlexBuilder()
                .queryParam("search", title)
                .queryParam("sort", "relevance_score:desc")
                .queryParam("per-page", 5)
                .build()
                .encode()
                .toUriString();
        return fetchOpenAlexCandidates(url);
    }

    private UriComponentsBuilder baseOpenAlexBuilder() {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_WORKS_URL)
                .queryParam("select", OPEN_ALEX_SELECT_FIELDS);
        if (openalexEmail != null && !openalexEmail.isBlank()) {
            builder.queryParam("mailto", openalexEmail);
        }
        return builder;
    }

    private List<PdfCandidateResponse> fetchOpenAlexCandidates(String url) {
        try {
            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getResults() == null) {
                return List.of();
            }

            List<PdfCandidateResponse> candidates = new ArrayList<>();
            for (OpenAlexResponseDTO.OpenAlexWorkDTO work : response.getResults()) {
                addOpenAlexLocation(candidates, work.getBestOaLocation());
                addOpenAlexLocation(candidates, work.getPrimaryLocation());
                if (work.getOpenAccess() != null && isLikelyPdfUrl(work.getOpenAccess().getOaUrl())) {
                    candidates.add(PdfCandidateResponse.builder()
                            .source("OpenAlex")
                            .pdfUrl(work.getOpenAccess().getOaUrl())
                            .landingPageUrl(work.getOpenAccess().getOaUrl())
                            .isOpenAccess(work.getOpenAccess().getIsOa())
                            .build());
                }
            }
            return candidates;
        } catch (RestClientException e) {
            log.warn("OpenAlex PDF candidate search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private void addOpenAlexLocation(List<PdfCandidateResponse> candidates, OpenAlexResponseDTO.BestOaLocation location) {
        if (location == null || location.getPdfUrl() == null || location.getPdfUrl().isBlank()) {
            return;
        }
        candidates.add(PdfCandidateResponse.builder()
                .source("OpenAlex")
                .pdfUrl(location.getPdfUrl())
                .landingPageUrl(location.getLandingPageUrl())
                .isOpenAccess(location.getIsOa())
                .build());
    }

    private void addOpenAlexLocation(List<PdfCandidateResponse> candidates, OpenAlexResponseDTO.PrimaryLocation location) {
        if (location == null || location.getPdfUrl() == null || location.getPdfUrl().isBlank()) {
            return;
        }
        candidates.add(PdfCandidateResponse.builder()
                .source("OpenAlex")
                .pdfUrl(location.getPdfUrl())
                .landingPageUrl(location.getLandingPageUrl())
                .isOpenAccess(true)
                .build());
    }

    private List<PdfCandidateResponse> dedupeCandidates(List<PdfCandidateResponse> candidates) {
        Map<String, PdfCandidateResponse> unique = new LinkedHashMap<>();
        for (PdfCandidateResponse candidate : candidates) {
            if (candidate.getPdfUrl() != null && !candidate.getPdfUrl().isBlank()) {
                unique.putIfAbsent(candidate.getPdfUrl(), candidate);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private void notifyRequester(PdfRequest request, String title, String message) {
        notificationRepository.save(Notification.builder()
                .user(request.getUser())
                .type(NotificationType.SYSTEM)
                .title(title)
                .message(message)
                .relatedPaper(request.getPaper())
                .relatedJournal(request.getPaper().getJournal())
                .isRead(false)
                .build());
    }

    private PdfRequestResponse mapToResponse(PdfRequest request) {
        ResearchPaper paper = request.getPaper();
        User user = request.getUser();
        User admin = request.getResolvedByAdmin();
        return PdfRequestResponse.builder()
                .requestId(request.getRequestId())
                .status(request.getStatus() != null ? request.getStatus().name().toLowerCase() : null)
                .userMessage(request.getUserMessage())
                .adminNote(request.getAdminNote())
                .requestedAt(request.getRequestedAt())
                .resolvedAt(request.getResolvedAt())
                .paperId(paper != null ? paper.getPaperId() : null)
                .paperTitle(paper != null ? paper.getTitle() : null)
                .doi(paper != null ? paper.getDoi() : null)
                .pdfUrl(paper != null ? paper.getPdfUrl() : null)
                .pdfAvailable(paper != null && hasPdf(paper))
                .journalId(paper != null && paper.getJournal() != null ? paper.getJournal().getJournalId() : null)
                .journalName(paper != null && paper.getJournal() != null ? paper.getJournal().getJournalName() : null)
                .requestedByUserId(user != null ? user.getUserId() : null)
                .requestedByEmail(user != null ? user.getEmail() : null)
                .requestedByName(user != null ? user.getFullName() : null)
                .resolvedByAdminId(admin != null ? admin.getUserId() : null)
                .resolvedByAdminEmail(admin != null ? admin.getEmail() : null)
                .build();
    }

    private PdfRequestStatus parseStatus(String status) {
        try {
            return PdfRequestStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("status must be pending, fulfilled, or rejected.");
        }
    }

    private boolean hasPdf(ResearchPaper paper) {
        return paper.getPdfUrl() != null && !paper.getPdfUrl().isBlank();
    }

    private boolean isLikelyPdfUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.endsWith(".pdf") || lower.contains("/pdf") || lower.contains("pdf?");
    }

    private boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String normalizeDoi(String doi) {
        if (doi == null || doi.isBlank()) {
            return null;
        }
        String normalized = doi.trim()
                .replace("https://doi.org/", "")
                .replace("http://doi.org/", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
