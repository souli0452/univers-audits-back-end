# Mission de suivi (Lot 6, sous-chantier 3/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter l'enregistrement de missions de suivi (`MissionSuivi`) — visites de vérification terrain de l'exécution d'un plan d'actions, avec un indicateur de retard visible même avant toute mission enregistrée — troisième sous-chantier du Lot 6.

**Architecture:** Une nouvelle entité JPA (`MissionSuivi`, liste `0..n` rattachée directement à `Investigation`, FK non-unique — contrairement à `PlanActions`/`TransmissionAutorite`), un service concret qui construit directement le DTO de liste (y compris le calcul d'échéance ancré sur `PlanActions.submittedAt`), un contrôleur dédié nesté sous `/investigations/{id}/missions-suivi`.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok (`@SuperBuilder`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-18-mission-suivi-design.md`

## Global Constraints

- Migration Liquibase : fichier `037-create-mission-suivi.sql`, format `--liquibase formatted sql` / `--changeset dev:037-create-mission-suivi` (numéro confirmé libre, dernier existant = `036`).
- Nouveau code `ParametreDelai` : `MISSION_SUIVI_PLAN_ACTIONS` (365 jours, `jours_ouvrables=FALSE` — le texte dit « dans l'année », pas « X jours ouvrables », contrairement aux autres lignes du §7).
- Création de `MissionSuivi` refusée si `investigation.getCgeApprovedAt() == null` OU si aucun `PlanActions` n'existe pour cette investigation (`BusinessException` dans les deux cas). Pas de contrainte d'unicité — plusieurs missions possibles par investigation.
- `MissionSuiviRequest.missionDate` porte `@NotNull` ; `objectifs` et `syntheseRecommandations` portent `@NotBlank` ; `nouvellesRecommandations` reste libre (nullable, une mission peut ne rien ajouter).
- **Divergence assumée, dans la continuité de `PlanActions`** : `GET /missions-suivi` répond **toujours `200`** avec `MissionSuiviListResponse` (`missions: []` si aucune), jamais `204`/`404` — nécessaire pour exposer `missionSuiviDueAt`/`missionSuiviOverdue` avant même la première mission. Ne pas copier le patron `try/catch ResourceNotFoundException` du contrôleur `TransmissionAutorite`.
- `missionSuiviDueAt` ancré sur `PlanActions.submittedAt` (**pas** `investigation.getReportSubmittedAt()`, à la différence de `PlanActionsDueAt`) + délai `MISSION_SUIVI_PLAN_ACTIONS`. Si aucun `PlanActions` n'existe, `dueAt = null`. `missionSuiviOverdue` : `true` seulement si la liste des missions est vide ET l'échéance est dépassée — devient `false` dès qu'au moins une mission existe, quelle que soit sa date réelle.
- Masquage confidentialité en lecture (`lister`) : réponse « vide » (`missions=List.of()`, `missionSuiviOverdue=false`, `missionSuiviDueAt=null`), jamais une exception. En écriture (`ajouter`) : rejet `BusinessException`, appliqué dès la conception.
- Rôles : écriture (`POST /missions-suivi`) = `hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')` (distinct de `PlanActions`, qui est `CGEA`,`ADMIN_DDIC` — le rôle DSRAJ attendu par le texte source n'existe pas dans le système, voir spec). Lecture (`GET /missions-suivi`) = `hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')`.
- Aucun nouveau `DossierStatus`.
- Aucun test de contrôleur : convention déjà établie sur tout le dépôt (0 fichier `*ControllerTest.java`).

---

### Task 1: Entité, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/MissionSuivi.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/MissionSuiviRepository.java`
- Create: `src/main/resources/db/changelog/migrations/037-create-mission-suivi.sql`

**Interfaces:**
- Consumes: `AuditEntity` (`gov.bf.ascelc.univers_audits.abstracts.AuditEntity`), `Investigation`/`Agent` (`gov.bf.ascelc.univers_audits.model.entity`, champ `id` existant).
- Produces: `MissionSuivi.{getInvestigation(), getMissionDate(), getConductedBy(), getObjectifs(), getSyntheseRecommandations(), getNouvellesRecommandations(), getSubmittedAt()}`, `MissionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(UUID): List<MissionSuivi>`, `ParametreDelai` code `MISSION_SUIVI_PLAN_ACTIONS` en base — consommés par la Tâche 2.

Pas de test d'entité dédié : `MissionSuivi` n'a pas de logique métier propre (même situation que
`PlanActions`/`TransmissionAutorite`, sous-chantiers précédents).

- [ ] **Step 1: Créer l'entité `MissionSuivi`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mission_suivi", indexes = {
        @Index(name = "idx_mission_suivi_investigation",
                columnList = "investigation_id")
})
public class MissionSuivi extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "mission_date", nullable = false)
    private Instant missionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id", nullable = false)
    private Agent conductedBy;

    @Column(name = "objectifs", nullable = false, columnDefinition = "TEXT")
    private String objectifs;

    @Column(name = "synthese_recommandations", nullable = false, columnDefinition = "TEXT")
    private String syntheseRecommandations;

    @Column(name = "nouvelles_recommandations", columnDefinition = "TEXT")
    private String nouvellesRecommandations;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
```

- [ ] **Step 2: Créer `MissionSuiviRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.MissionSuivi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MissionSuiviRepository extends JpaRepository<MissionSuivi, UUID> {
    List<MissionSuivi> findByInvestigationIdOrderByMissionDateDesc(UUID investigationId);
}
```

- [ ] **Step 3: Créer la migration `037-create-mission-suivi.sql`**

```sql
--liquibase formatted sql
--changeset dev:037-create-mission-suivi

CREATE TABLE mission_suivi (
    id                        UUID         PRIMARY KEY,
    investigation_id          UUID         NOT NULL REFERENCES investigation(id),
    mission_date              TIMESTAMP    NOT NULL,
    conducted_by_id           UUID         NOT NULL REFERENCES agent(id),
    objectifs                 TEXT         NOT NULL,
    synthese_recommandations  TEXT         NOT NULL,
    nouvelles_recommandations TEXT,
    submitted_at              TIMESTAMP    NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0,
    created_at                TIMESTAMP    NOT NULL,
    updated_at                TIMESTAMP,
    created_by_id              VARCHAR(100),
    updated_by_id              VARCHAR(100)
);

CREATE INDEX idx_mission_suivi_investigation
    ON mission_suivi (investigation_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'MISSION_SUIVI_PLAN_ACTIONS', 'Délai pour mener une mission de suivi après le dépôt du plan d''actions', 365, FALSE, TRUE, 0, now());

COMMENT ON TABLE mission_suivi IS 'Missions de verification terrain de l execution des plans d actions (Lot 6 sous-chantier 3/5) - liste, plusieurs missions possibles par investigation, contrairement a PlanActions/TransmissionAutorite';
```

- [ ] **Step 4: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/MissionSuivi.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/MissionSuiviRepository.java \
        src/main/resources/db/changelog/migrations/037-create-mission-suivi.sql
git commit -m "feat: add MissionSuivi entity, repository and migration"
```

---

### Task 2: DTOs et `MissionSuiviService`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/MissionSuiviRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MissionSuiviResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MissionSuiviListResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/MissionSuiviService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/MissionSuiviServiceTest.java`

**Interfaces:**
- Consumes: `MissionSuiviRepository.{findByInvestigationIdOrderByMissionDateDesc, save}` (Task 1), `PlanActionsRepository.findByInvestigationId(UUID): Optional<PlanActions>` (existant, ajouté sous-chantier 2/5), `InvestigationRepository.findById(UUID): Optional<Investigation>` (existant), `DossierAccessGuard.{checkReadAccess(Dossier), canSeeConfidential()}` (existant), `AgentContextResolver.getCurrentAgent(): Agent` (existant), `ParametreDelaiService.resolveDelaiJours(String): int` (existant, lève `ResourceNotFoundException`), `Investigation.getCgeApprovedAt()` (existant).
- Produces: `MissionSuiviService.{ajouter(UUID, MissionSuiviRequest): MissionSuiviListResponse, lister(UUID): MissionSuiviListResponse}` — consommés par la Tâche 3.

- [ ] **Step 1: Créer les DTOs**

Fichier `MissionSuiviRequest.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionSuiviRequest {
    @NotNull(message = "La date de la mission est obligatoire")
    private Instant missionDate;

    @NotBlank(message = "Les objectifs de la mission sont obligatoires")
    private String objectifs;

    @NotBlank(message = "La synthèse des recommandations est obligatoire")
    private String syntheseRecommandations;

    private String nouvellesRecommandations;
}
```

Fichier `MissionSuiviResponse.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionSuiviResponse {
    private UUID id;
    private Instant missionDate;
    private String conductedByNom;
    private String objectifs;
    private String syntheseRecommandations;
    private String nouvellesRecommandations;
    private Instant submittedAt;
}
```

Fichier `MissionSuiviListResponse.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionSuiviListResponse {
    private UUID investigationId;
    private Instant missionSuiviDueAt;
    private boolean missionSuiviOverdue;
    private List<MissionSuiviResponse> missions;
}
```

- [ ] **Step 2: Écrire les tests de `MissionSuiviService` (échouent, la classe n'existe pas encore)**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.MissionSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviListResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.MissionSuiviRepository;
import gov.bf.ascelc.univers_audits.repository.PlanActionsRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionSuiviServiceTest {

    @Mock private MissionSuiviRepository missionSuiviRepository;
    @Mock private PlanActionsRepository planActionsRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private ParametreDelaiService parametreDelaiService;

    @InjectMocks
    private MissionSuiviService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;
    private PlanActions planActions;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .dossier(dossier)
                .cgeApprovedAt(Instant.now())
                .build();
        planActions = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee("Direction Générale des Impôts")
                .contenu("Plan d'action détaillé")
                .submittedAt(Instant.now().minusSeconds(100L * 24 * 3600))
                .build();
    }

    private MissionSuiviRequest.MissionSuiviRequestBuilder validRequest() {
        return MissionSuiviRequest.builder()
                .missionDate(Instant.now())
                .objectifs("Vérifier l'application des recommandations")
                .syntheseRecommandations("3 sur 5 recommandations appliquées, retard justifié par un manque de budget");
    }

    @Test
    void ajouter_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.ajouter(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(missionSuiviRepository, never()).save(any());
    }

    @Test
    void ajouter_rejetteSiAucunPlanActions() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ajouter(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(missionSuiviRepository, never()).save(any());
    }

    @Test
    void ajouter_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.ajouter(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(missionSuiviRepository, never()).save(any());
    }

    @Test
    void ajouter_succeedsEtRenseigneConductedByEtSubmittedAt() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(missionSuiviRepository.save(any(MissionSuivi.class))).thenAnswer(inv -> inv.getArgument(0));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result.getInvestigationId()).isEqualTo(investigationId);
        verify(missionSuiviRepository).save(argThat(m ->
                m.getConductedBy() == agent
                        && m.getSubmittedAt() != null
                        && m.getObjectifs().equals("Vérifier l'application des recommandations")));
    }

    @Test
    void ajouter_permetPlusieursMissionsPourLaMemeInvestigation() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        MissionSuivi missionExistante = MissionSuivi.builder()
                .investigation(investigation)
                .missionDate(Instant.now().minusSeconds(200L * 24 * 3600))
                .conductedBy(agent)
                .objectifs("Première mission")
                .syntheseRecommandations("Synthèse initiale")
                .submittedAt(Instant.now().minusSeconds(200L * 24 * 3600))
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(missionSuiviRepository.save(any(MissionSuivi.class))).thenAnswer(inv -> inv.getArgument(0));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of(missionExistante));
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result.getMissions()).hasSize(1);
        verify(missionSuiviRepository).save(any(MissionSuivi.class));
    }

    @Test
    void lister_listeVideSiAucuneMission() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.getMissions()).isEmpty();
    }

    @Test
    void lister_masqueSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.getMissions()).isEmpty();
        assertThat(result.isMissionSuiviOverdue()).isFalse();
        verify(planActionsRepository, never()).findByInvestigationId(any());
        verify(missionSuiviRepository, never()).findByInvestigationIdOrderByMissionDateDesc(any());
    }

    @Test
    void lister_leveBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.lister(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(missionSuiviRepository, never()).findByInvestigationIdOrderByMissionDateDesc(any());
    }

    @Test
    void lister_dueAtNullSiAucunPlanActions() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.getMissionSuiviDueAt()).isNull();
        assertThat(result.isMissionSuiviOverdue()).isFalse();
    }

    @Test
    void missionSuiviOverdue_vraiSiEcheanceDepasseeEtAucuneMission() {
        planActions.setSubmittedAt(Instant.now().minusSeconds(400L * 24 * 3600));
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.isMissionSuiviOverdue()).isTrue();
    }

    @Test
    void missionSuiviOverdue_fauxSiAuMoinsUneMissionExiste() {
        planActions.setSubmittedAt(Instant.now().minusSeconds(400L * 24 * 3600));
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        MissionSuivi mission = MissionSuivi.builder()
                .investigation(investigation)
                .missionDate(Instant.now())
                .conductedBy(agent)
                .objectifs("Vérification tardive")
                .syntheseRecommandations("Synthèse")
                .submittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of(mission));
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.isMissionSuiviOverdue()).isFalse();
    }

    @Test
    void missionSuiviOverdue_degradeVersFauxSiParametreIndisponible() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS"))
                .thenThrow(new ResourceNotFoundException("Paramètre introuvable"));

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.getMissionSuiviDueAt()).isNull();
        assertThat(result.isMissionSuiviOverdue()).isFalse();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvnw -Dtest=MissionSuiviServiceTest test`
Expected: FAIL (compilation error — `MissionSuiviService` n'existe pas)

- [ ] **Step 4: Créer `MissionSuiviService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.MissionSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviListResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.MissionSuiviRepository;
import gov.bf.ascelc.univers_audits.repository.PlanActionsRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionSuiviService {

    private final MissionSuiviRepository missionSuiviRepository;
    private final PlanActionsRepository planActionsRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final ParametreDelaiService parametreDelaiService;

    @Transactional
    public MissionSuiviListResponse ajouter(UUID investigationId, MissionSuiviRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "L'enregistrement d'une mission de suivi n'est possible qu'après la décision finale du CGE.");
        }
        PlanActions planActions = planActionsRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'actions déposé pour cette investigation — "
                                + "impossible d'enregistrer une mission de suivi."));

        Agent agent = agentContextResolver.getCurrentAgent();
        MissionSuivi mission = MissionSuivi.builder()
                .investigation(investigation)
                .missionDate(request.getMissionDate())
                .conductedBy(agent)
                .objectifs(request.getObjectifs())
                .syntheseRecommandations(request.getSyntheseRecommandations())
                .nouvellesRecommandations(request.getNouvellesRecommandations())
                .submittedAt(Instant.now())
                .build();

        missionSuiviRepository.save(mission);
        log.info("Mission de suivi enregistrée — investigation: {}", investigationId);
        return toListResponse(investigation, planActions);
    }

    public MissionSuiviListResponse lister(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return MissionSuiviListResponse.builder()
                    .investigationId(investigationId)
                    .missionSuiviOverdue(false)
                    .missions(List.of())
                    .build();
        }

        Optional<PlanActions> planActions = planActionsRepository.findByInvestigationId(investigationId);
        return toListResponse(investigation, planActions.orElse(null));
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private MissionSuiviListResponse toListResponse(Investigation investigation, PlanActions planActions) {
        List<MissionSuivi> missions =
                missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigation.getId());

        Instant dueAt = planActions != null
                ? resolveDeadline(planActions.getSubmittedAt(), "MISSION_SUIVI_PLAN_ACTIONS")
                : null;
        boolean overdue = missions.isEmpty()
                && dueAt != null
                && Instant.now().isAfter(dueAt);

        return MissionSuiviListResponse.builder()
                .investigationId(investigation.getId())
                .missionSuiviDueAt(dueAt)
                .missionSuiviOverdue(overdue)
                .missions(missions.stream().map(this::toResponse).toList())
                .build();
    }

    private MissionSuiviResponse toResponse(MissionSuivi m) {
        return MissionSuiviResponse.builder()
                .id(m.getId())
                .missionDate(m.getMissionDate())
                .conductedByNom(m.getConductedBy().getNomComplet())
                .objectifs(m.getObjectifs())
                .syntheseRecommandations(m.getSyntheseRecommandations())
                .nouvellesRecommandations(m.getNouvellesRecommandations())
                .submittedAt(m.getSubmittedAt())
                .build();
    }

    private Instant resolveDeadline(Instant from, String delaiCode) {
        try {
            int delaiJours = parametreDelaiService.resolveDelaiJours(delaiCode);
            return from.plusSeconds((long) delaiJours * 24 * 3600);
        } catch (ResourceNotFoundException e) {
            log.warn("Délai {} indisponible — échéance non calculée : {}", delaiCode, e.getMessage());
            return null;
        }
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvnw -Dtest=MissionSuiviServiceTest test`
Expected: PASS (12 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/MissionSuiviRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MissionSuiviResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MissionSuiviListResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/MissionSuiviService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/MissionSuiviServiceTest.java
git commit -m "feat: add MissionSuiviService"
```

---

### Task 3: Contrôleur `MissionSuiviController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/MissionSuiviController.java`

**Interfaces:**
- Consumes: `MissionSuiviService.{ajouter, lister}` (Task 2), `ApiUrls.INVESTIGATIONS` (existant).
- Produces: endpoints `GET/POST /api/v1/investigations/{id}/missions-suivi` — dernière tâche du chantier.

Pas de test de contrôleur (convention déjà établie).

- [ ] **Step 1: Créer `MissionSuiviController`**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.MissionSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviListResponse;
import gov.bf.ascelc.univers_audits.service.MissionSuiviService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/missions-suivi")
public class MissionSuiviController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final MissionSuiviService missionSuiviService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<MissionSuiviListResponse> lister(@PathVariable UUID id) {
        return ResponseEntity.ok(missionSuiviService.lister(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<MissionSuiviListResponse> ajouter(
            @PathVariable UUID id,
            @Valid @RequestBody MissionSuiviRequest request) {

        log.info("Enregistrement mission de suivi — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(missionSuiviService.ajouter(id, request));
    }
}
```

Comme `PlanActionsController.getStatus`, `lister` ne fait **aucun** `try/catch` — le service ne
lève jamais d'exception pour signaler une absence, il répond toujours `200` avec `missions: []`.

- [ ] **Step 2: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run the full test suite**

Run: `mvnw -q -Dtest='!UniversAuditsApplicationTests' test`
Expected: BUILD SUCCESS (exclut le seul test d'intégration pré-existant qui échoue en environnement
local faute de DataSource — sans rapport avec ce chantier, déjà confirmé sur la branche de base)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/MissionSuiviController.java
git commit -m "feat: add missions-suivi REST endpoints"
```
