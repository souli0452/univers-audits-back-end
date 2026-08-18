# Plan d'actions et notes d'avancement (Lot 6, sous-chantier 2/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter l'enregistrement factuel du dépôt d'un plan d'actions par l'entité contrôlée (`PlanActions`) et la liste des notes de suivi de son exécution (`NoteAvancement`) — deuxième sous-chantier du Lot 6, avec un indicateur de retard visible même avant tout dépôt.

**Architecture:** Deux nouvelles entités JPA (`PlanActions` 1:1 avec `Investigation`, `NoteAvancement` en liste `@OneToMany` rattachée, sans repository dédié), un service concret qui construit directement le DTO de statut (y compris le calcul d'échéance sur les deux branches « existe » / « n'existe pas encore »), un contrôleur dédié nesté sous `/investigations/{id}/plan-actions`.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok (`@SuperBuilder`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-18-plan-actions-note-avancement-design.md`

## Global Constraints

- Migration Liquibase : fichier `036-create-plan-actions.sql`, format `--liquibase formatted sql` / `--changeset dev:036-create-plan-actions` (numéro confirmé libre, dernier existant = `035`).
- Nouveau code `ParametreDelai` : `PLAN_ACTIONS_ENTITE_CONTROLEE` (20 jours, `jours_ouvrables=TRUE`, `actif=TRUE`).
- Création de `PlanActions` refusée si `investigation.getCgeApprovedAt() == null` (décision finale CGE pas encore rendue) OU si un plan existe déjà pour cette investigation (unique par investigation, contrainte en base).
- `PlanActionsRequest.entiteControlee` ET `PlanActionsRequest.contenu` portent tous les deux `@NotBlank` — contrairement à `RequeteParquet` (brouillon progressif), c'est un événement daté avec contenu obligatoire.
- **Divergence assumée par rapport à `TransmissionAutorite`** : `GET /plan-actions` répond **toujours `200`** avec `PlanActionsStatusResponse.exists` (`true`/`false`), jamais `204`/`404` — nécessaire pour exposer `planActionsDueAt`/`planActionsOverdue` avant même le dépôt. Ne pas copier le patron `try/catch ResourceNotFoundException` du contrôleur `TransmissionAutorite`.
- `planActionsDueAt` ancré sur `investigation.getReportSubmittedAt()` (pas sur `NoteRecommandations`) + délai `PLAN_ACTIONS_ENTITE_CONTROLEE`. `planActionsOverdue` : `true` seulement si `planActions == null` ET l'échéance est dépassée — devient `false` dès qu'un plan existe, quelle que soit sa date réelle de dépôt.
- Masquage confidentialité en lecture (`getStatus`) : réponse « vide » (`exists=false`, `planActionsOverdue=false`, aucun contenu, `avancements=List.of()`), jamais une exception. En écriture (`creer`, `ajouterAvancement`) : rejet `BusinessException`, appliqué dès la conception aux deux méthodes.
- Rôles : écriture (`POST /plan-actions`, `POST .../avancements`) = `hasAnyRole('CGEA','ADMIN_DDIC')` (distinct de `TransmissionAutorite`, qui est `CGE`,`ADMIN_DDIC`). Lecture (`GET /plan-actions`) = `hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')`.
- Aucun nouveau `DossierStatus`.
- Aucun test de contrôleur : confirmé par `find . -iname "*ControllerTest.java"` → 0 résultat dans tout le projet.

---

### Task 1: Entités, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/PlanActions.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/NoteAvancement.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/PlanActionsRepository.java`
- Create: `src/main/resources/db/changelog/migrations/036-create-plan-actions.sql`

**Interfaces:**
- Consumes: `AuditEntity` (`gov.bf.ascelc.univers_audits.abstracts.AuditEntity`), `Investigation`/`Agent` (`gov.bf.ascelc.univers_audits.model.entity`, champ `id` existant).
- Produces: `PlanActions.{getAvancements(): List<NoteAvancement>, ...}`, `NoteAvancement.{...}`, `PlanActionsRepository.findByInvestigationId(UUID): Optional<PlanActions>`, `ParametreDelai` code `PLAN_ACTIONS_ENTITE_CONTROLEE` en base — consommés par la Tâche 2.

Pas de test d'entité dédié : ni `PlanActions` ni `NoteAvancement` n'ont de logique métier propre
(même situation que `TransmissionAutorite`/`RelanceSuites`, sous-chantier précédent).

- [ ] **Step 1: Créer l'entité `PlanActions`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "plan_actions")
public class PlanActions extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "entite_controlee", nullable = false, length = 300)
    private String entiteControlee;

    @Column(name = "contenu", nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_id", nullable = false)
    private Agent receivedBy;

    @OneToMany(mappedBy = "planActions",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<NoteAvancement> avancements = new ArrayList<>();
}
```

- [ ] **Step 2: Créer l'entité `NoteAvancement`**

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
@Table(name = "note_avancement")
public class NoteAvancement extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_actions_id", nullable = false)
    private PlanActions planActions;

    @Column(name = "note_at", nullable = false)
    private Instant noteAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;
}
```

- [ ] **Step 3: Créer `PlanActionsRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PlanActions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanActionsRepository extends JpaRepository<PlanActions, UUID> {
    Optional<PlanActions> findByInvestigationId(UUID investigationId);
}
```

- [ ] **Step 4: Créer la migration `036-create-plan-actions.sql`**

```sql
--liquibase formatted sql
--changeset dev:036-create-plan-actions

CREATE TABLE plan_actions (
    id                UUID         PRIMARY KEY,
    investigation_id  UUID         NOT NULL UNIQUE REFERENCES investigation(id),
    entite_controlee  VARCHAR(300) NOT NULL,
    contenu           TEXT         NOT NULL,
    submitted_at      TIMESTAMP    NOT NULL,
    received_by_id    UUID         NOT NULL REFERENCES agent(id),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE TABLE note_avancement (
    id              UUID      PRIMARY KEY,
    plan_actions_id UUID      NOT NULL REFERENCES plan_actions(id),
    note_at         TIMESTAMP NOT NULL,
    agent_id        UUID      NOT NULL REFERENCES agent(id),
    contenu         TEXT,
    version         BIGINT    NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by_id   VARCHAR(100),
    updated_by_id   VARCHAR(100)
);

CREATE INDEX idx_note_avancement_plan_actions
    ON note_avancement (plan_actions_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'PLAN_ACTIONS_ENTITE_CONTROLEE', 'Délai de dépôt du plan d''actions par l''entité contrôlée après note de recommandations', 20, TRUE, TRUE, 0, now());

COMMENT ON TABLE plan_actions IS 'Depot du plan d actions par l entite controlee (Lot 6 sous-chantier 2/5) - evenement factuel, une seule par investigation, contrairement a TransmissionAutorite le delai de depot est suivi via planActionsOverdue meme avant depot';
COMMENT ON TABLE note_avancement IS 'Notes de suivi de l execution du plan d actions - rattachees a un plan_actions';
```

- [ ] **Step 5: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/PlanActions.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/NoteAvancement.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/PlanActionsRepository.java \
        src/main/resources/db/changelog/migrations/036-create-plan-actions.sql
git commit -m "feat: add PlanActions/NoteAvancement entities, repository and migration"
```

---

### Task 2: DTOs et `PlanActionsService`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PlanActionsRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/NoteAvancementRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/NoteAvancementResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PlanActionsStatusResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/PlanActionsService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/PlanActionsServiceTest.java`

**Interfaces:**
- Consumes: `PlanActionsRepository.{findByInvestigationId, save}` (Task 1), `InvestigationRepository.findById(UUID): Optional<Investigation>` (existant), `DossierAccessGuard.{checkReadAccess(Dossier), canSeeConfidential()}` (existant), `AgentContextResolver.getCurrentAgent(): Agent` (existant), `ParametreDelaiService.resolveDelaiJours(String): int` (existant, lève `ResourceNotFoundException`), `Investigation.{getCgeApprovedAt(), getReportSubmittedAt()}` (existant).
- Produces: `PlanActionsService.{creer(UUID, PlanActionsRequest): PlanActionsStatusResponse, getStatus(UUID): PlanActionsStatusResponse, ajouterAvancement(UUID, NoteAvancementRequest): PlanActionsStatusResponse}` — consommés par la Tâche 3.

- [ ] **Step 1: Créer les DTOs**

Fichier `PlanActionsRequest.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanActionsRequest {
    @NotBlank(message = "L'entité contrôlée est obligatoire")
    private String entiteControlee;

    @NotBlank(message = "Le contenu du plan d'actions est obligatoire")
    private String contenu;
}
```

Fichier `NoteAvancementRequest.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteAvancementRequest {
    private String contenu;
}
```

Fichier `NoteAvancementResponse.java` :

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
public class NoteAvancementResponse {
    private UUID id;
    private Instant noteAt;
    private String agentNom;
    private String contenu;
}
```

Fichier `PlanActionsStatusResponse.java` :

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
public class PlanActionsStatusResponse {
    private UUID investigationId;
    private boolean exists;
    private Instant planActionsDueAt;
    private boolean planActionsOverdue;
    private UUID id;
    private String entiteControlee;
    private String contenu;
    private Instant submittedAt;
    private String receivedByNom;
    private List<NoteAvancementResponse> avancements;
}
```

- [ ] **Step 2: Écrire les tests de `PlanActionsService` (échouent, la classe n'existe pas encore)**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteAvancementRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanActionsRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanActionsStatusResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanActionsServiceTest {

    @Mock private PlanActionsRepository planActionsRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private ParametreDelaiService parametreDelaiService;

    @InjectMocks
    private PlanActionsService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .dossier(dossier)
                .cgeApprovedAt(Instant.now())
                .reportSubmittedAt(Instant.now().minusSeconds(5L * 24 * 3600))
                .build();
    }

    @Test
    void creer_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        PlanActionsRequest request = PlanActionsRequest.builder()
                .entiteControlee("Direction Générale des Impôts").contenu("Plan d'action détaillé").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiPlanDejaExistant() {
        PlanActions existant = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee("DGI")
                .contenu("Plan existant")
                .submittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(existant));

        PlanActionsRequest request = PlanActionsRequest.builder()
                .entiteControlee("Autre entité").contenu("Autre plan").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        PlanActionsRequest request = PlanActionsRequest.builder()
                .entiteControlee("Direction Générale des Impôts").contenu("Plan d'action détaillé").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).save(any());
    }

    @Test
    void creer_succeedsEtRenseigneSubmittedAtEtReceivedBy() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(planActionsRepository.save(any(PlanActions.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        PlanActionsRequest request = PlanActionsRequest.builder()
                .entiteControlee("Direction Générale des Impôts").contenu("Plan d'action détaillé").build();

        PlanActionsStatusResponse result = service.creer(investigationId, request);

        assertThat(result.isExists()).isTrue();
        assertThat(result.getEntiteControlee()).isEqualTo("Direction Générale des Impôts");
        assertThat(result.getSubmittedAt()).isNotNull();
        assertThat(result.getReceivedByNom()).isEqualTo("Jean Ouedraogo");
    }

    @Test
    void getStatus_existsFauxSiAucunPlan() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.isExists()).isFalse();
        assertThat(result.getAvancements()).isEmpty();
    }

    @Test
    void getStatus_masqueSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.isExists()).isFalse();
        assertThat(result.isPlanActionsOverdue()).isFalse();
        assertThat(result.getAvancements()).isEmpty();
        verify(planActionsRepository, never()).findByInvestigationId(any());
    }

    @Test
    void getStatus_leveBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.getStatus(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).findByInvestigationId(any());
    }

    @Test
    void ajouterAvancement_rejetteSiAucunPlan() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        NoteAvancementRequest request = NoteAvancementRequest.builder().contenu("Suivi").build();

        assertThatThrownBy(() -> service.ajouterAvancement(investigationId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ajouterAvancement_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        NoteAvancementRequest request = NoteAvancementRequest.builder().contenu("Suivi").build();

        assertThatThrownBy(() -> service.ajouterAvancement(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).save(any());
    }

    @Test
    void ajouterAvancement_ajouteALaListeExistante() {
        PlanActions planActions = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee("DGI")
                .contenu("Plan d'action détaillé")
                .submittedAt(Instant.now())
                .build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(planActions));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(planActionsRepository.save(any(PlanActions.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        NoteAvancementRequest request = NoteAvancementRequest.builder().contenu("Première étape réalisée").build();

        PlanActionsStatusResponse result = service.ajouterAvancement(investigationId, request);

        assertThat(result.getAvancements()).hasSize(1);
        assertThat(result.getAvancements().get(0).getContenu()).isEqualTo("Première étape réalisée");
        assertThat(result.getAvancements().get(0).getAgentNom()).isEqualTo("Awa Sawadogo");
    }

    @Test
    void planActionsOverdue_vraiSiEcheanceDepasseeEtAucunPlanDepose() {
        investigation.setReportSubmittedAt(Instant.now().minusSeconds(30L * 24 * 3600));
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.isPlanActionsOverdue()).isTrue();
    }

    @Test
    void planActionsOverdue_fauxSiPlanDejaDepose() {
        investigation.setReportSubmittedAt(Instant.now().minusSeconds(30L * 24 * 3600));
        PlanActions planActions = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee("DGI")
                .contenu("Plan déposé tardivement")
                .submittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(planActions));
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.isPlanActionsOverdue()).isFalse();
    }

    @Test
    void planActionsOverdue_degradeVersFauxSiParametreIndisponible() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE"))
                .thenThrow(new ResourceNotFoundException("Paramètre introuvable"));

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.getPlanActionsDueAt()).isNull();
        assertThat(result.isPlanActionsOverdue()).isFalse();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvnw -Dtest=PlanActionsServiceTest test`
Expected: FAIL (compilation error — `PlanActionsService` n'existe pas)

- [ ] **Step 4: Créer `PlanActionsService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteAvancementRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanActionsRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.NoteAvancementResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanActionsStatusResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
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
public class PlanActionsService {

    private final PlanActionsRepository planActionsRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final ParametreDelaiService parametreDelaiService;

    @Transactional
    public PlanActionsStatusResponse creer(UUID investigationId, PlanActionsRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "Le dépôt du plan d'actions n'est possible qu'après la décision finale du CGE.");
        }
        if (planActionsRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Un plan d'actions a déjà été déposé pour ce dossier.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        PlanActions planActions = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee(request.getEntiteControlee())
                .contenu(request.getContenu())
                .submittedAt(Instant.now())
                .receivedBy(agent)
                .build();

        PlanActions saved = planActionsRepository.save(planActions);
        log.info("Plan d'actions enregistré — investigation: {}", investigationId);
        return toResponse(investigation, saved);
    }

    public PlanActionsStatusResponse getStatus(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return PlanActionsStatusResponse.builder()
                    .investigationId(investigationId)
                    .exists(false)
                    .planActionsOverdue(false)
                    .avancements(List.of())
                    .build();
        }

        Optional<PlanActions> planActions = planActionsRepository.findByInvestigationId(investigationId);
        return toResponse(investigation, planActions.orElse(null));
    }

    @Transactional
    public PlanActionsStatusResponse ajouterAvancement(UUID investigationId, NoteAvancementRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        PlanActions planActions = planActionsRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'actions déposé pour cette investigation — "
                                + "impossible d'ajouter une note d'avancement."));

        Agent agent = agentContextResolver.getCurrentAgent();
        NoteAvancement note = NoteAvancement.builder()
                .planActions(planActions)
                .noteAt(Instant.now())
                .agent(agent)
                .contenu(request.getContenu())
                .build();
        planActions.getAvancements().add(note);

        PlanActions saved = planActionsRepository.save(planActions);
        log.info("Note d'avancement ajoutée — investigation: {}", investigationId);
        return toResponse(investigation, saved);
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private PlanActionsStatusResponse toResponse(Investigation investigation, PlanActions planActions) {
        Instant dueAt = investigation.getReportSubmittedAt() != null
                ? resolveDeadline(investigation.getReportSubmittedAt(), "PLAN_ACTIONS_ENTITE_CONTROLEE")
                : null;
        boolean overdue = planActions == null
                && dueAt != null
                && Instant.now().isAfter(dueAt);

        PlanActionsStatusResponse.PlanActionsStatusResponseBuilder builder =
                PlanActionsStatusResponse.builder()
                        .investigationId(investigation.getId())
                        .exists(planActions != null)
                        .planActionsDueAt(dueAt)
                        .planActionsOverdue(overdue)
                        .avancements(List.of());

        if (planActions != null) {
            builder.id(planActions.getId())
                    .entiteControlee(planActions.getEntiteControlee())
                    .contenu(planActions.getContenu())
                    .submittedAt(planActions.getSubmittedAt())
                    .receivedByNom(planActions.getReceivedBy().getNomComplet())
                    .avancements(planActions.getAvancements().stream()
                            .map(this::toAvancementResponse)
                            .toList());
        }

        return builder.build();
    }

    private NoteAvancementResponse toAvancementResponse(NoteAvancement n) {
        return NoteAvancementResponse.builder()
                .id(n.getId())
                .noteAt(n.getNoteAt())
                .agentNom(n.getAgent().getNomComplet())
                .contenu(n.getContenu())
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

Run: `mvnw -Dtest=PlanActionsServiceTest test`
Expected: PASS (13 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PlanActionsRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/NoteAvancementRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/NoteAvancementResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PlanActionsStatusResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/PlanActionsService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/PlanActionsServiceTest.java
git commit -m "feat: add PlanActionsService"
```

---

### Task 3: Contrôleur `PlanActionsController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/PlanActionsController.java`

**Interfaces:**
- Consumes: `PlanActionsService.{creer, getStatus, ajouterAvancement}` (Task 2), `ApiUrls.INVESTIGATIONS` (existant).
- Produces: endpoints `GET/POST /api/v1/investigations/{id}/plan-actions`, `POST /api/v1/investigations/{id}/plan-actions/avancements` — dernière tâche du chantier.

Pas de test de contrôleur (convention déjà établie).

- [ ] **Step 1: Créer `PlanActionsController`**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteAvancementRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanActionsRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanActionsStatusResponse;
import gov.bf.ascelc.univers_audits.service.PlanActionsService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/plan-actions")
public class PlanActionsController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CGEA','ADMIN_DDIC')";

    private final PlanActionsService planActionsService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<PlanActionsStatusResponse> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(planActionsService.getStatus(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PlanActionsStatusResponse> creerPlanActions(
            @PathVariable UUID id,
            @Valid @RequestBody PlanActionsRequest request) {

        log.info("Enregistrement plan d'actions — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planActionsService.creer(id, request));
    }

    @PostMapping("/avancements")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PlanActionsStatusResponse> ajouterAvancement(
            @PathVariable UUID id,
            @Valid @RequestBody NoteAvancementRequest request) {

        log.info("Ajout d'une note d'avancement — investigation {}", id);
        return ResponseEntity.ok(planActionsService.ajouterAvancement(id, request));
    }
}
```

Contrairement à `TransmissionAutoriteController.getTransmission`, `getStatus` ne fait **aucun**
`try/catch` — le service ne lève jamais d'exception pour signaler une absence, il répond toujours
`200` avec `exists=false`.

- [ ] **Step 2: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run the full test suite**

Run: `mvnw -q -Dtest='!UniversAuditsApplicationTests' test`
Expected: BUILD SUCCESS (exclut le seul test d'intégration pré-existant qui échoue en environnement
local faute de DataSource — sans rapport avec ce chantier, déjà confirmé sur la branche de base)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/PlanActionsController.java
git commit -m "feat: add plan-actions REST endpoints"
```
