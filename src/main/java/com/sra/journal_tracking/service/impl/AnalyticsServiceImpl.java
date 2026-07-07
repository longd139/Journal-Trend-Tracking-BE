package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.analytics.CountryBreakdownResponse;
import com.sra.journal_tracking.dto.analytics.CountryTrendResponse;
import com.sra.journal_tracking.dto.analytics.InstitutionBreakdownResponse;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
import com.sra.journal_tracking.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final int DEFAULT_INSTITUTION_LIMIT = 10;
    private static final int MAX_INSTITUTION_LIMIT = 100;
    private static final int DEFAULT_TREND_YEAR_WINDOW = 5;

    private final PaperAuthorRepository paperAuthorRepository;

    @Override
    public CountryBreakdownResponse getCountryBreakdown(String keyword, Integer year) {
        Short normalizedYear = normalizeYear(year, "year");
        String normalizedKeyword = normalizeKeyword(keyword);

        List<CountryBreakdownResponse.CountryStats> countries = paperAuthorRepository
                .findCountryBreakdown(normalizedKeyword, normalizedYear)
                .stream()
                .map(row -> CountryBreakdownResponse.CountryStats.builder()
                        .country(asString(row[0]))
                        .paperCount(asLong(row[1]))
                        .citationCount(asLong(row[2]))
                        .build())
                .toList();

        return CountryBreakdownResponse.builder()
                .keyword(normalizedKeyword)
                .year(year)
                .countries(countries)
                .build();
    }

    @Override
    public InstitutionBreakdownResponse getInstitutionBreakdown(String keyword, Integer year, Integer limit) {
        Short normalizedYear = normalizeYear(year, "year");
        String normalizedKeyword = normalizeKeyword(keyword);
        int normalizedLimit = normalizeLimit(limit);

        List<InstitutionBreakdownResponse.InstitutionStats> institutions = paperAuthorRepository
                .findInstitutionBreakdown(normalizedKeyword, normalizedYear, PageRequest.of(0, normalizedLimit))
                .stream()
                .map(row -> InstitutionBreakdownResponse.InstitutionStats.builder()
                        .institution(asString(row[0]))
                        .paperCount(asLong(row[1]))
                        .citationCount(asLong(row[2]))
                        .build())
                .toList();

        return InstitutionBreakdownResponse.builder()
                .keyword(normalizedKeyword)
                .year(year)
                .limit(normalizedLimit)
                .institutions(institutions)
                .build();
    }

    @Override
    public CountryTrendResponse getTrendByCountry(String keyword, Integer startYear, Integer endYear) {
        int currentYear = Year.now().getValue();
        int normalizedEndYear = endYear != null ? endYear : currentYear;
        int normalizedStartYear = startYear != null ? startYear : normalizedEndYear - DEFAULT_TREND_YEAR_WINDOW + 1;

        Short start = normalizeYear(normalizedStartYear, "startYear");
        Short end = normalizeYear(normalizedEndYear, "endYear");
        if (start > end) {
            throw new IllegalArgumentException("startYear must be less than or equal to endYear.");
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        Map<String, List<CountryTrendResponse.YearlyStats>> timelines = new LinkedHashMap<>();

        for (Object[] row : paperAuthorRepository.findCountryTrend(normalizedKeyword, start, end)) {
            String country = asString(row[0]);
            timelines.computeIfAbsent(country, ignored -> new ArrayList<>())
                    .add(CountryTrendResponse.YearlyStats.builder()
                            .year(asInteger(row[1]))
                            .paperCount(asLong(row[2]))
                            .citationCount(asLong(row[3]))
                            .build());
        }

        List<CountryTrendResponse.CountryTimeline> countries = timelines.entrySet().stream()
                .map(entry -> CountryTrendResponse.CountryTimeline.builder()
                        .country(entry.getKey())
                        .timeline(entry.getValue())
                        .build())
                .toList();

        return CountryTrendResponse.builder()
                .keyword(normalizedKeyword)
                .startYear(normalizedStartYear)
                .endYear(normalizedEndYear)
                .countries(countries)
                .build();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private Short normalizeYear(Integer year, String parameterName) {
        if (year == null) {
            return null;
        }
        if (year < 0 || year > 9999) {
            throw new IllegalArgumentException(parameterName + " must be between 0 and 9999.");
        }
        return year.shortValue();
    }

    private int normalizeLimit(Integer limit) {
        int value = limit != null ? limit : DEFAULT_INSTITUTION_LIMIT;
        if (value < 1) {
            throw new IllegalArgumentException("limit must be greater than 0.");
        }
        return Math.min(value, MAX_INSTITUTION_LIMIT);
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
