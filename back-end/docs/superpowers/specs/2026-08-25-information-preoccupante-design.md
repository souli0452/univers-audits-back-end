# Information préoccupante — Design

## Statut

**Lot 9 — Veille et capitalisation**, redécoupé en 3 sous-chantiers après recherche
exhaustive de l'existant (aucun des 4 sous-composants du texte source n'existe, contrairement
au Lot 8 où tout existait déjà) :

1. **Information préoccupante** ← ce document (en cours)
2. Fiche RETEX par mission + base de leçons à partager — à venir
3. Référentiel des indices et typologies — à venir

## Contexte

Texte source du Lot 9 (`docs/reference/plan-de-travail-asce-lc.md`, ligne 382) : « Module
d'information préoccupante alimenté par la DCP, avec rattachement a posteriori à un ou
plusieurs dossiers et déclenchement possible d'auto-saisine. »

Recherche exhaustive de l'existant avant conception (même prudence qu'aux Lots 6/7/8) :

- **Aucune des 4 entités du Lot 9 n'existe**, ni sous ce nom ni orpheline — confirmé par
  recherche sur plusieurs variantes FR/EN dans tout `src/main/java`.
- **`AUTO_SAISINE` existe déjà** (`TypeSaisine.AUTO_SAISINE`) mais uniquement via le flux de
  soumission de dossier classique (`DossierServiceImpl.submit()`, `Declarant` de type
  `TypeDeclarant.ASCE_SELF_REFERRAL`, résolu par `NatureSaisineResolver`) — aucun mécanisme
  de déclenchement *depuis* une information préoccupante n'existe.
- **`Dossier.autoReferralSource`** (enum `AutoReferralSource` : `WRITTEN_PRESS, TELEVISION,
  RADIO, SOCIAL_MEDIA, AUDIT_REPORT, INSPECTION_REPORT, INTERNAL_TIP, PARTNER_INSTITUTION,
  PROSECUTOR_REFERRAL, OTHER`) documente déjà l'origine d'une auto-saisine — recouvre
  exactement la notion de « source de veille » qu'une information préoccupante doit porter.
  Décision : **réutiliser cet enum tel quel** pour le champ `source` de
  `InformationPreoccupante`, plutôt que d'en créer un nouveau redondant.
- **`SeanceCtadpDossier`** est le patron de liaison N-N à réutiliser pour le « rattachement
  a posteriori à un ou plusieurs dossiers » : entité de jonction dédiée (pas un `@JoinTable`
  implicite), deux `@ManyToOne`, index unique composite, attributs métier portés par la
  liaison elle-même (`recommandation`, `commentaire`), étend `AuditEntity` (qui fournit déjà
  `createdAt`/`createdById` — pas de champs `linkedAt`/`linkedBy` redondants à ajouter).
- **« DCP »** (sigle du texte source, veille presse) n'existe nulle part dans le code — ni
  rôle, ni département seedé. **Décision utilisateur** : pas de nouveau rôle/département DCP
  dans ce sous-chantier ; réutilisation des rôles internes existants (`AGENT_BRPD`,
  `ADMIN_DDIC`), cohérent avec l'accès déjà accordé à l'enregistrement de dossiers.
- **`AgentContextResolver`** (utilitaire déjà extrait pour dédupliquer la résolution de
  l'agent Keycloak courant, précédemment dupliquée dans `DossierServiceImpl`/
  `InvestigationServiceImpl`/`ObservationServiceImpl`) — disponible si besoin, mais
  **non nécessaire ici** : `AuditEntity` fournit déjà `createdById`/`createdAt` via
  l'auditing Spring Data (`@CreatedBy`/`@CreatedDate`), donc « qui a créé »/« qui a rattaché »
  sont déjà couverts sans champ dédié, exactement comme `SeanceCtadpDossier` ne les
  duplique pas non plus.

## Objectif

Ajouter la capacité de journaliser une information préoccupante (signal de veille), de la
rattacher a posteriori à un ou plusieurs dossiers existants, et de déclencher depuis elle une
auto-saisine (nouveau dossier), en réutilisant intégralement `DossierService.submit()` déjà
existant — aucune duplication de logique de création de dossier.

## Hors périmètre

- **Rôle/département DCP dédié** — décision utilisateur, hors périmètre de ce sous-chantier.
- **Rattachement du référentiel des indices/typologies aux informations préoccupantes** —
  ce référentiel n'existe pas encore (sous-chantier 3/3 du Lot 9), et la décision utilisateur
  pour ce référentiel exclut déjà tout rattachement structurel à d'autres entités dans un
  premier temps.
- **Notification automatique** lors d'un rattachement ou d'un déclenchement d'auto-saisine —
  non demandé par le texte source, YAGNI.
- **Import automatisé depuis une source de veille externe** (flux RSS, API presse, etc.) —
  le texte source dit « alimenté par la DCP », interprété comme une saisie manuelle par un
  agent habilité (cohérent avec l'absence de toute infrastructure d'ingestion externe dans ce
  dépôt), pas une intégration technique avec un outil de veille tiers.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Champ `source` | Réutilise l'enum `AutoReferralSource` existant | Sémantiquement identique, évite un enum redondant (voir Contexte) |
| Rattachement a posteriori | Nouvelle entité de jonction `InformationPreoccupanteDossier`, patron `SeanceCtadpDossier` | Patron déjà établi et éprouvé dans ce dépôt pour ce besoin exact |
| Déclenchement d'auto-saisine | Construit un `DossierCreateRequest` (déclarant `ASCE_SELF_REFERRAL`, `autoReferralSource` recopié) et appelle `DossierService.submit()` existant | Zéro duplication de la logique de création/validation de dossier déjà en place |
| `submissionMode` du dossier auto-saisine créé | `SubmissionMode.AUDIT_REPORT` | Valeur existante la plus proche sémantiquement d'un dossier généré en interne par ASCE-LC (ni un canal citoyen, ni un dépôt physique) ; documenté ici explicitement plutôt que choisi arbitrairement dans le code |
| Traçabilité création/rattachement | Champs `createdAt`/`createdById` déjà fournis par `AuditEntity` | Pas de champs `createdBy Agent`/`linkedBy Agent` redondants — même choix que `SeanceCtadpDossier` |
| Accès | `hasAnyRole('AGENT_BRPD','ADMIN_DDIC')` | Décision utilisateur — rôles internes existants, pas de nouveau rôle DCP |
| Statuts | Nouvel enum `StatutInformationPreoccupante` : `NOUVELLE`, `RATTACHEE`, `AUTO_SAISINE_DECLENCHEE`, `CLASSEE_SANS_SUITE` | Cycle de vie explicite demandé implicitement par le texte source (rattachement OU auto-saisine OU classement) |

## Composants

### 1. Nouvel enum `StatutInformationPreoccupante`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/enums/StatutInformationPreoccupante.java`

```java
package gov.bf.ascelc.univers_audits.enums;

public enum StatutInformationPreoccupante {
    NOUVELLE,
    RATTACHEE,
    AUTO_SAISINE_DECLENCHEE,
    CLASSEE_SANS_SUITE
}
```

### 2. Nouvelle entité `InformationPreoccupante`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/InformationPreoccupante.java`

Étend `AuditEntity` (patron `MissionSuivi`/`SeanceCtadpDossier`) :

```java
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "information_preoccupante")
public class InformationPreoccupante extends AuditEntity {

    @Column(name = "objet", nullable = false, length = 500)
    private String objet;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private AutoReferralSource source;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Column(name = "date_reception", nullable = false)
    private Instant dateReception;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutInformationPreoccupante statut;
}
```

### 3. Nouvelle entité de jonction `InformationPreoccupanteDossier`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/InformationPreoccupanteDossier.java`

Patron `SeanceCtadpDossier` exact :

```java
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "information_preoccupante_dossier", indexes = {
        @Index(name = "idx_ip_dossier_information",
                columnList = "information_preoccupante_id"),
        @Index(name = "idx_ip_dossier_dossier",
                columnList = "dossier_id"),
        @Index(name = "idx_ip_dossier_unique",
                columnList = "information_preoccupante_id, dossier_id", unique = true)
})
public class InformationPreoccupanteDossier extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "information_preoccupante_id", nullable = false)
    private InformationPreoccupante informationPreoccupante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Column(name = "commentaire", length = 2000)
    private String commentaire;
}
```

### 4. Migration Liquibase

Fichier : `src/main/resources/db/changelog/migrations/040-create-information-preoccupante.sql`
(prochain numéro disponible, `039-create-constitution-partie-civile.sql` étant le dernier).
Ramassée automatiquement par `includeAll` du changelog maître — aucune inscription manuelle.

```sql
--liquibase formatted sql
--changeset dev:040-create-information-preoccupante

CREATE TABLE information_preoccupante (
    id                UUID         PRIMARY KEY,
    objet             VARCHAR(500) NOT NULL,
    description       TEXT         NOT NULL,
    source            VARCHAR(40)  NOT NULL,
    source_reference  VARCHAR(500),
    date_reception    TIMESTAMP    NOT NULL,
    statut            VARCHAR(30)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE TABLE information_preoccupante_dossier (
    id                            UUID         PRIMARY KEY,
    information_preoccupante_id  UUID         NOT NULL REFERENCES information_preoccupante(id),
    dossier_id                   UUID         NOT NULL REFERENCES dossier(id),
    commentaire                  VARCHAR(2000),
    version                      BIGINT       NOT NULL DEFAULT 0,
    created_at                   TIMESTAMP    NOT NULL,
    updated_at                   TIMESTAMP,
    created_by_id                VARCHAR(100),
    updated_by_id                VARCHAR(100)
);

CREATE UNIQUE INDEX idx_ip_dossier_unique
    ON information_preoccupante_dossier (information_preoccupante_id, dossier_id);

CREATE INDEX idx_ip_dossier_information
    ON information_preoccupante_dossier (information_preoccupante_id);

CREATE INDEX idx_ip_dossier_dossier
    ON information_preoccupante_dossier (dossier_id);

COMMENT ON TABLE information_preoccupante IS 'Signaux de veille (Lot 9 sous-chantier 1/3) - rattachables a posteriori a un ou plusieurs dossiers, ou pouvant declencher une auto-saisine';
COMMENT ON TABLE information_preoccupante_dossier IS 'Rattachement N-N information preoccupante / dossier, patron SeanceCtadpDossier';
```

### 5. Repositories

Fichiers :
- `src/main/java/gov/bf/ascelc/univers_audits/repository/InformationPreoccupanteRepository.java`
  — `extends JpaRepository<InformationPreoccupante, UUID>`, méthode
  `Page<InformationPreoccupante> findAllByOrderByDateReceptionDesc(Pageable pageable)`.
- `src/main/java/gov/bf/ascelc/univers_audits/repository/InformationPreoccupanteDossierRepository.java`
  — `extends JpaRepository<InformationPreoccupanteDossier, UUID>`, méthode
  `List<InformationPreoccupanteDossier> findByInformationPreoccupanteId(UUID informationPreoccupanteId)`.

### 6. DTOs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/InformationPreoccupanteCreateRequest.java`

```java
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InformationPreoccupanteCreateRequest {

    @NotBlank(message = "L'objet est obligatoire")
    @Size(max = 500, message = "L'objet ne doit pas dépasser 500 caractères")
    private String objet;

    @NotBlank(message = "La description est obligatoire")
    @Size(max = 10000, message = "La description ne doit pas dépasser 10000 caractères")
    private String description;

    @NotNull(message = "La source est obligatoire")
    private AutoReferralSource source;

    @Size(max = 500, message = "La référence source ne doit pas dépasser 500 caractères")
    private String sourceReference;

    @NotNull(message = "La date de réception est obligatoire")
    private Instant dateReception;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RattacherDossierRequest.java`

```java
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RattacherDossierRequest {

    @Size(max = 2000, message = "Le commentaire ne doit pas dépasser 2000 caractères")
    private String commentaire;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InformationPreoccupanteResponse.java`

```java
@Data @Builder
public class InformationPreoccupanteResponse {
    private UUID id;
    private String objet;
    private String description;
    private AutoReferralSource source;
    private String sourceReference;
    private Instant dateReception;
    private StatutInformationPreoccupante statut;
    private Instant createdAt;
    private List<DossierRattacheResponse> dossiersRattaches;

    @Data @Builder
    public static class DossierRattacheResponse {
        private UUID dossierId;
        private String dossierNumber;
        private String commentaire;
        private Instant linkedAt;
    }
}
```

### 7. Service

Fichiers :
- `src/main/java/gov/bf/ascelc/univers_audits/service/InformationPreoccupanteService.java` (interface)
- `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InformationPreoccupanteServiceImpl.java`

```java
public interface InformationPreoccupanteService {

    InformationPreoccupanteResponse create(InformationPreoccupanteCreateRequest request);

    InformationPreoccupanteResponse findById(UUID id);

    Page<InformationPreoccupanteResponse> findAll(Pageable pageable);

    InformationPreoccupanteResponse rattacherDossier(
            UUID informationPreoccupanteId, UUID dossierId, RattacherDossierRequest request);

    DossierResponse declencherAutoSaisine(UUID informationPreoccupanteId, String ipAddress);

    InformationPreoccupanteResponse classerSansSuite(UUID informationPreoccupanteId);
}
```

**`rattacherDossier`** : charge l'information préoccupante (404 si absente) et le dossier via
`DossierRepository.findById` (404 si absent) ; refuse (`BusinessException`) si le statut est
`CLASSEE_SANS_SUITE` ; crée l'`InformationPreoccupanteDossier` ; passe le statut à
`RATTACHEE` si le statut courant est `NOUVELLE` (ne rétrograde jamais un statut
`AUTO_SAISINE_DECLENCHEE` déjà atteint).

**`declencherAutoSaisine`** : charge l'information préoccupante (404 si absente) ; refuse
(`BusinessException`) si le statut est déjà `AUTO_SAISINE_DECLENCHEE` ou
`CLASSEE_SANS_SUITE` ; construit :

```java
DossierCreateRequest.builder()
        .submissionMode(SubmissionMode.AUDIT_REPORT)
        .autoReferralSource(info.getSource())
        .object(info.getObjet())
        .description(info.getDescription())
        .declarantData(DeclarantCreateRequest.builder()
                .typeDeclarant(TypeDeclarant.ASCE_SELF_REFERRAL)
                .build())
        .build();
```

appelle `dossierService.submit(request, ipAddress)` ; crée l'`InformationPreoccupanteDossier`
reliant l'information préoccupante au dossier nouvellement créé ; passe le statut à
`AUTO_SAISINE_DECLENCHEE` ; retourne le `DossierResponse` du dossier créé.

**`classerSansSuite`** : refuse si le statut est déjà `AUTO_SAISINE_DECLENCHEE` (un dossier a
déjà été créé, classer « sans suite » n'a plus de sens) ; sinon passe le statut à
`CLASSEE_SANS_SUITE`.

### 8. Contrôleur

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/InformationPreoccupanteController.java`

Nouvelle constante `ApiUrls.INFORMATIONS_PREOCCUPANTES = API + VERSION +
"/informations-preoccupantes"` (patron `ApiUrls.DOSSIERS` existant).

| Méthode | Route | Rôles |
|---|---|---|
| POST | `` | `AGENT_BRPD`, `ADMIN_DDIC` |
| GET | `` (paginé) | `AGENT_BRPD`, `ADMIN_DDIC` |
| GET | `/{id}` | `AGENT_BRPD`, `ADMIN_DDIC` |
| POST | `/{id}/rattacher-dossier/{dossierId}` | `AGENT_BRPD`, `ADMIN_DDIC` |
| POST | `/{id}/declencher-auto-saisine` | `AGENT_BRPD`, `ADMIN_DDIC` |
| POST | `/{id}/classer-sans-suite` | `AGENT_BRPD`, `ADMIN_DDIC` |

`declencherAutoSaisine` reprend la résolution d'IP cliente déjà dupliquée dans
`DossierController`/`RateLimitFilter` (méthode privée locale, patron déjà établi — pas
d'utilitaire partagé créé pour 3 occurrences).

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| Information préoccupante introuvable | `ResourceNotFoundException` (404) |
| Dossier introuvable lors du rattachement | `ResourceNotFoundException` (404) |
| Rattachement sur une information `CLASSEE_SANS_SUITE` | `BusinessException` (400) |
| Déclenchement d'auto-saisine sur une information déjà `AUTO_SAISINE_DECLENCHEE` | `BusinessException` (400) — évite un doublon de dossier |
| Déclenchement d'auto-saisine sur une information `CLASSEE_SANS_SUITE` | `BusinessException` (400) |
| Classement sans suite d'une information déjà `AUTO_SAISINE_DECLENCHEE` | `BusinessException` (400) |
| Rattachement du même dossier deux fois à la même information | Contrainte unique `idx_ip_dossier_unique` — erreur d'intégrité, à traduire en `BusinessException` explicite avant l'insertion (vérifier l'absence via `existsByInformationPreoccupanteIdAndDossierId` plutôt que de laisser remonter l'exception SQL brute) |

## Tests

- **`InformationPreoccupanteServiceImplTest`** (nouveau, patron Mockito standard du dépôt) :
  - `create_creeUneInformationPreoccupanteAvecStatutNouvelle`
  - `rattacherDossier_passeLeStatutARattacheeSiNouvelle`
  - `rattacherDossier_neRetrogradePasUnStatutAutoSaisineDeclenchee`
  - `rattacherDossier_refuseSiClasseeSansSuite`
  - `rattacherDossier_refuseSiDejaRattacheAuMemeDossier`
  - `declencherAutoSaisine_appelleDossierServiceSubmitAvecLesBonsChamps`
  - `declencherAutoSaisine_passeLeStatutAAutoSaisineDeclenchee`
  - `declencherAutoSaisine_refuseSiDejaDeclenchee`
  - `declencherAutoSaisine_refuseSiClasseeSansSuite`
  - `classerSansSuite_refuseSiAutoSaisineDejaDeclenchee`
- Pas de test de contrôleur (convention du dépôt).

## Risques et points d'attention pour le plan d'implémentation

- **Ne pas ajouter de champ `createdBy Agent`/`linkedBy Agent` redondant** — `AuditEntity`
  fournit déjà `createdAt`/`createdById`, exactement comme `SeanceCtadpDossier` ne les
  duplique pas.
- **`declencherAutoSaisine` doit appeler `DossierService.submit()` existant, jamais
  dupliquer sa logique** — c'est le point central de ce sous-chantier (zéro nouvelle logique
  de création de dossier).
- **Vérifier l'existence du couple (informationPreoccupanteId, dossierId) avant insertion**
  dans `rattacherDossier`, pas seulement compter sur la contrainte unique SQL — pour retourner
  une `BusinessException` explicite plutôt qu'une erreur d'intégrité SQL brute au client.
- **`SubmissionMode.AUDIT_REPORT`** est un choix de conception documenté ci-dessus, pas une
  valeur à deviner différemment lors de l'implémentation.
