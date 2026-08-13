package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointChecklistDossierTravailTest {

    @Test
    void builder_actifEstVraiParDefaut() {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .code("PT-01")
                .libelle("Point de contrôle 1")
                .ordre(1)
                .build();

        assertThat(point.getActif()).isTrue();
    }

    @Test
    void builder_actifPeutEtreDesactiveExplicitement() {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .code("PT-01")
                .libelle("Point de contrôle 1")
                .ordre(1)
                .actif(false)
                .build();

        assertThat(point.getActif()).isFalse();
    }
}
