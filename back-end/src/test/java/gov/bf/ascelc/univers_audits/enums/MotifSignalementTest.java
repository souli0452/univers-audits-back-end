package gov.bf.ascelc.univers_audits.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MotifSignalementTest {

    /** Codes des allégations en base (migrations 006 et 036), pour que le motif ne renvoie jamais à un code inconnu. */
    private static final Set<String> ALLEGATIONS_OFFICIELLES = Set.of(
            "ENRICHISSEMENT_ILLICITE", "CONFLIT_INTERET", "BLANCHIMENT_CAPITAUX", "FAVORITISME", "ABUS_DE_FONCTION",
            "DEMANDE_INTERVENTION_DETTES", "FRAUDE", "CONCUSSION", "DELIT_APPARENCE", "CONFLIT_FONCIER", "CORRUPTION",
            "DETOURNEMENT_MALVERSATIONS", "STELLIONAT", "DYSFONCTIONNEMENT", "FAUX_ET_USAGE_DE_FAUX");

    @Test
    void proposeLesQuinzeAllegationsPuisAutre() {
        assertThat(MotifSignalement.values()).hasSize(16);
        assertThat(MotifSignalement.AUTRE.allegation()).isEmpty();
        assertThat(MotifSignalement.values()[15]).isEqualTo(MotifSignalement.AUTRE);
    }

    @Test
    void lesLibellesSontLesNomsOfficielsDesAllegations_dansLOrdreDeLASCE() {
        List<String> libelles = Arrays.stream(MotifSignalement.values()).map(MotifSignalement::libelle).toList();

        assertThat(libelles).containsExactly(
                "Conflit d’intérêt", "Blanchiment de capitaux", "Favoritisme", "Enrichissement illicite",
                "Abus de fonction", "Demande d’intervention pour règlement de dettes", "Fraude", "Concussion",
                "Délit d’apparence", "Conflit foncier", "Corruption", "Détournement et malversations financières",
                "Stellionat", "Dysfonctionnement", "Faux et usage de faux", "Autre");
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
    void leMotifPorteLeCodeDeSonAllegation() {
        for (MotifSignalement motif : MotifSignalement.values()) {
            motif.allegation().ifPresent(code -> assertThat(motif.name()).isEqualTo(code));
        }
    }

    @Test
    void chaqueMotifAUnLibelleEtUneExplicationEnMotsSimples() {
        for (MotifSignalement motif : MotifSignalement.values()) {
            assertThat(motif.libelle()).as("libellé de %s", motif).isNotBlank();
            assertThat(motif.explication()).as("explication de %s", motif).isNotBlank();
            assertThat(motif.explication().split("\\s+").length)
                    .as("l'explication de %s doit rester courte", motif).isLessThanOrEqualTo(25);
        }
    }

    @Test
    void deuxMotifsNeSontPasEnConcurrenceSurLaMemeAllegation() {
        List<String> allegations = Arrays.stream(MotifSignalement.values())
                .flatMap(m -> m.allegation().stream()).toList();

        assertThat(allegations).doesNotHaveDuplicates();
    }
}
