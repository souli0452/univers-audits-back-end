package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.model.dto.response.DelaiEtapeResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.StatutDelaiEtape;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlerteDelaiEtapeServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-09T10:00:00Z");

    private final DossierRepository dossierRepository = mock(DossierRepository.class);
    private final DelaiEtapeService delaiEtapeService = mock(DelaiEtapeService.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final PortalConfigService portalConfigService = mock(PortalConfigService.class);
    private final SuperieursResolver superieursResolver = mock(SuperieursResolver.class);
    private final AlerteDelaiEtapeService service = new AlerteDelaiEtapeService(
            dossierRepository, delaiEtapeService, notificationRepository, portalConfigService, superieursResolver);

    private final Agent cgea = agent("kc-cgea");
    private final Agent cge = agent("kc-cge");

    private Agent agent(String keycloakId) {
        Agent a = mock(Agent.class);
        when(a.getKeycloakId()).thenReturn(keycloakId);
        return a;
    }

    private Dossier dossier(String numero) {
        Dossier d = mock(Dossier.class);
        when(d.getId()).thenReturn(UUID.randomUUID());
        when(d.getNumber()).thenReturn(numero);
        when(d.getReceptionDate()).thenReturn(Instant.parse("2026-09-01T10:00:00Z"));
        return d;
    }

    private DelaiEtapeResponse etape(String code, StatutDelaiEtape statut, Instant echeance, Long heuresRestantes) {
        return DelaiEtapeResponse.builder()
                .code(code).libelle("Étape " + code).acteur("CGEA")
                .debut(echeance.minusSeconds(3 * 24 * 3600)).echeance(echeance)
                .delaiJours(3).joursOuvrables(true).statut(statut).heuresRestantes(heuresRestantes)
                .build();
    }

    private void etapesDe(Dossier d, DelaiEtapeResponse... etapes) {
        when(delaiEtapeService.evaluer(eq(d.getId()), any(), eq(MAINTENANT))).thenReturn(List.of(etapes));
    }

    @BeforeEach
    void init() {
        when(superieursResolver.resoudre()).thenReturn(List.of(cgea, cge));
        when(portalConfigService.resolveNotificationText(any(), anyMap())).thenReturn("texte");
        when(notificationRepository.existsByDossierIdAndTypeAndEtapeCode(any(), any(), any())).thenReturn(false);
    }

    @Test
    void etape_depassee_cree_une_alerte_par_superieur_avec_le_code_de_l_etape() {
        Dossier d = dossier("ASCE-LC-2026-0001");
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(d));
        etapesDe(d, etape("ETAPE_ANALYSE_CGEA", StatutDelaiEtape.DEPASSE,
                Instant.parse("2026-10-08T10:00:00Z"), -24L));

        int creees = service.alerterDelaisDepasses(MAINTENANT);

        assertThat(creees).isEqualTo(1);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getRecipient)
                .containsExactly("kc-cgea", "kc-cge");
        assertThat(captor.getAllValues()).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.ALERTE_DELAI_ETAPE);
            assertThat(n.getChannel()).isEqualTo(NotificationChannel.PORTAL);
            assertThat(n.getEtapeCode()).isEqualTo("ETAPE_ANALYSE_CGEA");
        });
    }

    @Test
    void les_variables_du_message_contiennent_le_numero_l_etape_et_le_retard() {
        Dossier d = dossier("ASCE-LC-2026-0001");
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(d));
        etapesDe(d, etape("ETAPE_QUITUS_CGE", StatutDelaiEtape.DEPASSE,
                Instant.parse("2026-10-08T06:00:00Z"), -28L));

        service.alerterDelaisDepasses(MAINTENANT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_alerte_delai_etape"), captor.capture());
        assertThat(captor.getValue()).containsEntry("numero", "ASCE-LC-2026-0001")
                .containsEntry("etape", "Étape ETAPE_QUITUS_CGE")
                .containsEntry("acteur", "CGEA")
                .containsEntry("retard", "1 j 4 h");
    }

    @Test
    void pas_de_doublon_quand_l_alerte_existe_deja_pour_cette_etape() {
        Dossier d = dossier("ASCE-LC-2026-0001");
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(d));
        etapesDe(d, etape("ETAPE_ANALYSE_CGEA", StatutDelaiEtape.DEPASSE,
                Instant.parse("2026-10-08T10:00:00Z"), -24L));
        when(notificationRepository.existsByDossierIdAndTypeAndEtapeCode(
                d.getId(), NotificationType.ALERTE_DELAI_ETAPE, "ETAPE_ANALYSE_CGEA")).thenReturn(true);

        assertThat(service.alerterDelaisDepasses(MAINTENANT)).isZero();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void les_etapes_non_depassees_ou_terminees_ne_declenchent_rien() {
        Dossier d = dossier("ASCE-LC-2026-0001");
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(d));
        Instant echeance = Instant.parse("2026-10-08T10:00:00Z");
        etapesDe(d,
                etape("ETAPE_ANALYSE_CGEA", StatutDelaiEtape.EN_COURS, echeance, 50L),
                etape("ETAPE_CONVOCATION_CTADP", StatutDelaiEtape.PROCHE, echeance, 5L),
                etape("ETAPE_QUITUS_CGE", StatutDelaiEtape.RESPECTE, echeance, null),
                etape("ETAPE_IMPUTATION_CGE", StatutDelaiEtape.TERMINE_EN_RETARD, echeance, null));

        assertThat(service.alerterDelaisDepasses(MAINTENANT)).isZero();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void un_retard_ancien_n_inonde_pas_les_destinataires_a_la_mise_en_service() {
        Dossier d = dossier("ASCE-LC-2026-0001");
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(d));
        // Échéance dépassée depuis plus de 7 jours : visible sur la carte, mais pas d'alerte.
        etapesDe(d, etape("ETAPE_ANALYSE_CGEA", StatutDelaiEtape.DEPASSE,
                Instant.parse("2026-09-20T10:00:00Z"), -400L));

        assertThat(service.alerterDelaisDepasses(MAINTENANT)).isZero();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sans_destinataire_aucune_alerte_et_aucune_lecture_des_dossiers() {
        when(superieursResolver.resoudre()).thenReturn(List.of());

        assertThat(service.alerterDelaisDepasses(MAINTENANT)).isZero();
        verify(dossierRepository, never()).findByStatusNotIn(any());
    }

    @Test
    void un_dossier_en_echec_n_empeche_pas_l_alerte_des_autres() {
        Dossier casse = dossier("ASCE-LC-2026-0001");
        Dossier sain = dossier("ASCE-LC-2026-0002");
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(casse, sain));
        when(delaiEtapeService.evaluer(eq(casse.getId()), any(), eq(MAINTENANT)))
                .thenThrow(new IllegalStateException("données incohérentes"));
        etapesDe(sain, etape("ETAPE_ANALYSE_CGEA", StatutDelaiEtape.DEPASSE,
                Instant.parse("2026-10-08T10:00:00Z"), -24L));

        assertThat(service.alerterDelaisDepasses(MAINTENANT)).isEqualTo(1);
    }

    @Test
    void formaterDuree_ecrit_jours_et_heures() {
        assertThat(AlerteDelaiEtapeService.formaterDuree(52)).isEqualTo("2 j 4 h");
        assertThat(AlerteDelaiEtapeService.formaterDuree(-24)).isEqualTo("1 j");
        assertThat(AlerteDelaiEtapeService.formaterDuree(6)).isEqualTo("6 h");
        assertThat(AlerteDelaiEtapeService.formaterDuree(0)).isEqualTo("moins d'1 h");
    }
}
