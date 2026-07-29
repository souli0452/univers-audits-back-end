# Dérivation NatureSaisine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `Dossier.type` (nature de la saisine) a value derived server-side from the declarant's type and quality, instead of a value chosen freely by the API client — closing the gap with §4.1 of the ASCE-LC manual.

**Architecture:** A new pure `NatureSaisineResolver` component encodes the derivation matrix (TypeDeclarant × QualiteDeclarant × anonymous → TypeSaisine) and is called from `DossierServiceImpl.submit()` right after the declarant is resolved, before the dossier is persisted. `TypeSaisine` is renamed to match the manual's vocabulary and loses its `ANONYMOUS` member (anonymity is only ever expressed by `Declarant.anonymous`). The "quality" of the declarant moves from `Declarant` (a person, reused across dossiers) to `Dossier` (a single saisine), because the same person can be a witness on one case and a victim on another.

**Tech Stack:** Spring Boot 3 / Java 17, JPA/Hibernate, MapStruct 1.5.5, Liquibase (formatted SQL changesets), JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Derivation matrix (locked in the approved design spec, `docs/superpowers/specs/2026-07-28-nature-saisine-derivation-design.md`):
  - `PUBLIC_AUTHORITY` → `SIGNALEMENT` (quality ignored)
  - `ASCE_SELF_REFERRAL` → `AUTO_SAISINE` (quality ignored)
  - `CITIZEN` / `COMPANY` / `ASSOCIATION` / `ANONYMOUS` → quality **required**; `VICTIME`/`REPRESENTANT_VICTIME` → `PLAINTE` (anonymat interdit); `TEMOIN` → `DENONCIATION` (anonymat autorisé)
  - `TypeDeclarant.ANONYMOUS` combined with `VICTIME`/`REPRESENTANT_VICTIME` is rejected outright
- No Bean Validation (`@AssertTrue` etc.) for the quality/anonymat rule — it spans two different request objects (`DossierCreateRequest.quality` and `DossierCreateRequest.declarantData.anonymous`) and depends on data (the resolved `Declarant`, possibly pre-existing) not fully known at DTO-validation time. All derivation errors are `BusinessException`, thrown from the service layer, same pattern as the existing protection-lanceur-d'alerte check in `DossierServiceImpl.submit()`.
- `DossierCreateRequest.type` is removed entirely (breaking API change, already approved) — the client never sends `type` again.
- Existing `Declarant.quality` (free-text column) is removed — quality now lives only on `Dossier.quality` (typed enum).

---

## File Structure

| File | Responsibility |
|---|---|
| `enums/QualiteDeclarant.java` (new) | The 3 allowed qualities of a declarant: VICTIME, REPRESENTANT_VICTIME, TEMOIN |
| `enums/TypeSaisine.java` (modified) | Renamed to the manual's nomenclature; `ANONYMOUS` removed |
| `service/PdfExportService.java` (modified) | Label mapping for the renamed enum values |
| `shared/utils/NatureSaisineResolver.java` (new) | The derivation matrix, as a stateless Spring component |
| `model/entity/Dossier.java` (modified) | Adds the `quality` column |
| `model/entity/Declarant.java` (modified) | Removes the `quality` column |
| `model/dto/request/DossierCreateRequest.java` (modified) | Removes `type`, adds `quality` |
| `model/dto/request/DeclarantCreateRequest.java` (modified) | Removes `quality` |
| `model/dto/response/DossierResponse.java` (modified) | Adds `quality` |
| `model/dto/response/DeclarantResponse.java` (modified) | Removes `quality` |
| `mapper/DossierMapper.java` (modified) | `type` is no longer mapped from the request (server-computed) |
| `db/changelog/migrations/009-nature-saisine-derivation.sql` (new) | Schema + data migration |
| `service/impl/DossierServiceImpl.java` (modified) | Wires the resolver into `submit()` |

---

### Task 1: `QualiteDeclarant` enum, `TypeSaisine` rename, PDF label fix

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/QualiteDeclarant.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/enums/TypeSaisine.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java:483-491`

**Interfaces:**
- Produces: `enum QualiteDeclarant { VICTIME, REPRESENTANT_VICTIME, TEMOIN }` and `enum TypeSaisine { DENONCIATION, PLAINTE, SIGNALEMENT, AUTO_SAISINE }`, both consumed by every later task.

This task has no interesting behavior to unit-test (plain enum declarations); the deliverable is verified by a successful compile. `PdfExportService.getTypeLabel()` is a `String`-switch (not an enum-switch), so the old case labels wouldn't fail to compile if left stale — they'd just silently stop matching at runtime, which is exactly the bug this step prevents.

- [ ] **Step 1: Create the `QualiteDeclarant` enum**

```java
package gov.bf.ascelc.univers_audits.enums;

public enum QualiteDeclarant {
    VICTIME,
    REPRESENTANT_VICTIME,
    TEMOIN
}
```

- [ ] **Step 2: Rename `TypeSaisine` values**

Replace the full content of `enums/TypeSaisine.java`:

```java
package gov.bf.ascelc.univers_audits.enums;

public enum TypeSaisine {
    DENONCIATION,
    PLAINTE,
    SIGNALEMENT,
    AUTO_SAISINE
}
```

- [ ] **Step 3: Fix the label mapping in `PdfExportService`**

In `PdfExportService.java`, replace the `getTypeLabel` method (around line 483-491):

```java
    private String getTypeLabel(String type) {
        return switch (type) {
            case "DENONCIATION"  -> "Dénonciation";
            case "PLAINTE"       -> "Plainte";
            case "SIGNALEMENT"   -> "Signalement";
            case "AUTO_SAISINE"  -> "Auto-saisine";
            default              -> type;
        };
    }
```

- [ ] **Step 4: Verify the project still compiles**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`. (`Dossier.java`, `DossierCreateRequest.java` and `DossierResponse.java` only reference the `TypeSaisine` type, not specific constant names, so this succeeds even though nothing yet produces the new values.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/QualiteDeclarant.java src/main/java/gov/bf/ascelc/univers_audits/enums/TypeSaisine.java src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java
git commit -m "refactor: rename TypeSaisine to manual nomenclature, add QualiteDeclarant"
```

---

### Task 2: `NatureSaisineResolver`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/NatureSaisineResolver.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/shared/utils/NatureSaisineResolverTest.java`

**Interfaces:**
- Consumes: `TypeDeclarant` (existing enum, `enums/TypeDeclarant.java`), `QualiteDeclarant` and `TypeSaisine` from Task 1.
- Produces: `@Component NatureSaisineResolver` with `public TypeSaisine resolve(TypeDeclarant typeDeclarant, QualiteDeclarant quality, boolean anonymous)`, throwing `gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException` on any invalid combination. Consumed by Task 4.

- [ ] **Step 1: Write the failing test**

```java
package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatureSaisineResolverTest {

    private final NatureSaisineResolver resolver = new NatureSaisineResolver();

    @Test
    void citizenVictimeNotAnonymous_returnsPlainte() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.CITIZEN, QualiteDeclarant.VICTIME, false);
        assertThat(result).isEqualTo(TypeSaisine.PLAINTE);
    }

    @Test
    void citizenRepresentantVictimeNotAnonymous_returnsPlainte() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.CITIZEN, QualiteDeclarant.REPRESENTANT_VICTIME, false);
        assertThat(result).isEqualTo(TypeSaisine.PLAINTE);
    }

    @Test
    void citizenTemoinNotAnonymous_returnsDenonciation() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, false);
        assertThat(result).isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void citizenTemoinAnonymous_returnsDenonciation() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, true);
        assertThat(result).isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void companyVictime_returnsPlainte() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.COMPANY, QualiteDeclarant.VICTIME, false);
        assertThat(result).isEqualTo(TypeSaisine.PLAINTE);
    }

    @Test
    void associationTemoin_returnsDenonciation() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.ASSOCIATION, QualiteDeclarant.TEMOIN, false);
        assertThat(result).isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void anonymousDeclarantTemoin_returnsDenonciation() {
        TypeSaisine result = resolver.resolve(
                TypeDeclarant.ANONYMOUS, QualiteDeclarant.TEMOIN, true);
        assertThat(result).isEqualTo(TypeSaisine.DENONCIATION);
    }

    @Test
    void anonymousDeclarantVictime_throws() {
        assertThatThrownBy(() -> resolver.resolve(
                TypeDeclarant.ANONYMOUS, QualiteDeclarant.VICTIME, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void anonymousDeclarantRepresentantVictime_throws() {
        assertThatThrownBy(() -> resolver.resolve(
                TypeDeclarant.ANONYMOUS, QualiteDeclarant.REPRESENTANT_VICTIME, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void victimeButAnonymousFlagTrue_throws() {
        assertThatThrownBy(() -> resolver.resolve(
                TypeDeclarant.CITIZEN, QualiteDeclarant.VICTIME, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void representantVictimeButAnonymousFlagTrue_throws() {
        assertThatThrownBy(() -> resolver.resolve(
                TypeDeclarant.COMPANY, QualiteDeclarant.REPRESENTANT_VICTIME, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void citizenWithoutQuality_throws() {
        assertThatThrownBy(() -> resolver.resolve(
                TypeDeclarant.CITIZEN, null, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void publicAuthority_qualityIgnored_returnsSignalement() {
        assertThat(resolver.resolve(TypeDeclarant.PUBLIC_AUTHORITY, null, false))
                .isEqualTo(TypeSaisine.SIGNALEMENT);
        assertThat(resolver.resolve(
                TypeDeclarant.PUBLIC_AUTHORITY, QualiteDeclarant.VICTIME, false))
                .isEqualTo(TypeSaisine.SIGNALEMENT);
    }

    @Test
    void asceSelfReferral_qualityIgnored_returnsAutoSaisine() {
        assertThat(resolver.resolve(TypeDeclarant.ASCE_SELF_REFERRAL, null, false))
                .isEqualTo(TypeSaisine.AUTO_SAISINE);
    }

    @Test
    void typeDeclarantNull_throws() {
        assertThatThrownBy(() -> resolver.resolve(null, QualiteDeclarant.TEMOIN, false))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=NatureSaisineResolverTest`
Expected: compile error — `NatureSaisineResolver` does not exist yet.

- [ ] **Step 3: Implement `NatureSaisineResolver`**

```java
package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Dérive TypeSaisine (nature de la saisine) depuis le type et la qualité du
 * déclarant, conformément au §4.1 du manuel ASCE-LC. Le client ne choisit
 * jamais directement la nature — elle est toujours calculée ici.
 */
@Component
public class NatureSaisineResolver {

    public TypeSaisine resolve(TypeDeclarant typeDeclarant,
                                QualiteDeclarant quality,
                                boolean anonymous) {
        if (typeDeclarant == null) {
            throw new BusinessException(
                    "Le type de déclarant est requis pour déterminer "
                            + "la nature de la saisine.");
        }

        return switch (typeDeclarant) {
            case PUBLIC_AUTHORITY -> TypeSaisine.SIGNALEMENT;
            case ASCE_SELF_REFERRAL -> TypeSaisine.AUTO_SAISINE;
            case CITIZEN, COMPANY, ASSOCIATION, ANONYMOUS ->
                    resolveFromQuality(typeDeclarant, quality, anonymous);
        };
    }

    private TypeSaisine resolveFromQuality(TypeDeclarant typeDeclarant,
                                            QualiteDeclarant quality,
                                            boolean anonymous) {
        if (quality == null) {
            throw new BusinessException(
                    "La qualité du déposant (victime, représentant de la "
                            + "victime ou témoin) est obligatoire pour ce "
                            + "type de déclarant.");
        }

        if (typeDeclarant == TypeDeclarant.ANONYMOUS
                && quality != QualiteDeclarant.TEMOIN) {
            throw new BusinessException(
                    "Un déclarant anonyme ne peut être enregistré qu'en "
                            + "tant que témoin.");
        }

        return switch (quality) {
            case VICTIME, REPRESENTANT_VICTIME -> {
                if (anonymous) {
                    throw new BusinessException(
                            "L'anonymat est incompatible avec la qualité de "
                                    + labelFor(quality) + ".");
                }
                yield TypeSaisine.PLAINTE;
            }
            case TEMOIN -> TypeSaisine.DENONCIATION;
        };
    }

    private String labelFor(QualiteDeclarant quality) {
        return quality == QualiteDeclarant.VICTIME
                ? "victime"
                : "représentant de la victime";
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=NatureSaisineResolverTest`
Expected: all 16 tests `PASS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/utils/NatureSaisineResolver.java src/test/java/gov/bf/ascelc/univers_audits/shared/utils/NatureSaisineResolverTest.java
git commit -m "feat: add NatureSaisineResolver implementing the §4.1 derivation matrix"
```

---

### Task 3: Schema and DTO plumbing

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java:1-17` (imports + field)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Declarant.java:42-43`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DossierCreateRequest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DeclarantCreateRequest.java:16-18`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DeclarantResponse.java:16-17`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierMapper.java:65-77`
- Create: `src/main/resources/db/changelog/migrations/009-nature-saisine-derivation.sql`

**Interfaces:**
- Consumes: `QualiteDeclarant` from Task 1.
- Produces: `Dossier.getQuality()/setQuality(QualiteDeclarant)`, `DossierCreateRequest.getQuality()/setQuality(QualiteDeclarant)`, `DossierResponse.getQuality()/setQuality(QualiteDeclarant)` — all consumed by Task 4 and by MapStruct's auto-mapping (matching field name + type, no explicit `@Mapping` needed since `Dossier.quality` and `DossierCreateRequest.quality` share the same name and type).

This task is pure wiring with no new business logic, so there is no new unit test to write; the deliverable is verified by a full build + the full existing test suite (which already covers everything that touches these fields, per Step 5 below).

- [ ] **Step 1: Add `quality` to `Dossier`**

In `Dossier.java`, add the import and field. Add to the imports block (after `import gov.bf.ascelc.univers_audits.enums.DossierStatus;`):

```java
import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
```

Add the field, right after the `type` field declaration:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TypeSaisine type;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality", length = 30)
    private QualiteDeclarant quality;
```

- [ ] **Step 2: Remove `quality` from `Declarant`**

In `Declarant.java`, delete these lines (42-43):

```java
    @Column(name = "quality", length = 20)
    private String quality;
```

- [ ] **Step 3: Update `DossierCreateRequest`**

In `DossierCreateRequest.java`, remove the `type` field and its `@NotNull` annotation/import if now unused:

```java
    @NotNull(message = "Le type de saisine est obligatoire")
    private TypeSaisine type;
```

Replace with:

```java
    private QualiteDeclarant quality;
```

Add the import (replacing the now-unused `TypeSaisine` import if nothing else in the file uses it — check before removing; `TypeSaisine` is no longer referenced elsewhere in this file, so replace the import line):

```java
import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
```

- [ ] **Step 4: Update `DeclarantCreateRequest`**

In `DeclarantCreateRequest.java`, delete line 18:

```java
    private String quality;
```

- [ ] **Step 5: Update `DossierResponse` and `DeclarantResponse`**

In `DossierResponse.java`, add the import `gov.bf.ascelc.univers_audits.enums.QualiteDeclarant` and the field, right after `type`:

```java
    private TypeSaisine                type;
    private QualiteDeclarant           quality;
```

In `DeclarantResponse.java`, delete line 17:

```java
    private String quality;
```

- [ ] **Step 6: Update `DossierMapper`**

In `DossierMapper.java`, in the `toEntity(DossierCreateRequest request)` mapping (the method currently spanning lines 39-65), add an explicit ignore for `type` since it's no longer sourced from the request — insert alongside the other `@Mapping(target = "...", ignore = true)` lines, e.g. right after `@Mapping(target = "status", ignore = true)`:

```java
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "type", ignore = true)
```

(`quality` needs no explicit mapping: `DossierCreateRequest.quality` and `Dossier.quality` now share the same name and type, so MapStruct auto-maps it.)

- [ ] **Step 7: Write the migration**

Create `src/main/resources/db/changelog/migrations/009-nature-saisine-derivation.sql`:

```sql
--liquibase formatted sql
--changeset dev:009-nature-saisine-derivation

ALTER TABLE dossier
    ADD COLUMN IF NOT EXISTS quality VARCHAR(30);

COMMENT ON COLUMN dossier.quality IS 'Qualite du deposant (VICTIME / REPRESENTANT_VICTIME / TEMOIN) - nulle pour SIGNALEMENT et AUTO_SAISINE';

-- Remap des valeurs existantes de dossier.type vers la nouvelle nomenclature
-- (TypeSaisine{COMPLAINT,DENUNCIATION,AUTO_REFERRAL,ANONYMOUS} -> {DENONCIATION,PLAINTE,SIGNALEMENT,AUTO_SAISINE}).
-- dossier.quality reste NULL sur les lignes existantes : l'ancienne colonne
-- declarant.quality etait une chaine libre non validee, sa fiabilite n'est
-- pas garantie pour reconstruire automatiquement la qualite du deposant.
UPDATE dossier SET type = 'PLAINTE'      WHERE type = 'COMPLAINT';
UPDATE dossier SET type = 'DENONCIATION' WHERE type = 'DENUNCIATION';
UPDATE dossier SET type = 'AUTO_SAISINE' WHERE type = 'AUTO_REFERRAL';

-- Les dossiers historiques marques ANONYMOUS deviennent des denonciations ; on
-- force la coherence du declarant lie plutot que de supposer qu'elle etait deja correcte.
UPDATE declarant SET anonymous = TRUE
    WHERE id IN (
        SELECT declarant_id FROM dossier
        WHERE type = 'ANONYMOUS' AND declarant_id IS NOT NULL
    );
UPDATE dossier SET type = 'DENONCIATION' WHERE type = 'ANONYMOUS';

ALTER TABLE declarant
    DROP COLUMN IF EXISTS quality;
```

(No manual registration needed — `db.changelog-master.yaml` already picks up every file under `migrations/` via `includeAll`.)

- [ ] **Step 8: Verify the full build and test suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, all existing tests still pass (confirms nothing else in the codebase referenced the removed `Declarant.quality` / `DeclarantCreateRequest.quality` / `DeclarantResponse.quality` fields, or the removed `DossierCreateRequest.type` field).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierMapper.java src/main/resources/db/changelog/migrations/009-nature-saisine-derivation.sql
git commit -m "feat: move declarant quality onto Dossier, migrate schema"
```

---

### Task 4: Wire the resolver into `DossierServiceImpl.submit()`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java:1-56` (imports + fields), `:141-161` (`submit()`)
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java` (new)

**Interfaces:**
- Consumes: `NatureSaisineResolver.resolve(TypeDeclarant, QualiteDeclarant, boolean)` from Task 2; `Dossier.setType/setQuality`, `DossierCreateRequest.getQuality()` from Task 3.

- [ ] **Step 1: Write the failing test**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.mapper.DeclarantMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DeclarantCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.DeclarantRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.service.NotificationDispatcherService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import gov.bf.ascelc.univers_audits.shared.utils.NatureSaisineResolver;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DossierServiceImplTest {

    @Mock private DossierRepository dossierRepository;
    @Mock private DeclarantRepository declarantRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ObservationRepository observationRepository;
    @Mock private DossierMapper dossierMapper;
    @Mock private DossierDetailsMapper dossierDetailsMapper;
    @Mock private DeclarantMapper declarantMapper;
    @Mock private AccessCodeGenerator accessCodeGenerator;
    @Mock private SecurityUtils securityUtils;
    @Mock private NotificationDispatcherService notificationDispatcher;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private DossierAuditRecorder auditRecorder;
    @Mock private ParametreDelaiService parametreDelaiService;
    @Mock private NatureSaisineResolver natureSaisineResolver;

    @InjectMocks
    private DossierServiceImpl service;

    private DossierCreateRequest buildRequest(QualiteDeclarant quality) {
        DeclarantCreateRequest declarantData = DeclarantCreateRequest.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .anonymous(false)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .build();
        return DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Marché public suspect")
                .quality(quality)
                .declarantData(declarantData)
                .build();
    }

    @Test
    void submit_appliesResolvedNatureSaisineToNewDossier() {
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN);
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .anonymous(false)
                .build();

        when(declarantMapper.toEntity(request.getDeclarantData())).thenReturn(declarant);
        when(declarantRepository.save(declarant)).thenReturn(declarant);
        when(natureSaisineResolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, false))
                .thenReturn(TypeSaisine.DENONCIATION);
        when(dossierMapper.toEntity(request)).thenReturn(Dossier.builder().build());
        when(accessCodeGenerator.generate()).thenReturn("ABCD1234");
        when(dossierRepository.existsByAccessCode("ABCD1234")).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submit(request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d ->
                d.getType() == TypeSaisine.DENONCIATION
                        && d.getDeclarant() == declarant));
    }

    @Test
    void submit_throwsWhenDeclarantMissing() {
        DossierCreateRequest request = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Objet")
                .build();

        assertThatThrownBy(() -> service.submit(request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void submit_propagatesBusinessExceptionFromResolverWithoutSaving() {
        DossierCreateRequest request = buildRequest(QualiteDeclarant.VICTIME);
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.ANONYMOUS)
                .anonymous(true)
                .build();

        when(declarantMapper.toEntity(request.getDeclarantData())).thenReturn(declarant);
        when(declarantRepository.save(declarant)).thenReturn(declarant);
        when(natureSaisineResolver.resolve(TypeDeclarant.ANONYMOUS, QualiteDeclarant.VICTIME, true))
                .thenThrow(new BusinessException(
                        "Un déclarant anonyme ne peut être enregistré qu'en tant que témoin."));

        assertThatThrownBy(() -> service.submit(request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=DossierServiceImplTest`
Expected: compile error on `natureSaisineResolver` (no such field on `DossierServiceImpl` yet) and/or `DossierCreateRequest.builder().quality(...)` if Task 3 wasn't already merged — if Task 3 is done first as planned, only the missing `natureSaisineResolver` field/constructor argument causes the failure.

- [ ] **Step 3: Wire the resolver into `DossierServiceImpl`**

Add the import (alongside the other `shared.utils` imports):

```java
import gov.bf.ascelc.univers_audits.shared.utils.NatureSaisineResolver;
```

Add the field (after `private final ParametreDelaiService parametreDelaiService;`):

```java
    private final NatureSaisineResolver         natureSaisineResolver;
```

In `submit()`, replace:

```java
        Declarant declarant = resolveDeclarant(request);
        Dossier   dossier   = dossierMapper.toEntity(request);
        dossier.setDeclarant(declarant);
        dossier.setStatus(DossierStatus.SOUMIS);
```

with:

```java
        Declarant declarant = resolveDeclarant(request);
        if (declarant == null) {
            throw new BusinessException(
                    "Le déclarant est requis pour déterminer la nature de la saisine.");
        }

        TypeSaisine natureSaisine = natureSaisineResolver.resolve(
                declarant.getTypeDeclarant(), request.getQuality(),
                declarant.isAnonymous());

        Dossier dossier = dossierMapper.toEntity(request);
        dossier.setDeclarant(declarant);
        dossier.setType(natureSaisine);
        dossier.setStatus(DossierStatus.SOUMIS);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=DossierServiceImplTest`
Expected: all 3 tests `PASS`.

- [ ] **Step 5: Run the full suite one more time**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java
git commit -m "feat: derive Dossier.type from declarant quality at submission"
```

---

## Manual follow-up (not automatable in this plan)

- No Testcontainers/DB-integration test harness exists in this repo, so the Liquibase migration (Task 3, Step 7) is not exercised by an automated test. Before deploying: run the app against a copy of the staging/dev database and confirm `SELECT type, quality FROM dossier LIMIT 20;` shows the remapped values and no `COMPLAINT`/`DENUNCIATION`/`AUTO_REFERRAL`/`ANONYMOUS` rows remain.
- The frontend (separate repository, not covered here) currently sends `type` and reads `Declarant.quality` — it needs a matching change once this ships. Flag this as a coordination item, not part of this plan.
