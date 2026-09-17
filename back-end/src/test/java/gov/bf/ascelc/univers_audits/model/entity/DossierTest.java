package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DossierTest {

    @Test
    void registerReception_setsDeadlinesFromGivenInstants() {
        Dossier dossier = Dossier.builder().build();
        Agent agent = Agent.builder().build();
        Instant receptionDate = Instant.parse("2026-01-01T00:00:00Z");
        Instant acknowledgmentDeadline = receptionDate.plus(Duration.ofDays(7));
        Instant additionalInfoDeadline = receptionDate.plus(Duration.ofDays(14));

        dossier.registerReception(agent, receptionDate, acknowledgmentDeadline, additionalInfoDeadline);

        assertThat(dossier.getReceptionDate()).isEqualTo(receptionDate);
        assertThat(dossier.getAcknowledgmentDeadline()).isEqualTo(acknowledgmentDeadline);
        assertThat(dossier.getAdditionalInfoDeadline()).isEqualTo(additionalInfoDeadline);
        assertThat(dossier.getStatus()).isEqualTo(DossierStatus.RECU);
        assertThat(dossier.getAgentInCharge()).isEqualTo(agent);
    }
}
