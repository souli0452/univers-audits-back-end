package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
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
        if (estDepotEnAttenteDEnregistrement(dossier)) {
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

    /**
     * Un dépôt fait sur le portail (statut SOUMIS) n'a encore aucun agent en charge, donc aucune habilitation :
     * sans cette règle, personne au BRPD ne pourrait l'ouvrir pour l'enregistrer. Dès l'enregistrement,
     * l'agent qui l'enregistre en devient l'agent en charge (habilitation) et l'accès redevient nominatif.
     */
    public boolean estDepotEnAttenteDEnregistrement(Dossier dossier) {
        return dossier.getStatus() == DossierStatus.SOUMIS && securityUtils.hasRole("AGENT_BRPD");
    }

    /**
     * Lève BusinessException sauf pour un dépôt de pièce jointe légitime
     * sans compte : au dépôt initial (SOUMIS) ou en réponse à une demande
     * de complément (EN_ATTENTE_COMPLEMENT), la requête est anonyme et ne
     * porte aucune information d'authentification — c'est attendu, pas une
     * faille. Dans ce cas, le code de suivi du dossier (accessCode) doit
     * être fourni et correspondre, afin de prouver que l'appelant est bien
     * le déposant légitime et pas seulement quelqu'un ayant deviné/trouvé
     * l'UUID du dossier. En dehors de ces deux statuts, se comporte comme
     * checkReadAccess : authentification et habilitation nominative exigées.
     */
    public void checkAttachmentUploadAccess(Dossier dossier, String suppliedAccessCode) {
        if (dossier.getStatus() == DossierStatus.SOUMIS
                || dossier.getStatus() == DossierStatus.EN_ATTENTE_COMPLEMENT) {
            if (suppliedAccessCode == null || !suppliedAccessCode.equals(dossier.getAccessCode())) {
                throw new BusinessException(
                        "Code de suivi requis ou invalide pour déposer une pièce à ce stade");
            }
            return;
        }
        checkReadAccess(dossier);
    }
}
