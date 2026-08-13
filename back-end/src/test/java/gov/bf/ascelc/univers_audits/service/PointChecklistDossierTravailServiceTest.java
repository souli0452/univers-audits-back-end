package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PointChecklistDossierTravailRequest;
import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import gov.bf.ascelc.univers_audits.repository.PointChecklistDossierTravailRepository;
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
class PointChecklistDossierTravailServiceTest {

    @Mock
    private PointChecklistDossierTravailRepository repository;

    @InjectMocks
    private PointChecklistDossierTravailService service;

    @Test
    void create_genereLePremierCodeLibreQuandLeReferentielEstVide() {
        when(repository.existsByCode("PT-01")).thenReturn(false);
        when(repository.save(any(PointChecklistDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("Premier point").ordre(1).build();

        PointChecklistDossierTravail result = service.create(request);

        assertThat(result.getCode()).isEqualTo("PT-01");
        assertThat(result.getLibelle()).isEqualTo("Premier point");
        assertThat(result.getActif()).isTrue();
    }

    @Test
    void create_sauteLesCodesDejaPris() {
        when(repository.existsByCode("PT-01")).thenReturn(true);
        when(repository.existsByCode("PT-02")).thenReturn(true);
        when(repository.existsByCode("PT-03")).thenReturn(false);
        when(repository.save(any(PointChecklistDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("Troisième point").ordre(3).build();

        PointChecklistDossierTravail result = service.create(request);

        assertThat(result.getCode()).isEqualTo("PT-03");
    }

    @Test
    void create_actifFauxExplicitementRespecte() {
        when(repository.existsByCode("PT-01")).thenReturn(false);
        when(repository.save(any(PointChecklistDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("Point désactivé").ordre(1).actif(false).build();

        PointChecklistDossierTravail result = service.create(request);

        assertThat(result.getActif()).isFalse();
    }

    @Test
    void update_metAJourLesChampsDuPointExistant() {
        PointChecklistDossierTravail existant = PointChecklistDossierTravail.builder()
                .code("PT-01").libelle("Ancien libellé").ordre(1).actif(true).build();
        when(repository.findByCode("PT-01")).thenReturn(Optional.of(existant));
        when(repository.save(any(PointChecklistDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("Nouveau libellé").categorie("PRISE_CONNAISSANCE").ordre(2).actif(false)
                .build();

        PointChecklistDossierTravail result = service.update("PT-01", request);

        assertThat(result.getLibelle()).isEqualTo("Nouveau libellé");
        assertThat(result.getCategorie()).isEqualTo("PRISE_CONNAISSANCE");
        assertThat(result.getOrdre()).isEqualTo(2);
        assertThat(result.getActif()).isFalse();
    }

    @Test
    void update_leveResourceNotFoundExceptionSiCodeInconnu() {
        when(repository.findByCode("PT-99")).thenReturn(Optional.empty());

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("X").ordre(1).build();

        assertThatThrownBy(() -> service.update("PT-99", request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void findAllActifs_delegueAuRepository() {
        List<PointChecklistDossierTravail> actifs = List.of(
                PointChecklistDossierTravail.builder().code("PT-01").ordre(1).build());
        when(repository.findByActifTrueOrderByOrdreAsc()).thenReturn(actifs);

        assertThat(service.findAllActifs()).isEqualTo(actifs);
    }
}
