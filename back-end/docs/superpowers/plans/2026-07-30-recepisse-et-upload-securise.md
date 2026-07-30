# Récépissé (B4) et sécurisation de l'upload de pièces jointes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a récépissé (deposit receipt, Annexe B4) PDF on demand, close a real security hole in the public attachment-upload endpoint, and correct institutional contact details that are wrong everywhere they currently appear in the codebase.

**Architecture:** A new shared constants class holds the ASCE-LC institutional contact block, sourced from the real paper form (ground truth, not the code's previous — incorrect — values), consumed by `PdfExportService` (both the existing fiche-dossier PDF and the new récépissé) and `EmailService`. `PdfExportService.exportRecepisse` mirrors the existing `exportDossier` method's architecture exactly (iText, `Document`, `DossierResponse` via `dossierService.findById` — which already enforces habilitation and confidentiality masking, so no separate access check is needed). `DossierAccessGuard` gains a new method, `checkAttachmentUploadAccess`, used by `AttachmentStorageService.upload` to allow anonymous uploads only while a dossier is in `SOUMIS` or `EN_ATTENTE_COMPLEMENT` status (the two moments an account-less citizen is expected to attach evidence), and to require normal authenticated habilitation otherwise.

**Tech Stack:** Spring Boot 3 / Java 17, iText 7 (PDF), JUnit 5 + Mockito + AssertJ, Liquibase.

## Global Constraints

- Institutional contact values (verified against the real paper forms provided by the user, 2026-07-30 — these are the ground truth, overriding what's currently hardcoded anywhere in the codebase):
  - Adresse : `01 BP 617 Ouagadougou 01 BF – Ouaga 2000 – Avenue Pascal ZAGRE`
  - Téléphone : `(00226) 25 37 40 56`
  - Email info : `info@asce-lc.bf`
  - Email contact : `contact@asce-lc.bf`
  - Site web : `www.asce-lc.bf`
  - Numéro vert : `80 00 11 02`
  - Slogan : `Au nom de notre intégrité, combattons la corruption !`
- `PdfController.exportDossier` needs **no new access check** — `PdfExportService.exportDossier` already calls `dossierService.findById(dossierId)`, which enforces `DossierAccessGuard.checkReadAccess` and confidentiality masking (confirmed by reading the existing code and its own comment at `PdfExportService.java:57-58`). Do not add a redundant guard call here — this was a mistaken finding during design that this plan corrects.
- `exportRecepisse` must follow the exact same pattern (route access through `dossierService.findById`, never call the repository or the guard directly) for the same reason: single source of truth for dossier access.
- `checkAttachmentUploadAccess` is a distinct method from `checkReadAccess` — it must NOT replace or alter `checkReadAccess`'s existing behavior or its 4 existing passing tests.
- `SecurityConfig`'s `permitAll()` on `POST /api/v1/attachments/dossier/**` is NOT touched — the fix happens inside `AttachmentStorageService.upload`, not at the HTTP filter level (the anonymous case must still reach the method).
- No new `AttachmentType`/`AttachmentSource` enum values — the existing `DOCUMENT`/`INITIAL_SUBMISSION` combination (already the one `upload()` assigns) is sufficient for a scanned signed paper form.
- The récépissé does not include a "lieu de dépôt" field — no such field exists on any entity today, and adding one is out of scope for this plan (tracked separately in the ASCE-LC backlog memory).

---

## File Structure

| File | Responsibility |
|---|---|
| `shared/utils/AsceLcInstitutionalInfo.java` (new) | Single source of truth for institutional contact constants |
| `service/PdfExportService.java` (modified) | Fixes existing footer values; adds `exportRecepisse` |
| `service/EmailService.java` (modified) | Fixes hardcoded address/hotline in the email footer |
| `controller/PdfController.java` (modified) | Adds `GET /api/v1/pdf/recepisse/{id}` |
| `db/changelog/migrations/011-fix-portal-config-hotline.sql` (new) | Corrects the seeded `hotline_number` value |
| `shared/utils/DossierAccessGuard.java` (modified) | Adds `checkAttachmentUploadAccess` |
| `service/AttachmentStorageService.java` (modified) | Calls the new guard method before writing any file |

---

### Task 1: Institutional constants — centralize and correct

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/AsceLcInstitutionalInfo.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java:390-394` (existing `addFooter` method)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/EmailService.java:309-319` (`FOOTER` constant)
- Create: `src/main/resources/db/changelog/migrations/011-fix-portal-config-hotline.sql`

**Interfaces:**
- Produces: `AsceLcInstitutionalInfo.{ADDRESS, PHONE, EMAIL_INFO, EMAIL_CONTACT, WEBSITE, NUMERO_VERT, SLOGAN}` (all `public static final String`), consumed by Task 2's `exportRecepisse`.

No new test for this task — it corrects existing hardcoded string values with no new branching logic; verified by compile plus a visual/grep check that no old wrong value remains.

- [ ] **Step 1: Create the constants class**

```java
package gov.bf.ascelc.univers_audits.shared.utils;

/**
 * Coordonnées institutionnelles ASCE-LC — source unique, alignée sur le
 * formulaire papier officiel (référence du 2026-07-30). Les valeurs
 * précédemment codées en dur dans PdfExportService et EmailService étaient
 * incorrectes et divergentes entre elles ; celle-ci fait foi.
 */
public final class AsceLcInstitutionalInfo {

    private AsceLcInstitutionalInfo() {}

    public static final String ADDRESS =
            "01 BP 617 Ouagadougou 01 BF – Ouaga 2000 – Avenue Pascal ZAGRE";
    public static final String PHONE = "(00226) 25 37 40 56";
    public static final String EMAIL_INFO = "info@asce-lc.bf";
    public static final String EMAIL_CONTACT = "contact@asce-lc.bf";
    public static final String WEBSITE = "www.asce-lc.bf";
    public static final String NUMERO_VERT = "80 00 11 02";
    public static final String SLOGAN =
            "Au nom de notre intégrité, combattons la corruption !";
}
```

- [ ] **Step 2: Fix `PdfExportService.addFooter`**

Add the import (alongside the other imports at the top of the file):

```java
import gov.bf.ascelc.univers_audits.shared.utils.AsceLcInstitutionalInfo;
```

Replace, inside `addFooter`:

```java
        doc.add(new Paragraph(
                "Document confidentiel — ASCE-LC Burkina Faso | " +
                        "03 BP 7204 Ouagadougou 03 | " +
                        "Tél: +226 25 36 62 62 | " +
                        "Numéro vert: 80 00 11 57")
                .setFont(fontNormal)
```

with:

```java
        doc.add(new Paragraph(
                "Document confidentiel — ASCE-LC Burkina Faso | " +
                        AsceLcInstitutionalInfo.ADDRESS + " | " +
                        "Tél: " + AsceLcInstitutionalInfo.PHONE + " | " +
                        "Numéro vert: " + AsceLcInstitutionalInfo.NUMERO_VERT)
                .setFont(fontNormal)
```

- [ ] **Step 3: Fix `EmailService.FOOTER`**

Add the import (alongside `EmailService`'s existing imports):

```java
import gov.bf.ascelc.univers_audits.shared.utils.AsceLcInstitutionalInfo;
```

Replace:

```java
    private static final String FOOTER = """
        <div class="footer">
          <p>
            Ce message est généré automatiquement — merci de ne pas y répondre.<br>
            <strong>ASCE-LC</strong> · 03 BP 7204 Ouagadougou 03, Burkina Faso<br>
            Numéro Vert : <strong>80 00 11 11</strong> ·
            <a href="mailto:contact@asce-lc.bf">contact@asce-lc.bf</a><br><br>
            Vos informations sont traitées conformément à la Loi N°010-2004/AN
          </p>
        </div>
        """;
```

with:

```java
    private static final String FOOTER = """
        <div class="footer">
          <p>
            Ce message est généré automatiquement — merci de ne pas y répondre.<br>
            <strong>ASCE-LC</strong> · %s<br>
            Numéro Vert : <strong>%s</strong> ·
            <a href="mailto:%s">%s</a><br><br>
            Vos informations sont traitées conformément à la Loi N°010-2004/AN
          </p>
        </div>
        """.formatted(
                AsceLcInstitutionalInfo.ADDRESS,
                AsceLcInstitutionalInfo.NUMERO_VERT,
                AsceLcInstitutionalInfo.EMAIL_CONTACT,
                AsceLcInstitutionalInfo.EMAIL_CONTACT);
```

- [ ] **Step 4: Write the migration**

Create `src/main/resources/db/changelog/migrations/011-fix-portal-config-hotline.sql`:

```sql
--liquibase formatted sql
--changeset dev:011-fix-portal-config-hotline

-- La valeur seedée en 006-create-portal-config.sql ('80 00 11 11') est
-- incorrecte. La vraie valeur, confirmée sur le formulaire papier officiel,
-- est '80 00 11 02'.
UPDATE portal_config
    SET config_value = '80 00 11 02', updated_at = now()
    WHERE config_key = 'hotline_number';
```

- [ ] **Step 5: Verify the project compiles and no wrong value remains**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`.

Then check no stale wrong value remains in source (should return nothing):
`grep -rn "7204\|25 36 62 62\|80 00 11 57\|80 00 11 11" src/main/java src/main/resources/db/changelog/migrations/`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/utils/AsceLcInstitutionalInfo.java src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java src/main/java/gov/bf/ascelc/univers_audits/service/EmailService.java src/main/resources/db/changelog/migrations/011-fix-portal-config-hotline.sql
git commit -m "fix: correct institutional contact details, centralize as shared constants"
```

---

### Task 2: Récépissé PDF generation

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java` (add `exportRecepisse` + helpers)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/PdfController.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/PdfExportServiceTest.java` (new — first test for this class)

**Interfaces:**
- Consumes: `AsceLcInstitutionalInfo` (Task 1); existing `DossierService.findById(UUID)` (unchanged signature).
- Produces: `PdfExportService.exportRecepisse(UUID dossierId): byte[]`, consumed by `PdfController`.

- [ ] **Step 1: Write the failing test**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.repository.StatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfExportServiceTest {

    @Mock private DossierService dossierService;
    @Mock private StatusHistoryRepository statusHistoryRepository;

    @InjectMocks
    private PdfExportService service;

    @Test
    void exportRecepisse_producesNonEmptyPdfForNamedDeclarant() {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000042")
                .accessCode("ABCD1234")
                .type(TypeSaisine.DENONCIATION)
                .quality(QualiteDeclarant.TEMOIN)
                .submissionMode(SubmissionMode.IN_PERSON)
                .object("Marché public suspect")
                .receptionDate(Instant.now())
                .declarant(DeclarantResponse.builder()
                        .firstName("Awa")
                        .lastName("Ouedraogo")
                        .anonymous(false)
                        .build())
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        byte[] pdf = service.exportRecepisse(dossierId);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void exportRecepisse_producesNonEmptyPdfForAnonymousDeclarant() {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000043")
                .accessCode("EFGH5678")
                .type(TypeSaisine.DENONCIATION)
                .quality(QualiteDeclarant.TEMOIN)
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Détournement présumé")
                .receptionDate(Instant.now())
                .declarant(DeclarantResponse.builder()
                        .anonymous(true)
                        .build())
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        byte[] pdf = service.exportRecepisse(dossierId);

        assertThat(pdf).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=PdfExportServiceTest`
Expected: compile error — `exportRecepisse` does not exist yet.

- [ ] **Step 3: Implement `exportRecepisse`**

Add this public method to `PdfExportService`, right after `exportDossier`:

```java
    public byte[] exportRecepisse(UUID dossierId) {

        // findById applique le contrôle d'affectation/rôle et le masquage de
        // confidentialité — le récépissé ne doit jamais exposer plus que
        // l'API JSON, exactement comme exportDossier.
        DossierResponse dossier = dossierService.findById(dossierId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addRecepisseHeader(doc, dossier, fontBold, fontNormal);
            addRecepisseBody(doc, dossier, fontBold, fontNormal);
            addRecepisseFooter(doc, fontBold, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur export récépissé dossier {}: {}", dossierId, e.getMessage());
            throw new RuntimeException("Erreur génération récépissé: " + e.getMessage());
        }
    }

    private void addRecepisseHeader(Document doc, DossierResponse dossier,
                                    PdfFont fontBold, PdfFont fontNormal) {

        Table topBar = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setHeight(6)
                .setBackgroundColor(OR_ASCE)
                .setBorder(Border.NO_BORDER)
                .setMarginBottom(0);
        topBar.addCell(new Cell().setBorder(Border.NO_BORDER).add(new Paragraph("")));
        doc.add(topBar);

        Table header = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(VERT_ASCE)
                .setBorder(Border.NO_BORDER)
                .setMarginBottom(20);

        Cell leftCell = new Cell().setBorder(Border.NO_BORDER).setPadding(20);
        leftCell.add(new Paragraph("ASCE-LC")
                .setFont(fontBold).setFontSize(22)
                .setFontColor(ColorConstants.WHITE).setMarginBottom(4));
        leftCell.add(new Paragraph("Autorité Supérieure de Contrôle d'État")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(200, 230, 210)).setMarginBottom(2));
        leftCell.add(new Paragraph("et de Lutte contre la Corruption")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(200, 230, 210)).setMarginBottom(8));
        leftCell.add(new Paragraph("RÉCÉPISSÉ DE DÉPÔT")
                .setFont(fontBold).setFontSize(14).setFontColor(OR_ASCE));
        header.addCell(leftCell);

        Cell rightCell = new Cell()
                .setBorder(Border.NO_BORDER).setPadding(20)
                .setTextAlignment(TextAlignment.RIGHT);
        String number = dossier.getNumber() != null ? dossier.getNumber() : "En attente";
        rightCell.add(new Paragraph(number)
                .setFont(fontBold).setFontSize(16)
                .setFontColor(ColorConstants.WHITE).setMarginBottom(8));
        if (dossier.getAccessCode() != null) {
            rightCell.add(new Paragraph("Code de suivi : " + dossier.getAccessCode())
                    .setFont(fontNormal).setFontSize(9)
                    .setFontColor(new DeviceRgb(180, 220, 195)));
        }
        header.addCell(rightCell);
        doc.add(header);
    }

    private void addRecepisseBody(Document doc, DossierResponse dossier,
                                  PdfFont fontBold, PdfFont fontNormal) {

        doc.add(new Paragraph("CONFIDENTIEL")
                .setFont(fontBold).setFontSize(10)
                .setFontColor(new DeviceRgb(180, 30, 30))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(16));

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        addInfoCell(table, "Numéro d'enregistrement",
                dossier.getNumber() != null ? dossier.getNumber() : "—",
                fontBold, fontNormal);

        addInfoCell(table, "Date et heure de réception",
                dossier.getReceptionDate() != null
                        ? FMT.format(dossier.getReceptionDate()) : "—",
                fontBold, fontNormal);

        addInfoCell(table, "Mode de réception",
                getModeLabel(dossier.getSubmissionMode() != null
                        ? dossier.getSubmissionMode().name() : ""),
                fontBold, fontNormal);

        addInfoCell(table, "Objet",
                dossier.getObject() != null ? dossier.getObject() : "—",
                fontBold, fontNormal);

        DeclarantResponse declarant = dossier.getDeclarant();
        String deposantLabel = declarant != null && Boolean.TRUE.equals(declarant.getAnonymous())
                ? "Anonyme"
                : declarant != null
                        ? ((declarant.getFirstName() != null ? declarant.getFirstName() : "")
                                + " " + (declarant.getLastName() != null ? declarant.getLastName() : "")).trim()
                        : "—";
        addInfoCell(table, "Déposant", deposantLabel.isEmpty() ? "—" : deposantLabel,
                fontBold, fontNormal);

        addInfoCell(table, "Code de suivi",
                dossier.getAccessCode() != null ? dossier.getAccessCode() : "—",
                fontBold, fontNormal);

        doc.add(table);

        doc.add(new Paragraph(
                "Ce récépissé atteste de la réception de votre dénonciation ou plainte par "
                        + "l'ASCE-LC. Conservez le code de suivi ci-dessus : il vous permet de "
                        + "suivre l'état d'avancement de votre dossier sur le portail en ligne, "
                        + "sans qu'aucune information permettant de vous identifier ne soit requise.")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(40, 40, 40))
                .setMarginTop(8));
    }

    private void addRecepisseFooter(Document doc, PdfFont fontBold, PdfFont fontNormal) {

        doc.add(new Paragraph()
                .setBorderTop(new SolidBorder(VERT_ASCE, 1))
                .setMarginTop(20)
                .setMarginBottom(8));

        doc.add(new Paragraph(
                "Adresse postale : " + AsceLcInstitutionalInfo.ADDRESS)
                .setFont(fontNormal).setFontSize(8).setFontColor(TEXTE_GRIS)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph(
                "Tél. : " + AsceLcInstitutionalInfo.PHONE
                        + " - E-mail : " + AsceLcInstitutionalInfo.EMAIL_INFO
                        + " ou " + AsceLcInstitutionalInfo.EMAIL_CONTACT
                        + " - Site web : " + AsceLcInstitutionalInfo.WEBSITE
                        + " – Numéro vert : " + AsceLcInstitutionalInfo.NUMERO_VERT)
                .setFont(fontNormal).setFontSize(8).setFontColor(TEXTE_GRIS)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph(AsceLcInstitutionalInfo.SLOGAN)
                .setFont(fontBold).setFontSize(8).setFontColor(VERT_ASCE)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(4));
    }
```

Add the import (alongside the other imports at the top of the file — if Task 1 already added it, this is a no-op, just confirm it's present):

```java
import gov.bf.ascelc.univers_audits.shared.utils.AsceLcInstitutionalInfo;
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=PdfExportServiceTest`
Expected: both tests `PASS`.

- [ ] **Step 5: Add the controller endpoint**

In `PdfController.java`, add this method after `exportDossier`:

```java
    @GetMapping("/recepisse/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportRecepisse(
            @PathVariable UUID id) {

        log.info("Export récépissé dossier {}", id);
        byte[] pdf = pdfExportService.exportRecepisse(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"recepisse-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
```

(`@PreAuthorize("isAuthenticated()")` matches `exportDossier`'s own annotation exactly — the real access control is `dossierService.findById`'s internal habilitation check, same as for `exportDossier`.)

- [ ] **Step 6: Verify the project compiles**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java src/main/java/gov/bf/ascelc/univers_audits/controller/PdfController.java src/test/java/gov/bf/ascelc/univers_audits/service/PdfExportServiceTest.java
git commit -m "feat: generate recepisse (Annexe B4) PDF on demand"
```

---

### Task 3: Secure the public attachment-upload endpoint

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuard.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageService.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuardTest.java` (already exists — extend it)
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageServiceTest.java` (new — first test for this class)

**Interfaces:**
- Produces: `DossierAccessGuard.checkAttachmentUploadAccess(Dossier dossier): void`, consumed by `AttachmentStorageService.upload`.

**Read `DossierAccessGuardTest.java` first** — it already exists with 4 tests for `checkReadAccess` and the same 4 `@Mock` fields you'll need (`dossierRepository`, `agentRepository`, `securityUtils`, `habilitationRepository`) plus `@InjectMocks DossierAccessGuard guard`. You are extending this file, not replacing it — do not modify the 4 existing tests.

- [ ] **Step 1: Write the failing tests**

Add these methods to the existing `DossierAccessGuardTest` class (no new `@Mock` fields needed — reuses the existing ones):

```java
    @Test
    void checkAttachmentUploadAccess_allowsAnonymousWhileSoumis() {
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID())
                .status(gov.bf.ascelc.univers_audits.enums.DossierStatus.SOUMIS)
                .build();

        assertThatCode(() -> guard.checkAttachmentUploadAccess(dossier))
                .doesNotThrowAnyException();
    }

    @Test
    void checkAttachmentUploadAccess_allowsAnonymousWhileAwaitingComplement() {
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID())
                .status(gov.bf.ascelc.univers_audits.enums.DossierStatus.EN_ATTENTE_COMPLEMENT)
                .build();

        assertThatCode(() -> guard.checkAttachmentUploadAccess(dossier))
                .doesNotThrowAnyException();
    }

    @Test
    void checkAttachmentUploadAccess_delegatesToReadAccessForOtherStatuses() {
        when(securityUtils.hasRole("CGE")).thenReturn(false);
        when(securityUtils.hasRole("CGEA")).thenReturn(false);
        when(securityUtils.hasRole("ADMIN_DDIC")).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());

        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID())
                .status(gov.bf.ascelc.univers_audits.enums.DossierStatus.EN_INVESTIGATION)
                .build();

        assertThatThrownBy(() -> guard.checkAttachmentUploadAccess(dossier))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void checkAttachmentUploadAccess_allowsHabilitatedAgentForOtherStatuses() {
        when(securityUtils.hasRole("CGE")).thenReturn(false);
        when(securityUtils.hasRole("CGEA")).thenReturn(false);
        when(securityUtils.hasRole("ADMIN_DDIC")).thenReturn(false);

        UUID agentId   = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder()
                .id(dossierId)
                .status(gov.bf.ascelc.univers_audits.enums.DossierStatus.EN_INVESTIGATION)
                .build();
        Agent agent = Agent.builder().id(agentId).build();

        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-1"));
        when(agentRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(agent));
        when(habilitationRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId))
                .thenReturn(true);

        assertThatCode(() -> guard.checkAttachmentUploadAccess(dossier))
                .doesNotThrowAnyException();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=DossierAccessGuardTest`
Expected: compile error — `checkAttachmentUploadAccess` does not exist yet.

- [ ] **Step 3: Add `checkAttachmentUploadAccess` to `DossierAccessGuard`**

Add the import:

```java
import gov.bf.ascelc.univers_audits.enums.DossierStatus;
```

Add this method, right after `checkReadAccess`:

```java
    /**
     * Lève BusinessException sauf pour un dépôt de pièce jointe légitime
     * sans compte : au dépôt initial (SOUMIS) ou en réponse à une demande
     * de complément (EN_ATTENTE_COMPLEMENT), la requête est anonyme et ne
     * porte aucune information d'authentification — c'est attendu, pas une
     * faille. En dehors de ces deux statuts, se comporte comme
     * checkReadAccess : authentification et habilitation nominative exigées.
     */
    public void checkAttachmentUploadAccess(Dossier dossier) {
        if (dossier.getStatus() == DossierStatus.SOUMIS
                || dossier.getStatus() == DossierStatus.EN_ATTENTE_COMPLEMENT) {
            return;
        }
        checkReadAccess(dossier);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q test -Dtest=DossierAccessGuardTest`
Expected: all 8 tests `PASS` (4 pre-existing + 4 new).

- [ ] **Step 5: Write the failing test for `AttachmentStorageService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentStorageServiceTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private DossierRepository    dossierRepository;
    @Mock private DossierAccessGuard   accessGuard;

    @InjectMocks
    private AttachmentStorageService service;

    @Test
    void upload_checksAttachmentUploadAccessBeforeWritingAnything() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder()
                .id(dossierId)
                .status(DossierStatus.EN_INVESTIGATION)
                .build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkAttachmentUploadAccess(dossier);

        assertThatThrownBy(() -> service.upload(dossierId.toString(), List.of()))
                .isInstanceOf(BusinessException.class);

        verify(attachmentRepository, never()).save(any());
    }
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `mvn -q test -Dtest=AttachmentStorageServiceTest`
Expected: compile error — `AttachmentStorageService` has no `DossierAccessGuard` field yet, and never calls it.

- [ ] **Step 7: Wire the guard into `AttachmentStorageService.upload`**

Add the import:

```java
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
```

Add the field (after `private final DossierRepository dossierRepository;`):

```java
    private final DossierAccessGuard   accessGuard;
```

In `upload(String dossierId, List<MultipartFile> files)`, replace:

```java
        Dossier dossier = dossierRepository.findById(UUID.fromString(dossierId))
                .orElseThrow(() -> new BusinessException(
                        "Dossier introuvable: " + dossierId));

        Path dir = Paths.get(uploadDir, dossierId);
```

with:

```java
        Dossier dossier = dossierRepository.findById(UUID.fromString(dossierId))
                .orElseThrow(() -> new BusinessException(
                        "Dossier introuvable: " + dossierId));
        accessGuard.checkAttachmentUploadAccess(dossier);

        Path dir = Paths.get(uploadDir, dossierId);
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q test -Dtest=AttachmentStorageServiceTest`
Expected: the test `PASS`.

- [ ] **Step 9: Run the full suite once**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, only the known pre-existing baseline failure
`UniversAuditsApplicationTests.contextLoads` (no live datasource in this sandbox — unrelated to this plan).

- [ ] **Step 10: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuard.java src/main/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageService.java src/test/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuardTest.java src/test/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageServiceTest.java
git commit -m "fix: restrict public attachment upload to legitimate anonymous-citizen windows"
```

---

## Manual follow-up (not automatable in this plan)

- No Testcontainers/DB-integration test harness exists in this repo, so the migration (Task 1, Step 4) is not exercised by an automated test. Before deploying: confirm `SELECT config_value FROM portal_config WHERE config_key = 'hotline_number';` returns `80 00 11 02`.
- The dossier number format discrepancy noted during design (code generates `ASCE-YYYY-NNNNNN`, the paper form shows a bare `NNNNN/YYYY` style) is NOT addressed by this plan — flagged separately in the ASCE-LC backlog memory for a future decision.
- The 8 missing form fields identified during the form comparison (téléphone/cellulaire split, localité, mode FAX, lieu de dépôt, organisme des faits, attentes, décision de justice, autre institution saisie) are explicitly out of scope for this plan — tracked as the next chantier.
