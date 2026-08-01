package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
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
}
