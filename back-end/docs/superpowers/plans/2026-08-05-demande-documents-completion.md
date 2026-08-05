# Complétion DemandeDocuments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `EscalationLevel.SAISINE_JUDICIAIRE` actually reachable (currently
throws unconditionally), and add a "wrong address" recovery action that resets a
`DemandeDocuments`' deadline at its current escalation level without treating it as a
failed response.

**Architecture:** Two independent, small additions to the existing, already-tested
`DemandeDocuments` module: (1) complete `delaiCodeFor()`'s switch to cover all 4
`EscalationLevel` values and seed the one missing `ParametreDelai` row; (2) a new
entity method + service method + controller endpoint mirroring the existing
`markReceived`/`escalate` shape exactly.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL),
JUnit 5 + Mockito.

## Global Constraints

- `delaiCodeFor(EscalationLevel)` becomes exhaustive over all 4 enum values
  (`INITIAL`, `RELANCE`, `SOMMATION`, `SAISINE_JUDICIAIRE`) with **no `default`
  branch** — a Java switch expression over an enum with every case covered and no
  `default` fails to compile if a 5th enum value is ever added without updating this
  method, which is the intended safety net.
- `create()` is updated to call `delaiCodeFor(EscalationLevel.INITIAL)` instead of the
  hardcoded string `"DEMANDE_DOCUMENTS_INITIAL"` — both evaluate to the identical
  string, so the existing test `create_resolvesInitialDeadlineAndSaves` (which stubs
  `parametreDelaiService.resolveDelaiJours("DEMANDE_DOCUMENTS_INITIAL")` directly)
  continues to pass unmodified — **no existing test needs updating for this change**,
  unlike most sub-chantiers in the prior Lot.
- The new `ParametreDelai` row for `DEMANDE_DOCUMENTS_SAISINE_JUDICIAIRE` has
  `valeur_jours = NULL`, matching the 3 existing rows seeded by migration `008` —
  this is a deliberate, established pattern in this codebase (delay values are left
  for an administrator to configure via `PUT /api/v1/parametres-delai/{code}` in
  production), not a bug to work around.
- `reportAddressError` does **not** change `escalationLevel` — it is a delivery-retry
  action, not an escalation. It resets `recipientLabel`, `sentAt`, and `deadline`
  (recomputed using the delay code for the investigation's **current**
  `escalationLevel`, via the same now-exhaustive `delaiCodeFor()`).
- `reportAddressError` rejects if the `DemandeDocuments` has already been marked
  `received` — no `isOverdue()` precondition (a returned-mail notice can arrive at any
  time relative to the deadline).
- Endpoint uses `PATCH`, matching this controller's existing `mark-received` and
  `escalate` endpoints (both mutate an existing resource's state) — not `POST`.
- Role: same `WRITE_ROLES` constant already defined in `DemandeDocumentsController`
  (`CONTROLEUR_ETAT, CGEA, ADMIN_DDIC`) — no new role introduced.
- No scheduler, no PDF generation, no external/legal integration on reaching
  `SAISINE_JUDICIAIRE` — all explicitly out of scope per the spec.
- Next migration file number is `024` (last is
  `023-add-procedure-urgence-mesure-conservatoire.sql`).

---

### Task 1: Complete escalation delay resolution, add address-error recovery

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/DemandeDocuments.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/DemandeDocumentsService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DemandeDocumentsServiceImpl.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/DemandeDocumentsController.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DemandeDocumentsAddressErrorRequest.java`
- Create: `src/main/resources/db/changelog/migrations/024-add-demande-documents-saisine-judiciaire-delai.sql`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DemandeDocumentsServiceImplTest.java`

**Interfaces:**
- Produces: `DemandeDocuments.resetForAddressError(String newRecipientLabel, int
  deadlineDays): void` (entity method), `DemandeDocumentsService.reportAddressError(
  UUID id, DemandeDocumentsAddressErrorRequest request): DemandeDocumentsResponse`,
  `DemandeDocumentsAddressErrorRequest` DTO (`correctedRecipientLabel`, `@NotBlank`,
  `@Size(max = 300)` — matching `DemandeDocumentsCreateRequest.recipientLabel`'s
  constraints exactly since it's the same underlying field).

- [ ] **Step 1: Write the failing tests for the escalation-delay fix**

Add to `DemandeDocumentsServiceImplTest.java`, after the existing
`escalate_throwsWhenAlreadyAtTerminalLevel` test:

```java
    @Test
    void escalate_reachesSaisineJudiciaireFromSommation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        DemandeDocuments demande = DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .escalationLevel(EscalationLevel.SOMMATION)
                .received(false)
                .deadline(Instant.now().minusSeconds(3600))
                .build();

        when(demandeDocumentsRepository.findById(demande.getId()))
                .thenReturn(Optional.of(demande));
        when(parametreDelaiService.resolveDelaiJours("DEMANDE_DOCUMENTS_SAISINE_JUDICIAIRE"))
                .thenReturn(0);
        when(demandeDocumentsRepository.save(any(DemandeDocuments.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(DemandeDocuments.class)))
                .thenReturn(DemandeDocumentsResponse.builder().build());

        service.escalate(demande.getId());

        assertThat(demande.getEscalationLevel()).isEqualTo(EscalationLevel.SAISINE_JUDICIAIRE);
    }
```

Run: `mvn -q test -Dtest=DemandeDocumentsServiceImplTest#escalate_reachesSaisineJudiciaireFromSommation`
Expected: FAIL — `delaiCodeFor(SAISINE_JUDICIAIRE)` currently throws `BusinessException`
("Aucun délai configuré...") instead of returning a code to resolve.

- [ ] **Step 2: Make `delaiCodeFor()` exhaustive and update `create()`**

In `DemandeDocumentsServiceImpl.java`, replace:

```java
    private String delaiCodeFor(EscalationLevel level) {
        return switch (level) {
            case RELANCE -> "DEMANDE_DOCUMENTS_RELANCE";
            case SOMMATION -> "DEMANDE_DOCUMENTS_SOMMATION";
            default -> throw new BusinessException(
                    "Aucun délai configuré pour le niveau d'escalade : " + level);
        };
    }
```

with:

```java
    private String delaiCodeFor(EscalationLevel level) {
        return switch (level) {
            case INITIAL -> "DEMANDE_DOCUMENTS_INITIAL";
            case RELANCE -> "DEMANDE_DOCUMENTS_RELANCE";
            case SOMMATION -> "DEMANDE_DOCUMENTS_SOMMATION";
            case SAISINE_JUDICIAIRE -> "DEMANDE_DOCUMENTS_SAISINE_JUDICIAIRE";
        };
    }
```

Then in the same file, in `create()`, replace:

```java
        int deadlineDays = parametreDelaiService.resolveDelaiJours("DEMANDE_DOCUMENTS_INITIAL");
```

with:

```java
        int deadlineDays = parametreDelaiService.resolveDelaiJours(delaiCodeFor(EscalationLevel.INITIAL));
```

- [ ] **Step 3: Write the migration for the missing delay parameter**

Create `src/main/resources/db/changelog/migrations/024-add-demande-documents-saisine-judiciaire-delai.sql`:

```sql
--liquibase formatted sql
--changeset dev:024-add-demande-documents-saisine-judiciaire-delai

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_SAISINE_JUDICIAIRE', 'Délai avant saisine judiciaire (immédiat après sommation infructueuse)', NULL, TRUE, TRUE, 0, now());
```

This migration is auto-discovered by `db.changelog-master.yaml`'s `includeAll` on
`db/changelog/migrations/` — no changelog master edit needed.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=DemandeDocumentsServiceImplTest`
Expected: BUILD SUCCESS, all tests pass including the new one. The migration doesn't
affect this unit test (it mocks `ParametreDelaiService` directly), but it's needed for
the real runtime behavior — Step 3 and Steps 1-2 fix the same bug from two different
layers (application-code default-branch, and missing DB config), both are required.

- [ ] **Step 5: Write the failing tests for address-error recovery**

Add to the same test file, after the new `escalate_reachesSaisineJudiciaireFromSommation`
test:

```java
    @Test
    void reportAddressError_succeedsAndKeepsEscalationLevel() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        DemandeDocuments demande = DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .escalationLevel(EscalationLevel.RELANCE)
                .recipientLabel("Ancienne adresse")
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

        DemandeDocumentsAddressErrorRequest request = DemandeDocumentsAddressErrorRequest.builder()
                .correctedRecipientLabel("Nouvelle adresse").build();

        service.reportAddressError(demande.getId(), request);

        assertThat(demande.getRecipientLabel()).isEqualTo("Nouvelle adresse");
        assertThat(demande.getEscalationLevel()).isEqualTo(EscalationLevel.RELANCE);
    }

    @Test
    void reportAddressError_rejectsWhenAlreadyReceived() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        DemandeDocuments demande = DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .escalationLevel(EscalationLevel.INITIAL)
                .received(true)
                .build();

        when(demandeDocumentsRepository.findById(demande.getId()))
                .thenReturn(Optional.of(demande));

        DemandeDocumentsAddressErrorRequest request = DemandeDocumentsAddressErrorRequest.builder()
                .correctedRecipientLabel("Nouvelle adresse").build();

        assertThatThrownBy(() -> service.reportAddressError(demande.getId(), request))
                .isInstanceOf(BusinessException.class);
    }
```

Add this import at the top of the test file (check it isn't already present before
adding a duplicate):

```java
import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsAddressErrorRequest;
```

Run: `mvn -q test -Dtest=DemandeDocumentsServiceImplTest`
Expected: FAIL to compile — `DemandeDocumentsAddressErrorRequest` and
`service.reportAddressError(...)` don't exist yet.

- [ ] **Step 6: Create the `DemandeDocumentsAddressErrorRequest` DTO**

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
public class DemandeDocumentsAddressErrorRequest {

    @NotBlank(message = "Le destinataire corrigé est obligatoire")
    @Size(max = 300)
    private String correctedRecipientLabel;
}
```

- [ ] **Step 7: Add the entity method**

In `DemandeDocuments.java`, add this method directly after the existing `escalate(...)`
method:

```java

    public void resetForAddressError(String newRecipientLabel, int deadlineDays) {
        this.recipientLabel = newRecipientLabel;
        this.sentAt = Instant.now();
        this.deadline = sentAt.plusSeconds(deadlineDays * 24L * 3600);
    }
```

- [ ] **Step 8: Add the service method**

In `DemandeDocumentsService.java`, add after `escalate(UUID id)`:

```java

    DemandeDocumentsResponse reportAddressError(UUID id, DemandeDocumentsAddressErrorRequest request);
```

Add the import:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsAddressErrorRequest;
```

In `DemandeDocumentsServiceImpl.java`, add after `escalate(...)` (before
`findByInvestigationId`):

```java

    @Override
    @Transactional
    public DemandeDocumentsResponse reportAddressError(
            UUID id, DemandeDocumentsAddressErrorRequest request) {
        DemandeDocuments demande = getOrThrow(id);
        accessGuard.checkReadAccess(demande.getInvestigation().getDossier());

        if (Boolean.TRUE.equals(demande.getReceived())) {
            throw new BusinessException(
                    "Cette demande a déjà été satisfaite, elle ne peut pas être "
                            + "corrigée pour adresse erronée");
        }

        int deadlineDays = parametreDelaiService.resolveDelaiJours(
                delaiCodeFor(demande.getEscalationLevel()));
        demande.resetForAddressError(request.getCorrectedRecipientLabel(), deadlineDays);

        DemandeDocuments saved = demandeDocumentsRepository.save(demande);
        log.info("Demande de documents corrigée pour adresse erronée — id: {}", id);
        return mapper.toResponse(saved);
    }
```

`DemandeDocumentsAddressErrorRequest` is already covered by whatever import style
`DemandeDocumentsServiceImpl.java` already uses for its sibling DTO
(`DemandeDocumentsCreateRequest`) — add an explicit import matching that same style if
the file imports request DTOs individually (check the file's current imports before
adding).

- [ ] **Step 9: Run the tests to verify they pass**

Run: `mvn -q test -Dtest=DemandeDocumentsServiceImplTest`
Expected: BUILD SUCCESS, all tests (existing + 3 new) pass.

- [ ] **Step 10: Add the controller endpoint**

In `DemandeDocumentsController.java`, add the import:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsAddressErrorRequest;
```

Add after the `escalate` endpoint, before the closing `}`:

```java

    @PatchMapping("/{id}/adresse-erronee")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<DemandeDocumentsResponse> reportAddressError(
            @PathVariable UUID investigationId,
            @PathVariable UUID id,
            @Valid @RequestBody DemandeDocumentsAddressErrorRequest request) {
        return ResponseEntity.ok(demandeDocumentsService.reportAddressError(id, request));
    }
```

- [ ] **Step 11: Compile and run the full test suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, no regressions anywhere in the suite (the same one
pre-existing environment-only `UniversAuditsApplicationTests.contextLoads` failure,
needing a live datasource, is expected and not yours to fix). Actually run the full
command and report the real "Tests run: N, Failures: X, Errors: Y" summary line.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/DemandeDocuments.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/DemandeDocumentsService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/DemandeDocumentsServiceImpl.java \
        src/main/java/gov/bf/ascelc/univers_audits/controller/DemandeDocumentsController.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DemandeDocumentsAddressErrorRequest.java \
        src/main/resources/db/changelog/migrations/024-add-demande-documents-saisine-judiciaire-delai.sql \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/DemandeDocumentsServiceImplTest.java
git commit -m "feat: make SAISINE_JUDICIAIRE reachable and add address-error recovery for demandes de documents"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** §1 (SAISINE_JUDICIAIRE reachable) → Steps 1-4. §2 (adresse
  erronée) → Steps 5-10. Hors périmètre items (scheduler, PDF, external legal
  integration, structured address field) are correctly absent from every step.
- **Type consistency verified:** `DemandeDocumentsAddressErrorRequest`'s field name
  and constraints match across its Step 6 definition and Steps 8/10's usage.
  `resetForAddressError`'s signature matches between Step 7's definition and Step 8's
  call site.
- **No existing test breaks** — this plan's Global Constraints call out explicitly why
  (the `create()` refactor is string-identical before and after), a deliberate
  contrast with prior Lot 3 sub-chantiers where breaking 1-2 existing tests was
  expected and documented.
