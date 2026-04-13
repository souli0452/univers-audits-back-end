package gov.bf.ascelc.univers_audits.enums;

/**
 * Statut d'envoi d'une notification.
 */
public enum NotificationStatus {
    // En attente d'envoi par le service planifié
    PENDING,
    // Envoyée avec succès
    SENT,
    // Échec d'envoi — nouvelle tentative possible (max 3)
    FAILED,
    // Annulée manuellement avant envoi
    CANCELLED
}
