package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProtectedIdentityExportAuditorTest {

    @Mock private CurrentUserAuditor auditor;

    private DossierResponse dossier(UUID id, DeclarantResponse declarant) {
        return DossierResponse.builder().id(id).number("DOS-2026-001").declarant(declarant).build();
    }

    private DeclarantResponse declarant(boolean protege, String prenom, String nom) {
        return DeclarantResponse.builder().protectionRequested(protege).firstName(prenom).lastName(nom).build();
    }

    @Test
    void traceUnExportQuiRendLIdentiteDUnLanceurDAlerteProtegeEnClair() {
        UUID id = UUID.randomUUID();

        new ProtectedIdentityExportAuditor(auditor)
                .record(dossier(id, declarant(true, "Awa", "Ouedraogo")), "Récépissé");

        verify(auditor).record(eq("EXPORTER_IDENTITE_LANCEUR_ALERTE"), eq("DOSSIER"), eq(id.toString()),
                contains("Récépissé"));
    }

    @Test
    void traceAussiUneIdentiteQuiNEstQueUneOrganisationUnCourrielOuUnTelephone() {
        for (DeclarantResponse declarant : java.util.List.of(
                DeclarantResponse.builder().protectionRequested(true).organizationName("Association X").build(),
                DeclarantResponse.builder().protectionRequested(true).email("a@b.bf").build(),
                DeclarantResponse.builder().protectionRequested(true).phoneNumber("70000000").build())) {
            org.mockito.Mockito.clearInvocations(auditor);

            new ProtectedIdentityExportAuditor(auditor).record(dossier(UUID.randomUUID(), declarant), "Récépissé");

            verify(auditor).record(eq("EXPORTER_IDENTITE_LANCEUR_ALERTE"), eq("DOSSIER"), org.mockito.ArgumentMatchers.anyString(),
                    contains("Récépissé"));
        }
    }

    @Test
    void identiteEnClair_estFauxQuandToutEstMasque() {
        org.assertj.core.api.Assertions.assertThat(ProtectedIdentityExportAuditor.identiteEnClair(
                DeclarantResponse.builder().protectionRequested(true).displayName("Lanceur d'alerte protégé").build()))
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(ProtectedIdentityExportAuditor.identiteEnClair(null)).isFalse();
    }

    @Test
    void neTracePasQuandLIdentiteEstMasquee() {
        new ProtectedIdentityExportAuditor(auditor)
                .record(dossier(UUID.randomUUID(), declarant(true, null, null)), "Récépissé");

        verifyNoInteractions(auditor);
    }

    @Test
    void neTracePasUnDeclarantNonProtege() {
        new ProtectedIdentityExportAuditor(auditor)
                .record(dossier(UUID.randomUUID(), declarant(false, "Awa", "Ouedraogo")), "Récépissé");

        verifyNoInteractions(auditor);
    }

    @Test
    void neTracePasUnDossierSansDeclarant() {
        new ProtectedIdentityExportAuditor(auditor).record(dossier(UUID.randomUUID(), null), "Récépissé");

        verifyNoInteractions(auditor);
    }
}
