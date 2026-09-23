package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie que les 5 nouvelles valeurs de NotificationType ajoutees par le
 * sous-chantier "alertes-delai-j3" peuvent reellement etre persistees en
 * base — c'est le test qui aurait detecte le finding 1 de la revue finale
 * de branche (le CHECK constraint notification_type_check n'avait pas ete
 * elargi en meme temps que l'enum Java, rejetant les 5 nouvelles valeurs a
 * l'insertion). ddl-auto=validate ne verifie jamais les CHECK constraints,
 * et la suite Mockito pure de NotificationServiceImplTest ne touche jamais
 * la vraie base — ce test comble ce trou et protege tout ajout futur a
 * NotificationType.
 *
 * Suit le meme patron que UniversAuditsApplicationTests : @SpringBootTest
 * nu, execute contre le vrai Postgres Docker via
 * -Dspring.profiles.active=dev (pas de Testcontainers/DB embarquee dans ce
 * depot). @Transactional annule automatiquement chaque insertion a la fin
 * de la methode de test — aucune donnee de test n'est laissee en base.
 */
@SpringBootTest
@Transactional
class NotificationTypePersistenceTest {

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {
            "DEADLINE_ALERT_J3",
            "COMPLEMENT_ALERT_J3",
            "INVESTIGATION_ALERT_J3",
            "DEMANDE_DOCUMENTS_ALERT",
            "DEMANDE_DOCUMENTS_ALERT_J3",
            "AFFECTATION_DOSSIER",
            "ESCALADE_AR",
            "ESCALADE_COMPLEMENT",
            "ESCALADE_INVESTIGATION",
            "ESCALADE_DEMANDE_DOCUMENTS"
    })
    void notification_persistsSuccessfullyForEachNewAlertType(NotificationType type) {
        Dossier dossier = Dossier.builder()
                .accessCode("T" + UUID.randomUUID().toString().substring(0, 8))
                .object("Dossier de test — persistance NotificationType")
                .type(TypeSaisine.DENONCIATION)
                .build();
        dossier = dossierRepository.saveAndFlush(dossier);

        Notification notification = Notification.builder()
                .dossier(dossier)
                .type(type)
                .channel(NotificationChannel.PORTAL)
                .subject("Sujet de test")
                .content("Contenu de test")
                .build();

        Notification saved = notificationRepository.saveAndFlush(notification);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getType()).isEqualTo(type);
    }
}
