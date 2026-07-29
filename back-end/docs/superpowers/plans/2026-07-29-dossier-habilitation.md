# Habilitation nominative par dossier (lecture) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current "single `agentInCharge` or privileged role" read-access model with a nominative, tracked, revocable per-dossier habilitation — auto-granted to the agent in charge and to active investigation team members, plus a manual grant/revoke API for CGE/CGEA.

**Architecture:** A new append-only `DossierHabilitation` entity records grant/revoke events (never updated in place except to stamp a revocation). `DossierAccessGuard.checkReadAccess` — already the single enforcement point used by ~13 files — is rewritten to check for an active habilitation row instead of `agentInCharge` equality; its privileged-role short-circuit (CGE/CGEA/ADMIN_DDIC) is untouched. `DossierServiceImpl.registerReception()` and `InvestigationServiceImpl.addMember()/removeMember()` are the three places that change dossier assignment today; each gets a call into a new `DossierHabilitationService` to keep habilitation rows in sync. `DossierServiceImpl.findById()`/`findAll()`, which today duplicate their own `agentInCharge`-only access logic instead of using the guard, are simplified to use it — otherwise the primary way to open a dossier would still block team members after everything else is fixed.

**Tech Stack:** Spring Boot 3 / Java 17, JPA/Hibernate, MapStruct 1.5.5, Liquibase (formatted SQL changesets), JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Scope is **read access only** (per approved design, §1). Write-side access control (state-transition endpoints in `DossierController`, currently role-gated only) is explicitly out of scope — do not touch any `@PreAuthorize` annotation on a state-transition endpoint.
- No new `TeamRole` values (personnes-ressources, DEI) — out of scope, covered by manual grant instead.
- `DossierHabilitation` is **append-only**: grant = new row; revoke = set `revokedAt`/`revokedBy`/`revocationReason` on the existing row, never delete, never reuse a row for a second grant.
- "Has active read access" = `EXISTS` a `DossierHabilitation` row for `(dossier, agent)` with `revokedAt IS NULL`, regardless of `source`.
- Grant idempotency is **per-source**: granting the same `(dossier, agent, source)` twice while one is already active is a no-op (auto-grants) or a rejected duplicate (manual grant) — never a second active row for the same source.
- `canSeeConfidential()` (CGE/CGEA/ADMIN_DDIC bypass) is unchanged — do not fold it into the habilitation model.
- `DossierServiceImpl.findByStatus()` and `findMyDossiers()` are **not** touched by this plan. Correction versus the approved design doc: `findByStatus()` has no per-agent restriction branch today at all (unlike `findAll()`, which does) — there is no "duplicated `agentInCharge` logic" there to fix, so adding a new restriction would be a scope expansion, not a bug fix. `findMyDossiers()` answers a different question ("dossiers I'm the case owner of") and correctly stays `agentInCharge`-based.
- Manual grant `reason` is mandatory (`@NotBlank`); manual revoke `reason` is mandatory (required `@RequestParam`).

---

## File Structure

| File | Responsibility |
|---|---|
| `enums/HabilitationSource.java` (new) | `AGENT_IN_CHARGE`, `INVESTIGATION_TEAM`, `MANUAL` |
| `model/entity/DossierHabilitation.java` (new) | Append-only grant/revoke record |
| `repository/DossierHabilitationRepository.java` (new) | Existence/lookup queries for the guard and the service |
| `repository/DossierRepository.java` (modified) | Adds `findAccessibleByAgentId` for `findAll()`'s restricted branch |
| `db/changelog/migrations/010-dossier-habilitation.sql` (new) | Table + mandatory backfill from existing `agentInCharge`/`InvestigationMember` data |
| `model/dto/request/HabilitationGrantRequest.java` (new) | Manual grant payload |
| `model/dto/response/DossierHabilitationResponse.java` (new) | Habilitation read model |
| `mapper/DossierHabilitationMapper.java` (new) | Entity → response, reuses `AgentMapper` |
| `service/DossierHabilitationService.java` (new) | Grant/revoke contract (both auto-trigger and manual-API methods) |
| `service/impl/DossierHabilitationServiceImpl.java` (new) | Implementation |
| `controller/DossierHabilitationController.java` (new) | `/api/v1/dossiers/{dossierId}/habilitations` |
| `shared/utils/DossierAccessGuard.java` (modified) | `checkReadAccess` now checks habilitation instead of `agentInCharge` |
| `service/impl/DossierServiceImpl.java` (modified) | Grants `AGENT_IN_CHARGE` in `registerReception`; simplifies `findById`/`findAll` |
| `service/impl/InvestigationServiceImpl.java` (modified) | Grants/revokes `INVESTIGATION_TEAM` in `addMember`/`removeMember` |

---

### Task 1: Foundation — enum, entity, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/HabilitationSource.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/DossierHabilitation.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/DossierHabilitationRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java`
- Create: `src/main/resources/db/changelog/migrations/010-dossier-habilitation.sql`

**Interfaces:**
- Produces: `enum HabilitationSource{AGENT_IN_CHARGE, INVESTIGATION_TEAM, MANUAL}`; entity `DossierHabilitation` with fields `dossier, agent, source, grantedBy, reason, revokedAt, revokedBy, revocationReason` and methods `boolean isActive()` / `void revoke(Agent revokedBy, String revocationReason)`; repository methods `existsByDossierIdAndAgentIdAndRevokedAtIsNull`, `existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull`, `findByDossierIdAndAgentIdAndRevokedAtIsNull`, `findFirstByDossierIdAndAgentIdAndSourceAndRevokedAtIsNullOrderByCreatedAtDesc`, `findByDossierIdAndRevokedAtIsNull` — all consumed by Task 2 and Task 4. `DossierRepository.findAccessibleByAgentId(UUID agentId, Pageable pageable)` — consumed by Task 5.

No new unit test for this task — plain declarations plus a migration; the deliverable is verified by a successful compile, matching the precedent set by Task 1 of the NatureSaisine plan for the same reason (nothing here has behavior to unit-test yet).

- [ ] **Step 1: Create the `HabilitationSource` enum**

```java
package gov.bf.ascelc.univers_audits.enums;

public enum HabilitationSource {
    AGENT_IN_CHARGE,
    INVESTIGATION_TEAM,
    MANUAL
}
```

- [ ] **Step 2: Create the `DossierHabilitation` entity**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
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
@Table(name = "dossier_habilitation", indexes = {
        @Index(name = "idx_habilitation_dossier_agent",
                columnList = "dossier_id, agent_id"),
        @Index(name = "idx_habilitation_agent",
                columnList = "agent_id")
})
public class DossierHabilitation extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 25)
    private HabilitationSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_id")
    private Agent grantedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_id")
    private Agent revokedBy;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(Agent revokedBy, String revocationReason) {
        this.revokedAt = Instant.now();
        this.revokedBy = revokedBy;
        this.revocationReason = revocationReason;
    }
}
```

- [ ] **Step 3: Create the `DossierHabilitationRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.model.entity.DossierHabilitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DossierHabilitationRepository extends JpaRepository<DossierHabilitation, UUID> {

    boolean existsByDossierIdAndAgentIdAndRevokedAtIsNull(UUID dossierId, UUID agentId);

    boolean existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
            UUID dossierId, UUID agentId, HabilitationSource source);

    List<DossierHabilitation> findByDossierIdAndAgentIdAndRevokedAtIsNull(
            UUID dossierId, UUID agentId);

    Optional<DossierHabilitation> findFirstByDossierIdAndAgentIdAndSourceAndRevokedAtIsNullOrderByCreatedAtDesc(
            UUID dossierId, UUID agentId, HabilitationSource source);

    List<DossierHabilitation> findByDossierIdAndRevokedAtIsNull(UUID dossierId);
}
```

- [ ] **Step 4: Add `findAccessibleByAgentId` to `DossierRepository`**

In `DossierRepository.java`, add this method (anywhere among the other `@Query` methods, e.g. right after `findByAgentInChargeId`):

```java
    @EntityGraph(attributePaths = "investigation")
    @Query("""
            SELECT DISTINCT d FROM Dossier d
            JOIN DossierHabilitation h ON h.dossier = d
            WHERE h.agent.id = :agentId AND h.revokedAt IS NULL
            """)
    Page<Dossier> findAccessibleByAgentId(
            @Param("agentId") UUID agentId, Pageable pageable);
```

- [ ] **Step 5: Write the migration**

Create `src/main/resources/db/changelog/migrations/010-dossier-habilitation.sql`:

```sql
--liquibase formatted sql
--changeset dev:010-dossier-habilitation

CREATE TABLE dossier_habilitation (
    id                  UUID PRIMARY KEY,
    dossier_id          UUID         NOT NULL REFERENCES dossier(id),
    agent_id            UUID         NOT NULL REFERENCES agent(id),
    source              VARCHAR(25)  NOT NULL,
    granted_by_id       UUID         REFERENCES agent(id),
    reason              VARCHAR(500),
    revoked_at          TIMESTAMP,
    revoked_by_id       UUID         REFERENCES agent(id),
    revocation_reason   VARCHAR(500),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by_id       VARCHAR(100),
    updated_by_id       VARCHAR(100)
);

CREATE INDEX idx_dossier_habilitation_dossier_agent
    ON dossier_habilitation (dossier_id, agent_id);
CREATE INDEX idx_dossier_habilitation_agent
    ON dossier_habilitation (agent_id);

-- Backfill obligatoire : sans ceci, tout agent perdrait l'accès à ses dossiers
-- actuels au déploiement (le nouveau garde-fou ne connaîtrait aucune habilitation).
INSERT INTO dossier_habilitation
    (id, dossier_id, agent_id, source, reason, version, created_at)
SELECT gen_random_uuid(), d.id, d.agent_in_charge_id, 'AGENT_IN_CHARGE',
       'Backfill migration 010 — agent en charge existant', 0, now()
FROM dossier d
WHERE d.agent_in_charge_id IS NOT NULL;

INSERT INTO dossier_habilitation
    (id, dossier_id, agent_id, source, reason, version, created_at)
SELECT gen_random_uuid(), i.case_id, im.agent_id, 'INVESTIGATION_TEAM',
       'Backfill migration 010 — membre d''équipe actif existant', 0, now()
FROM investigation_member im
JOIN investigation i ON i.id = im.investigation_id
WHERE im.active = TRUE;
```

- [ ] **Step 6: Verify the project compiles**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/HabilitationSource.java src/main/java/gov/bf/ascelc/univers_audits/model/entity/DossierHabilitation.java src/main/java/gov/bf/ascelc/univers_audits/repository/DossierHabilitationRepository.java src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java src/main/resources/db/changelog/migrations/010-dossier-habilitation.sql
git commit -m "feat: add DossierHabilitation entity, repository and migration"
```

---

### Task 2: `DossierHabilitationService` — grant/revoke logic, DTOs, mapper

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/HabilitationGrantRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierHabilitationResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierHabilitationMapper.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/DossierHabilitationService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierHabilitationServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierHabilitationServiceImplTest.java`

**Interfaces:**
- Consumes: `DossierHabilitationRepository`, `DossierHabilitation`, `HabilitationSource` from Task 1; `DossierAccessGuard.getDossierOrThrow`/`checkReadAccess` (existing, unchanged signatures); `AgentMapper.toSummaryResponse(Agent)` (existing); `AgentContextResolver.getCurrentAgent()` (existing); `AgentRepository.findById(UUID)` (existing).
- Produces: `DossierHabilitationService` with methods `grant(Dossier, Agent, HabilitationSource, Agent grantedBy, String reason)`, `revokeBySource(Dossier, Agent, HabilitationSource)`, `DossierHabilitationResponse grantManual(UUID dossierId, HabilitationGrantRequest)`, `void revokeManual(UUID dossierId, UUID agentId, String reason)`, `List<DossierHabilitationResponse> findActiveByDossier(UUID dossierId)`. `grant`/`revokeBySource` are consumed by Task 5 and Task 6; the rest by Task 3 (controller).

- [ ] **Step 1: Create the request/response DTOs**

`model/dto/request/HabilitationGrantRequest.java`:

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HabilitationGrantRequest {

    @NotNull(message = "L'agent est obligatoire")
    private UUID agentId;

    @NotBlank(message = "Le motif est obligatoire pour un octroi manuel")
    @Size(max = 500)
    private String reason;
}
```

`model/dto/response/DossierHabilitationResponse.java`:

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierHabilitationResponse {

    private UUID id;
    private AgentSummaryResponse agent;
    private HabilitationSource source;
    private AgentSummaryResponse grantedBy;
    private String reason;
    private Instant grantedAt;
    private Instant revokedAt;
    private AgentSummaryResponse revokedBy;
    private String revocationReason;
    private boolean active;
}
```

- [ ] **Step 2: Create the mapper**

```java
package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.model.entity.DossierHabilitation;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        uses = AgentMapper.class,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface DossierHabilitationMapper {

    @Mapping(target = "grantedAt", source = "createdAt")
    @Mapping(target = "active", expression = "java(habilitation.getRevokedAt() == null)")
    DossierHabilitationResponse toResponse(DossierHabilitation habilitation);
}
```

- [ ] **Step 3: Create the service interface**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.model.dto.request.HabilitationGrantRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;

import java.util.List;
import java.util.UUID;

public interface DossierHabilitationService {

    /** Octroi (auto-déclenché). Idempotent par source : no-op si déjà actif. */
    void grant(Dossier dossier, Agent agent, HabilitationSource source,
               Agent grantedBy, String reason);

    /** Révocation (auto-déclenchée) de l'habilitation active de cette source. No-op si absente. */
    void revokeBySource(Dossier dossier, Agent agent, HabilitationSource source);

    /** Octroi manuel (API), source = MANUAL. */
    DossierHabilitationResponse grantManual(UUID dossierId, HabilitationGrantRequest request);

    /** Révocation manuelle (API) : révoque toutes les habilitations actives de cet agent sur ce dossier. */
    void revokeManual(UUID dossierId, UUID agentId, String reason);

    List<DossierHabilitationResponse> findActiveByDossier(UUID dossierId);
}
```

- [ ] **Step 4: Write the failing test**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.mapper.DossierHabilitationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.HabilitationGrantRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.DossierHabilitation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DossierHabilitationServiceImplTest {

    @Mock private DossierHabilitationRepository habilitationRepository;
    @Mock private AgentRepository               agentRepository;
    @Mock private DossierAccessGuard            accessGuard;
    @Mock private AgentContextResolver          agentContextResolver;
    @Mock private DossierHabilitationMapper     mapper;

    @InjectMocks
    private DossierHabilitationServiceImpl service;

    @Test
    void grant_createsRowWhenNoneActiveForSource() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Agent   agent   = Agent.builder().id(UUID.randomUUID()).build();
        Agent   grantedBy = Agent.builder().id(UUID.randomUUID()).build();

        when(habilitationRepository.existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                dossier.getId(), agent.getId(), HabilitationSource.AGENT_IN_CHARGE))
                .thenReturn(false);

        service.grant(dossier, agent, HabilitationSource.AGENT_IN_CHARGE, grantedBy, "motif");

        verify(habilitationRepository).save(argThat(h ->
                h.getDossier() == dossier
                        && h.getAgent() == agent
                        && h.getSource() == HabilitationSource.AGENT_IN_CHARGE
                        && h.getGrantedBy() == grantedBy
                        && "motif".equals(h.getReason())));
    }

    @Test
    void grant_isNoOpWhenAlreadyActiveForSource() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Agent   agent   = Agent.builder().id(UUID.randomUUID()).build();

        when(habilitationRepository.existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                dossier.getId(), agent.getId(), HabilitationSource.INVESTIGATION_TEAM))
                .thenReturn(true);

        service.grant(dossier, agent, HabilitationSource.INVESTIGATION_TEAM, agent, "motif");

        verify(habilitationRepository, never()).save(any());
    }

    @Test
    void revokeBySource_revokesTheActiveRow() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Agent   agent   = Agent.builder().id(UUID.randomUUID()).build();
        DossierHabilitation existing = DossierHabilitation.builder()
                .dossier(dossier).agent(agent)
                .source(HabilitationSource.INVESTIGATION_TEAM)
                .build();

        when(habilitationRepository
                .findFirstByDossierIdAndAgentIdAndSourceAndRevokedAtIsNullOrderByCreatedAtDesc(
                        dossier.getId(), agent.getId(), HabilitationSource.INVESTIGATION_TEAM))
                .thenReturn(Optional.of(existing));

        service.revokeBySource(dossier, agent, HabilitationSource.INVESTIGATION_TEAM);

        assertThat(existing.getRevokedAt()).isNotNull();
        verify(habilitationRepository).save(existing);
    }

    @Test
    void revokeBySource_isNoOpWhenNoneActive() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Agent   agent   = Agent.builder().id(UUID.randomUUID()).build();

        when(habilitationRepository
                .findFirstByDossierIdAndAgentIdAndSourceAndRevokedAtIsNullOrderByCreatedAtDesc(
                        dossier.getId(), agent.getId(), HabilitationSource.INVESTIGATION_TEAM))
                .thenReturn(Optional.empty());

        service.revokeBySource(dossier, agent, HabilitationSource.INVESTIGATION_TEAM);

        verify(habilitationRepository, never()).save(any());
    }

    @Test
    void grantManual_rejectsWhenAgentAlreadyHasActiveManualGrant() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   agent   = Agent.builder().id(agentId).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(habilitationRepository.existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                dossierId, agentId, HabilitationSource.MANUAL)).thenReturn(true);

        HabilitationGrantRequest request = HabilitationGrantRequest.builder()
                .agentId(agentId).reason("Consultation étude d'opportunité").build();

        assertThatThrownBy(() -> service.grantManual(dossierId, request))
                .isInstanceOf(BusinessException.class);

        verify(habilitationRepository, never()).save(any());
    }

    @Test
    void grantManual_createsRowAndReturnsResponse() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   agent   = Agent.builder().id(agentId).build();
        Agent   currentAgent = Agent.builder().id(UUID.randomUUID()).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(habilitationRepository.existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                dossierId, agentId, HabilitationSource.MANUAL)).thenReturn(false);
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(habilitationRepository.save(any(DossierHabilitation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(DossierHabilitation.class)))
                .thenReturn(DossierHabilitationResponse.builder().build());

        HabilitationGrantRequest request = HabilitationGrantRequest.builder()
                .agentId(agentId).reason("Consultation étude d'opportunité").build();

        service.grantManual(dossierId, request);

        verify(habilitationRepository).save(argThat(h ->
                h.getSource() == HabilitationSource.MANUAL
                        && h.getGrantedBy() == currentAgent
                        && "Consultation étude d'opportunité".equals(h.getReason())));
    }

    @Test
    void revokeManual_revokesAllActiveRowsForAgent() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   currentAgent = Agent.builder().id(UUID.randomUUID()).build();

        DossierHabilitation row1 = DossierHabilitation.builder()
                .source(HabilitationSource.AGENT_IN_CHARGE).build();
        DossierHabilitation row2 = DossierHabilitation.builder()
                .source(HabilitationSource.MANUAL).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(habilitationRepository.findByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId))
                .thenReturn(List.of(row1, row2));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);

        service.revokeManual(dossierId, agentId, "Fin de mission");

        assertThat(row1.getRevokedAt()).isNotNull();
        assertThat(row2.getRevokedAt()).isNotNull();
        assertThat(row1.getRevocationReason()).isEqualTo("Fin de mission");
        verify(habilitationRepository).saveAll(List.of(row1, row2));
    }

    @Test
    void revokeManual_throwsWhenNoActiveHabilitation() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(habilitationRepository.findByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.revokeManual(dossierId, agentId, "motif"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void grantManual_throwsWhenAgentNotFound() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

        HabilitationGrantRequest request = HabilitationGrantRequest.builder()
                .agentId(agentId).reason("motif").build();

        assertThatThrownBy(() -> service.grantManual(dossierId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `mvn -q test -Dtest=DossierHabilitationServiceImplTest`
Expected: compile error — `DossierHabilitationServiceImpl` does not exist yet.

- [ ] **Step 6: Implement `DossierHabilitationServiceImpl`**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.mapper.DossierHabilitationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.HabilitationGrantRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.DossierHabilitation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
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
public class DossierHabilitationServiceImpl implements DossierHabilitationService {

    private final DossierHabilitationRepository habilitationRepository;
    private final AgentRepository               agentRepository;
    private final DossierAccessGuard            accessGuard;
    private final AgentContextResolver          agentContextResolver;
    private final DossierHabilitationMapper     mapper;

    @Override
    @Transactional
    public void grant(Dossier dossier, Agent agent, HabilitationSource source,
                       Agent grantedBy, String reason) {
        boolean alreadyActive = habilitationRepository
                .existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                        dossier.getId(), agent.getId(), source);
        if (alreadyActive) {
            return;
        }
        DossierHabilitation habilitation = DossierHabilitation.builder()
                .dossier(dossier)
                .agent(agent)
                .source(source)
                .grantedBy(grantedBy)
                .reason(reason)
                .build();
        habilitationRepository.save(habilitation);
        log.info("[Habilitation] Octroyée — dossier: {}, agent: {}, source: {}",
                dossier.getId(), agent.getId(), source);
    }

    @Override
    @Transactional
    public void revokeBySource(Dossier dossier, Agent agent, HabilitationSource source) {
        habilitationRepository
                .findFirstByDossierIdAndAgentIdAndSourceAndRevokedAtIsNullOrderByCreatedAtDesc(
                        dossier.getId(), agent.getId(), source)
                .ifPresentOrElse(
                        h -> {
                            h.revoke(null, "Retrait automatique — source " + source);
                            habilitationRepository.save(h);
                            log.info("[Habilitation] Révoquée — dossier: {}, agent: {}, source: {}",
                                    dossier.getId(), agent.getId(), source);
                        },
                        () -> log.warn("[Habilitation] Aucune habilitation active à révoquer — "
                                        + "dossier: {}, agent: {}, source: {}",
                                dossier.getId(), agent.getId(), source));
    }

    @Override
    @Transactional
    public DossierHabilitationResponse grantManual(UUID dossierId, HabilitationGrantRequest request) {
        Dossier dossier = accessGuard.getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        Agent agent = agentRepository.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Agent introuvable : " + request.getAgentId()));

        boolean alreadyActive = habilitationRepository
                .existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                        dossierId, agent.getId(), HabilitationSource.MANUAL);
        if (alreadyActive) {
            throw new BusinessException(
                    "Cet agent a déjà un accès manuel actif à ce dossier");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();
        DossierHabilitation habilitation = DossierHabilitation.builder()
                .dossier(dossier)
                .agent(agent)
                .source(HabilitationSource.MANUAL)
                .grantedBy(currentAgent)
                .reason(request.getReason())
                .build();

        DossierHabilitation saved = habilitationRepository.save(habilitation);
        log.info("[Habilitation] Octroi manuel — dossier: {}, agent: {}, par: {}",
                dossierId, agent.getId(), currentAgent.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void revokeManual(UUID dossierId, UUID agentId, String reason) {
        accessGuard.checkReadAccess(accessGuard.getDossierOrThrow(dossierId));

        List<DossierHabilitation> active = habilitationRepository
                .findByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId);
        if (active.isEmpty()) {
            throw new BusinessException("Cet agent n'a aucun accès actif à ce dossier");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();
        active.forEach(h -> h.revoke(currentAgent, reason));
        habilitationRepository.saveAll(active);
        log.info("[Habilitation] Révocation manuelle — dossier: {}, agent: {}, par: {}",
                dossierId, agentId, currentAgent.getId());
    }

    @Override
    public List<DossierHabilitationResponse> findActiveByDossier(UUID dossierId) {
        accessGuard.checkReadAccess(accessGuard.getDossierOrThrow(dossierId));
        return habilitationRepository.findByDossierIdAndRevokedAtIsNull(dossierId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -q test -Dtest=DossierHabilitationServiceImplTest`
Expected: all 9 tests `PASS`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/HabilitationGrantRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierHabilitationResponse.java src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierHabilitationMapper.java src/main/java/gov/bf/ascelc/univers_audits/service/DossierHabilitationService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierHabilitationServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierHabilitationServiceImplTest.java
git commit -m "feat: add DossierHabilitationService with grant/revoke logic"
```

---

### Task 3: REST controller

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/DossierHabilitationController.java`

**Interfaces:**
- Consumes: `DossierHabilitationService` (Task 2), `HabilitationGrantRequest`/`DossierHabilitationResponse` (Task 2).

No dedicated controller test — this codebase has no controller-level unit tests anywhere (verified: none exist for any of the ~20 existing controllers); the service layer underneath is already fully tested in Task 2. Verification is via compile.

- [ ] **Step 1: Create the controller**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.HabilitationGrantRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/habilitations")
@RequiredArgsConstructor
public class DossierHabilitationController {

    private final DossierHabilitationService habilitationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CGE','CGEA','ADMIN_DDIC')")
    public ResponseEntity<List<DossierHabilitationResponse>> findActive(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(habilitationService.findActiveByDossier(dossierId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CGE','CGEA','ADMIN_DDIC')")
    public ResponseEntity<DossierHabilitationResponse> grant(
            @PathVariable UUID dossierId,
            @Valid @RequestBody HabilitationGrantRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(habilitationService.grantManual(dossierId, request));
    }

    @DeleteMapping("/{agentId}")
    @PreAuthorize("hasAnyRole('CGE','CGEA','ADMIN_DDIC')")
    public ResponseEntity<Void> revoke(
            @PathVariable UUID dossierId,
            @PathVariable UUID agentId,
            @RequestParam String reason) {
        habilitationService.revokeManual(dossierId, agentId, reason);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Verify the project compiles**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/DossierHabilitationController.java
git commit -m "feat: add DossierHabilitationController REST endpoints"
```

---

### Task 4: Rewrite `DossierAccessGuard.checkReadAccess`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuard.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuardTest.java`

**Interfaces:**
- Consumes: `DossierHabilitationRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull` (Task 1).
- Produces: `DossierAccessGuard.checkReadAccess(Dossier)` with new internal behavior, same public signature — consumed unchanged by the ~13 existing call sites and by Task 2's `DossierHabilitationServiceImpl`. `getDossierOrThrow`/`canSeeConfidential` are untouched.

This is the safety-critical piece (confidentiality enforcement) — full TDD, thorough coverage.

- [ ] **Step 1: Write the failing test**

```java
package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DossierAccessGuardTest {

    @Mock private DossierRepository             dossierRepository;
    @Mock private AgentRepository               agentRepository;
    @Mock private SecurityUtils                 securityUtils;
    @Mock private DossierHabilitationRepository habilitationRepository;

    @InjectMocks
    private DossierAccessGuard guard;

    @Test
    void checkReadAccess_allowsPrivilegedRoleRegardlessOfHabilitation() {
        when(securityUtils.hasRole("CGE")).thenReturn(true);

        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();

        assertThatCode(() -> guard.checkReadAccess(dossier)).doesNotThrowAnyException();
    }

    @Test
    void checkReadAccess_allowsAgentWithActiveHabilitation() {
        when(securityUtils.hasRole("CGE")).thenReturn(false);
        when(securityUtils.hasRole("CGEA")).thenReturn(false);
        when(securityUtils.hasRole("ADMIN_DDIC")).thenReturn(false);

        UUID agentId   = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   agent   = Agent.builder().id(agentId).build();

        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-1"));
        when(agentRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(agent));
        when(habilitationRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId))
                .thenReturn(true);

        assertThatCode(() -> guard.checkReadAccess(dossier)).doesNotThrowAnyException();
    }

    @Test
    void checkReadAccess_rejectsAgentWithoutHabilitation() {
        when(securityUtils.hasRole("CGE")).thenReturn(false);
        when(securityUtils.hasRole("CGEA")).thenReturn(false);
        when(securityUtils.hasRole("ADMIN_DDIC")).thenReturn(false);

        UUID agentId   = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   agent   = Agent.builder().id(agentId).build();

        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-1"));
        when(agentRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(agent));
        when(habilitationRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.checkReadAccess(dossier))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void checkReadAccess_rejectsUnauthenticatedAgent() {
        when(securityUtils.hasRole("CGE")).thenReturn(false);
        when(securityUtils.hasRole("CGEA")).thenReturn(false);
        when(securityUtils.hasRole("ADMIN_DDIC")).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());

        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();

        assertThatThrownBy(() -> guard.checkReadAccess(dossier))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=DossierAccessGuardTest`
Expected: compile error — `DossierAccessGuard` has no constructor accepting a `DossierHabilitationRepository` yet, and its current logic doesn't call `habilitationRepository`.

- [ ] **Step 3: Rewrite `checkReadAccess`**

Replace the full content of `DossierAccessGuard.java`:

```java
package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Contrôle d'accès partagé pour un dossier et ses sous-ressources (témoins,
 * parties visées, notifications, pièces jointes...) : un rôle privilégié
 * (CGE/CGEA/ADMIN_DDIC) voit tout ; les autres agents doivent disposer d'une
 * habilitation nominative active sur ce dossier (DossierHabilitation).
 */
@Component
@RequiredArgsConstructor
public class DossierAccessGuard {

    private final DossierRepository             dossierRepository;
    private final AgentRepository               agentRepository;
    private final SecurityUtils                 securityUtils;
    private final DossierHabilitationRepository habilitationRepository;

    public Dossier getDossierOrThrow(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));
    }

    public boolean canSeeConfidential() {
        return securityUtils.hasRole("CGE")
                || securityUtils.hasRole("CGEA")
                || securityUtils.hasRole("ADMIN_DDIC");
    }

    /** Lève BusinessException si l'agent courant n'est ni privilégié ni habilité sur ce dossier. */
    public void checkReadAccess(Dossier dossier) {
        if (canSeeConfidential()) {
            return;
        }
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        Agent agent = agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));
        boolean hasAccess = habilitationRepository
                .existsByDossierIdAndAgentIdAndRevokedAtIsNull(dossier.getId(), agent.getId());
        if (!hasAccess) {
            throw new BusinessException(
                    "Accès refusé — ce dossier ne vous est pas assigné");
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=DossierAccessGuardTest`
Expected: all 4 tests `PASS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuard.java src/test/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuardTest.java
git commit -m "feat: base DossierAccessGuard.checkReadAccess on habilitation instead of agentInCharge"
```

---

### Task 5: Wire into `DossierServiceImpl`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java` (already exists — extend it)

**Interfaces:**
- Consumes: `DossierHabilitationService.grant` (Task 2), `DossierAccessGuard.checkReadAccess`/`canSeeConfidential` (Task 4), `DossierRepository.findAccessibleByAgentId` (Task 1).

**Read `DossierServiceImplTest.java` first** — it already exists (added by a prior plan) with `@Mock` fields for `dossierRepository, declarantRepository, notificationRepository, observationRepository, dossierMapper, dossierDetailsMapper, declarantMapper, accessCodeGenerator, securityUtils, notificationDispatcher, agentContextResolver, auditRecorder, parametreDelaiService, natureSaisineResolver` and an `@InjectMocks DossierServiceImpl service` field, plus existing tests for `submit()`. Add two new `@Mock` fields (`DossierAccessGuard accessGuard`, `DossierHabilitationService habilitationService`) to the existing class — Mockito's `@InjectMocks` will pick them up automatically — and add the new test methods below without modifying the existing ones.

- [ ] **Step 1: Write the failing tests**

Add these methods to the existing `DossierServiceImplTest` class (after adding the two new `@Mock` fields described above):

```java
    @Test
    void findById_delegatesAccessCheckToGuard() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(DossierResponse.builder().build());

        service.findById(dossierId);

        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void findById_propagatesGuardRejection() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.findById(dossierId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findAll_usesAccessibleDossiersForNonPrivilegedAgent() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Pageable pageable = PageRequest.of(0, 20);

        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(dossierRepository.findAccessibleByAgentId(agent.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.findAll(pageable);

        verify(dossierRepository).findAccessibleByAgentId(agent.getId(), pageable);
        verify(dossierRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void findAll_usesFindAllForPrivilegedAgent() {
        Pageable pageable = PageRequest.of(0, 20);

        when(accessGuard.canSeeConfidential()).thenReturn(true);
        when(dossierRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        service.findAll(pageable);

        verify(dossierRepository).findAll(pageable);
        verify(dossierRepository, never()).findAccessibleByAgentId(any(), any());
    }
```

Add the necessary new imports to the test file if not already present: `gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard`, `gov.bf.ascelc.univers_audits.service.DossierHabilitationService`, `org.springframework.data.domain.PageImpl`, `org.springframework.data.domain.PageRequest`, `org.mockito.ArgumentMatchers.any` (if not already statically imported), `java.util.List`, `java.util.Optional`. Check the existing file's imports before adding — several of these (`UUID`, `Optional`, `BusinessException`, `Mockito.*`, `assertThatThrownBy`) are almost certainly already there from the existing `submit()` tests.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=DossierServiceImplTest`
Expected: compile errors — `DossierServiceImpl` has no `accessGuard`/`habilitationService` fields yet, `DossierRepository.findAccessibleByAgentId` is unused in `findAll`, and `findById`/`findAll` don't yet call the guard.

- [ ] **Step 3: Add the two new fields**

Add the import (alongside the other field-type imports):

```java
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
```

Add to the field list (after `private final NatureSaisineResolver natureSaisineResolver;`):

```java
    private final DossierAccessGuard            accessGuard;
    private final DossierHabilitationService    habilitationService;
```

(`DossierAccessGuard` is in `gov.bf.ascelc.univers_audits.shared.utils`, already imported via the existing `AccessCodeGenerator`/`AgentContextResolver`/etc. import lines in that package — add `import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;` alongside them.)

- [ ] **Step 4: Simplify `findById`**

Replace:

```java
    @Override
    public DossierResponse findById(UUID id) {
        Dossier dossier = getDossierOrThrow(id);
        logSensitiveAccessIfProtected(dossier, "findById");

        boolean isAdmin = securityUtils.hasRole("ADMIN_DDIC");
        boolean isCge   = securityUtils.hasRole("CGE");
        boolean isCgea  = securityUtils.hasRole("CGEA");

        if (!isAdmin && !isCge && !isCgea) {
            Agent agent = agentContextResolver.getCurrentAgent();
            boolean isAssigned = dossier.getAgentInCharge() != null
                    && dossier.getAgentInCharge().getId().equals(agent.getId());
            if (!isAssigned) {
                throw new BusinessException(
                        "Accès refusé — ce dossier ne vous est pas assigné");
            }
        }

        return enrichAndMaskDetail(dossier);
    }
```

with:

```java
    @Override
    public DossierResponse findById(UUID id) {
        Dossier dossier = getDossierOrThrow(id);
        logSensitiveAccessIfProtected(dossier, "findById");
        accessGuard.checkReadAccess(dossier);
        return enrichAndMaskDetail(dossier);
    }
```

- [ ] **Step 5: Simplify `findAll`**

Replace:

```java
    @Override
    public Page<DossierResponse> findAll(Pageable pageable) {
        boolean isAdmin = securityUtils.hasRole("ADMIN_DDIC");
        boolean isCge   = securityUtils.hasRole("CGE");
        boolean isCgea  = securityUtils.hasRole("CGEA");

        if (isAdmin || isCge || isCgea) {
            return dossierRepository.findAll(pageable)
                    .map(this::enrichAndMask);
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        log.debug("[Dossiers] Accès restreint — agent: {} voit uniquement ses dossiers",
                agent.getMatricule());
        return dossierRepository
                .findByAgentInChargeId(agent.getId(), pageable)
                .map(this::enrichAndMask);
    }
```

with:

```java
    @Override
    public Page<DossierResponse> findAll(Pageable pageable) {
        if (accessGuard.canSeeConfidential()) {
            return dossierRepository.findAll(pageable)
                    .map(this::enrichAndMask);
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        log.debug("[Dossiers] Accès restreint — agent: {} voit uniquement ses dossiers habilités",
                agent.getMatricule());
        return dossierRepository
                .findAccessibleByAgentId(agent.getId(), pageable)
                .map(this::enrichAndMask);
    }
```

- [ ] **Step 6: Grant `AGENT_IN_CHARGE` in `registerReception`**

In `registerReception`, replace:

```java
        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.RECU);
        Agent agent = agentContextResolver.getCurrentAgent();

        String number = generateUniqueNumber();
        dossier.setNumber(number);
        int accuseReceptionJours = parametreDelaiService
                .resolveDelaiJours("ACCUSE_RECEPTION");
        int demandeComplementJours = parametreDelaiService
                .resolveDelaiJours("DEMANDE_COMPLEMENT");
        dossier.registerReception(agent, accuseReceptionJours, demandeComplementJours);
```

with:

```java
        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.RECU);
        Agent agent = agentContextResolver.getCurrentAgent();

        String number = generateUniqueNumber();
        dossier.setNumber(number);
        int accuseReceptionJours = parametreDelaiService
                .resolveDelaiJours("ACCUSE_RECEPTION");
        int demandeComplementJours = parametreDelaiService
                .resolveDelaiJours("DEMANDE_COMPLEMENT");
        dossier.registerReception(agent, accuseReceptionJours, demandeComplementJours);

        habilitationService.grant(dossier, agent, HabilitationSource.AGENT_IN_CHARGE,
                agent, "Agent en charge du dossier (enregistrement BRPD)");
```

(`HabilitationSource` is already covered by the existing `import gov.bf.ascelc.univers_audits.enums.*;` wildcard import at the top of this file — no new import needed.)

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn -q test -Dtest=DossierServiceImplTest`
Expected: all tests in this file `PASS` (the pre-existing `submit()` tests plus the new ones).

- [ ] **Step 8: Run the full suite once**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, only the known pre-existing baseline failure `UniversAuditsApplicationTests.contextLoads` (no live datasource in this sandbox — unrelated to this change).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java
git commit -m "feat: grant AGENT_IN_CHARGE habilitation on registration, use guard for findById/findAll"
```

---

### Task 6: Wire into `InvestigationServiceImpl`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java` (new)

**Interfaces:**
- Consumes: `DossierHabilitationService.grant`/`revokeBySource` (Task 2).

No test file exists yet for `InvestigationServiceImpl` (a ~660-line class covering much more than this task touches). This new test file covers **only** the habilitation side-effect of `addMember`/`removeMember` — not the rest of the class's existing untested surface, which predates this plan and is out of scope.

- [ ] **Step 1: Write the failing test**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.enums.TeamRole;
import gov.bf.ascelc.univers_audits.mapper.InvestigationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AddMemberRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.InvestigationMember;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import gov.bf.ascelc.univers_audits.service.EmailService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestigationServiceImplTest {

    @Mock private InvestigationRepository       investigationRepository;
    @Mock private InvestigationMemberRepository memberRepository;
    @Mock private DossierRepository             dossierRepository;
    @Mock private AgentRepository               agentRepository;
    @Mock private NotificationRepository        notificationRepository;
    @Mock private EmailService                  emailService;
    @Mock private InvestigationMapper           investigationMapper;
    @Mock private SecurityUtils                 securityUtils;
    @Mock private AgentContextResolver          agentContextResolver;
    @Mock private DossierAuditRecorder          auditRecorder;
    @Mock private ParametreDelaiService         parametreDelaiService;
    @Mock private DossierHabilitationService    habilitationService;

    @InjectMocks
    private InvestigationServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
    }

    @Test
    void addMember_grantsInvestigationTeamHabilitation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigation.getId(), agent.getId())).thenReturn(false);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(memberRepository.findFirstByInvestigationIdAndAgentIdOrderByCreatedAtDesc(
                investigation.getId(), agent.getId())).thenReturn(Optional.empty());
        when(memberRepository.save(any(InvestigationMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        AddMemberRequest request = AddMemberRequest.builder()
                .agentId(agent.getId()).teamRole(TeamRole.MEMBER).build();

        service.addMember(investigation.getId(), request, "127.0.0.1");

        verify(habilitationService).grant(dossier, agent, HabilitationSource.INVESTIGATION_TEAM,
                currentAgent, "Membre de l'équipe d'investigation");
    }

    @Test
    void removeMember_revokesInvestigationTeamHabilitation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-current").build();

        InvestigationMember member = InvestigationMember.builder()
                .investigation(investigation).agent(agent)
                .teamRole(TeamRole.MEMBER).active(true).build();
        investigation.getMembers().add(member);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(memberRepository.save(any(InvestigationMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.removeMember(investigation.getId(), agent.getId(), "127.0.0.1");

        verify(habilitationService).revokeBySource(
                dossier, agent, HabilitationSource.INVESTIGATION_TEAM);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=InvestigationServiceImplTest`
Expected: compile error — `InvestigationServiceImpl` has no `habilitationService` field yet, and `addMember`/`removeMember` never call it.

- [ ] **Step 3: Wire the calls in**

Add the import:

```java
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
```

Add the field (after `private final ParametreDelaiService parametreDelaiService;`):

```java
    private final DossierHabilitationService habilitationService;
```

In `addMember`, right after the `if (existing.isPresent()) { ... } else { ... }` block and before `auditRecorder.addObservation(...)`, insert:

```java
        habilitationService.grant(inv.getDossier(), agent, HabilitationSource.INVESTIGATION_TEAM,
                currentAgent, "Membre de l'équipe d'investigation");
```

In `removeMember`, right after `memberRepository.save(member);` and before `auditRecorder.addObservation(...)`, insert:

```java
        habilitationService.revokeBySource(inv.getDossier(), member.getAgent(),
                HabilitationSource.INVESTIGATION_TEAM);
```

(`HabilitationSource` is already covered by the existing `import gov.bf.ascelc.univers_audits.enums.*;` wildcard import at the top of this file.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=InvestigationServiceImplTest`
Expected: both tests `PASS`.

- [ ] **Step 5: Run the full suite once**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, only the known pre-existing baseline failure `UniversAuditsApplicationTests.contextLoads`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: grant/revoke INVESTIGATION_TEAM habilitation on team membership changes"
```

---

## Manual follow-up (not automatable in this plan)

- No Testcontainers/DB-integration test harness exists in this repo, so the Liquibase migration (Task 1, Step 5), including its backfill, is not exercised by an automated test. Before deploying: run against a copy of the staging/dev database and confirm every dossier that had a non-null `agent_in_charge_id` and every active `investigation_member` now has a corresponding active `dossier_habilitation` row — e.g. `SELECT count(*) FROM dossier WHERE agent_in_charge_id IS NOT NULL AND id NOT IN (SELECT dossier_id FROM dossier_habilitation WHERE source = 'AGENT_IN_CHARGE' AND revoked_at IS NULL);` should return 0.
- The frontend (separate repository) will need a screen for the new manual grant/revoke endpoints if CGE/CGEA are expected to use that capability day one — not part of this backend plan.
- Write-side access control (state-transition endpoints) is explicitly out of scope — see Global Constraints. Flag as the next follow-up chantier once this ships.
