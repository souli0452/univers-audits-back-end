package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequeteParquetTest {

    @Test
    void isComplet_retourneVraiSiContenuRenseigne() {
        RequeteParquet requete = RequeteParquet.builder().contenu("Faits et qualification").build();

        assertThat(requete.isComplet()).isTrue();
    }

    @Test
    void isComplet_retourneFauxSiContenuVide() {
        RequeteParquet requete = RequeteParquet.builder().contenu("   ").build();

        assertThat(requete.isComplet()).isFalse();
    }

    @Test
    void isComplet_retourneFauxSiContenuNull() {
        RequeteParquet requete = RequeteParquet.builder().build();

        assertThat(requete.isComplet()).isFalse();
    }
}
