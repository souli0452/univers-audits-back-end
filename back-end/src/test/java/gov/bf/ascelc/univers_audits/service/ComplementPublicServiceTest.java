package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import gov.bf.ascelc.univers_audits.model.dto.response.ComplementRequestResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Observation;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
