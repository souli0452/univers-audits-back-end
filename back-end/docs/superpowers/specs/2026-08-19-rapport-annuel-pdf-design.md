# Exports pour le rapport annuel d'activité — Design

## Statut

**Lot 7 — Processus D : reporting et pilotage**, sous-chantier 3/3 (dernier) :

1. Compléter `StatistiqueService` (priorisations CTADP, parties prenantes, délais) — livré et mergé
2. Ratios de couverture — livré et mergé
3. **Exports pour le rapport annuel d'activité** ← ce document (en cours)

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, ligne 376) termine le Lot 7
par « Exports pour le rapport annuel d'activité ». Exploration complète de l'existant avant
conception (même prudence qu'aux deux sous-chantiers précédents, où des surprises avaient forcé un
changement de périmètre) :

- **Aucune capacité d'export de statistiques n'existe, sous quelque forme que ce soit** —
  `StatistiqueService`/`StatistiqueController` sont purement JSON. Aucun export Excel/CSV n'existe
  nulle part dans le dépôt (confirmé par recherche dédiée). Ce sous-chantier est donc du
  développement neuf, pas une extension d'agrégation comme les deux précédents.
- **`PdfExportService`/`PdfController` (920 + 81 lignes) sont le seul mécanisme d'export de
  fichiers du dépôt**, avec un patron de code entièrement établi : iText 7, mise en page
  programmatique codée en dur (pas de moteur de templating — `ModeleDocument`, l'entité de
  templating envisagée par le plan de travail au §5, n'existe pas en code), constantes de
  couleur/police, helpers privés réutilisables (`sectionTitle`, `addInfoCell`, `addTableRow`,
  en-tête/pied de page institutionnels via `AsceLcInstitutionalInfo`), méthodes de libellé par enum
  (`getStatusLabel`, `getTypeLabel`, `getModeLabel`). Mais **les 2 exports existants
  (`exportDossier`, `exportRecepisse`) sont tous deux mono-dossier** — aucun helper de rendu agrégé/
  multi-enregistrement n'existe. C'est donc du code de mise en page réellement nouveau, pas du
  copier-coller.
- **`StatistiqueController.getAnnualStats(int year)` existe déjà** (`/api/v1/stats/annual`,
  `@PreAuthorize("hasAnyRole('CGEA','CGE','ADMIN_DDIC')")`) et retourne un `StatistiqueResponse`
  JSON complet pour l'année. C'est la source de données naturelle : aucune nouvelle requête, aucun
  nouveau calcul — uniquement la mise en forme PDF de données déjà agrégées et déjà exposées.
- **Codebase-wide : zéro test de contrôleur** (confirmé — aucun `*ControllerTest.java` dans tout le
  dépôt). Le patron de test à suivre est donc uniquement au niveau service, comme
  `PdfExportServiceTest` (Mockito + `@InjectMocks`, assertions de contenu via `PdfTextExtractor`).

## Objectif

Ajouter un export PDF du rapport annuel d'activité, réutilisant intégralement les données déjà
calculées par `StatistiqueService.getAnnualStats(int year)` — aucune nouvelle donnée, uniquement
une nouvelle représentation.

## Hors périmètre

- **Export Excel/CSV** : non demandé par le texte source (« exports... » au pluriel dans le plan de
  travail réfère aux formats de fenêtre temporelle du dashboard — mensuel/trimestriel/semestriel/
  annuel —, pas à des formats de fichier ; seul le PDF a un patron établi dans ce dépôt). Si
  besoin futur, un chantier dédié.
- **Export mensuel/trimestriel/semestriel en PDF** : le texte source cible spécifiquement « le
  rapport annuel d'activité ». `getQuarterlyStats`/`getDashboard` restent JSON-only. Peut être
  généralisé plus tard sans changement d'architecture (même service, paramètre différent).
- **Graphiques/visualisations** : iText est utilisé ici en mise en page tabulaire pure, comme
  partout ailleurs dans `PdfExportService` — aucune bibliothèque de graphiques n'existe dans le
  dépôt. Le rapport présente les données en tableaux, pas en courbes.
- **Modification de `StatistiqueService`/`StatistiqueResponse`/tout calcul existant** — ce
  sous-chantier est purement une nouvelle vue en lecture sur des données déjà produites.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Emplacement du code de mise en page | Nouvelle classe `RapportAnnuelPdfService`, pas une extension de `PdfExportService` | `PdfExportService` (920 lignes) est déjà volumineux et 100% mono-dossier ; un rapport agrégé est une responsabilité distincte. Duplication mineure de 3 helpers de libellé (voir ci-dessous) jugée préférable à coupler les deux classes. |
| Source de données | `StatistiqueService.getAnnualStats(int year)` (déjà existant, déjà utilisé par le JSON) | Aucune nouvelle requête ni nouveau calcul ; le PDF ne doit jamais diverger de ce que l'API JSON expose déjà pour la même année. |
| Emplacement du contrôleur | Nouvel endpoint dans `StatistiqueController` (`GET /api/v1/stats/annual/export`), pas dans `PdfController` | `PdfController` est 100% dossier-centrique (`{id}` sur chaque route, `isAuthenticated()`). Ce rapport n'a pas de dossier associé et expose des statistiques globales — sa place naturelle est à côté de `getAnnualStats` dont il réutilise directement la logique. |
| Restriction d'accès | `@PreAuthorize("hasAnyRole('CGEA','CGE','ADMIN_DDIC')")` — identique à `/annual` existant | Les statistiques globales de l'institution ne sont pas filtrées par dossier (contrairement aux exports `PdfController`, protégés par le contrôle d'affectation de `dossierService.findById`). Réutiliser `isAuthenticated()` ici exposerait les stats globales à tout utilisateur authentifié, y compris des rôles n'ayant pas accès à `/annual` en JSON — incohérence de sécurité à éviter. |
| Mention « CONFIDENTIEL » | **Absente** de ce document | Contrairement aux exports `PdfExportService` (mono-dossier, contiennent des données de déclarant/description — PII), le rapport annuel est une agrégation statistique pure, sans donnée personnelle. La restriction de rôle protège déjà l'accès ; la mention CONFIDENTIEL resterait cohérente avec les autres documents institutionnels mais n'est pas un signal de confidentialité au sens du §9 du plan de travail (masquage de données personnelles), qui ne s'applique pas ici. |
| Champs `Double` de délai à `null` (ex. `avgAcknowledgmentDays`, ou tout délai sans échantillon sur l'année) | Affichés « Non disponible » | Comportement uniforme pour tout champ `Double` nul, pas un cas spécial pour `avgAcknowledgmentDays` — un délai peut être `null` pour n'importe quel champ si le dénominateur (nombre d'événements) est nul sur la période. |
| Libellés d'énumération (statut, type, mode, recommandation CTADP, décision CGE, type de partie, résultat d'investigation) | Petites méthodes privées dédiées dans `RapportAnnuelPdfService`, sur le modèle de `getStatusLabel`/`getTypeLabel`/`getModeLabel` de `PdfExportService` | Aucune classe utilitaire de libellés partagée n'existe dans le dépôt ; dupliquer 3 méthodes existantes + en écrire quelques nouvelles reste plus simple qu'introduire un couplage entre les deux services PDF pour ce seul besoin. |
| En-tête/pied de page institutionnel | Réutilise `AsceLcInstitutionalInfo` (classe utilitaire publique, déjà partagée) | Pas de duplication nécessaire — c'est déjà une constante partagée, pas une méthode privée de `PdfExportService`. |

## Composants

### 1. Nouveau service — `RapportAnnuelPdfService`

Fichier (nouveau) : `src/main/java/gov/bf/ascelc/univers_audits/service/RapportAnnuelPdfService.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RapportAnnuelPdfService {

    private final StatistiqueService statistiqueService;

    // Mêmes constantes de couleur que PdfExportService (VERT_ASCE, OR_ASCE, GRIS_CLAIR,
    // TEXTE_GRIS, BLANC) — dupliquées ici pour l'identité visuelle, pas d'import croisé.

    public byte[] exportRapportAnnuel(int year) {

        StatistiqueResponse stats = statistiqueService.getAnnualStats(year);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addHeader(doc, year, stats, fontBold, fontNormal);
            addVolumesSection(doc, stats, fontBold, fontNormal);
            addTraitementSection(doc, stats, fontBold, fontNormal);
            addPriorisationsSection(doc, stats, fontBold, fontNormal);
            addPartiesViseesSection(doc, stats, fontBold, fontNormal);
            addInvestigationsSection(doc, stats, fontBold, fontNormal);
            addDelaisSection(doc, stats, fontBold, fontNormal);
            addRatiosCouvertureSection(doc, stats, fontBold, fontNormal);
            addDepassementsSection(doc, stats, fontBold, fontNormal);
            addFooter(doc, fontBold, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur export rapport annuel {}: {}", year, e.getMessage());
            throw new RuntimeException("Erreur génération rapport annuel: " + e.getMessage());
        }
    }

    // + 9 méthodes privées addXxxSection(doc, stats, fontBold, fontNormal), une par section
    //   ci-dessous, suivant le patron sectionTitle()/Table/addTableRow() de PdfExportService.
    // + helpers privés : sectionTitle(String, PdfFont), formatDelay(Double),
    //   formatPercent(double), formatMap(Map<String, Long>, Function<String,String> labelFn)
    //   pour restituer countByXxx en lignes de tableau triées par clé.
    // + méthodes de libellé : getStatusLabel, getTypeLabel, getModeLabel (copiées de
    //   PdfExportService), getCtadpRecommandationLabel, getCgeDecisionLabel,
    //   getPartyTypeLabel, getInvestigationOutcomeLabel (nouvelles, sur les enums
    //   RecommandationCtadp, PartyType, InvestigationOutcome).
}
```

### 2. Sections du document (dans l'ordre)

Toutes les données proviennent de `StatistiqueResponse` (aucun champ non déjà exposé par le JSON
`/annual` actuel) :

1. **En-tête institutionnel** : bandeau ASCE-LC (identique visuellement à `PdfExportService`),
   titre « RAPPORT ANNUEL D'ACTIVITÉ {year} », `stats.getPeriod()`, date de génération
   (`stats.getGeneratedAt()`).
2. **Volumes et tendances** : `totalDossiers`, tableau `countByStatus`, tableau
   `countBySubmissionMode`, tableau `countByType`, `totalEstimatedLoss` (formaté en FCFA).
3. **État de traitement** : `inadmissibleCount`, `transferredCount`,
   `closedWithoutInvestigationCount`, `admissibilityRate` (%).
4. **Priorisations et décisions** : tableau `countByCtadpRecommandation`, tableau
   `countByCgeDecision`.
5. **Parties visées** : tableau `countByTargetedPartyType`.
6. **Résultats des investigations** : `investigatedCount`, `inProgressInvestigations`,
   `reportsProduced`, `referredToJustice`, `unfoundedWithoutInvestigation`,
   `unfoundedAfterInvestigation`, tableau `countByInvestigationOutcome`.
7. **Délais moyens** (en jours civils — limite connue, cf. jours ouvrables réels en dette
   transversale, hors périmètre) : `avgRegistrationDelayDays`, `avgOpportunityStudyDays`,
   `avgAcknowledgmentDays`, `avgInvestigationDurationDays`, `avgLegalAdvisorApprovalDays`,
   `avgDeiApprovalDays`, `avgCgeaApprovalDays`, `avgCgeApprovalDays`,
   `avgPlanActionsSubmissionDays` — chacun via `formatDelay()` (« Non disponible » si `null`).
8. **Ratios de couverture** : `admissibilityRate`, `investigationCoverageRate`,
   `reportProductionRate`, `acknowledgmentCoverageRate` (les 4 en %, regroupés dans une seule
   section malgré `admissibilityRate` déjà mentionné en §3 — cohérence thématique avec le plan de
   travail qui les nomme ensemble comme "ratios de couverture").
9. **Dépassements de délai** : `overdueAcknowledgments`, `overdueInvestigations`,
   `overdueComplements`.
10. **Pied de page institutionnel** : `AsceLcInstitutionalInfo` (adresse, téléphone, email, numéro
    vert), identique au patron `addFooter` de `PdfExportService`.

### 3. Nouvel endpoint — `StatistiqueController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/StatistiqueController.java`

Ajouter après `getAnnualStats` :

```java
@GetMapping("/annual/export")
@PreAuthorize("hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')")
public ResponseEntity<byte[]> exportAnnualStats(@RequestParam int year) {

    if (year < 2020 || year > 2100) {
        throw new BusinessException("Année invalide : " + year);
    }
    log.info("Export PDF rapport annuel {}", year);
    byte[] pdf = rapportAnnuelPdfService.exportRapportAnnuel(year);

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"rapport-annuel-activite-" + year + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
}
```

Nécessite l'ajout de `private final RapportAnnuelPdfService rapportAnnuelPdfService;` au
constructeur (`@RequiredArgsConstructor`), et des imports `HttpHeaders`, `MediaType` déjà présents
dans `PdfController` (patron identique).

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| Année hors plage (`< 2020` ou `> 2100`) | `BusinessException` — identique au garde-fou déjà existant sur `/annual` |
| Aucun dossier reçu sur l'année | Rapport généré normalement, toutes les sections affichent 0 / tableaux vides / ratios à 0.0% — pas d'erreur (même sémantique que `getDashboard` avec `total == 0`) |
| Champ `Double` de délai `null` | Affiché « Non disponible », jamais une exception de formatage |
| Erreur de génération iText (cas exceptionnel) | `RuntimeException` avec message, identique au patron `catch (Exception e)` de `PdfExportService.exportDossier` |

## Tests

- **`RapportAnnuelPdfServiceTest`** (nouveau fichier, patron `PdfExportServiceTest`) :
  Mockito + `@InjectMocks`, mock de `StatistiqueService.getAnnualStats(int)`.
  - `exportRapportAnnuel_genereUnPdfNonVide`
  - `exportRapportAnnuel_contientLeTitreEtAnnee` (assertion `PdfTextExtractor` sur « RAPPORT ANNUEL
    D'ACTIVITÉ 2026 »)
  - `exportRapportAnnuel_contientLesVolumesEtStatuts`
  - `exportRapportAnnuel_contientLesRatiosDeCouverture`
  - `exportRapportAnnuel_affenceNonDisponiblePourDelaiNull` (mock avec un champ `Double` à `null`,
    assertion que le texte extrait contient « Non disponible » et ne lève pas d'exception)
  - `exportRapportAnnuel_genereUnPdfValideMemeSiAucunDossier` (mock avec `totalDossiers = 0`,
    maps vides, tous ratios à `0.0`)
- Pas de test de contrôleur, conformément à la convention du dépôt (0 `*ControllerTest.java`
  partout ailleurs).

## Risques et points d'attention pour le plan d'implémentation

- **Ne pas faire dépendre `RapportAnnuelPdfService` de `PdfExportService`** — ce sont deux services
  indépendants qui partagent un style visuel par duplication volontaire des constantes de couleur
  et de quelques méthodes de libellé, pas par héritage ni composition. Coupler les deux créerait une
  dépendance artificielle entre mono-dossier et agrégat.
- **`formatMap(Map<String, Long>, ...)` doit trier les clés** pour un rendu déterministe (les `Map`
  retournés par `StatistiqueServiceImpl` ne garantissent pas d'ordre) — utiliser `TreeMap` ou trier
  avant itération, sinon les tests de contenu et la lisibilité humaine du PDF varient d'un export à
  l'autre.
- **`getCtadpRecommandationLabel`/`getCgeDecisionLabel`/`getPartyTypeLabel`/
  `getInvestigationOutcomeLabel` sont de nouvelles méthodes** (contrairement à `getStatusLabel`/
  `getTypeLabel`/`getModeLabel` qui existent déjà et peuvent être recopiées telles quelles) — leurs
  valeurs d'enum exactes doivent être lues dans `RecommandationCtadp`, `PartyType`,
  `InvestigationOutcome` avant écriture, ne pas deviner les noms de constantes.
- **Ne pas ajouter de mention CONFIDENTIEL** — décision de conception explicite (voir tableau),
  ne pas reproduire ce détail visuel de `PdfExportService` par réflexe de copier-coller.
- **Le contrôleur ne doit pas utiliser `isAuthenticated()`** — décision de conception explicite,
  la restriction de rôle doit être `hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')`, identique à
  `/annual` JSON existant.
