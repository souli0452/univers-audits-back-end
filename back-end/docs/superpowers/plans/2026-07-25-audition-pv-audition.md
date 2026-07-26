# Objets d'enquête — Audition et PV d'audition — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Processus C investigation workflow its two most frequent procedural acts — `Audition` (interview session) and `PVAudition` (its formal minutes) — as tracked, timestamped entities instead of free-text `Observation` entries, per the ASCE-LC compliance report's §5 gap analysis and priority-3 recommendation.

**Architecture:** Two new JPA entities extending `AuditEntity`, nested under `Investigation` (an audition only makes sense within an investigation). `Audition` records who was interviewed (a `TargetedParty` or a `Witness`, mutually exclusive), when, where, and its outcome. `PVAudition` is its one-to-one formal minutes, created once the audition is conducted and finalized with the interviewee's signature or refusal. Both follow the existing dossier-scoped sub-resource pattern already used by `Witness`/`TargetedParty`/`Observation` (entity → repository → service → controller, `DossierDetailsMapper` for response mapping, confidentiality masking via `DossierAccessGuard`).

**Tech Stack:** Spring Boot 3.3.5 / Java 21, Spring Data JPA, Liquibase (raw SQL changesets), Lombok (`@SuperBuilder`), MapStruct (`DossierDetailsMapper`), JUnit 5 + Mockito.

## Global Constraints

- Package root: `gov.bf.ascelc.univers_audits`.
- New entities extend `abstracts.AuditEntity` — do not redeclare `id`/`createdAt`/`updatedAt`/`createdById`/`updatedById`/`version`.
- Entity style matches `Witness`/`TargetedParty`: `@Getter @Setter @Entity @SuperBuilder @NoArgsConstructor @AllArgsConstructor`, explicit `@Table(name=..., indexes = {@Index(...)})`, `@Builder.Default` on every column with a Java-side default.
- Enum style matches `InvestigationStatus`: plain English identifiers, French one-line comment above each value explaining its meaning.
- `Audition` is nested under `Investigation`, not `Dossier` directly — `investigation_id` is the required foreign key, matching the plan's framing of auditions as an investigation-phase act.
- Exactly one of `targetedPartyId` / `witnessId` must be supplied when scheduling an audition, matching `intervieweeType` — this is a cross-field business rule, validated in the service layer (`BusinessException`) like `AgentService`'s existing cross-field checks, not via a Bean Validation annotation.
- Confidentiality: `AuditionServiceImpl.findByInvestigationId` must return an empty list when the underlying dossier is confidential and the current agent cannot see confidential dossiers — same rule as `WitnessServiceImpl.findByDossierId` (`shared.utils.DossierAccessGuard.canSeeConfidential()`).
- Response mapping goes through `mapper.DossierDetailsMapper` (add overloaded `toResponse(Audition)` / `toResponse(PVAudition)` there) — do not create separate mapper files, matching how `Witness`/`TargetedParty`/`Observation`/`Attachment`/`Notification`/`StatusHistory` are already handled in that one file.
- Migrations live in `src/main/resources/db/changelog/migrations/`, auto-included via `includeAll` in `db.changelog-master.yaml`. Next free numeric prefixes: `006`, `007` (existing: `001`–`005`).
- Not-found errors → `shared.exceptions.ResourceNotFoundException` (404). Invalid state transitions / cross-field violations → `shared.exceptions.BusinessException`.
- Role restrictions: scheduling, conducting, cancelling an audition, and creating/finalizing its PV are restricted to `CONTROLEUR_ETAT`, `CGEA`, `ADMIN_DDIC` (the field-investigation roles, matching `WitnessController`'s create/update role set). Reading auditions/PVs is open to the broader investigation-visibility role set already used by `InvestigationController.findById`: `CGEA`, `CGE`, `CONTROLEUR_ETAT`, `MEMBRE_CTADP`, `CONSEILLER_JURIDIQUE`, `ADMIN_DDIC`.
- No fabricated legal citations or article references anywhere in this plan's code, comments, or seed data — none are needed for this subsystem (no referential data is seeded here).
- Out of scope (flag as follow-up, do not build now): PDF generation of the PV document (§9 gap, separate priority item), audio/video recording attachment linkage beyond the existing generic `Attachment.investigation` relation, multi-round PV amendment history.

---

## File Structure

```
src/main/java/gov/bf/ascelc/univers_audits/
  enums/IntervieweeType.java                       (new)
  enums/AuditionStatus.java                         (new)
  model/entity/Audition.java                        (new)
  model/entity/PVAudition.java                      (new)
  repository/AuditionRepository.java                (new)
  repository/PVAuditionRepository.java              (new)
  model/dto/request/AuditionScheduleRequest.java    (new)
  model/dto/request/AuditionConductRequest.java     (new)
  model/dto/request/PvAuditionCreateRequest.java    (new)
  model/dto/request/PvAuditionFinalizeRequest.java  (new)
  model/dto/response/AuditionResponse.java          (new)
  model/dto/response/PvAuditionResponse.java        (new)
  mapper/DossierDetailsMapper.java                  (modify: add Audition/PVAudition mappings)
  service/AuditionService.java                      (new, interface)
  service/impl/AuditionServiceImpl.java             (new)
  service/PvAuditionService.java                    (new, interface)
  service/impl/PvAuditionServiceImpl.java           (new)
  controller/AuditionController.java                (new)

src/main/resources/db/changelog/migrations/
  006-create-audition.sql                           (new)
  007-create-pv-audition.sql                        (new)

src/test/java/gov/bf/ascelc/univers_audits/
  service/impl/AuditionServiceImplTest.java         (new)
  service/impl/PvAuditionServiceImplTest.java       (new)
```

---

### Task 1: Enums and `Audition` entity, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/IntervieweeType.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/AuditionStatus.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Audition.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/AuditionRepository.java`
- Create: `src/main/resources/db/changelog/migrations/006-create-audition.sql`

**Interfaces:**
- Produces: `Audition` entity with fields `investigation:Investigation`, `intervieweeType:IntervieweeType`, `targetedParty:TargetedParty` (nullable), `witness:Witness` (nullable), `conductedBy:Agent`, `location:String`, `scheduledAt:Instant`, `conductedAt:Instant` (nullable), `status:AuditionStatus`, `summary:String` (nullable), `cancellationReason:String` (nullable); methods `conduct(String summary)`, `cancel(String reason)`. `AuditionRepository.findByInvestigationIdOrderByScheduledAtAsc(UUID):List<Audition>`.

- [ ] **Step 1: Create `IntervieweeType`**

```java
package gov.bf.ascelc.univers_audits.enums;

/**
 * Type de personne auditionnée dans le cadre d'une investigation.
 */
public enum IntervieweeType {
    // Partie visée par le dossier
    TARGETED_PARTY,
    // Témoin du dossier
    WITNESS
}
```

- [ ] **Step 2: Create `AuditionStatus`**

```java
package gov.bf.ascelc.univers_audits.enums;

/**
 * Statuts du cycle de vie d'une audition.
 */
public enum AuditionStatus {
    // Planifiée, pas encore tenue
    SCHEDULED,
    // Tenue, compte-rendu enregistré
    CONDUCTED,
    // Annulée avant d'avoir eu lieu
    CANCELLED,
    // La personne convoquée ne s'est pas présentée
    NO_SHOW
}
```

- [ ] **Step 3: Create the `Audition` entity**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id", nullable = false)
    private Agent conductedBy;

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

    public void conduct(String summary) {
        this.conductedAt = Instant.now();
        this.summary = summary;
        this.status = AuditionStatus.CONDUCTED;
    }

    public void cancel(String reason) {
        this.cancellationReason = reason;
        this.status = AuditionStatus.CANCELLED;
    }

    public String getIntervieweeDisplayName() {
        if (IntervieweeType.WITNESS.equals(intervieweeType) && witness != null) {
            return witness.getDisplayName();
        }
        if (IntervieweeType.TARGETED_PARTY.equals(intervieweeType) && targetedParty != null) {
            return targetedParty.getDisplayName();
        }
        return "Inconnu";
    }
}
```

- [ ] **Step 4: Create the repository**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Audition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditionRepository extends JpaRepository<Audition, UUID> {

    List<Audition> findByInvestigationIdOrderByScheduledAtAsc(UUID investigationId);
}
```

- [ ] **Step 5: Create the migration**

```sql
--liquibase formatted sql
--changeset dev:006-create-audition

CREATE TABLE audition (
    id                   UUID PRIMARY KEY,
    investigation_id     UUID         NOT NULL REFERENCES investigation(id),
    interviewee_type     VARCHAR(20)  NOT NULL,
    targeted_party_id    UUID REFERENCES targeted_party(id),
    witness_id           UUID REFERENCES witness(id),
    conducted_by_id      UUID         NOT NULL REFERENCES agent(id),
    location             VARCHAR(300),
    scheduled_at         TIMESTAMP    NOT NULL,
    conducted_at         TIMESTAMP,
    status               VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    summary              TEXT,
    cancellation_reason  TEXT,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP,
    created_by_id        VARCHAR(100),
    updated_by_id        VARCHAR(100)
);

CREATE INDEX idx_audition_investigation ON audition (investigation_id);
```

- [ ] **Step 6: Compile to verify no syntax errors**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/IntervieweeType.java src/main/java/gov/bf/ascelc/univers_audits/enums/AuditionStatus.java src/main/java/gov/bf/ascelc/univers_audits/model/entity/Audition.java src/main/java/gov/bf/ascelc/univers_audits/repository/AuditionRepository.java src/main/resources/db/changelog/migrations/006-create-audition.sql
git commit -m "feat: add Audition entity, repository and migration"
```

---

### Task 2: `AuditionService` with confidentiality masking and unit tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/AuditionScheduleRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/AuditionConductRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/AuditionResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/AuditionService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImplTest.java`

**Interfaces:**
- Consumes: `AuditionRepository` (Task 1), `InvestigationRepository.findById` (existing), `TargetedPartyRepository.findById` / `WitnessRepository.findById` (existing), `shared.utils.AgentContextResolver.getCurrentAgent()` (existing), `shared.utils.DossierAccessGuard.checkReadAccess`/`canSeeConfidential` (existing).
- Produces: `AuditionService.schedule(UUID investigationId, AuditionScheduleRequest):AuditionResponse`, `.conduct(UUID auditionId, AuditionConductRequest):AuditionResponse`, `.cancel(UUID auditionId, String reason):AuditionResponse`, `.findByInvestigationId(UUID investigationId):List<AuditionResponse>` — these four signatures are what Task 3's controller calls.

- [ ] **Step 1: Write the failing tests**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
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

    @Test
    void schedule_createsAuditionForWitness() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Witness witness = Witness.builder().id(UUID.randomUUID()).dossier(dossier).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(witnessRepository.findById(witness.getId()))
                .thenReturn(Optional.of(witness));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witnessId(witness.getId())
                .scheduledAt(Instant.now())
                .location("Bureau BRPD")
                .build();

        service.schedule(investigation.getId(), request);

        verify(auditionRepository).save(argThat(a ->
                a.getIntervieweeType() == IntervieweeType.WITNESS
                        && a.getWitness() == witness
                        && a.getStatus() == AuditionStatus.SCHEDULED
                        && a.getConductedBy() == agent));
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
                .build();

        assertThatThrownBy(() -> service.schedule(investigation.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void conduct_setsStatusAndSummary() {
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.SCHEDULED)
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
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
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.CANCELLED)
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("x").build()))
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

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=AuditionServiceImplTest test`
Expected: FAIL — compilation error, DTOs/service/impl don't exist yet, `Dossier.isConfidential`/`Investigation.dossier` builder calls won't resolve until the surrounding types compile (they already exist on `main` — only the new Audition-related types are missing)

- [ ] **Step 3: Create `AuditionScheduleRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
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
}
```

- [ ] **Step 4: Create `AuditionConductRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditionConductRequest {

    @NotBlank(message = "Le compte-rendu est obligatoire")
    private String summary;
}
```

- [ ] **Step 5: Create `AuditionResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import lombok.*;

import java.time.Instant;
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
    private String conductedByName;
    private String location;
    private Instant scheduledAt;
    private Instant conductedAt;
    private AuditionStatus status;
    private String summary;
    private String cancellationReason;
}
```

- [ ] **Step 6: Add `Audition` mapping to `DossierDetailsMapper`**

Add to `mapper/DossierDetailsMapper.java` (inside the existing interface, alongside the other `toResponse` overloads):

```java
    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "intervieweeDisplayName", ignore = true)
    @Mapping(target = "conductedByName", source = "conductedBy.nomComplet")
    AuditionResponse toResponse(Audition audition);

    @AfterMapping
    default void fillAudition(
            Audition audition,
            @MappingTarget AuditionResponse response) {
        response.setIntervieweeDisplayName(audition.getIntervieweeDisplayName());
    }
```

- [ ] **Step 7: Create the service interface**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;

import java.util.List;
import java.util.UUID;

public interface AuditionService {

    AuditionResponse schedule(UUID investigationId, AuditionScheduleRequest request);

    AuditionResponse conduct(UUID auditionId, AuditionConductRequest request);

    AuditionResponse cancel(UUID auditionId, String reason);

    List<AuditionResponse> findByInvestigationId(UUID investigationId);
}
```

- [ ] **Step 8: Implement the service**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
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
    private final DossierDetailsMapper     mapper;
    private final AgentContextResolver     agentContextResolver;
    private final DossierAccessGuard       accessGuard;

    @Override
    @Transactional
    public AuditionResponse schedule(UUID investigationId, AuditionScheduleRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);

        boolean hasTargetedParty = request.getTargetedPartyId() != null;
        boolean hasWitness = request.getWitnessId() != null;
        if (hasTargetedParty == hasWitness) {
            throw new BusinessException(
                    "Il faut renseigner exactement une personne auditionnée (partie visée OU témoin)");
        }

        Audition.AuditionBuilder<?, ?> builder = Audition.builder()
                .investigation(investigation)
                .intervieweeType(request.getIntervieweeType())
                .scheduledAt(request.getScheduledAt())
                .location(request.getLocation())
                .conductedBy(agentContextResolver.getCurrentAgent());

        if (hasTargetedParty) {
            TargetedParty targetedParty = targetedPartyRepository.findById(request.getTargetedPartyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Partie visée introuvable : " + request.getTargetedPartyId()));
            builder.targetedParty(targetedParty);
        } else {
            Witness witness = witnessRepository.findById(request.getWitnessId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Témoin introuvable : " + request.getWitnessId()));
            builder.witness(witness);
        }

        Audition saved = auditionRepository.save(builder.build());
        log.info("Audition planifiée — investigation: {}, id: {}", investigationId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AuditionResponse conduct(UUID auditionId, AuditionConductRequest request) {
        Audition audition = getAuditionOrThrow(auditionId);
        if (audition.getStatus() != AuditionStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une audition planifiée peut être tenue (statut actuel : " + audition.getStatus() + ")");
        }
        audition.conduct(request.getSummary());
        Audition saved = auditionRepository.save(audition);
        log.info("Audition tenue — id: {}", auditionId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AuditionResponse cancel(UUID auditionId, String reason) {
        Audition audition = getAuditionOrThrow(auditionId);
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
    public List<AuditionResponse> findByInvestigationId(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigationId)
                .stream()
                .map(mapper::toResponse)
                .toList();
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

- [ ] **Step 9: Run tests to verify they pass**

Run: `mvn -q -Dtest=AuditionServiceImplTest test`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 10: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/AuditionScheduleRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/AuditionConductRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/AuditionResponse.java src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java src/main/java/gov/bf/ascelc/univers_audits/service/AuditionService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImplTest.java
git commit -m "feat: add AuditionService with scheduling, conduct/cancel and confidentiality masking"
```

---

### Task 3: `AuditionController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java`

**Interfaces:**
- Consumes: `AuditionService.schedule/conduct/cancel/findByInvestigationId` (Task 2).

- [ ] **Step 1: Create the controller**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.service.AuditionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/auditions")
@RequiredArgsConstructor
public class AuditionController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','CGEA','ADMIN_DDIC')";

    private final AuditionService auditionService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<AuditionResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(auditionService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> schedule(
            @PathVariable UUID investigationId,
            @Valid @RequestBody AuditionScheduleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(auditionService.schedule(investigationId, request));
    }

    @PatchMapping("/{auditionId}/conduct")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> conduct(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody AuditionConductRequest request) {
        return ResponseEntity.ok(auditionService.conduct(auditionId, request));
    }

    @PatchMapping("/{auditionId}/cancel")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> cancel(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @RequestParam String reason) {
        return ResponseEntity.ok(auditionService.cancel(auditionId, reason));
    }
}
```

- [ ] **Step 2: Compile to verify no syntax errors**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java
git commit -m "feat: add Audition REST endpoints"
```

---

### Task 4: `PVAudition` entity, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/PVAudition.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/PVAuditionRepository.java`
- Create: `src/main/resources/db/changelog/migrations/007-create-pv-audition.sql`

**Interfaces:**
- Produces: `PVAudition` entity with fields `audition:Audition` (unique FK), `content:String`, `draftedBy:Agent`, `intervieweeSigned:Boolean`, `intervieweeSignatureRefused:Boolean`, `finalizedAt:Instant` (nullable); method `finalizeSignatures(boolean signed, boolean refused)`. `PVAuditionRepository.findByAuditionId(UUID):Optional<PVAudition>`.

- [ ] **Step 1: Create the entity**

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
@Table(name = "pv_audition", indexes = {
        @Index(name = "idx_pv_audition_audition",
                columnList = "audition_id", unique = true)
})
public class PVAudition extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audition_id", nullable = false, unique = true)
    private Audition audition;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drafted_by_id", nullable = false)
    private Agent draftedBy;

    @Column(name = "interviewee_signed", nullable = false)
    @Builder.Default
    private Boolean intervieweeSigned = false;

    @Column(name = "interviewee_signature_refused", nullable = false)
    @Builder.Default
    private Boolean intervieweeSignatureRefused = false;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    public boolean isFinalized() {
        return finalizedAt != null;
    }

    public void finalizeSignatures(boolean signed, boolean refused) {
        this.intervieweeSigned = signed;
        this.intervieweeSignatureRefused = refused;
        this.finalizedAt = Instant.now();
    }
}
```

- [ ] **Step 2: Create the repository**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PVAuditionRepository extends JpaRepository<PVAudition, UUID> {

    Optional<PVAudition> findByAuditionId(UUID auditionId);
}
```

- [ ] **Step 3: Create the migration**

```sql
--liquibase formatted sql
--changeset dev:007-create-pv-audition

CREATE TABLE pv_audition (
    id                             UUID PRIMARY KEY,
    audition_id                    UUID         NOT NULL REFERENCES audition(id),
    content                        TEXT         NOT NULL,
    drafted_by_id                  UUID         NOT NULL REFERENCES agent(id),
    interviewee_signed             BOOLEAN      NOT NULL DEFAULT FALSE,
    interviewee_signature_refused  BOOLEAN      NOT NULL DEFAULT FALSE,
    finalized_at                   TIMESTAMP,
    version                        BIGINT       NOT NULL DEFAULT 0,
    created_at                     TIMESTAMP    NOT NULL,
    updated_at                     TIMESTAMP,
    created_by_id                  VARCHAR(100),
    updated_by_id                  VARCHAR(100)
);

CREATE UNIQUE INDEX idx_pv_audition_audition ON pv_audition (audition_id);
```

- [ ] **Step 4: Compile to verify no syntax errors**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/PVAudition.java src/main/java/gov/bf/ascelc/univers_audits/repository/PVAuditionRepository.java src/main/resources/db/changelog/migrations/007-create-pv-audition.sql
git commit -m "feat: add PVAudition entity, repository and migration"
```

---

### Task 5: `PvAuditionService` with unit tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PvAuditionCreateRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PvAuditionFinalizeRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PvAuditionResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/PvAuditionService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImplTest.java`

**Interfaces:**
- Consumes: `PVAuditionRepository` (Task 4), `AuditionRepository.findById` (Task 1), `AgentContextResolver.getCurrentAgent()` (existing).
- Produces: `PvAuditionService.create(UUID auditionId, PvAuditionCreateRequest):PvAuditionResponse`, `.finalizeSignatures(UUID auditionId, PvAuditionFinalizeRequest):PvAuditionResponse`, `.findByAuditionId(UUID auditionId):PvAuditionResponse` — these are what Task 6's controller endpoints call.

- [ ] **Step 1: Write the failing tests**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
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
class PvAuditionServiceImplTest {

    @Mock private PVAuditionRepository pvAuditionRepository;
    @Mock private AuditionRepository auditionRepository;
    @Mock private DossierDetailsMapper mapper;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private PvAuditionServiceImpl service;

    @Test
    void create_savesPvWithContentAndDraftedBy() {
        Audition audition = Audition.builder().id(UUID.randomUUID()).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));
        when(pvAuditionRepository.findByAuditionId(audition.getId())).thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(pvAuditionRepository.save(any(PVAudition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVAudition.class)))
                .thenReturn(PvAuditionResponse.builder().build());

        service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("Procès-verbal...").build());

        verify(pvAuditionRepository).save(argThat(pv ->
                pv.getContent().equals("Procès-verbal...")
                        && pv.getDraftedBy() == agent
                        && pv.getAudition() == audition));
    }

    @Test
    void create_throwsWhenPvAlreadyExistsForAudition() {
        Audition audition = Audition.builder().id(UUID.randomUUID()).build();
        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));
        when(pvAuditionRepository.findByAuditionId(audition.getId()))
                .thenReturn(Optional.of(PVAudition.builder().build()));

        assertThatThrownBy(() -> service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeSignatures_throwsWhenSignedAndRefusedBothTrue() {
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(true)
                .build();

        assertThatThrownBy(() -> service.finalizeSignatures(pv.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeSignatures_throwsWhenAlreadyFinalized() {
        PVAudition pv = PVAudition.builder()
                .id(UUID.randomUUID())
                .finalizedAt(java.time.Instant.now())
                .build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(false)
                .build();

        assertThatThrownBy(() -> service.finalizeSignatures(pv.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByAuditionId_throwsWhenNoPvExists() {
        UUID auditionId = UUID.randomUUID();
        when(pvAuditionRepository.findByAuditionId(auditionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByAuditionId(auditionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

Note: `finalizeSignatures` in the service is looked up by **audition id** (via `pvAuditionRepository.findByAuditionId`), matching the controller's nested route `/auditions/{auditionId}/pv/finalize` — not by the PV's own id, since callers only know the audition id.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=PvAuditionServiceImplTest test`
Expected: FAIL — compilation error, DTOs/service/impl don't exist yet

- [ ] **Step 3: Create `PvAuditionCreateRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvAuditionCreateRequest {

    @NotBlank(message = "Le contenu du procès-verbal est obligatoire")
    private String content;
}
```

- [ ] **Step 4: Create `PvAuditionFinalizeRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvAuditionFinalizeRequest {

    @NotNull(message = "L'indicateur de signature est obligatoire")
    private Boolean intervieweeSigned;

    @NotNull(message = "L'indicateur de refus de signature est obligatoire")
    private Boolean intervieweeSignatureRefused;
}
```

- [ ] **Step 5: Create `PvAuditionResponse`**

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
public class PvAuditionResponse {
    private UUID id;
    private UUID auditionId;
    private String content;
    private String draftedByName;
    private Boolean intervieweeSigned;
    private Boolean intervieweeSignatureRefused;
    private Instant finalizedAt;
}
```

- [ ] **Step 6: Add `PVAudition` mapping to `DossierDetailsMapper`**

Add to `mapper/DossierDetailsMapper.java`:

```java
    @Mapping(target = "auditionId", source = "audition.id")
    @Mapping(target = "draftedByName", source = "draftedBy.nomComplet")
    PvAuditionResponse toResponse(PVAudition pvAudition);
```

- [ ] **Step 7: Create the service interface**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;

import java.util.UUID;

public interface PvAuditionService {

    PvAuditionResponse create(UUID auditionId, PvAuditionCreateRequest request);

    PvAuditionResponse finalizeSignatures(UUID auditionId, PvAuditionFinalizeRequest request);

    PvAuditionResponse findByAuditionId(UUID auditionId);
}
```

- [ ] **Step 8: Implement the service**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.service.PvAuditionService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PvAuditionServiceImpl implements PvAuditionService {

    private final PVAuditionRepository pvAuditionRepository;
    private final AuditionRepository   auditionRepository;
    private final DossierDetailsMapper mapper;
    private final AgentContextResolver agentContextResolver;

    @Override
    @Transactional
    public PvAuditionResponse create(UUID auditionId, PvAuditionCreateRequest request) {
        Audition audition = auditionRepository.findById(auditionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audition introuvable : " + auditionId));

        if (pvAuditionRepository.findByAuditionId(auditionId).isPresent()) {
            throw new BusinessException(
                    "Un procès-verbal existe déjà pour cette audition");
        }

        PVAudition pv = PVAudition.builder()
                .audition(audition)
                .content(request.getContent())
                .draftedBy(agentContextResolver.getCurrentAgent())
                .build();

        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition créé — audition: {}, id: {}", auditionId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PvAuditionResponse finalizeSignatures(UUID auditionId, PvAuditionFinalizeRequest request) {
        PVAudition pv = getPvOrThrow(auditionId);

        if (Boolean.TRUE.equals(request.getIntervieweeSigned())
                && Boolean.TRUE.equals(request.getIntervieweeSignatureRefused())) {
            throw new BusinessException(
                    "Un PV ne peut pas être à la fois signé et refusé par la personne auditionnée");
        }
        if (pv.isFinalized()) {
            throw new BusinessException("Ce procès-verbal est déjà finalisé");
        }

        pv.finalizeSignatures(
                Boolean.TRUE.equals(request.getIntervieweeSigned()),
                Boolean.TRUE.equals(request.getIntervieweeSignatureRefused()));

        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition finalisé — audition: {}", auditionId);
        return mapper.toResponse(saved);
    }

    @Override
    public PvAuditionResponse findByAuditionId(UUID auditionId) {
        return mapper.toResponse(getPvOrThrow(auditionId));
    }

    private PVAudition getPvOrThrow(UUID auditionId) {
        return pvAuditionRepository.findByAuditionId(auditionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procès-verbal introuvable pour l'audition : " + auditionId));
    }
}
```

- [ ] **Step 9: Run tests to verify they pass**

Run: `mvn -q -Dtest=PvAuditionServiceImplTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 10: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PvAuditionCreateRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PvAuditionFinalizeRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PvAuditionResponse.java src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java src/main/java/gov/bf/ascelc/univers_audits/service/PvAuditionService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImplTest.java
git commit -m "feat: add PvAuditionService with create/finalize and unit tests"
```

---

### Task 6: PV endpoints on `AuditionController`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java`

**Interfaces:**
- Consumes: `PvAuditionService.create/finalizeSignatures/findByAuditionId` (Task 5).

- [ ] **Step 1: Add the PV endpoints**

In `controller/AuditionController.java`, add the import, inject the new service, and add three endpoints. The full modified file:

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;
import gov.bf.ascelc.univers_audits.service.AuditionService;
import gov.bf.ascelc.univers_audits.service.PvAuditionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/auditions")
@RequiredArgsConstructor
public class AuditionController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','CGEA','ADMIN_DDIC')";

    private final AuditionService auditionService;
    private final PvAuditionService pvAuditionService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<AuditionResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(auditionService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> schedule(
            @PathVariable UUID investigationId,
            @Valid @RequestBody AuditionScheduleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(auditionService.schedule(investigationId, request));
    }

    @PatchMapping("/{auditionId}/conduct")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> conduct(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody AuditionConductRequest request) {
        return ResponseEntity.ok(auditionService.conduct(auditionId, request));
    }

    @PatchMapping("/{auditionId}/cancel")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> cancel(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @RequestParam String reason) {
        return ResponseEntity.ok(auditionService.cancel(auditionId, reason));
    }

    @GetMapping("/{auditionId}/pv")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<PvAuditionResponse> getPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId) {
        return ResponseEntity.ok(pvAuditionService.findByAuditionId(auditionId));
    }

    @PostMapping("/{auditionId}/pv")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PvAuditionResponse> createPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody PvAuditionCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(pvAuditionService.create(auditionId, request));
    }

    @PatchMapping("/{auditionId}/pv/finalize")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PvAuditionResponse> finalizePv(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody PvAuditionFinalizeRequest request) {
        return ResponseEntity.ok(pvAuditionService.finalizeSignatures(auditionId, request));
    }
}
```

- [ ] **Step 2: Compile the full project**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Run the entire new test suite together**

Run: `mvn -q -Dtest=AuditionServiceImplTest,PvAuditionServiceImplTest test`
Expected: `Tests run: 11, Failures: 0, Errors: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java
git commit -m "feat: add PV d'audition REST endpoints"
```

---

## Self-Review

**Spec coverage:**
- §5 gap "`Audition`, `PVAudition`... sans équivalent dans le code" → both entities built, with the interviewee correctly modeled as a mutually-exclusive link to `TargetedParty` or `Witness` (Task 1, 4).
- Priority-3 recommendation ("Objets d'enquête — `Audition`/`PVAudition`... les deux actes les plus fréquents du Processus C") → covered end-to-end: schedule → conduct/cancel → draft PV → finalize with signature/refusal (Tasks 1-6).
- §0 principle ("chaque acte doit correspondre à une transition tracée, horodatée, signée et opposable") → `Audition` transitions are timestamped (`scheduledAt`/`conductedAt`) and status-tracked; `PVAudition` finalization is timestamped and captures the interviewee's signature or explicit refusal, never silently defaulting either way (finalize requires an explicit boolean, not inferred).
- §3 confidentiality rule ("l'appartenance à un rôle ne donne accès à aucun dossier") → `AuditionServiceImpl.findByInvestigationId` reuses the exact `DossierAccessGuard.canSeeConfidential()` check already governing `Witness`/`TargetedParty` visibility (Task 2).
- `DemandeDocuments` with its 3-stage escalation (the other half of priority-3) is explicitly out of scope for this plan — it is an independent subsystem (a document-request workflow with time-based escalation, not an interview record) and belongs in its own plan per the `writing-plans` skill's scope-check guidance.

**Placeholder scan:** no TBD/TODO, no "add appropriate validation" — every step shows complete, compilable code including the cross-field mutual-exclusivity check and the signed/refused mutual-exclusivity check.

**Type consistency:** `AuditionService.schedule/conduct/cancel/findByInvestigationId` (Task 2) signatures match `AuditionController`'s calls (Task 3, extended in Task 6). `PvAuditionService.create/finalizeSignatures/findByAuditionId` (Task 5) signatures match `AuditionController`'s PV endpoints (Task 6). `PVAuditionRepository.findByAuditionId` (Task 4) is what `PvAuditionServiceImpl` calls throughout (Task 5) — consistent with the note under Task 5's tests that finalize is looked up by audition id, not PV id.

---

**Plan complete and saved to `docs/superpowers/plans/2026-07-25-audition-pv-audition.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
