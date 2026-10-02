package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DossierMapperTest {

    private final DossierMapper mapper = new DossierMapperImpl();

    @Test
    void toResponse_computesAcknowledgmentOverdueAndDaysSinceReception() {
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID())
                .status(DossierStatus.RECU)
                .receptionDate(Instant.now().minus(5, ChronoUnit.DAYS))
                .acknowledgmentDeadline(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        DossierResponse response = mapper.toResponse(dossier);

        assertThat(response.getAcknowledgmentOverdue()).isTrue();
        assertThat(response.getDaysSinceReception()).isEqualTo(5L);
    }

    @Test
    void toResponse_notOverdueWhenDeadlineNotPassed() {
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID())
                .status(DossierStatus.RECU)
                .receptionDate(Instant.now())
                .acknowledgmentDeadline(Instant.now().plus(3, ChronoUnit.DAYS))
                .build();

        DossierResponse response = mapper.toResponse(dossier);

        assertThat(response.getAcknowledgmentOverdue()).isFalse();
        assertThat(response.getDaysSinceReception()).isEqualTo(0L);
    }

    // Couvre le champ anonymous de DossierCreateRequest#toEntity. Voir
    // DossierServiceImpl#submit, qui applique dossier.setAnonymous(anonymousRequested)
    // juste après l'appel à toEntity() pour couvrir le cas request.anonymous == null
    // (le mapper seul, testé isolément ici, ne fait aucune coalescence).

    @Test
    void toEntity_anonymousTrue_isCopiedToEntity() {
        DossierCreateRequest request = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Objet du dossier")
                .anonymous(true)
                .build();

        Dossier dossier = mapper.toEntity(request);

        assertThat(dossier.getAnonymous()).isTrue();
    }

    @Test
    void toEntity_anonymousFalse_isCopiedToEntity() {
        DossierCreateRequest request = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Objet du dossier")
                .anonymous(false)
                .build();

        Dossier dossier = mapper.toEntity(request);

        assertThat(dossier.getAnonymous()).isFalse();
    }

    @Test
    void toEntity_anonymousNull_producesNullOnEntity_mapperAloneDoesNotDefault() {
        // Documente le comportement réel, non protégé, du mapper seul : à la
        // différence de isConfidential (qui a un getter défensif dans
        // DossierCreateRequest), request.getAnonymous() == null est recopié
        // tel quel par MapStruct, ce qui écrase le @Builder.Default(false) de
        // l'entité. C'est exactement le bug de la Finding 1 : le garde-fou
        // vit dans DossierServiceImpl#submit (dossier.setAnonymous(anonymousRequested)),
        // pas dans le mapper.
        DossierCreateRequest request = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Objet du dossier")
                .anonymous(null)
                .build();

        Dossier dossier = mapper.toEntity(request);

        assertThat(dossier.getAnonymous()).isNull();
    }

    @Test
    void numeroCourrier_traverseCreationEtReponse() {
        DossierCreateRequest request = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.POSTAL_MAIL)
                .object("Objet du dossier")
                .numeroCourrier("COUR-2026-0042")
                .build();

        Dossier dossier = mapper.toEntity(request);
        DossierResponse response = mapper.toResponse(dossier);

        assertThat(dossier.getNumeroCourrier()).isEqualTo("COUR-2026-0042");
        assertThat(response.getNumeroCourrier()).isEqualTo("COUR-2026-0042");
    }
}
