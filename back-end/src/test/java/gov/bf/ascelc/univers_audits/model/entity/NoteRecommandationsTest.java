package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteRecommandationsTest {

    @Test
    void isComplet_retourneVraiQuandContenuRenseigne() {
        NoteRecommandations note = NoteRecommandations.builder()
                .contenu("Recommandation n°1 : ...")
                .build();

        assertThat(note.isComplet()).isTrue();
    }

    @Test
    void isComplet_retourneFauxQuandContenuVide() {
        NoteRecommandations note = NoteRecommandations.builder()
                .contenu("   ")
                .build();

        assertThat(note.isComplet()).isFalse();
    }

    @Test
    void isComplet_retourneFauxQuandContenuNull() {
        NoteRecommandations note = NoteRecommandations.builder().build();

        assertThat(note.isComplet()).isFalse();
    }
}
