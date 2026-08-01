# Déplacement de `anonymous` du Déclarant vers le Dossier — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corriger le bug de péremption où `Declarant.anonymous` (partagé entre dossiers via
la réutilisation par email/téléphone dans `resolveDeclarant()`) fait dériver la nature de
saisine et le masquage d'identité d'un état obsolète plutôt que de la soumission courante.

**Architecture:** `anonymous` devient une colonne de `Dossier` (comme `quality`, migration
`009`), plus jamais de `Declarant`. Toute lecture d'anonymat pour DÉCIDER quelque chose sur
la saisine courante (dérivation de nature, masquage de réponse, PDF, notifications) doit
passer par le `Dossier`/`DossierResponse`/`DossierCreateRequest`, jamais par l'entité/DTO
`Declarant` réutilisable.

**Tech Stack:** Spring Boot 3, Liquibase (formatted SQL), MapStruct, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Migration Liquibase : fichier `015-declarant-anonymous-to-dossier.sql` dans
  `src/main/resources/db/changelog/migrations/`, format `--liquibase formatted sql`, id
  `dev:015-declarant-anonymous-to-dossier` — suit `includeAll` (dossier `migrations/`), donc
  pas d'ajout requis dans `db.changelog-master.yaml`.
- Ne jamais introduire de branchement `uses = {DeclarantMapper.class}` sur `DossierMapper` —
  hors périmètre, `DeclarantMapper.toResponse()/fillAndMask()` restent du code mort assumé
  (cf. spec §"Hors périmètre").
- Ne pas toucher à la réconciliation des autres champs du déclarant réutilisé (adresse,
  téléphone…) — hors périmètre.
- Chaque tâche doit laisser le projet compilable et les tests existants verts avant de passer
  à la suivante (`mvn test -q` ou équivalent module ciblé).

Spec de référence : `docs/superpowers/specs/2026-08-01-declarant-anonymous-vers-dossier-design.md`

---

### Task 1: Migration + entité `Declarant`/`Dossier`

**Files:**
- Create: `src/main/resources/db/changelog/migrations/015-declarant-anonymous-to-dossier.sql`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Declarant.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DeclarantMapper.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/mapper/DeclarantMapperTest.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java`

**Interfaces:**
- Produces: `Dossier.getAnonymous()`/`setAnonymous(Boolean)` (nouveau, `@Builder.Default =
  false`) ; `Declarant.isAnonymous()` redéfini en `typeDeclarant == ANONYMOUS` uniquement
  (aucun champ booléen `anonymous` sur `Declarant`).

- [ ] **Step 1: Écrire la migration**

Créer `src/main/resources/db/changelog/migrations/015-declarant-anonymous-to-dossier.sql` :

```sql
--liquibase formatted sql
--changeset dev:015-declarant-anonymous-to-dossier

ALTER TABLE dossier ADD COLUMN IF NOT EXISTS anonymous BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN dossier.anonymous IS 'Demande d''anonymat du declarant pour CETTE saisine uniquement — ne doit pas etre deduite de declarant.anonymous (personne reutilisable entre dossiers)';

-- Report l'anonymat historique du declarant vers chacun de ses dossiers, avant de
-- supprimer la colonne : un declarant marque anonymous=true l'etait potentiellement
-- sur tous ses dossiers existants (aucune granularite par dossier avant cette migration).
UPDATE dossier d SET anonymous = TRUE
    FROM declarant de
    WHERE d.declarant_id = de.id AND de.anonymous = TRUE;

ALTER TABLE declarant DROP COLUMN IF EXISTS anonymous;
```

- [ ] **Step 2: Ajouter `Dossier.anonymous`**

Dans `Dossier.java`, juste après le champ `quality` (ligne 59) :

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "quality", length = 30)
    private QualiteDeclarant quality;

    @Column(name = "anonymous", nullable = false)
    @Builder.Default
    private Boolean anonymous = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_mode", length = 25)
    private SubmissionMode submissionMode;
```

- [ ] **Step 3: Retirer `Declarant.anonymous` et adapter les méthodes dérivées**

Dans `Declarant.java`, retirer entièrement ce bloc :

```java
    @Column(name = "anonymous", nullable = false)
    @Builder.Default
    private Boolean anonymous = false;
```

Remplacer `getDisplayName()` :

```java
    public String getDisplayName() {
        if (TypeDeclarant.ANONYMOUS.equals(typeDeclarant)) {
            return "Anonymous";
        }
        if (TypeDeclarant.ASCE_SELF_REFERRAL.equals(typeDeclarant)) {
            return "ASCE-LC (Self-referral)";
        }
        if (organizationName != null && !organizationName.isBlank()) {
            return organizationName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return "Not provided";
    }
```

Remplacer `isAnonymous()` :

```java
    public boolean isAnonymous() {
        return TypeDeclarant.ANONYMOUS.equals(typeDeclarant);
    }
```

Dans `onPrePersist()`, retirer les deux lignes devenues invalides :

```java
        if (anonymous == null)
            anonymous = false;
```

et

```java
        if (TypeDeclarant.ANONYMOUS.equals(typeDeclarant))
            anonymous = true;
```

`onPrePersist()` devient :

```java
    @PrePersist
    protected void onPrePersist() {
        if (protectionRequested == null)
            protectionRequested = false;
        if (dataProcessingConsent == null)
            dataProcessingConsent = false;
        if (notificationsAccepted == null)
            notificationsAccepted = true;
        if (typeDeclarant == null)
            typeDeclarant = TypeDeclarant.CITIZEN;
    }
```

- [ ] **Step 4: Corriger `DeclarantMapper`**

Dans `DeclarantMapper.java`, retirer ces deux lignes de `toEntity()` (le champ cible
`Declarant.anonymous` n'existe plus) :

```java
    @Mapping(target = "anonymous",
            source = "anonymous",
            defaultValue = "false")
```

`fillAndMask()` n'a besoin d'aucun changement de code — `declarant.isAnonymous()` compile et
se comporte désormais selon la nouvelle sémantique (`typeDeclarant == ANONYMOUS`).

- [ ] **Step 5: Corriger `DeclarantMapperTest`**

Le premier test visait à prouver que `fillAndMask()` masque bien un déclarant anonyme. Avec
la nouvelle sémantique d'`isAnonymous()`, il faut piloter `typeDeclarant` plutôt que le
booléen retiré :

```java
    @Test
    void toResponse_masksCellulaireAndLocaliteForAnonymousDeclarant() {
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.ANONYMOUS)
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
                .firstName("Awa")
                .lastName("Ouedraogo")
                .cellulaire("70000000")
                .localite("Tampouy")
                .build();

        DeclarantResponse response = mapper.toResponse(declarant);

        assertThat(response.getCellulaire()).isEqualTo("70000000");
        assertThat(response.getLocalite()).isEqualTo("Tampouy");
    }
```

(Seul changement : `.typeDeclarant(TypeDeclarant.ANONYMOUS)` remplace
`.typeDeclarant(TypeDeclarant.CITIZEN).anonymous(true)` dans le premier test ; suppression de
`.anonymous(false)`, sans autre effet, dans le second.)

- [ ] **Step 6: Corriger `DossierServiceImplTest`**

Retirer `.anonymous(...)` des trois `Declarant.builder()` de ce fichier — le champ n'existe
plus sur l'entité. Les valeurs `typeDeclarant` déjà présentes dans ces trois tests
(`CITIZEN`, `PUBLIC_AUTHORITY`, `ANONYMOUS`) reproduisent exactement le même résultat pour
`isAnonymous()` qu'avant (respectivement `false`, `false`, `true`), donc aucune autre
assertion ni stub `natureSaisineResolver.resolve(...)` n'a besoin de changer à cette étape :

```java
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .build();
```
(dans `submit_appliesResolvedNatureSaisineToNewDossier`, retirer `.anonymous(false)`)

```java
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.PUBLIC_AUTHORITY)
                .build();
```
(dans `submit_nullsQualityForSignalement`, retirer `.anonymous(false)`)

```java
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.ANONYMOUS)
                .build();
```
(dans `submit_propagatesBusinessExceptionFromResolverWithoutSaving`, retirer
`.anonymous(true)`)

`DeclarantCreateRequest.builder().anonymous(false)` dans `buildRequest()` (ligne ~83) et dans
`submit_nullsQualityForSignalement` (ligne ~123) : **laisser tel quel pour l'instant** — ce
champ n'est retiré de `DeclarantCreateRequest` qu'à la Task 2. Le retirer ici casserait
inutilement la compilation en avance de phase.

- [ ] **Step 7: Vérifier la compilation et les tests**

```
mvn test -q -pl . -Dtest=DeclarantMapperTest,DossierServiceImplTest
```

Expected: BUILD SUCCESS, tous les tests verts (aucune assertion nouvelle à ce stade, seule
la structure change).

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/changelog/migrations/015-declarant-anonymous-to-dossier.sql \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/Declarant.java \
        src/main/java/gov/bf/ascelc/univers_audits/mapper/DeclarantMapper.java \
        src/test/java/gov/bf/ascelc/univers_audits/mapper/DeclarantMapperTest.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java
git commit -m "feat: move anonymous flag from Declarant entity to Dossier"
```

---

### Task 2: DTOs `Declarant*`/`Dossier*` + `PdfExportService` (couplage de compilation)

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DeclarantCreateRequest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DeclarantResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DossierCreateRequest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/PdfExportServiceTest.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java`

**Interfaces:**
- Consumes: rien de nouveau côté Task 1 (entités déjà stables).
- Produces: `DossierCreateRequest.getAnonymous()`, `DossierResponse.getAnonymous()` — utilisés
  par les Tasks 3 et 4.

Ce regroupement est nécessaire : `PdfExportService` lit `DeclarantResponse.getAnonymous()`,
qui disparaît dans cette même tâche — les deux doivent être modifiés ensemble pour rester
compilables.

- [ ] **Step 1: Retirer `anonymous` des DTOs `Declarant*`**

Dans `DeclarantCreateRequest.java`, retirer :
```java
    private Boolean anonymous;
```

Dans `DeclarantResponse.java`, retirer :
```java
    private Boolean anonymous;
```

- [ ] **Step 2: Ajouter `anonymous` aux DTOs `Dossier*`**

Dans `DossierCreateRequest.java`, juste après `private QualiteDeclarant quality;` (ligne 24) :

```java
    private QualiteDeclarant quality;

    private Boolean anonymous;

    @NotNull(message = "Le mode de soumission est obligatoire")
```

Dans `DossierResponse.java`, juste après `private QualiteDeclarant quality;` (ligne 29) :

```java
    private QualiteDeclarant           quality;
    private Boolean                    anonymous;
    private SubmissionMode             submissionMode;
```

- [ ] **Step 3: Corriger `PdfExportService`**

Dans `addRecepisseBody` (méthode reçoit déjà `DossierResponse dossier`), remplacer :

```java
        } else if (Boolean.TRUE.equals(declarant.getAnonymous())) {
```

par :

```java
        } else if (Boolean.TRUE.equals(dossier.getAnonymous())) {
```

Dans `addDeclarantInfo` (méthode reçoit déjà `DossierResponse dossier`), remplacer :

```java
        if (Boolean.TRUE.equals(declarant.getAnonymous())) {
```

par :

```java
        if (Boolean.TRUE.equals(dossier.getAnonymous())) {
```

- [ ] **Step 4: Corriger `PdfExportServiceTest`**

Déplacer `.anonymous(...)` du builder `DeclarantResponse` vers le builder `DossierResponse`
englobant, dans les 3 tests existants. `exportRecepisse_producesNonEmptyPdfForNamedDeclarant` :

```java
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000042")
                .accessCode("ABCD1234")
                .type(TypeSaisine.DENONCIATION)
                .quality(QualiteDeclarant.TEMOIN)
                .anonymous(false)
                .submissionMode(SubmissionMode.IN_PERSON)
                .object("Marché public suspect")
                .receptionDate(Instant.now())
                .declarant(DeclarantResponse.builder()
                        .firstName("Awa")
                        .lastName("Ouedraogo")
                        .build())
                .build();
```

`exportRecepisse_producesNonEmptyPdfForAnonymousDeclarant` — ajoute aussi une assertion de
contenu (absente jusqu'ici, l'occasion de vérifier réellement le comportement plutôt que la
seule non-vacuité) :

```java
    @Test
    void exportRecepisse_producesNonEmptyPdfForAnonymousDeclarant() throws Exception {
        UUID dossierId = UUID.randomUUID();
        DossierResponse dossier = DossierResponse.builder()
                .number("ASCE-2026-000043")
                .accessCode("EFGH5678")
                .type(TypeSaisine.DENONCIATION)
                .quality(QualiteDeclarant.TEMOIN)
                .anonymous(true)
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Détournement présumé")
                .receptionDate(Instant.now())
                .declarant(DeclarantResponse.builder().build())
                .build();

        when(dossierService.findById(dossierId)).thenReturn(dossier);

        byte[] pdf = service.exportRecepisse(dossierId);

        assertThat(pdf).isNotEmpty();

        String text;
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            text = PdfTextExtractor.getTextFromPage(pdfDoc.getFirstPage());
        }
        assertThat(text).contains("Anonyme");
    }
```

`exportRecepisse_containsInstitutionalContactInformation` : mêmes changements que le premier
test (déplacer `.anonymous(false)` sur `DossierResponse`, le retirer de `DeclarantResponse`).

- [ ] **Step 5: Retirer `.anonymous(false)` des `DeclarantCreateRequest.builder()` restants**

Dans `DossierServiceImplTest.java`, méthode `buildRequest()` et test
`submit_nullsQualityForSignalement` : retirer `.anonymous(false)` des deux
`DeclarantCreateRequest.builder()` (champ supprimé à l'étape 1 de cette tâche).

- [ ] **Step 6: Vérifier la compilation et les tests**

```
mvn test -q -Dtest=PdfExportServiceTest,DossierServiceImplTest,DeclarantMapperTest
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DeclarantCreateRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DeclarantResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/DossierCreateRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/PdfExportService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/PdfExportServiceTest.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java
git commit -m "feat: move anonymous field from Declarant DTOs to Dossier DTOs"
```

---

### Task 3: Correctif fonctionnel `DossierServiceImpl` (le bug de fond)

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java`

**Interfaces:**
- Consumes: `DossierCreateRequest.getAnonymous()`, `DossierResponse.getAnonymous()` (Task 2).

- [ ] **Step 1: `submit()` — dériver l'anonymat depuis la soumission courante**

Remplacer (lignes ~165-167) :

```java
        TypeSaisine natureSaisine = natureSaisineResolver.resolve(
                declarant.getTypeDeclarant(), request.getQuality(),
                declarant.isAnonymous());
```

par :

```java
        boolean anonymousRequested = Boolean.TRUE.equals(request.getAnonymous());
        TypeSaisine natureSaisine = natureSaisineResolver.resolve(
                declarant.getTypeDeclarant(), request.getQuality(),
                anonymousRequested);
```

Aucun appel `dossier.setAnonymous(...)` à ajouter : `dossierMapper.toEntity(request)`
(ligne suivante) copie déjà `request.anonymous → dossier.anonymous` par correspondance de
nom de propriété, exactement comme pour `quality`.

- [ ] **Step 2: `maskSensitiveData()` — masquer selon le dossier, pas le déclarant réutilisé**

Remplacer (lignes ~988-999) :

```java
        if (response.getDeclarant() != null
                && Boolean.TRUE.equals(response.getDeclarant().getAnonymous())) {
            response.getDeclarant().setFirstName(null);
            response.getDeclarant().setLastName(null);
            response.getDeclarant().setEmail(null);
            response.getDeclarant().setPhoneNumber(null);
            response.getDeclarant().setAddress(null);
            response.getDeclarant().setCommune(null);
            response.getDeclarant().setProvince(null);
            response.getDeclarant().setCellulaire(null);
            response.getDeclarant().setLocalite(null);
        }
```

par :

```java
        if (response.getDeclarant() != null
                && Boolean.TRUE.equals(response.getAnonymous())) {
            response.getDeclarant().setFirstName(null);
            response.getDeclarant().setLastName(null);
            response.getDeclarant().setEmail(null);
            response.getDeclarant().setPhoneNumber(null);
            response.getDeclarant().setAddress(null);
            response.getDeclarant().setCommune(null);
            response.getDeclarant().setProvince(null);
            response.getDeclarant().setCellulaire(null);
            response.getDeclarant().setLocalite(null);
            response.getDeclarant().setDisplayName("Déclarant anonyme");
        }
```

(Le `setDisplayName(...)` comble une fuite identifiée pendant l'audit : sans lui, le
`displayName` généré par MapStruct — positionné avant tout masquage — resterait le nom réel
d'un déclarant réutilisé anonyme sur ce dossier.)

- [ ] **Step 3: Test de régression — l'anonymat suit la soumission, pas le déclarant réutilisé**

Ajouter dans `DossierServiceImplTest.java` :

```java
    @Test
    void submit_derivesAnonymityFromCurrentRequestAcrossReusedDeclarant() {
        // Le meme Declarant (meme instance = meme ligne reutilisee par
        // resolveDeclarant) est soumis deux fois avec des demandes d'anonymat
        // opposees. Avant ce correctif, natureSaisineResolver recevait
        // declarant.isAnonymous() — un etat de l'entite qui ne change pas entre
        // les deux appels ici — au lieu de l'anonymat de CHAQUE soumission.
        Declarant reusedDeclarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .build();
        UUID declarantId = UUID.randomUUID();

        DossierCreateRequest anonymousRequest = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Premier signalement")
                .quality(QualiteDeclarant.TEMOIN)
                .anonymous(true)
                .declarantId(declarantId)
                .build();
        when(declarantRepository.findById(declarantId)).thenReturn(Optional.of(reusedDeclarant));
        when(natureSaisineResolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, true))
                .thenReturn(TypeSaisine.DENONCIATION);
        when(dossierMapper.toEntity(anonymousRequest))
                .thenReturn(Dossier.builder().anonymous(true).build());
        when(accessCodeGenerator.generate()).thenReturn("ABCD1234");
        when(dossierRepository.existsByAccessCode("ABCD1234")).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submit(anonymousRequest, "127.0.0.1");

        verify(natureSaisineResolver).resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, true);

        DossierCreateRequest namedRequest = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Deuxième plainte, identité révélée")
                .quality(QualiteDeclarant.VICTIME)
                .anonymous(false)
                .declarantId(declarantId)
                .build();
        when(declarantRepository.findById(declarantId)).thenReturn(Optional.of(reusedDeclarant));
        when(natureSaisineResolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.VICTIME, false))
                .thenReturn(TypeSaisine.PLAINTE);
        when(dossierMapper.toEntity(namedRequest))
                .thenReturn(Dossier.builder().anonymous(false).build());

        service.submit(namedRequest, "127.0.0.1");

        verify(natureSaisineResolver).resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.VICTIME, false);
    }
```

- [ ] **Step 4: Test de régression — masquage piloté par le dossier**

```java
    @Test
    void findById_masksIdentityWhenDossierAnonymous() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        DeclarantResponse declarantResponse = DeclarantResponse.builder()
                .firstName("Awa")
                .lastName("Ouedraogo")
                .email("awa@example.com")
                .phoneNumber("70000000")
                .build();
        DossierResponse response = DossierResponse.builder()
                .anonymous(true)
                .declarant(declarantResponse)
                .build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(response);
        when(securityUtils.hasRole(anyString())).thenReturn(false);

        DossierResponse result = service.findById(dossierId);

        assertThat(result.getDeclarant().getFirstName()).isNull();
        assertThat(result.getDeclarant().getLastName()).isNull();
        assertThat(result.getDeclarant().getEmail()).isNull();
        assertThat(result.getDeclarant().getPhoneNumber()).isNull();
        assertThat(result.getDeclarant().getDisplayName()).isEqualTo("Déclarant anonyme");
    }
```

Ajouter l'import (vérifié absent du fichier à ce jour, qui n'importe que
`assertThatCode`/`assertThatThrownBy` de la même classe) :

```java
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 5: Lancer les tests**

```
mvn test -q -Dtest=DossierServiceImplTest
```

Expected: BUILD SUCCESS, y compris les 2 nouveaux tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java
git commit -m "fix: derive dossier anonymity from current submission, not reused declarant"
```

---

### Task 4: `NotificationDispatcherService` — masquage du nom dans les notifications

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/NotificationDispatcherService.java`
- Test: Create `src/test/java/gov/bf/ascelc/univers_audits/service/NotificationDispatcherServiceTest.java`

**Interfaces:**
- Consumes: `Dossier.getAnonymous()` (Task 1).

- [ ] **Step 1: Changer la signature de `resolveDisplayName`**

Remplacer :

```java
    private String resolveDisplayName(Declarant declarant) {
        if (declarant.isAnonymous()
                || Boolean.TRUE.equals(declarant.getProtectionRequested())) {
            return null;
        }
        return declarant.getDisplayName();
    }
```

par :

```java
    private String resolveDisplayName(Dossier dossier) {
        Declarant declarant = dossier.getDeclarant();
        if (Boolean.TRUE.equals(dossier.getAnonymous())
                || Boolean.TRUE.equals(declarant.getProtectionRequested())) {
            return null;
        }
        return declarant.getDisplayName();
    }
```

- [ ] **Step 2: Mettre à jour les 4 appelants**

Dans `dispatchAccessCode`, `dispatchStatusUpdate`, `dispatchComplementRequest`,
`dispatchTransferExternal` : remplacer chaque `resolveDisplayName(declarant)` par
`resolveDisplayName(dossier)` (le paramètre `dossier` est déjà disponible dans chacune de
ces 4 méthodes).

- [ ] **Step 3: Écrire le test**

Créer `src/test/java/gov/bf/ascelc/univers_audits/service/NotificationDispatcherServiceTest.java` :

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherServiceTest {

    @Mock private EmailService emailService;
    @Mock private SmsService   smsService;

    @InjectMocks
    private NotificationDispatcherService service;

    @Test
    void dispatchAccessCode_omitsNameWhenDossierAnonymousEvenForReusedNamedDeclarant() {
        // Le Declarant reutilise porte un vrai nom (typeDeclarant=CITIZEN, pas
        // ANONYMOUS) : avant ce correctif, resolveDisplayName lisait
        // declarant.isAnonymous() (toujours false ici) et aurait revele le nom
        // malgre la demande d'anonymat sur CE dossier.
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .email("awa@example.com")
                .build();
        Dossier dossier = Dossier.builder()
                .declarant(declarant)
                .accessCode("ABCD1234")
                .anonymous(true)
                .build();

        service.dispatchAccessCode(dossier);

        verify(emailService).sendAccessCode(
                eq("awa@example.com"), eq("ABCD1234"), isNull());
    }

    @Test
    void dispatchAccessCode_includesNameWhenDossierNotAnonymous() {
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .email("awa@example.com")
                .build();
        Dossier dossier = Dossier.builder()
                .declarant(declarant)
                .accessCode("ABCD1234")
                .anonymous(false)
                .build();

        service.dispatchAccessCode(dossier);

        verify(emailService).sendAccessCode(
                eq("awa@example.com"), eq("ABCD1234"), eq("Awa Ouedraogo"));
    }
}
```

Avant d'écrire ce fichier, lire la signature exacte de
`EmailService.sendAccessCode(String, String, String)` (import déjà utilisé dans
`NotificationDispatcherService.java`) pour confirmer l'ordre des paramètres attendu par
`verify(...)` — l'adapter si l'ordre réel diffère de email/code/nom.

- [ ] **Step 4: Lancer les tests**

```
mvn test -q -Dtest=NotificationDispatcherServiceTest
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/NotificationDispatcherService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/NotificationDispatcherServiceTest.java
git commit -m "fix: base notification name masking on dossier anonymity, not reused declarant"
```

---

### Task 5: Suite complète + mise à jour du backlog

**Files:**
- Modify: mémoire projet `project_asce_backlog_2026_07_30.md` (hors dépôt git, système de
  mémoire persistant)

- [ ] **Step 1: Lancer la suite complète**

```
mvn test -q
```

Expected: BUILD SUCCESS, 0 échec, 0 erreur.

- [ ] **Step 2: Revue finale rapide**

Vérifier par `grep -rn "declarant.getAnonymous\|declarant.isAnonymous\|Declarant.*anonymous"`
dans `src/main/java` qu'il ne reste aucune lecture d'anonymat basée sur `Declarant`/
`DeclarantResponse` en dehors de `Declarant.isAnonymous()` lui-même (désormais
`typeDeclarant == ANONYMOUS` uniquement) et de `DeclarantMapper.fillAndMask()` (code mort
assumé, cf. spec).

- [ ] **Step 3: Mettre à jour le backlog mémoire**

Marquer le point "Réutilisation du Declarant — anonymat" comme résolu dans
`project_asce_backlog_2026_07_30.md`, en notant explicitement que la dérive des AUTRES
champs du déclarant réutilisé (adresse, téléphone…) reste ouverte et non traitée par ce
chantier.
