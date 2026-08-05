# Procédure d'urgence + mesures conservatoires Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the DEI (via the `CGEA` role, no dedicated DEI role exists in this
codebase) propose an emergency procedure on an investigation, let the `CGE` approve or
reject it, and let field agents record conservatory measures once at least one
emergency procedure has been approved for that investigation — as a tracked event
layer, with zero effect on `Investigation.status` or `Dossier.status`.

**Architecture:** Two new N:1 `Investigation` sub-resources, following the same
established pattern as every other entity in this Lot. `ProcedureUrgence` carries a
2-actor approval workflow (`EN_ATTENTE` → `APPROUVEE`/`REJETEE`, requester ≠ decider).
`MesureConservatoire` has a simple precondition — at least one `ProcedureUrgence` with
`status = APPROUVEE` must exist for the investigation — checked via
`existsByInvestigationIdAndStatus`, no FK link to a specific procedure. Every method
(read AND write) calls `DossierAccessGuard.checkReadAccess(investigation.getDossier())`
first, and the two list-returning read methods additionally apply the confidentiality
filter established at sub-chantier 4/6's final review — this plan applies both
consistently from the start, rather than needing a follow-up fix like that sub-chantier
did.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL),
Lombok `@SuperBuilder`, JUnit 5 + Mockito.

## Global Constraints

- `ProcedureUrgence` fields: `investigation` (FK, not unique — many per investigation
  allowed), `justification` (text, required), `requestedBy` (FK `Agent`, always
  `agentContextResolver.getCurrentAgent()`), `requestedAt` (`Instant`), `status`
  (`StatutProcedureUrgence` enum: `EN_ATTENTE`/`APPROUVEE`/`REJETEE`, default
  `EN_ATTENTE`), `decidedBy`/`decidedAt` (nullable until decided), `motifDecision`
  (text, nullable — optional on approval, **required** on rejection, validated in the
  service layer not via a DTO annotation since the same DTO/field serves both actions).
- `MesureConservatoire` fields: `investigation` (FK, not unique), `description` (text,
  required), `takenBy` (FK `Agent`, always current agent), `takenAt` (`Instant`). No
  FK to a specific `ProcedureUrgence` — only an existence precondition.
- Neither entity has an update or delete method — append-only, matching
  `IncidentObjectivite`'s precedent, except `ProcedureUrgence` which has exactly two
  state-transition actions (`approuver`/`rejeter`) each usable only once
  (`status` must still be `EN_ATTENTE`).
- `demanderProcedureUrgence`/`approuverProcedureUrgence`/`rejeterProcedureUrgence`/
  `declarerMesureConservatoire`/`getProcedures`/`getMesures` — **all six** call
  `accessGuard.checkReadAccess(inv.getDossier())` immediately after
  `getInvestigationOrThrow`, before any other logic. This applies the guard uniformly
  across read AND write, unlike sub-chantier 4/6 which initially only added it and the
  confidentiality filter to the write method and had to add the filter to the read
  method in its own final review — this plan bakes in the full pattern from Task 2
  directly, do not skip the guard on any of the six methods.
- `getProcedures`/`getMesures` (the two list-returning reads) additionally apply:
  `if (Boolean.TRUE.equals(inv.getDossier().getIsConfidential()) &&
  !accessGuard.canSeeConfidential()) { return List.of(); }` — same block, same
  position (after the guard, before the repository query), copied from
  `AuditionServiceImpl.findByInvestigationId` and from `IncidentObjectivite`'s
  corrected `getIncidents`. The other four (single-item or create) methods do NOT get
  this filter — it only applies to list reads.
- `approuverProcedureUrgence`/`rejeterProcedureUrgence` look up the procedure via
  `procedureUrgenceRepository.findByIdAndInvestigationId(procedureId, investigationId)`
  (a NEW repository method, not `findById` alone) — this prevents an agent from acting
  on a `ProcedureUrgence` that belongs to a different investigation by supplying a
  mismatched `{id}`/`{procedureId}` pair in the URL. Both reject with `BusinessException`
  if `status != EN_ATTENTE` (already decided, no re-decision).
- Roles: `demanderProcedureUrgence` → `CGEA, ADMIN_DDIC` (DEI-proxy, mirrors
  `approveDei`/sub-chantier 3/6's plan-validation role). `approuverProcedureUrgence`/
  `rejeterProcedureUrgence` → `CGE, ADMIN_DDIC` (mirrors `deliverMandat`).
  `declarerMesureConservatoire` → `CONTROLEUR_ETAT, CGEA, ADMIN_DDIC` (identical to
  `AuditionController.WRITE_ROLES` — a field action, not a governance decision).
  `getProcedures`/`getMesures` → `CGEA, CGE, CONTROLEUR_ETAT, MEMBRE_CTADP, ADMIN_DDIC`
  (the standard read-role list used throughout this Lot).
- No change to `Investigation.status` or `Dossier.status` anywhere in this plan — the
  emergency-procedure workflow is a tracked event layer only (decision documented in
  the spec, grounded in §6's "transitions exceptionnelles" text treating this the same
  way as the already-existing, status-neutral `extendDeadline`).
- No PDF generation — neither entity appears in §9's generated-documents list.
- `InvestigationServiceImpl` uses `@RequiredArgsConstructor` with a manually-ordered
  field list; append the two new repository fields at the end.
  `InvestigationServiceImplTest` uses `@InjectMocks` only (no manual positional
  constructor), so field order is safe.
- `AuditEntity` columns for both new tables: `id UUID PRIMARY KEY`,
  `version BIGINT NOT NULL DEFAULT 0`, `created_at TIMESTAMP NOT NULL`,
  `updated_at TIMESTAMP`, `created_by_id VARCHAR(100)`, `updated_by_id VARCHAR(100)`.
- Next migration file number is `023` (last is `022-add-incident-objectivite.sql`).
  Both are brand-new tables with no pre-existing Hibernate-bootstrapped history, so
  neither needs the CHECK-constraint drop/recreate dance that migration `019` needed.
- New enum `StatutProcedureUrgence` follows this codebase's existing
  `Statut<Entity>` naming convention (see `StatutSeanceCtadp`), placed in
  `enums/StatutProcedureUrgence.java`.
- Existing wildcard imports already cover most new classes without new import lines:
  `InvestigationServiceImpl.java` has `import ...model.dto.request.*;`,
  `import ...model.entity.*;`, `import ...repository.*;`, `import ...enums.*;` — only
  `ProcedureUrgenceResponse`/`MesureConservatoireResponse` need explicit imports
  (response DTOs are imported individually in this file). `InvestigationService.java`
  and `InvestigationController.java` import both request and response DTOs explicitly
  (no wildcards) — both need explicit imports for all 4 new DTOs.

---

### Task 1: `StatutProcedureUrgence` enum, `ProcedureUrgence`/`MesureConservatoire` entities, repositories, DTOs, migration 023

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/StatutProcedureUrgence.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/ProcedureUrgence.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/MesureConservatoire.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/ProcedureUrgenceRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/MesureConservatoireRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ProcedureUrgenceRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ProcedureUrgenceDecisionRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/MesureConservatoireRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/ProcedureUrgenceResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MesureConservatoireResponse.java`
- Create: `src/main/resources/db/changelog/migrations/023-add-procedure-urgence-mesure-conservatoire.sql`

**Interfaces:**
- Consumes: `Investigation`, `Agent` entities (`Agent` has `getNomComplet()`),
  `AuditEntity` base class.
- Produces: `StatutProcedureUrgence` enum, `ProcedureUrgence`/`MesureConservatoire`
  entities (fields per Global Constraints) — consumed by Task 2.
- Produces: `ProcedureUrgenceRepository.findByInvestigationIdOrderByRequestedAtDesc(UUID):
  List<ProcedureUrgence>`, `.findByIdAndInvestigationId(UUID, UUID):
  Optional<ProcedureUrgence>`, `.existsByInvestigationIdAndStatus(UUID,
  StatutProcedureUrgence): boolean`; `MesureConservatoireRepository
  .findByInvestigationIdOrderByTakenAtDesc(UUID): List<MesureConservatoire>` —
  consumed by Task 2.
- Produces: `ProcedureUrgenceRequest` (`justification` `@NotBlank`),
  `ProcedureUrgenceDecisionRequest` (`motifDecision`, no annotation — conditionally
  required, validated in service), `MesureConservatoireRequest` (`description`
  `@NotBlank`), `ProcedureUrgenceResponse` (`id, investigationId, justification,
  requestedById, requestedByNom, requestedAt, status, decidedById, decidedByNom,
  decidedAt, motifDecision`), `MesureConservatoireResponse` (`id, investigationId,
  description, takenById, takenByNom, takenAt`) — consumed by Task 2 and Task 3.

- [ ] **Step 1: Create the `StatutProcedureUrgence` enum**

```java
package gov.bf.ascelc.univers_audits.enums;

public enum StatutProcedureUrgence {
    EN_ATTENTE,
    APPROUVEE,
    REJETEE
}
```

- [ ] **Step 2: Create the `ProcedureUrgence` entity**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.StatutProcedureUrgence;
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
@Table(name = "procedure_urgence", indexes = {
        @Index(name = "idx_procedure_urgence_investigation",
                columnList = "investigation_id")
})
public class ProcedureUrgence extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Agent requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StatutProcedureUrgence status = StatutProcedureUrgence.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private Agent decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "motif_decision", columnDefinition = "TEXT")
    private String motifDecision;
}
```

- [ ] **Step 3: Create the `MesureConservatoire` entity**

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
@Table(name = "mesure_conservatoire", indexes = {
        @Index(name = "idx_mesure_conservatoire_investigation",
                columnList = "investigation_id")
})
public class MesureConservatoire extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taken_by_id", nullable = false)
    private Agent takenBy;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;
}
```

- [ ] **Step 4: Create `ProcedureUrgenceRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.StatutProcedureUrgence;
import gov.bf.ascelc.univers_audits.model.entity.ProcedureUrgence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcedureUrgenceRepository extends JpaRepository<ProcedureUrgence, UUID> {

    List<ProcedureUrgence> findByInvestigationIdOrderByRequestedAtDesc(UUID investigationId);

    Optional<ProcedureUrgence> findByIdAndInvestigationId(UUID id, UUID investigationId);

    boolean existsByInvestigationIdAndStatus(UUID investigationId, StatutProcedureUrgence status);
}
```

- [ ] **Step 5: Create `MesureConservatoireRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.MesureConservatoire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MesureConservatoireRepository extends JpaRepository<MesureConservatoire, UUID> {

    List<MesureConservatoire> findByInvestigationIdOrderByTakenAtDesc(UUID investigationId);
}
```

- [ ] **Step 6: Create `ProcedureUrgenceRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcedureUrgenceRequest {

    @NotBlank(message = "La justification de la procédure d'urgence est obligatoire")
    private String justification;
}
```

- [ ] **Step 7: Create `ProcedureUrgenceDecisionRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcedureUrgenceDecisionRequest {

    private String motifDecision;
}
```

- [ ] **Step 8: Create `MesureConservatoireRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesureConservatoireRequest {

    @NotBlank(message = "La description de la mesure conservatoire est obligatoire")
    private String description;
}
```

- [ ] **Step 9: Create `ProcedureUrgenceResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.StatutProcedureUrgence;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcedureUrgenceResponse {

    private UUID id;
    private UUID investigationId;
    private String justification;
    private UUID requestedById;
    private String requestedByNom;
    private Instant requestedAt;
    private StatutProcedureUrgence status;
    private UUID decidedById;
    private String decidedByNom;
    private Instant decidedAt;
    private String motifDecision;
}
```

- [ ] **Step 10: Create `MesureConservatoireResponse`**

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
public class MesureConservatoireResponse {

    private UUID id;
    private UUID investigationId;
    private String description;
    private UUID takenById;
    private String takenByNom;
    private Instant takenAt;
}
```

- [ ] **Step 11: Write migration 023**

Create `src/main/resources/db/changelog/migrations/023-add-procedure-urgence-mesure-conservatoire.sql`:

```sql
--liquibase formatted sql
--changeset dev:023-add-procedure-urgence-mesure-conservatoire

CREATE TABLE procedure_urgence (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    investigation_id  UUID NOT NULL REFERENCES investigation(id),
    justification     TEXT NOT NULL,
    requested_by_id   UUID NOT NULL REFERENCES agent(id),
    requested_at      TIMESTAMP NOT NULL,
    status            VARCHAR(20) NOT NULL,
    decided_by_id     UUID REFERENCES agent(id),
    decided_at        TIMESTAMP,
    motif_decision    TEXT
);

CREATE INDEX idx_procedure_urgence_investigation
    ON procedure_urgence (investigation_id);

CREATE TABLE mesure_conservatoire (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    investigation_id  UUID NOT NULL REFERENCES investigation(id),
    description       TEXT NOT NULL,
    taken_by_id       UUID NOT NULL REFERENCES agent(id),
    taken_at          TIMESTAMP NOT NULL
);

CREATE INDEX idx_mesure_conservatoire_investigation
    ON mesure_conservatoire (investigation_id);

COMMENT ON TABLE procedure_urgence IS 'Demande DEI (=CGEA) + decision CGE de declenchement d une procedure d urgence sur une investigation - evenement trace, aucun effet sur le statut de l investigation ou du dossier (Lot 3, plan de travail S5/S6/S11)';
COMMENT ON TABLE mesure_conservatoire IS 'Mesure conservatoire concrete prise dans le cadre d une procedure d urgence approuvee (Lot 3, plan de travail S5/S11)';
```

This migration is auto-discovered by `db.changelog-master.yaml`'s `includeAll` on
`db/changelog/migrations/` — no changelog master edit needed. Neither table has a
unique constraint, so the plain indexes are not redundant.

- [ ] **Step 12: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (new classes only, nothing consumes them yet).

- [ ] **Step 13: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/StatutProcedureUrgence.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/ProcedureUrgence.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/MesureConservatoire.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/ProcedureUrgenceRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/MesureConservatoireRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ProcedureUrgenceRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ProcedureUrgenceDecisionRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/MesureConservatoireRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/ProcedureUrgenceResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MesureConservatoireResponse.java \
        src/main/resources/db/changelog/migrations/023-add-procedure-urgence-mesure-conservatoire.sql
git commit -m "feat: add ProcedureUrgence and MesureConservatoire entities, repositories, DTOs and migration"
```

---

### Task 2: Service layer — demander/approuver/rejeter, declarer mesure, reads, tests

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `ProcedureUrgence`/`MesureConservatoire`/repositories/DTOs (Task 1),
  `DossierAccessGuard` (existing field `accessGuard`, already injected by
  sub-chantier 4/6).
- Produces: `InvestigationService.demanderProcedureUrgence(UUID,
  ProcedureUrgenceRequest, String): ProcedureUrgenceResponse`,
  `.approuverProcedureUrgence(UUID, UUID, ProcedureUrgenceDecisionRequest, String):
  ProcedureUrgenceResponse`, `.rejeterProcedureUrgence(UUID, UUID,
  ProcedureUrgenceDecisionRequest, String): ProcedureUrgenceResponse`,
  `.getProcedures(UUID): List<ProcedureUrgenceResponse>`,
  `.declarerMesureConservatoire(UUID, MesureConservatoireRequest, String):
  MesureConservatoireResponse`, `.getMesures(UUID): List<MesureConservatoireResponse>`
  — consumed by Task 3's controller.

- [ ] **Step 1: Add imports and fields to `InvestigationServiceImpl`**

Add these imports next to the existing
`import gov.bf.ascelc.univers_audits.model.dto.response.IncidentObjectiviteResponse;`:

```java
import gov.bf.ascelc.univers_audits.model.dto.response.ProcedureUrgenceResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MesureConservatoireResponse;
```

Add these fields at the end of the existing field list (after
`private final DossierAccessGuard accessGuard;`):

```java
    private final ProcedureUrgenceRepository      procedureUrgenceRepository;
    private final MesureConservatoireRepository   mesureConservatoireRepository;
```

(`ProcedureUrgence`, `MesureConservatoire`, `StatutProcedureUrgence`,
`ProcedureUrgenceRequest`, `ProcedureUrgenceDecisionRequest`,
`MesureConservatoireRequest` are already covered by this file's existing
`import ...model.entity.*;`, `import ...enums.*;`, and `import ...model.dto.request.*;`
wildcards — no new import lines needed for any of them.)

- [ ] **Step 2: Add the six service methods**

Add these methods right after `getIncidents` (before `buildResponseWithFreshMembers`):

```java
    @Override
    @Transactional
    public ProcedureUrgenceResponse demanderProcedureUrgence(
            UUID investigationId,
            ProcedureUrgenceRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(inv.getDossier());

        Agent currentAgent = agentContextResolver.getCurrentAgent();

        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .investigation(inv)
                .justification(request.getJustification())
                .requestedBy(currentAgent)
                .requestedAt(Instant.now())
                .status(StatutProcedureUrgence.EN_ATTENTE)
                .build();
        ProcedureUrgence saved = procedureUrgenceRepository.save(procedure);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Procédure d'urgence demandée par " + currentAgent.getNomComplet(),
                true, currentAgent);

        log.info("Procédure d'urgence demandée — investigation: {}", investigationId);
        return toProcedureUrgenceResponse(saved);
    }

    @Override
    @Transactional
    public ProcedureUrgenceResponse approuverProcedureUrgence(
            UUID investigationId,
            UUID procedureId,
            ProcedureUrgenceDecisionRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(inv.getDossier());
        ProcedureUrgence procedure = getProcedureOrThrow(investigationId, procedureId);

        if (procedure.getStatus() != StatutProcedureUrgence.EN_ATTENTE) {
            throw new BusinessException(
                    "Cette procédure d'urgence a déjà été décidée.");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();
        procedure.setStatus(StatutProcedureUrgence.APPROUVEE);
        procedure.setDecidedBy(currentAgent);
        procedure.setDecidedAt(Instant.now());
        procedure.setMotifDecision(request.getMotifDecision());
        ProcedureUrgence saved = procedureUrgenceRepository.save(procedure);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Procédure d'urgence approuvée par le CGE — " + currentAgent.getNomComplet(),
                true, currentAgent);

        log.info("Procédure d'urgence approuvée — investigation: {}, procedure: {}",
                investigationId, procedureId);
        return toProcedureUrgenceResponse(saved);
    }

    @Override
    @Transactional
    public ProcedureUrgenceResponse rejeterProcedureUrgence(
            UUID investigationId,
            UUID procedureId,
            ProcedureUrgenceDecisionRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(inv.getDossier());
        ProcedureUrgence procedure = getProcedureOrThrow(investigationId, procedureId);

        if (procedure.getStatus() != StatutProcedureUrgence.EN_ATTENTE) {
            throw new BusinessException(
                    "Cette procédure d'urgence a déjà été décidée.");
        }

        if (request.getMotifDecision() == null || request.getMotifDecision().isBlank()) {
            throw new BusinessException(
                    "Le motif du rejet est obligatoire.");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();
        procedure.setStatus(StatutProcedureUrgence.REJETEE);
        procedure.setDecidedBy(currentAgent);
        procedure.setDecidedAt(Instant.now());
        procedure.setMotifDecision(request.getMotifDecision());
        ProcedureUrgence saved = procedureUrgenceRepository.save(procedure);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Procédure d'urgence rejetée par le CGE — " + currentAgent.getNomComplet()
                        + " — motif : " + request.getMotifDecision(),
                true, currentAgent);

        log.info("Procédure d'urgence rejetée — investigation: {}, procedure: {}",
                investigationId, procedureId);
        return toProcedureUrgenceResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcedureUrgenceResponse> getProcedures(UUID investigationId) {
        Investigation inv = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(inv.getDossier());

        if (Boolean.TRUE.equals(inv.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return procedureUrgenceRepository
                .findByInvestigationIdOrderByRequestedAtDesc(investigationId)
                .stream()
                .map(this::toProcedureUrgenceResponse)
                .toList();
    }

    @Override
    @Transactional
    public MesureConservatoireResponse declarerMesureConservatoire(
            UUID investigationId,
            MesureConservatoireRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(inv.getDossier());

        if (!procedureUrgenceRepository.existsByInvestigationIdAndStatus(
                investigationId, StatutProcedureUrgence.APPROUVEE)) {
            throw new BusinessException(
                    "Aucune procédure d'urgence approuvée n'existe pour cette investigation.");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();

        MesureConservatoire mesure = MesureConservatoire.builder()
                .investigation(inv)
                .description(request.getDescription())
                .takenBy(currentAgent)
                .takenAt(Instant.now())
                .build();
        MesureConservatoire saved = mesureConservatoireRepository.save(mesure);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Mesure conservatoire prise par " + currentAgent.getNomComplet(),
                true, currentAgent);

        log.info("Mesure conservatoire déclarée — investigation: {}", investigationId);
        return toMesureConservatoireResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MesureConservatoireResponse> getMesures(UUID investigationId) {
        Investigation inv = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(inv.getDossier());

        if (Boolean.TRUE.equals(inv.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return mesureConservatoireRepository
                .findByInvestigationIdOrderByTakenAtDesc(investigationId)
                .stream()
                .map(this::toMesureConservatoireResponse)
                .toList();
    }

```

- [ ] **Step 3: Add the private helpers**

Add these in the "MÉTHODES PRIVÉES" section, directly before `getInvestigationOrThrow`:

```java
    private ProcedureUrgence getProcedureOrThrow(UUID investigationId, UUID procedureId) {
        return procedureUrgenceRepository
                .findByIdAndInvestigationId(procedureId, investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procédure d'urgence introuvable : " + procedureId));
    }

    private ProcedureUrgenceResponse toProcedureUrgenceResponse(ProcedureUrgence procedure) {
        return ProcedureUrgenceResponse.builder()
                .id(procedure.getId())
                .investigationId(procedure.getInvestigation().getId())
                .justification(procedure.getJustification())
                .requestedById(procedure.getRequestedBy().getId())
                .requestedByNom(procedure.getRequestedBy().getNomComplet())
                .requestedAt(procedure.getRequestedAt())
                .status(procedure.getStatus())
                .decidedById(procedure.getDecidedBy() != null
                        ? procedure.getDecidedBy().getId() : null)
                .decidedByNom(procedure.getDecidedBy() != null
                        ? procedure.getDecidedBy().getNomComplet() : null)
                .decidedAt(procedure.getDecidedAt())
                .motifDecision(procedure.getMotifDecision())
                .build();
    }

    private MesureConservatoireResponse toMesureConservatoireResponse(
            MesureConservatoire mesure) {
        return MesureConservatoireResponse.builder()
                .id(mesure.getId())
                .investigationId(mesure.getInvestigation().getId())
                .description(mesure.getDescription())
                .takenById(mesure.getTakenBy().getId())
                .takenByNom(mesure.getTakenBy().getNomComplet())
                .takenAt(mesure.getTakenAt())
                .build();
    }

```

- [ ] **Step 4: Add the six methods to `InvestigationService`**

In `InvestigationService.java`, add these imports next to the existing
`import gov.bf.ascelc.univers_audits.model.dto.response.IncidentObjectiviteResponse;`:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceDecisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.MesureConservatoireRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ProcedureUrgenceResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MesureConservatoireResponse;
```

Add after `getIncidents(...)` (before the closing `}`):

```java

    ProcedureUrgenceResponse demanderProcedureUrgence(
            UUID investigationId,
            ProcedureUrgenceRequest request,
            String ipAddress);

    ProcedureUrgenceResponse approuverProcedureUrgence(
            UUID investigationId,
            UUID procedureId,
            ProcedureUrgenceDecisionRequest request,
            String ipAddress);

    ProcedureUrgenceResponse rejeterProcedureUrgence(
            UUID investigationId,
            UUID procedureId,
            ProcedureUrgenceDecisionRequest request,
            String ipAddress);

    List<ProcedureUrgenceResponse> getProcedures(UUID investigationId);

    MesureConservatoireResponse declarerMesureConservatoire(
            UUID investigationId,
            MesureConservatoireRequest request,
            String ipAddress);

    List<MesureConservatoireResponse> getMesures(UUID investigationId);
```

- [ ] **Step 5: Write the tests**

Add to `InvestigationServiceImplTest.java`, after the existing tests and before the
closing `}`. These tests reuse the existing `Dossier`/`Investigation`/`Agent` builder
patterns already used throughout this file:

```java

    @Test
    void demanderProcedureUrgence_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(procedureUrgenceRepository.save(any(ProcedureUrgence.class)))
                .thenAnswer(inv -> {
                    ProcedureUrgence p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        ProcedureUrgenceRequest request = ProcedureUrgenceRequest.builder()
                .justification("Risque de destruction de preuves").build();

        ProcedureUrgenceResponse response =
                service.demanderProcedureUrgence(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo(StatutProcedureUrgence.EN_ATTENTE);
        assertThat(response.getRequestedById()).isEqualTo(currentAgent.getId());
        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void approuverProcedureUrgence_rejectsWhenAlreadyDecided() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .status(StatutProcedureUrgence.APPROUVEE)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByIdAndInvestigationId(
                procedure.getId(), investigation.getId()))
                .thenReturn(Optional.of(procedure));

        ProcedureUrgenceDecisionRequest request = ProcedureUrgenceDecisionRequest.builder().build();

        assertThatThrownBy(() -> service.approuverProcedureUrgence(
                investigation.getId(), procedure.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été décidée");
    }

    @Test
    void approuverProcedureUrgence_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(StatutProcedureUrgence.EN_ATTENTE)
                .requestedBy(currentAgent)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByIdAndInvestigationId(
                procedure.getId(), investigation.getId()))
                .thenReturn(Optional.of(procedure));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(procedureUrgenceRepository.save(any(ProcedureUrgence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProcedureUrgenceDecisionRequest request = ProcedureUrgenceDecisionRequest.builder().build();

        ProcedureUrgenceResponse response = service.approuverProcedureUrgence(
                investigation.getId(), procedure.getId(), request, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo(StatutProcedureUrgence.APPROUVEE);
        assertThat(response.getDecidedById()).isEqualTo(currentAgent.getId());
    }

    @Test
    void rejeterProcedureUrgence_rejectsWhenMotifMissing() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .status(StatutProcedureUrgence.EN_ATTENTE)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByIdAndInvestigationId(
                procedure.getId(), investigation.getId()))
                .thenReturn(Optional.of(procedure));

        ProcedureUrgenceDecisionRequest request = ProcedureUrgenceDecisionRequest.builder().build();

        assertThatThrownBy(() -> service.rejeterProcedureUrgence(
                investigation.getId(), procedure.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motif du rejet");
    }

    @Test
    void rejeterProcedureUrgence_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(StatutProcedureUrgence.EN_ATTENTE)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByIdAndInvestigationId(
                procedure.getId(), investigation.getId()))
                .thenReturn(Optional.of(procedure));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(procedureUrgenceRepository.save(any(ProcedureUrgence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProcedureUrgenceDecisionRequest request = ProcedureUrgenceDecisionRequest.builder()
                .motifDecision("Situation déjà maîtrisée par les moyens existants").build();

        ProcedureUrgenceResponse response = service.rejeterProcedureUrgence(
                investigation.getId(), procedure.getId(), request, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo(StatutProcedureUrgence.REJETEE);
        assertThat(response.getMotifDecision())
                .isEqualTo("Situation déjà maîtrisée par les moyens existants");
    }

    @Test
    void declarerMesureConservatoire_rejectsWhenNoApprovedProcedure() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.existsByInvestigationIdAndStatus(
                investigation.getId(), StatutProcedureUrgence.APPROUVEE)).thenReturn(false);

        MesureConservatoireRequest request = MesureConservatoireRequest.builder()
                .description("Mise sous scellés du serveur").build();

        assertThatThrownBy(() -> service.declarerMesureConservatoire(
                investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucune procédure d'urgence approuvée");
    }

    @Test
    void declarerMesureConservatoire_succeedsWhenApprovedProcedureExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.existsByInvestigationIdAndStatus(
                investigation.getId(), StatutProcedureUrgence.APPROUVEE)).thenReturn(true);
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(mesureConservatoireRepository.save(any(MesureConservatoire.class)))
                .thenAnswer(inv -> {
                    MesureConservatoire m = inv.getArgument(0);
                    m.setId(UUID.randomUUID());
                    return m;
                });

        MesureConservatoireRequest request = MesureConservatoireRequest.builder()
                .description("Mise sous scellés du serveur").build();

        MesureConservatoireResponse response = service.declarerMesureConservatoire(
                investigation.getId(), request, "127.0.0.1");

        assertThat(response.getTakenById()).isEqualTo(currentAgent.getId());
        assertThat(response.getDescription()).isEqualTo("Mise sous scellés du serveur");
    }

    @Test
    void getProcedures_returnsOrderedList() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .requestedBy(Agent.builder().id(UUID.randomUUID()).build())
                .status(StatutProcedureUrgence.EN_ATTENTE)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByInvestigationIdOrderByRequestedAtDesc(
                investigation.getId())).thenReturn(List.of(procedure));

        List<ProcedureUrgenceResponse> procedures = service.getProcedures(investigation.getId());

        assertThat(procedures).hasSize(1);
        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void getMesures_returnsOrderedList() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        MesureConservatoire mesure = MesureConservatoire.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .takenBy(Agent.builder().id(UUID.randomUUID()).build())
                .description("Mise sous scellés")
                .takenAt(Instant.now())
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mesureConservatoireRepository.findByInvestigationIdOrderByTakenAtDesc(
                investigation.getId())).thenReturn(List.of(mesure));

        List<MesureConservatoireResponse> mesures = service.getMesures(investigation.getId());

        assertThat(mesures).hasSize(1);
        verify(accessGuard).checkReadAccess(dossier);
    }
```

Add these imports at the top of `InvestigationServiceImplTest.java` alongside the
existing ones (check before adding a duplicate):

```java
import gov.bf.ascelc.univers_audits.enums.StatutProcedureUrgence;
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceDecisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.MesureConservatoireRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ProcedureUrgenceResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MesureConservatoireResponse;
import gov.bf.ascelc.univers_audits.model.entity.ProcedureUrgence;
import gov.bf.ascelc.univers_audits.model.entity.MesureConservatoire;
import gov.bf.ascelc.univers_audits.repository.ProcedureUrgenceRepository;
import gov.bf.ascelc.univers_audits.repository.MesureConservatoireRepository;
```

Add the mock fields, after `@Mock private DossierAccessGuard accessGuard;`:

```java
    @Mock private ProcedureUrgenceRepository      procedureUrgenceRepository;
    @Mock private MesureConservatoireRepository   mesureConservatoireRepository;
```

- [ ] **Step 6: Run the tests**

Run: `mvn -q test -Dtest=InvestigationServiceImplTest`
Expected: BUILD SUCCESS, all tests (existing + 8 new) pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: add procedure d'urgence request/decide and mesure conservatoire declaration"
```

---

### Task 3: Controller endpoints

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java`

**Interfaces:**
- Consumes: `InvestigationService.demanderProcedureUrgence`/`approuverProcedureUrgence`/
  `rejeterProcedureUrgence`/`getProcedures`/`declarerMesureConservatoire`/`getMesures`
  (Task 2).
- Produces: `POST /api/v1/investigations/{id}/procedures-urgence`,
  `PATCH /api/v1/investigations/{id}/procedures-urgence/{procedureId}/approuver`,
  `PATCH /api/v1/investigations/{id}/procedures-urgence/{procedureId}/rejeter`,
  `GET /api/v1/investigations/{id}/procedures-urgence`,
  `POST /api/v1/investigations/{id}/mesures-conservatoires`,
  `GET /api/v1/investigations/{id}/mesures-conservatoires` — terminal, nothing else
  in this plan depends on these.

- [ ] **Step 1: Add the imports**

Add alongside the existing `model.dto.response.IncidentObjectiviteResponse` import:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceDecisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.MesureConservatoireRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ProcedureUrgenceResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MesureConservatoireResponse;
```

- [ ] **Step 2: Add the six endpoints**

Add a new section after the `// ── Incident d'objectivité ────` block's `getIncidents`
method, before the closing `}` of the class:

```java

    // ── Procédure d'urgence ───────────────────────────────────

    @PostMapping("/{id}/procedures-urgence")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<ProcedureUrgenceResponse> demanderProcedureUrgence(
            @PathVariable UUID id,
            @Valid @RequestBody ProcedureUrgenceRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Demande procédure d'urgence — investigation {}", id);
        ProcedureUrgenceResponse result = investigationService.demanderProcedureUrgence(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "DEMANDER_PROCEDURE_URGENCE", "INVESTIGATION", id.toString(),
                "Demande de procédure d'urgence",
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PatchMapping("/{id}/procedures-urgence/{procedureId}/approuver")
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<ProcedureUrgenceResponse> approuverProcedureUrgence(
            @PathVariable UUID id,
            @PathVariable UUID procedureId,
            @RequestBody ProcedureUrgenceDecisionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Approbation procédure d'urgence — investigation {}, procédure {}",
                id, procedureId);
        ProcedureUrgenceResponse result = investigationService.approuverProcedureUrgence(
                id, procedureId, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "APPROUVER_PROCEDURE_URGENCE", "INVESTIGATION", id.toString(),
                "Approbation de la procédure d'urgence",
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/procedures-urgence/{procedureId}/rejeter")
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<ProcedureUrgenceResponse> rejeterProcedureUrgence(
            @PathVariable UUID id,
            @PathVariable UUID procedureId,
            @RequestBody ProcedureUrgenceDecisionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Rejet procédure d'urgence — investigation {}, procédure {}",
                id, procedureId);
        ProcedureUrgenceResponse result = investigationService.rejeterProcedureUrgence(
                id, procedureId, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "REJETER_PROCEDURE_URGENCE", "INVESTIGATION", id.toString(),
                "Rejet de la procédure d'urgence",
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/procedures-urgence")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<List<ProcedureUrgenceResponse>> getProcedures(
            @PathVariable UUID id) {
        return ResponseEntity.ok(investigationService.getProcedures(id));
    }

    // ── Mesures conservatoires ────────────────────────────────

    @PostMapping("/{id}/mesures-conservatoires")
    @PreAuthorize("hasAnyRole('CONTROLEUR_ETAT','CGEA','ADMIN_DDIC')")
    public ResponseEntity<MesureConservatoireResponse> declarerMesureConservatoire(
            @PathVariable UUID id,
            @Valid @RequestBody MesureConservatoireRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Déclaration mesure conservatoire — investigation {}", id);
        MesureConservatoireResponse result = investigationService.declarerMesureConservatoire(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "DECLARER_MESURE_CONSERVATOIRE", "INVESTIGATION", id.toString(),
                "Déclaration d'une mesure conservatoire",
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/mesures-conservatoires")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<List<MesureConservatoireResponse>> getMesures(
            @PathVariable UUID id) {
        return ResponseEntity.ok(investigationService.getMesures(id));
    }
```

- [ ] **Step 3: Compile and run the full test suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, no regressions anywhere in the suite (the same one
pre-existing environment-only `UniversAuditsApplicationTests.contextLoads` failure,
needing a live datasource, is expected and not yours to fix). You MUST actually run
the full `mvn -q test` command and report the real "Tests run: N, Failures: X, Errors:
Y" summary line — do not substitute `mvn -q compile` and claim equivalence.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java
git commit -m "feat: add procedure d'urgence and mesure conservatoire endpoints to InvestigationController"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** §1 (`ProcedureUrgence`) → Task 1 Steps 1-2. §2 (`MesureConservatoire`)
  → Task 1 Step 3. §3 (API, all 6 endpoints + roles + preconditions) → Task 2 Step 2,
  Task 3. Hors périmètre items (status changes, team-replacement, deadline extension,
  FK link between the two entities, PDF, `open()`/`start()` insertion) are correctly
  absent from every task.
- **Type consistency verified:** `ProcedureUrgence`/`MesureConservatoire`/repository/
  DTO field names and signatures are identical across Task 1's definitions and Task
  2/3's usage. `StatutProcedureUrgence` enum values used consistently.
- **The uniform `accessGuard.checkReadAccess` + confidentiality-filter-on-lists
  pattern is applied from the start in Task 2**, rather than being discovered as a gap
  at final review the way sub-chantier 4/6's `getIncidents` was — this plan explicitly
  calls out in Global Constraints that all six methods need the guard and both list
  methods need the filter, so neither the implementer nor reviewer should need to
  rediscover this.
- **`findByIdAndInvestigationId` (not `findById` alone) prevents a cross-investigation
  ID-mismatch bug** in `approuverProcedureUrgence`/`rejeterProcedureUrgence` — this
  wasn't needed by prior sub-chantiers in this Lot since none of them had a
  investigation-scoped sub-resource ALSO addressed by its own separate ID in the URL
  path (`{id}/{procedureId}`), a new shape introduced by this sub-chantier's 2-actor
  approval workflow.
