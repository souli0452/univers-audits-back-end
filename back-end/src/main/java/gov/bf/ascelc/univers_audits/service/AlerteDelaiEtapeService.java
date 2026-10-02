package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.model.dto.response.DelaiEtapeResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.StatutDelaiEtape;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Alerte automatique vers le CGEA et le CGE quand une étape du circuit de traitement dépasse son échéance
 * (workflow PGPD_GU V3). Une seule alerte par dossier et par étape, sans délai de grâce.
 *
 * <p>Vérification toutes les heures les jours ouvrés. Pour ne pas inonder les destinataires à la mise en
 * service avec des retards anciens, seules les échéances dépassées depuis moins de
 * {@value #FENETRE_ALERTE_JOURS} jours déclenchent une alerte ; les autres restent visibles sur la carte du
 * dossier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlerteDelaiEtapeService {

    static final int FENETRE_ALERTE_JOURS = 7;

    private static final List<DossierStatus> STATUTS_TERMINES = List.of(DossierStatus.CLOS, DossierStatus.CLASSE);
    private static final DateTimeFormatter FMT_ECHEANCE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(ZoneId.of("Africa/Ouagadougou"));

    private final DossierRepository dossierRepository;
    private final DelaiEtapeService delaiEtapeService;
    private final NotificationRepository notificationRepository;
    private final PortalConfigService portalConfigService;
    private final SuperieursResolver superieursResolver;

    @Scheduled(cron = "0 0 7-18 * * MON-FRI", zone = "Africa/Ouagadougou")
    @Transactional
    public void alerterDelaisDepasses() {
        alerterDelaisDepasses(Instant.now());
    }

    /** Point d'entrée testable : l'instant courant est fourni. Retourne le nombre d'alertes créées. */
    @Transactional
    public int alerterDelaisDepasses(Instant maintenant) {
        List<Agent> superieurs = superieursResolver.resoudre();
        if (superieurs.isEmpty()) {
            log.warn("[AlerteDelai] Aucun agent CGEA/CGE résolu — alertes ignorées pour ce passage");
            return 0;
        }

        int creees = 0;
        for (Dossier dossier : dossierRepository.findByStatusNotIn(STATUTS_TERMINES)) {
            if (dossier.getReceptionDate() == null) continue;
            try {
                creees += alerterDossier(dossier, superieurs, maintenant);
            } catch (RuntimeException e) {
                // Un dossier en échec ne doit pas empêcher l'alerte des autres.
                log.error("[AlerteDelai] Échec pour le dossier {} : {}", dossier.getNumber(), e.getMessage(), e);
            }
        }
        if (creees > 0) {
            log.info("[AlerteDelai] {} alerte(s) de délai dépassé créée(s)", creees);
        }
        return creees;
    }

    private int alerterDossier(Dossier dossier, List<Agent> superieurs, Instant maintenant) {
        Instant limite = maintenant.minus(Duration.ofDays(FENETRE_ALERTE_JOURS));
        int creees = 0;

        for (DelaiEtapeResponse etape : delaiEtapeService.evaluer(dossier.getId(), dossier.getReceptionDate(), maintenant)) {
            if (etape.getStatut() != StatutDelaiEtape.DEPASSE) continue;
            if (!etape.getEcheance().isAfter(limite)) continue;
            if (notificationRepository.existsByDossierIdAndTypeAndEtapeCode(
                    dossier.getId(), NotificationType.ALERTE_DELAI_ETAPE, etape.getCode())) continue;

            creerAlertes(dossier, etape, superieurs);
            creees++;
        }
        return creees;
    }

    private void creerAlertes(Dossier dossier, DelaiEtapeResponse etape, List<Agent> superieurs) {
        Map<String, String> placeholders = Map.of(
                "numero", dossier.getNumber() != null ? dossier.getNumber() : "en attente de numéro",
                "etape", etape.getLibelle(),
                "acteur", etape.getActeur(),
                "echeance", FMT_ECHEANCE.format(etape.getEcheance()),
                "retard", formaterDuree(-(etape.getHeuresRestantes() != null ? etape.getHeuresRestantes() : 0L)));

        String sujet = portalConfigService.resolveNotificationText("notif_subject_alerte_delai_etape", placeholders);
        String contenu = portalConfigService.resolveNotificationText("notif_content_alerte_delai_etape", placeholders);

        for (Agent superieur : superieurs) {
            notificationRepository.save(Notification.builder()
                    .dossier(dossier)
                    .type(NotificationType.ALERTE_DELAI_ETAPE)
                    .channel(NotificationChannel.PORTAL)
                    .recipient(superieur.getKeycloakId())
                    .subject(sujet)
                    .content(contenu)
                    .etapeCode(etape.getCode())
                    .scheduledAt(Instant.now())
                    .build());
        }
        log.warn("[AlerteDelai] Étape {} en retard — dossier {}, {} destinataire(s)",
                etape.getCode(), dossier.getNumber(), superieurs.size());
    }

    /** Durée en heures écrite en jours et heures, par exemple « 1 j 4 h ». */
    static String formaterDuree(long heures) {
        long h = Math.abs(heures);
        if (h < 1) return "moins d'1 h";
        long jours = h / 24;
        long reste = h % 24;
        if (jours == 0) return reste + " h";
        return reste > 0 ? jours + " j " + reste + " h" : jours + " j";
    }
}
