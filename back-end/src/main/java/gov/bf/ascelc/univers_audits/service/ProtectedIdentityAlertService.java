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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prévient la sécurité (DSI) dès qu'une identité de lanceur d'alerte protégé est consultée ou exportée.
 *
 * <ul>
 *   <li>Désactivé tant qu'aucune adresse n'est configurée ({@code app.security.protected-identity-alert.to}).</li>
 *   <li>Le message ne contient jamais l'identité elle-même : qui, quoi, quand, quel dossier.</li>
 *   <li>Une même personne qui répète la même action sur le même objet ne déclenche qu'un message par fenêtre
 *       ({@code ...throttle}, 1 heure par défaut), pour que l'alerte reste lisible.</li>
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
        if (!premierDansLaFenetre(acteurId + "|" + action + "|" + entityId)) {
            return;
        }
        String verbe = "EXPORTER_IDENTITE_LANCEUR_ALERTE".equals(action) ? "exporté" : "consulté";
        email.sendInternalAlert(destinataire, "Responsable sécurité",
                "Identité d'un lanceur d'alerte protégé " + verbe,
                "Personne : " + (acteurNom != null ? acteurNom : acteurId)
                        + " (rôle : " + (acteurRoles != null ? acteurRoles : "inconnu") + ")\n"
                        + description + "\n"
                        + "Heure : " + horloge.instant() + "\n"
                        + "Le détail figure dans le Journal d'audit (action " + action + ").");
    }

    /** Vrai si aucun message identique n'est parti dans la fenêtre ; réserve alors le créneau. */
    private boolean premierDansLaFenetre(String cle) {
        Instant maintenant = horloge.instant();
        dernierEnvoi.values().removeIf(t -> t.plus(fenetre).isBefore(maintenant));
        boolean[] autorise = {false};
        dernierEnvoi.compute(cle, (k, precedent) -> {
            if (precedent == null || !precedent.plus(fenetre).isAfter(maintenant)) {
                autorise[0] = true;
                return maintenant;
            }
            return precedent;
        });
        return autorise[0];
    }
}
