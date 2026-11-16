package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.EtapeCircuitRapport;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Prévient, dans le portail, les personnes dont c'est le tour d'approuver le rapport d'enquête (ou de le corriger
 * après un rejet). Sans cela, personne n'apprenait qu'un rapport l'attendait. Ne fait jamais échouer l'action
 * métier : une panne est seulement journalisée.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitRapportNotifier {

    private final AgentService agentService;
    private final NotificationRepository notifications;

    /** Le rapport attend la décision de cette étape. */
    public void prevenirEtape(Investigation inv, EtapeCircuitRapport etape) {
        String numero = numero(inv);
        envoyer(inv, etape,
                "Rapport d'enquête à approuver (" + etape.libelle() + ") — dossier " + numero,
                "Le rapport d'enquête du dossier " + numero + " attend votre décision (" + etape.libelle() + ").");
    }

    /** Le rapport a été rejeté : cette étape doit le corriger. */
    public void prevenirRejet(Investigation inv, EtapeCircuitRapport etapeACorriger, String motif) {
        String numero = numero(inv);
        envoyer(inv, etapeACorriger,
                "Rapport d'enquête renvoyé (" + etapeACorriger.libelle() + ") — dossier " + numero,
                "Le rapport d'enquête du dossier " + numero + " vous est renvoyé. Motif : " + motif);
    }

    private void envoyer(Investigation inv, EtapeCircuitRapport etape, String sujet, String contenu) {
        try {
            Dossier dossier = inv.getDossier();
            for (Agent destinataire : agentService.findActiveByKeycloakRole(etape.roleKey())) {
                if (destinataire.getKeycloakId() == null) {
                    continue;
                }
                notifications.save(Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.INTERNAL_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .recipient(destinataire.getKeycloakId())
                        .subject(sujet)
                        .content(contenu)
                        .scheduledAt(Instant.now())
                        .build());
            }
        } catch (RuntimeException e) {
            log.error("[CircuitRapport] Notification non envoyée à l'étape {} (investigation {}) : {}",
                    etape, inv.getId(), e.getMessage());
        }
    }

    private static String numero(Investigation inv) {
        Dossier dossier = inv.getDossier();
        return dossier != null && dossier.getNumber() != null ? dossier.getNumber() : "(en attente de numéro)";
    }
}
