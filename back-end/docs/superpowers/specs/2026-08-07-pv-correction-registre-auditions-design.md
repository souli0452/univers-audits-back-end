# Correction PV + RegistreAuditions — Design

Statut : approuvé par l'utilisateur le 2026-08-07. Sous-chantier 4/6 du Lot 4
(Conduite de l'investigation), décodage approuvé le 2026-08-05 (voir
`back-end/docs/reference/plan-de-travail-asce-lc.md`, section "Conduite" —
`Audition — PVAudition — RegistreAuditions`). Les sous-chantiers 1/6
(`DemandeDocuments`), 2/6 (`VisiteTerrain`/`PVConstat`) et 3/6 (règles
métier `Audition`) sont livrés — voir
`docs/superpowers/specs/2026-08-06-audition-regles-metier-design.md` pour le
précédent le plus proche (même module `Audition`, même style de masquage
d'identité).

**Périmètre non couvert par un texte détaillé** : le manuel d'enquête du
contrôleur d'État (PV d'audition, maquette exacte) est une annexe non
disponible dans ce dépôt (`[EN ATTENTE ANNEXES]`, ligne 309 du document de
référence). Les exigences "correction justifiée" et "relecture tracée" sont
reprises telles qu'identifiées à l'audit du 2026-08-05 (backlog, section C) ;
ce document formalise comment les implémenter dans le modèle de données
existant, en s'alignant sur les patrons déjà établis dans ce dépôt plutôt que
sur une maquette précise.

## Objectif

Combler 3 trous identifiés sur le module `Audition`/`PVAudition` et livrer
`RegistreAuditions` :

1. Un PV finalisé ne peut aujourd'hui jamais être corrigé — aucune méthode
   d'amendement, aucune historisation.
2. La relecture du PV à la personne auditionnée avant signature n'est pas
   tracée.
3. `PvAuditionServiceImpl.create()` n'a aucune garde de statut — un PV peut
   être rédigé pour une audition `CANCELLED`/`NO_SHOW`.
4. `RegistreAuditions` (registre confidentiel au DEI, vue transversale
   multi-dossiers) n'existe pas du tout.

## Architecture

Aucune nouvelle machine à états. `PVAudition` gagne deux champs
(`pvVersion`, `readBackAt`) et une nouvelle entité satellite
`CorrectionPvAudition` (N:1), qui reproduit **exactement** le patron déjà
utilisé par `RevisionPlan`/`PlanInvestigation` (snapshot du contenu actuel
avant écrasement, motif obligatoire, jamais de suppression). `RegistreAuditions`
n'est **pas** une nouvelle entité — c'est une requête paginée en lecture
seule sur `Audition`/`PVAudition`/`Investigation`/`Dossier` déjà existants,
exposée par un nouveau contrôleur dédié.

## Composants

### 1. `PVAudition` — champs ajoutés

- `pvVersion: Integer` — `@Builder.Default = 1`, incrémenté à chaque
  correction.
- `readBackAt: Instant` — nullable, positionné une seule fois par
  `PATCH .../pv/relecture`.

### 2. `CorrectionPvAudition` (nouvelle entité)

Même forme que `RevisionPlan` :

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "pv_audition_id", nullable = false)
private PVAudition pvAudition;

@Column(name = "version_number", nullable = false)
private Integer versionNumber;          // version AVANT correction (snapshot)

@Column(name = "content", nullable = false, columnDefinition = "TEXT")
private String content;                 // contenu AVANT correction (snapshot)

@Column(name = "corrected_at", nullable = false)
private Instant correctedAt;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "corrected_by_id", nullable = false)
private Agent correctedBy;

@Column(name = "motif_correction", nullable = false, columnDefinition = "TEXT")
private String motifCorrection;
```

### 3. Workflow PV — méthodes de service

- `PvAuditionServiceImpl.create()` : ajoute la garde
  `audition.getStatus() == AuditionStatus.CONDUCTED`, sinon
  `BusinessException` (même style que `Audition.conduct()`/`cancel()`).
  Un PV documente ce qui a été dit/observé pendant une audition qui a
  effectivement eu lieu.
- Nouvelle méthode `markReadBack(UUID auditionId)` : exige `pv.isFinalized()
  == false` (la relecture précède la signature, pas l'inverse) et
  `readBackAt == null` (une seule fois) ; sinon `BusinessException`. Positionne
  `readBackAt = Instant.now()`.
- `finalizeSignatures()` : ajoute la précondition `pv.getReadBackAt() !=
  null`, sinon `BusinessException` ("Le procès-verbal doit être relu à la
  personne auditionnée avant signature"). Comportement existant inchangé
  sinon (mutuellement exclusif signé/refusé, pas de double finalisation).
- Nouvelle méthode `correct(UUID auditionId, PvAuditionCorrectionRequest
  request)` : exige `pv.isFinalized() == true`, sinon `BusinessException`
  ("Seul un procès-verbal finalisé peut faire l'objet d'une correction").
  Snapshot du contenu courant dans une nouvelle `CorrectionPvAudition`
  (`versionNumber = pv.getPvVersion()`, `content = pv.getContent()`,
  `correctedAt = now()`, `correctedBy = agent courant`, `motifCorrection =
  request.getMotifCorrection()`), puis `pv.setContent(request.getContent())`
  et `pv.setPvVersion(pv.getPvVersion() + 1)`.

### 4. DTOs

- `PvAuditionCorrectionRequest` : `content` (`@NotBlank`), `motifCorrection`
  (`@NotBlank`) — même style que `PlanInvestigationRevisionRequest`.
- `CorrectionPvAuditionResponse` : `versionNumber`, `content`, `correctedAt`,
  `correctedByName`, `motifCorrection`.
- `PvAuditionResponse` gagne `pvVersion`, `readBackAt`,
  `corrections: List<CorrectionPvAuditionResponse>` (peuplé uniquement par
  `findByAuditionId`, comme le patron `InvestigationResponse.members` déjà
  établi — pas de N+1 à craindre, un seul PV par audition).

### 5. Endpoints (`AuditionController`, même contrôleur que le reste du PV)

- `PATCH /api/v1/investigations/{investigationId}/auditions/{auditionId}/pv/relecture`
  — `WRITE_ROLES`, sans corps de requête.
- `PATCH /api/v1/investigations/{investigationId}/auditions/{auditionId}/pv/correction`
  — `WRITE_ROLES`, corps `PvAuditionCorrectionRequest`.

### 6. `RegistreAuditions` — vue transversale

Nouveau `RegistreAuditionsController`, `GET /api/v1/registre-auditions`,
`@PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")` (même précédent
d'approximation DEI que `PlanInvestigation.valider` — aucun rôle DEI dédié
dans ce dépôt). Paramètres `Pageable` (page/size/défaut trié
`scheduledAt,desc`), **aucun filtre en v1** (YAGNI — le besoin réel de
filtrage n'est pas connu, à ajouter s'il se manifeste).

Requête sur `AuditionRepository` (nouvelle méthode, `JOIN FETCH` sur les
relations *-to-one uniquement — `investigation`, `investigation.dossier`,
`investigation.dossier.declarant`, `witness`, `targetedParty` — jamais sur
`investigators`, qui est une collection et casserait la pagination JPA en
mémoire). Le statut du PV (`draft`/`finalized`/`corrected`) est résolu par un
second appel batché sur `PVAuditionRepository` filtré par les
`auditionId` de la page courante (une seule requête `IN (...)`, pas de N+1
par ligne).

`RegistreAuditionEntryResponse` par ligne : `dossierNumber`,
`investigationId`, `auditionId`, `intervieweeType`,
`intervieweeDisplayName` (masqué — **réutilise tel quel** le helper
`AuditionServiceImpl.maskedDeclarantAwareDisplayName(Audition)` livré au
sous-chantier 3/6, aucune duplication de la logique de masquage), `status`
(`AuditionStatus`), `pvStatus` (enum à 3 valeurs, sans ambiguïté :
`AUCUN_PV` = aucune ligne `PVAudition` pour cette audition ; `BROUILLON` =
`PVAudition` existe mais `finalizedAt == null` ; `FINALISE` =
`finalizedAt != null` — pas de valeur `CORRECTED` séparée, `pvVersion > 1`
suffit à le signaler côté client), `pvVersion` (0 si `pvStatus == AUCUN_PV`,
sinon la valeur réelle du PV).

Aucune vérification de confidentialité dossier par dossier n'est nécessaire
ici : `CGEA`/`ADMIN_DDIC` figurent déjà parmi les rôles qui contournent le
masquage de confidentialité (`DossierServiceImpl.maskSensitiveData`,
`canSeeConfidential`) — ce registre n'ouvre donc aucun accès qui n'existe
pas déjà par ailleurs pour ces deux rôles. Le masquage lanceur d'alerte
(`protectionRequested`) reste néanmoins appliqué à l'identique (`CGEA` le
contourne, `ADMIN_DDIC` non — même incohérence déjà assumée et documentée
dans `DossierServiceImpl.maskSensitiveData`, pas une nouvelle décision prise
ici).

## Migration

`028-pv-correction-registre-auditions.sql` :

```sql
--liquibase formatted sql
--changeset dev:028-pv-correction-registre-auditions

ALTER TABLE pv_audition ADD COLUMN pv_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE pv_audition ADD COLUMN read_back_at TIMESTAMP;

CREATE TABLE correction_pv_audition (
    id                UUID PRIMARY KEY,
    pv_audition_id    UUID NOT NULL REFERENCES pv_audition(id),
    version_number    INTEGER NOT NULL,
    content           TEXT NOT NULL,
    corrected_at      TIMESTAMP NOT NULL,
    corrected_by_id   UUID NOT NULL REFERENCES agent(id),
    motif_correction  TEXT NOT NULL,
    -- colonnes AuditEntity standard (created_at/created_by_id/updated_at/
    -- updated_by_id/version) — même patron que toutes les tables existantes
);

CREATE INDEX idx_correction_pv_audition_pv
    ON correction_pv_audition(pv_audition_id);
```

Colonnes `AuditEntity` exactes (types, contraintes) à reprendre telles
qu'elles apparaissent dans une migration existante récente (ex.
`revision_plan` dans la migration `021`) plutôt que redérivées ici.

## Gestion des erreurs

Toutes les nouvelles gardes lèvent `BusinessException` (400), cohérent avec
le reste du module `Audition`/`PVAudition` — aucun nouveau type d'exception.

## Tests

- `create()` : refuse une audition non `CONDUCTED` (nouveau cas), les cas
  existants restent couverts.
- `markReadBack()` : positionne `readBackAt`, refuse une deuxième relecture,
  refuse sur un PV déjà finalisé.
- `finalizeSignatures()` : refuse désormais si `readBackAt == null` (nouveau
  cas), tous les cas existants (signé/refusé exclusifs, déjà finalisé)
  restent couverts.
- `correct()` : refuse sur un PV non finalisé, crée bien une
  `CorrectionPvAudition` avec le contenu ET la version d'AVANT la correction,
  incrémente `pvVersion`, remplace `content`.
- `RegistreAuditionsController`/service : pagination fonctionne, masquage
  DECLARANT appliqué (réutilise les tests déjà écrits pour le helper au
  sous-chantier 3/6 comme référence, pas dupliqués intégralement), rôle
  `CONTROLEUR_ETAT` (qui n'a PAS accès) rejeté — `@PreAuthorize` testé au
  niveau contrôleur si le patron existant du dépôt le permet (sinon test de
  service uniquement, cohérent avec le reste du dépôt qui ne teste
  généralement pas `@PreAuthorize` par du test d'intégration).

## Hors périmètre

- Filtres sur `RegistreAuditions` (par dossier, par période, par statut) —
  ajoutés si un besoin réel se manifeste, pas anticipés.
- `Departement.code == 'DEI'` comme contrôle d'accès dédié — référentiel
  `Departement` toujours non semé (même trou que le Lot 0 et que tous les
  sous-chantiers précédents de ce Lot).
- Notification/alerte à la correction d'un PV — non demandé, aucun patron de
  notification par rôle n'existe dans ce dépôt (voir sous-chantier 4/6 du
  Lot 3, `IncidentObjectivite`, même constat).
- Édition du PV **avant** finalisation (brouillon modifiable) — décidé
  explicitement hors périmètre par l'utilisateur ; un brouillon erroné n'est
  simplement pas finalisé tant qu'il n'est pas prêt.
- Le point différé du sous-chantier 2/6 sur `VisiteTerrain.plannedBy` (non
  mis à jour à `conduct()`) est déjà résolu par le renommage effectué au
  sous-chantier 3/6 — rien à faire ici.
