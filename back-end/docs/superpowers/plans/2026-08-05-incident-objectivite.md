# Incident d'objectivité Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let any agent holding a legitimate dossier habilitation declare a permanent,
untraceable-to-delete objectivity incident on an investigation, and notify the
investigation's CGE (via the existing `Mandat`) when one is declared.

**Architecture:** A new `IncidentObjectivite` entity, N:1 with `Investigation` (many
incidents allowed per investigation), append-only — no update/delete exposed. Access is
gated by a functional role list (same as `AuditionController`'s read roles) **plus** a
second, dossier-specific habilitation check via the existing `DossierAccessGuard`
(exactly the same two-layer pattern `AuditionServiceImpl` already uses), rather than
either a narrow investigation-team-only check or an unrestricted self-service endpoint.
CGE notification reuses the existing `Notification`/`NotificationRepository`
portal-notification mechanism already established by `sendMemberAddedNotifications`,
targeted at `Mandat.agentCGE` (the specific CGE tied to this investigation) rather than
a role-wide broadcast, which this codebase has no mechanism for.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL),
Lombok `@SuperBuilder`, JUnit 5 + Mockito.

## Global Constraints

- `IncidentObjectivite` fields: `investigation` (FK, not unique — many incidents
  allowed), `declaredBy` (FK `Agent`, always `agentContextResolver.getCurrentAgent()`,
  never a request field), `description` (text, required), `declaredAt` (`Instant`,
  required).
- No update, no delete, no status field — permanent and immutable by construction. Do
  not add any method beyond `declareIncident`/`getIncidents` in this plan.
- Access control is two layers, mirroring `AuditionServiceImpl`'s existing pattern
  exactly:
  1. Controller `@PreAuthorize` with the functional role list
     `hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')`
     (identical to `AuditionController.READ_ROLES`) on both endpoints.
  2. Inside the service, `accessGuard.checkReadAccess(investigation.getDossier())` —
     throws `BusinessException` if the current agent holds none of the privileged
     roles (`CGE`/`CGEA`/`ADMIN_DDIC`, see `DossierAccessGuard.canSeeConfidential()`)
     AND has no active nominative habilitation on this specific dossier. Call this on
     BOTH `declareIncident` and `getIncidents`, before any other logic.
- CGE notification: look up `Mandat` via `mandatRepository.findByInvestigationId(...)`
  (already exists from sub-chantier 1/6); if present, create a `Notification`
  (`channel=PORTAL`, `type=INTERNAL_ALERT`, `recipient=mandat.getAgentCGE().getKeycloakId()`)
  targeted at that specific CGE agent. If no `Mandat` exists yet, skip notification
  silently — the incident is still created and traced. Wrap the notification-creation
  block in `try/catch (Exception e)` with `log.error(...)`, exactly mirroring
  `sendMemberAddedNotifications`'s existing try/catch around its own
  `notificationRepository.save(...)` call — a notification failure must never block or
  fail the incident declaration itself.
- No email (`EmailService` is not touched by this plan — deliberate scope boundary, see
  spec). No new `PortalConfigService` template key — notification subject/content are
  hardcoded French strings.
- `auditRecorder.addObservation(...)` on `declareIncident` is appropriate here (unlike
  sub-chantier 2/6's `declareEngagementPrealable`, whose unrestricted `isAuthenticated()`
  access led to a dossier-timeline write being removed at that sub-chantier's final
  review) — `declareIncident` is gated by `DossierAccessGuard.checkReadAccess`
  (nominative habilitation), not open self-service, so writing to this specific
  dossier's own timeline is legitimate.
- `InvestigationServiceImpl` uses `@RequiredArgsConstructor` with a manually-ordered
  field list; append the new `IncidentObjectiviteRepository` field AND the new
  `DossierAccessGuard accessGuard` field at the end. `InvestigationServiceImplTest`
  uses `@InjectMocks` only (no manual positional constructor), so field order is safe.
- `AuditEntity` columns for the new table: `id UUID PRIMARY KEY`,
  `version BIGINT NOT NULL DEFAULT 0`, `created_at TIMESTAMP NOT NULL`,
  `updated_at TIMESTAMP`, `created_by_id VARCHAR(100)`, `updated_by_id VARCHAR(100)`.
- Next migration file number is `022` (last is `021-add-plan-investigation.sql`). This
  is a brand-new table with no enum column, so it does not need the CHECK-constraint
  drop/recreate dance that migration `019` needed for a different, pre-existing
  Hibernate-bootstrapped table.
- Existing wildcard imports already cover most new classes without new import lines:
  `InvestigationServiceImpl.java` has `import ...model.dto.request.*;`,
  `import ...model.entity.*;`, `import ...repository.*;` — only
  `IncidentObjectiviteResponse` needs an explicit import (response DTOs are imported
  individually in this file). `InvestigationService.java` and
  `InvestigationController.java` import both request and response DTOs explicitly (no
  wildcards) — both need explicit imports for the 2 new DTOs.
- `DossierAccessGuard` is an existing `@Component` (`shared/utils/DossierAccessGuard.java`)
  with a public `checkReadAccess(Dossier)` method that throws `BusinessException` — no
  new class needed, just inject it. Field name convention in this codebase for this
  exact dependency is `accessGuard` (see `AuditionServiceImpl`).

---

### Task 1: `IncidentObjectivite` entity, repository, DTOs, migration 022

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/IncidentObjectivite.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/IncidentObjectiviteRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/IncidentObjectiviteRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/IncidentObjectiviteResponse.java`
- Create: `src/main/resources/db/changelog/migrations/022-add-incident-objectivite.sql`

**Interfaces:**
- Consumes: `Investigation`, `Agent` entities (`Agent` has `getNomComplet()`),
  `AuditEntity` base class.
- Produces: `IncidentObjectivite` entity (fields per Global Constraints) — consumed by
  Task 2.
- Produces: `IncidentObjectiviteRepository.findByInvestigationIdOrderByDeclaredAtDesc(UUID):
  List<IncidentObjectivite>` — consumed by Task 2.
- Produces: `IncidentObjectiviteRequest` (`description` `@NotBlank`),
  `IncidentObjectiviteResponse` (`id, investigationId, declaredById, declaredByNom,
  description, declaredAt`) — consumed by Task 2 and Task 3.

- [ ] **Step 1: Create the `IncidentObjectivite` entity**

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
@Table(name = "incident_objectivite", indexes = {
        @Index(name = "idx_incident_objectivite_investigation",
                columnList = "investigation_id")
})
public class IncidentObjectivite extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declared_by_id", nullable = false)
    private Agent declaredBy;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "declared_at", nullable = false)
    private Instant declaredAt;
}
```

- [ ] **Step 2: Create `IncidentObjectiviteRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.IncidentObjectivite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentObjectiviteRepository
        extends JpaRepository<IncidentObjectivite, UUID> {

    List<IncidentObjectivite> findByInvestigationIdOrderByDeclaredAtDesc(
            UUID investigationId);
}
```

- [ ] **Step 3: Create `IncidentObjectiviteRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentObjectiviteRequest {

    @NotBlank(message = "La description de l'incident est obligatoire")
    private String description;
}
```

- [ ] **Step 4: Create `IncidentObjectiviteResponse`**

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
public class IncidentObjectiviteResponse {

    private UUID id;
    private UUID investigationId;
    private UUID declaredById;
    private String declaredByNom;
    private String description;
    private Instant declaredAt;
}
```

- [ ] **Step 5: Write migration 022**

Create `src/main/resources/db/changelog/migrations/022-add-incident-objectivite.sql`:

```sql
--liquibase formatted sql
--changeset dev:022-add-incident-objectivite

CREATE TABLE incident_objectivite (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    investigation_id  UUID NOT NULL REFERENCES investigation(id),
    declared_by_id    UUID NOT NULL REFERENCES agent(id),
    description       TEXT NOT NULL,
    declared_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_incident_objectivite_investigation
    ON incident_objectivite (investigation_id);

COMMENT ON TABLE incident_objectivite IS 'Declaration d incident d objectivite par un agent affecte au dossier - tracee, permanente, distincte de la declaration de conflit d interets prealable (Lot 3, plan de travail S8.4/S11)';
```

This migration is auto-discovered by `db.changelog-master.yaml`'s `includeAll` on
`db/changelog/migrations/` — no changelog master edit needed. No unique constraint
exists on this table, so the plain index is not redundant (unlike a prior lesson in
this Lot about duplicating a unique constraint as an index).

- [ ] **Step 6: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (new classes only, nothing consumes them yet).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/IncidentObjectivite.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/IncidentObjectiviteRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/IncidentObjectiviteRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/IncidentObjectiviteResponse.java \
        src/main/resources/db/changelog/migrations/022-add-incident-objectivite.sql
git commit -m "feat: add IncidentObjectivite entity, repository, DTOs and migration"
```

---

### Task 2: Service layer — declareIncident/getIncidents, access guard, CGE notification, tests

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `IncidentObjectivite`/`IncidentObjectiviteRepository`/DTOs (Task 1),
  `DossierAccessGuard.checkReadAccess(Dossier)` (existing, `shared/utils/DossierAccessGuard.java`),
  `Mandat`/`MandatRepository.findByInvestigationId` (existing, sub-chantier 1/6),
  `Notification`/`NotificationRepository` (existing).
- Produces: `InvestigationService.declareIncident(UUID investigationId,
  IncidentObjectiviteRequest request, String ipAddress): IncidentObjectiviteResponse`
  and `.getIncidents(UUID investigationId): List<IncidentObjectiviteResponse>` —
  consumed by Task 3's controller.

- [ ] **Step 1: Add imports and fields to `InvestigationServiceImpl`**

Add this import next to the existing
`import gov.bf.ascelc.univers_audits.model.dto.response.RevisionPlanResponse;`:

```java
import gov.bf.ascelc.univers_audits.model.dto.response.IncidentObjectiviteResponse;
```

Add this import for the access guard:

```java
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
```

Add these fields at the end of the existing field list (after
`private final RevisionPlanRepository revisionPlanRepository;`):

```java
    private final IncidentObjectiviteRepository   incidentObjectiviteRepository;
    private final DossierAccessGuard              accessGuard;
```

(`IncidentObjectivite` and `IncidentObjectiviteRequest` are already covered by this
file's existing `import ...model.entity.*;` and `import ...model.dto.request.*;`
wildcards — no new import lines needed for either.)

- [ ] **Step 2: Add `declareIncident` and `getIncidents`**

Add these methods right after `getPlanRevisions` (before `buildResponseWithFreshMembers`):

```java
    @Override
    @Transactional
    public IncidentObjectiviteResponse declareIncident(
            UUID investigationId,
            IncidentObjectiviteRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(inv.getDossier());

        Agent currentAgent = agentContextResolver.getCurrentAgent();

        IncidentObjectivite incident = IncidentObjectivite.builder()
                .investigation(inv)
                .declaredBy(currentAgent)
                .description(request.getDescription())
                .declaredAt(Instant.now())
                .build();
        IncidentObjectivite saved = incidentObjectiviteRepository.save(incident);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Incident d'objectivité déclaré par " + currentAgent.getNomComplet(),
                true, currentAgent);

        notifyCgeOfIncident(inv, currentAgent);

        log.info("Incident d'objectivité déclaré — investigation: {}, agent: {}",
                investigationId, currentAgent.getId());
        return toIncidentObjectiviteResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentObjectiviteResponse> getIncidents(UUID investigationId) {
        Investigation inv = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(inv.getDossier());

        return incidentObjectiviteRepository
                .findByInvestigationIdOrderByDeclaredAtDesc(investigationId)
                .stream()
                .map(this::toIncidentObjectiviteResponse)
                .toList();
    }

```

- [ ] **Step 3: Add `notifyCgeOfIncident` and `toIncidentObjectiviteResponse` helpers**

Add these in the "MÉTHODES PRIVÉES" section, directly before `getInvestigationOrThrow`:

```java
    private void notifyCgeOfIncident(Investigation inv, Agent declarant) {
        mandatRepository.findByInvestigationId(inv.getId())
                .map(Mandat::getAgentCGE)
                .ifPresent(cge -> {
                    try {
                        Dossier dossier = inv.getDossier();
                        String dossierNumber = dossier.getNumber() != null
                                ? dossier.getNumber() : "(en attente de numéro)";
                        Notification notif = Notification.builder()
                                .dossier(dossier)
                                .type(NotificationType.INTERNAL_ALERT)
                                .channel(NotificationChannel.PORTAL)
                                .recipient(cge.getKeycloakId())
                                .subject("Incident d'objectivité déclaré — dossier "
                                        + dossierNumber)
                                .content("Un incident d'objectivité a été déclaré par "
                                        + declarant.getNomComplet() + " sur le dossier "
                                        + dossierNumber + ".")
                                .scheduledAt(Instant.now())
                                .build();
                        notificationRepository.save(notif);
                        log.info("[declareIncident] Notification CGE créée — "
                                + "investigation: {}", inv.getId());
                    } catch (Exception e) {
                        log.error("[declareIncident] Échec notification CGE — "
                                + "investigation: {} : {}", inv.getId(), e.getMessage());
                    }
                });
    }

    private IncidentObjectiviteResponse toIncidentObjectiviteResponse(
            IncidentObjectivite incident) {
        return IncidentObjectiviteResponse.builder()
                .id(incident.getId())
                .investigationId(incident.getInvestigation().getId())
                .declaredById(incident.getDeclaredBy().getId())
                .declaredByNom(incident.getDeclaredBy().getNomComplet())
                .description(incident.getDescription())
                .declaredAt(incident.getDeclaredAt())
                .build();
    }

```

- [ ] **Step 4: Add the 2 new methods to `InvestigationService`**

In `InvestigationService.java`, add this import next to the existing
`import gov.bf.ascelc.univers_audits.model.dto.response.RevisionPlanResponse;`:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.IncidentObjectiviteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.IncidentObjectiviteResponse;
```

Add after `getPlanRevisions(...)` (before the closing `}`):

```java

    IncidentObjectiviteResponse declareIncident(
            UUID investigationId,
            IncidentObjectiviteRequest request,
            String ipAddress);

    List<IncidentObjectiviteResponse> getIncidents(UUID investigationId);
```

- [ ] **Step 5: Write the tests**

Add to `InvestigationServiceImplTest.java`, after the existing tests and before the
closing `}`:

```java

    @Test
    void declareIncident_succeedsAndChecksDossierAccess() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(incidentObjectiviteRepository.save(any(IncidentObjectivite.class)))
                .thenAnswer(inv -> {
                    IncidentObjectivite i = inv.getArgument(0);
                    i.setId(UUID.randomUUID());
                    return i;
                });
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        IncidentObjectiviteRequest request = IncidentObjectiviteRequest.builder()
                .description("Lien personnel découvert avec une partie visée").build();

        IncidentObjectiviteResponse response =
                service.declareIncident(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getDeclaredById()).isEqualTo(currentAgent.getId());
        assertThat(response.getDescription())
                .isEqualTo("Lien personnel découvert avec une partie visée");
        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void declareIncident_propagatesAccessGuardRejection() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        IncidentObjectiviteRequest request = IncidentObjectiviteRequest.builder()
                .description("Incident").build();

        assertThatThrownBy(() -> service.declareIncident(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Accès refusé");

        verify(incidentObjectiviteRepository, never()).save(any());
    }

    @Test
    void declareIncident_notifiesCgeWhenMandatExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        Agent cge = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-cge").build();
        Mandat mandat = Mandat.builder().agentCGE(cge).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(incidentObjectiviteRepository.save(any(IncidentObjectivite.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(mandat));

        IncidentObjectiviteRequest request = IncidentObjectiviteRequest.builder()
                .description("Incident").build();

        service.declareIncident(investigation.getId(), request, "127.0.0.1");

        verify(notificationRepository).save(argThat(n ->
                n.getRecipient().equals("kc-cge")
                        && n.getType() == NotificationType.INTERNAL_ALERT));
    }

    @Test
    void getIncidents_returnsOrderedList() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent declarant = Agent.builder().id(UUID.randomUUID()).build();
        IncidentObjectivite incident = IncidentObjectivite.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .declaredBy(declarant)
                .description("Incident")
                .declaredAt(Instant.now())
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(incidentObjectiviteRepository
                .findByInvestigationIdOrderByDeclaredAtDesc(investigation.getId()))
                .thenReturn(List.of(incident));

        List<IncidentObjectiviteResponse> incidents =
                service.getIncidents(investigation.getId());

        assertThat(incidents).hasSize(1);
        assertThat(incidents.get(0).getDeclaredById()).isEqualTo(declarant.getId());
        verify(accessGuard).checkReadAccess(dossier);
    }
```

Add these imports at the top of `InvestigationServiceImplTest.java` alongside the
existing ones (check before adding a duplicate):

```java
import gov.bf.ascelc.univers_audits.model.dto.request.IncidentObjectiviteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.IncidentObjectiviteResponse;
import gov.bf.ascelc.univers_audits.model.entity.IncidentObjectivite;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
```

`import static org.mockito.Mockito.*;` (already present) already covers `doThrow`,
`never()`, and `argThat` — no additional static import needed.

Add the mock field, after `@Mock private RevisionPlanRepository revisionPlanRepository;`:

```java
    @Mock private IncidentObjectiviteRepository   incidentObjectiviteRepository;
    @Mock private DossierAccessGuard              accessGuard;
```

- [ ] **Step 6: Run the tests**

Run: `mvn -q test -Dtest=InvestigationServiceImplTest`
Expected: BUILD SUCCESS, all tests (existing + 4 new) pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: add incident d'objectivite declaration/read with dossier access guard and CGE notification"
```

---

### Task 3: Controller endpoints

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java`

**Interfaces:**
- Consumes: `InvestigationService.declareIncident`/`getIncidents` (Task 2).
- Produces: `POST /api/v1/investigations/{id}/incidents-objectivite`,
  `GET /api/v1/investigations/{id}/incidents-objectivite` — terminal, nothing else in
  this plan depends on these.

- [ ] **Step 1: Add the imports**

Add alongside the existing `model.dto.response.RevisionPlanResponse` import:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.IncidentObjectiviteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.IncidentObjectiviteResponse;
```

- [ ] **Step 2: Add the two endpoints**

Add a new section after the `// ── Plan d'investigation ────` block's
`getPlanRevisions` method, before the closing `}` of the class:

```java

    // ── Incident d'objectivité ───────────────────────────────

    @PostMapping("/{id}/incidents-objectivite")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<IncidentObjectiviteResponse> declareIncident(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentObjectiviteRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Déclaration incident d'objectivité — investigation {}", id);
        IncidentObjectiviteResponse result = investigationService.declareIncident(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "DECLARER_INCIDENT_OBJECTIVITE", "INVESTIGATION", id.toString(),
                "Déclaration d'un incident d'objectivité",
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/incidents-objectivite")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<List<IncidentObjectiviteResponse>> getIncidents(
            @PathVariable UUID id) {
        return ResponseEntity.ok(investigationService.getIncidents(id));
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
git commit -m "feat: add incident d'objectivite declaration and read endpoints to InvestigationController"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** §1 (entity) → Task 1. §2 (API, both endpoints + two-layer access
  control) → Task 2 Step 2, Task 3. §3 (CGE notification via Mandat) → Task 2 Step 3.
  Hors périmètre items (email, role-wide broadcast, update/delete, escalation,
  dossier-lifecycle-wide scope) are correctly absent from every task.
- **Type consistency verified:** `IncidentObjectivite`/`IncidentObjectiviteRepository`/
  DTO field names and signatures are identical across Task 1's definitions and Task
  2/3's usage.
- **The two-layer access-control pattern is copied from a real, working precedent**
  (`AuditionServiceImpl`/`AuditionController`), not invented — reduces the risk of a
  subtly wrong new authorization scheme.
- **GET/POST roles are identical** (both use the same functional role list) — unlike
  `Mandat`'s narrower POST-vs-GET role split, this matches `AuditionController`'s
  `READ_ROLES` pattern for both of the two endpoints this plan adds, since declaring an
  incident is itself gated by the same "affected agent" concept as reading one, not a
  more privileged action reserved to a management role.
