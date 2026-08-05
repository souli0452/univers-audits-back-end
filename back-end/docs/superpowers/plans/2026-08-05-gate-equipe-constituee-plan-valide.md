# Porte EQUIPE_CONSTITUEE/PLAN_VALIDE dans start() Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the last remaining gap in `InvestigationServiceImpl.start()`'s
precondition chain — require that the investigation's `PlanInvestigation` exists and
has been validated by the DEI, alongside the team-composition and mandate checks
already in place.

**Architecture:** A single additional precondition block inside the existing `start()`
method, using the `PlanInvestigationRepository` already injected into
`InvestigationServiceImpl` since sub-chantier 3/6 of this Lot. No new entity, DTO,
repository, endpoint, or `DossierStatus` value — this plan is deliberately minimal per
the approved design (see spec).

**Tech Stack:** Spring Boot 3 / Java 17, JUnit 5 + Mockito.

## Global Constraints

- `start()` gains exactly one new precondition, inserted after the existing mandate
  check and before `inv.start()`: a `PlanInvestigation` must exist for the
  investigation AND its `validatedAt` must be non-null. Two distinct
  `BusinessException` messages for the two distinct failure reasons (no plan at all,
  vs. plan exists but not yet validated) — matches this codebase's established
  convention of one message per violated rule, not a generic error.
- No change to `open()`, `Investigation`, `Dossier`, `DossierStatus`, or any
  controller — this is the explicit, approved scope boundary from the spec (see
  `docs/superpowers/specs/2026-08-05-gate-equipe-constituee-plan-valide-design.md`
  for the full reasoning: this codebase's real `DossierStatus` is far coarser than
  the plan de travail's literal §6 text, and the granular sub-stages of investigation
  readiness are tracked via `Investigation`-level fields, not new `DossierStatus`
  values — exactly the same pattern already used for the report-approval sequence
  `deiApprovedAt`/`legalAdvisorApprovedAt`/`cgeApprovedAt`).
- `PlanInvestigationRepository` is **already injected** into `InvestigationServiceImpl`
  as a field (`planInvestigationRepository`, added in sub-chantier 3/6) — do not add a
  new field, new constructor parameter, or new import for it. `PlanInvestigation` the
  entity is already covered by this file's existing `import
  gov.bf.ascelc.univers_audits.model.entity.*;` wildcard.
- Two EXISTING tests will fail once this precondition is added, because neither
  currently stubs `planInvestigationRepository`:
  `start_succeedsWithFullCompositionAndMandat` and
  `start_succeedsWithExtraPersonneRessource` (both in
  `InvestigationServiceImplTest.java`). This is expected and intentional — this task
  updates both with the new stub.
- `PlanInvestigationRepository`, `PlanInvestigation`, and the `@Mock` field for the
  repository are **already present** in `InvestigationServiceImplTest.java` (added in
  sub-chantier 3/6) — no new imports or mock field declarations needed in the test
  file either. This task only adds/modifies test method bodies.

---

### Task 1: Add the plan-validated precondition to `start()`, update tests

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `PlanInvestigationRepository.findByInvestigationId(UUID):
  Optional<PlanInvestigation>` (existing, sub-chantier 3/6),
  `PlanInvestigation.getValidatedAt(): Instant` (existing, sub-chantier 3/6).
- Produces: nothing new — `start()`'s public signature is unchanged, this task only
  adds internal validation logic.

- [ ] **Step 1: Add the precondition to `start()`**

In `InvestigationServiceImpl.java`, find the `start()` method:

```java
    public InvestigationResponse start(UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        validateTeamComposition(investigationId);

        if (mandatRepository.findByInvestigationId(investigationId).isEmpty()) {
            throw new BusinessException(
                    "Aucun mandat n'a été délivré par le CGE pour cette investigation.");
        }

        inv.start();
        Investigation saved = investigationRepository.save(inv);
```

Insert this block immediately after the existing mandate check, and before
`inv.start();`:

```java

        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'investigation n'a été soumis pour cette investigation."));

        if (plan.getValidatedAt() == null) {
            throw new BusinessException(
                    "Le plan d'investigation n'a pas encore été validé par le DEI.");
        }
```

The method should read, after this change:

```java
    public InvestigationResponse start(UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        validateTeamComposition(investigationId);

        if (mandatRepository.findByInvestigationId(investigationId).isEmpty()) {
            throw new BusinessException(
                    "Aucun mandat n'a été délivré par le CGE pour cette investigation.");
        }

        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'investigation n'a été soumis pour cette investigation."));

        if (plan.getValidatedAt() == null) {
            throw new BusinessException(
                    "Le plan d'investigation n'a pas encore été validé par le DEI.");
        }

        inv.start();
        Investigation saved = investigationRepository.save(inv);
```

Everything after `Investigation saved = investigationRepository.save(inv);` (the
audit observation, logging, and return) stays exactly as it is today — do not modify
it.

- [ ] **Step 2: Fix the two existing tests that will now fail**

In `InvestigationServiceImplTest.java`, `start_succeedsWithFullCompositionAndMandat`
currently has this stub block:

```java
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID())
                        .investigation(investigation).agentCGE(cge)
                        .dateDelivrance(java.time.Instant.now()).build()));
        when(investigationRepository.save(any(Investigation.class)))
```

Insert this stub between the two existing lines (after the `mandatRepository` stub,
before the `investigationRepository.save` stub):

```java
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(PlanInvestigation.builder().id(UUID.randomUUID())
                        .validatedAt(java.time.Instant.now()).build()));
```

Do the identical addition — same stub, same insertion point relative to the existing
`mandatRepository`/`investigationRepository.save` stubs — in
`start_succeedsWithExtraPersonneRessource` (this second test already has a plain
`import java.time.Instant;` in scope, so `Instant.now()` without the `java.time.`
prefix is fine there — match whichever style each test already uses for its own
`mandatRepository` stub's `dateDelivrance(...)` call).

- [ ] **Step 3: Write the two new tests**

Add to `InvestigationServiceImplTest.java`, near the other `start_rejects*` tests
(e.g. directly after `start_rejectsWhenCompositionValidButNoMandat`, before
`start_succeedsWithFullCompositionAndMandat`):

```java
    @Test
    void start_rejectsWhenNoPlanExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
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
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("plan d'investigation");
    }

    @Test
    void start_rejectsWhenPlanNotValidated() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
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
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(PlanInvestigation.builder().id(UUID.randomUUID())
                        .validatedAt(null).build()));

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("validé");
    }

```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest=InvestigationServiceImplTest`
Expected: BUILD SUCCESS, all tests (existing + 2 new) pass. The test count should be
2 higher than before this task, with the two previously-passing success tests still
passing (now with their new stub).

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS across the whole suite, no regressions anywhere (the same one
pre-existing environment-only `UniversAuditsApplicationTests.contextLoads` failure,
needing a live datasource, is expected and not yours to fix). Actually run the full
command and report the real "Tests run: N, Failures: X, Errors: Y" summary line — do
not substitute `mvn -q compile` and claim equivalence.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: require validated PlanInvestigation before start() can proceed"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** The spec's single decision (plan-validated precondition in
  `start()`, no new `DossierStatus`, no `open()` change) maps to this plan's single
  task exactly. The two explicit hors-périmètre items (new `DossierStatus` values,
  moving the `EN_INVESTIGATION` transition) are correctly absent — no `DossierStatus`
  file is touched, `open()` is not in this plan's file list at all.
- **Type consistency verified:** `PlanInvestigationRepository.findByInvestigationId`
  and `PlanInvestigation.getValidatedAt()` signatures match their actual sub-chantier
  3/6 definitions (verified by reading the live source before writing this plan, not
  assumed from memory).
- **This is the smallest plan in the whole Lot** — a single task, no new files, no new
  fields, no new endpoint. That is a deliberate, approved outcome of the design
  discussion (see spec), not an oversight — the sub-chantier that was flagged as
  riskiest at the start of the Lot turned out to need only this one precondition once
  the actual `DossierStatus` architecture was understood.
