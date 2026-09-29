package gov.bf.ascelc.univers_audits.model.dto.response;

import java.time.Instant;

/** Ce que le déclarant voit d'une demande de complément : aucune donnée d'identité. */
public record ComplementRequestResponse(
        String status,
        String motif,
        Instant requestedAt,
        Instant deadline,
        boolean overdue) {
}
