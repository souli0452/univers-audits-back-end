# Fiche RETEX et leçons à partager — Design

## Statut

**Lot 9 — Veille et capitalisation**, sous-chantier 2/3 :

1. Information préoccupante — livré et mergé
2. **Fiche RETEX par mission + base de leçons à partager** ← ce document (en cours)
3. Référentiel des indices et typologies — à venir

## Contexte

Texte source (`docs/reference/plan-de-travail-asce-lc.md`, ligne 382) : « Fiche RETEX par
mission (type d'infraction, lieu, difficultés rencontrées, origine des premiers soupçons,
impact financier, originalité des schémas détectés, collaborateurs planifiés, jours chargés,
contexte, stratégie et méthodes, synthèse des résultats, enseignements et axes
d'amélioration). Base de leçons à partager consultable. »

Recherche exhaustive de l'existant avant conception (même prudence qu'aux sous-chantiers
précédents) :

- **Deux entités candidates pour porter « mission » existaient : `MissionSuivi` et
  `VisiteTerrain`. Aucune des deux ne convient.** Le plan de travail les situe dans deux
  sections distinctes et jamais synonymes : `VisiteTerrain` (§4 « Conduite », ligne 168) est
  une visite de site pendant l'enquête active ; `MissionSuivi` (§5/§7 « Suivi », lignes 177,
  373) est une vérification a posteriori de l'application d'un plan d'actions, après décision
  CGE. Recoupement champ par champ avec les 12 champs demandés pour la fiche RETEX : **1 seul
  correspond tel quel** (« lieu » → `VisiteTerrain.location`), 3 ont un équivalent partiel de
  sémantique différente, **8 n'existent nulle part**. Le mot « mission » de la ligne 382 est
  employé au sens générique du §5 (ligne 161, « Mission » = l'investigation menée par une
  équipe mandatée), pas au sens de l'une des deux entités techniques.
- **Décision de conception : `FicheRetex` se rattache directement à `Investigation`**, pas à
  `MissionSuivi` ni `VisiteTerrain`. Une fiche RETEX est un bilan rétrospectif de
  l'investigation entière (type d'infraction, schémas de fraude, stratégie d'enquête, impact
  financier) — rien à voir avec un suivi de plan d'actions ou une visite ponctuelle.
- **Décision utilisateur : une seule fiche RETEX par investigation** (`@OneToOne`), rédigée
  après clôture — même garde-fou que `MissionSuivi.ajouter()`
  (`investigation.getCgeApprovedAt() != null`), cohérent : le bilan rétrospectif n'a de sens
  qu'une fois la décision finale rendue.
- **Décision utilisateur (Lot 9, déjà tranchée au sous-chantier 1/3) : la base de leçons à
  partager est un extrait publié des fiches RETEX**, pas une entité indépendante — une action
  explicite « publier » transforme une fiche RETEX existante en `LeconAPartager` consultable
  plus largement.
- **Patron `MissionSuiviService`/`MissionSuiviController` directement réutilisable** : lecture
  du code confirme qu'il applique déjà les 3 leçons du sous-chantier précédent
  (`accessGuard.checkReadAccess(investigation.getDossier())`, `@Transactional(readOnly =
  true)` classe + `@Transactional` sur les méthodes d'écriture, `AgentContextResolver
  .getCurrentAgent()`) — patron de référence direct pour `FicheRetexService`.
- **`TypeInfraction`** (référentiel administrable déjà existant, déjà utilisé par
  `EtudeOpportunite.typeInfraction`) réutilisé pour le champ « type d'infraction » de la fiche
  RETEX — pas de champ texte libre ni de nouvel enum.

## Objectif

Ajouter la capacité de rédiger un bilan RETEX rétrospectif par investigation clôturée, et de
publier tout ou partie de son contenu comme leçon consultable par un public interne plus
large.

## Hors périmètre

- **Rattachement de la fiche RETEX à `MissionSuivi` ou `VisiteTerrain`** — décision de
  conception explicite, voir Contexte.
- **Plusieurs fiches RETEX par investigation** — décision utilisateur explicite.
- **Dépublication/modification d'une leçon déjà publiée** — non demandé par le texte source,
  YAGNI ; si besoin, chantier séparé.
- **Recherche plein texte structurée sur la base de leçons** — « consultable » interprété
  comme une liste paginée triable, pas un moteur de recherche ; YAGNI.
- **Rattachement des collaborateurs planifiés à des comptes `Agent` structurés** — le champ
  reste un texte libre narratif (voir Composants), pas une relation N-N vers `Agent`.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Rattachement de `FicheRetex` | `@OneToOne` sur `Investigation`, pas `MissionSuivi`/`VisiteTerrain` | Aucune des deux entités ne recoupe la sémantique RETEX (voir Contexte) ; « mission » = l'investigation elle-même |
| Cardinalité | Une fiche RETEX par investigation | Décision utilisateur |
| Garde-fou de création | `investigation.getCgeApprovedAt() != null` | Même patron que `MissionSuivi.ajouter()` — le bilan n'a de sens qu'après décision finale |
| Type d'infraction | `@ManyToOne TypeInfraction` (référentiel existant) | Réutilise le référentiel juridique déjà en place, cohérent avec `EtudeOpportunite` |
| Impact financier | `BigDecimal`, nullable | Cohérent avec `Dossier.estimatedLoss`/`ConstitutionPartieCivile.montantReclame` déjà existants |
| Jours chargés | `Integer`, nullable | Nombre de jours-agent consacrés à l'investigation |
| Collaborateurs planifiés | `String` (`TEXT`), texte libre | Champ narratif d'un bilan rétrospectif, pas un roster structuré — YAGNI sur une relation N-N vers `Agent` |
| Contrôle d'accès | `DossierAccessGuard.checkReadAccess(investigation.getDossier())` | Patron déjà établi et vérifié dans `MissionSuiviService`, leçon du sous-chantier précédent (C2) |
| Transactions | `@Transactional(readOnly = true)` classe + `@Transactional` sur les méthodes d'écriture | Patron déjà établi dans `MissionSuiviService`/`DossierServiceImpl`, leçon du sous-chantier précédent (C3) |
| Rattachement de `LeconAPartager` | `@OneToOne` sur `FicheRetex` | Une leçon est un extrait publié d'une fiche précise — au plus une leçon par fiche |
| Accès en lecture à `FicheRetex` | `hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')` | Identique à `MissionSuiviController.READ_ROLES` |
| Accès en écriture à `FicheRetex` et à la publication | `hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')` | Identique à `MissionSuiviController.WRITE_ROLES` |
| Accès en lecture à `LeconAPartager` | `isAuthenticated()` | « Base consultable » à partager largement en interne, patron déjà utilisé par `StatistiqueController.getDashboard` pour un accès interne large |

## Composants

### 1. Nouvelle entité `FicheRetex`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/FicheRetex.java`

Étend `AuditEntity` (patron `MissionSuivi`) :

```java
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fiche_retex", indexes = {
        @Index(name = "idx_fiche_retex_investigation",
                columnList = "investigation_id", unique = true)
})
public class FicheRetex extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_infraction_id")
    private TypeInfraction typeInfraction;

    @Column(name = "lieu", length = 300)
    private String lieu;

    @Column(name = "difficultes_rencontrees", columnDefinition = "TEXT")
    private String difficultesRencontrees;

    @Column(name = "origine_soupcons", columnDefinition = "TEXT")
    private String origineSoupcons;

    @Column(name = "impact_financier", precision = 18, scale = 2)
    private BigDecimal impactFinancier;

    @Column(name = "originalite_schemas", columnDefinition = "TEXT")
    private String originaliteSchemas;

    @Column(name = "collaborateurs_planifies", columnDefinition = "TEXT")
    private String collaborateursPlanifies;

    @Column(name = "jours_charges")
    private Integer joursCharges;

    @Column(name = "contexte", columnDefinition = "TEXT")
    private String contexte;

    @Column(name = "strategie_methodes", columnDefinition = "TEXT")
    private String strategieMethodes;

    @Column(name = "synthese_resultats", nullable = false, columnDefinition = "TEXT")
    private String syntheseResultats;

    @Column(name = "enseignements_axes_amelioration", nullable = false, columnDefinition = "TEXT")
    private String enseignementsAxesAmelioration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redige_par_id", nullable = false)
    private Agent redigePar;
}
```

Seuls `syntheseResultats` et `enseignementsAxesAmelioration` sont obligatoires (le cœur du
bilan rétrospectif) ; les autres champs restent facultatifs — cohérent avec le fait qu'un
bilan RETEX peut être renseigné progressivement et que tous les champs ne s'appliquent pas
systématiquement à chaque investigation.

### 2. Nouvelle entité `LeconAPartager`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/LeconAPartager.java`

```java
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lecon_a_partager", indexes = {
        @Index(name = "idx_lecon_fiche_retex",
                columnList = "fiche_retex_id", unique = true)
})
public class LeconAPartager extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiche_retex_id", nullable = false, unique = true)
    private FicheRetex ficheRetex;

    @Column(name = "titre", nullable = false, length = 300)
    private String titre;

    @Column(name = "resume", nullable = false, columnDefinition = "TEXT")
    private String resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publiee_par_id", nullable = false)
    private Agent publieePar;
}
```

`titre`/`resume` sont rédigés explicitement par l'agent qui publie — pas une copie
automatique des champs de la fiche RETEX (le publicateur choisit ce qui est partageable
largement, une fiche RETEX pouvant contenir des éléments sensibles à ne pas diffuser tels
quels).

### 3. Migration Liquibase

Fichier : `src/main/resources/db/changelog/migrations/041-create-fiche-retex-lecon.sql`
(prochain numéro disponible, `040-create-information-preoccupante.sql` étant le dernier).

```sql
--liquibase formatted sql
--changeset dev:041-create-fiche-retex-lecon

CREATE TABLE fiche_retex (
    id                               UUID          PRIMARY KEY,
    investigation_id                 UUID          NOT NULL UNIQUE REFERENCES investigation(id),
    type_infraction_id               UUID          REFERENCES type_infraction(id),
    lieu                             VARCHAR(300),
    difficultes_rencontrees          TEXT,
    origine_soupcons                 TEXT,
    impact_financier                 NUMERIC(18,2),
    originalite_schemas              TEXT,
    collaborateurs_planifies         TEXT,
    jours_charges                    INTEGER,
    contexte                         TEXT,
    strategie_methodes               TEXT,
    synthese_resultats               TEXT          NOT NULL,
    enseignements_axes_amelioration  TEXT          NOT NULL,
    redige_par_id                    UUID          NOT NULL REFERENCES agent(id),
    version                          BIGINT        NOT NULL DEFAULT 0,
    created_at                       TIMESTAMP     NOT NULL,
    updated_at                       TIMESTAMP,
    created_by_id                    VARCHAR(100),
    updated_by_id                    VARCHAR(100)
);

CREATE TABLE lecon_a_partager (
    id                UUID         PRIMARY KEY,
    fiche_retex_id    UUID         NOT NULL UNIQUE REFERENCES fiche_retex(id),
    titre             VARCHAR(300) NOT NULL,
    resume            TEXT         NOT NULL,
    publiee_par_id    UUID         NOT NULL REFERENCES agent(id),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

COMMENT ON TABLE fiche_retex IS 'Bilan retrospectif RETEX par investigation cloturee (Lot 9 sous-chantier 2/3) - une fiche par investigation';
COMMENT ON TABLE lecon_a_partager IS 'Extrait publie d une fiche RETEX, consultable largement en interne';
```

### 4. Repositories

Fichiers :
- `src/main/java/gov/bf/ascelc/univers_audits/repository/FicheRetexRepository.java` —
  `extends JpaRepository<FicheRetex, UUID>`, méthode
  `Optional<FicheRetex> findByInvestigationId(UUID investigationId)`.
- `src/main/java/gov/bf/ascelc/univers_audits/repository/LeconAPartagerRepository.java` —
  `extends JpaRepository<LeconAPartager, UUID>`, méthodes
  `Page<LeconAPartager> findAllByOrderByCreatedAtDesc(Pageable pageable)`,
  `boolean existsByFicheRetexId(UUID ficheRetexId)`.

### 5. DTOs

- `FicheRetexRequest` (request) : tous les champs de `FicheRetex` sauf `investigation`,
  `redigePar` (résolu côté serveur) — `syntheseResultats`/`enseignementsAxesAmelioration`
  `@NotBlank`, le reste facultatif ; `typeInfractionId` (`UUID`, optionnel) au lieu d'une
  référence directe à l'entité.
- `FicheRetexResponse` (response) : tous les champs + `typeInfractionLibelle`
  (`String`, résolu depuis `typeInfraction.getLibelle()`), `redigeParNom`
  (`Agent.getNomComplet()`), `investigationId`, `createdAt`.
- `PublierLeconRequest` (request) : `titre` (`@NotBlank`, max 300), `resume` (`@NotBlank`).
- `LeconAPartagerResponse` (response) : `id`, `titre`, `resume`, `publieeParNom`,
  `investigationId` (pour permettre au lecteur de remonter au contexte si son rôle l'y
  autorise), `createdAt`.

### 6. Service `FicheRetexService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/FicheRetexService.java`
(classe concrète, patron `MissionSuiviService` — pas d'interface séparée dans ce dépôt pour
ce type de service imbriqué sous investigation).

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FicheRetexService {

    private final FicheRetexRepository ficheRetexRepository;
    private final InvestigationRepository investigationRepository;
    private final TypeInfractionRepository typeInfractionRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public FicheRetexResponse creer(UUID investigationId, FicheRetexRequest request) { ... }

    public FicheRetexResponse obtenir(UUID investigationId) { ... }
}
```

**`creer`** : charge l'investigation (404 si absente) ; `accessGuard.checkReadAccess
(investigation.getDossier())` ; refuse (`BusinessException`) si `cgeApprovedAt == null`
("La rédaction d'une fiche RETEX n'est possible qu'après la décision finale du CGE.") ;
refuse (`BusinessException`) si une fiche existe déjà pour cette investigation
(`findByInvestigationId` non vide — "Une fiche RETEX existe déjà pour cette investigation.")
; résout `typeInfraction` via `typeInfractionRepository.findById` si `typeInfractionId`
fourni (404 si l'ID ne correspond à rien) ; `redigePar` via `agentContextResolver
.getCurrentAgent()`.

**`obtenir`** : charge l'investigation, `accessGuard.checkReadAccess(...)`, retourne la
fiche existante ou `ResourceNotFoundException` si aucune n'a encore été rédigée.

### 7. Service `LeconAPartagerService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/LeconAPartagerService.java`

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeconAPartagerService {

    private final LeconAPartagerRepository leconAPartagerRepository;
    private final FicheRetexRepository ficheRetexRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public LeconAPartagerResponse publier(UUID investigationId, PublierLeconRequest request) { ... }

    public Page<LeconAPartagerResponse> lister(Pageable pageable) { ... }
}
```

**`publier`** : charge l'investigation et sa fiche RETEX (404 si l'une ou l'autre absente —
« Aucune fiche RETEX n'a été rédigée pour cette investigation, impossible de publier une
leçon. ») ; `accessGuard.checkReadAccess(investigation.getDossier())` (contrôle d'accès à la
fiche source, pas à la leçon elle-même — la publication est un acte sur la fiche) ; refuse
(`BusinessException`) si une leçon existe déjà pour cette fiche (`existsByFicheRetexId`) ;
`publieePar` via `agentContextResolver.getCurrentAgent()`.

**`lister`** : **aucun contrôle d'accès dossier** — c'est le point central de la « base
consultable » : une fois publiée, une leçon est visible par tout agent authentifié quel que
soit son habilitation sur le dossier source (c'est tout l'intérêt de la publication —
partager un enseignement au-delà du cercle habilité sur le dossier d'origine). Décision de
conception explicite, pas un oubli du contrôle d'accès.

### 8. Contrôleurs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/FicheRetexController.java`

Route imbriquée `ApiUrls.INVESTIGATIONS + "/{id}/fiche-retex"`, patron
`MissionSuiviController` :

| Méthode | Route | Rôles |
|---|---|---|
| POST | `` | `CONTROLEUR_ETAT`, `ADMIN_DDIC` |
| GET | `` | `CGEA`, `CGE`, `CONSEILLER_JURIDIQUE`, `CONTROLEUR_ETAT`, `MEMBRE_CTADP`, `ADMIN_DDIC` |
| POST | `/publier-lecon` | `CONTROLEUR_ETAT`, `ADMIN_DDIC` |

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/LeconAPartagerController.java`

Nouvelle constante `ApiUrls.LECONS_A_PARTAGER = BASE + "/lecons-a-partager"` (route racine,
pas imbriquée sous investigation — cohérent avec le fait qu'une leçon publiée n'appartient
plus au périmètre d'une seule investigation) :

| Méthode | Route | Rôles |
|---|---|---|
| GET | `` (paginé) | `isAuthenticated()` |

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| Investigation introuvable | `ResourceNotFoundException` (404) |
| Création d'une fiche RETEX avant décision CGE | `BusinessException` (400) |
| Création d'une 2e fiche RETEX pour la même investigation | `BusinessException` (400) |
| `typeInfractionId` fourni mais introuvable | `ResourceNotFoundException` (404) |
| `obtenir` sur une investigation sans fiche RETEX | `ResourceNotFoundException` (404) |
| Publication d'une leçon sans fiche RETEX existante | `BusinessException` (400) |
| Publication d'une 2e leçon pour la même fiche | `BusinessException` (400) |
| Accès à une fiche RETEX/publication sans habilitation sur le dossier | `BusinessException` (403, via `DossierAccessGuard`) |

## Tests

- **`FicheRetexServiceTest`** (patron Mockito standard) :
  - `creer_creeUneFicheRetexApresDecisionCge`
  - `creer_refuseSiCgeApprovedAtNull`
  - `creer_refuseSiFicheDejaExistante`
  - `creer_resoutLeTypeInfractionSiFourni`
  - `creer_refuseSiTypeInfractionIntrouvable`
  - `creer_verifieLeControleDaccesViaDossierAccessGuard`
  - `obtenir_retourneLaFicheExistante`
  - `obtenir_leveResourceNotFoundSiAucuneFiche`
- **`LeconAPartagerServiceTest`** :
  - `publier_creeUneLeconDepuisUneFicheExistante`
  - `publier_refuseSiAucuneFicheRetex`
  - `publier_refuseSiLeconDejaPubliee`
  - `publier_verifieLeControleDaccesViaDossierAccessGuard`
  - `lister_neFiltrePasParHabilitationDossier`
- Pas de test de contrôleur (convention du dépôt).

## Risques et points d'attention pour le plan d'implémentation

- **`creer`/`publier` doivent appeler `accessGuard.checkReadAccess(investigation
  .getDossier())`** — leçon C2 du sous-chantier précédent, à ne jamais oublier pour un
  service touchant `Investigation`/`Dossier`.
- **`@Transactional(readOnly = true)` au niveau classe, `@Transactional` sur les méthodes
  d'écriture uniquement** — leçon C3 du sous-chantier précédent.
- **`LeconAPartagerService.lister()` ne doit PAS appeler `DossierAccessGuard`** — c'est
  intentionnel (voir Composants §7), ne pas "corriger" par réflexe de copier-coller du
  patron des autres services.
- **Ne pas confondre les deux contraintes d'unicité** : `fiche_retex.investigation_id` est
  `UNIQUE` (une fiche par investigation) ET `lecon_a_partager.fiche_retex_id` est `UNIQUE`
  (une leçon par fiche) — deux contraintes distinctes, à vérifier explicitement en code
  (`findByInvestigationId`/`existsByFicheRetexId`) avant insertion, pas seulement compter
  sur l'erreur SQL brute (même leçon que le sous-chantier précédent sur
  `InformationPreoccupanteDossier`).
