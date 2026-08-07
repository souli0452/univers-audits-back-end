package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.model.dto.response.RegistreAuditionEntryResponse;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.shared.utils.AuditionDisplayNameMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistreAuditionsServiceImplTest {

    @Mock private AuditionRepository auditionRepository;
    @Mock private PVAuditionRepository pvAuditionRepository;
    @Mock private AuditionDisplayNameMasker displayNameMasker;

    @InjectMocks
    private RegistreAuditionsServiceImpl service;

    @Test
    void findAll_mapsAuditionsWithPvStatusAndMaskedName() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("ASCE-2026-000042").build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();

        Audition withFinalizedPv = Audition.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .intervieweeType(IntervieweeType.DECLARANT)
                .status(AuditionStatus.CONDUCTED)
                .scheduledAt(Instant.now())
                .build();
        Audition withoutPv = Audition.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .scheduledAt(Instant.now())
                .build();

        PVAudition finalizedPv = PVAudition.builder()
                .audition(withFinalizedPv)
                .pvVersion(2)
                .finalizedAt(Instant.now())
                .build();

        when(auditionRepository.findAllForRegistre(any()))
                .thenReturn(new PageImpl<>(List.of(withFinalizedPv, withoutPv)));
        when(pvAuditionRepository.findByAuditionIdIn(any()))
                .thenReturn(List.of(finalizedPv));
        when(displayNameMasker.mask(withFinalizedPv)).thenReturn("Déclarant anonyme");
        when(displayNameMasker.mask(withoutPv)).thenReturn("Partie visée X");

        Page<RegistreAuditionEntryResponse> page = service.findAll(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(2);
        RegistreAuditionEntryResponse entry1 = page.getContent().get(0);
        assertThat(entry1.getDossierNumber()).isEqualTo("ASCE-2026-000042");
        assertThat(entry1.getIntervieweeDisplayName()).isEqualTo("Déclarant anonyme");
        assertThat(entry1.getPvStatus()).isEqualTo(RegistreAuditionEntryResponse.PvStatus.FINALISE);
        assertThat(entry1.getPvVersion()).isEqualTo(2);

        RegistreAuditionEntryResponse entry2 = page.getContent().get(1);
        assertThat(entry2.getPvStatus()).isEqualTo(RegistreAuditionEntryResponse.PvStatus.AUCUN_PV);
        assertThat(entry2.getPvVersion()).isZero();
        assertThat(entry2.getIntervieweeDisplayName()).isEqualTo("Partie visée X");
    }
}
