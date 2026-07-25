package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.ParametreDelaiRequest;
import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;
import gov.bf.ascelc.univers_audits.repository.ParametreDelaiRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParametreDelaiServiceImplTest {

    @Mock
    private ParametreDelaiRepository repository;

    @InjectMocks
    private ParametreDelaiServiceImpl service;

    @Test
    void resolveDelaiJours_returnsValueForActiveCode() {
        ParametreDelai delai = ParametreDelai.builder()
                .code("ACCUSE_RECEPTION")
                .libelle("Délai d'accusé de réception")
                .valeurJours(7)
                .actif(true)
                .build();
        when(repository.findByCode("ACCUSE_RECEPTION"))
                .thenReturn(Optional.of(delai));

        int result = service.resolveDelaiJours("ACCUSE_RECEPTION");

        assertThat(result).isEqualTo(7);
    }

    @Test
    void resolveDelaiJours_throwsWhenCodeUnknown() {
        when(repository.findByCode("INCONNU"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveDelaiJours("INCONNU"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveDelaiJours_throwsWhenCodeInactive() {
        ParametreDelai delai = ParametreDelai.builder()
                .code("ACCUSE_RECEPTION")
                .libelle("Délai d'accusé de réception")
                .valeurJours(7)
                .actif(false)
                .build();
        when(repository.findByCode("ACCUSE_RECEPTION"))
                .thenReturn(Optional.of(delai));

        assertThatThrownBy(() -> service.resolveDelaiJours("ACCUSE_RECEPTION"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveDelaiJours_throwsWhenValeurJoursIsNull() {
        ParametreDelai delai = ParametreDelai.builder()
                .code("ACCUSE_RECEPTION")
                .libelle("Délai d'accusé de réception")
                .valeurJours(null)
                .actif(true)
                .build();
        when(repository.findByCode("ACCUSE_RECEPTION"))
                .thenReturn(Optional.of(delai));

        assertThatThrownBy(() -> service.resolveDelaiJours("ACCUSE_RECEPTION"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_changesValeurJoursAndSaves() {
        ParametreDelai existing = ParametreDelai.builder()
                .code("ACCUSE_RECEPTION")
                .libelle("Délai d'accusé de réception")
                .valeurJours(7)
                .actif(true)
                .build();
        when(repository.findByCode("ACCUSE_RECEPTION"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(ParametreDelai.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ParametreDelaiRequest request = ParametreDelaiRequest.builder()
                .libelle("Délai d'accusé de réception")
                .valeurJours(10)
                .joursOuvrables(true)
                .actif(true)
                .build();

        ParametreDelai result = service.update("ACCUSE_RECEPTION", request);

        assertThat(result.getValeurJours()).isEqualTo(10);
        verify(repository).save(existing);
    }
}
