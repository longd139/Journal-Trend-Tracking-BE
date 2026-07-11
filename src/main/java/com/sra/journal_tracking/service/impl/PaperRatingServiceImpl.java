package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.rating.RatingResponse;
import com.sra.journal_tracking.entity.jpa.PaperRating;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.exception.PaperNotFoundException;
import com.sra.journal_tracking.repository.jpa.PaperRatingRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.PaperRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperRatingServiceImpl implements PaperRatingService {

    private final PaperRatingRepository paperRatingRepository;
    private final UserRepository userRepository;
    private final ResearchPaperRepository researchPaperRepository;

    @Override
    @Transactional
    public RatingResponse ratePaper(UUID paperId, String userEmail, int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Score must be between 1 and 5");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        ResearchPaper paper = researchPaperRepository.findById(paperId)
                .orElseThrow(() -> new PaperNotFoundException("Paper not found with ID: " + paperId));

        PaperRating rating = paperRatingRepository
                .findByUser_UserIdAndPaper_PaperId(user.getUserId(), paperId)
                .orElseGet(() -> PaperRating.builder()
                        .user(user)
                        .paper(paper)
                        .build());

        rating.setScore(score);
        paperRatingRepository.save(rating);

        log.info("User {} rated paper {} with score {}", userEmail, paperId, score);
        return buildResponse(paperId, user.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public RatingResponse getRating(UUID paperId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        researchPaperRepository.findById(paperId)
                .orElseThrow(() -> new PaperNotFoundException("Paper not found with ID: " + paperId));

        return buildResponse(paperId, user.getUserId());
    }

    private RatingResponse buildResponse(UUID paperId, UUID userId) {
        Double avg = paperRatingRepository.avgScoreByPaper_PaperId(paperId);
        long count = paperRatingRepository.countByPaper_PaperId(paperId);
        Integer myScore = paperRatingRepository
                .findByUser_UserIdAndPaper_PaperId(userId, paperId)
                .map(PaperRating::getScore)
                .orElse(null);

        return RatingResponse.builder()
                .averageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null)
                .totalRatings(count)
                .myRating(myScore)
                .build();
    }
}
