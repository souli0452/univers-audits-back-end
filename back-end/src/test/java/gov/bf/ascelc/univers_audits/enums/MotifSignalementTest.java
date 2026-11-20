package gov.bf.ascelc.univers_audits.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le motif est ce que le citoyen choisit en mots simples ; il renvoie à l'une des allégations officielles publiées
 * (tableau « Situation des plaintes et dénonciations sous l'angle des allégations »). Le choix de l'agent à l'étude
 * d'opportunité reste seul décisif pour les statistiques.
 */
class MotifSignalementTest {

    /** Codes des allégations en base (migrations 006 et 036), pour que le motif ne renvoie jamais à un code inconnu. */
    private static final Set<String> ALLEGATIONS_OFFICIELLES = Set.of(
            "ENRICHISSEMENT_ILLICITE", "CONFLIT_INTERET", "BLANCHIMENT_CAPITAUX", "FAVORITISME", "ABUS_DE_FONCTION",
            "DEMANDE_INTERVENTION_DETTES", "FRAUDE", "CONCUSSION", "DELIT_APPARENCE", "CONFLIT_FONCIER", "CORRUPTION",
            "DETOURNEMENT_MALVERSATIONS", "STELLIONAT", "DYSFONCTIONNEMENT", "FAUX_ET_USAGE_DE_FAUX");

    @Test
    void proposeDixChoixDontUnAutre() {
        assertThat(MotifSignalement.values()).hasSize(10);
        assertThat(MotifSignalement.AUTRE.allegation()).isEmpty();
    }

    @Test
    void chaqueMotifSaitSonAllegationOfficielleOuAucune() {
        for (MotifSignalement motif : MotifSignalement.values()) {
            Optional<String> allegation = motif.allegation();
            assertThat(allegation.map(ALLEGATIONS_OFFICIELLES::contains).orElse(true))
                    .as("allégation inconnue pour %s", motif).isTrue();
        }
    }

    @Test
    void corruptionEtDetournementRenvoientAuxAllegationsLesPlusFrequentes() {
        assertThat(MotifSignalement.CORRUPTION.allegation()).contains("CORRUPTION");
        assertThat(MotifSignalement.DETOURNEMENT.allegation()).contains("DETOURNEMENT_MALVERSATIONS");
        assertThat(MotifSignalement.FAVORITISME.allegation()).contains("FAVORITISME");
    }

    @Test
    void nePropose_auCitoyen_aucuneQualificationJuridiqueTechnique() {
        // Blanchiment, stellionat, concussion, délit d'apparence, conflit d'intérêt, dettes : réservés à l'agent.
        Set<String> proposees = new HashSet<>();
        Arrays.stream(MotifSignalement.values()).forEach(m -> m.allegation().ifPresent(proposees::add));

        assertThat(proposees).doesNotContainAnyElementsOf(List.of(
                "BLANCHIMENT_CAPITAUX", "STELLIONAT", "CONCUSSION", "DELIT_APPARENCE", "CONFLIT_INTERET",
                "DEMANDE_INTERVENTION_DETTES"));
    }

    @Test
    void chaqueMotifAUnLibelleEnMotsSimplesEtUneExplication() {
        for (MotifSignalement motif : MotifSignalement.values()) {
            assertThat(motif.libelle()).as("libellé de %s", motif).isNotBlank();
            assertThat(motif.explication()).as("explication de %s", motif).isNotBlank();
        }
    }

    @Test
    void deuxMotifsNeSontPasEnConcurrenceSurLaMemeAllegation() {
        List<String> allegations = Arrays.stream(MotifSignalement.values())
                .flatMap(m -> m.allegation().stream()).toList();

        assertThat(allegations).doesNotHaveDuplicates();
    }
}
