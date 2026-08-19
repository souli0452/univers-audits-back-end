package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;
import gov.bf.ascelc.univers_audits.repository.DecisionCGERepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatistiqueServiceImplTest {

    @Mock private DossierRepository dossierRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private SeanceCtadpDossierRepository seanceCtadpDossierRepository;
    @Mock private DecisionCGERepository decisionCgeRepository;
    @Mock private TargetedPartyRepository targetedPartyRepository;

    @InjectMocks
    private StatistiqueServiceImpl service;

    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        start = Instant.parse("2026-01-01T00:00:00Z");
        end = Instant.parse("2026-02-01T00:00:00Z");

        // Stubs communs a getDashboard, non pertinents pour ces tests mais necessaires
        // pour que la methode s'execute sans NPE (Mockito "lenient" car pas tous exerces
        // par chaque test individuel).
        lenient().when(dossierRepository.countByReceptionDateBetween(start, end)).thenReturn(0L);
        lenient().when(dossierRepository.countByStatusAndReceptionDateBetween(start, end))
                .thenReturn(List.of());
        lenient().when(dossierRepository.countBySubmissionModeBetween(start, end))
                .thenReturn(List.of());
        lenient().when(dossierRepository.countByTypeBetween(start, end)).thenReturn(List.of());
        lenient().when(dossierRepository.sumEstimatedLossBetween(start, end)).thenReturn(null);
        lenient().when(investigationRepository.countByDossierReceptionDateBetween(start, end))
                .thenReturn(0L);
        lenient().when(investigationRepository.countByOutcomeBetween("JUDICIAL_REFERRAL", start, end))
                .thenReturn(0L);
        lenient().when(investigationRepository.countByOutcomeBetween("ARCHIVED", start, end))
                .thenReturn(0L);
        lenient().when(dossierRepository.countByStatusInAndReceptionDateBetween(
                        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(start),
                        org.mockito.ArgumentMatchers.eq(end)))
                .thenReturn(0L);
        lenient().when(dossierRepository.avgRegistrationDelayInDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgDurationInDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgDeiApprovalDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgCgeApprovalDays(start, end)).thenReturn(null);
        lenient().when(dossierRepository.countOverdueAcknowledgments(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        lenient().when(investigationRepository.countOverdue(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        lenient().when(dossierRepository.countOverdueComplementRequests(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);

        // Stubs des nouvelles requetes, valeurs neutres par defaut
        lenient().when(seanceCtadpDossierRepository.countByRecommandationBetween(start, end))
                .thenReturn(List.of());
        lenient().when(decisionCgeRepository.countByDecisionBetween(start, end))
                .thenReturn(List.of());
        lenient().when(targetedPartyRepository.countByPartyTypeBetween(start, end))
                .thenReturn(List.of());
        lenient().when(investigationRepository.countByOutcomeGrouped(start, end))
                .thenReturn(List.of());
        lenient().when(dossierRepository.avgOpportunityStudyDelayInDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgLegalAdvisorApprovalDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgCgeaApprovalDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgPlanActionsSubmissionDays(start, end)).thenReturn(null);
    }

    @Test
    void getDashboard_renseigneCountByCtadpRecommandation() {
        when(seanceCtadpDossierRepository.countByRecommandationBetween(start, end))
                .thenReturn(List.<Object[]>of(
                        new Object[]{
                                gov.bf.ascelc.univers_audits.enums.RecommandationCtadp.VALIDATION_INVESTIGATION,
                                5L}));

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByCtadpRecommandation())
                .containsEntry("VALIDATION_INVESTIGATION", 5L);
    }

    @Test
    void getDashboard_renseigneCountByCgeDecision() {
        when(decisionCgeRepository.countByDecisionBetween(start, end))
                .thenReturn(List.<Object[]>of(
                        new Object[]{
                                gov.bf.ascelc.univers_audits.enums.RecommandationCtadp.CLASSEMENT, 3L}));

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByCgeDecision()).containsEntry("CLASSEMENT", 3L);
    }

    @Test
    void getDashboard_renseigneCountByTargetedPartyType() {
        when(targetedPartyRepository.countByPartyTypeBetween(start, end))
                .thenReturn(List.<Object[]>of(
                        new Object[]{gov.bf.ascelc.univers_audits.enums.PartyType.PUBLIC_AGENT, 8L}));

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByTargetedPartyType()).containsEntry("PUBLIC_AGENT", 8L);
    }

    @Test
    void getDashboard_renseigneCountByInvestigationOutcomeSansAltererReferredToJusticeExistant() {
        when(investigationRepository.countByOutcomeGrouped(start, end))
                .thenReturn(List.of(
                        new Object[]{
                                gov.bf.ascelc.univers_audits.enums.InvestigationOutcome.JUDICIAL_REFERRAL, 2L},
                        new Object[]{
                                gov.bf.ascelc.univers_audits.enums.InvestigationOutcome.PRESS_RELEASE, 1L}));
        when(investigationRepository.countByOutcomeBetween("JUDICIAL_REFERRAL", start, end))
                .thenReturn(2L);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByInvestigationOutcome())
                .containsEntry("JUDICIAL_REFERRAL", 2L)
                .containsEntry("PRESS_RELEASE", 1L);
        assertThat(result.getReferredToJustice()).isEqualTo(2L);
    }

    @Test
    void getDashboard_renseigneAvgOpportunityStudyDays() {
        when(dossierRepository.avgOpportunityStudyDelayInDays(start, end)).thenReturn(4.5);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgOpportunityStudyDays()).isEqualTo(4.5);
    }

    @Test
    void getDashboard_renseigneAvgLegalAdvisorApprovalDays() {
        when(investigationRepository.avgLegalAdvisorApprovalDays(start, end)).thenReturn(6.0);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgLegalAdvisorApprovalDays()).isEqualTo(6.0);
    }

    @Test
    void getDashboard_renseigneAvgCgeaApprovalDays() {
        when(investigationRepository.avgCgeaApprovalDays(start, end)).thenReturn(3.2);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgCgeaApprovalDays()).isEqualTo(3.2);
    }

    @Test
    void getDashboard_renseigneAvgPlanActionsSubmissionDays() {
        when(investigationRepository.avgPlanActionsSubmissionDays(start, end)).thenReturn(18.7);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgPlanActionsSubmissionDays()).isEqualTo(18.7);
    }

    @Test
    void getDashboard_avgAcknowledgmentDaysResteNull() {
        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgAcknowledgmentDays()).isNull();
    }

    @Test
    void getDashboard_degradeVersMapVideSiAucuneDonnee() {
        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByCtadpRecommandation()).isEmpty();
        assertThat(result.getCountByCgeDecision()).isEmpty();
        assertThat(result.getCountByTargetedPartyType()).isEmpty();
        assertThat(result.getCountByInvestigationOutcome()).isEmpty();
    }
}
