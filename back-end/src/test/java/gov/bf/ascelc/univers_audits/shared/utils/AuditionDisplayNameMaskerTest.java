package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.Witness;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.service.CurrentUserAuditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditionDisplayNameMaskerTest {

    @Mock private SecurityUtils securityUtils;
    @Mock private CurrentUserAuditor auditor;

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
        when(securityUtils.hasPermission(any(AppPermission.class))).thenReturn(false);

        assertThat(masker.mask(audition))
                .isEqualTo("Lanceur d'alerte protégé");
    }

    @Test
    void mask_preservesNameForProtectedDeclarantWhenCallerIsCge() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").protectionRequested(true).build();
        Audition audition = buildDeclarantAudition(declarant, false);
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

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

    private Audition protectedAudition(String dossierNumber) {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").protectionRequested(true).build();
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number(dossierNumber)
                .declarant(declarant).anonymous(false).build();
        return Audition.builder().id(UUID.randomUUID()).intervieweeType(IntervieweeType.DECLARANT)
                .investigation(Investigation.builder().dossier(dossier).build()).build();
    }

    @Test
    void mask_traceLaConsultationDUneIdentiteProtegeeRenvoyeeEnClair() {
        Audition audition = protectedAudition("DOS-1");
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        masker.mask(audition);

        verify(auditor).record(eq("CONSULTER_IDENTITE_LANCEUR_ALERTE"), eq("AUDITION"),
                eq(audition.getId().toString()), contains("DOS-1"));
    }

    @Test
    void mask_neTracePasQuandLeNomEstMasque() {
        when(securityUtils.hasPermission(any(AppPermission.class))).thenReturn(false);

        masker.mask(protectedAudition("DOS-1"));

        verifyNoInteractions(auditor);
    }

    @Test
    void mask_neTracePasUnDossierAnonymeMemeSiLAgentEstHabilite() {
        Audition audition = protectedAudition("DOS-1");
        audition.getInvestigation().getDossier().setAnonymous(true);
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        assertThat(masker.mask(audition)).isEqualTo("Déclarant anonyme");

        verifyNoInteractions(auditor);
    }

    @Test
    void maskAll_neTraceQuUneFoisPourToutLaListeAvecLeNombreDeNomsMontres() {
        Audition a1 = protectedAudition("DOS-1");
        Audition a2 = protectedAudition("DOS-2");
        Audition temoin = Audition.builder().id(UUID.randomUUID()).intervieweeType(IntervieweeType.WITNESS)
                .witness(Witness.builder().firstName("Jean").lastName("Kaboré").build()).build();
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        List<String> noms = masker.maskAll(List.of(a1, temoin, a2));

        assertThat(noms).containsExactly("Amidou Sawadogo", "Jean Kaboré", "Amidou Sawadogo");
        verify(auditor, times(1)).record(eq("CONSULTER_IDENTITE_LANCEUR_ALERTE"), eq("AUDITION"), isNull(),
                contains("2"));
    }

    @Test
    void maskAll_neTraceRienSiAucunNomProtegeNEstMontre() {
        when(securityUtils.hasPermission(any(AppPermission.class))).thenReturn(false);

        masker.maskAll(List.of(protectedAudition("DOS-1"), protectedAudition("DOS-2")));

        verifyNoInteractions(auditor);
    }
}
