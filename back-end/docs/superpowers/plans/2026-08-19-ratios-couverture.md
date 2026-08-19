# Ratios de couverture (Lot 7, sous-chantier 2/3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter 3 ratios de couverture (investigations/dossiers, rapports/investigations, accusés de réception envoyés/dossiers) au service de statistiques déjà existant — deuxième des 3 sous-chantiers du Lot 7.

**Architecture:** Extension purement additive : 1 nouvelle requête `@Query` dans `NotificationRepository` (déjà injecté dans `StatistiqueServiceImpl` mais jamais utilisé), 3 nouveaux champs `double` sur le DTO `StatistiqueResponse` existant, câblage dans `StatistiqueServiceImpl.getDashboard`. Aucune nouvelle entité, aucune migration, aucun changement de contrôleur.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA (JPQL `@Query`), Lombok, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-19-ratios-couverture-design.md`

## Global Constraints

- **Ne modifier aucun champ/requête existant** — ce chantier est purement additif sur `StatistiqueServiceImpl`/`StatistiqueResponse`/`NotificationRepository`, tous déjà en production.
- `investigationCoverageRate` et `reportProductionRate` sont de **pures divisions** sur des variables locales déjà calculées dans `getDashboard` (`total`, `investigatedCount`, `reportsProduced`) — **aucune nouvelle requête** pour ces deux champs.
- `acknowledgmentCoverageRate` nécessite **une seule nouvelle requête** : `NotificationRepository.countByTypeAndStatusAndDossierReceptionDateBetween(NotificationType, NotificationStatus, Instant, Instant): long`, filtrée sur `n.type = ACKNOWLEDGMENT_B5`, `n.status = SENT`, `n.dossier.receptionDate BETWEEN :start AND :end`.
- Garde division par zéro sur les 3 ratios : `0.0` si le dénominateur est nul (même patron que `admissibilityRate` existant, `StatistiqueServiceImpl.java:80-82`).
- **Ratio « rapports/information du dénonciateur » explicitement hors périmètre** — ne pas tenter de le calculer, aucune donnée n'existe pour l'alimenter (voir spec, Contexte).
- Les 3 champs sont tous de type `double` (pourcentage 0-100), pas `Double` — contrairement aux champs de délai (`avgDeiApprovalDays` etc.) qui sont `Double` nullable, ces ratios ont toujours une valeur (0.0 en absence de données), même patron que `admissibilityRate`.
- Aucun test de contrôleur (convention déjà établie sur tout le dépôt).

---

### Task 1: Nouvelle requête de repository et champs DTO

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/NotificationRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/StatistiqueResponse.java`

**Interfaces:**
- Consumes : `Notification.{type, status, dossier}` (existants), `Dossier.receptionDate` (existant), `NotificationType.ACKNOWLEDGMENT_B5` (existant), `NotificationStatus.SENT` (existant).
- Produces : `NotificationRepository.countByTypeAndStatusAndDossierReceptionDateBetween(NotificationType, NotificationStatus, Instant, Instant): long`, et 3 nouveaux champs `double` sur `StatistiqueResponse` (`investigationCoverageRate`, `reportProductionRate`, `acknowledgmentCoverageRate`) — consommés par la Tâche 2.

Pas de test dédié à cette tâche : requête déclarative Spring Data (comme toutes ses voisines dans
ce fichier, aucune n'a de test de repository dédié dans ce dépôt) et DTO sans logique — la Tâche 2
les exerce indirectement via `StatistiqueServiceImplTest`.

- [ ] **Step 1: Ajouter la requête dans `NotificationRepository`**

Ajouter à la fin de l'interface `NotificationRepository` (juste après `existsByDossierIdAndType`,
avant l'accolade fermante finale) :

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

(Les imports `Query`, `Param`, `Instant`, `NotificationType`, `NotificationStatus` sont déjà
présents dans ce fichier — aucun import supplémentaire nécessaire.)

- [ ] **Step 2: Ajouter 3 champs à `StatistiqueResponse`**

Ajouter à la fin de la classe `StatistiqueResponse` (juste avant la classe imbriquée
`MonthlyCount`, après le champ `avgPlanActionsSubmissionDays` ajouté au sous-chantier précédent) :

```java
    /** Part des dossiers reçus ayant déclenché une investigation, en % */
    private double investigationCoverageRate;

    /** Part des investigations ayant produit un rapport, en % */
    private double reportProductionRate;

    /** Part des dossiers reçus dont l'accusé de réception (B5) a été effectivement envoyé, en % */
    private double acknowledgmentCoverageRate;
```

- [ ] **Step 3: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/repository/NotificationRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/StatistiqueResponse.java
git commit -m "feat: add acknowledgment coverage query and coverage rate DTO fields"
```

---

### Task 2: Câblage dans `StatistiqueServiceImpl` et tests

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImplTest.java`

**Interfaces:**
- Consumes : `NotificationRepository.countByTypeAndStatusAndDossierReceptionDateBetween(...)` (Task 1), variables locales déjà existantes dans `getDashboard` (`total`, `investigatedCount`, `reportsProduced`).
- Produces : `StatistiqueServiceImpl.getDashboard` retourne un `StatistiqueResponse` avec les 3 nouveaux champs renseignés — consommé automatiquement par les 4 endpoints `/stats/*` existants (`StatistiqueController`, aucune modification de ce fichier).

- [ ] **Step 1: Écrire les tests (échouent, les nouveaux champs ne sont pas encore câblés)**

Ajouter ces 6 méthodes de test à la fin de la classe `StatistiqueServiceImplTest` (avant
l'accolade fermante finale) :

```java
    @Test
    void getDashboard_renseigneInvestigationCoverageRate() {
        when(dossierRepository.countByReceptionDateBetween(start, end)).thenReturn(10L);
        when(investigationRepository.countByDossierReceptionDateBetween(start, end))
                .thenReturn(4L);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getInvestigationCoverageRate()).isEqualTo(40.0);
    }

    @Test
    void getDashboard_investigationCoverageRateZeroSiAucunDossier() {
        when(dossierRepository.countByReceptionDateBetween(start, end)).thenReturn(0L);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getInvestigationCoverageRate()).isEqualTo(0.0);
    }

    @Test
    void getDashboard_renseigneReportProductionRate() {
        when(investigationRepository.countByDossierReceptionDateBetween(start, end))
                .thenReturn(8L);
        when(dossierRepository.countByStatusAndReceptionDateBetween(start, end))
                .thenReturn(List.of(
                        new Object[]{"RAPPORT_PRODUIT", 2L},
                        new Object[]{"DECISION_RENDUE", 1L},
                        new Object[]{"CLOS", 1L}));

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getReportProductionRate()).isEqualTo(50.0);
    }

    @Test
    void getDashboard_reportProductionRateZeroSiAucuneInvestigation() {
        when(investigationRepository.countByDossierReceptionDateBetween(start, end))
                .thenReturn(0L);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getReportProductionRate()).isEqualTo(0.0);
    }

    @Test
    void getDashboard_renseigneAcknowledgmentCoverageRate() {
        when(dossierRepository.countByReceptionDateBetween(start, end)).thenReturn(20L);
        when(notificationRepository.countByTypeAndStatusAndDossierReceptionDateBetween(
                        gov.bf.ascelc.univers_audits.enums.NotificationType.ACKNOWLEDGMENT_B5,
                        gov.bf.ascelc.univers_audits.enums.NotificationStatus.SENT,
                        start, end))
                .thenReturn(15L);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAcknowledgmentCoverageRate()).isEqualTo(75.0);
    }

    @Test
    void getDashboard_acknowledgmentCoverageRateZeroSiAucunAccuseEnvoye() {
        when(dossierRepository.countByReceptionDateBetween(start, end)).thenReturn(10L);
        when(notificationRepository.countByTypeAndStatusAndDossierReceptionDateBetween(
                        gov.bf.ascelc.univers_audits.enums.NotificationType.ACKNOWLEDGMENT_B5,
                        gov.bf.ascelc.univers_audits.enums.NotificationStatus.SENT,
                        start, end))
                .thenReturn(0L);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAcknowledgmentCoverageRate()).isEqualTo(0.0);
    }
```

`notificationRepository` est déjà un champ `@Mock` de la classe de test (injecté depuis le
sous-chantier 1/3, jamais utilisé jusqu'ici) — pas de nouveau mock à déclarer. Le `@BeforeEach
setUp()` existant stub déjà `notificationRepository.countByTypeAndStatusAndDossierReceptionDateBetween`
en `lenient()` ? **Non** — cette méthode n'existe pas encore avant la Tâche 1, donc aucun stub
`lenient()` ne peut exister pour elle dans `setUp()`. Ajouter ce stub `lenient()` par défaut (valeur
`0L`) dans `setUp()`, juste après les autres stubs `lenient()` du sous-chantier précédent :

```java
        lenient().when(notificationRepository.countByTypeAndStatusAndDossierReceptionDateBetween(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(start),
                        org.mockito.ArgumentMatchers.eq(end)))
                .thenReturn(0L);
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvnw -Dtest=StatistiqueServiceImplTest test`
Expected: FAIL (les assertions sur les 3 nouveaux champs échouent — `getInvestigationCoverageRate()`
etc. n'existent pas encore côté service, ou la compilation échoue si `StatistiqueResponse` n'a pas
encore les champs — la Tâche 1 doit être livrée avant cette Tâche 2, donc en pratique seule
l'assertion échoue, pas la compilation)

- [ ] **Step 3: Câbler les 3 champs dans `StatistiqueServiceImpl`**

Dans `getDashboard(Instant start, Instant end)`, ajouter juste après le bloc existant :
```java
log.info("[Stats] Taux recevabilité : {}/{} = {}%",
        recevablesTotal, examined, String.format("%.1f", admissibilityRate));
```

le nouveau bloc de calcul :

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

Puis dans le `.builder()...build()` existant, ajouter ces 3 lignes juste après
`.admissibilityRate(admissibilityRate)` :

```java
                .investigationCoverageRate(investigationCoverageRate)
                .reportProductionRate(reportProductionRate)
                .acknowledgmentCoverageRate(acknowledgmentCoverageRate)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvnw -Dtest=StatistiqueServiceImplTest test`
Expected: PASS (16 tests — 10 existants + 6 nouveaux)

- [ ] **Step 5: Run the full test suite**

Run: `mvnw -q -Dtest='!UniversAuditsApplicationTests' test`
Expected: BUILD SUCCESS (exclut le seul test d'intégration pré-existant qui échoue en environnement
local faute de DataSource — sans rapport avec ce chantier, déjà confirmé sur la branche de base)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImplTest.java
git commit -m "feat: wire coverage ratios into StatistiqueServiceImpl"
```
