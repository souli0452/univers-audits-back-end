# Champs manquants du formulaire de dépôt — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the 8 fields present on the real ASCE-LC paper intake form but missing from `Declarant`/`Dossier` today, so the digital submission can capture everything the paper form does.

**Architecture:** Purely additive schema/DTO changes across two independent areas — `Declarant` (2 new fields) and `Dossier` (7 new fields across 4 form items) — each with its own migration. Field names reuse the paper form's own vocabulary (matching the existing precedent set by `motifs`), not invented technical terms. No existing field is renamed, removed, or reinterpreted, so there is no breaking API change this time.

**Tech Stack:** Spring Boot 3 / Java 17, JPA/Hibernate, MapStruct 1.5.5 (auto-maps by matching field name + type — no new `@Mapping` entries needed for the new fields themselves), Liquibase, JUnit 5 + AssertJ.

## Global Constraints

- Field naming follows the paper form's own French vocabulary (`cellulaire`, `localite`, `lieuDepot`, `attentes`, etc.) — not translated or abbreviated.
- `object` is NOT renamed, removed, or repurposed. `attentes` is a new, distinct field (item 6 of the form) — do not merge the two.
- `lieuDepot` is distinct from the existing `incidentLocation` (lieu des faits). `organismeFaitsDenomination`/`organismeFaitsAdresse` are distinct from `TargetedParty` (partie visée) — do not reuse either.
- All new fields are nullable/optional at every layer (entity, create request, response) — the paper form does not mark all of them mandatory, and making any of them required would break existing portal clients.
- `cellulaire` and `localite` are personal-identifying information, same category as `phoneNumber`/`commune`/`province` — they MUST be added to `DeclarantMapper.fillAndMask`'s anonymity-masking null-out list, not just added as plain fields. Missing this would leak identifying data for an anonymous declarant.
- No existing migration file is modified — two new migration files (`013`, `014`), matching the sequential numbering already established by this Lot's prior chantiers (last was `012`).

---

## File Structure

| File | Responsibility |
|---|---|
| `model/entity/Declarant.java` (modified) | Adds `cellulaire`, `localite` |
| `model/dto/request/DeclarantCreateRequest.java` (modified) | Adds `cellulaire`, `localite` |
| `model/dto/response/DeclarantResponse.java` (modified) | Adds `cellulaire`, `localite` |
| `mapper/DeclarantMapper.java` (modified) | Masks `cellulaire`/`localite` for anonymous declarants |
| `enums/SubmissionMode.java` (modified) | Adds `FAX` |
| `service/PdfExportService.java` (modified) | Adds a `FAX` case to the existing mode-label switch |
| `db/changelog/migrations/013-add-declarant-fields.sql` (new) | Schema for the 2 declarant fields |
| `model/entity/Dossier.java` (modified) | Adds the 7 new dossier-side fields |
| `model/dto/request/DossierCreateRequest.java` (modified) | Adds the 7 new dossier-side fields |
| `model/dto/response/DossierResponse.java` (modified) | Adds the 7 new dossier-side fields |
| `db/changelog/migrations/014-add-dossier-fields.sql` (new) | Schema for the 7 dossier fields |

---

### Task 1: Declarant-side fields (`cellulaire`, `localite`) and `SubmissionMode.FAX`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Declarant.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DeclarantCreateRequest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DeclarantResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DeclarantMapper.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/enums/SubmissionMode.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java` (the existing `getModeLabel` switch)
- Create: `src/main/resources/db/changelog/migrations/013-add-declarant-fields.sql`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/mapper/DeclarantMapperTest.java` (new — first test for this mapper)

**Interfaces:**
- Produces: `Declarant.getCellulaire()/setCellulaire(String)`, `Declarant.getLocalite()/setLocalite(String)`, matching fields on `DeclarantCreateRequest`/`DeclarantResponse` (auto-mapped by MapStruct via matching name+type).

- [ ] **Step 1: Write the failing test**

```java
package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeclarantMapperTest {

    private final DeclarantMapper mapper = new DeclarantMapperImpl();

    @Test
    void toResponse_masksCellulaireAndLocaliteForAnonymousDeclarant() {
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .anonymous(true)
                .cellulaire("70000000")
                .localite("Tampouy")
                .build();

        DeclarantResponse response = mapper.toResponse(declarant);

        assertThat(response.getCellulaire()).isNull();
        assertThat(response.getLocalite()).isNull();
    }

    @Test
    void toResponse_keepsCellulaireAndLocaliteForNamedDeclarant() {
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .anonymous(false)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .cellulaire("70000000")
                .localite("Tampouy")
                .build();

        DeclarantResponse response = mapper.toResponse(declarant);

        assertThat(response.getCellulaire()).isEqualTo("70000000");
        assertThat(response.getLocalite()).isEqualTo("Tampouy");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=DeclarantMapperTest`
Expected: compile error — `Declarant`/`DeclarantResponse` have no `cellulaire`/`localite` fields yet, so `DeclarantMapperImpl` (MapStruct-generated) won't have matching getters/setters to call.

- [ ] **Step 3: Add the fields to `Declarant`**

In `Declarant.java`, add these two fields right after the existing `province` field:

```java
    @Column(name = "cellulaire", length = 20)
    private String cellulaire;

    @Column(name = "localite", length = 100)
    private String localite;
```

- [ ] **Step 4: Add the fields to `DeclarantCreateRequest`**

In `DeclarantCreateRequest.java`, add right after the existing `province` field:

```java
    @Size(max = 20)
    private String cellulaire;

    @Size(max = 100)
    private String localite;
```

- [ ] **Step 5: Add the fields to `DeclarantResponse`**

In `DeclarantResponse.java`, add right after the existing `province` field:

```java
    private String cellulaire;
    private String localite;
```

- [ ] **Step 6: Mask the new fields for anonymous declarants**

In `DeclarantMapper.java`, inside `fillAndMask`, add to the existing `if (declarant.isAnonymous())` block (alongside the other `response.setX(null)` calls):

```java
            response.setCellulaire(null);
            response.setLocalite(null);
```

- [ ] **Step 7: Add `FAX` to `SubmissionMode`**

Replace the full content of `SubmissionMode.java`:

```java
package gov.bf.ascelc.univers_audits.enums;

public enum SubmissionMode {
    IN_PERSON,
    AUDIO_COUNTER,
    WEB_FORM,
    PAPER_FORM,
    EMAIL,
    SMS,
    PHONE,
    FAX,
    GREEN_NUMBER,
    SOCIAL_MEDIA,
    PRESS_MEDIA,
    AUDIT_REPORT,
    POSTAL_MAIL,
}
```

- [ ] **Step 8: Add the `FAX` label to `PdfExportService.getModeLabel`**

In `PdfExportService.java`, add a case to the existing `getModeLabel` switch:

```java
            case "FAX"          -> "Fax";
```

(Insert it among the existing `case` lines, e.g. right after `case "PHONE" -> "Téléphone";` — match the existing switch's exact syntax style, arrow-case with `->` and a trailing `;` on each arm, not `,` and not `:`/`break`.)

- [ ] **Step 9: Write the migration**

Create `src/main/resources/db/changelog/migrations/013-add-declarant-fields.sql`:

```sql
--liquibase formatted sql
--changeset dev:013-add-declarant-fields

ALTER TABLE declarant
    ADD COLUMN IF NOT EXISTS cellulaire VARCHAR(20),
    ADD COLUMN IF NOT EXISTS localite   VARCHAR(100);

COMMENT ON COLUMN declarant.cellulaire IS 'Numero de telephone cellulaire, distinct du telephone fixe (phone_number)';
COMMENT ON COLUMN declarant.localite   IS 'Localite (quartier/village), plus fine que commune/province';
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `mvn -q test -Dtest=DeclarantMapperTest`
Expected: both tests `PASS`.

- [ ] **Step 11: Verify the project compiles**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/Declarant.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DeclarantCreateRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DeclarantResponse.java src/main/java/gov/bf/ascelc/univers_audits/mapper/DeclarantMapper.java src/main/java/gov/bf/ascelc/univers_audits/enums/SubmissionMode.java src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java src/main/resources/db/changelog/migrations/013-add-declarant-fields.sql src/test/java/gov/bf/ascelc/univers_audits/mapper/DeclarantMapperTest.java
git commit -m "feat: add cellulaire/localite to Declarant, FAX submission mode"
```

---

### Task 2: Dossier-side fields (lieu de dépôt, organisme des faits, attentes, contrôles de recevabilité)

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DossierCreateRequest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierResponse.java`
- Create: `src/main/resources/db/changelog/migrations/014-add-dossier-fields.sql`

**Interfaces:**
- Produces: 7 new matching-named fields across `Dossier`/`DossierCreateRequest`/`DossierResponse`, auto-mapped by MapStruct (no `DossierMapper` change needed — verify this holds after Step 4, per the "no explicit mapping needed" note in the Global Constraints).

No new test for this task — 7 additive fields with no branching/masking logic (unlike Task 1's `cellulaire`/`localite`, none of these fields are personal-identifying data requiring anonymity masking — they describe the incident/organism/claim, not the declarant). Verified by compile.

- [ ] **Step 1: Add the fields to `Dossier`**

In `Dossier.java`, add these fields right after the existing `incidentPeriod` field:

```java
    @Column(name = "lieu_depot", length = 300)
    private String lieuDepot;

    @Column(name = "organisme_faits_denomination", length = 200)
    private String organismeFaitsDenomination;

    @Column(name = "organisme_faits_adresse", length = 300)
    private String organismeFaitsAdresse;

    @Column(name = "attentes", columnDefinition = "TEXT")
    private String attentes;

    @Column(name = "decision_justice_existante", nullable = false)
    @Builder.Default
    private Boolean decisionJusticeExistante = false;

    @Column(name = "decision_justice_precision", columnDefinition = "TEXT")
    private String decisionJusticePrecision;

    @Column(name = "autre_institution_saisie", nullable = false)
    @Builder.Default
    private Boolean autreInstitutionSaisie = false;

    @Column(name = "autre_institution_nom", length = 200)
    private String autreInstitutionNom;

    @Column(name = "autre_institution_adresse", length = 300)
    private String autreInstitutionAdresse;
```

- [ ] **Step 2: Add the fields to `DossierCreateRequest`**

In `DossierCreateRequest.java`, add right after the existing `incidentPeriod` field:

```java
    @Size(max = 300, message = "Le lieu de dépôt ne doit pas dépasser 300 caractères")
    private String lieuDepot;

    @Size(max = 200, message = "La dénomination de l'organisme ne doit pas dépasser 200 caractères")
    private String organismeFaitsDenomination;

    @Size(max = 300, message = "L'adresse de l'organisme ne doit pas dépasser 300 caractères")
    private String organismeFaitsAdresse;

    @Size(max = 5000, message = "Les attentes ne doivent pas dépasser 5000 caractères")
    private String attentes;

    private Boolean decisionJusticeExistante;

    @Size(max = 2000, message = "La précision ne doit pas dépasser 2000 caractères")
    private String decisionJusticePrecision;

    private Boolean autreInstitutionSaisie;

    @Size(max = 200, message = "Le nom de l'institution ne doit pas dépasser 200 caractères")
    private String autreInstitutionNom;

    @Size(max = 300, message = "L'adresse de l'institution ne doit pas dépasser 300 caractères")
    private String autreInstitutionAdresse;
```

- [ ] **Step 3: Add the fields to `DossierResponse`**

In `DossierResponse.java`, add right after the existing `incidentPeriod` field:

```java
    private String                     lieuDepot;
    private String                     organismeFaitsDenomination;
    private String                     organismeFaitsAdresse;
    private String                     attentes;
    private Boolean                    decisionJusticeExistante;
    private String                     decisionJusticePrecision;
    private Boolean                    autreInstitutionSaisie;
    private String                     autreInstitutionNom;
    private String                     autreInstitutionAdresse;
```

- [ ] **Step 4: Verify `DossierMapper` needs no change**

Open `DossierMapper.java` and confirm: none of the 9 new field names appear in any `@Mapping(target = "...", ignore = true)` list on `toResponse`/`toEntity`. Since the names and types now match exactly between `DossierCreateRequest`/`Dossier`/`DossierResponse`, MapStruct auto-maps them — no edit needed to this file. (If you find a reason this assumption doesn't hold once you're looking at the real file, stop and report — don't guess a fix.)

- [ ] **Step 5: Write the migration**

Create `src/main/resources/db/changelog/migrations/014-add-dossier-fields.sql`:

```sql
--liquibase formatted sql
--changeset dev:014-add-dossier-fields

ALTER TABLE dossier
    ADD COLUMN IF NOT EXISTS lieu_depot                   VARCHAR(300),
    ADD COLUMN IF NOT EXISTS organisme_faits_denomination VARCHAR(200),
    ADD COLUMN IF NOT EXISTS organisme_faits_adresse      VARCHAR(300),
    ADD COLUMN IF NOT EXISTS attentes                     TEXT,
    ADD COLUMN IF NOT EXISTS decision_justice_existante   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS decision_justice_precision   TEXT,
    ADD COLUMN IF NOT EXISTS autre_institution_saisie     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS autre_institution_nom        VARCHAR(200),
    ADD COLUMN IF NOT EXISTS autre_institution_adresse    VARCHAR(300);

COMMENT ON COLUMN dossier.lieu_depot IS 'Lieu de la denonciation/plainte (lieu de depot) - distinct de incident_location (lieu des faits)';
COMMENT ON COLUMN dossier.organisme_faits_denomination IS 'Organisme ou les faits allegues ont ete perpetres - distinct de la partie visee (targeted_party)';
COMMENT ON COLUMN dossier.attentes IS 'Ce que le deposant attend que l''ASCE-LC fasse - distinct d''object (titre court)';
COMMENT ON COLUMN dossier.decision_justice_existante IS 'Decision de justice deja rendue ou instance en cours - controle de recevabilite (litispendance)';
COMMENT ON COLUMN dossier.autre_institution_saisie IS 'Autre institution deja saisie des memes faits - controle de recevabilite (competence)';
```

- [ ] **Step 6: Verify the project compiles**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Run the full suite once**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, only the known pre-existing baseline failure `UniversAuditsApplicationTests.contextLoads` (no live datasource in this sandbox — unrelated to this plan).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DossierCreateRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierResponse.java src/main/resources/db/changelog/migrations/014-add-dossier-fields.sql
git commit -m "feat: add lieu de depot, organisme des faits, attentes and recevabilite fields to Dossier"
```

---

## Manual follow-up (not automatable in this plan)

- No Testcontainers/DB-integration test harness exists in this repo, so migrations `013`/`014` are not exercised by an automated test. Before deploying: confirm both migrations apply cleanly against a copy of the staging/dev database.
- Frontend coordination: 9 new optional fields are now accepted by `POST /api/v1/dossiers` (citizen submission) — not a breaking change, but the portal form should be updated to actually collect them, otherwise they'll simply stay empty.
- Not addressed by this plan (tracked in the ASCE-LC backlog memory): the dossier number format discrepancy, the `addDeclarantInfo` displayName-fallback gap, exploitation of `decisionJusticeExistante`/`autreInstitutionSaisie` in an étude d'opportunité screen (Lot 2, not started), and the Liquibase changelog-ordering bug affecting a fresh-database bootstrap.
