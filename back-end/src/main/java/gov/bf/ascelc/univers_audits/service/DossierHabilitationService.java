package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.model.dto.request.HabilitationGrantRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;

import java.util.List;
import java.util.UUID;

public interface DossierHabilitationService {

    /** Octroi (auto-déclenché). Idempotent par source : no-op si déjà actif. */
    void grant(Dossier dossier, Agent agent, HabilitationSource source,
               Agent grantedBy, String reason);

    /** Révocation (auto-déclenchée) de l'habilitation active de cette source. No-op si absente. */
    void revokeBySource(Dossier dossier, Agent agent, HabilitationSource source);

    /** Octroi manuel (API), source = MANUAL. */
    DossierHabilitationResponse grantManual(UUID dossierId, HabilitationGrantRequest request);

    /** Révocation manuelle (API) : révoque toutes les habilitations actives de cet agent sur ce dossier. */
    void revokeManual(UUID dossierId, UUID agentId, String reason);

    List<DossierHabilitationResponse> findActiveByDossier(UUID dossierId);
}
