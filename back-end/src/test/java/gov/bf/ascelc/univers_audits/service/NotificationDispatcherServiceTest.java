package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherServiceTest {

    @Mock private EmailService emailService;
    @Mock private SmsService   smsService;

    @InjectMocks
    private NotificationDispatcherService service;

    @Test
    void dispatchAccessCode_omitsNameWhenDossierAnonymousEvenForReusedNamedDeclarant() {
        // Le Declarant reutilise porte un vrai nom (typeDeclarant=CITIZEN, pas
        // ANONYMOUS) : avant ce correctif, resolveDisplayName lisait
        // declarant.isAnonymous() (toujours false ici) et aurait revele le nom
        // malgre la demande d'anonymat sur CE dossier.
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .email("awa@example.com")
                .build();
        Dossier dossier = Dossier.builder()
                .declarant(declarant)
                .accessCode("ABCD1234")
                .anonymous(true)
                .build();

        service.dispatchAccessCode(dossier);

        verify(emailService).sendAccessCode(
                eq("awa@example.com"), eq("ABCD1234"), isNull());
    }

    @Test
    void dispatchAccessCode_includesNameWhenDossierNotAnonymous() {
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .email("awa@example.com")
                .build();
        Dossier dossier = Dossier.builder()
                .declarant(declarant)
                .accessCode("ABCD1234")
                .anonymous(false)
                .build();

        service.dispatchAccessCode(dossier);

        verify(emailService).sendAccessCode(
                eq("awa@example.com"), eq("ABCD1234"), eq("Awa Ouedraogo"));
    }
}
