# Engagement de confidentialité + Déclaration de conflit d'intérêts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a self-service endpoint where an agent candidate declares the absence of
a conflict of interest and signs the confidentiality engagement before being added to
an investigation team, and make `InvestigationServiceImpl.addMember` require that
declaration to exist (and be conflict-free) before allowing the assignment.

**Architecture:** A single new entity `EngagementConfidentialite` (1:N `Investigation`,
1:N `Agent`, unique per pair) captures both facts in one row, created via a new
self-service `POST /api/v1/investigations/{id}/engagement-prealable` endpoint (agent
signatory always `agentContextResolver.getCurrentAgent()`, never a request field —
mirrors the existing `Mandat`/CGE pattern). `addMember` gains a precondition check
against this same table before it proceeds.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL),
Lombok `@SuperBuilder`, JUnit 5 + Mockito.

## Global Constraints

- One `EngagementConfidentialite` row per `(investigation_id, agent_id)` pair,
  enforced by a unique constraint — never reissued once submitted (mirrors the
  `Mandat` precedent: no revision/reissue logic in this plan).
- `agent` on `EngagementConfidentialite` is always `agentContextResolver.getCurrentAgent()`
  — no agent-id request field.
- `conflictDetails` is required (non-blank) when `hasConflictOfInterest = true`,
  optional otherwise — validated in the service layer, not via a DB constraint.
- `addMember` rejects if no `EngagementConfidentialite` exists for
  `(investigationId, request.getAgentId())`, or if the existing one has
  `hasConflictOfInterest = true`. This applies identically to a brand-new add and to
  a reactivation (the entity is independent of `InvestigationMember.active`).
- Two EXISTING tests in `InvestigationServiceImplTest`
  (`addMember_grantsInvestigationTeamHabilitation` and
  `addMember_reactivationBranchStillGrantsInvestigationTeamHabilitation`) call
  `service.addMember(...)` without mocking the new repository lookup — Mockito's
  default answer for an `Optional`-returning method is `Optional.empty()`, so
  **both will start throwing `BusinessException` and fail** unless updated to stub
  `engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(...)` with a
  conflict-free engagement. Task 2 updates both.
- No PDF generation, no signature for interviewed persons (`Audition`), no
  enforcement of confidentiality-signing as a dossier-access gate — all explicitly
  out of scope per the spec.
- `EngagementConfidentialite` is a brand-new table with no `@Enumerated(EnumType.STRING)`
  column, so it does NOT need the CHECK-constraint drop/recreate dance that migration
  019 needed for `investigation_member.team_role` (that issue only applies to
  Hibernate-bootstrapped tables with enum columns predating Liquibase tracking).
- `AuditEntity` columns for the new table: `id UUID PRIMARY KEY`,
  `version BIGINT NOT NULL DEFAULT 0`, `created_at TIMESTAMP NOT NULL`,
  `updated_at TIMESTAMP`, `created_by_id VARCHAR(100)`, `updated_by_id VARCHAR(100)`.
- Next migration file number is `020` (last is `019-team-role-rename-and-mandat.sql`).
- `InvestigationServiceImpl` uses `@RequiredArgsConstructor` with a manually-ordered
  field list; append the new repository field at the end. `InvestigationServiceImplTest`
  uses `@InjectMocks` only (no manual positional constructor), so field order is safe.
- Existing wildcard imports already cover the new classes without new import lines:
  `InvestigationServiceImpl.java` has `import ...model.dto.request.*;` and
  `import ...model.entity.*;` — only `EngagementConfidentialiteResponse` needs an
  explicit import (response DTOs are imported individually in this file, e.g.
  `MandatResponse`). `InvestigationService.java` and `InvestigationController.java`
  import both request and response DTOs explicitly (no wildcards there) — both need
  explicit imports for the two new DTOs.
- Do NOT duplicate the unique constraint as both a named `@Table(indexes=...)`
  `unique=true` index AND a migration-level `UNIQUE` constraint — this exact
  duplication was flagged as a Minor finding in sub-chantier 1's final review. Put the
  uniqueness only in the migration (`UNIQUE (investigation_id, agent_id)`); the
  entity's `@Table(indexes=...)` may declare a plain (non-unique) index on
  `investigation_id` for query performance, nothing more.

---

### Task 1: `EngagementConfidentialite` entity, repository, DTOs, migration 020

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/EngagementConfidentialite.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/EngagementConfidentialiteRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/EngagementConfidentialiteRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/EngagementConfidentialiteResponse.java`
- Create: `src/main/resources/db/changelog/migrations/020-add-engagement-confidentialite.sql`

**Interfaces:**
- Consumes: `Investigation` entity, `Agent` entity (has `getNomComplet()`),
  `AuditEntity` base class.
- Produces: `EngagementConfidentialite` entity with fields `investigation`
  (`Investigation`), `agent` (`Agent`), `hasConflictOfInterest` (`Boolean`),
  `conflictDetails` (`String`), `signedAt` (`Instant`) — consumed by Task 2.
- Produces: `EngagementConfidentialiteRepository.findByInvestigationIdAndAgentId(UUID, UUID): Optional<EngagementConfidentialite>`
  — consumed by Task 2 (both `addMember`'s precondition and the two new service
  methods).
- Produces: `EngagementConfidentialiteRequest` DTO (`hasConflictOfInterest: Boolean`
  with `@NotNull`, `conflictDetails: String` optional) and
  `EngagementConfidentialiteResponse` DTO (`id, investigationId, agentId, agentNom,
  hasConflictOfInterest, conflictDetails, signedAt`) — consumed by Task 2 and Task 3.

- [ ] **Step 1: Create the `EngagementConfidentialite` entity**

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
@Table(name = "engagement_confidentialite", indexes = {
        @Index(name = "idx_engagement_confidentialite_investigation",
                columnList = "investigation_id")
})
public class EngagementConfidentialite extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "has_conflict_of_interest", nullable = false)
    private Boolean hasConflictOfInterest;

    @Column(name = "conflict_details", length = 2000)
    private String conflictDetails;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;
}
```

- [ ] **Step 2: Create `EngagementConfidentialiteRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.EngagementConfidentialite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EngagementConfidentialiteRepository
        extends JpaRepository<EngagementConfidentialite, UUID> {

    Optional<EngagementConfidentialite> findByInvestigationIdAndAgentId(
            UUID investigationId, UUID agentId);
}
```

- [ ] **Step 3: Create `EngagementConfidentialiteRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngagementConfidentialiteRequest {

    @NotNull(message = "La déclaration de conflit d'intérêts est obligatoire")
    private Boolean hasConflictOfInterest;

    private String conflictDetails;
}
```

- [ ] **Step 4: Create `EngagementConfidentialiteResponse`**

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
public class EngagementConfidentialiteResponse {

    private UUID id;
    private UUID investigationId;
    private UUID agentId;
    private String agentNom;
    private Boolean hasConflictOfInterest;
    private String conflictDetails;
    private Instant signedAt;
}
```

- [ ] **Step 5: Write migration 020**

Create `src/main/resources/db/changelog/migrations/020-add-engagement-confidentialite.sql`:

```sql
--liquibase formatted sql
--changeset dev:020-add-engagement-confidentialite

CREATE TABLE engagement_confidentialite (
    id                       UUID PRIMARY KEY,
    version                  BIGINT NOT NULL DEFAULT 0,
    created_at               TIMESTAMP NOT NULL,
    updated_at               TIMESTAMP,
    created_by_id            VARCHAR(100),
    updated_by_id            VARCHAR(100),
    investigation_id         UUID NOT NULL REFERENCES investigation(id),
    agent_id                 UUID NOT NULL REFERENCES agent(id),
    has_conflict_of_interest BOOLEAN NOT NULL,
    conflict_details         VARCHAR(2000),
    signed_at                TIMESTAMP NOT NULL,
    CONSTRAINT uq_engagement_confidentialite_investigation_agent
        UNIQUE (investigation_id, agent_id)
);

CREATE INDEX idx_engagement_confidentialite_investigation
    ON engagement_confidentialite (investigation_id);

COMMENT ON TABLE engagement_confidentialite IS 'Declaration de conflit d interets + signature de l engagement de confidentialite par un agent, prealable a son affectation a une equipe d investigation (Lot 3, plan de travail S8.1/S8.4/S11) - une par couple (investigation, agent), jamais reemise';
```

This migration is auto-discovered by `db.changelog-master.yaml`'s `includeAll` on
`db/changelog/migrations/` — no changelog master edit needed.

- [ ] **Step 6: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (new classes only, nothing consumes them yet).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/EngagementConfidentialite.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/EngagementConfidentialiteRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/EngagementConfidentialiteRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/EngagementConfidentialiteResponse.java \
        src/main/resources/db/changelog/migrations/020-add-engagement-confidentialite.sql
git commit -m "feat: add EngagementConfidentialite entity, repository, DTOs and migration"
```

---

### Task 2: Service layer — declaration/read methods, `addMember` precondition, tests

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `EngagementConfidentialite`/`EngagementConfidentialiteRepository`/
  `EngagementConfidentialiteRequest`/`EngagementConfidentialiteResponse` (Task 1).
- Produces: `InvestigationService.declareEngagementPrealable(UUID investigationId,
  EngagementConfidentialiteRequest request, String ipAddress):
  EngagementConfidentialiteResponse` and
  `InvestigationService.getEngagementPrealable(UUID investigationId, UUID agentId):
  EngagementConfidentialiteResponse` — consumed by Task 3's controller.

- [ ] **Step 1: Add the import and field to `InvestigationServiceImpl`**

Add this import next to the existing `import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;`:

```java
import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;
```

Add this field at the end of the existing field list (after
`private final MandatRepository mandatRepository;`):

```java
    private final EngagementConfidentialiteRepository engagementConfidentialiteRepository;
```

(`EngagementConfidentialite` the entity and `EngagementConfidentialiteRequest` the
request DTO are already covered by this file's existing `import
...model.entity.*;` and `import ...model.dto.request.*;` wildcards — no new import
line needed for either.)

- [ ] **Step 2: Add the `addMember` precondition**

In `addMember`, insert this block right after the existing "already active member"
check and right before `Agent agent = agentRepository.findById(...)`:

```java
        EngagementConfidentialite engagement = engagementConfidentialiteRepository
                .findByInvestigationIdAndAgentId(investigationId, request.getAgentId())
                .orElseThrow(() -> new BusinessException(
                        "L'agent doit d'abord déclarer l'absence de conflit d'intérêts et "
                                + "signer l'engagement de confidentialité avant d'être affecté "
                                + "à l'équipe."));

        if (Boolean.TRUE.equals(engagement.getHasConflictOfInterest())) {
            throw new BusinessException(
                    "Cet agent a déclaré un conflit d'intérêts et ne peut pas être affecté "
                            + "à cette investigation.");
        }

```

- [ ] **Step 3: Add `declareEngagementPrealable`, `getEngagementPrealable`, and the
      response-builder helper**

Add these methods right after `removeMember` (before `buildResponseWithFreshMembers`):

```java
    @Override
    @Transactional
    public EngagementConfidentialiteResponse declareEngagementPrealable(
            UUID investigationId,
            EngagementConfidentialiteRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        Agent agent = agentContextResolver.getCurrentAgent();

        if (Boolean.TRUE.equals(request.getHasConflictOfInterest())
                && (request.getConflictDetails() == null
                        || request.getConflictDetails().isBlank())) {
            throw new BusinessException(
                    "Veuillez préciser la nature du conflit d'intérêts déclaré.");
        }

        if (engagementConfidentialiteRepository
                .findByInvestigationIdAndAgentId(investigationId, agent.getId())
                .isPresent()) {
            throw new BusinessException(
                    "Une déclaration a déjà été soumise pour cet agent sur cette investigation.");
        }

        EngagementConfidentialite engagement = EngagementConfidentialite.builder()
                .investigation(inv)
                .agent(agent)
                .hasConflictOfInterest(request.getHasConflictOfInterest())
                .conflictDetails(request.getConflictDetails())
                .signedAt(Instant.now())
                .build();
        EngagementConfidentialite saved = engagementConfidentialiteRepository.save(engagement);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Engagement de confidentialité signé par " + agent.getNomComplet()
                        + (Boolean.TRUE.equals(saved.getHasConflictOfInterest())
                                ? " — conflit d'intérêts déclaré"
                                : " — aucun conflit déclaré"),
                true, agent);

        log.info("Engagement de confidentialité signé — investigation: {}, agent: {}",
                investigationId, agent.getId());
        return toEngagementConfidentialiteResponse(saved);
    }

    @Override
    public EngagementConfidentialiteResponse getEngagementPrealable(
            UUID investigationId, UUID agentId) {
        EngagementConfidentialite engagement = engagementConfidentialiteRepository
                .findByInvestigationIdAndAgentId(investigationId, agentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun engagement de confidentialité pour cet agent sur cette "
                                + "investigation."));
        return toEngagementConfidentialiteResponse(engagement);
    }

```

Add this private helper in the "MÉTHODES PRIVÉES" section, directly before
`getInvestigationOrThrow`:

```java
    private EngagementConfidentialiteResponse toEngagementConfidentialiteResponse(
            EngagementConfidentialite engagement) {
        return EngagementConfidentialiteResponse.builder()
                .id(engagement.getId())
                .investigationId(engagement.getInvestigation().getId())
                .agentId(engagement.getAgent().getId())
                .agentNom(engagement.getAgent().getNomComplet())
                .hasConflictOfInterest(engagement.getHasConflictOfInterest())
                .conflictDetails(engagement.getConflictDetails())
                .signedAt(engagement.getSignedAt())
                .build();
    }

```

- [ ] **Step 4: Add the two new methods to `InvestigationService`**

In `InvestigationService.java`, add these imports next to the existing
`import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;`:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.EngagementConfidentialiteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;
```

Add after `getMandat(UUID investigationId);` (before the closing `}`):

```java

    EngagementConfidentialiteResponse declareEngagementPrealable(
            UUID investigationId,
            EngagementConfidentialiteRequest request,
            String ipAddress);

    EngagementConfidentialiteResponse getEngagementPrealable(
            UUID investigationId, UUID agentId);
```

- [ ] **Step 5: Fix the two EXISTING `addMember` tests that will now fail**

In `InvestigationServiceImplTest.java`, `addMember_grantsInvestigationTeamHabilitation`
currently has this stub block:

```java
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigation.getId(), agent.getId())).thenReturn(false);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
```

Add this stub immediately after (before `when(agentContextResolver.getCurrentAgent())`):

```java
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), agent.getId()))
                .thenReturn(Optional.of(EngagementConfidentialite.builder()
                        .hasConflictOfInterest(false).build()));
```

Do the identical addition — same stub, same insertion point relative to the existing
`existsByInvestigationIdAndAgentIdAndActiveTrue`/`agentRepository.findById` stubs — in
`addMember_reactivationBranchStillGrantsInvestigationTeamHabilitation`.

- [ ] **Step 6: Write the new tests**

Add to `InvestigationServiceImplTest.java`, after the (now-fixed) existing tests and
before the closing `}`:

```java

    @Test
    void addMember_rejectsWhenNoEngagementDeclared() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigation.getId(), agent.getId())).thenReturn(false);
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), agent.getId())).thenReturn(Optional.empty());

        AddMemberRequest request = AddMemberRequest.builder()
                .agentId(agent.getId()).teamRole(TeamRole.INVESTIGATEUR).build();

        assertThatThrownBy(() -> service.addMember(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("engagement");
    }

    @Test
    void addMember_rejectsWhenConflictOfInterestDeclared() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigation.getId(), agent.getId())).thenReturn(false);
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), agent.getId()))
                .thenReturn(Optional.of(EngagementConfidentialite.builder()
                        .hasConflictOfInterest(true).build()));

        AddMemberRequest request = AddMemberRequest.builder()
                .agentId(agent.getId()).teamRole(TeamRole.INVESTIGATEUR).build();

        assertThatThrownBy(() -> service.addMember(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("conflit d'intérêts");
    }

    @Test
    void declareEngagementPrealable_succeedsWithoutConflict() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), currentAgent.getId())).thenReturn(Optional.empty());
        when(engagementConfidentialiteRepository.save(any(EngagementConfidentialite.class)))
                .thenAnswer(inv -> {
                    EngagementConfidentialite e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        EngagementConfidentialiteRequest request = EngagementConfidentialiteRequest.builder()
                .hasConflictOfInterest(false).build();

        EngagementConfidentialiteResponse response =
                service.declareEngagementPrealable(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getAgentId()).isEqualTo(currentAgent.getId());
        assertThat(response.getHasConflictOfInterest()).isFalse();
    }

    @Test
    void declareEngagementPrealable_rejectsWhenConflictDetailsMissing() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        EngagementConfidentialiteRequest request = EngagementConfidentialiteRequest.builder()
                .hasConflictOfInterest(true).build();

        assertThatThrownBy(() -> service.declareEngagementPrealable(
                investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("préciser");
    }

    @Test
    void declareEngagementPrealable_rejectsWhenAlreadyDeclared() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), currentAgent.getId()))
                .thenReturn(Optional.of(EngagementConfidentialite.builder()
                        .hasConflictOfInterest(false).build()));

        EngagementConfidentialiteRequest request = EngagementConfidentialiteRequest.builder()
                .hasConflictOfInterest(false).build();

        assertThatThrownBy(() -> service.declareEngagementPrealable(
                investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été soumise");
    }

    @Test
    void getEngagementPrealable_returnsResponseWhenExists() {
        UUID investigationId = UUID.randomUUID();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(
                Dossier.builder().id(UUID.randomUUID()).build());
        EngagementConfidentialite engagement = EngagementConfidentialite.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .agent(agent)
                .hasConflictOfInterest(false)
                .signedAt(java.time.Instant.now())
                .build();

        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigationId, agent.getId())).thenReturn(Optional.of(engagement));

        EngagementConfidentialiteResponse response =
                service.getEngagementPrealable(investigationId, agent.getId());

        assertThat(response.getAgentId()).isEqualTo(agent.getId());
        assertThat(response.getHasConflictOfInterest()).isFalse();
    }

    @Test
    void getEngagementPrealable_throwsWhenNotFound() {
        UUID investigationId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigationId, agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEngagementPrealable(investigationId, agentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

Add these imports at the top of `InvestigationServiceImplTest.java` alongside the
existing ones (some may already be present from the previous sub-chantier — check
before adding a duplicate):

```java
import gov.bf.ascelc.univers_audits.model.dto.request.EngagementConfidentialiteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;
import gov.bf.ascelc.univers_audits.model.entity.EngagementConfidentialite;
import gov.bf.ascelc.univers_audits.repository.EngagementConfidentialiteRepository;
```

and add the mock field, after `@Mock private MandatRepository mandatRepository;`:

```java
    @Mock private EngagementConfidentialiteRepository engagementConfidentialiteRepository;
```

- [ ] **Step 7: Run the tests**

Run: `mvn -q test -Dtest=InvestigationServiceImplTest`
Expected: BUILD SUCCESS, all tests (existing + 7 new) pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: require engagement de confidentialite before addMember, add declare/read methods"
```

---

### Task 3: Controller endpoints

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java`

**Interfaces:**
- Consumes: `InvestigationService.declareEngagementPrealable`/`getEngagementPrealable`
  (Task 2).
- Produces: `POST /api/v1/investigations/{id}/engagement-prealable`,
  `GET /api/v1/investigations/{id}/engagement-prealable/{agentId}` — terminal, nothing
  else in this plan depends on these.

- [ ] **Step 1: Add the imports**

Add alongside the existing `model.dto.response.MandatResponse` import:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.EngagementConfidentialiteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;
```

- [ ] **Step 2: Add the two endpoints**

Add a new section after the `// ── Mandat ────` block's `getMandat` method, before the
closing `}` of the class:

```java

    // ── Engagement préalable ─────────────────────────────────

    @PostMapping("/{id}/engagement-prealable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EngagementConfidentialiteResponse> declareEngagementPrealable(
            @PathVariable UUID id,
            @Valid @RequestBody EngagementConfidentialiteRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Déclaration engagement préalable — investigation {}", id);
        EngagementConfidentialiteResponse result = investigationService
                .declareEngagementPrealable(id, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "DECLARER_ENGAGEMENT_PREALABLE", "INVESTIGATION", id.toString(),
                "Déclaration engagement préalable (conflit d'intérêts + confidentialité)",
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/engagement-prealable/{agentId}")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<EngagementConfidentialiteResponse> getEngagementPrealable(
            @PathVariable UUID id,
            @PathVariable UUID agentId) {
        return ResponseEntity.ok(investigationService.getEngagementPrealable(id, agentId));
    }
```

- [ ] **Step 3: Compile and run the full test suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, no regressions anywhere in the suite (the same one
pre-existing environment-only `UniversAuditsApplicationTests.contextLoads` failure,
needing a live datasource, is expected and not yours to fix).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java
git commit -m "feat: add engagement-prealable declaration and read endpoints to InvestigationController"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** Decision §1 (entity) → Task 1. Decision §2 (self-service
  declare/sign endpoint) → Task 2 Step 3, Task 3. Decision §3 (`addMember`
  precondition) → Task 2 Step 2. Hors périmètre items (Audition signing, PDF
  generation, reissue logic, access-gate enforcement) are correctly absent from
  every task.
- **Type consistency verified:** `EngagementConfidentialite`/
  `EngagementConfidentialiteRepository`/`EngagementConfidentialiteRequest`/
  `EngagementConfidentialiteResponse` field names and signatures are identical
  across Task 1's definitions and Task 2/3's usage.
- **The two pre-existing test breakages are explicitly called out** (Global
  Constraints and Task 2 Step 5) rather than left for the task reviewer to discover
  as a surprise regression — this is a known, deliberate consequence of Task 2's
  change, not a defect.
- **GET roles** mirror the existing `GET /{id}/mandat` roles exactly (`CGEA, CGE,
  CONTROLEUR_ETAT, MEMBRE_CTADP, ADMIN_DDIC`) — no new role introduced. **POST role**
  is deliberately the broadest in this controller (`isAuthenticated()`, no role
  restriction) because any agent who might join a team must be able to self-declare,
  regardless of their functional role — mirrors `GET /api/v1/agents/active`'s existing
  precedent in this codebase for the same reason.
