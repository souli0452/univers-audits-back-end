package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;

import java.time.Instant;

/**
 * Ce qu'un calcul interne (délais d'étapes, par exemple) a besoin de savoir d'un dossier : sa date de réception et
 * son statut. Aucune donnée personnelle, donc aucune identité à masquer ni à tracer.
 */
public record DossierResume(Instant receptionDate, DossierStatus status) {
}
