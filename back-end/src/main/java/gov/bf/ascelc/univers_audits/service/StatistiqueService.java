package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;

import java.time.Instant;
import java.util.Map;

public interface StatistiqueService {

    StatistiqueResponse getDashboard(Instant start, Instant end);

    StatistiqueResponse getQuarterlyStats(int year, int quarter);

    StatistiqueResponse getAnnualStats(int year);

    Map<String, Object> getPublicStats();
}