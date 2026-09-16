package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.IndiceFraudeRequest;
import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;
import gov.bf.ascelc.univers_audits.repository.IndiceFraudeRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndiceFraudeServiceImplTest {

    @Mock
    private IndiceFraudeRepository repository;

    @InjectMocks
    private IndiceFraudeServiceImpl service;

    @Test
    void findAllActifs_sansCategorie_delegueAuxActifsOrdonnes() {
        IndiceFraude indice = IndiceFraude.builder().code("MP_SURFACTURATION").build();
        when(repository.findByActifTrueOrderByOrdreAsc())
                .thenReturn(List.of(indice));

        List<IndiceFraude> result = service.findAllActifs(null);

        assertThat(result).containsExactly(indice);
        verify(repository).findByActifTrueOrderByOrdreAsc();
        verify(repository, never()).findByActifTrueAndCategorieOrderByOrdreAsc(any());
    }

    @Test
    void findAllActifs_avecCategorie_delegueAuFiltreParCategorie() {
        IndiceFraude indice = IndiceFraude.builder()
                .code("MP_SURFACTURATION")
                .categorie("Marchés publics")
                .build();
        when(repository.findByActifTrueAndCategorieOrderByOrdreAsc("Marchés publics"))
                .thenReturn(List.of(indice));

        List<IndiceFraude> result = service.findAllActifs("Marchés publics");

        assertThat(result).containsExactly(indice);
        verify(repository).findByActifTrueAndCategorieOrderByOrdreAsc("Marchés publics");
        verify(repository, never()).findByActifTrueOrderByOrdreAsc();
    }

    @Test
    void create_savesNewIndice() {
        when(repository.existsByCode("MP_SURFACTURATION")).thenReturn(false);
        when(repository.save(any(IndiceFraude.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        IndiceFraudeRequest request = IndiceFraudeRequest.builder()
                .code("MP_SURFACTURATION")
                .libelle("Surfacturation sur marché public")
                .categorie("Marchés publics")
                .description("Ecart significatif entre prix facturé et prix de marché observé")
                .actif(true)
                .ordre(1)
                .build();

        IndiceFraude result = service.create(request);

        assertThat(result.getCode()).isEqualTo("MP_SURFACTURATION");
        assertThat(result.getCategorie()).isEqualTo("Marchés publics");
        verify(repository).save(any(IndiceFraude.class));
    }

    @Test
    void create_throwsConflictWhenCodeAlreadyExists() {
        when(repository.existsByCode("MP_SURFACTURATION")).thenReturn(true);

        IndiceFraudeRequest request = IndiceFraudeRequest.builder()
                .code("MP_SURFACTURATION")
                .libelle("Surfacturation sur marché public")
                .actif(true)
                .build();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_updatesExistingIndice() {
        IndiceFraude existing = IndiceFraude.builder()
                .code("MP_SURFACTURATION")
                .libelle("Ancien libellé")
                .actif(true)
                .ordre(1)
                .build();
        when(repository.findByCode("MP_SURFACTURATION")).thenReturn(Optional.of(existing));
        when(repository.save(any(IndiceFraude.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        IndiceFraudeRequest request = IndiceFraudeRequest.builder()
                .code("MP_SURFACTURATION")
                .libelle("Nouveau libellé")
                .categorie("Marchés publics")
                .description("Nouvelle description")
                .actif(false)
                .ordre(2)
                .build();

        IndiceFraude result = service.update("MP_SURFACTURATION", request);

        assertThat(result.getLibelle()).isEqualTo("Nouveau libellé");
        assertThat(result.getCategorie()).isEqualTo("Marchés publics");
        assertThat(result.getActif()).isFalse();
        assertThat(result.getOrdre()).isEqualTo(2);
        verify(repository).save(existing);
    }

    @Test
    void update_throwsWhenCodeUnknown() {
        when(repository.findByCode("INCONNU")).thenReturn(Optional.empty());

        IndiceFraudeRequest request = IndiceFraudeRequest.builder()
                .code("INCONNU")
                .libelle("Libellé")
                .actif(true)
                .build();

        assertThatThrownBy(() -> service.update("INCONNU", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
