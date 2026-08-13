package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistDossierTravailCocheTest {

    @Test
    void builder_cocheEstFauxParDefaut() {
        ChecklistDossierTravailCoche etat = ChecklistDossierTravailCoche.builder()
                .investigation(Investigation.builder().build())
                .point(PointChecklistDossierTravail.builder().build())
                .build();

        assertThat(etat.getCoche()).isFalse();
    }
}
