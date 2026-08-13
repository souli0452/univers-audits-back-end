package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RapportEnqueteTest {

    private RapportEnquete buildComplet() {
        return RapportEnquete.builder()
                .titre("Rapport d'enquête n°1")
                .introduction("Introduction")
                .methodologie("Méthodologie")
                .informationsCollectees("Informations collectées")
                .exposeFactuelAnomalies("Exposé factuel")
                .quantificationPrejudice("Préjudice estimé à 1 000 000 FCFA")
                .conclusions("Conclusions")
                .build();
    }

    @Test
    void isComplet_retourneVraiQuandTousLesChampsRequisSontRenseignes() {
        RapportEnquete rapport = buildComplet();

        assertThat(rapport.isComplet()).isTrue();
    }

    @Test
    void isComplet_resteVraiSiReservesEstVide() {
        RapportEnquete rapport = buildComplet();
        rapport.setReserves(null);

        assertThat(rapport.isComplet()).isTrue();
    }

    @Test
    void isComplet_retourneFauxSiUnChampRequisEstVide() {
        RapportEnquete rapport = buildComplet();
        rapport.setConclusions("   ");

        assertThat(rapport.isComplet()).isFalse();
    }

    @Test
    void isComplet_retourneFauxSiUnChampRequisEstNull() {
        RapportEnquete rapport = buildComplet();
        rapport.setTitre(null);

        assertThat(rapport.isComplet()).isFalse();
    }
}
