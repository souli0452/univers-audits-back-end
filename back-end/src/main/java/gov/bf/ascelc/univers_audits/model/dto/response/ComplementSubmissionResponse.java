package gov.bf.ascelc.univers_audits.model.dto.response;

/** Résultat du dépôt d'une réponse à une demande de complément. */
public record ComplementSubmissionResponse(
        String status,
        boolean late,
        int filesUploaded) {
}
