package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.DelaiEtapeResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.StatutDelaiEtape;
import gov.bf.ascelc.univers_audits.model.entity.JourFerie;
import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DelaiEtapeCalculatorTest {

    private final JourFerieRepository jourFerieRepository = mock(JourFerieRepository.class);
    private final DelaiEtapeCalculator calculator =
            new DelaiEtapeCalculator(new DeadlineCalculator(jourFerieRepository));

    // Lundi 5 octobre 2026, 14 h 00 (UTC = heure du Burkina Faso).
    private static final Instant LUNDI_14H = Instant.parse("2026-10-05T14:00:00Z");

    private DelaiEtapeResponse evaluer(Instant debut, Instant fin, Instant maintenant) {
        return calculator.evaluer("ETAPE_TEST", "Étape", "CGEA", debut, fin, 3, true, maintenant);
    }

    @Test
    void echeance_trois_jours_ouvrables_a_la_meme_heure() {
        DelaiEtapeResponse r = evaluer(LUNDI_14H, null, Instant.parse("2026-10-05T15:00:00Z"));

        assertThat(r.getEcheance()).isEqualTo(Instant.parse("2026-10-08T14:00:00Z"));
    }

    @Test
    void echeance_saute_le_week_end() {
        Instant vendredi = Instant.parse("2026-10-02T14:00:00Z");

        DelaiEtapeResponse r = evaluer(vendredi, null, vendredi.plusSeconds(3600));

        assertThat(r.getEcheance()).isEqualTo(Instant.parse("2026-10-07T14:00:00Z")); // mercredi
    }

    @Test
    void echeance_saute_un_jour_ferie_actif() {
        JourFerie ferie = mock(JourFerie.class);
        when(ferie.getDate()).thenReturn(LocalDate.of(2026, 10, 6)); // mardi
        when(jourFerieRepository.findByActifTrueOrderByDateAsc()).thenReturn(List.of(ferie));

        DelaiEtapeResponse r = evaluer(LUNDI_14H, null, LUNDI_14H.plusSeconds(3600));

        assertThat(r.getEcheance()).isEqualTo(Instant.parse("2026-10-09T14:00:00Z")); // vendredi
    }

    @Test
    void en_cours_quand_il_reste_plus_de_24_heures() {
        DelaiEtapeResponse r = evaluer(LUNDI_14H, null, Instant.parse("2026-10-06T10:00:00Z"));

        assertThat(r.getStatut()).isEqualTo(StatutDelaiEtape.EN_COURS);
        assertThat(r.getHeuresRestantes()).isEqualTo(52L);
    }

    @Test
    void proche_quand_il_reste_moins_de_24_heures() {
        DelaiEtapeResponse r = evaluer(LUNDI_14H, null, Instant.parse("2026-10-08T08:00:00Z"));

        assertThat(r.getStatut()).isEqualTo(StatutDelaiEtape.PROCHE);
        assertThat(r.getHeuresRestantes()).isEqualTo(6L);
    }

    @Test
    void depasse_apres_l_echeance_avec_heures_negatives() {
        DelaiEtapeResponse r = evaluer(LUNDI_14H, null, Instant.parse("2026-10-09T14:00:00Z"));

        assertThat(r.getStatut()).isEqualTo(StatutDelaiEtape.DEPASSE);
        assertThat(r.getHeuresRestantes()).isEqualTo(-24L);
    }

    @Test
    void respecte_quand_termine_avant_l_echeance() {
        DelaiEtapeResponse r = evaluer(LUNDI_14H, Instant.parse("2026-10-08T13:59:00Z"),
                Instant.parse("2026-12-01T00:00:00Z"));

        assertThat(r.getStatut()).isEqualTo(StatutDelaiEtape.RESPECTE);
        assertThat(r.getHeuresRestantes()).isNull();
    }

    @Test
    void termine_en_retard_quand_la_fin_depasse_l_echeance() {
        DelaiEtapeResponse r = evaluer(LUNDI_14H, Instant.parse("2026-10-09T09:00:00Z"),
                Instant.parse("2026-12-01T00:00:00Z"));

        assertThat(r.getStatut()).isEqualTo(StatutDelaiEtape.TERMINE_EN_RETARD);
    }

    @Test
    void jours_calendaires_quand_le_parametre_n_est_pas_ouvrable() {
        Instant vendredi = Instant.parse("2026-10-02T14:00:00Z");

        DelaiEtapeResponse r = calculator.evaluer("ETAPE_TEST", "Étape", "CGEA",
                vendredi, null, 3, false, vendredi.plusSeconds(3600));

        assertThat(r.getEcheance()).isEqualTo(Instant.parse("2026-10-05T14:00:00Z")); // lundi
    }
}
