# Déplacement de `anonymous` du Déclarant vers le Dossier — Design

Statut : approuvé par l'utilisateur le 2026-08-01. Périmètre : correctif ciblé sur le
mécanisme de réutilisation de `Declarant` entre dossiers indépendants (suite du chantier
NatureSaisine / cf. `2026-07-28-nature-saisine-derivation-design.md`).

## Contexte

`DossierServiceImpl.resolveDeclarant()` recherche un `Declarant` existant par email ou
téléphone et le **réutilise tel quel** s'il en trouve un, sans réconcilier ses champs avec la
soumission courante :

```java
if (email != null || phone != null) {
    var existing = declarantRepository.findByEmailOrPhone(email, phone);
    if (!existing.isEmpty()) return existing.get(0);
}
```

`Declarant.anonymous` (booléen) est aujourd'hui porté par la personne, pas par la saisine.
Deux scénarios de dysfonctionnement en découlent :

- **(a) Rejet confus** : un déclarant précédemment anonyme (`anonymous=true` stocké) soumet
  une nouvelle saisine en tant que Victime avec identité révélée
  (`request.anonymous=false`) ; `NatureSaisineResolver` lit encore l'ancien
  `declarant.isAnonymous()==true` et rejette la saisine comme incompatible avec la qualité
  Victime, alors que la demande courante ne l'est pas.
- **(b) Sous-protection** (sens dangereux) : un déclarant demande l'anonymat sur la saisine
  courante, mais l'enregistrement réutilisé porte `anonymous=false` (positionné lors d'une
  soumission antérieure sous son vrai nom) ; son identité n'est alors **pas masquée** malgré
  la demande d'anonymat courante.

Le même défaut existe, de façon indépendante, dans `PdfExportService` (récépissé et fiche
dossier) : `declarant.getAnonymous()` y est lu directement sur l'entité/DTO déclarant plutôt
que sur le dossier courant, alors que les deux méthodes concernées reçoivent déjà un
`DossierResponse` en paramètre.

Audit complémentaire : `DeclarantMapper.toResponse()` / `fillAndMask()` (masquage de
`anonymous`) sont du code mort en usage réel — le mapping imbriqué
`Dossier → DossierResponse.declarant` généré par MapStruct pour `DossierMapper` ne déclare
pas `uses = {DeclarantMapper.class}` ; il génère sa propre copie directe des champs
(confirmé en lisant `DossierMapperImpl.declarantToDeclarantResponse()` généré). Le masquage
réellement actif en production est celui de `DossierServiceImpl.maskSensitiveData()`.

## Décision

Déplacement complet de `anonymous` de `Declarant` vers `Dossier`, à l'identique du
déplacement déjà effectué pour `quality` (cf. spec NatureSaisine, migration `009`) :
l'anonymat demandé est une propriété de **cette saisine**, pas de la personne. Une même
personne peut être anonyme sur un dossier et identifiée sur un autre.

`Declarant.isAnonymous()` conserve un sens résiduel légitime, indépendant de toute
réutilisation : `typeDeclarant == ANONYMOUS`. Ce cas est structurellement sûr à laisser sur
`Declarant` — un déclarant de type `ANONYMOUS` n'a par construction ni email ni téléphone
renseignés (règle métier déjà en vigueur), donc `resolveDeclarant()` ne peut jamais le
retrouver/réutiliser par correspondance email/téléphone.

Hors périmètre : la dérive des autres champs du déclarant réutilisé (adresse, téléphone,
profession…) reste un point distinct, déjà noté au backlog, non traité ici.

## Changements

### 1. Migration Liquibase `015-declarant-anonymous-to-dossier.sql`

```sql
ALTER TABLE dossier ADD COLUMN IF NOT EXISTS anonymous BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN dossier.anonymous IS 'Demande d''anonymat du declarant pour CETTE saisine uniquement — ne doit pas etre deduite de declarant.anonymous (personne reutilisable entre dossiers)';

UPDATE dossier d SET anonymous = TRUE
    FROM declarant de
    WHERE d.declarant_id = de.id AND de.anonymous = TRUE;

ALTER TABLE declarant DROP COLUMN IF EXISTS anonymous;
```

### 2. Entités

- `Dossier` : ajoute `private Boolean anonymous` (`@Builder.Default = false`), à côté de
  `quality`.
- `Declarant` :
  - retire le champ `anonymous` et sa colonne.
  - `isAnonymous()` : `return TypeDeclarant.ANONYMOUS.equals(typeDeclarant);`
  - `getDisplayName()` : retire la branche `Boolean.TRUE.equals(anonymous)`. Ajoute en tête
    `if (TypeDeclarant.ANONYMOUS.equals(typeDeclarant)) return "Anonymous";` (préserve le
    comportement pour ce cas précis).
  - `onPrePersist()` : retire l'initialisation `anonymous == null` et la synchronisation
    `typeDeclarant==ANONYMOUS → anonymous=true`.

### 3. DTOs

- `DeclarantCreateRequest`, `DeclarantResponse` : suppression du champ `anonymous`.
- `DossierCreateRequest`, `DossierResponse` : ajout du champ `anonymous`, au même niveau que
  `quality`. Mappé automatiquement par nom de propriété (MapStruct), sans `@Mapping`
  explicite — identique au traitement actuel de `quality`.

### 4. `DeclarantMapper`

Retire le `@Mapping(target = "anonymous", source = "anonymous", defaultValue = "false")`
dans `toEntity()`. `fillAndMask()` n'a pas besoin de changement de logique : son test
`declarant.isAnonymous()` continue de compiler avec la nouvelle sémantique
(`typeDeclarant==ANONYMOUS`), et reste du code mort en usage réel (déjà le cas
aujourd'hui) — non traité ici, hors périmètre.

### 5. `DossierServiceImpl`

- `submit()` : `natureSaisineResolver.resolve(...)` lit désormais l'anonymat de la
  soumission courante plutôt que celui de l'éventuel déclarant réutilisé :

  ```java
  boolean anonymousRequested = Boolean.TRUE.equals(request.getAnonymous());
  TypeSaisine natureSaisine = natureSaisineResolver.resolve(
          declarant.getTypeDeclarant(), request.getQuality(), anonymousRequested);
  ```

  `dossier.setAnonymous(...)` n'a pas besoin d'appel explicite : `dossierMapper.toEntity(request)`
  copie déjà `request.anonymous → dossier.anonymous` par correspondance de nom (comme
  `quality`).

- `maskSensitiveData()` : la condition passe de
  `response.getDeclarant().getAnonymous()` à `response.getAnonymous()` (nouveau champ sur
  `DossierResponse`). Ajoute l'écrasement de `displayName`, sur le même modèle que le bloc
  de masquage "protection lanceur d'alerte" juste au-dessus (qui pose déjà un libellé fixe
  après avoir nullifié les champs) :

  ```java
  if (Boolean.TRUE.equals(response.getAnonymous()) && response.getDeclarant() != null) {
      response.getDeclarant().setFirstName(null);
      // ... (champs existants, inchangés)
      response.getDeclarant().setDisplayName("Déclarant anonyme");
  }
  ```

  Ce dernier point comble une fuite identifiée pendant l'audit : sans cet ajout, le
  `displayName` généré par MapStruct (`declarant.getDisplayName()`, positionné avant tout
  masquage) resterait le nom réel pour un déclarant réutilisé anonyme sur ce dossier — le
  nom/prénom seraient nullifiés mais `displayName` les révélerait quand même.

### 6. `NotificationDispatcherService`

`resolveDisplayName(Declarant declarant)` devient `resolveDisplayName(Dossier dossier)`,
lit `dossier.getAnonymous()` au lieu de `dossier.getDeclarant().isAnonymous()`. Les 4 points
d'appel (`dispatchAccessCode`, `dispatchStatusUpdate`, `dispatchComplementRequest`,
`dispatchTransferExternal`) passent déjà `dossier` en paramètre — changement d'appel trivial
(`resolveDisplayName(declarant)` → `resolveDisplayName(dossier)`).

### 7. `PdfExportService`

Deux lectures de `declarant.getAnonymous()` deviennent `dossier.getAnonymous()` — les deux
méthodes concernées (`addRecepisseBody`, `addDeclarantInfo`) reçoivent déjà un
`DossierResponse dossier` en paramètre, aucun changement de signature nécessaire :

- `addRecepisseBody` (ligne ~215, libellé "Déposant" du récépissé)
- `addDeclarantInfo` (ligne ~424, encart anonymat de la fiche dossier)

## Tests

- `NatureSaisineResolver` : aucun changement (signature déjà `resolve(typeDeclarant,
  quality, boolean anonymous)`, indépendante de l'entité).
- `DossierServiceImplTest` (ou équivalent) : cas de régression pour le scénario (a) —
  déclarant réutilisé avec `declarant`-level anonymat périmé, soumission courante
  `anonymous=false` + qualité Victime → acceptée (pas de rejet). Cas (b) — déclarant réutilisé
  non-anonyme historiquement, soumission courante `anonymous=true` + qualité Témoin →
  `DossierResponse.getAnonymous()==true` et identité masquée dans la réponse.
- `DeclarantMapperTest` : retirer les assertions sur `anonymous` (champ supprimé des DTOs).
- Migration `015` : vérifier le backfill sur un jeu de données où `declarant.anonymous=true`
  est partagé par plusieurs dossiers du même déclarant (chacun doit hériter `true`).
- `PdfExportServiceTest` (si existant) / vérification manuelle : récépissé d'un déclarant
  réutilisé anonyme sur le dossier courant mais non-anonyme historiquement → "Anonyme"
  affiché.

## Hors périmètre

- Réconciliation des autres champs du déclarant réutilisé (adresse, téléphone, profession…).
- `DeclarantMapper.toResponse()`/`fillAndMask()` restent du code mort en usage réel — pas de
  branchement (`uses = DeclarantMapper.class`) ajouté sur `DossierMapper` dans ce chantier.
