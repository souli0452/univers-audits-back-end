# Règles métier manquantes sur Audition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the five missing business rules on `Audition` from Lot 4 §4 of the
plan de travail — mandated interview order (non-blocking alert), a blocking ≥2-investigator
precondition, a non-blocking second-interview warning for the mis en cause, a fix for the
unreachable `AuditionStatus.NO_SHOW`, and a naming cleanup on the sibling `VisiteTerrain`
module.

**Architecture:** `Audition.conductedBy` (single `Agent`) is replaced by a proper
`investigators` collection (`@ManyToMany`) to support the "at least two" rule. A new
`IntervieweeType.DECLARANT` value and a new `Witness.possiblyImplicated` flag make the
4-step mandated order (dénonciateur → témoin non impliqué → témoin possiblement impliqué →
mis en cause) representable and computable. Order and second-interview checks are
non-blocking — they populate warning fields on the response rather than rejecting the
request, matching the plan de travail's own "alerte"/"déconseillée" wording, in contrast
to the investigator-count check which is a real blocking precondition ("contrôle").

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, MapStruct 1.5.5.Final,
Liquibase (formatted SQL), Lombok `@SuperBuilder`, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Order ranking: `DECLARANT`=0, `WITNESS(possiblyImplicated=false)`=1,
  `WITNESS(possiblyImplicated=true)`=2, `TARGETED_PARTY`=3.
- Order warning (`AuditionResponse.orderWarning`) is computed at `conduct()`, not
  `schedule()` — several auditions can legitimately be pre-scheduled out of order for
  logistics; what matters is the actual sequence in which they are *held*. **Never
  blocks** — `conduct()` always succeeds regardless of this warning.
- Second-audition warning (`AuditionResponse.secondAuditionWarning`) is computed at
  `schedule()`, only when `intervieweeType == TARGETED_PARTY` and a `CONDUCTED` audition
  already exists in the same investigation for the same `targetedPartyId`. Never computed
  for `WITNESS`/`DECLARANT` — the plan de travail text only calls out the mis en cause as
  "déconseillée". **Never blocks** — `schedule()` always succeeds regardless.
- Investigator-count check (`AuditionScheduleRequest.investigatorIds`, `@Size(min = 2)`)
  is a **blocking** precondition, enforced once at `schedule()` via Bean Validation —
  never re-verified at `conduct()`/`cancel()`/`markNoShow()`.
- `IntervieweeType.DECLARANT` has **no dedicated FK** on `Audition` — the declarant is
  already unique per dossier (`Dossier.declarant`), resolved live via
  `investigation.getDossier().getDeclarant()`. `schedule()` rejects `DECLARANT` with a
  `targetedPartyId`/`witnessId` set, and rejects it entirely if the dossier has no
  declarant (anonymous dossier).
- `Witness.possiblyImplicated` defaults to `false`, mutable at any time via
  `WitnessRequest`/`WitnessController.update` (not fixed at creation) — reflects the
  investigation's evolving understanding.
- `Audition.noShowNote` is a **separate** field from `cancellationReason` — same
  rationale as `VisiteTerrain`'s distinct `cancellationReason`/`carenceReason`: a
  cancellation is a decision made ahead of time, a no-show is a fact observed on the day.
  Never reuse one field for two distinct outcomes.
- `VisiteTerrain.conductedBy` → `plannedBy` is a **Java-level rename only** — the
  database column stays `conducted_by_id` (never exposed through the JSON API), so **no
  migration** is needed for this rename.
- Migration numbering: `026` for the `Witness` change (Task 1), `027` for the `Audition`
  change (Task 2) — assigned in task order since Task 2 depends on Task 1's
  `Witness.possiblyImplicated` field already existing (used by `orderRank()`'s WITNESS
  branch). Confirmed via `ls src/main/resources/db/changelog/migrations` that the latest
  existing migration is `025-create-visite-terrain-pv-constat.sql`.
- No new roles introduced anywhere — `AuditionController`'s existing `READ_ROLES`/
  `WRITE_ROLES` constants are reused unchanged for the new `markNoShow` endpoint.

---

### Task 1: `Witness.possiblyImplicated`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Witness.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/WitnessRequest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/WitnessResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/WitnessServiceImpl.java`
- Create: `src/main/resources/db/changelog/migrations/026-add-witness-possibly-implicated.sql`
- Create: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/WitnessServiceImplTest.java`

**Interfaces:**
- Produces: `Witness.possiblyImplicated: Boolean` (default `false`), `Witness.isPossiblyImplicated(): boolean` — consumed by Task 2's `AuditionServiceImpl.orderRank()`.
- Produces: `WitnessRequest.possiblyImplicated`, `WitnessResponse.possiblyImplicated` — no other task consumes these directly (API-facing only).

- [ ] **Step 1: Add the field to `Witness`**

In `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Witness.java`, add after the
existing `anonymous` field (after the `isAnonymous()` method, before `getDisplayName()`):

```java
    @Column(name = "possibly_implicated", nullable = false)
    @Builder.Default
    private Boolean possiblyImplicated = false;
```

And add a helper method next to the existing `isAnonymous()`/`hasConsented()` helpers:

```java
    public boolean isPossiblyImplicated() {
        return Boolean.TRUE.equals(possiblyImplicated);
    }
```

- [ ] **Step 2: Add the field to `WitnessRequest`**

In `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/WitnessRequest.java`,
add after the existing `anonymous` field:

```java

    private Boolean possiblyImplicated;
```

- [ ] **Step 3: Add the field to `WitnessResponse`**

In `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/WitnessResponse.java`,
add after the existing `anonymous` field:

```java
    private Boolean possiblyImplicated;
```

- [ ] **Step 4: Wire `WitnessServiceImpl.create()`**

In `src/main/java/gov/bf/ascelc/univers_audits/service/impl/WitnessServiceImpl.java`,
add to the `Witness.builder()` chain in `create()`, right after
`.anonymous(Boolean.TRUE.equals(request.getAnonymous()))`:

```java
                .possiblyImplicated(Boolean.TRUE.equals(request.getPossiblyImplicated()))
```

- [ ] **Step 5: Wire `WitnessServiceImpl.update()`**

In the same file, add to `update()`, right after the existing
`if (request.getAnonymous() != null) { witness.setAnonymous(request.getAnonymous()); }`
block:

```java
        if (request.getPossiblyImplicated() != null) {
            witness.setPossiblyImplicated(request.getPossiblyImplicated());
        }
```

- [ ] **Step 6: Write migration 026**

Create `src/main/resources/db/changelog/migrations/026-add-witness-possibly-implicated.sql`:

```sql
--liquibase formatted sql
--changeset dev:026-add-witness-possibly-implicated

ALTER TABLE witness ADD COLUMN possibly_implicated BOOLEAN NOT NULL DEFAULT false;
```

- [ ] **Step 7: Write `WitnessServiceImplTest`**

Create `src/test/java/gov/bf/ascelc/univers_audits/service/impl/WitnessServiceImplTest.java`
(no test file currently exists for this service — verified by search — this is a new
file, scoped to this sub-chantier's change only, not exhaustive coverage of
`WitnessServiceImpl`):

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.WitnessRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.WitnessResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Witness;
import gov.bf.ascelc.univers_audits.repository.WitnessRepository;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WitnessServiceImplTest {

    @Mock private WitnessRepository    witnessRepository;
    @Mock private DossierDetailsMapper detailsMapper;
    @Mock private DossierAccessGuard   accessGuard;

    @InjectMocks
    private WitnessServiceImpl service;

    @Test
    void create_defaultsPossiblyImplicatedToFalseWhenOmitted() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        when(accessGuard.getDossierOrThrow(dossier.getId())).thenReturn(dossier);
        when(witnessRepository.save(any(Witness.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(detailsMapper.toResponse(any(Witness.class)))
                .thenReturn(WitnessResponse.builder().build());

        WitnessRequest request = WitnessRequest.builder()
                .firstName("Jean")
                .lastName("Kaboré")
                .build();

        service.create(dossier.getId(), request);

        verify(witnessRepository).save(argThat(w -> !w.isPossiblyImplicated()));
    }

    @Test
    void create_persistsPossiblyImplicatedWhenProvided() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        when(accessGuard.getDossierOrThrow(dossier.getId())).thenReturn(dossier);
        when(witnessRepository.save(any(Witness.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(detailsMapper.toResponse(any(Witness.class)))
                .thenReturn(WitnessResponse.builder().build());

        WitnessRequest request = WitnessRequest.builder()
                .firstName("Awa")
                .lastName("Sawadogo")
                .possiblyImplicated(true)
                .build();

        service.create(dossier.getId(), request);

        verify(witnessRepository).save(argThat(Witness::isPossiblyImplicated));
    }

    @Test
    void update_updatesPossiblyImplicatedWhenProvided() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Witness witness = Witness.builder()
                .id(UUID.randomUUID())
                .dossier(dossier)
                .possiblyImplicated(false)
                .build();
        when(accessGuard.getDossierOrThrow(dossier.getId())).thenReturn(dossier);
        when(witnessRepository.findById(witness.getId())).thenReturn(Optional.of(witness));
        when(witnessRepository.save(any(Witness.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(detailsMapper.toResponse(any(Witness.class)))
                .thenReturn(WitnessResponse.builder().build());

        WitnessRequest request = WitnessRequest.builder()
                .possiblyImplicated(true)
                .build();

        service.update(dossier.getId(), witness.getId(), request);

        assertThat(witness.isPossiblyImplicated()).isTrue();
    }
}
```

- [ ] **Step 8: Compile and run the new test**

Run: `mvn -q test -Dtest=WitnessServiceImplTest`
Expected: BUILD SUCCESS, 3/3 tests passing.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/Witness.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/WitnessRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/WitnessResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/WitnessServiceImpl.java \
        src/main/resources/db/changelog/migrations/026-add-witness-possibly-implicated.sql \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/WitnessServiceImplTest.java
git commit -m "feat: add Witness.possiblyImplicated flag"
```

---

### Task 2: `Audition` business rules — order, investigators, second audition, NO_SHOW

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/enums/IntervieweeType.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Audition.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/AuditionScheduleRequest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/AuditionResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/AuditionService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImpl.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`
- Create: `src/main/resources/db/changelog/migrations/027-audition-business-rules.sql`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImplTest.java`

**Interfaces:**
- Consumes: `Witness.possiblyImplicated`/`isPossiblyImplicated()` (Task 1).
- Consumes: `AgentRepository.findById(UUID): Optional<Agent>` (existing), `Agent.getNomComplet(): String` (existing).
- Produces: `AuditionService.markNoShow(UUID auditionId, String note): AuditionResponse` — no other task consumes this, terminal for this plan.

- [ ] **Step 1: Add `DECLARANT` to `IntervieweeType`**

In `src/main/java/gov/bf/ascelc/univers_audits/enums/IntervieweeType.java`, add after
the existing two values:

```java
package gov.bf.ascelc.univers_audits.enums;

/**
 * Type de personne auditionnée dans le cadre d'une investigation.
 */
public enum IntervieweeType {
    // Partie visée par le dossier
    TARGETED_PARTY,
    // Témoin du dossier
    WITNESS,
    // Dénonciateur (le déclarant du dossier)
    DECLARANT
}
```

- [ ] **Step 2: Rewrite `Audition` entity**

Replace the full content of `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Audition.java`:

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
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
@Table(name = "audition", indexes = {
        // Toutes les auditions d'une investigation
        @Index(name = "idx_audition_investigation",
                columnList = "investigation_id")
})
public class Audition extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Enumerated(EnumType.STRING)
    @Column(name = "interviewee_type", nullable = false, length = 20)
    private IntervieweeType intervieweeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "targeted_party_id")
    private TargetedParty targetedParty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "witness_id")
    private Witness witness;

    @ManyToMany
    @JoinTable(
            name = "audition_investigator",
            joinColumns = @JoinColumn(name = "audition_id"),
            inverseJoinColumns = @JoinColumn(name = "agent_id")
    )
    @Builder.Default
    private List<Agent> investigators = new ArrayList<>();

    @Column(name = "location", length = 300)
    private String location;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "conducted_at")
    private Instant conductedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AuditionStatus status = AuditionStatus.SCHEDULED;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "no_show_note", columnDefinition = "TEXT")
    private String noShowNote;

    public void conduct(String summary) {
        this.conductedAt = Instant.now();
        this.summary = summary;
        this.status = AuditionStatus.CONDUCTED;
    }

    public void cancel(String reason) {
        this.cancellationReason = reason;
        this.status = AuditionStatus.CANCELLED;
    }

    public void markNoShow(String note) {
        this.noShowNote = note;
        this.status = AuditionStatus.NO_SHOW;
    }

    public String getIntervieweeDisplayName() {
        if (IntervieweeType.WITNESS.equals(intervieweeType) && witness != null) {
            return witness.getDisplayName();
        }
        if (IntervieweeType.TARGETED_PARTY.equals(intervieweeType) && targetedParty != null) {
            return targetedParty.getDisplayName();
        }
        if (IntervieweeType.DECLARANT.equals(intervieweeType)
                && investigation != null
                && investigation.getDossier() != null
                && investigation.getDossier().getDeclarant() != null) {
            return investigation.getDossier().getDeclarant().getDisplayName();
        }
        return "Inconnu";
    }
}
```

- [ ] **Step 3: Update `AuditionScheduleRequest`**

Replace the full content of
`src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/AuditionScheduleRequest.java`:

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditionScheduleRequest {

    @NotNull(message = "Le type de personne auditionnée est obligatoire")
    private IntervieweeType intervieweeType;

    private UUID targetedPartyId;

    private UUID witnessId;

    @NotNull(message = "La date de convocation est obligatoire")
    private Instant scheduledAt;

    @Size(max = 300)
    private String location;

    @NotNull(message = "Au moins deux enquêteurs sont requis")
    @Size(min = 2, message = "Au moins deux enquêteurs sont requis")
    private List<UUID> investigatorIds;
}
```

- [ ] **Step 4: Update `AuditionResponse`**

Replace the full content of
`src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/AuditionResponse.java`:

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditionResponse {
    private UUID id;
    private UUID investigationId;
    private IntervieweeType intervieweeType;
    private String intervieweeDisplayName;
    private List<String> investigatorNames;
    private String location;
    private Instant scheduledAt;
    private Instant conductedAt;
    private AuditionStatus status;
    private String summary;
    private String cancellationReason;
    private String noShowNote;
    private String orderWarning;
    private String secondAuditionWarning;
}
```

- [ ] **Step 5: Add `markNoShow` to `AuditionService`**

In `src/main/java/gov/bf/ascelc/univers_audits/service/AuditionService.java`, add after
`cancel`:

```java

    AuditionResponse markNoShow(UUID auditionId, String note);
```

- [ ] **Step 6: Rewrite `AuditionServiceImpl`**

Replace the full content of
`src/main/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImpl.java`:

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
import gov.bf.ascelc.univers_audits.repository.WitnessRepository;
import gov.bf.ascelc.univers_audits.service.AuditionService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditionServiceImpl implements AuditionService {

    private final AuditionRepository       auditionRepository;
    private final InvestigationRepository  investigationRepository;
    private final TargetedPartyRepository  targetedPartyRepository;
    private final WitnessRepository        witnessRepository;
    private final AgentRepository          agentRepository;
    private final DossierDetailsMapper     mapper;
    private final AgentContextResolver     agentContextResolver;
    private final DossierAccessGuard       accessGuard;

    @Override
    @Transactional
    public AuditionResponse schedule(UUID investigationId, AuditionScheduleRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        boolean hasTargetedParty = request.getTargetedPartyId() != null;
        boolean hasWitness = request.getWitnessId() != null;

        if (request.getIntervieweeType() == IntervieweeType.DECLARANT) {
            if (hasTargetedParty || hasWitness) {
                throw new BusinessException(
                        "intervieweeType=DECLARANT ne doit référencer ni partie visée ni témoin");
            }
            if (investigation.getDossier().getDeclarant() == null) {
                throw new BusinessException(
                        "Ce dossier n'a pas de déclarant identifié (dossier anonyme)");
            }
        } else {
            if (hasTargetedParty == hasWitness) {
                throw new BusinessException(
                        "Il faut renseigner exactement une personne auditionnée (partie visée OU témoin)");
            }
            if (request.getIntervieweeType() == IntervieweeType.TARGETED_PARTY && !hasTargetedParty) {
                throw new BusinessException(
                        "intervieweeType=TARGETED_PARTY requiert targetedPartyId");
            }
            if (request.getIntervieweeType() == IntervieweeType.WITNESS && !hasWitness) {
                throw new BusinessException(
                        "intervieweeType=WITNESS requiert witnessId");
            }
        }

        List<Agent> investigators = resolveInvestigators(request.getInvestigatorIds());

        boolean secondAudition = request.getIntervieweeType() == IntervieweeType.TARGETED_PARTY
                && auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigationId)
                        .stream()
                        .anyMatch(a -> a.getIntervieweeType() == IntervieweeType.TARGETED_PARTY
                                && a.getTargetedParty() != null
                                && a.getTargetedParty().getId().equals(request.getTargetedPartyId())
                                && a.getStatus() == AuditionStatus.CONDUCTED);

        Audition.AuditionBuilder<?, ?> builder = Audition.builder()
                .investigation(investigation)
                .intervieweeType(request.getIntervieweeType())
                .scheduledAt(request.getScheduledAt())
                .location(request.getLocation())
                .investigators(investigators);

        if (hasTargetedParty) {
            TargetedParty targetedParty = targetedPartyRepository.findById(request.getTargetedPartyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Partie visée introuvable : " + request.getTargetedPartyId()));
            builder.targetedParty(targetedParty);
        } else if (hasWitness) {
            Witness witness = witnessRepository.findById(request.getWitnessId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Témoin introuvable : " + request.getWitnessId()));
            builder.witness(witness);
        }

        Audition saved = auditionRepository.save(builder.build());
        log.info("Audition planifiée — investigation: {}, id: {}", investigationId, saved.getId());

        AuditionResponse response = mapper.toResponse(saved);
        if (secondAudition) {
            response.setSecondAuditionWarning(
                    "Une audition de ce mis en cause a déjà été tenue — une seconde audition est déconseillée");
        }
        return response;
    }

    @Override
    @Transactional
    public AuditionResponse conduct(UUID auditionId, AuditionConductRequest request) {
        Audition audition = getAuditionOrThrow(auditionId);
        accessGuard.checkReadAccess(audition.getInvestigation().getDossier());
        if (audition.getStatus() != AuditionStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une audition planifiée peut être tenue (statut actuel : " + audition.getStatus() + ")");
        }

        String orderWarning = computeOrderWarning(audition);

        audition.conduct(request.getSummary());
        Audition saved = auditionRepository.save(audition);
        log.info("Audition tenue — id: {}", auditionId);

        AuditionResponse response = mapper.toResponse(saved);
        response.setOrderWarning(orderWarning);
        return response;
    }

    @Override
    @Transactional
    public AuditionResponse cancel(UUID auditionId, String reason) {
        Audition audition = getAuditionOrThrow(auditionId);
        accessGuard.checkReadAccess(audition.getInvestigation().getDossier());
        if (audition.getStatus() != AuditionStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une audition planifiée peut être annulée (statut actuel : " + audition.getStatus() + ")");
        }
        audition.cancel(reason);
        Audition saved = auditionRepository.save(audition);
        log.info("Audition annulée — id: {}, motif: {}", auditionId, reason);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AuditionResponse markNoShow(UUID auditionId, String note) {
        Audition audition = getAuditionOrThrow(auditionId);
        accessGuard.checkReadAccess(audition.getInvestigation().getDossier());
        if (audition.getStatus() != AuditionStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une audition planifiée peut être marquée absente (statut actuel : " + audition.getStatus() + ")");
        }
        audition.markNoShow(note);
        Audition saved = auditionRepository.save(audition);
        log.info("Audition — absence constatée — id: {}", auditionId);
        return mapper.toResponse(saved);
    }

    @Override
    public List<AuditionResponse> findByInvestigationId(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigationId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private List<Agent> resolveInvestigators(List<UUID> investigatorIds) {
        return investigatorIds.stream()
                .map(id -> agentRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Enquêteur introuvable : " + id)))
                .toList();
    }

    private String computeOrderWarning(Audition audition) {
        int rank = orderRank(audition);
        Optional<Audition> pending = auditionRepository
                .findByInvestigationIdOrderByScheduledAtAsc(audition.getInvestigation().getId())
                .stream()
                .filter(a -> !a.getId().equals(audition.getId()))
                .filter(a -> a.getStatus() == AuditionStatus.SCHEDULED)
                .filter(a -> orderRank(a) < rank)
                .findFirst();
        return pending
                .map(a -> "Ordre non respecté : l'audition de " + a.getIntervieweeDisplayName()
                        + " (rang antérieur dans l'ordre imposé) n'a pas encore été tenue")
                .orElse(null);
    }

    private int orderRank(Audition audition) {
        return switch (audition.getIntervieweeType()) {
            case DECLARANT -> 0;
            case WITNESS -> audition.getWitness().isPossiblyImplicated() ? 2 : 1;
            case TARGETED_PARTY -> 3;
        };
    }

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

    private Audition getAuditionOrThrow(UUID id) {
        return auditionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audition introuvable : " + id));
    }
}
```

- [ ] **Step 7: Add `no-show` endpoint to `AuditionController`**

In `src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java`, add
after the existing `cancel` method:

```java

    @PatchMapping("/{auditionId}/no-show")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> markNoShow(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(auditionService.markNoShow(auditionId, note));
    }
```

- [ ] **Step 8: Update `DossierDetailsMapper`'s Audition mapping**

In `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`,
replace this block:

```java
    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "intervieweeDisplayName", ignore = true)
    @Mapping(target = "conductedByName", source = "conductedBy.nomComplet")
    AuditionResponse mapToResponse(Audition audition);

    default void fillAudition(
            Audition audition,
            AuditionResponse response) {
        response.setIntervieweeDisplayName(audition.getIntervieweeDisplayName());
    }
```

with:

```java
    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "intervieweeDisplayName", ignore = true)
    @Mapping(target = "investigatorNames", ignore = true)
    @Mapping(target = "orderWarning", ignore = true)
    @Mapping(target = "secondAuditionWarning", ignore = true)
    AuditionResponse mapToResponse(Audition audition);

    default void fillAudition(
            Audition audition,
            AuditionResponse response) {
        response.setIntervieweeDisplayName(audition.getIntervieweeDisplayName());
        response.setInvestigatorNames(
                audition.getInvestigators().stream().map(Agent::getNomComplet).toList());
    }
```

(`orderWarning`/`secondAuditionWarning` are request-context-dependent, not pure
functions of the entity — they're set explicitly by `AuditionServiceImpl` on the
response object after mapping, never by the mapper itself, which is why they're
`ignore`d here rather than filled in `fillAudition`.)

- [ ] **Step 9: Write migration 027**

Create `src/main/resources/db/changelog/migrations/027-audition-business-rules.sql`:

```sql
--liquibase formatted sql
--changeset dev:027-audition-business-rules

ALTER TABLE audition ADD COLUMN no_show_note TEXT;

CREATE TABLE audition_investigator (
    audition_id UUID NOT NULL REFERENCES audition(id),
    agent_id    UUID NOT NULL REFERENCES agent(id),
    PRIMARY KEY (audition_id, agent_id)
);

INSERT INTO audition_investigator (audition_id, agent_id)
SELECT id, conducted_by_id FROM audition WHERE conducted_by_id IS NOT NULL;

ALTER TABLE audition DROP COLUMN conducted_by_id;
```

This migration is auto-discovered via `includeAll` — no changelog-master.yaml edit
needed. Brand-new `audition_investigator` table needs no CHECK-constraint handling. The
backfill preserves each existing audition's single historical investigator in the new
collection (even though it leaves that one row below the new minimum of 2 — the
`@Size(min = 2)` constraint only applies to new `schedule()` calls, never retroactively).

- [ ] **Step 10: Rewrite `AuditionServiceImplTest`**

Replace the full content of
`src/test/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImplTest.java`:

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
import gov.bf.ascelc.univers_audits.repository.WitnessRepository;
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
class AuditionServiceImplTest {

    @Mock private AuditionRepository auditionRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private TargetedPartyRepository targetedPartyRepository;
    @Mock private WitnessRepository witnessRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private DossierDetailsMapper mapper;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private DossierAccessGuard accessGuard;

    @InjectMocks
    private AuditionServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder()
                .id(UUID.randomUUID())
                .dossier(dossier)
                .build();
    }

    private List<UUID> twoInvestigatorIds(Agent a1, Agent a2) {
        when(agentRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(agentRepository.findById(a2.getId())).thenReturn(Optional.of(a2));
        return List.of(a1.getId(), a2.getId());
    }

    @Test
    void schedule_createsAuditionForWitness() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Witness witness = Witness.builder().id(UUID.randomUUID()).dossier(dossier).build();
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(witnessRepository.findById(witness.getId()))
                .thenReturn(Optional.of(witness));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of());
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witnessId(witness.getId())
                .scheduledAt(Instant.now())
                .location("Bureau BRPD")
                .investigatorIds(investigatorIds)
                .build();

        service.schedule(investigation.getId(), request);

        verify(auditionRepository).save(argThat(a ->
                a.getIntervieweeType() == IntervieweeType.WITNESS
                        && a.getWitness() == witness
                        && a.getStatus() == AuditionStatus.SCHEDULED
                        && a.getInvestigators().containsAll(List.of(agent1, agent2))
                        && a.getInvestigators().size() == 2));
    }

    @Test
    void schedule_createsAuditionForDeclarant() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID()).build();
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).declarant(declarant).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of());
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.DECLARANT)
                .scheduledAt(Instant.now())
                .investigatorIds(investigatorIds)
                .build();

        service.schedule(investigation.getId(), request);

        verify(auditionRepository).save(argThat(a ->
                a.getIntervieweeType() == IntervieweeType.DECLARANT
                        && a.getTargetedParty() == null
                        && a.getWitness() == null));
    }

    @Test
    void schedule_throwsWhenDeclarantMissingOnAnonymousDossier() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).declarant(null).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.DECLARANT)
                .scheduledAt(Instant.now())
                .investigatorIds(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .build();

        assertThatThrownBy(() -> service.schedule(investigation.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void schedule_throwsWhenBothTargetedPartyAndWitnessProvided() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witnessId(UUID.randomUUID())
                .targetedPartyId(UUID.randomUUID())
                .scheduledAt(Instant.now())
                .investigatorIds(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .build();

        assertThatThrownBy(() -> service.schedule(investigation.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void schedule_setsSecondAuditionWarningForRepeatedTargetedParty() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        TargetedParty targetedParty = TargetedParty.builder().id(UUID.randomUUID()).dossier(dossier).build();
        Audition previousAudition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .targetedParty(targetedParty)
                .status(AuditionStatus.CONDUCTED)
                .build();
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(targetedPartyRepository.findById(targetedParty.getId()))
                .thenReturn(Optional.of(targetedParty));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of(previousAudition));
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .targetedPartyId(targetedParty.getId())
                .scheduledAt(Instant.now())
                .investigatorIds(investigatorIds)
                .build();

        AuditionResponse response = service.schedule(investigation.getId(), request);

        assertThat(response.getSecondAuditionWarning()).isNotBlank();
    }

    @Test
    void schedule_noSecondAuditionWarningForWitnessRepeatedInterview() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Witness witness = Witness.builder().id(UUID.randomUUID()).dossier(dossier).build();
        Audition previousAudition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.WITNESS)
                .witness(witness)
                .status(AuditionStatus.CONDUCTED)
                .build();
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(witnessRepository.findById(witness.getId()))
                .thenReturn(Optional.of(witness));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of(previousAudition));
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witnessId(witness.getId())
                .scheduledAt(Instant.now())
                .investigatorIds(investigatorIds)
                .build();

        AuditionResponse response = service.schedule(investigation.getId(), request);

        assertThat(response.getSecondAuditionWarning()).isNull();
    }

    @Test
    void conduct_setsStatusAndSummary() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("Compte-rendu").build());

        assertThat(audition.getStatus()).isEqualTo(AuditionStatus.CONDUCTED);
        assertThat(audition.getSummary()).isEqualTo("Compte-rendu");
        assertThat(audition.getConductedAt()).isNotNull();
    }

    @Test
    void conduct_throwsWhenAuditionNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.CANCELLED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void conduct_setsOrderWarningWhenEarlierRankStillScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Witness pendingWitness = Witness.builder().id(UUID.randomUUID())
                .dossier(dossier).firstName("Jean").lastName("Kaboré")
                .possiblyImplicated(false).build();
        Audition pendingEarlierAudition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.WITNESS)
                .witness(pendingWitness)
                .status(AuditionStatus.SCHEDULED)
                .build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();

        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(pendingEarlierAudition, audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionResponse response = service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("Compte-rendu").build());

        assertThat(response.getOrderWarning()).isNotBlank();
    }

    @Test
    void conduct_noOrderWarningWhenOrderRespected() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.DECLARANT)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();

        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionResponse response = service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("Compte-rendu").build());

        assertThat(response.getOrderWarning()).isNull();
    }

    @Test
    void cancel_setsStatusAndReason() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.SCHEDULED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        service.cancel(audition.getId(), "Personne injoignable");

        assertThat(audition.getStatus()).isEqualTo(AuditionStatus.CANCELLED);
        assertThat(audition.getCancellationReason()).isEqualTo("Personne injoignable");
    }

    @Test
    void cancel_throwsWhenAuditionNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.CONDUCTED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.cancel(audition.getId(), "motif"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markNoShow_setsStatusAndNote() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.SCHEDULED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        service.markNoShow(audition.getId(), "Deux relances sans réponse");

        assertThat(audition.getStatus()).isEqualTo(AuditionStatus.NO_SHOW);
        assertThat(audition.getNoShowNote()).isEqualTo("Deux relances sans réponse");
    }

    @Test
    void markNoShow_throwsWhenAuditionNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.CONDUCTED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.markNoShow(audition.getId(), "motif"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByInvestigationId_returnsEmptyWhenConfidentialAndNotAuthorized() {
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID())
                .isConfidential(true)
                .build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<AuditionResponse> result = service.findByInvestigationId(investigation.getId());

        assertThat(result).isEmpty();
        verify(auditionRepository, never()).findByInvestigationIdOrderByScheduledAtAsc(any());
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

- [ ] **Step 11: Run the tests**

Run: `mvn -q test -Dtest=AuditionServiceImplTest`
Expected: BUILD SUCCESS, 16/16 tests passing.

- [ ] **Step 12: Compile the full project**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (confirms `AuditionController`/mapper/all call sites compile
against the changed entity/DTOs).

- [ ] **Step 13: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/IntervieweeType.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/Audition.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/AuditionScheduleRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/AuditionResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/AuditionService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImpl.java \
        src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java \
        src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java \
        src/main/resources/db/changelog/migrations/027-audition-business-rules.sql \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImplTest.java
git commit -m "feat: implement Audition order/investigator/second-audition/no-show rules"
```

---

### Task 3: `VisiteTerrain.conductedBy` → `plannedBy` rename

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/VisiteTerrain.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/VisiteTerrainResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImpl.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImplTest.java`

**Interfaces:**
- No cross-task interfaces — fully isolated from Tasks 1/2, touches only the
  already-shipped `VisiteTerrain` module. Terminal for this plan.

- [ ] **Step 1: Rename the entity field**

In `src/main/java/gov/bf/ascelc/univers_audits/model/entity/VisiteTerrain.java`,
replace:

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id", nullable = false)
    private Agent conductedBy;
```

with:

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id", nullable = false)
    private Agent plannedBy;
```

(Column name `conducted_by_id` is deliberately unchanged — see Global Constraints.)

- [ ] **Step 2: Rename the DTO field**

In `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/VisiteTerrainResponse.java`,
replace:

```java
    private String conductedByName;
```

with:

```java
    private String plannedByName;
```

- [ ] **Step 3: Update `VisiteTerrainServiceImpl`**

In `src/main/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImpl.java`,
in `schedule()`, replace:

```java
                .conductedBy(agentContextResolver.getCurrentAgent())
```

with:

```java
                .plannedBy(agentContextResolver.getCurrentAgent())
```

- [ ] **Step 4: Update `DossierDetailsMapper`'s VisiteTerrain mapping**

In `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`,
replace:

```java
    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "conductedByName", source = "conductedBy.nomComplet")
    VisiteTerrainResponse toResponse(VisiteTerrain visiteTerrain);
```

with:

```java
    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "plannedByName", source = "plannedBy.nomComplet")
    VisiteTerrainResponse toResponse(VisiteTerrain visiteTerrain);
```

- [ ] **Step 5: Update `VisiteTerrainServiceImplTest`**

In `src/test/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImplTest.java`,
in `schedule_createsVisite()`, replace:

```java
        verify(visiteTerrainRepository).save(argThat(v ->
                v.getLocation().equals("Siège de l'entreprise X")
                        && v.getStatus() == VisiteStatus.SCHEDULED
                        && v.getConductedBy() == agent));
```

with:

```java
        verify(visiteTerrainRepository).save(argThat(v ->
                v.getLocation().equals("Siège de l'entreprise X")
                        && v.getStatus() == VisiteStatus.SCHEDULED
                        && v.getPlannedBy() == agent));
```

- [ ] **Step 6: Run the tests**

Run: `mvn -q test -Dtest=VisiteTerrainServiceImplTest`
Expected: BUILD SUCCESS, 9/9 tests passing.

- [ ] **Step 7: Compile and run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, same pre-existing `UniversAuditsApplicationTests.contextLoads`
environment-only failure (no live datasource) as every prior sub-chantier this session,
no other failures.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/VisiteTerrain.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/VisiteTerrainResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImpl.java \
        src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/VisiteTerrainServiceImplTest.java
git commit -m "refactor: rename VisiteTerrain.conductedBy to plannedBy"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** §1 (ordre + DECLARANT + possiblyImplicated) → Task 1 + Task 2 Steps
  1-2, 6, 8. §2 (≥2 enquêteurs) → Task 2 Steps 3, 6. §3 (seconde audition) → Task 2 Step
  6. §4 (NO_SHOW) → Task 2 Steps 2, 5-7. §5 (VisiteTerrain rename) → Task 3. Hors
  périmètre items (PVAudition correction, RegistreAuditions, Departement.code=='DEI',
  notifications automatiques, dédoublonnage investigatorIds) correctly absent from every
  task.
- **Task ordering resolves the entity/consumer coupling correctly**: Task 2 (Audition)
  depends on Task 1's `Witness.possiblyImplicated` field for `orderRank()`'s WITNESS
  branch — Task 1 is dispatched first. Task 3 (VisiteTerrain) is fully independent of
  both and could in principle run first, but is kept last since it's the smallest,
  lowest-risk change and there's no benefit to reordering.
- **Compile-safety across task boundaries verified**: each task's file set, taken
  together, compiles standalone — Task 1 only adds a new column/field with no existing
  caller broken (nothing outside `WitnessServiceImpl`/`WitnessRequest`/`WitnessResponse`
  currently constructs a `Witness` requiring changes). Task 2 touches the entity,
  every one of its consumers (service, controller, mapper, DTOs, tests) in the same
  task, and its own migration — no intermediate broken-compile state within the task
  since it's specified as one replace-full-file step per file, applied together before
  the compile/test verification steps.
- **Type consistency verified**: `Audition.investigators`/`AuditionScheduleRequest.investigatorIds`/
  `AuditionResponse.investigatorNames` naming is consistent across every task and step.
  `Witness.possiblyImplicated`/`isPossiblyImplicated()` used identically in Task 1's own
  code and Task 2's `orderRank()`.
