package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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
    }

    @Test
    void sendDeadlineAlerts_createsJ3AlertForComplementDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0006").build();
        when(dossierRepository.findComplementsDueWithin(any(), any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.COMPLEMENT_ALERT_J3 && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_createsJ3AlertForInvestigationDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0007").build();
        when(dossierRepository.findInvestigationsDueWithin(any(), any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.INVESTIGATION_ALERT_J3 && n.getDossier() == dossier));
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
    }

    @Test
    void sendDeadlineAlerts_doesNotDuplicateDemandeDocumentsAlert() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0011").build();
        DemandeDocuments demande = buildDemande(dossier);
        when(demandeDocumentsRepository.findOverdue(any())).thenReturn(List.of(demande));
        when(notificationRepository.existsByDemandeDocumentsIdAndType(
                demande.getId(), NotificationType.DEMANDE_DOCUMENTS_ALERT)).thenReturn(true);

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
        when(notificationRepository.existsByDemandeDocumentsIdAndType(
                firstDemande.getId(), NotificationType.DEMANDE_DOCUMENTS_ALERT)).thenReturn(true);
        when(notificationRepository.existsByDemandeDocumentsIdAndType(
                secondDemande.getId(), NotificationType.DEMANDE_DOCUMENTS_ALERT)).thenReturn(false);

        service.sendDeadlineAlerts();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getDemandeDocuments() == firstDemande));
        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT
                        && n.getDemandeDocuments() == secondDemande));
    }
}
