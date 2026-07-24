package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Contrôle d'accès partagé pour les sous-ressources d'un dossier (témoins,
 * parties visées, notifications, pièces jointes...) : un rôle privilégié
 * (CGE/CGEA/ADMIN_DDIC) voit tout ; les autres rôles doivent être l'agent en
 * charge du dossier. Reproduit la même règle que
 * DossierServiceImpl.findById pour éviter toute divergence entre endpoints.
 */
@Component
@RequiredArgsConstructor
public class DossierAccessGuard {

    private final DossierRepository dossierRepository;
    private final AgentRepository   agentRepository;
    private final SecurityUtils     securityUtils;

    public Dossier getDossierOrThrow(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));
    }

    public boolean canSeeConfidential() {
        return securityUtils.hasRole("CGE")
                || securityUtils.hasRole("CGEA")
                || securityUtils.hasRole("ADMIN_DDIC");
    }

    /** Lève BusinessException si l'agent courant n'est ni privilégié ni affecté au dossier. */
    public void checkReadAccess(Dossier dossier) {
        if (canSeeConfidential()) {
            return;
        }
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        Agent agent = agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));
        boolean isAssigned = dossier.getAgentInCharge() != null
                && dossier.getAgentInCharge().getId().equals(agent.getId());
        if (!isAssigned) {
            throw new BusinessException(
                    "Accès refusé — ce dossier ne vous est pas assigné");
        }
    }
}
