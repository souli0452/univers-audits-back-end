# Statistiques — priorisations, parties prenantes, délais complémentaires (Lot 7, sous-chantier 1/3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compléter le service de statistiques déjà existant (`StatistiqueServiceImpl`) avec les répartitions et délais moyens manquants identifiés dans le texte du Lot 7 (priorisations CTADP, parties prenantes, décisions CGE, résultats d'investigation complets, 3 délais moyens supplémentaires) — premier des 3 sous-chantiers du Lot 7.

**Architecture:** Extension purement additive : nouvelles requêtes `@Query` dans 3 repositories existants (`SeanceCtadpDossierRepository`, `DecisionCGERepository`, `InvestigationRepository`) et 1 nouvelle requête native dans `DossierRepository`, nouveaux champs sur le DTO `StatistiqueResponse` existant, câblage dans `StatistiqueServiceImpl.getDashboard`. Aucune nouvelle entité, aucune migration, aucun changement de contrôleur — les 4 endpoints `/stats/*` existants exposent automatiquement les nouveaux champs.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA (JPQL `@Query` + SQL natif), Lombok, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-19-statistiques-priorisations-parties-delais-design.md`

## Global Constraints

- **Ne modifier aucune requête ni champ existant** : `avgDeiApprovalDays`, `avgCgeApprovalDays`, `countByOutcomeBetween(String, Instant, Instant)`, `referredToJustice`, `unfoundedAfterInvestigation` restent inchangés (décision de non-régression — voir spec, Contexte).
- **`TargetedPartyRepository.countByPartyTypeBetween(Instant, Instant): List<Object[]>` existe déjà** (`TargetedPartyRepository.java:39-48`) — ne pas la recréer, seulement l'injecter et l'appeler dans `StatistiqueServiceImpl`.
- **`avgAcknowledgmentDays` reste `null`** — pas de requête à écrire pour ce champ, seulement un commentaire de code documentant pourquoi (aucun horodatage d'émission du récépissé n'existe dans le modèle).
- **`avgProcessingTimeInSeconds` (existant, `DossierRepository.java:217-228`) reste inchangée et non appelée** — une nouvelle requête dédiée `avgOpportunityStudyDelayInDays` est créée à la place, en jours (cohérence d'unité avec le reste du DTO), pas en secondes.
- Toutes les nouvelles requêtes de répartition (`GROUP BY`) suivent le patron JPQL exact déjà établi : `SELECT <champ>, COUNT(<alias>) FROM <Entité> <alias> WHERE <alias>.dossier.receptionDate BETWEEN :start AND :end GROUP BY <champ>`, retour `List<Object[]>`, consommé via la méthode privée `buildMap` déjà existante dans `StatistiqueServiceImpl` (aucune modification de `buildMap` nécessaire).
- Toutes les nouvelles requêtes de délai moyen suivent le patron SQL natif exact déjà établi : `SELECT AVG(EXTRACT(EPOCH FROM (<fin> - <debut>)) / 86400.0) FROM <table> WHERE <fin> IS NOT NULL AND <debut> IS NOT NULL AND <ancrage> >= :start AND <ancrage> < :end`, retour `Double`.
- Aucun test de contrôleur (convention déjà établie sur tout le dépôt).

---

### Task 1: Nouvelles requêtes de repository et champs DTO

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/SeanceCtadpDossierRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/DecisionCGERepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/InvestigationRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/StatistiqueResponse.java`

**Interfaces:**
- Consumes : `SeanceCtadpDossier.{dossier, recommandation}`, `DecisionCGE.{dossier, decision}`, `Investigation.{dossier, outcome, legalAdvisorApprovedAt, reportSubmittedAt, deiApprovedAt, cgeaApprovedAt}`, `Dossier.{receptionDate, eligibilityDecisionDate}`, `PlanActions` (table `plan_actions`, colonnes `investigation_id`, `submitted_at`) — toutes existantes.
- Produces : `SeanceCtadpDossierRepository.countByRecommandationBetween(Instant, Instant): List<Object[]>`, `DecisionCGERepository.countByDecisionBetween(Instant, Instant): List<Object[]>`, `InvestigationRepository.{countByOutcomeGrouped(Instant, Instant): List<Object[]>, avgLegalAdvisorApprovalDays(Instant, Instant): Double, avgCgeaApprovalDays(Instant, Instant): Double, avgPlanActionsSubmissionDays(Instant, Instant): Double}`, `DossierRepository.avgOpportunityStudyDelayInDays(Instant, Instant): Double` — consommés par la Tâche 2. `StatistiqueResponse` porte 7 nouveaux champs consommés par la Tâche 2.

Pas de test dédié à cette tâche : ce sont des requêtes déclaratives Spring Data (comme toutes leurs
voisines dans ces fichiers, aucune n'a de test de repository dédié dans ce dépôt) et un DTO sans
logique — la Tâche 2 les exerce indirectement via `StatistiqueServiceImplTest`.

- [ ] **Step 1: Ajouter la requête dans `SeanceCtadpDossierRepository`**

Fichier actuel (`SeanceCtadpDossierRepository.java`) :

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeanceCtadpDossierRepository
        extends JpaRepository<SeanceCtadpDossier, UUID> {

    boolean existsBySeanceCtadpIdAndDossierId(UUID seanceCtadpId, UUID dossierId);

    Optional<SeanceCtadpDossier> findBySeanceCtadpIdAndDossierId(
            UUID seanceCtadpId, UUID dossierId);
}
```

Remplacer le contenu complet du fichier par :

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeanceCtadpDossierRepository
        extends JpaRepository<SeanceCtadpDossier, UUID> {

    boolean existsBySeanceCtadpIdAndDossierId(UUID seanceCtadpId, UUID dossierId);

    Optional<SeanceCtadpDossier> findBySeanceCtadpIdAndDossierId(
            UUID seanceCtadpId, UUID dossierId);

    @Query("""
            SELECT s.recommandation, COUNT(s)
            FROM SeanceCtadpDossier s
            WHERE s.dossier.receptionDate BETWEEN :start AND :end
            GROUP BY s.recommandation
            """)
    List<Object[]> countByRecommandationBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end);
}
```

- [ ] **Step 2: Ajouter la requête dans `DecisionCGERepository`**

Fichier actuel (`DecisionCGERepository.java`) :

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.DecisionCGE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisionCGERepository extends JpaRepository<DecisionCGE, UUID> {

    Optional<DecisionCGE> findByDossierId(UUID dossierId);
}
```

Remplacer le contenu complet du fichier par :

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.DecisionCGE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisionCGERepository extends JpaRepository<DecisionCGE, UUID> {

    Optional<DecisionCGE> findByDossierId(UUID dossierId);

    @Query("""
            SELECT d.decision, COUNT(d)
            FROM DecisionCGE d
            WHERE d.dossier.receptionDate BETWEEN :start AND :end
            GROUP BY d.decision
            """)
    List<Object[]> countByDecisionBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end);
}
```

- [ ] **Step 3: Ajouter 4 requêtes dans `InvestigationRepository`**

Ajouter à la fin de l'interface `InvestigationRepository` (juste avant l'accolade fermante finale,
après la méthode `countByOutcomeBetween` existante) :

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

- [ ] **Step 4: Ajouter 1 requête dans `DossierRepository`**

Ajouter à la fin de l'interface `DossierRepository` (juste avant l'accolade fermante finale, après
la méthode `avgProcessingTimeInSeconds` existante) :

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

- [ ] **Step 5: Ajouter 7 champs à `StatistiqueResponse`**

Ajouter à la fin de la classe `StatistiqueResponse` (juste avant la classe imbriquée
`MonthlyCount`, après le champ `overdueComplements` existant) :

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

- [ ] **Step 6: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/repository/SeanceCtadpDossierRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/DecisionCGERepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/InvestigationRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/StatistiqueResponse.java
git commit -m "feat: add reporting queries and DTO fields for CTADP/CGE/investigation/delay stats"
```

---

### Task 2: Câblage dans `StatistiqueServiceImpl` et tests

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImplTest.java`

**Interfaces:**
- Consumes : les 6 nouvelles méthodes de repository de la Tâche 1, plus
  `TargetedPartyRepository.countByPartyTypeBetween(Instant, Instant): List<Object[]>` (déjà
  existante, `TargetedPartyRepository.java:39-48`, pas créée par ce plan).
- Produces : `StatistiqueServiceImpl.getDashboard` retourne un `StatistiqueResponse` avec les 7
  nouveaux champs renseignés — consommé automatiquement par les 4 endpoints `/stats/*` existants
  (`StatistiqueController`, aucune modification de ce fichier).

- [ ] **Step 1: Écrire les tests (échouent, les nouveaux champs ne sont pas encore câblés)**

Fichier `StatistiqueServiceImplTest.java` (nouveau — aucun test n'existe aujourd'hui pour ce
service) :

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;
import gov.bf.ascelc.univers_audits.repository.DecisionCGERepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatistiqueServiceImplTest {

    @Mock private DossierRepository dossierRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private SeanceCtadpDossierRepository seanceCtadpDossierRepository;
    @Mock private DecisionCGERepository decisionCgeRepository;
    @Mock private TargetedPartyRepository targetedPartyRepository;

    @InjectMocks
    private StatistiqueServiceImpl service;

    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        start = Instant.parse("2026-01-01T00:00:00Z");
        end = Instant.parse("2026-02-01T00:00:00Z");

        // Stubs communs a getDashboard, non pertinents pour ces tests mais necessaires
        // pour que la methode s'execute sans NPE (Mockito "lenient" car pas tous exerces
        // par chaque test individuel).
        lenient().when(dossierRepository.countByReceptionDateBetween(start, end)).thenReturn(0L);
        lenient().when(dossierRepository.countByStatusAndReceptionDateBetween(start, end))
                .thenReturn(List.of());
        lenient().when(dossierRepository.countBySubmissionModeBetween(start, end))
                .thenReturn(List.of());
        lenient().when(dossierRepository.countByTypeBetween(start, end)).thenReturn(List.of());
        lenient().when(dossierRepository.sumEstimatedLossBetween(start, end)).thenReturn(null);
        lenient().when(investigationRepository.countByDossierReceptionDateBetween(start, end))
                .thenReturn(0L);
        lenient().when(investigationRepository.countByOutcomeBetween("JUDICIAL_REFERRAL", start, end))
                .thenReturn(0L);
        lenient().when(investigationRepository.countByOutcomeBetween("ARCHIVED", start, end))
                .thenReturn(0L);
        lenient().when(dossierRepository.countByStatusInAndReceptionDateBetween(
                        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(start),
                        org.mockito.ArgumentMatchers.eq(end)))
                .thenReturn(0L);
        lenient().when(dossierRepository.avgRegistrationDelayInDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgDurationInDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgDeiApprovalDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgCgeApprovalDays(start, end)).thenReturn(null);
        lenient().when(dossierRepository.countOverdueAcknowledgments(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        lenient().when(investigationRepository.countOverdue(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        lenient().when(dossierRepository.countOverdueComplementRequests(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);

        // Stubs des nouvelles requetes, valeurs neutres par defaut
        lenient().when(seanceCtadpDossierRepository.countByRecommandationBetween(start, end))
                .thenReturn(List.of());
        lenient().when(decisionCgeRepository.countByDecisionBetween(start, end))
                .thenReturn(List.of());
        lenient().when(targetedPartyRepository.countByPartyTypeBetween(start, end))
                .thenReturn(List.of());
        lenient().when(investigationRepository.countByOutcomeGrouped(start, end))
                .thenReturn(List.of());
        lenient().when(dossierRepository.avgOpportunityStudyDelayInDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgLegalAdvisorApprovalDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgCgeaApprovalDays(start, end)).thenReturn(null);
        lenient().when(investigationRepository.avgPlanActionsSubmissionDays(start, end)).thenReturn(null);
    }

    @Test
    void getDashboard_renseigneCountByCtadpRecommandation() {
        when(seanceCtadpDossierRepository.countByRecommandationBetween(start, end))
                .thenReturn(List.of(
                        new Object[]{
                                gov.bf.ascelc.univers_audits.enums.RecommandationCtadp.VALIDATION_INVESTIGATION,
                                5L}));

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByCtadpRecommandation())
                .containsEntry("VALIDATION_INVESTIGATION", 5L);
    }

    @Test
    void getDashboard_renseigneCountByCgeDecision() {
        when(decisionCgeRepository.countByDecisionBetween(start, end))
                .thenReturn(List.of(
                        new Object[]{
                                gov.bf.ascelc.univers_audits.enums.RecommandationCtadp.CLASSEMENT, 3L}));

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByCgeDecision()).containsEntry("CLASSEMENT", 3L);
    }

    @Test
    void getDashboard_renseigneCountByTargetedPartyType() {
        when(targetedPartyRepository.countByPartyTypeBetween(start, end))
                .thenReturn(List.of(
                        new Object[]{gov.bf.ascelc.univers_audits.enums.PartyType.PUBLIC_AGENT, 8L}));

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByTargetedPartyType()).containsEntry("PUBLIC_AGENT", 8L);
    }

    @Test
    void getDashboard_renseigneCountByInvestigationOutcomeSansAltererReferredToJusticeExistant() {
        when(investigationRepository.countByOutcomeGrouped(start, end))
                .thenReturn(List.of(
                        new Object[]{
                                gov.bf.ascelc.univers_audits.enums.InvestigationOutcome.JUDICIAL_REFERRAL, 2L},
                        new Object[]{
                                gov.bf.ascelc.univers_audits.enums.InvestigationOutcome.PRESS_RELEASE, 1L}));
        when(investigationRepository.countByOutcomeBetween("JUDICIAL_REFERRAL", start, end))
                .thenReturn(2L);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByInvestigationOutcome())
                .containsEntry("JUDICIAL_REFERRAL", 2L)
                .containsEntry("PRESS_RELEASE", 1L);
        assertThat(result.getReferredToJustice()).isEqualTo(2L);
    }

    @Test
    void getDashboard_renseigneAvgOpportunityStudyDays() {
        when(dossierRepository.avgOpportunityStudyDelayInDays(start, end)).thenReturn(4.5);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgOpportunityStudyDays()).isEqualTo(4.5);
    }

    @Test
    void getDashboard_renseigneAvgLegalAdvisorApprovalDays() {
        when(investigationRepository.avgLegalAdvisorApprovalDays(start, end)).thenReturn(6.0);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgLegalAdvisorApprovalDays()).isEqualTo(6.0);
    }

    @Test
    void getDashboard_renseigneAvgCgeaApprovalDays() {
        when(investigationRepository.avgCgeaApprovalDays(start, end)).thenReturn(3.2);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgCgeaApprovalDays()).isEqualTo(3.2);
    }

    @Test
    void getDashboard_renseigneAvgPlanActionsSubmissionDays() {
        when(investigationRepository.avgPlanActionsSubmissionDays(start, end)).thenReturn(18.7);

        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgPlanActionsSubmissionDays()).isEqualTo(18.7);
    }

    @Test
    void getDashboard_avgAcknowledgmentDaysResteNull() {
        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getAvgAcknowledgmentDays()).isNull();
    }

    @Test
    void getDashboard_degradeVersMapVideSiAucuneDonnee() {
        StatistiqueResponse result = service.getDashboard(start, end);

        assertThat(result.getCountByCtadpRecommandation()).isEmpty();
        assertThat(result.getCountByCgeDecision()).isEmpty();
        assertThat(result.getCountByTargetedPartyType()).isEmpty();
        assertThat(result.getCountByInvestigationOutcome()).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvnw -Dtest=StatistiqueServiceImplTest test`
Expected: FAIL (les assertions sur les nouveaux champs échouent — les champs sont `null`/absents
car `StatistiqueServiceImpl` ne les câble pas encore ; le test peut aussi échouer à la compilation
si `@InjectMocks` ne trouve pas de constructeur acceptant les 3 nouveaux mocks avant l'étape 3)

- [ ] **Step 3: Câbler les 7 champs dans `StatistiqueServiceImpl`**

Ajouter les 3 nouvelles dépendances au champ existant (juste après `notificationRepository`, avant
la constante `OUAGA_TZ`) :

```java
    private final SeanceCtadpDossierRepository seanceCtadpDossierRepository;
    private final DecisionCGERepository        decisionCgeRepository;
    private final TargetedPartyRepository      targetedPartyRepository;
```

Ajouter les imports correspondants en haut du fichier (avec les imports `repository.*` existants) :

```java
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.DecisionCGERepository;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
```

Dans `getDashboard(Instant start, Instant end)`, ajouter juste avant le bloc
`List<StatistiqueResponse.MonthlyCount> monthlyTrend = buildMonthlyTrend(start, end);` existant :

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

Dans le `.builder()...build()` existant, ajouter ces lignes juste avant `.overdueAcknowledgments(overdueAck)` :

```java
                .countByCtadpRecommandation(byCtadpRecommandation)
                .countByCgeDecision(byCgeDecision)
                .countByTargetedPartyType(byTargetedPartyType)
                .countByInvestigationOutcome(byInvestigationOutcome)
                .avgOpportunityStudyDays(avgOpportunityStudy)
                // avgAcknowledgmentDays reste null : aucun horodatage d'emission du recepisse
                // n'existe dans le modele (Dossier.acknowledgmentDeadline est une echeance,
                // pas un horodatage d'emission) — voir spec
                // 2026-08-19-statistiques-priorisations-parties-delais-design.md
                .avgLegalAdvisorApprovalDays(avgLegalAdvisorDays)
                .avgCgeaApprovalDays(avgCgeaDays)
                .avgPlanActionsSubmissionDays(avgPlanActionsDays)
```

`buildMap` (méthode privée existante, inchangée) gère déjà la conversion `List<Object[]>` →
`Map<String, Long>` de façon générique — aucune modification nécessaire.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvnw -Dtest=StatistiqueServiceImplTest test`
Expected: PASS (10 tests)

- [ ] **Step 5: Run the full test suite**

Run: `mvnw -q -Dtest='!UniversAuditsApplicationTests' test`
Expected: BUILD SUCCESS (exclut le seul test d'intégration pré-existant qui échoue en environnement
local faute de DataSource — sans rapport avec ce chantier, déjà confirmé sur la branche de base)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/StatistiqueServiceImplTest.java
git commit -m "feat: wire CTADP/CGE/investigation/delay stats into StatistiqueServiceImpl"
```
