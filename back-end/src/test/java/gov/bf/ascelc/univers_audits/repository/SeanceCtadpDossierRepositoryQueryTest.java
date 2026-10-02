package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Une requête dérivée mal nommée ne se détecte qu'au démarrage de l'application. Ce test valide le nom
 * de la méthode contre l'entité, sans contexte Spring ni base de données.
 */
class SeanceCtadpDossierRepositoryQueryTest {

    @Test
    void findByDossierIdOrderByCreatedAtAsc_estUneRequeteDeriveeValide() {
        assertThatCode(() -> new PartTree("findByDossierIdOrderByCreatedAtAsc", SeanceCtadpDossier.class))
                .doesNotThrowAnyException();
    }
}
