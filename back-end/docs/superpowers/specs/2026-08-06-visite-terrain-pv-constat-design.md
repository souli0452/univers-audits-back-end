# VisiteTerrain + PVConstat (Lot 4, sous-chantier 2/6) — Design

Statut : approuvé par l'utilisateur le 2026-08-06. Deuxième sous-chantier du Lot 4
(Conduite de l'investigation), §4/§5 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Le sous-chantier 1/6 (Complétion
`DemandeDocuments`) est livré et mergé. Découpage du Lot 4 en 6 sous-chantiers (voir
mémoire backlog) :
1. Complétion `DemandeDocuments` — livré
2. `VisiteTerrain` + `PVConstat` (ce document)
3. Règles métier manquantes sur `Audition` (ordre, 2 enquêteurs, seconde audition)
4. Correction PV + `RegistreAuditions`
5. Extension `Attachment` (chaîne de possession, code auto-généré) + index des pièces
6. `DossierDeTravail` structuré (le plus gros morceau)

## Contexte

Texte exact (§4, Lot 4) : « Visites terrain et PV de constat y compris de carence. »

§5 (Modèle de domaine) : `VisiteTerrain` — `PVConstat` (y compris de carence), listés
juste avant `Audition`/`PVAudition`/`RegistreAuditions` — même famille conceptuelle
(action de terrain + compte-rendu formel).

**Aucun autre détail textuel** au-delà de cette phrase — contrairement à `Audition`
(ordre, 2 enquêteurs, seconde audition, signature...), le texte ne précise rien de
spécifique à `VisiteTerrain`/`PVConstat` au-delà du concept général et de la mention
« y compris de carence » (le concept de « constat de carence » apparaît aussi §6,
« remplacement d'équipe défaillante... sans délai après constat de carence » —
confirmant que « carence » signifie ici un échec constaté : la visite a été tentée
mais n'a pas pu aboutir, ex. site inaccessible, personne présente refuse l'accès).

**Précédent direct dans ce dépôt** : le module `Audition`/`PVAudition` (déjà livré,
jamais couvert par l'audit initial de cette session) est structurellement identique
dans son intention — une action de terrain planifiée/tenue + un compte-rendu formel
séparé — et modélisé en Java exactement comme un futur `VisiteTerrain`/`PVConstat`
devrait l'être : deux entités séparées, deux services séparés
(`AuditionService`/`PvAuditionService`), un seul contrôleur combiné
(`AuditionController` expose les deux). Décision : reprendre cette structure à
l'identique plutôt que d'inventer un patron différent pour un besoin conceptuellement
jumeau.

**Découverte en marge, hors périmètre de ce sous-chantier** : `AuditionStatus.NO_SHOW`
existe déjà dans l'enum (« La personne convoquée ne s'est pas présentée ») mais n'est
jamais atteignable — aucune méthode `Audition`/`AuditionServiceImpl` ne l'assigne.
Même catégorie de trou que `EscalationLevel.SAISINE_JUDICIAIRE` corrigé au
sous-chantier 1/6, mais pour le module `Audition` existant, pas pour ce nouveau
module — laissé pour le sous-chantier 3/6 (« Règles métier manquantes sur Audition »),
qui est le bon endroit pour le traiter puisqu'il touche déjà ce module.

## Décision

### 1. Entité `VisiteTerrain`

| Champ | Type |
|---|---|
| `investigation` | FK, non nul |
| `conductedBy` | FK `Agent`, non nul — toujours l'agent authentifié courant à la planification |
| `location` | texte court, non nul — site visité |
| `scheduledAt` | `Instant`, non nul |
| `conductedAt` | `Instant`, nul jusqu'à la tenue effective |
| `status` | enum `VisiteStatus` : `SCHEDULED`/`CONDUCTED`/`CANCELLED`/`CARENCE`, défaut `SCHEDULED` |
| `summary` | texte, nul jusqu'à la tenue — bref compte-rendu (le détail complet vit dans `PVConstat`) |
| `cancellationReason` | texte, nul — motif si annulée avant la visite |
| `carenceReason` | texte, nul — circonstances constatées si la visite a échoué une fois tentée (distinct de `cancellationReason` : une annulation est une décision prise à l'avance, une carence est un constat fait sur place) |

Actions : `conduct(summary)`, `cancel(reason)`, `markCarence(reason)` — trois
transitions terminales distinctes depuis `SCHEDULED`, chacune avec sa propre
sémantique et son propre champ de motif, calquées sur `Audition.conduct()`/
`Audition.cancel()`.

### 2. Entité `PVConstat`

1:1 avec `VisiteTerrain`.

| Champ | Type |
|---|---|
| `visiteTerrain` | FK unique, non nul |
| `content` | texte, non nul — constat détaillé (findings normaux OU circonstances de la carence, selon le statut de la visite associée) |
| `draftedBy` | FK `Agent`, non nul |

**Pas de mécanisme de signature/finalisation** — contrairement à `PVAudition`
(signature de l'auditionné), une visite de site n'a pas de tiers auditionné à faire
signer. Décision utilisateur : rester minimal, ne pas inventer une signature d'agent
non demandée par le texte.

**Précondition de création, alignée sur le précédent réel (pas sur ce qu'il devrait
idéalement être)** : `PvAuditionServiceImpl.create()` ne vérifie **pas**
`Audition.status == CONDUCTED` avant de créer un PV — juste que l'audition existe et
qu'aucun PV n'existe déjà. `PvConstatServiceImpl.create()` reprend ce même
comportement (existence + absence de doublon uniquement), pour cohérence avec le
précédent réel plutôt que d'introduire une règle plus stricte que celle déjà en
production pour `Audition`.

### 3. API

Même patron qu'`AuditionController` : un seul contrôleur combiné.

- `POST /api/v1/investigations/{investigationId}/visites-terrain` — planification.
- `PATCH .../{visiteId}/conduct` — tenue.
- `PATCH .../{visiteId}/cancel` — annulation.
- `PATCH .../{visiteId}/carence` — constat de carence (nouveau par rapport à
  `Audition`, qui n'a pas d'équivalent atteignable aujourd'hui).
- `GET .../{investigationId}/visites-terrain` — liste.
- `POST .../{visiteId}/pv` — création du PV de constat.
- `GET .../{visiteId}/pv` — lecture du PV.

Rôles identiques à `AuditionController` (`READ_ROLES`/`WRITE_ROLES`, mêmes
constantes de rôles) — pas de nouveau rôle introduit.

## Hors périmètre

- Correction du trou `AuditionStatus.NO_SHOW` inatteignable — sous-chantier 3/6.
- Génération PDF du PV de constat (§9 le liste comme document généré, mais suit le
  même traitement systématiquement reporté que tous les autres documents de ce
  dépôt).
- Index des pièces associées à une visite terrain — sous-chantier 5/6.
- Toute contrainte de type « au moins deux enquêteurs » sur `VisiteTerrain` — le texte
  ne le mentionne que pour `Audition`, pas pour les visites terrain.

## Tests

- `VisiteTerrainServiceImplTest` : `schedule` — succès. `conduct`/`cancel`/`markCarence`
  — succès chacun, rejet si statut déjà terminal (pas `SCHEDULED`). `findByInvestigationId`
  — filtre confidentialité (même patron que `AuditionServiceImplTest`).
- `PvConstatServiceImplTest` : `create` — succès, rejet si un PV existe déjà.
  `findByVisiteId` — succès, rejet d'accès si dossier confidentiel et rôle non
  privilégié (même patron que `PvAuditionServiceImplTest`).
