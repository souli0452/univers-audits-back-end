package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.repository.StatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfExportServiceTest {

    @Mock private DossierService dossierService;
    @Mock private StatusHistoryRepository statusHistoryRepository;

    @InjectMocks
    private PdfExportService service;

    @Test
    void exportRecepisse_producesNonEmptyPdfForNamedDeclarant() {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000042")
                .accessCode("ABCD1234")
                .type(TypeSaisine.DENONCIATION)
                .quality(QualiteDeclarant.TEMOIN)
                .submissionMode(SubmissionMode.IN_PERSON)
                .object("Marché public suspect")
                .receptionDate(Instant.now())
                .declarant(DeclarantResponse.builder()
                        .firstName("Awa")
                        .lastName("Ouedraogo")
                        .anonymous(false)
                        .build())
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        byte[] pdf = service.exportRecepisse(dossierId);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void exportRecepisse_producesNonEmptyPdfForAnonymousDeclarant() {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000043")
                .accessCode("EFGH5678")
                .type(TypeSaisine.DENONCIATION)
                .quality(QualiteDeclarant.TEMOIN)
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Détournement présumé")
                .receptionDate(Instant.now())
                .declarant(DeclarantResponse.builder()
                        .anonymous(true)
                        .build())
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        byte[] pdf = service.exportRecepisse(dossierId);

        assertThat(pdf).isNotEmpty();
    }
}
