package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import gov.bf.ascelc.univers_audits.model.dto.response.DelaiEtapeResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.StatutDelaiEtape;
import gov.bf.ascelc.univers_audits.model.entity.DecisionCGE;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCTADP;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import gov.bf.ascelc.univers_audits.model.entity.StatusHistory;
import gov.bf.ascelc.univers_audits.repository.DecisionCGERepository;
import gov.bf.ascelc.univers_audits.repository.FicheAffectationRepository;
import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.StatusHistoryRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DelaiEtapeServiceTest {

    private final DossierService dossierService = mock(DossierService.class);
    private final StatusHistoryRepository historyRepo = mock(StatusHistoryRepository.class);
    private final SeanceCtadpDossierRepository seanceRepo = mock(SeanceCtadpDossierRepository.class);
    private final DecisionCGERepository decisionRepo = mock(DecisionCGERepository.class);
    private final FicheAffectationRepository ficheRepo = mock(FicheAffectationRepository.class);
    private final ParametreDelaiService parametres = mock(ParametreDelaiService.class);
    private final DelaiEtapeService service = new DelaiEtapeService(
            dossierService, historyRepo, seanceRepo, decisionRepo, ficheRepo, parametres,
            new DelaiEtapeCalculator(new DeadlineCalculator(mock(JourFerieRepository.class))));

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void init() {
        // Paramètres par défaut : 3 jours ouvrables, sauf le quitus (15 jours).
        when(parametres.resolveDelaiJours(anyString())).thenReturn(3);
        when(parametres.resolveDelaiJours(DelaiEtapeService.QUITUS_CGE)).thenReturn(15);
        when(parametres.resolveJoursOuvrables(anyString())).thenReturn(true);

        when(historyRepo.findByDossierIdOrderByChangedAtAsc(id)).thenReturn(List.of());
        when(seanceRepo.findByDossierIdOrderByCreatedAtAsc(id)).thenReturn(List.of());
        when(decisionRepo.findByDossierId(id)).thenReturn(Optional.empty());
        when(ficheRepo.findByDossierId(id)).thenReturn(Optional.empty());
    }

    private void dossierRecuLe(String instant) {
        when(dossierService.findById(id)).thenReturn(DossierResponse.builder()
                .id(id).receptionDate(Instant.parse(instant)).build());
    }

    private StatusHistory transition(DossierStatus statut, String instant) {
        return StatusHistory.builder().newStatus(statut).changedAt(Instant.parse(instant)).build();
    }

    private List<String> codes(List<DelaiEtapeResponse> etapes) {
        return etapes.stream().map(DelaiEtapeResponse::getCode).toList();
    }

    @Test
    void dossier_juste_recu_n_a_que_l_etape_d_analyse_en_cours() {
        dossierRecuLe("2026-10-05T10:00:00Z");

        List<DelaiEtapeResponse> etapes = service.findByDossierId(id);

        assertThat(codes(etapes)).containsExactly(DelaiEtapeService.ANALYSE_CGEA);
        assertThat(etapes.get(0).getFin()).isNull();
        assertThat(etapes.get(0).getEcheance()).isEqualTo(Instant.parse("2026-10-08T10:00:00Z"));
    }

    @Test
    void analyse_terminee_apres_l_echeance_est_signalee_en_retard() {
        dossierRecuLe("2026-10-05T10:00:00Z");
        when(historyRepo.findByDossierIdOrderByChangedAtAsc(id)).thenReturn(List.of(
                transition(DossierStatus.EN_ETUDE_OPPORTUNITE, "2026-10-12T09:00:00Z")));

        DelaiEtapeResponse analyse = service.findByDossierId(id).get(0);

        assertThat(analyse.getStatut()).isEqualTo(StatutDelaiEtape.TERMINE_EN_RETARD);
    }

    @Test
    void parcours_complet_de_l_envoi_au_comite_a_l_affectation() {
        dossierRecuLe("2026-10-05T10:00:00Z");
        when(historyRepo.findByDossierIdOrderByChangedAtAsc(id)).thenReturn(List.of(
                transition(DossierStatus.EN_ETUDE_OPPORTUNITE, "2026-10-06T09:00:00Z"),
                transition(DossierStatus.EN_REVUE_CTADP, "2026-10-13T09:00:00Z")));

        SeanceCTADP seance = SeanceCTADP.builder()
                .statut(StatutSeanceCtadp.TENUE).dateSeance(Instant.parse("2026-10-20T09:00:00Z")).build();
        SeanceCtadpDossier lien = SeanceCtadpDossier.builder().seanceCtadp(seance).build();
        ReflectionTestUtils.setField(lien, "createdAt", Instant.parse("2026-10-14T09:00:00Z"));
        when(seanceRepo.findByDossierIdOrderByCreatedAtAsc(id)).thenReturn(List.of(lien));

        when(decisionRepo.findByDossierId(id)).thenReturn(Optional.of(DecisionCGE.builder()
                .decision(RecommandationCtadp.VALIDATION_INVESTIGATION)
                .dateDecision(Instant.parse("2026-10-22T09:00:00Z")).build()));
        when(ficheRepo.findByDossierId(id)).thenReturn(Optional.of(FicheAffectation.builder()
                .dateDecisionCge(Instant.parse("2026-10-23T09:00:00Z")).build()));

        List<DelaiEtapeResponse> etapes = service.findByDossierId(id);

        assertThat(codes(etapes)).containsExactly(
                DelaiEtapeService.ANALYSE_CGEA, DelaiEtapeService.CONVOCATION_CTADP,
                DelaiEtapeService.QUITUS_CGE, DelaiEtapeService.IMPUTATION_CGE,
                DelaiEtapeService.AFFECTATION_CGEA);
        // Convocation : envoyé au comité le 13, inscrit à une séance le 14 → dans les 3 jours.
        assertThat(etapes.get(1).getStatut()).isEqualTo(StatutDelaiEtape.RESPECTE);
        // Quitus : séance le 20, décision le 22 → dans les 15 jours.
        assertThat(etapes.get(2).getStatut()).isEqualTo(StatutDelaiEtape.RESPECTE);
        // Imputation : décision le 22, fiche du CGE le 23 → dans les 3 jours.
        assertThat(etapes.get(3).getStatut()).isEqualTo(StatutDelaiEtape.RESPECTE);
        // Affectation : décision du CGE le 23, pas encore imputé → en cours ou dépassée, sans fin.
        assertThat(etapes.get(4).getFin()).isNull();
    }

    @Test
    void imputation_n_est_suivie_que_si_le_dossier_est_retenu_pour_investigation() {
        dossierRecuLe("2026-10-05T10:00:00Z");
        when(decisionRepo.findByDossierId(id)).thenReturn(Optional.of(DecisionCGE.builder()
                .decision(RecommandationCtadp.CLASSEMENT)
                .dateDecision(Instant.parse("2026-10-22T09:00:00Z")).build()));

        assertThat(codes(service.findByDossierId(id))).doesNotContain(DelaiEtapeService.IMPUTATION_CGE);
    }

    @Test
    void etape_dont_le_parametre_est_desactive_n_est_pas_suivie() {
        dossierRecuLe("2026-10-05T10:00:00Z");
        when(parametres.resolveDelaiJours(DelaiEtapeService.ANALYSE_CGEA))
                .thenThrow(new ResourceNotFoundException("inactif"));

        assertThat(service.findByDossierId(id)).isEmpty();
    }

    @Test
    void dossier_sans_date_de_reception_n_a_aucune_etape() {
        when(dossierService.findById(id)).thenReturn(DossierResponse.builder().id(id).build());

        assertThat(service.findByDossierId(id)).isEmpty();
    }
}
