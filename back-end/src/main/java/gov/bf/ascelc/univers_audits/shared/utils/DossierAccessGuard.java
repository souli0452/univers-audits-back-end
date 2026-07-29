package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Contrôle d'accès partagé pour un dossier et ses sous-ressources (témoins,
 * parties visées, notifications, pièces jointes...) : un rôle privilégié
 * (CGE/CGEA/ADMIN_DDIC) voit tout ; les autres agents doivent disposer d'une
 * habilitation nominative active sur ce dossier (DossierHabilitation).
 */
@Component
@RequiredArgsConstructor
public class DossierAccessGuard {

    private final DossierRepository             dossierRepository;
    private final AgentRepository               agentRepository;
    private final SecurityUtils                 securityUtils;
    private final DossierHabilitationRepository habilitationRepository;

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

    /** Lève BusinessException si l'agent courant n'est ni privilégié ni habilité sur ce dossier. */
    public void checkReadAccess(Dossier dossier) {
        if (canSeeConfidential()) {
            return;
        }
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        Agent agent = agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));
        boolean hasAccess = habilitationRepository
                .existsByDossierIdAndAgentIdAndRevokedAtIsNull(dossier.getId(), agent.getId());
        if (!hasAccess) {
            throw new BusinessException(
                    "Accès refusé — ce dossier ne vous est pas assigné");
        }
    }
}
