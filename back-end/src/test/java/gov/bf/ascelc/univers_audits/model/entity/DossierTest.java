package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DossierTest {

    @Test
    void registerReception_setsDeadlinesFromGivenDelays() {
        Dossier dossier = Dossier.builder().build();
        Agent agent = Agent.builder().build();

        Instant before = Instant.now();
        dossier.registerReception(agent, 7, 14);
        Instant after = Instant.now();

        assertThat(dossier.getReceptionDate()).isBetween(before, after);
        assertThat(dossier.getAcknowledgmentDeadline())
                .isCloseTo(dossier.getReceptionDate().plus(Duration.ofDays(7)),
                        org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(dossier.getAdditionalInfoDeadline())
                .isCloseTo(dossier.getReceptionDate().plus(Duration.ofDays(14)),
                        org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(dossier.getStatus()).isEqualTo(DossierStatus.RECU);
        assertThat(dossier.getAgentInCharge()).isEqualTo(agent);
    }
}
