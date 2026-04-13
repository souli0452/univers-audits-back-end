package gov.bf.ascelc.univers_audits.enums;

/**
 *  WORKFLOW COMPLET :
 *
 *  SOUMIS
 *    ↓ Agent BRPD — délai max : 7 jours
 *  RECU
 *    ↓ Conseiller juridique — délai : 7 jours
 *  EN_ETUDE_OPPORTUNITE
 *    ↓ Si informations manquantes
 *  EN_ATTENTE_COMPLEMENT ←→ retour EN_ETUDE_OPPORTUNITE
 *    ↓ Réunion hebdomadaire du comité
 *  EN_REVUE_CTADP
 *    ↓ Décision formelle du CGE
 *    ├── RECEVABLE
 *    │     ↓ Enquête terrain (90 jours extensibles)
 *    │   EN_INVESTIGATION
 *    │     ↓ Rapport remis par l'équipe
 *    │   RAPPORT_PRODUIT
 *    │     ↓ Approbation DEI (15j) + Conseiller (10j) + CGE (20j)
 *    │   DECISION_RENDUE
 *    │     ↓
 *    │   CLOS ← État terminal pour les dossiers traités
 *    │
 *    ├── IRRECEVABLE
 *    │     ↓ Réponse motivée au déclarant (3 jours)
 *    │   CLASSE ← État terminal pour les dossiers classés
 *    │
 *    └── TRANSFERE
 *          ↓ Transmission à institution compétente (7 jours)
 *        CLASSE ← État terminal pour les dossiers transférés
 */
public enum DossierStatus {

    SOUMIS,
    RECU,
    EN_ETUDE_OPPORTUNITE,
    EN_ATTENTE_COMPLEMENT,
    EN_REVUE_CTADP,
    RECEVABLE,
    IRRECEVABLE,
    TRANSFERE,
    EN_INVESTIGATION,
    RAPPORT_PRODUIT,
    DECISION_RENDUE,
    CLOS,
    CLASSE
}