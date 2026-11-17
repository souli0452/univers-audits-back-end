package gov.bf.ascelc.univers_audits.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** La liste des infractions couvre les 15 allégations du tableau publié sur le site. */
@SpringBootTest
class TypeInfractionPublicationIntegrationTest {

    @Autowired private TypeInfractionRepository infractions;

    @Test
    void lesQuinzeAllegationsDuDocumentDePublicationExistentEtSontActives() {
        var libelles = infractions.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getActif()))
                .map(t -> t.getLibelle()).toList();

        assertThat(libelles).contains(
                "Conflit d\u2019intérêt", "Blanchiment de capitaux", "Favoritisme", "Enrichissement illicite",
                "Abus de fonction", "Demande d\u2019intervention pour règlement de dettes", "Fraude", "Concussion",
                "Délit d\u2019apparence", "Conflit foncier", "Corruption", "Détournement et malversations financières",
                "Stellionat", "Dysfonctionnement", "Faux et usage de faux");
    }
}
