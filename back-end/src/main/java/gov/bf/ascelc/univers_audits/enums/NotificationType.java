package gov.bf.ascelc.univers_audits.enums;

/**
 * Types de notification légale envoyée au déclarant
 */
public enum NotificationType {
    // Récépissé immédiat remis au guichet (Annexe B4)
    RECEIPT_B4,
    // Accusé de réception officiel signé CGE  — 7 jours
    ACKNOWLEDGMENT_B5,
    // Demande de complément d'information — 14 jours
    COMPLEMENT_REQUEST,
    // Décision d'irrecevabilité motivée — 3 jours après CTADP
    INADMISSIBILITY_DECISION,
    // Décision de transfert vers autre institution
    TRANSFER_DECISION,
    // Décision finale du CGE
    FINAL_DECISION,
    // Alerte interne : délai légal dépassé
    DEADLINE_ALERT
}