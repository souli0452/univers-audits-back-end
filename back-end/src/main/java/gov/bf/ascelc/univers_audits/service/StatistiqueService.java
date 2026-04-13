package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;

import java.time.Instant;


public interface StatistiqueService {

    // Tableau de bord global — tous les indicateurs
    StatistiqueResponse getDashboard(Instant start, Instant end);

    // Statistiques par période
    StatistiqueResponse getQuarterlyStats(int year, int quarter);

    StatistiqueResponse getAnnualStats(int year);
}