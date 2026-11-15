package gov.bf.ascelc.univers_audits.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prévient la sécurité (DSI) dès qu'une identité de lanceur d'alerte protégé est consultée ou exportée.
 *
 * <ul>
 *   <li>Désactivé tant qu'aucune adresse n'est configurée ({@code app.security.protected-identity-alert.to}).</li>
 *   <li>Le message ne contient jamais l'identité elle-même : qui, quoi, quand, quel dossier.</li>
 *   <li>Une même personne qui répète la même action sur le même objet ne déclenche qu'un message par fenêtre
 *       ({@code ...throttle}, 1 heure par défaut), pour que l'alerte reste lisible. Le créneau n'est conservé que si
 *       le message a bien été accepté par la messagerie ; sinon il est libéré et une erreur
 *       {@code [ALERTE-SECURITE-NON-LIVREE]} est écrite dans les logs.</li>
 * </ul>
 */
@Slf4j
@Component
public class ProtectedIdentityAlertService {

    /** Actions du journal d'audit qui concernent une identité protégée. */
    static final Set<String> ACTIONS = Set.of("CONSULTER_IDENTITE_LANCEUR_ALERTE", "EXPORTER_IDENTITE_LANCEUR_ALERTE");

    private final EmailService email;
    private final String destinataire;
    private final Duration fenetre;
    private final Clock horloge;
    private final Map<String, Instant> dernierEnvoi = new ConcurrentHashMap<>();

    @Autowired
    public ProtectedIdentityAlertService(
            EmailService email,
            @Value("${app.security.protected-identity-alert.to:}") String destinataire,
            @Value("${app.security.protected-identity-alert.throttle:PT1H}") Duration fenetre) {
        this(email, destinataire, fenetre, Clock.systemUTC());
    }

    ProtectedIdentityAlertService(EmailService email, String destinataire, Duration fenetre, Clock horloge) {
        this.email = email;
        this.destinataire = destinataire;
        this.fenetre = fenetre;
        this.horloge = horloge;
    }

    public static boolean concerne(String action) {
        return ACTIONS.contains(action);
    }

    public void notifyAccess(String acteurId, String acteurNom, String acteurRoles, String action,
                             String entityId, String description) {
        if (destinataire == null || destinataire.isBlank() || !concerne(action)) {
            return;
        }
        // La description entre dans la clé : deux listes différentes (sans identifiant commun) ne se masquent pas.
        String cle = acteurId + "|" + action + "|" + entityId + "|" + description;
        Instant reserve = reserverLeCreneau(cle);
        if (reserve == null) {
            return;
        }
        String verbe = "EXPORTER_IDENTITE_LANCEUR_ALERTE".equals(action) ? "exporté" : "consulté";
        try {
            CompletableFuture<Boolean> envoi = email.sendInternalAlertTracked(destinataire, "Responsable sécurité",
                    "Identité d'un lanceur d'alerte protégé " + verbe,
                    "Personne : " + (acteurNom != null ? acteurNom : acteurId)
                            + " (rôle : " + (acteurRoles != null ? acteurRoles : "inconnu") + ")\n"
                            + description + "\n"
                            + "Heure : " + horloge.instant() + "\n"
                            + "Le détail figure dans le Journal d'audit (action " + action + ").");
            if (envoi == null) {
                nonLivree(cle, reserve, action, "aucun résultat d'envoi", null);
                return;
            }
            envoi.whenComplete((ok, erreur) -> {
                if (erreur != null || !Boolean.TRUE.equals(ok)) {
                    nonLivree(cle, reserve, action, "le serveur de messagerie n'a pas accepté le message", erreur);
                }
            });
        } catch (RuntimeException e) {
            nonLivree(cle, reserve, action, e.getMessage(), e);
        }
    }

    /**
     * Un message non livré ne doit pas étouffer les suivants : on libère le créneau et on écrit une erreur
     * facile à repérer dans les logs, pour qu'un échec d'alerte ne passe pas inaperçu.
     */
    private void nonLivree(String cle, Instant reserve, String action, String motif, Throwable erreur) {
        dernierEnvoi.remove(cle, reserve);
        log.error("[ALERTE-SECURITE-NON-LIVREE] action {} vers {} : {}", action, destinataire, motif, erreur);
    }

    /** Réserve le créneau et le renvoie, ou {@code null} si un message identique est déjà parti dans la fenêtre. */
    private Instant reserverLeCreneau(String cle) {
        Instant maintenant = horloge.instant();
        dernierEnvoi.values().removeIf(t -> t.plus(fenetre).isBefore(maintenant));
        Instant[] reserve = {null};
        dernierEnvoi.compute(cle, (k, precedent) -> {
            if (precedent == null || !precedent.plus(fenetre).isAfter(maintenant)) {
                reserve[0] = maintenant;
                return maintenant;
            }
            return precedent;
        });
        return reserve[0];
    }
}
