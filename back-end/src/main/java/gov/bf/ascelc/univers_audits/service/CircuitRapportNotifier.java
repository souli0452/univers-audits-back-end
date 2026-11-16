package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.EtapeCircuitRapport;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Prévient, dans le portail, les personnes dont c'est le tour d'approuver le rapport d'enquête (ou de le corriger
 * après un rejet). Sans cela, personne n'apprenait qu'un rapport l'attendait.
 *
 * <p>Les notifications partent <b>après</b> la validation de l'action métier et dans leur propre transaction : une
 * approbation annulée n'envoie rien, et une panne de notification (base, Keycloak) ne peut jamais annuler une
 * approbation déjà faite — elle est seulement journalisée.
 */
@Slf4j
@Component
public class CircuitRapportNotifier {

    private final AgentService agentService;
    private final NotificationRepository notifications;
    private final TransactionTemplate transactionIndependante;

    public CircuitRapportNotifier(AgentService agentService, NotificationRepository notifications,
                                  PlatformTransactionManager transactions) {
        this.agentService = agentService;
        this.notifications = notifications;
        this.transactionIndependante = new TransactionTemplate(transactions);
        this.transactionIndependante.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Le rapport attend la décision de cette étape. */
    public void prevenirEtape(Investigation inv, EtapeCircuitRapport etape) {
        String numero = numero(inv);
        apresValidation(inv, etape,
                "Rapport d'enquête à approuver (" + etape.libelle() + ") — dossier " + numero,
                "Le rapport d'enquête du dossier " + numero + " attend votre décision (" + etape.libelle() + ").");
    }

    /** Le rapport a été rejeté : cette étape doit le corriger. */
    public void prevenirRejet(Investigation inv, EtapeCircuitRapport etapeACorriger, String motif) {
        String numero = numero(inv);
        apresValidation(inv, etapeACorriger,
                "Rapport d'enquête renvoyé (" + etapeACorriger.libelle() + ") — dossier " + numero,
                "Le rapport d'enquête du dossier " + numero + " vous est renvoyé. Motif : " + motif);
    }

    private void apresValidation(Investigation inv, EtapeCircuitRapport etape, String sujet, String contenu) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envoyer(inv, etape, sujet, contenu);
                }
            });
        } else {
            envoyer(inv, etape, sujet, contenu);
        }
    }

    private void envoyer(Investigation inv, EtapeCircuitRapport etape, String sujet, String contenu) {
        try {
            Dossier dossier = inv.getDossier();
            for (Agent destinataire : agentService.findActiveByKeycloakRole(etape.roleKey())) {
                if (destinataire.getKeycloakId() == null) {
                    continue;
                }
                transactionIndependante.executeWithoutResult(statut -> notifications.save(Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.INTERNAL_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .recipient(destinataire.getKeycloakId())
                        .subject(sujet)
                        .content(contenu)
                        .scheduledAt(Instant.now())
                        .build()));
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
