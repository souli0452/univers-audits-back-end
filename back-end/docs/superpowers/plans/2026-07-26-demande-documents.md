# Demande de documents et escalade — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track document requests sent to an institution or third party during an investigation as a tracked entity with a 3-stage escalation (relance → sommation → saisine judiciaire), per the ASCE-LC compliance report's §5 gap analysis — the second half of the priority-3 recommendation whose first half (`Audition`/`PVAudition`) was deliberately scoped out of that earlier plan as an independent subsystem.

**Architecture:** One new JPA entity, `DemandeDocuments`, nested under `Investigation` (same nesting choice as `Audition`), following the exact same dossier-scoped sub-resource pattern (entity → repository → service → controller, `DossierDetailsMapper` for response mapping, `DossierAccessGuard` for access control and confidentiality masking). Escalation is an explicit agent-triggered action, not an automated background job — this codebase already has `@Scheduled` infrastructure (`NotificationServiceImpl.sendDeadlineAlerts`), but automatically escalating to `SAISINE_JUDICIAIRE` (referral to justice) without human review would be a real procedural overreach; an agent must trigger each escalation step once the current deadline has passed. Escalation deadlines are resolved through the `ParametreDelaiService` built in the earlier referentials plan.

**Tech Stack:** Spring Boot 3.3.5 / Java 21, Spring Data JPA, Liquibase (raw SQL changesets), Lombok (`@SuperBuilder`), MapStruct (`DossierDetailsMapper`), JUnit 5 + Mockito.

## Global Constraints

- Package root: `gov.bf.ascelc.univers_audits`.
- New entity extends `abstracts.AuditEntity` — do not redeclare `id`/`createdAt`/`updatedAt`/`createdById`/`updatedById`/`version`.
- Entity style matches `Audition` (from the prior plan): `@Getter @Setter @Entity @SuperBuilder @NoArgsConstructor @AllArgsConstructor`, explicit `@Table(name=..., indexes = {@Index(...)})`, `@Builder.Default` on every column with a Java-side default.
- Enum style matches `InvestigationStatus`/`AuditionStatus`: plain English identifiers, French one-line comment above each value.
- `DemandeDocuments` is nested under `Investigation`, not `Dossier` directly — same reasoning as `Audition`.
- **Authorization — this is the exact gap the final review caught in the prior plan (`PvAuditionServiceImpl` shipped with zero access control and had to be fixed after the fact). Do not repeat it here:** every public method of `DemandeDocumentsService` — `create`, `markReceived`, `escalate`, `findByInvestigationId` — MUST call `shared.utils.DossierAccessGuard.checkReadAccess(dossier)` before doing anything else, resolving the dossier via `investigation.getDossier()` (for `create`/`findByInvestigationId`) or `demandeDocuments.getInvestigation().getDossier()` (for `markReceived`/`escalate`). `findByInvestigationId` must additionally return an empty list when the dossier is confidential and the current agent cannot see confidential dossiers (`DossierAccessGuard.canSeeConfidential()`), checked AFTER `checkReadAccess` — matching `AuditionServiceImpl.findByInvestigationId`'s final, corrected form exactly.
- No fabricated delay values: this plan adds three new `ParametreDelai` referential rows (`DEMANDE_DOCUMENTS_INITIAL`, `DEMANDE_DOCUMENTS_RELANCE`, `DEMANDE_DOCUMENTS_SOMMATION`) with `valeurJours = NULL` — the row exists (so an admin can `PUT /api/v1/parametres-delai/{code}` to configure it, per the existing endpoint from the referentials plan, which only updates existing rows) but no day-count is invented. Until an admin sets a real value, `ParametreDelaiService.resolveDelaiJours(code)` throws `ResourceNotFoundException` ("sans valeur configurée") — this is the intended, honest behavior, not a bug: the feature requires configuration before first use, exactly matching the source spec's own principle ("tous les délais sont des paramètres administrables... jamais codés en dur").
- Escalation levels are exactly three stages plus the initial state: `INITIAL → RELANCE → SOMMATION → SAISINE_JUDICIAIRE`. `SAISINE_JUDICIAIRE` is terminal — attempting to escalate past it throws `BusinessException`.
- Escalating is only permitted when the request is actually overdue (`DemandeDocuments.isOverdue()` — deadline passed and not yet received) and not already received — enforced in the service, not the entity (entity methods stay plain setters, matching `Audition`'s style).
- Migrations live in `src/main/resources/db/changelog/migrations/`, auto-included via `includeAll`. Next free numeric prefix: `008` (existing: `001`–`007`).
- Not-found → `ResourceNotFoundException` (404). Invalid transitions / access denial → `BusinessException`.
- Role restrictions mirror `Audition` exactly: read (list) open to `CGEA`, `CGE`, `CONTROLEUR_ETAT`, `MEMBRE_CTADP`, `CONSEILLER_JURIDIQUE`, `ADMIN_DDIC`; write (create/mark-received/escalate) restricted to `CONTROLEUR_ETAT`, `CGEA`, `ADMIN_DDIC`.
- Out of scope (flag as follow-up, do not build now): automated `@Scheduled` overdue-detection/reminder job (a natural next step once this subsystem is proven, mirroring `NotificationServiceImpl.sendDeadlineAlerts`'s pattern, but a new scope not requested here); linking a `DemandeDocuments` to the `Attachment`(s) eventually received in response (received documents are uploaded as ordinary investigation attachments via the existing `AttachmentController` — no new coupling).

---

## File Structure

```
src/main/java/gov/bf/ascelc/univers_audits/
  enums/EscalationLevel.java                              (new)
  model/entity/DemandeDocuments.java                       (new)
  repository/DemandeDocumentsRepository.java               (new)
  model/dto/request/DemandeDocumentsCreateRequest.java     (new)
  model/dto/response/DemandeDocumentsResponse.java         (new)
  mapper/DossierDetailsMapper.java                         (modify: add DemandeDocuments mapping)
  service/DemandeDocumentsService.java                     (new, interface)
  service/impl/DemandeDocumentsServiceImpl.java            (new)
  controller/DemandeDocumentsController.java               (new)

src/main/resources/db/changelog/migrations/
  008-create-demande-documents.sql                         (new)

src/test/java/gov/bf/ascelc/univers_audits/
  service/impl/DemandeDocumentsServiceImplTest.java        (new)
```

---

### Task 1: `EscalationLevel` enum, `DemandeDocuments` entity, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/EscalationLevel.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/DemandeDocuments.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/DemandeDocumentsRepository.java`
- Create: `src/main/resources/db/changelog/migrations/008-create-demande-documents.sql`

**Interfaces:**
- Produces: `DemandeDocuments` entity with fields `investigation:Investigation`, `recipientLabel:String`, `documentsRequested:String`, `requestedBy:Agent`, `sentAt:Instant`, `deadline:Instant`, `escalationLevel:EscalationLevel`, `received:Boolean`, `receivedAt:Instant` (nullable); methods `markReceived()`, `escalate(EscalationLevel newLevel, int deadlineDays)`, `isOverdue():boolean`, `nextEscalationLevel():EscalationLevel` (nullable — `null` when already at the terminal level). `DemandeDocumentsRepository.findByInvestigationIdOrderBySentAtDesc(UUID):List<DemandeDocuments>`.

- [ ] **Step 1: Create `EscalationLevel`**

```java
package gov.bf.ascelc.univers_audits.enums;

/**
 * Niveau d'escalade d'une demande de documents non satisfaite.
 */
public enum EscalationLevel {
    // Demande initiale envoyée
    INITIAL,
    // Relance après dépassement du premier délai
    RELANCE,
    // Sommation après dépassement du délai de relance
    SOMMATION,
    // Saisine judiciaire après dépassement du délai de sommation
    SAISINE_JUDICIAIRE
}
```

- [ ] **Step 2: Create the `DemandeDocuments` entity**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.EscalationLevel;
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
@Table(name = "demande_documents", indexes = {
        // Toutes les demandes de documents d'une investigation
        @Index(name = "idx_demande_documents_investigation",
                columnList = "investigation_id")
})
public class DemandeDocuments extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "recipient_label", nullable = false, length = 300)
    private String recipientLabel;

    @Column(name = "documents_requested", nullable = false, columnDefinition = "TEXT")
    private String documentsRequested;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Agent requestedBy;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_level", nullable = false, length = 20)
    @Builder.Default
    private EscalationLevel escalationLevel = EscalationLevel.INITIAL;

    @Column(name = "received", nullable = false)
    @Builder.Default
    private Boolean received = false;

    @Column(name = "received_at")
    private Instant receivedAt;

    public void markReceived() {
        this.received = true;
        this.receivedAt = Instant.now();
    }

    public void escalate(EscalationLevel newLevel, int deadlineDays) {
        this.escalationLevel = newLevel;
        this.sentAt = Instant.now();
        this.deadline = sentAt.plusSeconds(deadlineDays * 24L * 3600);
    }

    public boolean isOverdue() {
        return !Boolean.TRUE.equals(received)
                && deadline != null
                && Instant.now().isAfter(deadline);
    }

    public EscalationLevel nextEscalationLevel() {
        int nextOrdinal = escalationLevel.ordinal() + 1;
        EscalationLevel[] levels = EscalationLevel.values();
        return nextOrdinal < levels.length ? levels[nextOrdinal] : null;
    }
}
```

- [ ] **Step 3: Create the repository**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandeDocumentsRepository extends JpaRepository<DemandeDocuments, UUID> {

    List<DemandeDocuments> findByInvestigationIdOrderBySentAtDesc(UUID investigationId);
}
```

- [ ] **Step 4: Create the migration (table + 3 unconfigured `ParametreDelai` rows)**

```sql
--liquibase formatted sql
--changeset dev:008-create-demande-documents

CREATE TABLE demande_documents (
    id                    UUID PRIMARY KEY,
    investigation_id      UUID         NOT NULL REFERENCES investigation(id),
    recipient_label       VARCHAR(300) NOT NULL,
    documents_requested   TEXT         NOT NULL,
    requested_by_id       UUID         NOT NULL REFERENCES agent(id),
    sent_at               TIMESTAMP    NOT NULL,
    deadline              TIMESTAMP    NOT NULL,
    escalation_level      VARCHAR(20)  NOT NULL DEFAULT 'INITIAL',
    received              BOOLEAN      NOT NULL DEFAULT FALSE,
    received_at           TIMESTAMP,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP,
    created_by_id         VARCHAR(100),
    updated_by_id         VARCHAR(100)
);

CREATE INDEX idx_demande_documents_investigation ON demande_documents (investigation_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_INITIAL', 'Délai de réponse à une demande initiale de documents', NULL, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_RELANCE', 'Délai de réponse après relance', NULL, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_SOMMATION', 'Délai de réponse après sommation', NULL, TRUE, TRUE, 0, now());
```

- [ ] **Step 5: Compile to verify no syntax errors**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/EscalationLevel.java src/main/java/gov/bf/ascelc/univers_audits/model/entity/DemandeDocuments.java src/main/java/gov/bf/ascelc/univers_audits/repository/DemandeDocumentsRepository.java src/main/resources/db/changelog/migrations/008-create-demande-documents.sql
git commit -m "feat: add DemandeDocuments entity, repository and migration"
```

---

### Task 2: `DemandeDocumentsService` with access control and unit tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DemandeDocumentsCreateRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DemandeDocumentsResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/DemandeDocumentsService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DemandeDocumentsServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DemandeDocumentsServiceImplTest.java`

**Interfaces:**
- Consumes: `DemandeDocumentsRepository` (Task 1), `InvestigationRepository.findById` (existing), `ParametreDelaiService.resolveDelaiJours(String):int` (existing, from the referentials plan), `AgentContextResolver.getCurrentAgent()` (existing), `DossierAccessGuard.checkReadAccess`/`canSeeConfidential` (existing).
- Produces: `DemandeDocumentsService.create(UUID investigationId, DemandeDocumentsCreateRequest):DemandeDocumentsResponse`, `.markReceived(UUID id):DemandeDocumentsResponse`, `.escalate(UUID id):DemandeDocumentsResponse`, `.findByInvestigationId(UUID investigationId):List<DemandeDocumentsResponse>` — these four signatures are what Task 3's controller calls.

- [ ] **Step 1: Write the failing tests**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.EscalationLevel;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DemandeDocumentsResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
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
class DemandeDocumentsServiceImplTest {

    @Mock private DemandeDocumentsRepository demandeDocumentsRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private ParametreDelaiService parametreDelaiService;
    @Mock private DossierDetailsMapper mapper;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private DossierAccessGuard accessGuard;

    @InjectMocks
    private DemandeDocumentsServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
    }

    @Test
    void create_resolvesInitialDeadlineAndSaves() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(parametreDelaiService.resolveDelaiJours("DEMANDE_DOCUMENTS_INITIAL"))
                .thenReturn(15);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(demandeDocumentsRepository.save(any(DemandeDocuments.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(DemandeDocuments.class)))
                .thenReturn(DemandeDocumentsResponse.builder().build());

        DemandeDocumentsCreateRequest request = DemandeDocumentsCreateRequest.builder()
                .recipientLabel("Banque XYZ")
                .documentsRequested("Relevés de compte 2024-2025")
                .build();

        service.create(investigation.getId(), request);

        verify(demandeDocumentsRepository).save(argThat(d ->
                d.getRecipientLabel().equals("Banque XYZ")
                        && d.getEscalationLevel() == EscalationLevel.INITIAL
                        && d.getRequestedBy() == agent
                        && d.getDeadline() != null));
    }

    @Test
    void create_throwsWhenAgentLacksReadAccess() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkReadAccess(dossier);

        DemandeDocumentsCreateRequest request = DemandeDocumentsCreateRequest.builder()
                .recipientLabel("Banque XYZ")
                .documentsRequested("Relevés")
                .build();

        assertThatThrownBy(() -> service.create(investigation.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void escalate_movesToRelanceWhenOverdue() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        DemandeDocuments demande = DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .escalationLevel(EscalationLevel.INITIAL)
                .received(false)
                .deadline(Instant.now().minusSeconds(3600))
                .build();

        when(demandeDocumentsRepository.findById(demande.getId()))
                .thenReturn(Optional.of(demande));
        when(parametreDelaiService.resolveDelaiJours("DEMANDE_DOCUMENTS_RELANCE"))
                .thenReturn(7);
        when(demandeDocumentsRepository.save(any(DemandeDocuments.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(DemandeDocuments.class)))
                .thenReturn(DemandeDocumentsResponse.builder().build());

        service.escalate(demande.getId());

        assertThat(demande.getEscalationLevel()).isEqualTo(EscalationLevel.RELANCE);
    }

    @Test
    void escalate_throwsWhenNotYetOverdue() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        DemandeDocuments demande = DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .escalationLevel(EscalationLevel.INITIAL)
                .received(false)
                .deadline(Instant.now().plusSeconds(3600))
                .build();

        when(demandeDocumentsRepository.findById(demande.getId()))
                .thenReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.escalate(demande.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void escalate_throwsWhenAlreadyAtTerminalLevel() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        DemandeDocuments demande = DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .escalationLevel(EscalationLevel.SAISINE_JUDICIAIRE)
                .received(false)
                .deadline(Instant.now().minusSeconds(3600))
                .build();

        when(demandeDocumentsRepository.findById(demande.getId()))
                .thenReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.escalate(demande.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByInvestigationId_returnsEmptyWhenConfidentialAndNotAuthorized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<DemandeDocumentsResponse> result = service.findByInvestigationId(investigation.getId());

        assertThat(result).isEmpty();
        verify(demandeDocumentsRepository, never()).findByInvestigationIdOrderBySentAtDesc(any());
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

Run: `mvn -q -Dtest=DemandeDocumentsServiceImplTest test`
Expected: FAIL — compilation error, DTOs/service/impl don't exist yet

- [ ] **Step 3: Create `DemandeDocumentsCreateRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeDocumentsCreateRequest {

    @NotBlank(message = "Le destinataire est obligatoire")
    @Size(max = 300)
    private String recipientLabel;

    @NotBlank(message = "La description des documents demandés est obligatoire")
    private String documentsRequested;
}
```

- [ ] **Step 4: Create `DemandeDocumentsResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.EscalationLevel;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeDocumentsResponse {
    private UUID id;
    private UUID investigationId;
    private String recipientLabel;
    private String documentsRequested;
    private String requestedByName;
    private Instant sentAt;
    private Instant deadline;
    private EscalationLevel escalationLevel;
    private Boolean received;
    private Instant receivedAt;
    private Boolean overdue;
}
```

- [ ] **Step 5: Add `DemandeDocuments` mapping to `DossierDetailsMapper`**

Add to `mapper/DossierDetailsMapper.java` (inside the existing interface, alongside the other `toResponse` overloads):

```java
    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "requestedByName", source = "requestedBy.nomComplet")
    @Mapping(target = "overdue", ignore = true)
    DemandeDocumentsResponse toResponse(DemandeDocuments demandeDocuments);

    @AfterMapping
    default void fillDemandeDocuments(
            DemandeDocuments demandeDocuments,
            @MappingTarget DemandeDocumentsResponse response) {
        response.setOverdue(demandeDocuments.isOverdue());
    }
```

- [ ] **Step 6: Create the service interface**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DemandeDocumentsResponse;

import java.util.List;
import java.util.UUID;

public interface DemandeDocumentsService {

    DemandeDocumentsResponse create(UUID investigationId, DemandeDocumentsCreateRequest request);

    DemandeDocumentsResponse markReceived(UUID id);

    DemandeDocumentsResponse escalate(UUID id);

    List<DemandeDocumentsResponse> findByInvestigationId(UUID investigationId);
}
```

- [ ] **Step 7: Implement the service**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.EscalationLevel;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DemandeDocumentsResponse;
import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.service.DemandeDocumentsService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemandeDocumentsServiceImpl implements DemandeDocumentsService {

    private final DemandeDocumentsRepository demandeDocumentsRepository;
    private final InvestigationRepository    investigationRepository;
    private final ParametreDelaiService      parametreDelaiService;
    private final DossierDetailsMapper       mapper;
    private final AgentContextResolver       agentContextResolver;
    private final DossierAccessGuard         accessGuard;

    @Override
    @Transactional
    public DemandeDocumentsResponse create(UUID investigationId, DemandeDocumentsCreateRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        int deadlineDays = parametreDelaiService.resolveDelaiJours("DEMANDE_DOCUMENTS_INITIAL");
        Instant sentAt = Instant.now();

        DemandeDocuments demande = DemandeDocuments.builder()
                .investigation(investigation)
                .recipientLabel(request.getRecipientLabel())
                .documentsRequested(request.getDocumentsRequested())
                .requestedBy(agentContextResolver.getCurrentAgent())
                .sentAt(sentAt)
                .deadline(sentAt.plusSeconds(deadlineDays * 24L * 3600))
                .build();

        DemandeDocuments saved = demandeDocumentsRepository.save(demande);
        log.info("Demande de documents créée — investigation: {}, id: {}", investigationId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DemandeDocumentsResponse markReceived(UUID id) {
        DemandeDocuments demande = getOrThrow(id);
        accessGuard.checkReadAccess(demande.getInvestigation().getDossier());

        demande.markReceived();
        DemandeDocuments saved = demandeDocumentsRepository.save(demande);
        log.info("Demande de documents reçue — id: {}", id);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DemandeDocumentsResponse escalate(UUID id) {
        DemandeDocuments demande = getOrThrow(id);
        accessGuard.checkReadAccess(demande.getInvestigation().getDossier());

        if (Boolean.TRUE.equals(demande.getReceived())) {
            throw new BusinessException("Cette demande a déjà été satisfaite, elle ne peut pas être escaladée");
        }
        if (!demande.isOverdue()) {
            throw new BusinessException("Cette demande n'est pas encore en retard, elle ne peut pas être escaladée");
        }
        EscalationLevel nextLevel = demande.nextEscalationLevel();
        if (nextLevel == null) {
            throw new BusinessException("Cette demande est déjà au niveau d'escalade maximal (saisine judiciaire)");
        }

        int deadlineDays = parametreDelaiService.resolveDelaiJours(delaiCodeFor(nextLevel));
        demande.escalate(nextLevel, deadlineDays);

        DemandeDocuments saved = demandeDocumentsRepository.save(demande);
        log.info("Demande de documents escaladée — id: {}, niveau: {}", id, nextLevel);
        return mapper.toResponse(saved);
    }

    @Override
    public List<DemandeDocumentsResponse> findByInvestigationId(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return demandeDocumentsRepository.findByInvestigationIdOrderBySentAtDesc(investigationId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private String delaiCodeFor(EscalationLevel level) {
        return switch (level) {
            case RELANCE -> "DEMANDE_DOCUMENTS_RELANCE";
            case SOMMATION -> "DEMANDE_DOCUMENTS_SOMMATION";
            default -> throw new BusinessException(
                    "Aucun délai configuré pour le niveau d'escalade : " + level);
        };
    }

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

    private DemandeDocuments getOrThrow(UUID id) {
        return demandeDocumentsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demande de documents introuvable : " + id));
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn -q -Dtest=DemandeDocumentsServiceImplTest test`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DemandeDocumentsCreateRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DemandeDocumentsResponse.java src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java src/main/java/gov/bf/ascelc/univers_audits/service/DemandeDocumentsService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/DemandeDocumentsServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/DemandeDocumentsServiceImplTest.java
git commit -m "feat: add DemandeDocumentsService with escalation and access control"
```

---

### Task 3: `DemandeDocumentsController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/DemandeDocumentsController.java`

**Interfaces:**
- Consumes: `DemandeDocumentsService.create/markReceived/escalate/findByInvestigationId` (Task 2).

- [ ] **Step 1: Create the controller**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DemandeDocumentsResponse;
import gov.bf.ascelc.univers_audits.service.DemandeDocumentsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/demandes-documents")
@RequiredArgsConstructor
public class DemandeDocumentsController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','CGEA','ADMIN_DDIC')";

    private final DemandeDocumentsService demandeDocumentsService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<DemandeDocumentsResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(demandeDocumentsService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<DemandeDocumentsResponse> create(
            @PathVariable UUID investigationId,
            @Valid @RequestBody DemandeDocumentsCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(demandeDocumentsService.create(investigationId, request));
    }

    @PatchMapping("/{id}/mark-received")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<DemandeDocumentsResponse> markReceived(
            @PathVariable UUID investigationId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(demandeDocumentsService.markReceived(id));
    }

    @PatchMapping("/{id}/escalate")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<DemandeDocumentsResponse> escalate(
            @PathVariable UUID investigationId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(demandeDocumentsService.escalate(id));
    }
}
```

- [ ] **Step 2: Compile the full project**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Run the entire new test suite**

Run: `mvn -q -Dtest=DemandeDocumentsServiceImplTest test`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/DemandeDocumentsController.java
git commit -m "feat: add DemandeDocuments REST endpoints"
```

---

## Self-Review

**Spec coverage:**
- §5 gap "`DemandeDocuments` et son escalade (relance, sommation, saisine judiciaire)" → `DemandeDocuments` entity with `EscalationLevel` 3-stage progression, built end-to-end (Tasks 1-3).
- Priority-3 recommendation's second half (the first half, `Audition`/`PVAudition`, was completed in the prior plan) → this plan closes it out.
- §7 principle ("tous les délais sont des paramètres administrables") → all three escalation deadlines resolve through `ParametreDelaiService`, with genuinely unconfigured (not fabricated) seed values that an admin must set before the feature is usable.
- Authorization discipline learned from the prior plan's final-review Critical finding → every `DemandeDocumentsService` method calls `checkReadAccess` from the start (Task 2), not retrofitted after the fact.

**Placeholder scan:** no TBD/TODO, no "add appropriate validation" — every step shows complete, compilable code, including the escalation-eligibility guards (not received, actually overdue, not already terminal).

**Type consistency:** `DemandeDocuments.escalate(EscalationLevel, int)` (Task 1) matches its only call site in `DemandeDocumentsServiceImpl.escalate` (Task 2). `ParametreDelaiService.resolveDelaiJours(String):int` (existing, from the referentials plan) matches its three call sites (Task 2: initial creation, relance, sommation). `DemandeDocumentsService.create/markReceived/escalate/findByInvestigationId` (Task 2) signatures match `DemandeDocumentsController`'s calls exactly (Task 3).

---

**Plan complete and saved to `docs/superpowers/plans/2026-07-26-demande-documents.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
