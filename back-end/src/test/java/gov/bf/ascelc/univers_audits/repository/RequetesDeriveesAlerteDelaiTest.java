package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Une requête dérivée mal nommée ne se détecte qu'au démarrage de l'application : on valide les noms
 * contre les entités, sans contexte Spring ni base de données.
 */
class RequetesDeriveesAlerteDelaiTest {

    @Test
    void existsByDossierIdAndTypeAndEtapeCode_estValide() {
        assertThatCode(() -> new PartTree("existsByDossierIdAndTypeAndEtapeCode", Notification.class))
                .doesNotThrowAnyException();
    }

    @Test
    void findByStatusNotIn_estValide() {
        assertThatCode(() -> new PartTree("findByStatusNotIn", Dossier.class))
                .doesNotThrowAnyException();
    }
}
