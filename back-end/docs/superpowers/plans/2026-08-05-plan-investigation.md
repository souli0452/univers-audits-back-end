# Plan d'investigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `PlanInvestigation` (the current, mutable investigation plan) and
`RevisionPlan` (its complete, immutable revision history), with a submit → validate →
revise workflow gated by role, a configurable DEI-validation deadline computed from the
existing `Mandat`, and read endpoints for both the current plan and its full history.

**Architecture:** Two new entities as sub-resources of `Investigation` (same pattern as
`Mandat`/`EngagementConfidentialite` from the two already-merged sub-chantiers of this
Lot): `PlanInvestigation` (1:1, mutable current state) and `RevisionPlan` (1:N,
append-only snapshots taken immediately before each revision overwrites the current
state). The DEI-validation deadline is never stored — it is computed on every read from
`Mandat.dateDelivrance` plus a configurable `ParametreDelai`, mirroring how
`Investigation.isOverdue()`/`getRemainingDays()` already compute deadlines on the fly
rather than storing them.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL),
Lombok `@SuperBuilder`, JUnit 5 + Mockito.

## Global Constraints

- `PlanInvestigation` fields: `investigation` (FK unique), `objectifs` (text,
  required), `methodologie` (text, required), `moyensMobilises` (text, optional),
  `planningProcedures` (text, optional — decision: folded into this entity as a plain
  text field rather than a separate structured entity, see spec), `planVersion`
  (`Integer`, starts at 1), `submittedAt`/`submittedBy` (set once, never changed by a
  revision), `validatedAt`/`validatedBy` (nullable, reset to `null` on every revision).
- **Naming trap already caught while designing this plan**: `AuditEntity` (the base
  class every entity in this codebase extends) already declares a `@Version private
  Long version` field for JPA optimistic locking. A business field also named
  `version` on `PlanInvestigation` would not compile (Lombok would generate a
  `getVersion(): Integer`/`setVersion(Integer)` pair colliding with the inherited
  `getVersion(): Long`/`setVersion(Long)` — not a valid override, return types aren't
  covariant). The business field is therefore named **`planVersion`**, not `version`,
  throughout every layer (entity, DTOs, migration column `plan_version`). Do not
  rename it back to `version` anywhere.
- `RevisionPlan` is a full-content snapshot of what `PlanInvestigation` held
  **immediately before** the revision overwrote it — not a diff. Its own version
  marker is named `versionNumber` (no collision risk, distinct word).
- `submitPlan` requires a `Mandat` to already exist for the investigation (rejects if
  not) and requires no `PlanInvestigation` to exist yet (rejects if one does — use
  revise instead).
- `revisePlan` requires an existing `PlanInvestigation` (rejects if none), requires a
  non-blank `motifRevision`, snapshots the pre-revision content into a new
  `RevisionPlan` row, increments `planVersion`, and resets `validatedAt`/`validatedBy`
  to `null` (decision: revising an already-validated plan un-validates it — the DEI
  validated a specific content, not a permanent framework).
- `validatePlan` requires an existing `PlanInvestigation` (rejects if none) and rejects
  if already validated (`validatedAt != null`). It does **not** block on the deadline
  being passed — late validation is allowed, the deadline is purely informational.
- Roles: `submitPlan`/`revisePlan` → `CONTROLEUR_ETAT, ADMIN_DDIC` (mirrors the
  existing `submitReport` endpoint's roles — same "chef de mission" actor).
  `validatePlan` → `CGEA, ADMIN_DDIC` (mirrors the existing `approveDei` endpoint's
  roles — there is no dedicated "DEI" Keycloak role in this codebase, confirmed by
  that precedent). Reads (`getPlan`, `getPlanRevisions`) → `CGEA, CGE,
  CONTROLEUR_ETAT, MEMBRE_CTADP, ADMIN_DDIC` (mirrors every other investigation-detail
  read in this controller).
- The DEI-validation deadline (`validationDeadline`) is computed on every response
  build, never stored: `Mandat.dateDelivrance + resolveDelaiJours("VALIDATION_PLAN_INVESTIGATION_DEI")`
  days (simple calendar-day arithmetic via `plusSeconds`, same simplification already
  used by `Investigation.start()`'s `plannedEndDate` calculation — no real
  business-day/holiday-calendar engine exists anywhere in this codebase despite the
  `joursOuvrables` flag on `ParametreDelai` being declarative only). `overdue = now >
  validationDeadline && validatedAt == null`. If no `Mandat` exists yet (should not
  happen once `submitPlan`'s precondition is enforced, but the helper must not throw
  if it does), both `validationDeadline` and `overdue` degrade gracefully — `null`/
  `false` — never an exception.
- No PDF generation for the "plan d'investigation" document (§9) — deferred, same
  treatment as `Mandat`/`EngagementConfidentialite`.
- No change to `open()`/`start()` — the `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` gate is
  sub-chantier 6/6, out of scope here.
- `ParametreDelaiService.resolveDelaiJours(String code)` throws
  `ResourceNotFoundException` if the code isn't seeded in the database — every prior
  chantier introducing a new delay code seeds it in its own migration (see `004`,
  `008`). This plan's migration seeds `VALIDATION_PLAN_INVESTIGATION_DEI` = 8,
  `jours_ouvrables = TRUE`.
- `AuditEntity` columns for both new tables: `id UUID PRIMARY KEY`,
  `version BIGINT NOT NULL DEFAULT 0` (the JPA optimistic-lock column — yes, the
  column is legitimately named `version` at the SQL level via `AuditEntity`; only the
  new *business* field avoids that name), `created_at TIMESTAMP NOT NULL`,
  `updated_at TIMESTAMP`, `created_by_id VARCHAR(100)`, `updated_by_id VARCHAR(100)`.
- Next migration file number is `021` (last is `020-add-engagement-confidentialite.sql`).
- `InvestigationServiceImpl` uses `@RequiredArgsConstructor` with a manually-ordered
  field list; append the two new repository fields at the end.
  `InvestigationServiceImplTest` uses `@InjectMocks` only (no manual positional
  constructor), so field order is safe.
- Existing wildcard imports already cover most new classes without new import lines:
  `InvestigationServiceImpl.java` has `import ...model.dto.request.*;`,
  `import ...model.entity.*;`, `import ...repository.*;` — only
  `PlanInvestigationResponse`/`RevisionPlanResponse` need explicit imports (response
  DTOs are imported individually in this file). `InvestigationService.java` and
  `InvestigationController.java` import both request and response DTOs explicitly (no
  wildcards) — both need explicit imports for all 4 new DTOs.
  `InvestigationController.java` does not currently import `java.util.List` — Task 3
  adds it (needed for `List<RevisionPlanResponse>`).
- `auditRecorder.addObservation(...)` calls ARE appropriate in this plan's service
  methods (unlike the self-service `declareEngagementPrealable` from sub-chantier 2,
  whose unrestricted `isAuthenticated()` access led to a dossier-timeline write being
  removed at that sub-chantier's final review) — `submitPlan`/`revisePlan`/
  `validatePlan` are all role-gated (`CONTROLEUR_ETAT`/`CGEA`/`ADMIN_DDIC`), mirroring
  `submitReport`'s existing pattern, which also writes an observation. Keep them.

---

### Task 1: `PlanInvestigation`/`RevisionPlan` entities, repositories, DTOs, migration 021

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/PlanInvestigation.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/RevisionPlan.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/PlanInvestigationRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/RevisionPlanRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PlanInvestigationSubmitRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PlanInvestigationRevisionRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PlanInvestigationResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RevisionPlanResponse.java`
- Create: `src/main/resources/db/changelog/migrations/021-add-plan-investigation.sql`

**Interfaces:**
- Consumes: `Investigation`, `Agent` entities (`Agent` has `getNomComplet()`),
  `AuditEntity` base class.
- Produces: `PlanInvestigation` entity (fields per Global Constraints) and
  `RevisionPlan` entity (fields per Global Constraints) — consumed by Task 2.
- Produces: `PlanInvestigationRepository.findByInvestigationId(UUID):
  Optional<PlanInvestigation>` and `RevisionPlanRepository
  .findByPlanInvestigationIdOrderByVersionNumberDesc(UUID): List<RevisionPlan>` —
  consumed by Task 2.
- Produces: `PlanInvestigationSubmitRequest` (`objectifs`/`methodologie` `@NotBlank`,
  `moyensMobilises`/`planningProcedures` optional), `PlanInvestigationRevisionRequest`
  (same 4 fields plus `motifRevision` `@NotBlank`), `PlanInvestigationResponse` (`id,
  investigationId, planVersion, objectifs, methodologie, moyensMobilises,
  planningProcedures, submittedAt, submittedById, submittedByNom, validatedAt,
  validatedById, validatedByNom, validationDeadline, overdue`), `RevisionPlanResponse`
  (`id, versionNumber, objectifs, methodologie, moyensMobilises, planningProcedures,
  revisedAt, revisedById, revisedByNom, motifRevision`) — consumed by Task 2 and
  Task 3.

- [ ] **Step 1: Create the `PlanInvestigation` entity**

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
@Table(name = "plan_investigation")
public class PlanInvestigation extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "objectifs", nullable = false, columnDefinition = "TEXT")
    private String objectifs;

    @Column(name = "methodologie", nullable = false, columnDefinition = "TEXT")
    private String methodologie;

    @Column(name = "moyens_mobilises", columnDefinition = "TEXT")
    private String moyensMobilises;

    @Column(name = "planning_procedures", columnDefinition = "TEXT")
    private String planningProcedures;

    @Column(name = "plan_version", nullable = false)
    private Integer planVersion;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    private Agent submittedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id")
    private Agent validatedBy;
}
```

- [ ] **Step 2: Create the `RevisionPlan` entity**

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
@Table(name = "revision_plan", indexes = {
        @Index(name = "idx_revision_plan_plan_investigation",
                columnList = "plan_investigation_id")
})
public class RevisionPlan extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_investigation_id", nullable = false)
    private PlanInvestigation planInvestigation;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "objectifs", nullable = false, columnDefinition = "TEXT")
    private String objectifs;

    @Column(name = "methodologie", nullable = false, columnDefinition = "TEXT")
    private String methodologie;

    @Column(name = "moyens_mobilises", columnDefinition = "TEXT")
    private String moyensMobilises;

    @Column(name = "planning_procedures", columnDefinition = "TEXT")
    private String planningProcedures;

    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revised_by_id", nullable = false)
    private Agent revisedBy;

    @Column(name = "motif_revision", nullable = false, columnDefinition = "TEXT")
    private String motifRevision;
}
```

- [ ] **Step 3: Create `PlanInvestigationRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PlanInvestigation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanInvestigationRepository
        extends JpaRepository<PlanInvestigation, UUID> {

    Optional<PlanInvestigation> findByInvestigationId(UUID investigationId);
}
```

- [ ] **Step 4: Create `RevisionPlanRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RevisionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RevisionPlanRepository extends JpaRepository<RevisionPlan, UUID> {

    List<RevisionPlan> findByPlanInvestigationIdOrderByVersionNumberDesc(
            UUID planInvestigationId);
}
```

- [ ] **Step 5: Create `PlanInvestigationSubmitRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanInvestigationSubmitRequest {

    @NotBlank(message = "Les objectifs de l'enquête sont obligatoires")
    private String objectifs;

    @NotBlank(message = "La méthodologie est obligatoire")
    private String methodologie;

    private String moyensMobilises;

    private String planningProcedures;
}
```

- [ ] **Step 6: Create `PlanInvestigationRevisionRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanInvestigationRevisionRequest {

    @NotBlank(message = "Les objectifs de l'enquête sont obligatoires")
    private String objectifs;

    @NotBlank(message = "La méthodologie est obligatoire")
    private String methodologie;

    private String moyensMobilises;

    private String planningProcedures;

    @NotBlank(message = "Le motif de la révision est obligatoire")
    private String motifRevision;
}
```

- [ ] **Step 7: Create `PlanInvestigationResponse`**

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
public class PlanInvestigationResponse {

    private UUID id;
    private UUID investigationId;
    private Integer planVersion;
    private String objectifs;
    private String methodologie;
    private String moyensMobilises;
    private String planningProcedures;
    private Instant submittedAt;
    private UUID submittedById;
    private String submittedByNom;
    private Instant validatedAt;
    private UUID validatedById;
    private String validatedByNom;
    private Instant validationDeadline;
    private boolean overdue;
}
```

- [ ] **Step 8: Create `RevisionPlanResponse`**

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
public class RevisionPlanResponse {

    private UUID id;
    private Integer versionNumber;
    private String objectifs;
    private String methodologie;
    private String moyensMobilises;
    private String planningProcedures;
    private Instant revisedAt;
    private UUID revisedById;
    private String revisedByNom;
    private String motifRevision;
}
```

- [ ] **Step 9: Write migration 021**

Create `src/main/resources/db/changelog/migrations/021-add-plan-investigation.sql`:

```sql
--liquibase formatted sql
--changeset dev:021-add-plan-investigation

CREATE TABLE plan_investigation (
    id                   UUID PRIMARY KEY,
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP,
    created_by_id        VARCHAR(100),
    updated_by_id        VARCHAR(100),
    investigation_id     UUID NOT NULL UNIQUE REFERENCES investigation(id),
    objectifs            TEXT NOT NULL,
    methodologie         TEXT NOT NULL,
    moyens_mobilises     TEXT,
    planning_procedures  TEXT,
    plan_version         INTEGER NOT NULL,
    submitted_at         TIMESTAMP NOT NULL,
    submitted_by_id      UUID NOT NULL REFERENCES agent(id),
    validated_at         TIMESTAMP,
    validated_by_id      UUID REFERENCES agent(id)
);

CREATE TABLE revision_plan (
    id                     UUID PRIMARY KEY,
    version                BIGINT NOT NULL DEFAULT 0,
    created_at             TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP,
    created_by_id          VARCHAR(100),
    updated_by_id          VARCHAR(100),
    plan_investigation_id  UUID NOT NULL REFERENCES plan_investigation(id),
    version_number         INTEGER NOT NULL,
    objectifs              TEXT NOT NULL,
    methodologie           TEXT NOT NULL,
    moyens_mobilises       TEXT,
    planning_procedures    TEXT,
    revised_at             TIMESTAMP NOT NULL,
    revised_by_id          UUID NOT NULL REFERENCES agent(id),
    motif_revision         TEXT NOT NULL
);

CREATE INDEX idx_revision_plan_plan_investigation
    ON revision_plan (plan_investigation_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'VALIDATION_PLAN_INVESTIGATION_DEI', 'Délai de validation du plan d''investigation par le DEI, après délivrance du mandat', 8, TRUE, TRUE, 0, now());

COMMENT ON TABLE plan_investigation IS 'Plan d investigation courant (mutable) d une investigation - objectifs/methodologie/moyens/planning, valide par le DEI (Lot 3, plan de travail S5/S7/S11)';
COMMENT ON TABLE revision_plan IS 'Historique complet des revisions du plan d investigation - snapshot immuable du contenu remplace a chaque revision (Lot 3, plan de travail S5)';
```

This migration is auto-discovered by `db.changelog-master.yaml`'s `includeAll` on
`db/changelog/migrations/` — no changelog master edit needed. `revision_plan`'s index
does not duplicate a unique constraint (no unique constraint exists on this table) —
unlike `EngagementConfidentialite`'s prior lesson, this is a legitimate plain index for
the one-to-many lookup, not redundant.

- [ ] **Step 10: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (new classes only, nothing consumes them yet).

- [ ] **Step 11: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/PlanInvestigation.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/RevisionPlan.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/PlanInvestigationRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/RevisionPlanRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PlanInvestigationSubmitRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PlanInvestigationRevisionRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PlanInvestigationResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RevisionPlanResponse.java \
        src/main/resources/db/changelog/migrations/021-add-plan-investigation.sql
git commit -m "feat: add PlanInvestigation and RevisionPlan entities, repositories, DTOs and migration"
```

---

### Task 2: Service layer — submit/revise/validate/read, deadline computation, tests

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `PlanInvestigation`/`RevisionPlan`/repositories/DTOs (Task 1),
  `MandatRepository.findByInvestigationId` and `Mandat.getDateDelivrance()` (already
  exist from sub-chantier 1), `ParametreDelaiService.resolveDelaiJours` (already
  exists).
- Produces: `InvestigationService.submitPlan(UUID, PlanInvestigationSubmitRequest,
  String): PlanInvestigationResponse`, `.revisePlan(UUID,
  PlanInvestigationRevisionRequest, String): PlanInvestigationResponse`,
  `.validatePlan(UUID, String): PlanInvestigationResponse`, `.getPlan(UUID):
  PlanInvestigationResponse`, `.getPlanRevisions(UUID): List<RevisionPlanResponse>` —
  consumed by Task 3's controller.

- [ ] **Step 1: Add imports and fields to `InvestigationServiceImpl`**

Add these imports next to the existing
`import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;`:

```java
import gov.bf.ascelc.univers_audits.model.dto.response.PlanInvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RevisionPlanResponse;
```

Add these fields at the end of the existing field list (after
`private final EngagementConfidentialiteRepository engagementConfidentialiteRepository;`):

```java
    private final PlanInvestigationRepository     planInvestigationRepository;
    private final RevisionPlanRepository          revisionPlanRepository;
```

(`PlanInvestigation`, `RevisionPlan`, `PlanInvestigationSubmitRequest`,
`PlanInvestigationRevisionRequest` are already covered by this file's existing
`import ...model.entity.*;` and `import ...model.dto.request.*;` wildcards — no new
import lines needed for any of them.)

- [ ] **Step 2: Add `submitPlan`, `revisePlan`, `validatePlan`, `getPlan`,
      `getPlanRevisions`**

Add these methods right after `getEngagementPrealable` (before
`buildResponseWithFreshMembers`):

```java
    @Override
    @Transactional
    public PlanInvestigationResponse submitPlan(
            UUID investigationId,
            PlanInvestigationSubmitRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (mandatRepository.findByInvestigationId(investigationId).isEmpty()) {
            throw new BusinessException(
                    "Aucun mandat n'a été délivré par le CGE pour cette investigation.");
        }

        if (planInvestigationRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Un plan d'investigation existe déjà pour cette investigation. "
                            + "Utilisez la révision pour le modifier.");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();

        PlanInvestigation plan = PlanInvestigation.builder()
                .investigation(inv)
                .objectifs(request.getObjectifs())
                .methodologie(request.getMethodologie())
                .moyensMobilises(request.getMoyensMobilises())
                .planningProcedures(request.getPlanningProcedures())
                .planVersion(1)
                .submittedAt(Instant.now())
                .submittedBy(currentAgent)
                .build();
        PlanInvestigation saved = planInvestigationRepository.save(plan);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Plan d'investigation soumis par " + currentAgent.getNomComplet(),
                true, currentAgent);

        log.info("Plan d'investigation soumis — investigation: {}", investigationId);
        return toPlanInvestigationResponse(saved);
    }

    @Override
    @Transactional
    public PlanInvestigationResponse revisePlan(
            UUID investigationId,
            PlanInvestigationRevisionRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'investigation n'existe pour cette investigation. "
                                + "Utilisez la soumission initiale."));

        Agent currentAgent = agentContextResolver.getCurrentAgent();

        RevisionPlan revision = RevisionPlan.builder()
                .planInvestigation(plan)
                .versionNumber(plan.getPlanVersion())
                .objectifs(plan.getObjectifs())
                .methodologie(plan.getMethodologie())
                .moyensMobilises(plan.getMoyensMobilises())
                .planningProcedures(plan.getPlanningProcedures())
                .revisedAt(Instant.now())
                .revisedBy(currentAgent)
                .motifRevision(request.getMotifRevision())
                .build();
        revisionPlanRepository.save(revision);

        plan.setObjectifs(request.getObjectifs());
        plan.setMethodologie(request.getMethodologie());
        plan.setMoyensMobilises(request.getMoyensMobilises());
        plan.setPlanningProcedures(request.getPlanningProcedures());
        plan.setPlanVersion(plan.getPlanVersion() + 1);
        plan.setValidatedAt(null);
        plan.setValidatedBy(null);
        PlanInvestigation saved = planInvestigationRepository.save(plan);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Plan d'investigation révisé par " + currentAgent.getNomComplet()
                        + " — motif : " + request.getMotifRevision(),
                true, currentAgent);

        log.info("Plan d'investigation révisé — investigation: {}, nouvelle version: {}",
                investigationId, saved.getPlanVersion());
        return toPlanInvestigationResponse(saved);
    }

    @Override
    @Transactional
    public PlanInvestigationResponse validatePlan(UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'investigation n'existe pour cette investigation."));

        if (plan.getValidatedAt() != null) {
            throw new BusinessException(
                    "Ce plan d'investigation a déjà été validé.");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();
        plan.setValidatedAt(Instant.now());
        plan.setValidatedBy(currentAgent);
        PlanInvestigation saved = planInvestigationRepository.save(plan);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Plan d'investigation validé par le DEI — " + currentAgent.getNomComplet(),
                true, currentAgent);

        log.info("Plan d'investigation validé — investigation: {}", investigationId);
        return toPlanInvestigationResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanInvestigationResponse getPlan(UUID investigationId) {
        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun plan d'investigation pour cette investigation : "
                                + investigationId));
        return toPlanInvestigationResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevisionPlanResponse> getPlanRevisions(UUID investigationId) {
        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun plan d'investigation pour cette investigation : "
                                + investigationId));

        return revisionPlanRepository
                .findByPlanInvestigationIdOrderByVersionNumberDesc(plan.getId())
                .stream()
                .map(this::toRevisionPlanResponse)
                .toList();
    }

```

- [ ] **Step 3: Add the two response-builder helpers**

Add these in the "MÉTHODES PRIVÉES" section, directly before `getInvestigationOrThrow`:

```java
    private PlanInvestigationResponse toPlanInvestigationResponse(PlanInvestigation plan) {
        UUID investigationId = plan.getInvestigation().getId();

        Instant validationDeadline = mandatRepository
                .findByInvestigationId(investigationId)
                .map(Mandat::getDateDelivrance)
                .map(delivrance -> delivrance.plusSeconds(
                        (long) parametreDelaiService.resolveDelaiJours(
                                "VALIDATION_PLAN_INVESTIGATION_DEI") * 24 * 3600))
                .orElse(null);

        boolean overdue = validationDeadline != null
                && plan.getValidatedAt() == null
                && Instant.now().isAfter(validationDeadline);

        return PlanInvestigationResponse.builder()
                .id(plan.getId())
                .investigationId(investigationId)
                .planVersion(plan.getPlanVersion())
                .objectifs(plan.getObjectifs())
                .methodologie(plan.getMethodologie())
                .moyensMobilises(plan.getMoyensMobilises())
                .planningProcedures(plan.getPlanningProcedures())
                .submittedAt(plan.getSubmittedAt())
                .submittedById(plan.getSubmittedBy().getId())
                .submittedByNom(plan.getSubmittedBy().getNomComplet())
                .validatedAt(plan.getValidatedAt())
                .validatedById(plan.getValidatedBy() != null
                        ? plan.getValidatedBy().getId() : null)
                .validatedByNom(plan.getValidatedBy() != null
                        ? plan.getValidatedBy().getNomComplet() : null)
                .validationDeadline(validationDeadline)
                .overdue(overdue)
                .build();
    }

    private RevisionPlanResponse toRevisionPlanResponse(RevisionPlan revision) {
        return RevisionPlanResponse.builder()
                .id(revision.getId())
                .versionNumber(revision.getVersionNumber())
                .objectifs(revision.getObjectifs())
                .methodologie(revision.getMethodologie())
                .moyensMobilises(revision.getMoyensMobilises())
                .planningProcedures(revision.getPlanningProcedures())
                .revisedAt(revision.getRevisedAt())
                .revisedById(revision.getRevisedBy().getId())
                .revisedByNom(revision.getRevisedBy().getNomComplet())
                .motifRevision(revision.getMotifRevision())
                .build();
    }

```

- [ ] **Step 4: Add the 5 new methods to `InvestigationService`**

In `InvestigationService.java`, add these imports next to the existing
`import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;`:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationSubmitRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationRevisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanInvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RevisionPlanResponse;
```

Add this import too (the interface does not currently import `List`):

```java
import java.util.List;
```

Add after `getEngagementPrealable(...)` (before the closing `}`):

```java

    PlanInvestigationResponse submitPlan(
            UUID investigationId,
            PlanInvestigationSubmitRequest request,
            String ipAddress);

    PlanInvestigationResponse revisePlan(
            UUID investigationId,
            PlanInvestigationRevisionRequest request,
            String ipAddress);

    PlanInvestigationResponse validatePlan(UUID investigationId, String ipAddress);

    PlanInvestigationResponse getPlan(UUID investigationId);

    List<RevisionPlanResponse> getPlanRevisions(UUID investigationId);
```

- [ ] **Step 5: Write the tests**

Add to `InvestigationServiceImplTest.java`, after the existing tests and before the
closing `}`:

```java

    @Test
    void submitPlan_rejectsWhenNoMandat() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        PlanInvestigationSubmitRequest request = PlanInvestigationSubmitRequest.builder()
                .objectifs("Établir les faits").methodologie("Auditions et documents")
                .build();

        assertThatThrownBy(() -> service.submitPlan(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mandat");
    }

    @Test
    void submitPlan_rejectsWhenPlanAlreadyExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(PlanInvestigation.builder().id(UUID.randomUUID()).build()));

        PlanInvestigationSubmitRequest request = PlanInvestigationSubmitRequest.builder()
                .objectifs("Établir les faits").methodologie("Auditions et documents")
                .build();

        assertThatThrownBy(() -> service.submitPlan(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void submitPlan_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(planInvestigationRepository.save(any(PlanInvestigation.class)))
                .thenAnswer(inv -> {
                    PlanInvestigation p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        PlanInvestigationSubmitRequest request = PlanInvestigationSubmitRequest.builder()
                .objectifs("Établir les faits").methodologie("Auditions et documents")
                .build();

        PlanInvestigationResponse response =
                service.submitPlan(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getPlanVersion()).isEqualTo(1);
        assertThat(response.getValidatedAt()).isNull();
    }

    @Test
    void revisePlan_rejectsWhenNoPlanExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        PlanInvestigationRevisionRequest request = PlanInvestigationRevisionRequest.builder()
                .objectifs("Établir les faits").methodologie("Auditions et documents")
                .motifRevision("Ajustement du périmètre").build();

        assertThatThrownBy(() -> service.revisePlan(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun plan");
    }

    @Test
    void revisePlan_succeedsAndResetsValidation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        PlanInvestigation existingPlan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .objectifs("Objectifs initiaux")
                .methodologie("Méthodologie initiale")
                .planVersion(1)
                .submittedAt(Instant.now())
                .submittedBy(currentAgent)
                .validatedAt(Instant.now())
                .validatedBy(currentAgent)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(existingPlan));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(planInvestigationRepository.save(any(PlanInvestigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PlanInvestigationRevisionRequest request = PlanInvestigationRevisionRequest.builder()
                .objectifs("Objectifs révisés").methodologie("Méthodologie révisée")
                .motifRevision("Ajustement du périmètre").build();

        PlanInvestigationResponse response =
                service.revisePlan(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getPlanVersion()).isEqualTo(2);
        assertThat(response.getValidatedAt()).isNull();
        assertThat(response.getObjectifs()).isEqualTo("Objectifs révisés");
        verify(revisionPlanRepository).save(argThat(r ->
                r.getVersionNumber() == 1 && r.getObjectifs().equals("Objectifs initiaux")));
    }

    @Test
    void validatePlan_rejectsWhenAlreadyValidated() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        PlanInvestigation existingPlan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .validatedAt(Instant.now())
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(existingPlan));

        assertThatThrownBy(() -> service.validatePlan(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été validé");
    }

    @Test
    void validatePlan_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        PlanInvestigation existingPlan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .submittedBy(currentAgent)
                .validatedAt(null)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(existingPlan));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(planInvestigationRepository.save(any(PlanInvestigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PlanInvestigationResponse response =
                service.validatePlan(investigation.getId(), "127.0.0.1");

        assertThat(response.getValidatedAt()).isNotNull();
        assertThat(response.getValidatedById()).isEqualTo(currentAgent.getId());
    }

    @Test
    void getPlan_computesOverdueFromMandatDate() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(
                Dossier.builder().id(UUID.randomUUID()).build());
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).build();
        PlanInvestigation plan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .submittedBy(currentAgent)
                .validatedAt(null)
                .build();
        Mandat mandat = Mandat.builder()
                .dateDelivrance(Instant.now().minusSeconds(30L * 24 * 3600))
                .build();

        when(planInvestigationRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(plan));
        when(mandatRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(mandat));
        when(parametreDelaiService.resolveDelaiJours("VALIDATION_PLAN_INVESTIGATION_DEI"))
                .thenReturn(8);

        PlanInvestigationResponse response = service.getPlan(investigationId);

        assertThat(response.isOverdue()).isTrue();
        assertThat(response.getValidationDeadline()).isNotNull();
    }

    @Test
    void getPlan_throwsWhenNotFound() {
        UUID investigationId = UUID.randomUUID();

        when(planInvestigationRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlan(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPlanRevisions_returnsOrderedHistory() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(
                Dossier.builder().id(UUID.randomUUID()).build());
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).build();
        PlanInvestigation plan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .build();
        RevisionPlan revision = RevisionPlan.builder()
                .id(UUID.randomUUID())
                .versionNumber(1)
                .objectifs("Objectifs initiaux")
                .methodologie("Méthodologie initiale")
                .revisedAt(Instant.now())
                .revisedBy(currentAgent)
                .motifRevision("Ajustement du périmètre")
                .build();

        when(planInvestigationRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(plan));
        when(revisionPlanRepository.findByPlanInvestigationIdOrderByVersionNumberDesc(
                plan.getId())).thenReturn(List.of(revision));

        List<RevisionPlanResponse> revisions = service.getPlanRevisions(investigationId);

        assertThat(revisions).hasSize(1);
        assertThat(revisions.get(0).getMotifRevision()).isEqualTo("Ajustement du périmètre");
    }
```

Add these imports at the top of `InvestigationServiceImplTest.java` alongside the
existing ones (check before adding a duplicate — some may already be present):

```java
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationSubmitRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationRevisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanInvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RevisionPlanResponse;
import gov.bf.ascelc.univers_audits.model.entity.PlanInvestigation;
import gov.bf.ascelc.univers_audits.model.entity.RevisionPlan;
```

and add the static import for `argThat` if not already present via the existing
`import static org.mockito.Mockito.*;` wildcard (it is — `Mockito.*` covers `argThat`,
no new import line needed).

Add the two mock fields, after
`@Mock private EngagementConfidentialiteRepository engagementConfidentialiteRepository;`:

```java
    @Mock private PlanInvestigationRepository     planInvestigationRepository;
    @Mock private RevisionPlanRepository          revisionPlanRepository;
```

- [ ] **Step 6: Run the tests**

Run: `mvn -q test -Dtest=InvestigationServiceImplTest`
Expected: BUILD SUCCESS, all tests (existing + 10 new) pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: add plan d'investigation submit/revise/validate/read service methods"
```

---

### Task 3: Controller endpoints

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java`

**Interfaces:**
- Consumes: `InvestigationService.submitPlan`/`revisePlan`/`validatePlan`/`getPlan`/
  `getPlanRevisions` (Task 2).
- Produces: `POST /api/v1/investigations/{id}/plan-investigation`,
  `PUT /api/v1/investigations/{id}/plan-investigation`,
  `PATCH /api/v1/investigations/{id}/plan-investigation/valider`,
  `GET /api/v1/investigations/{id}/plan-investigation`,
  `GET /api/v1/investigations/{id}/plan-investigation/revisions` — terminal, nothing
  else in this plan depends on these.

- [ ] **Step 1: Add the imports**

Add alongside the existing `model.dto.response.EngagementConfidentialiteResponse`
import:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationSubmitRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationRevisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanInvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RevisionPlanResponse;
```

Add this import — `java.util.List` is not currently imported in this file:

```java
import java.util.List;
```

- [ ] **Step 2: Add the five endpoints**

Add a new section after the `// ── Engagement préalable ────` block's
`getEngagementPrealable` method, before the closing `}` of the class:

```java

    // ── Plan d'investigation ─────────────────────────────────

    @PostMapping("/{id}/plan-investigation")
    @PreAuthorize("hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<PlanInvestigationResponse> submitPlan(
            @PathVariable UUID id,
            @Valid @RequestBody PlanInvestigationSubmitRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Soumission plan d'investigation — investigation {}", id);
        PlanInvestigationResponse result = investigationService.submitPlan(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "SOUMETTRE_PLAN_INVESTIGATION", "INVESTIGATION", id.toString(),
                "Soumission du plan d'investigation",
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}/plan-investigation")
    @PreAuthorize("hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<PlanInvestigationResponse> revisePlan(
            @PathVariable UUID id,
            @Valid @RequestBody PlanInvestigationRevisionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Révision plan d'investigation — investigation {}", id);
        PlanInvestigationResponse result = investigationService.revisePlan(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "REVISER_PLAN_INVESTIGATION", "INVESTIGATION", id.toString(),
                "Révision du plan d'investigation — motif : " + request.getMotifRevision(),
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/plan-investigation/valider")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<PlanInvestigationResponse> validatePlan(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Validation DEI plan d'investigation — investigation {}", id);
        PlanInvestigationResponse result = investigationService.validatePlan(
                id, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "VALIDER_PLAN_INVESTIGATION", "INVESTIGATION", id.toString(),
                "Validation DEI du plan d'investigation",
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/plan-investigation")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<PlanInvestigationResponse> getPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(investigationService.getPlan(id));
    }

    @GetMapping("/{id}/plan-investigation/revisions")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<List<RevisionPlanResponse>> getPlanRevisions(
            @PathVariable UUID id) {
        return ResponseEntity.ok(investigationService.getPlanRevisions(id));
    }
```

- [ ] **Step 3: Compile and run the full test suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, no regressions anywhere in the suite (the same one
pre-existing environment-only `UniversAuditsApplicationTests.contextLoads` failure,
needing a live datasource, is expected and not yours to fix — if `mvn -q compile`
alone would satisfy an easier check, still run the full `mvn -q test` as instructed
here; do not substitute a partial command and report it as equivalent).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java
git commit -m "feat: add plan d'investigation submit/revise/validate/read endpoints to InvestigationController"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** §1 (`PlanInvestigation`) → Task 1 Step 1. §2 (`RevisionPlan`) →
  Task 1 Step 2. §3 (workflow/API, all 5 endpoints + roles) → Task 2 Step 2, Task 3.
  §4 (configurable delay) → Task 1 Step 9 (seed), Task 2 Step 3
  (`toPlanInvestigationResponse`). Hors périmètre items (structured `PlanningProcedures`
  entity, PDF generation, alert/escalation engine, hard block on overdue validation,
  `start()` gate, real business-day calendar) are correctly absent from every task.
- **Type consistency verified:** `PlanInvestigation`/`RevisionPlan`/repository/DTO
  field names and signatures are identical across Task 1's definitions and Task 2/3's
  usage. `planVersion` (not `version`) used consistently everywhere — the naming-trap
  callout in Global Constraints exists specifically so no task silently reverts to the
  colliding name.
- **The `AuditEntity.version` collision was caught during plan-writing, not left for
  the task reviewer to discover as a compile failure** — this is exactly the kind of
  late-discovered gap this session's discipline exists to prevent; documented here so
  the implementer and reviewer both understand why `planVersion` looks like a slightly
  unusual name choice.
- **GET roles** mirror the existing `GET /{id}/mandat` and `GET
  /{id}/engagement-prealable/{agentId}` roles exactly. **Write roles** are split
  between the plan's author (`CONTROLEUR_ETAT`) and its DEI validator (`CGEA`,
  standing in for the non-existent "DEI" role, exact precedent from `approveDei`) —
  no new role introduced anywhere in this plan.
