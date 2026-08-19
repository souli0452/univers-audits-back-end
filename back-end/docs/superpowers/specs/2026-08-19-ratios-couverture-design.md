# Ratios de couverture — Design

## Statut

**Lot 7 — Processus D : reporting et pilotage**, redécoupé en 3 sous-chantiers après
découverte d'une base de statistiques déjà existante :

1. Compléter `StatistiqueService` (priorisations CTADP, parties prenantes, délais) — livré et mergé
2. **Ratios de couverture** ← ce document (en cours)
3. Exports pour le rapport annuel d'activité — à venir

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, ligne 376) nomme 4 ratios
de couverture : « dénonciations reçues / accusés transmis, reçues / investiguées, investigations /
rapports produits, rapports / information du dénonciateur. » Avant de concevoir, exploration
complète de l'existant (même prudence qu'au sous-chantier 1/3, où une base de statistiques
non documentée avait déjà été découverte) :

- **Ratios « reçues/investiguées » et « investigations/rapports produits » sont déjà entièrement
  calculables** — `StatistiqueResponse.{totalDossiers, investigatedCount, reportsProduced}` existent
  déjà et sont peuplés dans `StatistiqueServiceImpl.getDashboard`. Ces deux ratios sont de pures
  divisions sur des champs déjà calculés, aucune nouvelle requête.
- **Ratio « reçues/accusés transmis »** : deux signaux existent dans le code, aucun pleinement
  satisfaisant :
  - *Signal A* : `Notification` de type `ACKNOWLEDGMENT_B5`, créée automatiquement pour **chaque**
    dossier à l'enregistrement (`DossierServiceImpl.java:302-318`). Le ratio sera proche de 100% la
    plupart du temps (créée systématiquement), mais reste honnête : il mesure combien de ces
    notifications ont été **effectivement envoyées** (`status = SENT`, positionné par
    `NotificationServiceImpl.processPendingNotifications()`) plutôt que simplement créées/planifiées
    — un déficit reste visible et informatif (envois bloqués, en échec, ou pas encore traités par le
    scheduler). **`NotificationRepository` est déjà injecté dans `StatistiqueServiceImpl` mais n'y
    est jamais utilisé** (dépendance morte — situation inverse de la découverte du sous-chantier
    1/3, où une capacité existait mais n'était pas branchée sur le service).
  - *Signal B* : `PdfExportService.exportAccuseReception` — le document formel « accusé de
    réception / suites à donner » du texte source (§Lot 2, ligne 361), généré à la demande après
    décision CGE, mais **rien n'est jamais persisté** (aucune trace de génération en base).
  - **Décision utilisateur : retenir le Signal A** — donnée réellement interrogeable dès maintenant,
    contrairement au Signal B qui nécessiterait une nouvelle fonctionnalité de traçabilité de
    document, hors périmètre d'un chantier d'agrégation.
- **Ratio « rapports/information du dénonciateur » — bloqué, hors périmètre** : `NotificationType
  .FINAL_DECISION` existe dans l'enum mais n'est **jamais créé** nulle part dans le code. La
  notification de clôture de dossier (`DossierServiceImpl.close()` → `NotificationDispatcherService
  .dispatchStatusUpdate()`) envoie l'e-mail/SMS **directement** via `EmailService`/`SmsService`, en
  contournant entièrement l'entité `Notification` — aucune trace, aucun horodatage n'est jamais
  écrit en base pour cet événement. Même situation que `avgAcknowledgmentDays`, déjà écarté au
  sous-chantier 1/3 pour la même raison (aucune donnée à agréger). Le Lot 8 (portail externe,
  ligne 379) assigne d'ailleurs explicitement « l'information du dénonciateur... sur la conclusion
  finale » au futur portail en libre-service, pas à ce Lot.

## Objectif

Ajouter 3 champs de type ratio (pourcentage) à `StatistiqueResponse`, calculés dans
`StatistiqueServiceImpl.getDashboard` :
- `investigationCoverageRate` : part des dossiers reçus ayant déclenché une investigation
- `reportProductionRate` : part des investigations ayant produit un rapport
- `acknowledgmentCoverageRate` : part des dossiers reçus dont l'accusé de réception (B5) a
  effectivement été envoyé

## Hors périmètre

- **Ratio « rapports/information du dénonciateur »** : aucune donnée exploitable (voir Contexte).
  Nécessiterait de brancher `NotificationType.FINAL_DECISION` sur le flux de clôture — un chantier
  d'instrumentation, pas d'agrégation.
- **Signal B (traçabilité de génération de l'accusé de réception PDF)** : nécessiterait une nouvelle
  fonctionnalité de journalisation de documents, hors périmètre.
- **Nouveau champ `StatistiqueResponse.notificationCoverage...` par type de notification autre que
  `ACKNOWLEDGMENT_B5`** : uniquement ce type est pertinent pour ce ratio précis, YAGNI sur le reste.
- Toute modification des champs/requêtes existants (`totalDossiers`, `investigatedCount`,
  `reportsProduced`, et tout ce qui a été ajouté au sous-chantier 1/3) — ce chantier est purement
  additif, comme le précédent.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Ratios « reçues/investiguées » et « investigations/rapports produits » | Calcul en pure division sur les champs déjà existants, aucune nouvelle requête | Les données sont déjà présentes dans `getDashboard` |
| Ratio « reçues/accusés transmis » | Signal A (`Notification` type `ACKNOWLEDGMENT_B5`, `status = SENT`) | Décision utilisateur — seule donnée réellement persistée et interrogeable |
| Nouvelle requête | `NotificationRepository.countByTypeAndStatusAndDossierReceptionDateBetween(NotificationType, NotificationStatus, Instant, Instant): long` | Suit le patron `countByReceptionDateBetween`/`countByDossierReceptionDateBetween` déjà établi ailleurs, filtre sur `n.dossier.receptionDate` |
| Garde division par zéro | `0.0` si le dénominateur est nul | Même patron déjà établi pour `admissibilityRate` (`examined > 0 ? ... : 0.0`) |
| Nommage des champs | `investigationCoverageRate`, `reportProductionRate`, `acknowledgmentCoverageRate` (tous `double`, en pourcentage 0-100) | Cohérent avec `admissibilityRate` déjà existant (même type, même échelle) |
| Emplacement du code | Étend `NotificationRepository`/`StatistiqueResponse`/`StatistiqueServiceImpl` existants | Suit le précédent direct (sous-chantier 1/3) |

## Composants

### 1. Nouvelle méthode de repository

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/NotificationRepository.java`

Ajouter à la fin de l'interface (après `existsByDossierIdAndType`) :

```java
@Query("""
        SELECT COUNT(n)
        FROM Notification n
        WHERE n.type = :type
          AND n.status = :status
          AND n.dossier.receptionDate BETWEEN :start AND :end
        """)
long countByTypeAndStatusAndDossierReceptionDateBetween(
        @Param("type")   NotificationType type,
        @Param("status") NotificationStatus status,
        @Param("start")  Instant start,
        @Param("end")    Instant end);
```

### 2. `StatistiqueResponse` — nouveaux champs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/StatistiqueResponse.java`

Ajouter (au même niveau que `admissibilityRate` existant) :

```java
/** Part des dossiers reçus ayant déclenché une investigation, en % */
private double investigationCoverageRate;

/** Part des investigations ayant produit un rapport, en % */
private double reportProductionRate;

/** Part des dossiers reçus dont l'accusé de réception (B5) a été effectivement envoyé, en % */
private double acknowledgmentCoverageRate;
```

### 3. `StatistiqueServiceImpl.getDashboard` — nouveaux calculs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImpl.java`

Ajouter juste après le bloc `admissibilityRate` existant (après la ligne
`log.info("[Stats] Taux recevabilité : ...")`) :

```java
double investigationCoverageRate = total > 0
        ? (investigatedCount * 100.0) / total
        : 0.0;

double reportProductionRate = investigatedCount > 0
        ? (reportsProduced * 100.0) / investigatedCount
        : 0.0;

long acknowledgmentSentCount = notificationRepository
        .countByTypeAndStatusAndDossierReceptionDateBetween(
                gov.bf.ascelc.univers_audits.enums.NotificationType.ACKNOWLEDGMENT_B5,
                gov.bf.ascelc.univers_audits.enums.NotificationStatus.SENT,
                start, end);
double acknowledgmentCoverageRate = total > 0
        ? (acknowledgmentSentCount * 100.0) / total
        : 0.0;
```

(`investigatedCount` et `reportsProduced` sont des variables locales déjà calculées plus haut dans
la méthode — cf. `StatistiqueServiceImpl.java:64-70` — pas de recalcul, réutilisation directe.)

Puis dans le `.builder()...build()` existant, ajouter juste après `.admissibilityRate(admissibilityRate)` :

```java
.investigationCoverageRate(investigationCoverageRate)
.reportProductionRate(reportProductionRate)
.acknowledgmentCoverageRate(acknowledgmentCoverageRate)
```

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| Aucun dossier reçu sur la période (`total == 0`) | `investigationCoverageRate = 0.0`, `acknowledgmentCoverageRate = 0.0` (pas de division par zéro) |
| Aucune investigation sur la période (`investigatedCount == 0`) | `reportProductionRate = 0.0` |
| Aucun accusé de réception envoyé sur la période | `acknowledgmentCoverageRate = 0.0` (comportement normal, pas une erreur) |

## Tests

- **`StatistiqueServiceImplTest`** (fichier existant, ajout de tests) :
  - `getDashboard_renseigneInvestigationCoverageRate`
  - `getDashboard_investigationCoverageRateZeroSiAucunDossier`
  - `getDashboard_renseigneReportProductionRate`
  - `getDashboard_reportProductionRateZeroSiAucuneInvestigation`
  - `getDashboard_renseigneAcknowledgmentCoverageRate`
  - `getDashboard_acknowledgmentCoverageRateZeroSiAucunAccuseEnvoye`

## Risques et points d'attention pour le plan d'implémentation

- **Ne pas confondre `NotificationRepository.countByTypeAndStatusAndDossierReceptionDateBetween`
  avec une méthode de comptage générique** — elle filtre spécifiquement sur `n.dossier.receptionDate`
  (le dossier associé), pas sur une date propre à la notification (`scheduledAt`/`sentAt`), pour
  rester cohérente avec l'ancrage de période déjà utilisé par tout le reste du service.
- **Ne pas ajouter de garde `IS NOT NULL`** sur `type`/`status` dans la requête — ce sont des colonnes
  `NOT NULL` sur l'entité `Notification` (`type` ligne 38, `status` ligne 59), la garde serait morte.
- **`total`, `investigatedCount`, `reportsProduced` sont déjà calculés plus haut dans la méthode** —
  ne pas les recalculer, réutiliser les variables locales existantes.
- **Ne pas tenter de calculer le ratio « information du dénonciateur »** — c'est explicitement hors
  périmètre (voir Contexte), aucune donnée n'existe pour l'alimenter.
