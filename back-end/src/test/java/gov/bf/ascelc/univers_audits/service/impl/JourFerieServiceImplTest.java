package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.JourFerieRequest;
import gov.bf.ascelc.univers_audits.model.entity.JourFerie;
import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JourFerieServiceImplTest {

    @Mock
    private JourFerieRepository repository;

    @InjectMocks
    private JourFerieServiceImpl service;

    @Test
    void create_savesNewJourFerie() {
        when(repository.existsByDate(LocalDate.of(2027, 1, 1))).thenReturn(false);
        when(repository.save(any(JourFerie.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourFerieRequest request = JourFerieRequest.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Jour de l'An")
                .actif(true)
                .build();

        JourFerie result = service.create(request);

        assertThat(result.getDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(result.getLibelle()).isEqualTo("Jour de l'An");
        verify(repository).save(any(JourFerie.class));
    }

    @Test
    void create_throwsConflictWhenDateAlreadyExists() {
        when(repository.existsByDate(LocalDate.of(2027, 1, 1))).thenReturn(true);

        JourFerieRequest request = JourFerieRequest.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Jour de l'An")
                .actif(true)
                .build();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_updatesExistingJourFerie() {
        UUID id = UUID.randomUUID();
        JourFerie existing = JourFerie.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Ancien libellé")
                .actif(true)
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(JourFerie.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourFerieRequest request = JourFerieRequest.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Nouveau libellé")
                .actif(false)
                .build();

        JourFerie result = service.update(id, request);

        assertThat(result.getLibelle()).isEqualTo("Nouveau libellé");
        assertThat(result.getActif()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void update_throwsWhenIdUnknown() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        JourFerieRequest request = JourFerieRequest.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Libellé")
                .actif(true)
                .build();

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
