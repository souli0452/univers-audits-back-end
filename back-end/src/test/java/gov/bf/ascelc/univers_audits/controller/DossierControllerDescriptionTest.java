package gov.bf.ascelc.univers_audits.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Le journal d'audit ne doit jamais afficher « null » : le numéro n'est attribué qu'à l'enregistrement du dossier. */
class DossierControllerDescriptionTest {

    @Test
    void sansNumero_ladescriptionNeContientPasNull() {
        assertThat(DossierController.descriptionCreation(null)).isEqualTo("Création dossier (numéro attribué à l'enregistrement)");
    }

    @Test
    void numeroVide_estTraiteCommeAbsent() {
        assertThat(DossierController.descriptionCreation("  ")).isEqualTo("Création dossier (numéro attribué à l'enregistrement)");
    }

    @Test
    void avecNumero_ladescriptionLeMentionne() {
        assertThat(DossierController.descriptionCreation("ASCE-2026-000007")).isEqualTo("Création dossier : ASCE-2026-000007");
    }
}
