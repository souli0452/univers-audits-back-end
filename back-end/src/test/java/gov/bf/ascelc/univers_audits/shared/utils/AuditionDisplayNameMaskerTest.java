package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.Witness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditionDisplayNameMaskerTest {

    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private AuditionDisplayNameMasker masker;

    private Audition buildDeclarantAudition(Declarant declarant, boolean anonymous) {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID())
                .declarant(declarant).anonymous(anonymous).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        return Audition.builder()
                .intervieweeType(IntervieweeType.DECLARANT)
                .investigation(investigation)
                .build();
    }

    @Test
    void mask_returnsAnonymeForAnonymousDossier() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").build();
        Audition audition = buildDeclarantAudition(declarant, true);

        assertThat(masker.mask(audition)).isEqualTo("Déclarant anonyme");
    }

    @Test
    void mask_returnsProtectionMessageForProtectedDeclarantWithoutPrivilegedRole() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").protectionRequested(true).build();
        Audition audition = buildDeclarantAudition(declarant, false);
        when(securityUtils.hasRole(anyString())).thenReturn(false);

        assertThat(masker.mask(audition))
                .isEqualTo("Lanceur d'alerte protégé (Loi N°010-2004/AN)");
    }

    @Test
    void mask_preservesNameForProtectedDeclarantWhenCallerIsCge() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").protectionRequested(true).build();
        Audition audition = buildDeclarantAudition(declarant, false);
        when(securityUtils.hasRole("CGE")).thenReturn(true);

        assertThat(masker.mask(audition)).isEqualTo("Amidou Sawadogo");
    }

    @Test
    void mask_preservesNameForNormalDeclarant() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").build();
        Audition audition = buildDeclarantAudition(declarant, false);

        assertThat(masker.mask(audition)).isEqualTo("Amidou Sawadogo");
    }

    @Test
    void mask_returnsRawNameForNonDeclarantTypes() {
        Witness witness = Witness.builder().firstName("Jean").lastName("Kaboré").build();
        Audition audition = Audition.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witness(witness)
                .build();

        assertThat(masker.mask(audition)).isEqualTo("Jean Kaboré");
    }
}
