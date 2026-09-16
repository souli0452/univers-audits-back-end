# Référentiel des indices et typologies de fraude — Design

**Lot 9 — Veille et capitalisation, sous-chantier 3/3 (dernier sous-chantier du Lot 9).**

## Contexte et texte source

Plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`) :

- Ligne 88 (Lot 0 — Socle, référentiels à charger) : *« Typologies d'indices de
  fraude (marchés publics, postes sensibles, régies, subventions, gestion
  budgétaire, déclarations de patrimoine) »*.
- Ligne 382 (Lot 9) : *« Référentiel des indices et typologies exploitable
  comme aide à l'enquête et comme grille de cartographie des risques. »*

Aucune entité `Indice`/`Typologie` n'existe dans le code à ce jour (recherche
exhaustive négative). Ce sous-chantier crée ce référentiel de zéro.

## Arbitrages déjà tranchés (2026-08-25, Lot 9)

- Référentiel **administrable, consultable seul**, **sans rattachement
  structurel** à `Dossier`/`Investigation` dans un premier temps. Consé­quence
  directe : ce sous-chantier ne charge jamais `Dossier`/`Investigation`, donc
  **`DossierAccessGuard.checkReadAccess` et le masquage de confidentialité
  (`canSeeConfidential()`) ne s'appliquent pas ici** — voir
  `feedback_controle_acces_dossier_complet` (mémoire) : la règle des deux
  contrôles ne s'active que lorsqu'un service charge l'un de ces deux types ;
  ce n'est jamais le cas dans ce sous-chantier.

## Arbitrages tranchés pendant ce brainstorming

1. **Catégorie en texte libre administrable** (pas d'enum fermé des 6 valeurs
   citées) — cohérent avec le champ `categorie` déjà présent sur
   `PointChecklistDossierTravail`. Un administrateur peut créer nouveau
   domaine sans migration de code.
2. **Une seule entité plate `IndiceFraude`** portant un champ `categorie` —
   pas de hiérarchie à 2 niveaux (`Typologie` parent + `Indice` enfant). La
   « typologie » de chaque indice est simplement sa `categorie`. YAGNI : une
   typologie sans aucun indice n'a pas d'utilité métier identifiée à stocker
   séparément.
3. **Pas de champ de niveau de risque** (faible/moyen/élevé). Rien dans le
   plan de travail ne spécifie de méthodologie de notation (pondération,
   probabilité × impact). En inventer une serait arbitraire. Le rôle de
   « grille de cartographie des risques » est rempli par le regroupement en
   `categorie`, rendu exploitable via un **filtre par catégorie sur la
   lecture** (voir Composants ci-dessous) plutôt que par un score inventé.

## Composants

### Entité `IndiceFraude` (`extends AuditEntity`)

Patron structurel copié de `TypeInfraction`/`PointChecklistDossierTravail` —
mêmes conventions (`@Getter @Setter @Entity @SuperBuilder @NoArgsConstructor
@AllArgsConstructor`, table + index unique sur `code`).

| Champ | Type | Contrainte |
|---|---|---|
| `code` | `String` | NOT NULL, UNIQUE, max 50 |
| `libelle` | `String` | NOT NULL, max 300 |
| `categorie` | `String` | max 100, nullable (texte libre) |
| `description` | `String` (colonne `TEXT`) | nullable — détail de l'indice, ce qui en fait une aide à l'enquête concrète (pas seulement un libellé court) |
| `actif` | `Boolean` | NOT NULL, défaut `true` |
| `ordre` | `Integer` | défaut `0` |

Table `indice_fraude`, index unique `idx_indice_fraude_code` sur `code`.

### Migration

`db/changelog/migrations/042-create-indice-fraude.sql` — nouveau changelog
Liquibase suivant le patron des migrations `040`/`041` déjà livrées dans ce
Lot (colonnes `AuditEntity` : `id`, `created_at`, `created_by`, `updated_at`,
`updated_by`, plus les colonnes ci-dessus).

### Repository `IndiceFraudeRepository`

- `findByCode(String code): Optional<IndiceFraude>`
- `existsByCode(String code): boolean`
- `findByActifTrueOrderByOrdreAsc(): List<IndiceFraude>`
- `findByActifTrueAndCategorieOrderByOrdreAsc(String categorie): List<IndiceFraude>`

### Service `IndiceFraudeService` / `IndiceFraudeServiceImpl`

`@Transactional(readOnly = true)` au niveau classe, `@Transactional` en
override sur les méthodes d'écriture — convention établie du dépôt.

- `findAllActifs(String categorie)` — si `categorie` est non nul/non vide,
  délègue à `findByActifTrueAndCategorieOrderByOrdreAsc` ; sinon délègue à
  `findByActifTrueOrderByOrdreAsc`.
- `findAll()` — tous les indices, actifs ou non (vue admin).
- `create(IndiceFraudeRequest request)` — vérifie `existsByCode` →
  `ConflictException` si doublon (patron `TypeInfractionServiceImpl.create`),
  sinon construit et sauvegarde.
- `update(String code, IndiceFraudeRequest request)` — `findByCode` →
  `ResourceNotFoundException` si absent, sinon met à jour tous les champs
  modifiables et sauvegarde.

### DTO `IndiceFraudeRequest`

```java
@NotBlank @Size(max = 50)  private String code;
@NotBlank @Size(max = 300) private String libelle;
@Size(max = 100)            private String categorie;
private String description;
@NotNull                    private Boolean actif;
private Integer ordre;
```

### Controller `IndiceFraudeController`

Route `/api/v1/indices-fraude` (patron `/api/v1/types-infraction` —
mapping direct, pas de constante `ApiUrls`, cohérent avec le seul autre
référentiel de ce type dans le dépôt).

| Méthode | Route | Rôles | Comportement |
|---|---|---|---|
| `GET` | `/api/v1/indices-fraude?categorie={optionnel}` | `isAuthenticated()` | Liste les indices actifs ; filtre par `categorie` si le paramètre est fourni |
| `GET` | `/api/v1/indices-fraude/admin` | `hasAnyRole('ADMIN_DDIC','CGEA')` | Liste tous les indices, actifs ou non |
| `POST` | `/api/v1/indices-fraude` | `hasAnyRole('ADMIN_DDIC','CGEA')` | Création |
| `PUT` | `/api/v1/indices-fraude/{code}` | `hasAnyRole('ADMIN_DDIC','CGEA')` | Mise à jour |

## Tests

Suite `IndiceFraudeServiceTest` (Mockito) sur le modèle de
`TypeInfractionServiceImpl`/tests existants :
- `findAllActifs` sans filtre → délègue à `findByActifTrueOrderByOrdreAsc`.
- `findAllActifs` avec `categorie` renseignée → délègue à
  `findByActifTrueAndCategorieOrderByOrdreAsc`.
- `create` : cas succès, cas doublon de code → `ConflictException`.
- `update` : cas succès, cas code introuvable → `ResourceNotFoundException`.

Pas de test d'accès (`DossierAccessGuard`/confidentialité) : ce référentiel
ne charge jamais `Dossier`/`Investigation`.

## Hors périmètre (explicitement)

- Rattachement structurel à `Dossier`/`Investigation` (arbitrage du
  2026-08-25 : pas dans ce premier temps).
- Niveau de risque / score de criticité (arbitrage ci-dessus : pas de
  méthodologie définie dans le plan de travail).
- Hiérarchie `Typologie` parent / `Indice` enfant (arbitrage ci-dessus :
  YAGNI, une seule entité plate avec `categorie`).
