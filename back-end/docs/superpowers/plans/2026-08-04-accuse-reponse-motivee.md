# Accusé de réception / Réponse motivée (Lot 2, sous-chantier 4/4) — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Générer deux documents PDF distincts à partir de la `DecisionCGE` d'un dossier —
l'accusé de réception avec suites à donner (3 issues favorables/de transfert) et la réponse
motivée de rejet (issue classement) — clôturant le Lot 2 du plan de travail.

**Architecture:** Deux nouvelles méthodes sur `PdfExportService`, miroir exact du patron déjà
établi par `exportRecepisse` (en-tête/pied de page institutionnels réutilisés tels quels,
format générique aux couleurs ASCE-LC — la maquette exacte de l'annexe B5 n'est pas
disponible, même situation déjà assumée pour le récépissé/annexe B4). Chacune garde son
propre contrôle du type de décision CGE. Aucune nouvelle entité, aucune migration.

**Tech Stack:** Spring Boot 3 / Java 17, iText (déjà en place pour le récépissé/la fiche
dossier), JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Aucune invention de contenu au-delà de ce que `DecisionCGE` porte déjà (`decision`, `motif`,
  `dateDecision`, `agentCGE`) et du style institutionnel déjà établi
  (`AsceLcInstitutionalInfo`, couleurs `VERT_ASCE`/`OR_ASCE`/`TEXTE_GRIS` déjà déclarées dans
  `PdfExportService`).
- `exportAccuseReception` : rejette (`BusinessException`) si `dossier.getDecisionCGE() ==
  null`, ou si `decisionCGE.getDecision()` vaut `CLASSEMENT` (mauvais document — c'est le
  déclencheur de `exportReponseMotivee`).
- `exportReponseMotivee` : rejette si `decisionCGE == null`, ou si `decision != CLASSEMENT`.
- Réutiliser `addRecepisseFooter` et `addInfoCell` tels quels (déjà génériques, aucun
  paramètre spécifique au récépissé) — ne pas les dupliquer.
- `getStatusLabel` doit gagner un `case "ORIENTEE_ADMINISTRATIF"` (effet de bord assumé, ce
  fichier est de toute façon modifié par ce chantier — trou trouvé à la revue finale du
  sous-chantier précédent).

Spec de référence : `docs/superpowers/specs/2026-08-04-accuse-reponse-motivee-design.md`
Document source : `docs/reference/plan-de-travail-asce-lc.md` (§5, §6, §7, §9, §11)

---

### Task 1: `PdfExportService` — deux nouvelles méthodes d'export

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/PdfExportServiceTest.java`

- [ ] **Step 1: Corriger `getStatusLabel`**

Dans `getStatusLabel`, ajouter le cas manquant juste après `"TRANSFERE"` :

```java
            case "TRANSFERE"             -> "Transféré";
            case "ORIENTEE_ADMINISTRATIF" -> "Orienté (autorité hiérarchique)";
            case "EN_INVESTIGATION"      -> "Investigation";
```

- [ ] **Step 2: Ajouter `exportAccuseReception`**

Juste après `exportRecepisse` (avant la méthode privée `addRecepisseHeader`) :

```java
    public byte[] exportAccuseReception(UUID dossierId) {

        // findById applique le contrôle d'affectation/rôle et le masquage de
        // confidentialité — même garde que exportRecepisse/exportDossier.
        DossierResponse dossier = dossierService.findById(dossierId);

        if (dossier.getDecisionCGE() == null) {
            throw new BusinessException(
                    "Aucune décision CGE n'a encore été rendue pour ce dossier");
        }
        RecommandationCtadp decision = dossier.getDecisionCGE().getDecision();
        if (decision == RecommandationCtadp.CLASSEMENT) {
            throw new BusinessException(
                    "Ce dossier a été classé — utilisez l'export de la réponse motivée, "
                            + "pas l'accusé de réception");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addDecisionHeader(doc, dossier, fontBold, fontNormal,
                    "ACCUSÉ DE RÉCEPTION — SUITES À DONNER");
            addAccuseReceptionBody(doc, dossier, fontBold, fontNormal);
            addRecepisseFooter(doc, fontBold, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur export accusé de réception dossier {}: {}", dossierId, e.getMessage());
            throw new RuntimeException("Erreur génération accusé de réception: " + e.getMessage());
        }
    }
```

- [ ] **Step 3: Ajouter `exportReponseMotivee`**

Juste après `exportAccuseReception` :

```java
    public byte[] exportReponseMotivee(UUID dossierId) {

        DossierResponse dossier = dossierService.findById(dossierId);

        if (dossier.getDecisionCGE() == null) {
            throw new BusinessException(
                    "Aucune décision CGE n'a encore été rendue pour ce dossier");
        }
        if (dossier.getDecisionCGE().getDecision() != RecommandationCtadp.CLASSEMENT) {
            throw new BusinessException(
                    "Ce dossier n'a pas été classé — utilisez l'export de l'accusé de "
                            + "réception, pas la réponse motivée");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addDecisionHeader(doc, dossier, fontBold, fontNormal,
                    "RÉPONSE MOTIVÉE");
            addReponseMotiveeBody(doc, dossier, fontBold, fontNormal);
            addRecepisseFooter(doc, fontBold, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur export réponse motivée dossier {}: {}", dossierId, e.getMessage());
            throw new RuntimeException("Erreur génération réponse motivée: " + e.getMessage());
        }
    }
```

Note sur le `catch (BusinessException e) { throw e; }` ajouté avant le `catch (Exception e)`
générique dans les deux méthodes : sans ce bloc, la garde de type de décision (levée avant le
`try`, donc jamais interceptée en pratique — mais gardée par cohérence si jamais un futur
appel de garde était déplacé à l'intérieur du bloc `try`) serait capturée par le `catch
(Exception e)` générique et transformée en `RuntimeException` masquant le vrai message
métier. Conserver ce pattern même s'il est actuellement redondant avec le placement des
gardes avant le `try`.

- [ ] **Step 4: Ajouter `addDecisionHeader`**

Juste après `addRecepisseHeader` existant — variante paramétrée par le titre du document
(le récépissé garde son propre `addRecepisseHeader` inchangé, pas de fusion des deux) :

```java
    private void addDecisionHeader(Document doc, DossierResponse dossier,
                                   PdfFont fontBold, PdfFont fontNormal,
                                   String titre) {

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
        leftCell.add(new Paragraph(titre)
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
```

- [ ] **Step 5: Ajouter `addAccuseReceptionBody`**

```java
    private void addAccuseReceptionBody(Document doc, DossierResponse dossier,
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

        addInfoCell(table, "Date de la décision",
                dossier.getDecisionCGE().getDateDecision() != null
                        ? FMT_DATE.format(dossier.getDecisionCGE().getDateDecision()) : "—",
                fontBold, fontNormal);

        addInfoCell(table, "Décision",
                getStatusLabel(dossier.getStatus() != null ? dossier.getStatus().name() : ""),
                fontBold, fontNormal);

        addInfoCell(table, "Code de suivi",
                dossier.getAccessCode() != null ? dossier.getAccessCode() : "—",
                fontBold, fontNormal);

        doc.add(table);

        doc.add(new Paragraph(
                "L'ASCE-LC accuse réception de votre dossier et vous informe que la suite "
                        + "suivante lui a été donnée par le Contrôleur Général d'État. "
                        + "Conservez le code de suivi ci-dessus pour toute correspondance "
                        + "ultérieure.")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(40, 40, 40))
                .setMarginTop(8).setMarginBottom(12));

        String motif = dossier.getDecisionCGE().getMotif();
        if (motif != null && !motif.isBlank()) {
            doc.add(new Paragraph("Observations")
                    .setFont(fontBold).setFontSize(11)
                    .setFontColor(VERT_ASCE).setMarginBottom(4));
            doc.add(new Paragraph(motif)
                    .setFont(fontNormal).setFontSize(10)
                    .setFontColor(new DeviceRgb(40, 40, 40)));
        }
    }
```

- [ ] **Step 6: Ajouter `addReponseMotiveeBody`**

```java
    private void addReponseMotiveeBody(Document doc, DossierResponse dossier,
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

        addInfoCell(table, "Date de la décision",
                dossier.getDecisionCGE().getDateDecision() != null
                        ? FMT_DATE.format(dossier.getDecisionCGE().getDateDecision()) : "—",
                fontBold, fontNormal);

        addInfoCell(table, "Code de suivi",
                dossier.getAccessCode() != null ? dossier.getAccessCode() : "—",
                fontBold, fontNormal);

        doc.add(table);

        doc.add(new Paragraph(
                "Après examen, l'ASCE-LC vous informe que votre dossier a été classé sans "
                        + "suite, pour les motifs exposés ci-dessous.")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(40, 40, 40))
                .setMarginTop(8).setMarginBottom(12));

        doc.add(new Paragraph("Motifs du classement")
                .setFont(fontBold).setFontSize(11)
                .setFontColor(VERT_ASCE).setMarginBottom(4));

        String motif = dossier.getDecisionCGE().getMotif();
        doc.add(new Paragraph(motif != null && !motif.isBlank() ? motif : "—")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(40, 40, 40)));
    }
```

- [ ] **Step 7: Ajouter les imports nécessaires**

En tête de `PdfExportService.java`, ajouter (s'ils ne sont pas déjà présents) :

```java
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
```

- [ ] **Step 8: Écrire les tests**

Lire `PdfExportServiceTest.java` en entier avant d'écrire (déjà 3 tests existants pour
`exportRecepisse` — même patron de mock `dossierService.findById(dossierId)` à réutiliser) :

```java
    @Test
    void exportAccuseReception_producesNonEmptyPdfForValidationInvestigation() throws Exception {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000050")
                .accessCode("MNOP3456")
                .status(DossierStatus.RECEVABLE)
                .submissionMode(SubmissionMode.WEB_FORM)
                .decisionCGE(DecisionCGEResponse.builder()
                        .decision(RecommandationCtadp.VALIDATION_INVESTIGATION)
                        .dateDecision(Instant.now())
                        .motif("Preuves suffisantes pour ouvrir une investigation")
                        .build())
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        byte[] pdf = service.exportAccuseReception(dossierId);

        assertThat(pdf).isNotEmpty();

        String text;
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            text = PdfTextExtractor.getTextFromPage(pdfDoc.getFirstPage());
        }
        assertThat(text).contains("ACCUSÉ DE RÉCEPTION");
        assertThat(text).contains("Preuves suffisantes");
    }

    @Test
    void exportAccuseReception_rejectsWhenDecisionIsClassement() {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000051")
                .decisionCGE(DecisionCGEResponse.builder()
                        .decision(RecommandationCtadp.CLASSEMENT)
                        .dateDecision(Instant.now())
                        .build())
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        assertThatThrownBy(() -> service.exportAccuseReception(dossierId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void exportAccuseReception_rejectsWhenNoDecisionYet() {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000052")
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        assertThatThrownBy(() -> service.exportAccuseReception(dossierId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void exportReponseMotivee_producesNonEmptyPdfWithMotifForClassement() throws Exception {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000053")
                .accessCode("QRST7890")
                .status(DossierStatus.IRRECEVABLE)
                .decisionCGE(DecisionCGEResponse.builder()
                        .decision(RecommandationCtadp.CLASSEMENT)
                        .dateDecision(Instant.now())
                        .motif("Faits non constitutifs d'infraction")
                        .build())
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        byte[] pdf = service.exportReponseMotivee(dossierId);

        assertThat(pdf).isNotEmpty();

        String text;
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            text = PdfTextExtractor.getTextFromPage(pdfDoc.getFirstPage());
        }
        assertThat(text).contains("RÉPONSE MOTIVÉE");
        assertThat(text).contains("Faits non constitutifs d'infraction");
    }

    @Test
    void exportReponseMotivee_rejectsWhenDecisionIsNotClassement() {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000054")
                .decisionCGE(DecisionCGEResponse.builder()
                        .decision(RecommandationCtadp.TRANSMISSION_INSTITUTION_PARTENAIRE)
                        .dateDecision(Instant.now())
                        .build())
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        assertThatThrownBy(() -> service.exportReponseMotivee(dossierId))
                .isInstanceOf(BusinessException.class);
    }
```

Ajouter les imports nécessaires en tête du fichier de test s'ils sont absents :
`gov.bf.ascelc.univers_audits.enums.DossierStatus`,
`gov.bf.ascelc.univers_audits.enums.RecommandationCtadp`,
`gov.bf.ascelc.univers_audits.model.dto.response.DecisionCGEResponse`,
`gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException`,
`java.time.Instant`, `static org.assertj.core.api.Assertions.assertThatThrownBy`.

- [ ] **Step 9: Lancer les tests**

```
mvn test -q -Dtest=PdfExportServiceTest
```

Expected: BUILD SUCCESS, 8/8 tests (3 existants + 5 nouveaux).

- [ ] **Step 10: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/PdfExportServiceTest.java
git commit -m "feat: add accuse-reception and reponse-motivee PDF export (Lot 2)"
```

---

### Task 2: Contrôleur — deux nouveaux endpoints

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/PdfController.java`

**Interfaces:**
- Consumes: `PdfExportService.exportAccuseReception`/`.exportReponseMotivee` (Task 1).

- [ ] **Step 1: Ajouter les deux endpoints**

Juste après `exportRecepisse` dans `PdfController.java` :

```java
    @GetMapping("/accuse-reception/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportAccuseReception(
            @PathVariable UUID id) {

        log.info("Export accusé de réception dossier {}", id);
        byte[] pdf = pdfExportService.exportAccuseReception(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"accuse-reception-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/reponse-motivee/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportReponseMotivee(
            @PathVariable UUID id) {

        log.info("Export réponse motivée dossier {}", id);
        byte[] pdf = pdfExportService.exportReponseMotivee(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reponse-motivee-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
```

- [ ] **Step 2: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/PdfController.java
git commit -m "feat: add accuse-reception and reponse-motivee PDF endpoints"
```

---

### Task 3: Suite complète + mise à jour du backlog

**Files:** mémoire projet `project_asce_backlog_2026_07_30.md`.

- [ ] **Step 1: Lancer la suite complète**

```
mvn test -q
```

Expected: 0 échec (l'erreur `UniversAuditsApplicationTests.contextLoads` — absence de base de
données dans cet environnement — est un baseline connu, sans rapport avec ce chantier).

- [ ] **Step 2: Mettre à jour le backlog mémoire**

Marquer le sous-chantier "Accusé de réception / Réponse motivée" comme livré (4/4, dernier du
Lot 2 — **Lot 2 entièrement livré**). Mettre à jour la section C (état des lots) en
conséquence.
