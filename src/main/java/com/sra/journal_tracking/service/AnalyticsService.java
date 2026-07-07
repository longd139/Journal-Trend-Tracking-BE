package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.analytics.CountryBreakdownResponse;
import com.sra.journal_tracking.dto.analytics.CountryTrendResponse;
import com.sra.journal_tracking.dto.analytics.InstitutionBreakdownResponse;

public interface AnalyticsService {
    CountryBreakdownResponse getCountryBreakdown(String keyword, Integer year);

    InstitutionBreakdownResponse getInstitutionBreakdown(String keyword, Integer year, Integer limit);

    CountryTrendResponse getTrendByCountry(String keyword, Integer startYear, Integer endYear);
}
