package gov.bf.ascelc.univers_audits.enums;


public enum ObservationType {

    // Note interne entre agents (non visible par le déclarant)
    INTERNAL_NOTE,

    // Analyse de recevabilité par le conseiller juridique
    ADMISSIBILITY_ANALYSIS,

    // Avis collectif du comité CTADP
    CTADP_OPINION,

    // Demande de complément d'information au déclarant
    COMPLEMENT_REQUEST,

    // Décision finale du CGE (investiguer, transférer, classer)
    CGE_DECISION,

    // Constat effectué sur le terrain par les contrôleurs d'État
    FIELD_FINDING,

    // Note de transfert vers une institution compétente
    TRANSFER_NOTE
}