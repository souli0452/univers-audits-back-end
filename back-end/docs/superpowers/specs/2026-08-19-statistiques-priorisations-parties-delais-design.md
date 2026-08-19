# Statistiques — priorisations, parties prenantes, délais complémentaires — Design

## Statut

**Lot 7 — Processus D : reporting et pilotage**, redécoupé en 3 sous-chantiers après
découverte d'une base de statistiques déjà existante (voir Contexte) :

1. **Compléter `StatistiqueService`** (priorisations CTADP, parties prenantes, recommandations,
   résultats d'investigation, délais manquants) ← ce document (en cours)
2. Ratios de couverture — à venir
3. Exports pour le rapport annuel d'activité — à venir

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, lignes 375-376) décrit le
Lot 7 comme un chantier neuf. Une exploration du code avant conception a révélé qu'**une base de
statistiques existe déjà**, entièrement câblée et non documentée dans le suivi de ce Lot :
`StatistiqueController` (`GET /stats/public`, `/stats/dashboard`, `/stats/quarterly`, `/stats/annual`),
`StatistiqueService`/`StatistiqueServiceImpl`, `StatistiqueResponse`. Elle couvre déjà : volumes
totaux + tendance mensuelle, répartition par statut et par mode de saisine, montant total des
préjudices estimés, taux de recevabilité, 4 délais moyens (enregistrement, durée d'investigation,
approbation DEI, approbation CGE), 3 compteurs de dépassement de délai.

Ce sous-chantier **complète l'existant**, il ne recrée rien. Il cible précisément les manques
identifiés par comparaison ligne à ligne entre le texte du Lot 7 (§D.2) et le code actuel :

- Résultats des priorisations CTADP — absent
- Institutions et personnes mises en cause — absent
- Types et natures des recommandations (décision CGE) — absent
- Répartition complète des résultats d'investigation — partielle (2 des 5 valeurs
  `InvestigationOutcome` exploitées ; `JUDICIAL_REFERRAL` et `ARCHIVED` seulement)
- Délai « étude d'opportunité » — champ `avgOpportunityStudyDays` déclaré dans le DTO mais **jamais
  calculé** (toujours `null`)
- Délai « analyse conseiller juridique » — absent (`Investigation.legalAdvisorApprovedAt` existe
  mais n'est lu par aucune requête)
- Délai « approbation CGEA » isolée — absent (`Investigation.cgeaApprovedAt` existe, non exploité)
- Délai « transmission des plans d'actions » — absent

**Bloqué par des données manquantes, explicitement hors périmètre** :
- Délai « édition et transmission de l'accusé de réception » (champ `avgAcknowledgmentDays`,
  également mort dans le DTO) : aucun horodatage d'émission du récépissé n'existe dans le modèle
  (`Dossier.acknowledgmentDeadline` est une échéance, pas un horodatage d'émission ; aucune entité
  `Recepisse` n'existe). Nécessiterait une fonctionnalité de génération/horodatage de document, hors
  périmètre d'un chantier d'agrégation.
- Délai « information des partenaires techniques et financiers » : concept absent du modèle.
- Délai « conduite des missions » : `VisiteTerrain` est horodatée par visite, pas par investigation
  — mapping ambigu, laissé de côté.
- Financeurs : champ absent du modèle (`Dossier.estimatedLoss` couvre déjà les montants en cause).
- Motifs de classement en répartition structurée : reste du texte libre partout dans le modèle
  (`Dossier.motifs`, `DecisionCGE.motif`), non agrégeable proprement sans un référentiel qui
  n'existe pas.

**Décision de non-régression** : `avgDeiApprovalDays` (ancré `reportSubmittedAt`→`deiApprovedAt`) et
`avgCgeApprovalDays` (ancré `deiApprovedAt`→`cgeApprovedAt`) incluent chacun, de fait, le délai de
l'étape précédente (DEI inclut CJ, CGE inclut CGEA) — une imprécision préexistante, pas introduite
ici. Ce sous-chantier ajoute des délais **isolés** pour CJ et CGEA à côté des existants, sans modifier
leur définition actuelle (décision utilisateur : ne pas changer un contrat de réponse déjà exposé).

## Objectif

Étendre `StatistiqueResponse`/`StatistiqueServiceImpl` avec les champs manquants identifiés
ci-dessus, en réutilisant exactement le patron de requêtes déjà établi (JPQL `GROUP BY` retournant
`List<Object[]>` pour les répartitions, SQL natif `EXTRACT(EPOCH FROM ...) / 86400.0` pour les
délais moyens en jours), filtré sur la même période `[start, end)` ancrée sur `Dossier.receptionDate`
(répartitions) ou `Investigation.reportSubmittedAt`/`Dossier.receptionDate` (délais, cohérent avec
les requêtes existantes).

## Hors périmètre

- Les 4 points listés ci-dessus comme bloqués par des données manquantes.
- Toute modification des définitions existantes de `avgDeiApprovalDays`/`avgCgeApprovalDays` (voir
  décision de non-régression).
- Toute modification des endpoints `StatistiqueController` — aucun changement de route, de rôle, ni
  de signature ; seuls `StatistiqueResponse` et `StatistiqueServiceImpl` sont étendus, les 4
  endpoints existants exposent automatiquement les nouveaux champs sans changement de code
  contrôleur.
- Ratios de couverture et exports — sous-chantiers 2/3 et 3/3 séparés.
- `financeurs` sur `Dossier` — nécessiterait une migration de schéma, hors périmètre d'un chantier
  purement additif sur la couche agrégation.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Ancrage de période pour les nouvelles répartitions | `Dossier.receptionDate` (via jointure quand la table source n'a pas de champ date propre) | Cohérent avec `countByStatus`/`countBySubmissionMode` déjà existants dans le même service |
| Ancrage de période pour les nouveaux délais | `Investigation.reportSubmittedAt` (CJ, plans d'actions) / `Investigation.deiApprovedAt` (CGEA, isolant le délai propre à cette étape) / `Dossier.receptionDate` (étude d'opportunité) | Réplique exactement le filtre déjà utilisé par `avgDeiApprovalDays`/`avgRegistrationDelayInDays` ; CGEA est ancré sur `deiApprovedAt` (pas `reportSubmittedAt`) pour isoler son propre délai, comme `avgCgeApprovalDays` l'est déjà sur `deiApprovedAt` |
| `countByInvestigationOutcome` complet | Nouvelle méthode de repository distincte, **n'écrase pas** `countByOutcomeBetween(String, ...)` existant | `referredToJustice`/`unfoundedAfterInvestigation` restent calculés tels quels (non-régression), le nouveau champ `countByOutcome` (Map complète) s'ajoute à côté |
| `avgAcknowledgmentDays` | Reste `null`, avec un commentaire de code explicite au point où il serait fixé dans le builder | Documente la décision plutôt que de laisser un champ mort silencieux sans explication |
| `avgOpportunityStudyDays` | Nouvelle requête `Dossier.receptionDate`→`Dossier.eligibilityDecisionDate` en jours | Réutilise un champ déjà présent sur `Dossier` (`eligibilityDecisionDate`) ; une requête `avgProcessingTimeInSeconds` existe déjà sur le même calcul mais en secondes et n'est appelée nulle part — laissée telle quelle (hors périmètre de la retoucher), nouvelle requête dédiée en jours pour cohérence d'unité avec le reste du DTO |
| Test | Nouveau fichier `StatistiqueServiceImplTest` | Aucun test n'existe aujourd'hui pour ce service (gap hérité, pas introduit ici) — ce sous-chantier teste les champs qu'il ajoute, ne tente pas une couverture rétroactive complète de l'existant |
| `TargetedPartyRepository.countByPartyTypeBetween` | Réutilisée telle quelle, pas recréée | Requête déjà présente dans le dépôt (`TargetedPartyRepository.java:39-48`), jamais consommée — même patron « capacité orpheline » déjà rencontré avec `avgProcessingTimeInSeconds` (voir plus haut) |

## Composants

### 1. Nouvelles méthodes de repository

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/SeanceCtadpDossierRepository.java`

```java
@Query("""
        SELECT s.recommandation, COUNT(s)
        FROM SeanceCtadpDossier s
        WHERE s.dossier.receptionDate BETWEEN :start AND :end
        GROUP BY s.recommandation
        """)
List<Object[]> countByRecommandationBetween(
        @Param("start") Instant start,
        @Param("end")   Instant end);
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/DecisionCGERepository.java`

```java
@Query("""
        SELECT d.decision, COUNT(d)
        FROM DecisionCGE d
        WHERE d.dossier.receptionDate BETWEEN :start AND :end
        GROUP BY d.decision
        """)
List<Object[]> countByDecisionBetween(
        @Param("start") Instant start,
        @Param("end")   Instant end);
```

**`TargetedPartyRepository.countByPartyTypeBetween(Instant, Instant): List<Object[]>` existe déjà**
(`TargetedPartyRepository.java:39-48`), identique à ce qui était prévu ici — orpheline, jamais
consommée par aucun service. Aucun ajout nécessaire dans ce fichier ; la Tâche 2 la câble
directement. (Un bonus `countByAllegedRoleBetween` existe aussi au même endroit — hors périmètre
de ce sous-chantier, le texte du Lot 7 ne demande que la répartition par type de partie, pas par
rôle allégué ; non utilisé ici pour rester strictement dans le périmètre approuvé.)

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/InvestigationRepository.java` (ajouts)

```java
@Query("""
        SELECT i.outcome, COUNT(i)
        FROM Investigation i
        JOIN i.dossier d
        WHERE d.receptionDate BETWEEN :start AND :end
        AND i.outcome IS NOT NULL
        GROUP BY i.outcome
        """)
List<Object[]> countByOutcomeGrouped(
        @Param("start") Instant start,
        @Param("end")   Instant end);

@Query(
        value = """
            SELECT AVG(
                EXTRACT(EPOCH FROM (i.legal_advisor_approved_at - i.report_submitted_at))
                / 86400.0
            )
            FROM investigation i
            WHERE i.legal_advisor_approved_at IS NOT NULL
              AND i.report_submitted_at       IS NOT NULL
              AND i.report_submitted_at >= :start
              AND i.report_submitted_at <  :end
            """,
        nativeQuery = true
)
Double avgLegalAdvisorApprovalDays(
        @Param("start") Instant start,
        @Param("end")   Instant end);

@Query(
        value = """
            SELECT AVG(
                EXTRACT(EPOCH FROM (i.cgea_approved_at - i.dei_approved_at))
                / 86400.0
            )
            FROM investigation i
            WHERE i.cgea_approved_at IS NOT NULL
              AND i.dei_approved_at  IS NOT NULL
              AND i.dei_approved_at >= :start
              AND i.dei_approved_at <  :end
            """,
        nativeQuery = true
)
Double avgCgeaApprovalDays(
        @Param("start") Instant start,
        @Param("end")   Instant end);

@Query(
        value = """
            SELECT AVG(
                EXTRACT(EPOCH FROM (pa.submitted_at - i.report_submitted_at))
                / 86400.0
            )
            FROM investigation i
            JOIN plan_actions pa ON pa.investigation_id = i.id
            WHERE i.report_submitted_at >= :start
              AND i.report_submitted_at <  :end
            """,
        nativeQuery = true
)
Double avgPlanActionsSubmissionDays(
        @Param("start") Instant start,
        @Param("end")   Instant end);
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java` (ajout)

```java
@Query(value = """
        SELECT AVG(
            EXTRACT(EPOCH FROM (eligibility_decision_date - reception_date))
            / 86400.0
        )
        FROM dossier
        WHERE reception_date            >= :start
          AND reception_date            <  :end
          AND eligibility_decision_date IS NOT NULL
        """, nativeQuery = true)
Double avgOpportunityStudyDelayInDays(
        @Param("start") Instant start,
        @Param("end")   Instant end);
```

### 2. `StatistiqueResponse` — nouveaux champs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/StatistiqueResponse.java`

Ajouts (au même niveau que les champs existants, pas de nouvelle classe imbriquée nécessaire) :

```java
/** Répartition des recommandations CTADP — clé: RecommandationCtadp, valeur: count */
private Map<String, Long> countByCtadpRecommandation;

/** Répartition des décisions CGE — clé: RecommandationCtadp, valeur: count */
private Map<String, Long> countByCgeDecision;

/** Répartition des parties visées par type — clé: PartyType, valeur: count */
private Map<String, Long> countByTargetedPartyType;

/** Répartition complète des résultats d'investigation — clé: InvestigationOutcome, valeur: count */
private Map<String, Long> countByInvestigationOutcome;

private Double avgLegalAdvisorApprovalDays;

private Double avgCgeaApprovalDays;

private Double avgPlanActionsSubmissionDays;
```

`avgOpportunityStudyDays` et `avgAcknowledgmentDays` existent déjà dans le DTO — pas de nouveau
champ à déclarer pour eux, seule leur alimentation change (voir composant 3).

### 3. `StatistiqueServiceImpl.getDashboard` — nouveaux calculs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImpl.java`

Ajouter les dépendances au constructeur (via les champs `private final`, `@RequiredArgsConstructor`
génère le constructeur) :

```java
private final SeanceCtadpDossierRepository seanceCtadpDossierRepository;
private final DecisionCGERepository        decisionCgeRepository;
private final TargetedPartyRepository      targetedPartyRepository;
```

Dans `getDashboard(Instant start, Instant end)`, ajouter avant le `return` :

```java
Map<String, Long> byCtadpRecommandation = buildMap(
        seanceCtadpDossierRepository.countByRecommandationBetween(start, end));

Map<String, Long> byCgeDecision = buildMap(
        decisionCgeRepository.countByDecisionBetween(start, end));

Map<String, Long> byTargetedPartyType = buildMap(
        targetedPartyRepository.countByPartyTypeBetween(start, end));

Map<String, Long> byInvestigationOutcome = buildMap(
        investigationRepository.countByOutcomeGrouped(start, end));

Double avgOpportunityStudy = dossierRepository
        .avgOpportunityStudyDelayInDays(start, end);

Double avgLegalAdvisorDays = investigationRepository
        .avgLegalAdvisorApprovalDays(start, end);

Double avgCgeaDays = investigationRepository
        .avgCgeaApprovalDays(start, end);

Double avgPlanActionsDays = investigationRepository
        .avgPlanActionsSubmissionDays(start, end);
```

Puis dans le `.builder()...build()`, ajouter :

```java
.countByCtadpRecommandation(byCtadpRecommandation)
.countByCgeDecision(byCgeDecision)
.countByTargetedPartyType(byTargetedPartyType)
.countByInvestigationOutcome(byInvestigationOutcome)
.avgOpportunityStudyDays(avgOpportunityStudy)
// avgAcknowledgmentDays reste null : aucun horodatage d'emission du recepisse
// n'existe dans le modele (Dossier.acknowledgmentDeadline est une echeance,
// pas un horodatage d'emission) — voir spec 2026-08-19-statistiques-...
.avgLegalAdvisorApprovalDays(avgLegalAdvisorDays)
.avgCgeaApprovalDays(avgCgeaDays)
.avgPlanActionsSubmissionDays(avgPlanActionsDays)
```

`buildMap` (méthode privée existante, ligne 234-242) est réutilisée telle quelle — aucune
modification nécessaire, elle gère déjà `List<Object[]>` → `Map<String, Long>` de façon générique.

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| Aucune donnée sur la période pour une répartition (ex: aucune séance CTADP) | `buildMap` retourne une `Map` vide — comportement déjà établi, pas de changement |
| Aucune donnée pour un délai moyen (ex: aucun `PlanActions` déposé sur la période) | La requête `AVG(...)` SQL retourne `NULL` → `Double` `null` côté Java — comportement déjà établi (`avgDeiApprovalDays` etc. dégradent déjà ainsi) |
| `avgAcknowledgmentDays` | Toujours `null`, documenté par un commentaire de code — pas une dégradation dynamique, une absence permanente et volontaire |

## Tests

- **`StatistiqueServiceImplTest`** (nouveau, Mockito) — teste `getDashboard` avec des mocks sur les
  7 repositories (`DossierRepository`, `InvestigationRepository`, `NotificationRepository` existants
  + `SeanceCtadpDossierRepository`, `DecisionCGERepository`, `TargetedPartyRepository` nouveaux) :
  - `getDashboard_renseigneCountByCtadpRecommandation`
  - `getDashboard_renseigneCountByCgeDecision`
  - `getDashboard_renseigneCountByTargetedPartyType`
  - `getDashboard_renseigneCountByInvestigationOutcomeSansAlterernReferredToJusticeExistant`
    (vérifie que le nouveau champ ET l'ancien calcul `referredToJustice`/`unfoundedAfterInvestigation`
    coexistent sans conflit)
  - `getDashboard_renseigneAvgOpportunityStudyDays`
  - `getDashboard_renseigneAvgLegalAdvisorApprovalDays`
  - `getDashboard_renseigneAvgCgeaApprovalDays`
  - `getDashboard_renseigneAvgPlanActionsSubmissionDays`
  - `getDashboard_avgAcknowledgmentDaysResteNull`
  - `getDashboard_degradeVersMapVideSiAucuneDonnee` (toutes les nouvelles répartitions vides
    correctement si les repositories retournent une liste vide)

## Risques et points d'attention pour le plan d'implémentation

- **Ne pas toucher aux requêtes/champs existants** (`avgDeiApprovalDays`, `avgCgeApprovalDays`,
  `countByOutcomeBetween(String,...)`, `referredToJustice`, `unfoundedAfterInvestigation`) — ce
  sous-chantier est purement additif sur ce service déjà en production.
- **`countByOutcomeGrouped` et `countByOutcomeBetween(String,...)` coexistent** — la seconde
  (existante) prend un `String` en paramètre pour filtrer une seule valeur, la première (nouvelle)
  retourne la répartition complète groupée. Ne pas les confondre ni tenter de fusionner.
- **`avgAcknowledgmentDays` ne doit PAS être calculé par approximation** (ex: ne pas le faire
  pointer par erreur vers `acknowledgmentDeadline`, qui est une échéance future, pas un horodatage
  passé — cela produirait des valeurs absurdes ou négatives). Il reste `null`, documenté.
- **Aucun changement de migration, aucun changement de contrôleur** — vérifier que le module compile
  et que la suite complète passe, mais il n'y a ni fichier SQL ni fichier contrôleur à créer dans ce
  sous-chantier.
