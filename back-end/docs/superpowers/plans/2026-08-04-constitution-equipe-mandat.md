# Constitution d'équipe + Mandat (Lot 3, sous-chantier 1/6) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `TeamRole`'s two-value model with the four roles the plan de travail
requires, enforce the full team-composition rule in `Investigation.start()`, and add a
`Mandat` entity + endpoints so the CGE can formally deliver a mandate before an
investigation can start.

**Architecture:** A renaming Liquibase migration widens `investigation_member.team_role`
and remaps existing data (`TEAM_LEADER→CHEF_MISSION`, `MEMBER→INVESTIGATEUR`). A new
`Mandat` entity (1:1 `Investigation`, following the `DecisionCGE` pattern — `@ManyToOne`
unique FK + a dedicated repository, no bidirectional relation on `Investigation`) is
created via a new `POST /api/v1/investigations/{id}/mandat` endpoint and read via
`GET /api/v1/investigations/{id}/mandat`. `InvestigationServiceImpl.start()` gains a
private `validateTeamComposition()` helper (also used by `deliverMandat()`) and a
mandat-exists check.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL),
Lombok `@SuperBuilder`, JUnit 5 + Mockito.

## Global Constraints

- `TeamRole` becomes `{CHEF_MISSION, INVESTIGATEUR, PERSONNE_RESSOURCE, CONSEIL_JURIDIQUE}` —
  `TEAM_LEADER`/`MEMBER` are removed, not kept alongside the new values.
- Migration remaps existing data: `TEAM_LEADER → CHEF_MISSION`, `MEMBER → INVESTIGATEUR`.
- `investigation_member.team_role` column widens from `VARCHAR(15)` to `VARCHAR(20)`
  (longest new value `PERSONNE_RESSOURCE` = 18 chars).
- `start()` composition rule: exactly 1 `CHEF_MISSION`, ≥2 `INVESTIGATEUR`, exactly 1
  `CONSEIL_JURIDIQUE`, `PERSONNE_RESSOURCE` unconstrained (0+).
- No department check on chef de mission — role only (explicit user decision, see spec
  `docs/superpowers/specs/2026-08-04-constitution-equipe-mandat-design.md`).
- `Mandat.agentCGE` is always `agentContextResolver.getCurrentAgent()` — no agent-id
  request field (mirrors `approveLegalAdvisor`/`approveCge`'s existing pattern for
  "current signing agent").
- `open()` is NOT touched in this sub-chantier — the `EQUIPE_CONSTITUEE`/`PLAN_VALIDE`
  gate is sub-chantier 6/6.
- `InvestigationServiceImpl` uses `@RequiredArgsConstructor` with a manually-ordered
  field list; `InvestigationServiceImplTest` uses `@InjectMocks` (no manual positional
  constructor), so new fields can be appended at the end safely.
- `AuditEntity` columns for any new table: `id UUID PRIMARY KEY`,
  `version BIGINT NOT NULL DEFAULT 0`, `created_at TIMESTAMP NOT NULL`,
  `updated_at TIMESTAMP`, `created_by_id VARCHAR(100)`, `updated_by_id VARCHAR(100)`.
- Next migration file number is `019` (last is `018-add-decision-cge.sql`).

---

### Task 1: `TeamRole` rename + migration 019 + `InvestigationMember` column width

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/enums/TeamRole.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/InvestigationMember.java:33`
- Create: `src/main/resources/db/changelog/migrations/019-team-role-rename-and-mandat.sql`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/mapper/InvestigationMapperTest.java`
  (lines 4, 50, 56 — `TeamRole.MEMBER` → `TeamRole.INVESTIGATEUR`)
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`
  (lines 4, 81, 98, 133, 152 — `TeamRole.MEMBER` → `TeamRole.INVESTIGATEUR`)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java:610`
  (role-label switch, see Task 3 — do NOT touch here, listed for awareness only)

**Interfaces:**
- Produces: `TeamRole` enum with values `CHEF_MISSION, INVESTIGATEUR, PERSONNE_RESSOURCE,
  CONSEIL_JURIDIQUE` — every later task in this plan uses these exact names.
- Produces: `mandat` table (created by this migration, consumed by Task 2's `Mandat` entity).

- [ ] **Step 1: Rename the enum**

Replace the full contents of `TeamRole.java`:

```java
package gov.bf.ascelc.univers_audits.enums;

/**
 * Rôle d'un agent au sein d'une équipe d'investigation.
 */
public enum TeamRole {
    // Coordonne l'équipe, signataire du rapport final — exactement 1 par équipe
    CHEF_MISSION,
    // Participe à l'enquête terrain — au moins 2 par équipe
    INVESTIGATEUR,
    // Apporte une expertise ponctuelle — nombre libre (0 ou plus)
    PERSONNE_RESSOURCE,
    // Garantit la conformité juridique de la procédure — exactement 1 par équipe
    CONSEIL_JURIDIQUE
}
```

- [ ] **Step 2: Widen the `team_role` column mapping**

In `InvestigationMember.java:33`, change:

```java
    @Column(name = "team_role", nullable = false, length = 15)
```

to:

```java
    @Column(name = "team_role", nullable = false, length = 20)
```

- [ ] **Step 3: Write migration 019**

Create `src/main/resources/db/changelog/migrations/019-team-role-rename-and-mandat.sql`:

```sql
--liquibase formatted sql
--changeset dev:019-team-role-rename-and-mandat

ALTER TABLE investigation_member ALTER COLUMN team_role TYPE VARCHAR(20);

UPDATE investigation_member SET team_role = 'CHEF_MISSION' WHERE team_role = 'TEAM_LEADER';
UPDATE investigation_member SET team_role = 'INVESTIGATEUR' WHERE team_role = 'MEMBER';

CREATE TABLE mandat (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    investigation_id  UUID NOT NULL UNIQUE REFERENCES investigation(id),
    date_delivrance   TIMESTAMP NOT NULL,
    agent_cge_id      UUID NOT NULL REFERENCES agent(id)
);

COMMENT ON TABLE mandat IS 'Mandat delivre par le CGE avant le demarrage effectif d une investigation (Lot 3, plan de travail S11) - un par investigation, prealable obligatoire a start()';
```

This migration is auto-discovered by `db.changelog-master.yaml`'s `includeAll` on
`db/changelog/migrations/` — no changelog master edit needed.

- [ ] **Step 4: Update `InvestigationMapperTest.java`**

Replace all 3 occurrences of `TeamRole.MEMBER` with `TeamRole.INVESTIGATEUR` (lines 50, 56;
the import at line 4 stays as-is since the type name `TeamRole` is unchanged).

- [ ] **Step 5: Update `InvestigationServiceImplTest.java`**

Replace all 4 occurrences of `TeamRole.MEMBER` with `TeamRole.INVESTIGATEUR` (lines 81, 98,
133, 152).

- [ ] **Step 6: Compile and run existing tests**

Run: `mvn -q -pl . compile test -Dtest=InvestigationMapperTest,InvestigationServiceImplTest`
Expected: BUILD SUCCESS — these tests exercise `addMember`/`removeMember` only, unaffected
by the composition-rule changes coming in Task 3.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/TeamRole.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/InvestigationMember.java \
        src/main/resources/db/changelog/migrations/019-team-role-rename-and-mandat.sql \
        src/test/java/gov/bf/ascelc/univers_audits/mapper/InvestigationMapperTest.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: rename TeamRole to 4 roles, widen column, add mandat table"
```

---

### Task 2: `Mandat` entity, repository, response DTO

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Mandat.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/MandatRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MandatResponse.java`

**Interfaces:**
- Consumes: `Investigation` entity (`model/entity/Investigation.java`), `Agent` entity
  (`model/entity/Agent.java`, has `getNomComplet()`), `AuditEntity` base class
  (`abstracts/AuditEntity.java`).
- Produces: `Mandat` entity with fields `investigation` (`Investigation`), `dateDelivrance`
  (`Instant`), `agentCGE` (`Agent`) — consumed by Task 3's service methods.
- Produces: `MandatRepository.findByInvestigationId(UUID): Optional<Mandat>` — consumed by
  Task 3.
- Produces: `MandatResponse` DTO with fields `id, investigationId, dateDelivrance,
  agentCGEId, agentCGENom` — consumed by Task 3 (built manually, no mapper — mirrors the
  `DecisionCGEResponse` precedent which also has no dedicated mapper) and Task 4
  (controller return type).

- [ ] **Step 1: Create the `Mandat` entity**

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
@Table(name = "mandat", indexes = {
        @Index(name = "idx_mandat_investigation",
                columnList = "investigation_id", unique = true)
})
public class Mandat extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "date_delivrance", nullable = false)
    private Instant dateDelivrance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cge_id", nullable = false)
    private Agent agentCGE;
}
```

- [ ] **Step 2: Create `MandatRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Mandat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MandatRepository extends JpaRepository<Mandat, UUID> {

    Optional<Mandat> findByInvestigationId(UUID investigationId);
}
```

- [ ] **Step 3: Create `MandatResponse`**

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
public class MandatResponse {

    private UUID id;
    private UUID investigationId;
    private Instant dateDelivrance;
    private UUID agentCGEId;
    private String agentCGENom;
}
```

- [ ] **Step 4: Compile**

Run: `mvn -q -pl . compile`
Expected: BUILD SUCCESS (new classes only, nothing consumes them yet).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/Mandat.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/MandatRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MandatResponse.java
git commit -m "feat: add Mandat entity, repository and response DTO"
```

---

### Task 3: Composition rule in `start()`, `deliverMandat`/`getMandat` service methods

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/InvestigationMemberRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `Mandat`/`MandatRepository`/`MandatResponse` (Task 2), `TeamRole` 4-value enum
  (Task 1).
- Produces: `InvestigationService.deliverMandat(UUID investigationId, String ipAddress):
  MandatResponse` and `InvestigationService.getMandat(UUID investigationId): MandatResponse`
  — consumed by Task 4's controller.
- Produces: `InvestigationMemberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
  UUID investigationId, TeamRole teamRole): long`.

- [ ] **Step 1: Add the count method to `InvestigationMemberRepository`**

In `InvestigationMemberRepository.java`, add this method right after
`countByInvestigationIdAndActiveTrue` (after line 30):

```java

    long countByInvestigationIdAndTeamRoleAndActiveTrue(
            UUID investigationId, TeamRole teamRole);
```

- [ ] **Step 2: Add `MandatRepository` field to `InvestigationServiceImpl`**

In `InvestigationServiceImpl.java`, add a new field at the end of the existing field list
(after line 53, `private final PortalConfigService portalConfigService;`):

```java
    private final MandatRepository               mandatRepository;
```

Add the import alongside the existing `repository.*` wildcard import — it is already
covered by `import gov.bf.ascelc.univers_audits.repository.*;` (line 12), no new import
line needed. Also add:

```java
import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;
import gov.bf.ascelc.univers_audits.model.entity.Mandat;
```

next to the existing `model.dto.response.InvestigationResponse` / `model.entity.*` imports.

- [ ] **Step 3: Add `validateTeamComposition` private helper**

Add this method in the "MÉTHODES PRIVÉES" section, directly before `getInvestigationOrThrow`
(before line 670):

```java
    private void validateTeamComposition(UUID investigationId) {
        long chefMission = memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigationId, TeamRole.CHEF_MISSION);
        if (chefMission != 1) {
            throw new BusinessException(
                    "L'équipe doit compter exactement un chef de mission (trouvé : "
                            + chefMission + ").");
        }

        long investigateurs = memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigationId, TeamRole.INVESTIGATEUR);
        if (investigateurs < 2) {
            throw new BusinessException(
                    "L'équipe doit compter au moins deux investigateurs (trouvé : "
                            + investigateurs + ").");
        }

        long conseilJuridique = memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigationId, TeamRole.CONSEIL_JURIDIQUE);
        if (conseilJuridique != 1) {
            throw new BusinessException(
                    "L'équipe doit compter exactement un conseil juridique (trouvé : "
                            + conseilJuridique + ").");
        }
    }
```

- [ ] **Step 4: Replace the `start()` member-count check with the full rule**

In `start()` (lines 199-224), replace:

```java
        long memberCount = memberRepository
                .countByInvestigationIdAndActiveTrue(investigationId);

        if (memberCount == 0) {
            throw new BusinessException(
                    "L'équipe d'investigation doit avoir "
                            + "au moins un membre avant le démarrage");
        }
```

with:

```java
        validateTeamComposition(investigationId);

        if (mandatRepository.findByInvestigationId(investigationId).isEmpty()) {
            throw new BusinessException(
                    "Aucun mandat n'a été délivré par le CGE pour cette investigation.");
        }
```

- [ ] **Step 5: Add `toMandatResponse` private helper**

Add directly after the new `validateTeamComposition` helper from Step 3:

```java
    private MandatResponse toMandatResponse(Mandat mandat) {
        return MandatResponse.builder()
                .id(mandat.getId())
                .investigationId(mandat.getInvestigation().getId())
                .dateDelivrance(mandat.getDateDelivrance())
                .agentCGEId(mandat.getAgentCGE().getId())
                .agentCGENom(mandat.getAgentCGE().getNomComplet())
                .build();
    }
```

- [ ] **Step 6: Add `deliverMandat` and `getMandat` to `InvestigationService` interface**

In `InvestigationService.java`, add after `removeMember` (after line 67, before the closing
`}`):

```java

    MandatResponse deliverMandat(UUID investigationId, String ipAddress);

    MandatResponse getMandat(UUID investigationId);
```

Add the import: `import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;`

- [ ] **Step 7: Implement `deliverMandat` and `getMandat` in `InvestigationServiceImpl`**

Add after `removeMember` (after line 582, before `buildResponseWithFreshMembers`):

```java
    @Override
    @Transactional
    public MandatResponse deliverMandat(UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (mandatRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Un mandat a déjà été délivré pour cette investigation.");
        }

        validateTeamComposition(investigationId);

        Agent cge = agentContextResolver.getCurrentAgent();
        Mandat mandat = Mandat.builder()
                .investigation(inv)
                .dateDelivrance(Instant.now())
                .agentCGE(cge)
                .build();
        Mandat saved = mandatRepository.save(mandat);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Mandat délivré par le CGE — signataire : " + cge.getNomComplet(),
                true, cge);

        log.info("Mandat délivré — investigation: {}", investigationId);
        return toMandatResponse(saved);
    }

    @Override
    public MandatResponse getMandat(UUID investigationId) {
        Mandat mandat = mandatRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun mandat pour cette investigation : " + investigationId));
        return toMandatResponse(mandat);
    }

```

- [ ] **Step 8: Fix `sendMemberAddedNotifications`'s role label for 4 roles**

In `sendMemberAddedNotifications` (around line 604-611), replace:

```java
        String  roleLabel     = TeamRole.TEAM_LEADER.equals(teamRole)
                ? "Chef de mission" : "Investigateur";
```

with:

```java
        String roleLabel = switch (teamRole) {
            case CHEF_MISSION -> "Chef de mission";
            case INVESTIGATEUR -> "Investigateur";
            case PERSONNE_RESSOURCE -> "Personne ressource";
            case CONSEIL_JURIDIQUE -> "Conseil juridique";
        };
```

- [ ] **Step 9: Write the failing tests for `start()`'s composition rule**

Add to `InvestigationServiceImplTest.java`, after `addMember_reactivationBranchStillGrantsInvestigationTeamHabilitation`
(before the closing `}` at line 160):

```java

    @Test
    void start_rejectsWhenNoChefDeMission() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(0L);

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chef de mission");
    }

    @Test
    void start_rejectsWhenOnlyOneInvestigateur() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(1L);

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("investigateurs");
    }

    @Test
    void start_rejectsWhenNoConseilJuridique() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(0L);

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("conseil juridique");
    }

    @Test
    void start_rejectsWhenCompositionValidButNoMandat() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mandat");
    }

    @Test
    void start_succeedsWithFullCompositionAndMandat() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setPlannedDurationDays(30);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID())
                        .investigation(investigation).agentCGE(cge)
                        .dateDelivrance(java.time.Instant.now()).build()));
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(agentContextResolver.getCurrentAgent()).thenReturn(cge);

        service.start(investigation.getId(), "127.0.0.1");

        assertThat(investigation.getStatus())
                .isEqualTo(gov.bf.ascelc.univers_audits.enums.InvestigationStatus.IN_PROGRESS);
    }

    @Test
    void deliverMandat_rejectsWhenAlreadyDelivered() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.deliverMandat(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été délivré");
    }

    @Test
    void deliverMandat_rejectsWhenCompositionIncomplete() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(0L);

        assertThatThrownBy(() -> service.deliverMandat(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chef de mission");
    }

    @Test
    void deliverMandat_succeedsWithFullComposition() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(agentContextResolver.getCurrentAgent()).thenReturn(cge);
        when(mandatRepository.save(any(Mandat.class)))
                .thenAnswer(inv -> {
                    Mandat m = inv.getArgument(0);
                    m.setId(UUID.randomUUID());
                    return m;
                });

        MandatResponse response = service.deliverMandat(investigation.getId(), "127.0.0.1");

        assertThat(response.getAgentCGEId()).isEqualTo(cge.getId());
        assertThat(response.getInvestigationId()).isEqualTo(investigation.getId());
    }
```

Add these imports at the top of `InvestigationServiceImplTest.java` alongside the existing
ones:

```java
import gov.bf.ascelc.univers_audits.enums.TeamRole;
import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;
import gov.bf.ascelc.univers_audits.model.entity.Mandat;
import gov.bf.ascelc.univers_audits.repository.MandatRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
```

and add the mock field, after `@Mock private PortalConfigService portalConfigService;`
(line 49):

```java
    @Mock private MandatRepository               mandatRepository;
```

and the static import:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 10: Run the tests to verify they pass**

Run: `mvn -q -pl . test -Dtest=InvestigationServiceImplTest`
Expected: BUILD SUCCESS, all tests (existing + 8 new) pass.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/repository/InvestigationMemberRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: enforce team composition rule and add mandat delivery in InvestigationServiceImpl"
```

---

### Task 4: Controller endpoints

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java`

**Interfaces:**
- Consumes: `InvestigationService.deliverMandat`/`getMandat` (Task 3).
- Produces: `POST /api/v1/investigations/{id}/mandat`, `GET /api/v1/investigations/{id}/mandat`
  — terminal, nothing else in this plan depends on these.

- [ ] **Step 1: Add the import**

Add alongside the existing `model.dto.response.InvestigationResponse` import:

```java
import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;
```

- [ ] **Step 2: Add the two endpoints**

Add a new section after `// ── Équipe ────` block's `removeMember` method, before the
closing `}` of the class (after line 327):

```java

    // ── Mandat ────────────────────────────────────────────────

    @PostMapping("/{id}/mandat")
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<MandatResponse> deliverMandat(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Délivrance mandat — investigation {}", id);
        MandatResponse result = investigationService.deliverMandat(
                id, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "DELIVRER_MANDAT", "INVESTIGATION", id.toString(),
                "Délivrance du mandat CGE", AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/mandat")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<MandatResponse> getMandat(@PathVariable UUID id) {
        return ResponseEntity.ok(investigationService.getMandat(id));
    }
```

- [ ] **Step 3: Compile and run the full test suite**

Run: `mvn -q -pl . test`
Expected: BUILD SUCCESS, no regressions anywhere in the suite.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java
git commit -m "feat: add mandat delivery and read endpoints to InvestigationController"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** §1 (TeamRole rename) → Task 1. §2 (composition rule) → Task 3 Steps
  3-4. §3 (Mandat entity) → Task 2. §4 (API: POST mandat, start() rejects without mandat)
  → Task 3 Steps 4/7, Task 4. Hors périmètre items (département check, réémission,
  conflit d'intérêts, `open()` gate) are correctly not implemented anywhere in this plan.
- **Type consistency verified:** `TeamRole` values used identically across Tasks 1, 3;
  `MandatResponse` field names identical across Task 2's definition and Task 3/4's usage;
  `MandatRepository.findByInvestigationId` signature identical across Task 2 definition
  and Task 3 usage.
- **GET /mandat roles** mirror the existing `GET /{id}` investigation read roles exactly
  (`CGEA, CGE, CONTROLEUR_ETAT, MEMBRE_CTADP, ADMIN_DDIC`) — no new role introduced.
