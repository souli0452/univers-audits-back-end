package gov.bf.ascelc.univers_audits.enums;


public enum AttachmentStatus {
    // En attente de vérification par un agent BRPD
    PENDING_VALIDATION,
    // Vérifiée et acceptée comme preuve valide
    VALIDATED,
    // Rejetée (format invalide, hors sujet, fichier corrompu)
    REJECTED,
    // Archivée après clôture du dossier
    ARCHIVED
}