package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.service.KeycloakAdminService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private DossierDetailsMapper detailsMapper;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private PortalConfigService portalConfigService;
    @Mock(answer = Answers.CALLS_REAL_METHODS) private DeadlineCalculator deadlineCalculator;
    @Mock private DemandeDocumentsRepository demandeDocumentsRepository;
    @Mock private ParametreDelaiService parametreDelaiService;
    @Mock private KeycloakAdminService keycloakAdminService;

    @InjectMocks
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(dossierRepository.findOverdueAcknowledgments(any())).thenReturn(List.of());
        lenient().when(dossierRepository.findOverdueComplementRequests(any())).thenReturn(List.of());
        lenient().when(dossierRepository.findOverdueInvestigations(any())).thenReturn(List.of());
        lenient().when(dossierRepository.findAcknowledgmentsDueWithin(any(), any())).thenReturn(List.of());
        lenient().when(dossierRepository.findComplementsDueWithin(any(), any())).thenReturn(List.of());
        lenient().when(dossierRepository.findInvestigationsDueWithin(any(), any())).thenReturn(List.of());
        lenient().when(demandeDocumentsRepository.findOverdue(any())).thenReturn(List.of());
        lenient().when(demandeDocumentsRepository.findDueWithin(any(), any())).thenReturn(List.of());
        lenient().when(portalConfigService.resolveNotificationText(anyString(), anyMap()))
                .thenReturn("texte");
        lenient().when(parametreDelaiService.resolveDelaiJours("ESCALADE_DELAI_GRACE"))
                .thenReturn(3);
        lenient().when(keycloakAdminService.getUserIdsByRole(anyString())).thenReturn(List.of());
        lenient().when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of());
        lenient().when(dossierRepository.findComplementsOverdueBeyondGrace(any())).thenReturn(List.of());
        lenient().when(dossierRepository.findInvestigationsOverdueBeyondGrace(any())).thenReturn(List.of());
        lenient().when(demandeDocumentsRepository.findOverdueBeyondGrace(any())).thenReturn(List.of());
    }

    // ── Non-régression : les 3 blocs "à échéance dépassée" existants ──

    @Test
    void sendDeadlineAlerts_createsAlertForOverdueAcknowledgment() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0001").build();
        when(dossierRepository.findOverdueAcknowledgments(any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEADLINE_ALERT && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_createsAlertForOverdueComplement() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0002").build();
        when(dossierRepository.findOverdueComplementRequests(any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.INTERNAL_ALERT && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_createsAlertForOverdueInvestigation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0003").build();
        when(dossierRepository.findOverdueInvestigations(any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.INVESTIGATION_ALERT && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_doesNotDuplicateOverdueAcknowledgmentAlert() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0004").build();
        when(dossierRepository.findOverdueAcknowledgments(any())).thenReturn(List.of(dossier));
        when(notificationRepository.existsByDossierIdAndType(
                dossier.getId(), NotificationType.DEADLINE_ALERT)).thenReturn(true);

        service.sendDeadlineAlerts();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.DEADLINE_ALERT));
    }

    // ── Nouveau : alertes J-3 ──

    @Test
    void sendDeadlineAlerts_createsJ3AlertForAcknowledgmentDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0005").build();
        when(dossierRepository.findAcknowledgmentsDueWithin(any(), any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEADLINE_ALERT_J3 && n.getDossier() == dossier));
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_deadline_ar_j3"), anyMap());
        verify(portalConfigService).resolveNotificationText(eq("notif_content_deadline_ar_j3"), anyMap());
    }

    @Test
    void sendDeadlineAlerts_createsJ3AlertForComplementDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0006").build();
        when(dossierRepository.findComplementsDueWithin(any(), any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.COMPLEMENT_ALERT_J3 && n.getDossier() == dossier));
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_deadline_complement_j3"), anyMap());
        verify(portalConfigService).resolveNotificationText(eq("notif_content_deadline_complement_j3"), anyMap());
    }

    @Test
    void sendDeadlineAlerts_createsJ3AlertForInvestigationDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0007").build();
        when(dossierRepository.findInvestigationsDueWithin(any(), any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.INVESTIGATION_ALERT_J3 && n.getDossier() == dossier));
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_deadline_investigation_j3"), anyMap());
        verify(portalConfigService).resolveNotificationText(eq("notif_content_deadline_investigation_j3"), anyMap());
    }

    @Test
    void sendDeadlineAlerts_doesNotDuplicateJ3AcknowledgmentAlert() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0008").build();
        when(dossierRepository.findAcknowledgmentsDueWithin(any(), any())).thenReturn(List.of(dossier));
        when(notificationRepository.existsByDossierIdAndType(
                dossier.getId(), NotificationType.DEADLINE_ALERT_J3)).thenReturn(true);

        service.sendDeadlineAlerts();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.DEADLINE_ALERT_J3));
    }

    // ── Nouveau : demande de documents (a echeance + J-3) ──

    private DemandeDocuments buildDemande(Dossier dossier) {
        Investigation investigation = Investigation.builder()
                .id(UUID.randomUUID())
                .dossier(dossier)
                .build();
        return DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .received(false)
                .sentAt(Instant.now())
                .build();
    }

    @Test
    void sendDeadlineAlerts_createsAlertForOverdueDemandeDocuments() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0009").build();
        DemandeDocuments demande = buildDemande(dossier);
        when(demandeDocumentsRepository.findOverdue(any())).thenReturn(List.of(demande));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT
                        && n.getDossier() == dossier
                        && n.getDemandeDocuments() == demande));
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_deadline_demande_documents"), anyMap());
        verify(portalConfigService).resolveNotificationText(eq("notif_content_deadline_demande_documents"), anyMap());
    }

    @Test
    void sendDeadlineAlerts_createsJ3AlertForDemandeDocumentsDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0010").build();
        DemandeDocuments demande = buildDemande(dossier);
        when(demandeDocumentsRepository.findDueWithin(any(), any())).thenReturn(List.of(demande));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT_J3
                        && n.getDossier() == dossier
                        && n.getDemandeDocuments() == demande));
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_deadline_demande_documents_j3"), anyMap());
        verify(portalConfigService).resolveNotificationText(eq("notif_content_deadline_demande_documents_j3"), anyMap());
    }

    @Test
    void sendDeadlineAlerts_doesNotDuplicateDemandeDocumentsAlert() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0011").build();
        DemandeDocuments demande = buildDemande(dossier);
        when(demandeDocumentsRepository.findOverdue(any())).thenReturn(List.of(demande));
        when(notificationRepository.existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                eq(demande.getId()), eq(NotificationType.DEMANDE_DOCUMENTS_ALERT), any()))
                .thenReturn(true);

        service.sendDeadlineAlerts();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT));
    }

    @Test
    void sendDeadlineAlerts_alertsSecondConcurrentDemandeDocumentsIndependently() {
        // Preuve directe du motif de la FK ajoutee sur Notification : deux
        // demandes de documents non recues sur le MEME dossier, chacune en
        // depassement, doivent recevoir chacune leur propre alerte — la
        // premiere deja alertee ne doit pas supprimer l'alerte de la seconde.
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0012").build();
        DemandeDocuments firstDemande = buildDemande(dossier);
        DemandeDocuments secondDemande = buildDemande(dossier);
        when(demandeDocumentsRepository.findOverdue(any()))
                .thenReturn(List.of(firstDemande, secondDemande));
        when(notificationRepository.existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                eq(firstDemande.getId()), eq(NotificationType.DEMANDE_DOCUMENTS_ALERT), any()))
                .thenReturn(true);
        when(notificationRepository.existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                eq(secondDemande.getId()), eq(NotificationType.DEMANDE_DOCUMENTS_ALERT), any()))
                .thenReturn(false);

        service.sendDeadlineAlerts();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getDemandeDocuments() == firstDemande));
        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT
                        && n.getDemandeDocuments() == secondDemande));
    }

    @Test
    void sendDeadlineAlerts_reAlertsDemandeDocumentsAfterEscalationResetsSentAt() {
        // Une DemandeDocuments escaladee (RELANCE, SOMMATION, ...) reutilise
        // la MEME ligne avec un nouveau sentAt/deadline. Le dedup doit donc
        // s'appuyer sur existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter
        // avec le sentAt COURANT de la demande (pas juste l'id+type), pour
        // qu'une alerte de l'ancien cycle ne supprime pas l'alerte du
        // nouveau cycle. Ce test verifie que le service appelle bien le bon
        // parametre de recence et cree l'alerte quand la requete renvoie
        // false (comme le ferait la vraie requete apres une escalade).
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0013").build();
        Instant currentCycleSentAt = Instant.now();
        DemandeDocuments demande = buildDemande(dossier);
        demande.setSentAt(currentCycleSentAt);
        when(demandeDocumentsRepository.findOverdue(any())).thenReturn(List.of(demande));
        when(notificationRepository.existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                eq(demande.getId()), eq(NotificationType.DEMANDE_DOCUMENTS_ALERT), eq(currentCycleSentAt)))
                .thenReturn(false);

        service.sendDeadlineAlerts();

        verify(notificationRepository).existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                demande.getId(), NotificationType.DEMANDE_DOCUMENTS_ALERT, currentCycleSentAt);
        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT
                        && n.getDemandeDocuments() == demande));
    }

    // ── Nouveau : escalade automatique vers CGEA/CGE ──

    private Agent buildSuperieur(String keycloakId, String matricule) {
        return Agent.builder()
                .id(UUID.randomUUID())
                .keycloakId(keycloakId)
                .matricule(matricule)
                .firstName("Prénom")
                .lastName("Nom")
                .actif(true)
                .build();
    }

    @Test
    void escaladeVersSuperieurs_notifieChaqueSuperieurResoluPourUnDepassementAR() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0020").build();
        Agent cgea = buildSuperieur("kc-cgea", "M100");
        Agent cge = buildSuperieur("kc-cge", "M101");
        when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGEA")).thenReturn(List.of("kc-cgea"));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cgea")).thenReturn(java.util.Optional.of(cgea));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_AR
                        && n.getDossier() == dossier
                        && "kc-cgea".equals(n.getRecipient())));
        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_AR
                        && n.getDossier() == dossier
                        && "kc-cge".equals(n.getRecipient())));
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_escalade_ar"), anyMap());
        verify(portalConfigService).resolveNotificationText(eq("notif_content_escalade_ar"), anyMap());
    }

    @Test
    void escaladeVersSuperieurs_dedupliqueUnAgentCumulantCgeaEtCge() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0021").build();
        Agent cumulard = buildSuperieur("kc-cumulard", "M102");
        when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGEA")).thenReturn(List.of("kc-cumulard"));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cumulard"));
        when(agentRepository.findByKeycloakId("kc-cumulard")).thenReturn(java.util.Optional.of(cumulard));

        service.escaladeVersSuperieurs();

        verify(notificationRepository, times(1)).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_AR
                        && "kc-cumulard".equals(n.getRecipient())));
    }

    @Test
    void escaladeVersSuperieurs_notifieChaqueSuperieurPourUnDepassementComplement() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0022").build();
        Agent cge = buildSuperieur("kc-cge", "M103");
        when(dossierRepository.findComplementsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_COMPLEMENT
                        && n.getDossier() == dossier
                        && "kc-cge".equals(n.getRecipient())));
    }

    @Test
    void escaladeVersSuperieurs_notifieChaqueSuperieurPourUneInvestigationEnDepassement() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0023").build();
        Agent cge = buildSuperieur("kc-cge", "M104");
        when(dossierRepository.findInvestigationsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_INVESTIGATION
                        && n.getDossier() == dossier
                        && "kc-cge".equals(n.getRecipient())));
    }

    @Test
    void escaladeVersSuperieurs_doesNotDuplicateAlreadyEscaladedDossier() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0024").build();
        Agent cge = buildSuperieur("kc-cge", "M105");
        when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));
        when(notificationRepository.existsByDossierIdAndType(
                dossier.getId(), NotificationType.ESCALADE_AR)).thenReturn(true);

        service.escaladeVersSuperieurs();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_AR));
    }

    @Test
    void escaladeVersSuperieurs_ignoreUnAgentKeycloakSansCorrespondanceEnBase() {
        // Le seul keycloakId resolu par Keycloak n'a pas d'Agent correspondant
        // en base -> resolveSuperieurs() renvoie une liste vide -> la garde de
        // sortie anticipee empeche toute requete d'echeance et toute sauvegarde.
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-inconnu"));
        when(agentRepository.findByKeycloakId("kc-inconnu")).thenReturn(java.util.Optional.empty());

        service.escaladeVersSuperieurs();

        verify(notificationRepository, never()).save(any());
        verify(dossierRepository, never()).findAcknowledgmentsOverdueBeyondGrace(any());
    }

    @Test
    void escaladeVersSuperieurs_ignoreUnAgentInactif() {
        // Meme raisonnement : le seul keycloakId resolu correspond a un Agent
        // inactif, filtre par resolveSuperieurs() -> liste vide -> sortie
        // anticipee.
        Agent inactif = buildSuperieur("kc-inactif", "M106");
        inactif.setActif(false);
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-inactif"));
        when(agentRepository.findByKeycloakId("kc-inactif")).thenReturn(java.util.Optional.of(inactif));

        service.escaladeVersSuperieurs();

        verify(notificationRepository, never()).save(any());
        verify(dossierRepository, never()).findAcknowledgmentsOverdueBeyondGrace(any());
    }

    @Test
    void escaladeVersSuperieurs_neInterrogeAucuneEcheanceSiAucunSuperieurResolu() {
        // Garde de sortie anticipee : sans agent CGEA/CGE resolu, inutile
        // d'interroger les echeances (rien ne pourrait etre notifie).
        service.escaladeVersSuperieurs();

        verify(dossierRepository, never()).findAcknowledgmentsOverdueBeyondGrace(any());
        verify(parametreDelaiService).resolveDelaiJours("ESCALADE_DELAI_GRACE");
    }

    @Test
    void escaladeVersSuperieurs_calculeLeSeuilAvecLeDelaiConfigure() {
        Agent cge = buildSuperieur("kc-cge", "M110");
        when(parametreDelaiService.resolveDelaiJours("ESCALADE_DELAI_GRACE")).thenReturn(5);
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(dossierRepository).findAcknowledgmentsOverdueBeyondGrace(any());
        verify(parametreDelaiService).resolveDelaiJours("ESCALADE_DELAI_GRACE");
    }

    @Test
    void escaladeVersSuperieurs_notifieChaqueSuperieurPourUneDemandeDocumentsEnDepassement() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0027").build();
        DemandeDocuments demande = buildDemande(dossier);
        Agent cge = buildSuperieur("kc-cge", "M107");
        when(demandeDocumentsRepository.findOverdueBeyondGrace(any())).thenReturn(List.of(demande));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_DEMANDE_DOCUMENTS
                        && n.getDossier() == dossier
                        && n.getDemandeDocuments() == demande
                        && "kc-cge".equals(n.getRecipient())));
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_escalade_demande_documents"), anyMap());
        verify(portalConfigService).resolveNotificationText(eq("notif_content_escalade_demande_documents"), anyMap());
    }

    @Test
    void escaladeVersSuperieurs_doesNotDuplicateDemandeDocumentsEscaladeeDansLeMemeCycle() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0028").build();
        DemandeDocuments demande = buildDemande(dossier);
        Agent cge = buildSuperieur("kc-cge", "M108");
        when(demandeDocumentsRepository.findOverdueBeyondGrace(any())).thenReturn(List.of(demande));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));
        when(notificationRepository.existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                eq(demande.getId()), eq(NotificationType.ESCALADE_DEMANDE_DOCUMENTS), any()))
                .thenReturn(true);

        service.escaladeVersSuperieurs();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_DEMANDE_DOCUMENTS));
    }

    @Test
    void escaladeVersSuperieurs_reEscaladeApresUnNouveauCycleDeSentAt() {
        // Meme raisonnement que sendDeadlineAlerts_reAlertsDemandeDocumentsAfterEscalationResetsSentAt
        // (sous-chantier precedent) : une DemandeDocuments escaladee manuellement
        // (RELANCE, SOMMATION...) reinitialise sentAt sur la meme ligne. La
        // dedup doit s'appuyer sur le sentAt COURANT, pas sur l'existence a vie
        // d'une notification ESCALADE_DEMANDE_DOCUMENTS pour cet id.
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0029").build();
        Instant currentCycleSentAt = Instant.now();
        DemandeDocuments demande = buildDemande(dossier);
        demande.setSentAt(currentCycleSentAt);
        Agent cge = buildSuperieur("kc-cge", "M109");
        when(demandeDocumentsRepository.findOverdueBeyondGrace(any())).thenReturn(List.of(demande));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));
        when(notificationRepository.existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                eq(demande.getId()), eq(NotificationType.ESCALADE_DEMANDE_DOCUMENTS), eq(currentCycleSentAt)))
                .thenReturn(false);

        service.escaladeVersSuperieurs();

        verify(notificationRepository).existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                demande.getId(), NotificationType.ESCALADE_DEMANDE_DOCUMENTS, currentCycleSentAt);
        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_DEMANDE_DOCUMENTS
                        && n.getDemandeDocuments() == demande));
    }

    @Test
    void escaladeVersSuperieurs_neCrachePasSiLeDelaiDeGraceEstIndisponible() {
        when(parametreDelaiService.resolveDelaiJours("ESCALADE_DELAI_GRACE"))
                .thenThrow(new gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException(
                        "Paramètre de délai introuvable ou inactif : ESCALADE_DELAI_GRACE"));

        org.assertj.core.api.Assertions.assertThatCode(() -> service.escaladeVersSuperieurs())
                .doesNotThrowAnyException();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void escaladeVersSuperieurs_transmetLeNumeroEtLAgentEnChargeDansLesPlaceholders() {
        Agent agentEnCharge = buildSuperieur("kc-titulaire", "M200");
        agentEnCharge.setFirstName("Awa");
        agentEnCharge.setLastName("Ouedraogo");
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID()).number("2026-0030").agentInCharge(agentEnCharge).build();
        Agent cge = buildSuperieur("kc-cge", "M201");
        when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(portalConfigService).resolveNotificationText(eq("notif_subject_escalade_ar"), argThat(m ->
                "2026-0030".equals(m.get("numero")) && "Awa Ouedraogo".equals(m.get("agentEnCharge"))));
    }

    @Test
    void escaladeVersSuperieurs_utiliseUnRepliQuandAucunAgentEnChargeAssigne() {
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID()).number("2026-0031").agentInCharge(null).build();
        Agent cge = buildSuperieur("kc-cge", "M202");
        when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(portalConfigService).resolveNotificationText(eq("notif_subject_escalade_ar"), argThat(m ->
                "agent non identifié".equals(m.get("agentEnCharge"))));
    }
}
