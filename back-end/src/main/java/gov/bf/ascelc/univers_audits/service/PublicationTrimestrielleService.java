package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import gov.bf.ascelc.univers_audits.enums.SocialPlatform;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.enums.TypologieInstitutionnelle;
import gov.bf.ascelc.univers_audits.model.dto.response.PublicationTrimestrielleResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PublicationTrimestrielleResponse.Ligne;
import gov.bf.ascelc.univers_audits.model.dto.response.PublicationTrimestrielleResponse.Phase;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calcule les huit tableaux de la « Situation des plaintes et dénonciations » publiée chaque trimestre sur le site
 * (catégories, modes de saisine, typologie institutionnelle, allégations, traitement, recevabilité, origine
 * géographique, vélocité). Dossiers comptés : ceux enregistrés (date de réception) pendant le trimestre.
 * Rien de personnel n'en sort : uniquement des nombres et des taux.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicationTrimestrielleService {

    private static final ZoneId OUAGA_TZ = ZoneId.of("Africa/Ouagadougou");

    /** Dossiers dont l'enquête est en cours ou son rapport en circuit d'approbation. */
    private static final Set<DossierStatus> EN_COURS = Set.of(
            DossierStatus.EN_INVESTIGATION, DossierStatus.RAPPORT_PRODUIT, DossierStatus.DECISION_RENDUE);
    /** États terminaux : dossier traité de bout en bout ou classé. */
    private static final Set<DossierStatus> BOUCLES = Set.of(DossierStatus.CLOS, DossierStatus.CLASSE);
    /** Dossiers écartés sans enquête : irrecevables, transférés ou classés. */
    private static final Set<DossierStatus> CLASSES_SANS_SUITE = Set.of(
            DossierStatus.IRRECEVABLE, DossierStatus.TRANSFERE, DossierStatus.CLASSE);
    /** Pas encore qualifiés : déposés ou reçus mais pas encore étudiés. */
    private static final Set<DossierStatus> PAS_ENCORE_QUALIFIES = Set.of(DossierStatus.SOUMIS, DossierStatus.RECU);

    private final DossierRepository dossiers;
    private final InvestigationRepository investigations;

    public PublicationTrimestrielleResponse situation(int annee, int trimestre) {
        if (trimestre < 1 || trimestre > 4) {
            throw new BusinessException("Le trimestre doit être compris entre 1 et 4.");
        }
        int moisDebut = (trimestre - 1) * 3 + 1;
        Instant debut = YearMonth.of(annee, moisDebut).atDay(1).atStartOfDay(OUAGA_TZ).toInstant();
        Instant fin = (moisDebut + 3 > 12 ? YearMonth.of(annee + 1, 1) : YearMonth.of(annee, moisDebut + 3))
                .atDay(1).atStartOfDay(OUAGA_TZ).toInstant();

        long total = dossiers.countByReceptionDateBetween(debut, fin);
        Map<DossierStatus, Long> parStatut = parStatut(dossiers.countByStatusAndReceptionDateBetween(debut, fin));

        long enCours = somme(parStatut, EN_COURS);
        long boucles = somme(parStatut, BOUCLES);
        long classes = somme(parStatut, CLASSES_SANS_SUITE);
        long pasQualifies = somme(parStatut, PAS_ENCORE_QUALIFIES);
        long stock = Math.max(0, total - enCours - boucles);

        long incompetence = dossiers.countClassesPourIncompetenceBetween(debut, fin, CLASSES_SANS_SUITE);
        long preuves = dossiers.countClassesPourPreuvesInsuffisantesBetween(debut, fin, CLASSES_SANS_SUITE);
        long motifNonRenseigne = Math.max(0, classes - incompetence - preuves);

        BigDecimal enjeux = dossiers.sumEstimatedLossBetween(debut, fin);

        List<Ligne> traitement = List.of(
                ligne("ENREGISTRES", "Dossiers enregistrés", total, total),
                ligne("EN_COURS", "En cours de traitement", enCours, total),
                ligne("BOUCLES", "Traités (bouclés)", boucles, total),
                ligne("STOCK", "Stock en instance", stock, total));

        List<Ligne> recevabilite = List.of(
                ligne("RECEVABLES", "Dossiers jugés recevables (orientés vers un contrôle ou une investigation)",
                        Math.max(0, total - classes), total),
                ligne("CLASSES_INCOMPETENCE", "Dossiers classés sans suite pour incompétence matérielle",
                        incompetence, total),
                ligne("CLASSES_PREUVES_INSUFFISANTES", "Dossiers classés sans suite pour preuves ou éléments insuffisants",
                        preuves, total),
                ligne("CLASSES_MOTIF_NON_RENSEIGNE", "Dossiers classés sans suite (motif non renseigné)",
                        motifNonRenseigne, total));

        List<Phase> velocite = List.of(
                new Phase("ENREGISTRES", "Dossiers enregistrés", total, null),
                new Phase("QUALIFIES", "Dossiers qualifiés et affectés (instruction initiale)",
                        Math.max(0, total - pasQualifies), null),
                new Phase("INVESTIGATION", "Dossiers en phase d'investigation / enquêtes de terrain", enCours,
                        arrondi(investigations.avgDurationInDays(debut, fin))),
                new Phase("CLOTURES", "Dossiers entièrement clôturés", boucles, null));

        return new PublicationTrimestrielleResponse(
                annee, trimestre, "T" + trimestre + " " + annee, Instant.now().toString(), total,
                categories(total, debut, fin),
                modes(total, debut, fin),
                typologies(total, debut, fin),
                allegations(total, debut, fin),
                traitement,
                enjeux != null ? enjeux : BigDecimal.ZERO,
                recevabilite,
                regions(total, debut, fin),
                velocite,
                notes());
    }

    // ───────────────────────────── tableaux ─────────────────────────────

    private List<Ligne> categories(long total, Instant debut, Instant fin) {
        Map<TypeSaisine, Long> parType = new EnumMap<>(TypeSaisine.class);
        for (Object[] r : dossiers.countByTypeBetween(debut, fin)) {
            parType.merge((TypeSaisine) r[0], (Long) r[1], Long::sum);
        }
        List<Ligne> lignes = new ArrayList<>();
        lignes.add(ligne("DENONCIATION", "Dénonciations", parType.getOrDefault(TypeSaisine.DENONCIATION, 0L), total));
        lignes.add(ligne("PLAINTE", "Plaintes", parType.getOrDefault(TypeSaisine.PLAINTE, 0L), total));
        lignes.add(ligne("PLAINTE_DENONCIATION", "Plaintes-dénonciations",
                parType.getOrDefault(TypeSaisine.PLAINTE_DENONCIATION, 0L), total));
        lignes.add(ligne("DEMANDE_INTERVENTION", "Demandes d'intervention",
                parType.getOrDefault(TypeSaisine.DEMANDE_INTERVENTION, 0L), total));
        // Saisines d'autorités et auto-saisines : hors du document, mais le total doit tomber juste.
        ajouterSiNonVide(lignes, "SIGNALEMENT", "Signalements d'autorités", parType.get(TypeSaisine.SIGNALEMENT), total);
        ajouterSiNonVide(lignes, "AUTO_SAISINE", "Auto-saisines", parType.get(TypeSaisine.AUTO_SAISINE), total);
        return lignes;
    }

    private List<Ligne> modes(long total, Instant debut, Instant fin) {
        Map<String, Long> parGroupe = new LinkedHashMap<>();
        for (String code : List.of("EN_PERSONNE", "ECRIT", "MAIL", "TELEPHONE", "MESSENGER", "WHATSAPP", "PORTAIL", "AUTRES")) {
            parGroupe.put(code, 0L);
        }
        for (Object[] r : dossiers.countByModeEtPlateformeBetween(debut, fin)) {
            parGroupe.merge(groupeDeMode((SubmissionMode) r[0], (SocialPlatform) r[1]), (Long) r[2], Long::sum);
        }
        List<Ligne> lignes = new ArrayList<>();
        lignes.add(ligne("EN_PERSONNE", "En personne", parGroupe.get("EN_PERSONNE"), total));
        lignes.add(ligne("ECRIT", "Par écrit", parGroupe.get("ECRIT"), total));
        lignes.add(ligne("MAIL", "Par mail", parGroupe.get("MAIL"), total));
        lignes.add(ligne("TELEPHONE", "Par téléphone", parGroupe.get("TELEPHONE"), total));
        lignes.add(ligne("MESSENGER", "Par Messenger", parGroupe.get("MESSENGER"), total));
        lignes.add(ligne("WHATSAPP", "WhatsApp", parGroupe.get("WHATSAPP"), total));
        ajouterSiNonVide(lignes, "PORTAIL", "Via le portail en ligne", parGroupe.get("PORTAIL"), total);
        ajouterSiNonVide(lignes, "AUTRES", "Autres modes", parGroupe.get("AUTRES"), total);
        return lignes;
    }

    /** Regroupe les 13 modes de saisine de l'application dans les rubriques publiées. */
    static String groupeDeMode(SubmissionMode mode, SocialPlatform plateforme) {
        if (mode == null) {
            return "AUTRES";
        }
        return switch (mode) {
            case IN_PERSON, AUDIO_COUNTER -> "EN_PERSONNE";
            case PAPER_FORM, POSTAL_MAIL, FAX -> "ECRIT";
            case EMAIL -> "MAIL";
            case PHONE, GREEN_NUMBER, SMS -> "TELEPHONE";
            case WEB_FORM -> "PORTAIL";
            case SOCIAL_MEDIA -> plateforme == SocialPlatform.MESSENGER ? "MESSENGER"
                    : plateforme == SocialPlatform.WHATSAPP ? "WHATSAPP" : "AUTRES";
            default -> "AUTRES";
        };
    }

    private List<Ligne> typologies(long total, Instant debut, Instant fin) {
        Map<TypologieInstitutionnelle, Long> parType = new EnumMap<>(TypologieInstitutionnelle.class);
        long nonPrecise = 0;
        for (Object[] r : dossiers.countByTypologieBetween(debut, fin)) {
            if (r[0] == null) {
                nonPrecise += (Long) r[1];
            } else {
                parType.merge((TypologieInstitutionnelle) r[0], (Long) r[1], Long::sum);
            }
        }
        List<Ligne> lignes = new ArrayList<>();
        for (TypologieInstitutionnelle t : TypologieInstitutionnelle.values()) {
            lignes.add(ligne(t.name(), t.libelle(), parType.getOrDefault(t, 0L), total));
        }
        ajouterSiNonVide(lignes, "NON_PRECISE", "Non précisée", nonPrecise, total);
        return lignes;
    }

    private List<Ligne> regions(long total, Instant debut, Instant fin) {
        Map<RegionOrigine, Long> parRegion = new EnumMap<>(RegionOrigine.class);
        long nonPrecise = 0;
        for (Object[] r : dossiers.countByRegionOrigineBetween(debut, fin)) {
            if (r[0] == null) {
                nonPrecise += (Long) r[1];
            } else {
                parRegion.merge((RegionOrigine) r[0], (Long) r[1], Long::sum);
            }
        }
        List<Ligne> lignes = new ArrayList<>();
        for (RegionOrigine region : RegionOrigine.values()) {
            lignes.add(ligne(region.name(), region.libelle(), parRegion.getOrDefault(region, 0L), total));
        }
        ajouterSiNonVide(lignes, "NON_PRECISE", "Non précisée", nonPrecise, total);
        return lignes;
    }

    private List<Ligne> allegations(long total, Instant debut, Instant fin) {
        List<Ligne> nommees = new ArrayList<>();
        long nonQualifie = 0;
        for (Object[] r : dossiers.countByAllegationBetween(debut, fin)) {
            if (r[0] == null) {
                nonQualifie += (Long) r[1];
            } else {
                nommees.add(ligne((String) r[0], (String) r[0], (Long) r[1], total));
            }
        }
        nommees.sort(Comparator.comparingLong(Ligne::nombre).reversed().thenComparing(Ligne::libelle));
        ajouterSiNonVide(nommees, "NON_QUALIFIE", "Non qualifié", nonQualifie, total);
        return nommees;
    }

    private static List<String> notes() {
        return List.of(
                "Dossiers comptés : ceux enregistrés pendant le trimestre (date de réception).",
                "En cours de traitement : dossiers en investigation, ou dont le rapport est en circuit d'approbation.",
                "Traités (bouclés) : dossiers clos ou classés. Stock en instance : tous les autres dossiers enregistrés.",
                "Classés sans suite : dossiers irrecevables, transférés ou classés ; le motif vient de l'étude d'opportunité.",
                "La transmission à la Cour des comptes n'est pas encore suivie par l'application.",
                "Allégation : infraction retenue à l'étude d'opportunité ; « Non qualifié » tant que cette étude n'est pas faite.");
    }

    // ───────────────────────────── outils ─────────────────────────────

    private static Map<DossierStatus, Long> parStatut(List<Object[]> lignes) {
        Map<DossierStatus, Long> parStatut = new EnumMap<>(DossierStatus.class);
        for (Object[] r : lignes) {
            parStatut.merge((DossierStatus) r[0], (Long) r[1], Long::sum);
        }
        return parStatut;
    }

    private static long somme(Map<DossierStatus, Long> parStatut, Set<DossierStatus> statuts) {
        return statuts.stream().mapToLong(s -> parStatut.getOrDefault(s, 0L)).sum();
    }

    private static void ajouterSiNonVide(List<Ligne> lignes, String code, String libelle, Long nombre, long total) {
        if (nombre != null && nombre > 0) {
            lignes.add(ligne(code, libelle, nombre, total));
        }
    }

    private static Ligne ligne(String code, String libelle, long nombre, long total) {
        double taux = total > 0
                ? BigDecimal.valueOf(nombre * 100.0 / total).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        return new Ligne(code, libelle, nombre, taux);
    }

    private static Double arrondi(Double valeur) {
        return valeur == null ? null : BigDecimal.valueOf(valeur).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
