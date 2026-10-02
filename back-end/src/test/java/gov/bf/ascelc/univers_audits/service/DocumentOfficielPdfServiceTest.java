package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import gov.bf.ascelc.univers_audits.model.dto.response.DecisionCGEResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpDossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentOfficielPdfServiceTest {

    private final SeanceCtadpService seanceService = mock(SeanceCtadpService.class);
    private final DossierService dossierService = mock(DossierService.class);
    private final DocumentOfficielPdfService service =
            new DocumentOfficielPdfService(seanceService, dossierService);

    private SeanceCtadpResponse seance(StatutSeanceCtadp statut, List<SeanceCtadpDossierResponse> dossiers) {
        return SeanceCtadpResponse.builder()
                .id(UUID.randomUUID())
                .dateSeance(Instant.parse("2026-10-12T09:00:00Z"))
                .statut(statut)
                .participants("Président du comité\nConseiller juridique; Chef du BRPD")
                .dossiers(dossiers)
                .build();
    }

    private SeanceCtadpDossierResponse dossier() {
        return SeanceCtadpDossierResponse.builder()
                .dossierId(UUID.randomUUID())
                .dossierNumber("ASCE-LC-2026-0001")
                .dossierObject("Détournement présumé de fonds")
                .build();
    }

    @Test
    void convocation_produitUnPdfPourUneSeancePlanifiee() {
        SeanceCtadpResponse s = seance(StatutSeanceCtadp.PLANIFIEE, List.of(dossier()));
        when(seanceService.findById(s.getId())).thenReturn(s);

        byte[] pdf = service.convocationCtadp(s.getId());

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1500);
    }

    @Test
    void convocation_refuseUneSeanceDejaTenueOuAnnulee() {
        SeanceCtadpResponse s = seance(StatutSeanceCtadp.TENUE, List.of(dossier()));
        when(seanceService.findById(s.getId())).thenReturn(s);

        assertThatThrownBy(() -> service.convocationCtadp(s.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("séance planifiée");
    }

    @Test
    void convocation_refuseUneSeanceSansDossier() {
        SeanceCtadpResponse s = seance(StatutSeanceCtadp.PLANIFIEE, List.of());
        when(seanceService.findById(s.getId())).thenReturn(s);

        assertThatThrownBy(() -> service.convocationCtadp(s.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("au moins un dossier");
    }

    @Test
    void participants_decoupeParLigneEtPointVirgule() {
        assertThat(DocumentOfficielPdfService.participants("A\nB; C ;;\n"))
                .containsExactly("A", "B", "C");
        assertThat(DocumentOfficielPdfService.participants("  ")).isEmpty();
        assertThat(DocumentOfficielPdfService.participants(null)).isEmpty();
    }

    @Test
    void quitus_produitUnPdfQuandLeCgeADecide() {
        UUID id = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .id(id).number("ASCE-LC-2026-0001").object("Détournement présumé de fonds")
                .decisionCGE(DecisionCGEResponse.builder()
                        .decision(RecommandationCtadp.VALIDATION_INVESTIGATION)
                        .motif("Indices suffisants")
                        .dateDecision(Instant.parse("2026-10-12T09:00:00Z"))
                        .agentCGENom("Le CGE")
                        .build())
                .build();
        when(dossierService.findById(id)).thenReturn(dossier);

        byte[] pdf = service.quitusCge(id);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1500);
    }

    @Test
    void quitus_refuseUnDossierSansDecisionDuCge() {
        UUID id = UUID.randomUUID();
        when(dossierService.findById(id)).thenReturn(DossierResponse.builder().id(id).build());

        assertThatThrownBy(() -> service.quitusCge(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucune décision du CGE");
    }

    @Test
    void lettre_produitUnPdfPourUnDossierClos() {
        UUID id = UUID.randomUUID();
        when(dossierService.findById(id)).thenReturn(DossierResponse.builder()
                .id(id).number("ASCE-LC-2026-0001").accessCode("ABC12345")
                .status(DossierStatus.CLOS).anonymous(true)
                .receptionDate(Instant.parse("2026-03-01T09:00:00Z"))
                .closingDate(Instant.parse("2026-09-15T09:00:00Z"))
                .build());

        byte[] pdf = service.lettreInformationPlaignant(id);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1500);
    }

    @Test
    void lettre_refuseUnDossierEncoreEnCours() {
        UUID id = UUID.randomUUID();
        when(dossierService.findById(id)).thenReturn(
                DossierResponse.builder().id(id).status(DossierStatus.EN_INVESTIGATION).build());

        assertThatThrownBy(() -> service.lettreInformationPlaignant(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("traitement du dossier terminé");
    }
}
