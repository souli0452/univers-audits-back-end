package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.WitnessRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.WitnessResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Witness;
import gov.bf.ascelc.univers_audits.repository.WitnessRepository;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WitnessServiceImplTest {

    @Mock private WitnessRepository    witnessRepository;
    @Mock private DossierDetailsMapper detailsMapper;
    @Mock private DossierAccessGuard   accessGuard;

    @InjectMocks
    private WitnessServiceImpl service;

    @Test
    void create_defaultsPossiblyImplicatedToFalseWhenOmitted() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        when(accessGuard.getDossierOrThrow(dossier.getId())).thenReturn(dossier);
        when(witnessRepository.save(any(Witness.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(detailsMapper.toResponse(any(Witness.class)))
                .thenReturn(WitnessResponse.builder().build());

        WitnessRequest request = WitnessRequest.builder()
                .firstName("Jean")
                .lastName("Kaboré")
                .build();

        service.create(dossier.getId(), request);

        verify(witnessRepository).save(argThat(w -> !w.isPossiblyImplicated()));
    }

    @Test
    void create_persistsPossiblyImplicatedWhenProvided() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        when(accessGuard.getDossierOrThrow(dossier.getId())).thenReturn(dossier);
        when(witnessRepository.save(any(Witness.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(detailsMapper.toResponse(any(Witness.class)))
                .thenReturn(WitnessResponse.builder().build());

        WitnessRequest request = WitnessRequest.builder()
                .firstName("Awa")
                .lastName("Sawadogo")
                .possiblyImplicated(true)
                .build();

        service.create(dossier.getId(), request);

        verify(witnessRepository).save(argThat(Witness::isPossiblyImplicated));
    }

    @Test
    void update_updatesPossiblyImplicatedWhenProvided() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Witness witness = Witness.builder()
                .id(UUID.randomUUID())
                .dossier(dossier)
                .possiblyImplicated(false)
                .build();
        when(accessGuard.getDossierOrThrow(dossier.getId())).thenReturn(dossier);
        when(witnessRepository.findById(witness.getId())).thenReturn(Optional.of(witness));
        when(witnessRepository.save(any(Witness.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(detailsMapper.toResponse(any(Witness.class)))
                .thenReturn(WitnessResponse.builder().build());

        WitnessRequest request = WitnessRequest.builder()
                .possiblyImplicated(true)
                .build();

        service.update(dossier.getId(), witness.getId(), request);

        assertThat(witness.isPossiblyImplicated()).isTrue();
    }
}
