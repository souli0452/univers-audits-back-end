# Chaîne de possession + codage automatique + index des pièces (Attachment) — Design

Statut : approuvé par l'utilisateur le 2026-08-11. Sous-chantier 5/6 du Lot 4
(Conduite de l'investigation), décodage approuvé le 2026-08-05 (voir
`back-end/docs/reference/plan-de-travail-asce-lc.md`, §8.2 "Intégrité et
valeur probante"). Les sous-chantiers 1/6 (`DemandeDocuments`), 2/6
(`VisiteTerrain`/`PVConstat`), 3/6 (règles métier `Audition`) et 4/6
(correction PV + `RegistreAuditions`) sont livrés.

**Périmètre volontairement réduit du §8.2** — ce texte couvre 4 exigences,
seules les 3 premières sont traitées ici :

1. Chaîne de possession numérique (empreinte, horodatage, auteur du dépôt,
   personne remettante, origine, mode d'obtention, codage automatique) — **ce
   document**.
2. Index des pièces généré automatiquement — **ce document**.
3. Codage automatique du type `ACC-A-00001` — **ce document**.
4. Journal d'audit inaltérable sur toute lecture et toute écriture — **hors
   périmètre, reporté**. C'est un chantier d'instrumentation transversal
   (aucun endpoint `AttachmentController` n'appelle `AuditService`
   aujourd'hui, ni à l'upload, ni au téléchargement, ni à la suppression),
   pas un ajout de champs — mérite sa propre conception, potentiellement
   avec un patron générique (aspect/annotation) réutilisable au-delà des
   pièces jointes, plutôt qu'un ajout d'appels manuels ad hoc ici.
5. Distinction stricte original/copie de travail (l'original jamais
   modifié/annoté) et versionnage avec suppression logique uniquement (jamais
   de suppression physique) — **hors périmètre, reporté** avec le point 4.
   Même nature de chantier : changement de comportement transversal
   (`delete()`, futur `update()`) plutôt qu'ajout de champs.

## Objectif

Combler les 3 lacunes suivantes sur le module `Attachment` :

1. `Attachment.source` est câblé en dur sur `INITIAL_SUBMISSION` pour **tout**
   dépôt, quel que soit le contexte réel — un vrai bug, puisque le seul
   endpoint d'upload (`POST /api/v1/attachments/dossier/{dossierId}`) sert
   aussi bien la soumission citoyenne initiale que le dépôt d'une preuve
   collectée sur le terrain par un contrôleur d'État.
2. 4 champs de chaîne de possession exigés par le §8.2 sont absents :
   auteur du dépôt (`uploadedBy`), personne remettante, mode d'obtention
   (volontaire/réquisition), code auto-généré.
3. L'index des pièces (`GET /api/v1/attachments/dossier/{dossierId}`) est un
   résumé trop pauvre (`AttachmentSummary`) — ne porte ni la provenance, ni
   le code, ni le mode d'obtention, ni la description.

## Architecture

Aucune nouvelle entité, aucun nouveau workflow d'état. `Attachment` gagne 4
champs et un nouvel enum `ModeObtention`. Le code auto-généré suit
**exactement** le patron déjà en place pour le numéro de dossier
(`AccessCodeGenerator.generateDossierNumber` + boucle compte-et-réessaie
dans `DossierServiceImpl.generateUniqueNumber()`) : une méthode de
formatage pure ajoutée à `AccessCodeGenerator`, plus une boucle
`existsByCode` dans `AttachmentStorageService`. `source`/`modeObtention`/
`personneRemettante` deviennent des paramètres de requête optionnels sur
l'endpoint d'upload existant (défauts inchangés pour ne rien casser côté
appelants actuels) — pas de déduction automatique du contexte, jugée trop
fragile pour un champ probant. L'index enrichit `AttachmentSummary`
directement plutôt que d'ajouter un endpoint parallèle : ce endpoint de
liste est déjà, conceptuellement, l'index des pièces d'un dossier.

## Composants

### 1. Nouvel enum `ModeObtention`

```java
package gov.bf.ascelc.univers_audits.enums;

public enum ModeObtention {
    VOLONTAIRE,
    REQUISITION
}
```

### 2. `Attachment` — champs ajoutés

Après le bloc `validatedBy` :

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private Agent uploadedBy;

    @Column(name = "personne_remettante", length = 255)
    private String personneRemettante;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_obtention", nullable = false, length = 20)
    @Builder.Default
    private ModeObtention modeObtention = ModeObtention.VOLONTAIRE;

    @Column(name = "code", unique = true, length = 20)
    private String code;
```

- `uploadedBy` nullable : un dépôt citoyen anonyme via le portail public
  (`accessCode`, sans authentification Keycloak) n'a pas d'`Agent`.
- `personneRemettante` texte libre, nullable : la personne qui remet
  physiquement la pièce peut différer de celui qui la dépose (ex. un
  contrôleur en visite terrain reçoit un document d'un témoin) et n'est pas
  forcément un `Agent` du système (citoyen, société tierce...).
- `modeObtention` défaut `VOLONTAIRE` — la majorité des dépôts (soumission
  citoyenne, pièces jointes volontaires) le sont ; `REQUISITION` doit être
  explicitement renseigné.
- `code` nullable en base (les pièces déjà existantes n'en ont pas et ne
  sont pas rétroactivement codées — voir migration), mais toujours renseigné
  par le service à la création de toute nouvelle pièce.

### 3. Code auto-généré — `AccessCodeGenerator`

Ajouter à `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/AccessCodeGenerator.java` :

```java
    private static final java.util.Map<AttachmentSource, Character> ATTACHMENT_SOURCE_LETTERS =
            java.util.Map.of(
                    AttachmentSource.INITIAL_SUBMISSION, 'S',
                    AttachmentSource.FIELD_INVESTIGATION, 'T',
                    AttachmentSource.SOCIAL_MEDIA, 'R',
                    AttachmentSource.PRESS_MEDIA, 'P',
                    AttachmentSource.EXTERNAL_AUDIT, 'E',
                    AttachmentSource.OTHER, 'X');

    public String generateAttachmentCode(AttachmentSource source, long sequence) {
        char letter = ATTACHMENT_SOURCE_LETTERS.get(source);
        return String.format("ACC-%c-%05d", letter, sequence);
    }
```

(Ajouter `import gov.bf.ascelc.univers_audits.enums.AttachmentSource;` en tête de fichier.)

Format : `ACC-<lettre>-NNNNN` — lettre = provenance (`S`/`T`/`R`/`P`/`E`/`X`
selon `AttachmentSource`, une lettre par valeur de l'enum), `NNNNN` = compteur
séquentiel **global** (toutes provenances confondues) sur 5 chiffres, jamais
remis à zéro par année ni par lettre.

### 4. Génération du code — `AttachmentStorageService`

Nouvelle méthode privée, même patron compte-et-réessaie que
`DossierServiceImpl.generateUniqueNumber()` :

```java
    private String generateUniqueAttachmentCode(AttachmentSource source) {
        long sequence = attachmentRepository.count() + 1;
        String code;
        do {
            code = accessCodeGenerator.generateAttachmentCode(source, sequence);
            sequence++;
        } while (attachmentRepository.existsByCode(code));
        return code;
    }
```

Nécessite d'injecter `AccessCodeGenerator` dans `AttachmentStorageService`
(pas encore une dépendance de ce service).

### 5. `AttachmentRepository` — méthode ajoutée

```java
    boolean existsByCode(String code);
```

### 6. `AttachmentStorageService.upload()` — signature et corps modifiés

Nouvelle signature :

```java
    @Transactional
    public List<UploadedFile> upload(
            String dossierId, List<MultipartFile> files, String accessCode,
            AttachmentSource source, ModeObtention modeObtention, String personneRemettante) {
```

À l'intérieur de la boucle, remplacer :

```java
                .source(AttachmentSource.INITIAL_SUBMISSION)
```

par :

```java
                .source(source != null ? source : AttachmentSource.INITIAL_SUBMISSION)
                .modeObtention(modeObtention != null ? modeObtention : ModeObtention.VOLONTAIRE)
                .personneRemettante(personneRemettante)
                .uploadedBy(resolveUploaderOrNull())
                .code(generateUniqueAttachmentCode(
                        source != null ? source : AttachmentSource.INITIAL_SUBMISSION))
```

Nouvelle méthode privée (résolution sans lever d'exception pour les dépôts
anonymes — `AgentContextResolver.getCurrentAgent()` lève une
`BusinessException` si non authentifié, inutilisable ici tel quel) :

```java
    private Agent resolveUploaderOrNull() {
        return securityUtils.getCurrentKeycloakId()
                .flatMap(agentRepository::findByKeycloakId)
                .orElse(null);
    }
```

Nécessite d'injecter `SecurityUtils` et `AgentRepository` dans
`AttachmentStorageService` (ni l'un ni l'autre n'y sont présents
aujourd'hui).

### 7. `AttachmentSummary` — enrichi

Remplacer :

```java
    public record AttachmentSummary(String id, String originalName, String mimeType,
                                    Long fileSizeBytes, String uploadedAt,
                                    boolean isAudio, String status) {}
```

par :

```java
    public record AttachmentSummary(String id, String originalName, String mimeType,
                                    Long fileSizeBytes, String uploadedAt,
                                    boolean isAudio, String status,
                                    String description, String source,
                                    String modeObtention, String code) {}
```

`listByDossier()` peuple les 4 nouveaux champs depuis l'entité
(`att.getDescription()`, `att.getSource().name()`,
`att.getModeObtention().name()`, `att.getCode()` — `source`/`modeObtention`
ne sont jamais `null` sur une pièce, `code` peut l'être pour une pièce
antérieure à cette migration, à traiter par un null-check simple).

### 8. `AttachmentController.uploadFiles()` — paramètres ajoutés

```java
    @PostMapping("/dossier/{dossierId}")
    public ResponseEntity<?> uploadFiles(
            @PathVariable String dossierId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "accessCode", required = false) String accessCode,
            @RequestParam(value = "source", required = false) AttachmentSource source,
            @RequestParam(value = "modeObtention", required = false) ModeObtention modeObtention,
            @RequestParam(value = "personneRemettante", required = false) String personneRemettante) {

        List<AttachmentStorageService.UploadedFile> saved =
                attachmentStorageService.upload(
                        dossierId, files, accessCode, source, modeObtention, personneRemettante);
        ...
```

Spring lie nativement un `@RequestParam` de type enum depuis une chaîne
(nom de la constante) — pas de convertisseur custom nécessaire, même
mécanisme implicite déjà utilisé ailleurs dans ce dépôt pour les enums en
paramètre de requête.

**Coordination frontend** (à l'instar du paramètre `accessCode` déjà noté
dans le backlog) : ces 3 paramètres sont optionnels, aucun appelant existant
n'est cassé, mais le frontend doit les envoyer quand le contexte réel est
connu (ex. un écran de dépôt de preuve en cours de visite terrain devrait
envoyer `source=FIELD_INVESTIGATION`) pour que la chaîne de possession soit
exacte plutôt que par défaut.

## Migration

`029-add-attachment-chain-of-custody.sql` — aucune migration trackée dans ce
dépôt ne crée la table `attachment` (schéma de base antérieur au suivi
Liquibase de ce dépôt, confirmé par recherche exhaustive dans
`db/changelog/`) ; cette migration s'ajoute simplement à la table existante,
comme toute migration normale sur un schéma déjà en place :

```sql
--liquibase formatted sql
--changeset dev:029-add-attachment-chain-of-custody

ALTER TABLE attachment ADD COLUMN uploaded_by_id UUID REFERENCES agent(id);
ALTER TABLE attachment ADD COLUMN personne_remettante VARCHAR(255);
ALTER TABLE attachment ADD COLUMN mode_obtention VARCHAR(20) NOT NULL DEFAULT 'VOLONTAIRE';
ALTER TABLE attachment ADD COLUMN code VARCHAR(20);

CREATE UNIQUE INDEX idx_attachment_code ON attachment (code) WHERE code IS NOT NULL;

COMMENT ON COLUMN attachment.uploaded_by_id IS 'Auteur du depot (Agent) - null pour un depot citoyen anonyme via accessCode';
COMMENT ON COLUMN attachment.personne_remettante IS 'Personne ayant physiquement remis la piece, distincte de l auteur du depot';
COMMENT ON COLUMN attachment.mode_obtention IS 'Volontaire ou requisition - plan de travail S8.2';
COMMENT ON COLUMN attachment.code IS 'Code auto-genere ACC-<lettre-provenance>-NNNNN, null pour les pieces anterieures a cette migration';
```

Contrainte d'unicité en index partiel (`WHERE code IS NOT NULL`) plutôt
qu'une contrainte `UNIQUE` de colonne classique, pour ne pas exiger un
backfill des pièces existantes (qui resteraient toutes `NULL` sinon, ce
qu'une contrainte `UNIQUE` standard autorise déjà pour plusieurs `NULL` en
PostgreSQL — l'index partiel documente l'intention explicitement plutôt que
de compter sur ce comportement implicite).

## Gestion des erreurs

Aucune nouvelle exception. La boucle de génération de code réutilise le même
principe compte-et-réessaie déjà accepté pour les numéros de dossier — la
fenêtre de course théorique sous upload concurrent existe déjà pour ce
patron dans ce dépôt, pas une régression introduite ici.

## Tests

- `AttachmentStorageServiceTest` (nouveau si absent, sinon étendu) :
  - `upload()` sans `source`/`modeObtention` fournis → défauts
    `INITIAL_SUBMISSION`/`VOLONTAIRE` préservés (non-régression du
    comportement actuel).
  - `upload()` avec `source=FIELD_INVESTIGATION` explicite → la pièce
    sauvegardée porte bien cette valeur, pas `INITIAL_SUBMISSION`.
  - `upload()` génère un `code` non-null au format `ACC-<lettre>-NNNNN`
    correspondant à la source.
  - `upload()` par un agent authentifié → `uploadedBy` renseigné.
  - `upload()` anonyme (pas de contexte Keycloak) → `uploadedBy` reste
    `null`, pas d'exception levée.
  - `listByDossier()` peuple bien les 4 nouveaux champs de
    `AttachmentSummary`.

## Hors périmètre

- Journal d'audit lecture/écriture sur `Attachment` (§8.2 point 1) — chantier
  séparé, potentiellement transversal.
- Distinction original/copie de travail, versionnage + suppression logique
  (§8.2 points 4-5 restants) — chantier séparé, changement de comportement
  transversal plutôt qu'ajout de champs.
- `ChainePossession` comme registre des transferts successifs (distinct des
  champs statiques traités ici) — décidé hors périmètre par l'utilisateur,
  aucune base textuelle précise pour ce workflow dans ce dépôt.
- Rétro-codage des pièces jointes déjà existantes (`code` reste `NULL` pour
  elles) — pas demandé, et un rétro-codage poserait la question de l'ordre
  d'attribution rétroactif sans base légale claire.
- `Attachment.validate()`/`.reject()` restent du code mort (confirmé par
  l'exploration — aucun appelant nulle part) — non traité ici, hors
  périmètre de ce sous-chantier.
