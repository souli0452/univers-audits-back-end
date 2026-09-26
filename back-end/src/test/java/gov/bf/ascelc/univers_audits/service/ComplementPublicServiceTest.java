package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import gov.bf.ascelc.univers_audits.model.dto.response.ComplementRequestResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.ComplementSubmissionResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.model.entity.Observation;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplementPublicServiceTest {

    static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Mock private DossierRepository dossierRepository;
    @Mock private ObservationRepository observationRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AttachmentStorageService attachmentStorageService;
    @Mock private DossierAuditRecorder auditRecorder;
    @Mock private AuditService auditService;
    @InjectMocks private ComplementPublicService service;

    private final UUID dossierId = UUID.randomUUID();
    private final Agent demandeur = Agent.builder().firstName("Issouf").lastName("Souli").build();

    private Dossier dossier(DossierStatus status, Instant deadline) {
        return Dossier.builder().id(dossierId).accessCode("ABCD1234")
                .status(status).additionalInfoDeadline(deadline).build();
    }

    private Observation demande() {
        return Observation.builder().type(ObservationType.COMPLEMENT_REQUEST)
                .content("Veuillez fournir les justificatifs de paiement")
                .author(demandeur)
                .createdAt(Instant.parse("2026-09-20T09:00:00Z")).build();
    }

    private void givenDossierEnAttente(Instant deadline) {
        when(dossierRepository.findByAccessCode("ABCD1234"))
                .thenReturn(Optional.of(dossier(DossierStatus.EN_ATTENTE_COMPLEMENT, deadline)));
        when(observationRepository.findTopByDossierIdAndTypeOrderByCreatedAtDesc(
                dossierId, ObservationType.COMPLEMENT_REQUEST)).thenReturn(Optional.of(demande()));
    }

    // ── Lecture de la demande ───────────────────────────────────────────────

    @Test
    void getComplementRequest_renvoie_le_motif_et_l_echeance() {
        Instant deadline = Instant.parse("2026-09-30T23:59:59Z");
        givenDossierEnAttente(deadline);

        ComplementRequestResponse r = service.getComplementRequest("ABCD1234", NOW);

        assertThat(r.status()).isEqualTo("EN_ATTENTE_COMPLEMENT");
        assertThat(r.motif()).isEqualTo("Veuillez fournir les justificatifs de paiement");
        assertThat(r.requestedAt()).isEqualTo(Instant.parse("2026-09-20T09:00:00Z"));
        assertThat(r.deadline()).isEqualTo(deadline);
        assertThat(r.overdue()).isFalse();
    }

    @Test
    void getComplementRequest_signale_le_retard_quand_l_echeance_est_depassee() {
        givenDossierEnAttente(Instant.parse("2026-09-25T23:59:59Z"));

        assertThat(service.getComplementRequest("ABCD1234", NOW).overdue()).isTrue();
    }

    @Test
    void getComplementRequest_sans_echeance_n_est_jamais_en_retard() {
        givenDossierEnAttente(null);

        ComplementRequestResponse r = service.getComplementRequest("ABCD1234", NOW);

        assertThat(r.deadline()).isNull();
        assertThat(r.overdue()).isFalse();
    }

    @Test
    void getComplementRequest_code_inconnu_donne_404() {
        when(dossierRepository.findByAccessCode("ZZZZZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getComplementRequest("ZZZZZZZZ", NOW))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getComplementRequest_dossier_qui_n_attend_pas_de_complement_donne_409() {
        when(dossierRepository.findByAccessCode("ABCD1234"))
                .thenReturn(Optional.of(dossier(DossierStatus.EN_ETUDE_OPPORTUNITE, null)));

        assertThatThrownBy(() -> service.getComplementRequest("ABCD1234", NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Aucun complément n'est attendu pour ce dossier");
    }

    // ── Dépôt de la réponse ─────────────────────────────────────────────────

    private static MultipartFile fichier(String nom) {
        return new MockMultipartFile("files", nom, "application/pdf", "contenu".getBytes());
    }

    private static AttachmentStorageService.UploadedFile recu(String nom) {
        return new AttachmentStorageService.UploadedFile("id-" + nom, nom, "application/pdf", 7L, false);
    }

    @Test
    void submitComplement_message_seul_fait_repasser_le_dossier_en_etude() {
        givenDossierEnAttente(Instant.parse("2026-09-30T23:59:59Z"));

        ComplementSubmissionResponse r = service.submitComplement(
                "ABCD1234", "  Voici mes justificatifs  ", null, "10.0.0.1", NOW);

        assertThat(r.status()).isEqualTo("EN_ETUDE_OPPORTUNITE");
        assertThat(r.late()).isFalse();
        assertThat(r.filesUploaded()).isZero();

        ArgumentCaptor<Dossier> saved = ArgumentCaptor.forClass(Dossier.class);
        verify(dossierRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DossierStatus.EN_ETUDE_OPPORTUNITE);

        verify(auditRecorder).addDeclarantObservation(any(Dossier.class),
                eq(ObservationType.COMPLEMENT_RESPONSE), eq("Voici mes justificatifs"),
                eq(demandeur), eq("Déclarant (via le portail)"));
        verify(auditRecorder).recordStatusChange(any(Dossier.class),
                eq(DossierStatus.EN_ATTENTE_COMPLEMENT), eq(DossierStatus.EN_ETUDE_OPPORTUNITE),
                eq("Complément reçu du déclarant"), eq(null), eq("10.0.0.1"));
        verify(auditService).logAction(eq(null), eq("Déclarant (via le portail)"), eq(null),
                eq("RECEVOIR_COMPLEMENT"), eq("DOSSIER"), eq(dossierId.toString()),
                anyString(), eq("10.0.0.1"), eq(null));
        verifyNoInteractions(attachmentStorageService);
    }

    @Test
    void submitComplement_cree_une_alerte_interne_pour_le_dossier() {
        givenDossierEnAttente(null);

        service.submitComplement("ABCD1234", "Voici", null, "10.0.0.1", NOW);

        ArgumentCaptor<Notification> notif = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notif.capture());
        assertThat(notif.getValue().getType()).isEqualTo(NotificationType.INTERNAL_ALERT);
        assertThat(notif.getValue().getChannel()).isEqualTo(NotificationChannel.PORTAL);
        assertThat(notif.getValue().getSubject()).isEqualTo("Complément reçu");
        assertThat(notif.getValue().getScheduledAt()).isEqualTo(NOW);
    }

    @Test
    void submitComplement_fichiers_seuls_sont_deposes_avec_le_code_de_suivi() {
        givenDossierEnAttente(null);
        List<MultipartFile> files = List.of(fichier("a.pdf"), fichier("b.pdf"));
        when(attachmentStorageService.upload(dossierId.toString(), files, "ABCD1234",
                null, null, null, null)).thenReturn(List.of(recu("a.pdf"), recu("b.pdf")));

        ComplementSubmissionResponse r = service.submitComplement(
                "ABCD1234", "", files, "10.0.0.1", NOW);

        assertThat(r.filesUploaded()).isEqualTo(2);
        verify(auditRecorder).addDeclarantObservation(any(Dossier.class),
                eq(ObservationType.COMPLEMENT_RESPONSE),
                eq("Pièces jointes uniquement (2 fichier(s))."), eq(demandeur), anyString());
    }

    @Test
    void submitComplement_apres_l_echeance_est_accepte_et_marque_en_retard() {
        givenDossierEnAttente(Instant.parse("2026-09-25T23:59:59Z"));

        ComplementSubmissionResponse r = service.submitComplement(
                "ABCD1234", "Désolé du retard", null, "10.0.0.1", NOW);

        assertThat(r.late()).isTrue();
        assertThat(r.status()).isEqualTo("EN_ETUDE_OPPORTUNITE");
        verify(auditRecorder).addDeclarantObservation(any(Dossier.class),
                eq(ObservationType.COMPLEMENT_RESPONSE),
                eq("Reçu en retard (échéance du 25/09/2026) — Désolé du retard"),
                eq(demandeur), anyString());
        verify(auditRecorder).recordStatusChange(any(Dossier.class), any(), any(),
                eq("Complément reçu du déclarant (en retard)"), eq(null), anyString());
    }

    @Test
    void submitComplement_les_fichiers_vides_sont_ignores() {
        givenDossierEnAttente(null);
        MultipartFile vide = new MockMultipartFile("files", "vide.pdf", "application/pdf", new byte[0]);

        ComplementSubmissionResponse r = service.submitComplement(
                "ABCD1234", "Message", List.of(vide), "10.0.0.1", NOW);

        assertThat(r.filesUploaded()).isZero();
        verifyNoInteractions(attachmentStorageService);
    }

    @Test
    void submitComplement_sans_message_ni_fichier_donne_400_sans_rien_lire() {
        assertThatThrownBy(() -> service.submitComplement("ABCD1234", "   ", null, "10.0.0.1", NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Joignez un message ou au moins un fichier");

        verifyNoInteractions(dossierRepository, attachmentStorageService, auditRecorder);
    }

    @Test
    void submitComplement_avec_uniquement_des_fichiers_vides_donne_400() {
        MultipartFile vide = new MockMultipartFile("files", "vide.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.submitComplement("ABCD1234", null, List.of(vide), "10.0.0.1", NOW))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(dossierRepository);
    }

    @Test
    void submitComplement_message_trop_long_donne_400() {
        String trop = "x".repeat(ComplementPublicService.MESSAGE_MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.submitComplement("ABCD1234", trop, null, "10.0.0.1", NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void submitComplement_plus_de_cinq_fichiers_donne_400() {
        List<MultipartFile> six = List.of(fichier("1"), fichier("2"), fichier("3"),
                fichier("4"), fichier("5"), fichier("6"));

        assertThatThrownBy(() -> service.submitComplement("ABCD1234", "Message", six, "10.0.0.1", NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5");

        verifyNoInteractions(dossierRepository, attachmentStorageService);
    }

    @Test
    void submitComplement_dossier_qui_n_attend_pas_de_complement_donne_409_sans_ecriture() {
        when(dossierRepository.findByAccessCode("ABCD1234"))
                .thenReturn(Optional.of(dossier(DossierStatus.EN_ETUDE_OPPORTUNITE, null)));

        assertThatThrownBy(() -> service.submitComplement("ABCD1234", "Message", null, "10.0.0.1", NOW))
                .isInstanceOf(ConflictException.class);

        verify(dossierRepository, never()).save(any());
        verifyNoInteractions(attachmentStorageService, auditRecorder, notificationRepository, auditService);
    }

    @Test
    void submitComplement_un_fichier_refuse_n_enregistre_rien_et_ne_change_pas_le_statut() {
        givenDossierEnAttente(null);
        List<MultipartFile> files = List.of(fichier("a.exe"));
        when(attachmentStorageService.upload(anyString(), any(), anyString(), any(), any(), any(), any()))
                .thenThrow(new BusinessException("Type de fichier non autorisé"));

        assertThatThrownBy(() -> service.submitComplement("ABCD1234", "Message", files, "10.0.0.1", NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Type de fichier non autorisé");

        verify(dossierRepository, never()).save(any());
        verifyNoInteractions(auditRecorder, notificationRepository, auditService);
    }

    @Test
    void submitComplement_un_double_envoi_simultane_remonte_le_verrou_optimiste_pour_donner_409() {
        givenDossierEnAttente(null);
        when(dossierRepository.save(any(Dossier.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        Dossier.class, dossierId));

        // GlobalExceptionHandler mappe cette exception en 409 : elle ne doit pas être avalée.
        assertThatThrownBy(() -> service.submitComplement("ABCD1234", "Message", null, "10.0.0.1", NOW))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

        verifyNoInteractions(notificationRepository, auditService);
    }

    @Test
    void submitComplement_fonctionne_pour_un_dossier_sans_declarant() {
        Dossier anonyme = dossier(DossierStatus.EN_ATTENTE_COMPLEMENT, null);
        assertThat(anonyme.getDeclarant()).isNull();
        when(dossierRepository.findByAccessCode("ABCD1234")).thenReturn(Optional.of(anonyme));
        when(observationRepository.findTopByDossierIdAndTypeOrderByCreatedAtDesc(
                dossierId, ObservationType.COMPLEMENT_REQUEST)).thenReturn(Optional.of(demande()));

        ComplementSubmissionResponse r = service.submitComplement(
                "ABCD1234", "Message", null, "10.0.0.1", NOW);

        assertThat(r.status()).isEqualTo("EN_ETUDE_OPPORTUNITE");
    }
}
