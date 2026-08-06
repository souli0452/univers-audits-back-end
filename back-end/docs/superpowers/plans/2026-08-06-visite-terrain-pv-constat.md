# VisiteTerrain + PVConstat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `VisiteTerrain`/`PVConstat` module for planning and conducting field
visits and recording their findings (including failed attempts — "constat de
carence"), structurally identical to the existing `Audition`/`PVAudition` module.

**Architecture:** Two new entities as top-level `Investigation` sub-resources (not
methods bolted onto `InvestigationServiceImpl` — this Lot's established precedent for
a full new module is its own dedicated service pair + one combined controller,
exactly matching how `Audition`/`PVAudition` is already built). `VisiteTerrain`
carries a `SCHEDULED → {CONDUCTED | CANCELLED | CARENCE}` lifecycle (three distinct
terminal transitions from `SCHEDULED`, each with its own reason field).
`PVConstat` is a simple 1:1 findings record with no signature/finalization step (no
"personne auditionnée" exists for a site visit).

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, MapStruct 1.5.5.Final,
Liquibase (formatted SQL), Lombok `@SuperBuilder`, JUnit 5 + Mockito.

## Global Constraints

- New enum `VisiteStatus`: `SCHEDULED`, `CONDUCTED`, `CANCELLED`, `CARENCE` (4 values,
  no `NO_SHOW` — this module uses `CARENCE` as its own domain term, matching the plan
  de travail's exact vocabulary, unlike `AuditionStatus.NO_SHOW` which is a different,
  already-existing, still-unreachable value in a different module — not touched by
  this plan).
- `VisiteTerrain.location` is **`nullable = false`** (unlike `Audition.location`,
  which is optional) — a field visit is inherently about a specific site, so this
  field is required here even though the closest precedent leaves its analogous field
  optional. This is a deliberate, justified deviation, not an oversight.
- Three distinct entity methods for the three terminal transitions from `SCHEDULED`:
  `conduct(summary)`, `cancel(reason)`, `markCarence(reason)` — each sets its own
  reason field (`summary`, `cancellationReason`, `carenceReason` respectively) and its
  own `VisiteStatus`. Do not reuse one reason field for two transitions.
- `cancel` and `markCarence` both take their reason via `@RequestParam String reason`
  at the controller layer (no request-body DTO) — matching `AuditionController.cancel`'s
  existing pattern exactly, not inventing a new DTO for a single-string action.
- `PvConstatServiceImpl.create()` does **not** require `VisiteTerrain.status` to be
  `CONDUCTED` or `CARENCE` before allowing PV creation — it only checks the visit
  exists and no PV already exists for it. This deliberately mirrors
  `PvAuditionServiceImpl.create()`'s actual behavior (which also has no status
  precondition), for consistency with the real precedent rather than inventing a
  stricter rule than what's already shipped for the sibling module.
- Both new response DTOs (`VisiteTerrainResponse`, `PvConstatResponse`) map via
  simple, direct `@Mapping(source = ...)` annotations on `DossierDetailsMapper` — no
  calculated/derived field requiring the `@AfterMapping`-avoidance wrapper pattern
  this codebase uses elsewhere (e.g. `Audition.intervieweeDisplayName`). Confirmed:
  every field on both new response DTOs maps directly from an entity field or a
  simple nested property path (`conductedBy.nomComplet`, `draftedBy.nomComplet`,
  `investigation.id`, `visiteTerrain.id`) — exactly the same shape as the already-working
  `PvAuditionResponse` mapping, which also needs no wrapper.
- Roles: same `READ_ROLES`/`WRITE_ROLES` constants and values as `AuditionController`
  (`READ_ROLES = CGEA,CGE,CONTROLEUR_ETAT,MEMBRE_CTADP,CONSEILLER_JURIDIQUE,ADMIN_DDIC`;
  `WRITE_ROLES = CONTROLEUR_ETAT,CGEA,ADMIN_DDIC`) — no new role introduced.
- `findByInvestigationId`/`findByVisiteId` apply the same two-layer access pattern
  already established across this session (`accessGuard.checkReadAccess` first, then
  the `isConfidential && !canSeeConfidential()` filter on the list-returning method
  only — `findByVisiteId`, a single-item read, throws `BusinessException` on
  confidentiality denial instead of returning an empty result, exactly matching
  `PvAuditionServiceImpl.findByAuditionId`'s existing behavior for the same shape of
  method).
- No PDF generation, no index-of-pieces linkage, no "at least two investigators"
  constraint on `VisiteTerrain` — all explicitly out of scope per the spec.
- Next migration file number is `025` (last is
  `024-add-demande-documents-saisine-judiciaire-delai.sql`). Both new tables are
  brand-new with no pre-existing Hibernate-bootstrapped history, so neither needs the
  CHECK-constraint drop/recreate dance `019` needed for a different, legacy table.
- `AuditEntity` columns for both new tables: `id UUID PRIMARY KEY`,
  `version BIGINT NOT NULL DEFAULT 0`, `created_at TIMESTAMP NOT NULL`,
  `updated_at TIMESTAMP`, `created_by_id VARCHAR(100)`, `updated_by_id VARCHAR(100)`.

---

### Task 1: `VisiteStatus` enum, `VisiteTerrain`/`PVConstat` entities, repositories, DTOs, migration 025

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/VisiteStatus.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/VisiteTerrain.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/PVConstat.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/VisiteTerrainRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/PVConstatRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/VisiteTerrainScheduleRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/VisiteTerrainConductRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PvConstatCreateRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/VisiteTerrainResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PvConstatResponse.java`
- Create: `src/main/resources/db/changelog/migrations/025-create-visite-terrain-pv-constat.sql`

**Interfaces:**
- Consumes: `Investigation`, `Agent` entities (`Agent` has `getNomComplet()`),
  `AuditEntity` base class.
- Produces: `VisiteStatus` enum, `VisiteTerrain`/`PVConstat` entities (fields per
  Global Constraints) — consumed by Task 2.
- Produces: `VisiteTerrainRepository.findByInvestigationIdOrderByScheduledAtAsc(UUID):
  List<VisiteTerrain>`, `PVConstatRepository.findByVisiteTerrainId(UUID):
  Optional<PVConstat>` — consumed by Task 2.
- Produces: `VisiteTerrainScheduleRequest` (`location` `@NotBlank @Size(max=300)`,
  `scheduledAt` `@NotNull`), `VisiteTerrainConductRequest` (`summary` `@NotBlank`),
  `PvConstatCreateRequest` (`content` `@NotBlank`), `VisiteTerrainResponse` (`id,
  investigationId, conductedByName, location, scheduledAt, conductedAt, status,
  summary, cancellationReason, carenceReason`), `PvConstatResponse` (`id,
  visiteTerrainId, content, draftedByName`) — consumed by Task 2 and Task 3.

- [ ] **Step 1: Create the `VisiteStatus` enum**

```java
package gov.bf.ascelc.univers_audits.enums;

/**
 * Statuts du cycle de vie d'une visite terrain.
 */
public enum VisiteStatus {
    // Planifiée, pas encore tenue
    SCHEDULED,
    // Tenue, constat enregistré
    CONDUCTED,
    // Annulée avant d'avoir eu lieu
    CANCELLED,
    // Tentée mais n'a pas pu aboutir (site inaccessible, accès refusé...)
    CARENCE
}
```

- [ ] **Step 2: Create the `VisiteTerrain` entity**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.VisiteStatus;
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
@Table(name = "visite_terrain", indexes = {
        @Index(name = "idx_visite_terrain_investigation",
                columnList = "investigation_id")
})
public class VisiteTerrain extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id", nullable = false)
    private Agent conductedBy;

    @Column(name = "location", length = 300, nullable = false)
    private String location;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "conducted_at")
    private Instant conductedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VisiteStatus status = VisiteStatus.SCHEDULED;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "carence_reason", columnDefinition = "TEXT")
    private String carenceReason;

    public void conduct(String summary) {
        this.conductedAt = Instant.now();
        this.summary = summary;
        this.status = VisiteStatus.CONDUCTED;
    }

    public void cancel(String reason) {
        this.cancellationReason = reason;
        this.status = VisiteStatus.CANCELLED;
    }

    public void markCarence(String reason) {
        this.carenceReason = reason;
        this.status = VisiteStatus.CARENCE;
    }
}
```

- [ ] **Step 3: Create the `PVConstat` entity**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pv_constat", indexes = {
        @Index(name = "idx_pv_constat_visite",
                columnList = "visite_terrain_id", unique = true)
})
public class PVConstat extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visite_terrain_id", nullable = false, unique = true)
    private VisiteTerrain visiteTerrain;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drafted_by_id", nullable = false)
    private Agent draftedBy;
}
```

- [ ] **Step 4: Create `VisiteTerrainRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.VisiteTerrain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisiteTerrainRepository extends JpaRepository<VisiteTerrain, UUID> {

    List<VisiteTerrain> findByInvestigationIdOrderByScheduledAtAsc(UUID investigationId);
}
```

- [ ] **Step 5: Create `PVConstatRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PVConstat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PVConstatRepository extends JpaRepository<PVConstat, UUID> {

    Optional<PVConstat> findByVisiteTerrainId(UUID visiteTerrainId);
}
```

- [ ] **Step 6: Create `VisiteTerrainScheduleRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisiteTerrainScheduleRequest {

    @NotBlank(message = "Le lieu de la visite est obligatoire")
    @Size(max = 300)
    private String location;

    @NotNull(message = "La date de la visite est obligatoire")
    private Instant scheduledAt;
}
```

- [ ] **Step 7: Create `VisiteTerrainConductRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisiteTerrainConductRequest {

    @NotBlank(message = "Le compte-rendu est obligatoire")
    private String summary;
}
```

- [ ] **Step 8: Create `PvConstatCreateRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvConstatCreateRequest {

    @NotBlank(message = "Le contenu du procès-verbal de constat est obligatoire")
    private String content;
}
```

- [ ] **Step 9: Create `VisiteTerrainResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.VisiteStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisiteTerrainResponse {
    private UUID id;
    private UUID investigationId;
    private String conductedByName;
    private String location;
    private Instant scheduledAt;
    private Instant conductedAt;
    private VisiteStatus status;
    private String summary;
    private String cancellationReason;
    private String carenceReason;
}
```

- [ ] **Step 10: Create `PvConstatResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvConstatResponse {
    private UUID id;
    private UUID visiteTerrainId;
    private String content;
    private String draftedByName;
}
```

- [ ] **Step 11: Write migration 025**

Create `src/main/resources/db/changelog/migrations/025-create-visite-terrain-pv-constat.sql`:

```sql
--liquibase formatted sql
--changeset dev:025-create-visite-terrain-pv-constat

CREATE TABLE visite_terrain (
    id                   UUID PRIMARY KEY,
    investigation_id     UUID         NOT NULL REFERENCES investigation(id),
    conducted_by_id      UUID         NOT NULL REFERENCES agent(id),
    location             VARCHAR(300) NOT NULL,
    scheduled_at         TIMESTAMP    NOT NULL,
    conducted_at         TIMESTAMP,
    status               VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    summary              TEXT,
    cancellation_reason  TEXT,
    carence_reason       TEXT,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP,
    created_by_id        VARCHAR(100),
    updated_by_id        VARCHAR(100)
);

CREATE INDEX idx_visite_terrain_investigation ON visite_terrain (investigation_id);

CREATE TABLE pv_constat (
    id                UUID PRIMARY KEY,
    visite_terrain_id UUID         NOT NULL REFERENCES visite_terrain(id),
    content           TEXT         NOT NULL,
    drafted_by_id     UUID         NOT NULL REFERENCES agent(id),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE UNIQUE INDEX idx_pv_constat_visite ON pv_constat (visite_terrain_id);
```

This migration is auto-discovered by `db.changelog-master.yaml`'s `includeAll` on
`db/changelog/migrations/` — no changelog master edit needed.

- [ ] **Step 12: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (new classes only, nothing consumes them yet).

- [ ] **Step 13: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/VisiteStatus.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/VisiteTerrain.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/PVConstat.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/VisiteTerrainRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/PVConstatRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/VisiteTerrainScheduleRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/VisiteTerrainConductRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PvConstatCreateRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/VisiteTerrainResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PvConstatResponse.java \
        src/main/resources/db/changelog/migrations/025-create-visite-terrain-pv-constat.sql
git commit -m "feat: add VisiteTerrain and PVConstat entities, repositories, DTOs and migration"
```

---

### Task 2: Mapper additions, `VisiteTerrainService`/`PvConstatService`, tests

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/VisiteTerrainService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/PvConstatService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImpl.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/PvConstatServiceImpl.java`
- Create: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImplTest.java`
- Create: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/PvConstatServiceImplTest.java`

**Interfaces:**
- Consumes: `VisiteTerrain`/`PVConstat`/repositories/DTOs (Task 1),
  `DossierAccessGuard.checkReadAccess(Dossier)` (existing), `AgentContextResolver`
  (existing).
- Produces: `VisiteTerrainService.schedule/conduct/cancel/markCarence/findByInvestigationId`
  and `PvConstatService.create/findByVisiteId` — consumed by Task 3's controller.

- [ ] **Step 1: Add mapper methods to `DossierDetailsMapper`**

Add these two methods directly after the existing `PvAuditionResponse toResponse(PVAudition pvAudition);`
line (they need no `@AfterMapping`-avoidance wrapper — every field maps directly, exactly
like the `PvAuditionResponse` mapping right above them):

```java

    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "conductedByName", source = "conductedBy.nomComplet")
    VisiteTerrainResponse toResponse(VisiteTerrain visiteTerrain);

    @Mapping(target = "visiteTerrainId", source = "visiteTerrain.id")
    @Mapping(target = "draftedByName", source = "draftedBy.nomComplet")
    PvConstatResponse toResponse(PVConstat pvConstat);
```

No new import lines needed — `DossierDetailsMapper.java` already has
`import gov.bf.ascelc.univers_audits.model.dto.response.*;` and
`import gov.bf.ascelc.univers_audits.model.entity.*;` wildcards covering the new
classes.

- [ ] **Step 2: Create `VisiteTerrainService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.VisiteTerrainResponse;

import java.util.List;
import java.util.UUID;

public interface VisiteTerrainService {

    VisiteTerrainResponse schedule(UUID investigationId, VisiteTerrainScheduleRequest request);

    VisiteTerrainResponse conduct(UUID visiteId, VisiteTerrainConductRequest request);

    VisiteTerrainResponse cancel(UUID visiteId, String reason);

    VisiteTerrainResponse markCarence(UUID visiteId, String reason);

    List<VisiteTerrainResponse> findByInvestigationId(UUID investigationId);
}
```

- [ ] **Step 3: Create `PvConstatService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PvConstatCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvConstatResponse;

import java.util.UUID;

public interface PvConstatService {

    PvConstatResponse create(UUID visiteId, PvConstatCreateRequest request);

    PvConstatResponse findByVisiteId(UUID visiteId);
}
```

- [ ] **Step 4: Create `VisiteTerrainServiceImpl`**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.VisiteStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.VisiteTerrainResponse;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.VisiteTerrain;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.VisiteTerrainRepository;
import gov.bf.ascelc.univers_audits.service.VisiteTerrainService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisiteTerrainServiceImpl implements VisiteTerrainService {

    private final VisiteTerrainRepository visiteTerrainRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierDetailsMapper    mapper;
    private final AgentContextResolver    agentContextResolver;
    private final DossierAccessGuard      accessGuard;

    @Override
    @Transactional
    public VisiteTerrainResponse schedule(UUID investigationId, VisiteTerrainScheduleRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        VisiteTerrain visite = VisiteTerrain.builder()
                .investigation(investigation)
                .location(request.getLocation())
                .scheduledAt(request.getScheduledAt())
                .conductedBy(agentContextResolver.getCurrentAgent())
                .build();

        VisiteTerrain saved = visiteTerrainRepository.save(visite);
        log.info("Visite terrain planifiée — investigation: {}, id: {}", investigationId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VisiteTerrainResponse conduct(UUID visiteId, VisiteTerrainConductRequest request) {
        VisiteTerrain visite = getVisiteOrThrow(visiteId);
        accessGuard.checkReadAccess(visite.getInvestigation().getDossier());
        if (visite.getStatus() != VisiteStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une visite planifiée peut être tenue (statut actuel : " + visite.getStatus() + ")");
        }
        visite.conduct(request.getSummary());
        VisiteTerrain saved = visiteTerrainRepository.save(visite);
        log.info("Visite terrain tenue — id: {}", visiteId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VisiteTerrainResponse cancel(UUID visiteId, String reason) {
        VisiteTerrain visite = getVisiteOrThrow(visiteId);
        accessGuard.checkReadAccess(visite.getInvestigation().getDossier());
        if (visite.getStatus() != VisiteStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une visite planifiée peut être annulée (statut actuel : " + visite.getStatus() + ")");
        }
        visite.cancel(reason);
        VisiteTerrain saved = visiteTerrainRepository.save(visite);
        log.info("Visite terrain annulée — id: {}, motif: {}", visiteId, reason);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VisiteTerrainResponse markCarence(UUID visiteId, String reason) {
        VisiteTerrain visite = getVisiteOrThrow(visiteId);
        accessGuard.checkReadAccess(visite.getInvestigation().getDossier());
        if (visite.getStatus() != VisiteStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une visite planifiée peut faire l'objet d'un constat de carence "
                            + "(statut actuel : " + visite.getStatus() + ")");
        }
        visite.markCarence(reason);
        VisiteTerrain saved = visiteTerrainRepository.save(visite);
        log.info("Visite terrain — constat de carence — id: {}, motif: {}", visiteId, reason);
        return mapper.toResponse(saved);
    }

    @Override
    public List<VisiteTerrainResponse> findByInvestigationId(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return visiteTerrainRepository.findByInvestigationIdOrderByScheduledAtAsc(investigationId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

    private VisiteTerrain getVisiteOrThrow(UUID id) {
        return visiteTerrainRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Visite terrain introuvable : " + id));
    }
}
```

- [ ] **Step 5: Create `PvConstatServiceImpl`**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvConstatCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvConstatResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.PVConstat;
import gov.bf.ascelc.univers_audits.model.entity.VisiteTerrain;
import gov.bf.ascelc.univers_audits.repository.PVConstatRepository;
import gov.bf.ascelc.univers_audits.repository.VisiteTerrainRepository;
import gov.bf.ascelc.univers_audits.service.PvConstatService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PvConstatServiceImpl implements PvConstatService {

    private final PVConstatRepository     pvConstatRepository;
    private final VisiteTerrainRepository visiteTerrainRepository;
    private final DossierDetailsMapper    mapper;
    private final AgentContextResolver    agentContextResolver;
    private final DossierAccessGuard      accessGuard;

    @Override
    @Transactional
    public PvConstatResponse create(UUID visiteId, PvConstatCreateRequest request) {
        VisiteTerrain visite = visiteTerrainRepository.findById(visiteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Visite terrain introuvable : " + visiteId));
        accessGuard.checkReadAccess(visite.getInvestigation().getDossier());

        if (pvConstatRepository.findByVisiteTerrainId(visiteId).isPresent()) {
            throw new BusinessException(
                    "Un procès-verbal de constat existe déjà pour cette visite");
        }

        PVConstat pv = PVConstat.builder()
                .visiteTerrain(visite)
                .content(request.getContent())
                .draftedBy(agentContextResolver.getCurrentAgent())
                .build();

        PVConstat saved = pvConstatRepository.save(pv);
        log.info("PV de constat créé — visite: {}, id: {}", visiteId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    public PvConstatResponse findByVisiteId(UUID visiteId) {
        PVConstat pv = getPvOrThrow(visiteId);
        Dossier dossier = pv.getVisiteTerrain().getInvestigation().getDossier();
        accessGuard.checkReadAccess(dossier);

        if (Boolean.TRUE.equals(dossier.getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException(
                    "Accès refusé — le procès-verbal d'un dossier confidentiel n'est visible "
                            + "que par les rôles habilités");
        }

        return mapper.toResponse(pv);
    }

    private PVConstat getPvOrThrow(UUID visiteId) {
        return pvConstatRepository.findByVisiteTerrainId(visiteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procès-verbal de constat introuvable pour la visite : " + visiteId));
    }
}
```

- [ ] **Step 6: Write `VisiteTerrainServiceImplTest`**

Create `src/test/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImplTest.java`,
following `AuditionServiceImplTest.java`'s exact structure and style (same
`@ExtendWith(MockitoExtension.class)`, same `buildInvestigation` helper, same
`@Mock`/`@InjectMocks` shape):

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.VisiteStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.VisiteTerrainResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.VisiteTerrainRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisiteTerrainServiceImplTest {

    @Mock private VisiteTerrainRepository visiteTerrainRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierDetailsMapper    mapper;
    @Mock private AgentContextResolver    agentContextResolver;
    @Mock private DossierAccessGuard      accessGuard;

    @InjectMocks
    private VisiteTerrainServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
    }

    @Test
    void schedule_createsVisite() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(visiteTerrainRepository.save(any(VisiteTerrain.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(VisiteTerrain.class)))
                .thenReturn(VisiteTerrainResponse.builder().build());

        VisiteTerrainScheduleRequest request = VisiteTerrainScheduleRequest.builder()
                .location("Siège de l'entreprise X")
                .scheduledAt(Instant.now())
                .build();

        service.schedule(investigation.getId(), request);

        verify(visiteTerrainRepository).save(argThat(v ->
                v.getLocation().equals("Siège de l'entreprise X")
                        && v.getStatus() == VisiteStatus.SCHEDULED
                        && v.getConductedBy() == agent));
    }

    @Test
    void conduct_movesToConductedWhenScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.SCHEDULED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(visiteTerrainRepository.save(any(VisiteTerrain.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(VisiteTerrain.class)))
                .thenReturn(VisiteTerrainResponse.builder().build());

        VisiteTerrainConductRequest request = VisiteTerrainConductRequest.builder()
                .summary("Site visité, documents comptables observés").build();

        service.conduct(visite.getId(), request);

        assertThat(visite.getStatus()).isEqualTo(VisiteStatus.CONDUCTED);
        assertThat(visite.getConductedAt()).isNotNull();
    }

    @Test
    void conduct_throwsWhenNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.CANCELLED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));

        VisiteTerrainConductRequest request = VisiteTerrainConductRequest.builder()
                .summary("Résumé").build();

        assertThatThrownBy(() -> service.conduct(visite.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancel_movesToCancelledWhenScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.SCHEDULED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(visiteTerrainRepository.save(any(VisiteTerrain.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(VisiteTerrain.class)))
                .thenReturn(VisiteTerrainResponse.builder().build());

        service.cancel(visite.getId(), "Mission reportée");

        assertThat(visite.getStatus()).isEqualTo(VisiteStatus.CANCELLED);
        assertThat(visite.getCancellationReason()).isEqualTo("Mission reportée");
    }

    @Test
    void markCarence_movesToCarenceWhenScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.SCHEDULED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(visiteTerrainRepository.save(any(VisiteTerrain.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(VisiteTerrain.class)))
                .thenReturn(VisiteTerrainResponse.builder().build());

        service.markCarence(visite.getId(), "Site inaccessible, portail fermé");

        assertThat(visite.getStatus()).isEqualTo(VisiteStatus.CARENCE);
        assertThat(visite.getCarenceReason()).isEqualTo("Site inaccessible, portail fermé");
    }

    @Test
    void markCarence_throwsWhenNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.CONDUCTED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));

        assertThatThrownBy(() -> service.markCarence(visite.getId(), "motif"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByInvestigationId_returnsEmptyWhenConfidentialAndNotAuthorized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<VisiteTerrainResponse> result = service.findByInvestigationId(investigation.getId());

        assertThat(result).isEmpty();
        verify(visiteTerrainRepository, never()).findByInvestigationIdOrderByScheduledAtAsc(any());
    }

    @Test
    void findByInvestigationId_throwsWhenInvestigationUnknown() {
        UUID id = UUID.randomUUID();
        when(investigationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByInvestigationId(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 7: Write `PvConstatServiceImplTest`**

Create `src/test/java/gov/bf/ascelc/univers_audits/service/impl/PvConstatServiceImplTest.java`,
following `PvAuditionServiceImplTest.java`'s structure (read that file first for the
exact style — it is not reproduced verbatim here since its exact mock field names may
differ slightly; match its conventions):

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvConstatCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvConstatResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.PVConstatRepository;
import gov.bf.ascelc.univers_audits.repository.VisiteTerrainRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PvConstatServiceImplTest {

    @Mock private PVConstatRepository     pvConstatRepository;
    @Mock private VisiteTerrainRepository visiteTerrainRepository;
    @Mock private DossierDetailsMapper    mapper;
    @Mock private AgentContextResolver    agentContextResolver;
    @Mock private DossierAccessGuard      accessGuard;

    @InjectMocks
    private PvConstatServiceImpl service;

    @Test
    void create_savesPvWhenNoneExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
        VisiteTerrain visite = VisiteTerrain.builder().id(UUID.randomUUID()).investigation(investigation).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(pvConstatRepository.findByVisiteTerrainId(visite.getId()))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(pvConstatRepository.save(any(PVConstat.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVConstat.class)))
                .thenReturn(PvConstatResponse.builder().build());

        PvConstatCreateRequest request = PvConstatCreateRequest.builder()
                .content("Locaux vides, aucune activité constatée").build();

        service.create(visite.getId(), request);

        verify(pvConstatRepository).save(argThat(pv ->
                pv.getContent().equals("Locaux vides, aucune activité constatée")
                        && pv.getDraftedBy() == agent
                        && pv.getVisiteTerrain() == visite));
    }

    @Test
    void create_throwsWhenPvAlreadyExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
        VisiteTerrain visite = VisiteTerrain.builder().id(UUID.randomUUID()).investigation(investigation).build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(pvConstatRepository.findByVisiteTerrainId(visite.getId()))
                .thenReturn(Optional.of(PVConstat.builder().id(UUID.randomUUID()).build()));

        PvConstatCreateRequest request = PvConstatCreateRequest.builder()
                .content("Contenu").build();

        assertThatThrownBy(() -> service.create(visite.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByVisiteId_throwsWhenConfidentialAndNotAuthorized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
        VisiteTerrain visite = VisiteTerrain.builder().id(UUID.randomUUID()).investigation(investigation).build();
        PVConstat pv = PVConstat.builder().id(UUID.randomUUID()).visiteTerrain(visite).build();

        when(pvConstatRepository.findByVisiteTerrainId(visite.getId()))
                .thenReturn(Optional.of(pv));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.findByVisiteId(visite.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByVisiteId_throwsWhenNotFound() {
        UUID visiteId = UUID.randomUUID();
        when(pvConstatRepository.findByVisiteTerrainId(visiteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByVisiteId(visiteId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 8: Run the tests**

Run: `mvn -q test -Dtest=VisiteTerrainServiceImplTest,PvConstatServiceImplTest`
Expected: BUILD SUCCESS, all tests pass (7 + 3 = 10 new tests).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/VisiteTerrainService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/PvConstatService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImpl.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/PvConstatServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImplTest.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/PvConstatServiceImplTest.java
git commit -m "feat: add VisiteTerrainService and PvConstatService with tests"
```

---

### Task 3: `VisiteTerrainController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/VisiteTerrainController.java`

**Interfaces:**
- Consumes: `VisiteTerrainService`/`PvConstatService` (Task 2).
- Produces: `POST/GET /api/v1/investigations/{investigationId}/visites-terrain`,
  `PATCH .../{visiteId}/conduct`, `PATCH .../{visiteId}/cancel`,
  `PATCH .../{visiteId}/carence`, `POST/GET .../{visiteId}/pv` — terminal, nothing
  else in this plan depends on these.

- [ ] **Step 1: Create the controller**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.PvConstatCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvConstatResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.VisiteTerrainResponse;
import gov.bf.ascelc.univers_audits.service.PvConstatService;
import gov.bf.ascelc.univers_audits.service.VisiteTerrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/visites-terrain")
@RequiredArgsConstructor
public class VisiteTerrainController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','CGEA','ADMIN_DDIC')";

    private final VisiteTerrainService visiteTerrainService;
    private final PvConstatService     pvConstatService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<VisiteTerrainResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(visiteTerrainService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VisiteTerrainResponse> schedule(
            @PathVariable UUID investigationId,
            @Valid @RequestBody VisiteTerrainScheduleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(visiteTerrainService.schedule(investigationId, request));
    }

    @PatchMapping("/{visiteId}/conduct")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VisiteTerrainResponse> conduct(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @Valid @RequestBody VisiteTerrainConductRequest request) {
        return ResponseEntity.ok(visiteTerrainService.conduct(visiteId, request));
    }

    @PatchMapping("/{visiteId}/cancel")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VisiteTerrainResponse> cancel(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @RequestParam String reason) {
        return ResponseEntity.ok(visiteTerrainService.cancel(visiteId, reason));
    }

    @PatchMapping("/{visiteId}/carence")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VisiteTerrainResponse> markCarence(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @RequestParam String reason) {
        return ResponseEntity.ok(visiteTerrainService.markCarence(visiteId, reason));
    }

    @GetMapping("/{visiteId}/pv")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<PvConstatResponse> getPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId) {
        return ResponseEntity.ok(pvConstatService.findByVisiteId(visiteId));
    }

    @PostMapping("/{visiteId}/pv")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PvConstatResponse> createPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @Valid @RequestBody PvConstatCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(pvConstatService.create(visiteId, request));
    }
}
```

- [ ] **Step 2: Compile and run the full test suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, no regressions anywhere in the suite (the same one
pre-existing environment-only `UniversAuditsApplicationTests.contextLoads` failure,
needing a live datasource, is expected and not yours to fix). Actually run the full
command and report the real "Tests run: N, Failures: X, Errors: Y" summary line.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/VisiteTerrainController.java
git commit -m "feat: add VisiteTerrainController"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** §1 (`VisiteTerrain`) → Task 1 Step 2. §2 (`PVConstat`) → Task 1
  Step 3. §3 (API, combined controller matching `AuditionController`) → Task 2, Task 3.
  Hors périmètre items (`AuditionStatus.NO_SHOW` fix, PDF, index de pièces, "au moins
  deux enquêteurs") are correctly absent from every task.
- **Type consistency verified:** `VisiteTerrain`/`PVConstat`/repository/DTO field
  names and signatures are identical across Task 1's definitions and Task 2/3's usage.
  `VisiteStatus` enum values used consistently.
- **Mirrors a real, working precedent throughout** (`Audition`/`PVAudition`) rather
  than inventing new architecture — every method shape, role constant, and access
  pattern was checked against the actual live source of that module before being
  written into this plan, not assumed from memory.
- **The two deliberate deviations from the precedent are called out explicitly** in
  Global Constraints (`location` required, `CARENCE` instead of reusing
  `AuditionStatus.NO_SHOW`) so neither the implementer nor reviewer mistakes them for
  copy-paste inconsistencies.
