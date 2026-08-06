# Règles métier manquantes sur Audition (Lot 4, sous-chantier 3/6) — Design

Statut : approuvé par l'utilisateur le 2026-08-06. Troisième sous-chantier du Lot 4
(Conduite de l'investigation), §4 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Découpage du Lot 4 en 6 sous-chantiers
(voir mémoire backlog) :
1. Complétion `DemandeDocuments` — livré
2. `VisiteTerrain` + `PVConstat` — livré
3. Règles métier manquantes sur `Audition` (ce document)
4. Correction PV + `RegistreAuditions`
5. Extension `Attachment` (chaîne de possession, code auto-généré) + index des pièces
6. `DossierDeTravail` structuré (le plus gros morceau)

## Contexte

Texte exact (§4, Lot 4) : « Planification et déroulement des auditions dans l'ordre
imposé : dénonciateur, puis témoins non impliqués, puis témoins possiblement impliqués,
puis mis en cause en dernier ; alerte si l'ordre n'est pas respecté ; contrôle de la
présence d'au moins deux enquêteurs ; gestion de la seconde audition, déconseillée pour
le mis en cause. »

**Audit du module existant** (déjà livré avant cette session, jamais couvert par
l'audit initial) : `Audition`/`PVAudition`/`AuditionServiceImpl` fonctionnent
(schedule/conduct/cancel) mais aucune des 5 règles ci-dessus n'est implémentée, et
`AuditionStatus.NO_SHOW` existe dans l'enum sans jamais être atteignable (découvert en
marge du sous-chantier 2/6, laissé pour celui-ci).

**Point complémentaire découvert au sous-chantier 2/6** : `VisiteTerrain.conductedBy`
(module jumeau livré) souffre du même défaut de nommage que `Audition.conductedBy` —
le champ est fixé à la planification, jamais mis à jour à la tenue. Sur `Audition`, le
champ disparaît de toute façon (remplacé par une collection, voir Décision 2) ; sur
`VisiteTerrain`, corrigé ici par un simple renommage pour rester cohérent entre les
deux modules jumeaux.

## Décision

### 1. Ordre d'audition — `IntervieweeType.DECLARANT` + `Witness.possiblyImplicated`

Aucune valeur actuelle d'`IntervieweeType` (`TARGETED_PARTY`/`WITNESS`) ne représente le
dénonciateur (le `Declarant` du dossier). Nouvelle valeur `DECLARANT`, **sans FK
dédiée** sur `Audition` — le déclarant est déjà unique par dossier
(`Dossier.declarant`), pas besoin d'un troisième champ ciblé. Validation de `schedule()`
étendue en branche à 3 voies :
- `DECLARANT` : ni `targetedPartyId` ni `witnessId` ; échoue avec `BusinessException` si
  `investigation.getDossier().getDeclarant() == null` (dossier anonyme).
- `TARGETED_PARTY` : `targetedPartyId` requis (comportement actuel inchangé).
- `WITNESS` : `witnessId` requis (comportement actuel inchangé).

`getIntervieweeDisplayName()` étendu avec une branche `DECLARANT` lisant
`investigation.getDossier().getDeclarant().getDisplayName()`.

Aucun signal actuel ne distingue « témoin non impliqué » de « témoin possiblement
impliqué » (2e et 3e étapes de l'ordre imposé). Nouveau booléen
`Witness.possiblyImplicated` (défaut `false`), exposé sur `WitnessRequest`/
`WitnessResponse` (création ET modification), mutable à tout moment — reflète la
compréhension de l'enquête qui évolue, pas seulement figé à la création de la fiche
témoin.

**Classement d'ordre** : `DECLARANT`=0, `WITNESS(possiblyImplicated=false)`=1,
`WITNESS(possiblyImplicated=true)`=2, `TARGETED_PARTY`=3.

**Calcul de l'avertissement — au moment de `conduct()`, pas de `schedule()`** :
plusieurs auditions peuvent légitimement être pré-planifiées dans un ordre différent de
l'ordre imposé pour des raisons logistiques (agendas, disponibilité) ; ce qui compte est
l'ordre réel de **tenue**. À la tenue d'une audition de rang N, si une AUTRE audition de
la même investigation a un rang STRICTEMENT INFÉRIEUR et un statut encore `SCHEDULED`
(pas encore tenue), la réponse porte un champ non bloquant `orderWarning` nommant
l'interview de rang inférieur toujours en attente. **Non bloquant** — `conduct()`
réussit toujours, conformément au texte (« alerte », pas « interdiction »).

### 2. Contrôle « au moins deux enquêteurs » — précondition bloquante à `schedule()`

`Audition.conductedBy` (`Agent` unique) remplacé par une vraie collection
`investigators` (`@ManyToMany`, table de jonction `audition_investigator`
(`audition_id`, `agent_id`)). Cohérent avec le texte (« présence d'au moins deux
enquêteurs » décrit qui est présent, pas qui rédige) et évite de plafonner
arbitrairement à 2 alors que le texte dit « au moins ».

`AuditionScheduleRequest.investigatorIds : List<UUID>` avec `@Size(min = 2)` —
précondition bloquante dès la planification (on sait déjà à l'avance qui va mener
l'audition ; cohérent avec la validation déjà stricte de `schedule()` sur
targetedParty/witness). Chaque ID résolu via `AgentRepository.findById`,
`ResourceNotFoundException` si un ID est introuvable.

`AuditionResponse.conductedByName` → `investigatorNames : List<String>`. Ce champ EST
un champ calculé pur (dérivé de la collection `investigators` de l'entité elle-même) —
mappé via le patron `@AfterMapping`-avec-wrapper déjà établi dans
`DossierDetailsMapper` (même patron que `intervieweeDisplayName`).

Aucune re-vérification du nombre d'enquêteurs à `conduct()`/`cancel()` (décision
utilisateur : le contrôle a lieu une seule fois, à la planification).

### 3. Seconde audition — avertissement à `schedule()`, uniquement pour le mis en cause

**Détection automatique**, aucun champ de lien manuel à saisir. Uniquement quand
`intervieweeType == TARGETED_PARTY` (le texte ne mentionne la seconde audition comme
« déconseillée » que pour le mis en cause — une seconde audition d'un témoin est
normale, pas de raison de la signaler) : à `schedule()`, si une audition `CONDUCTED`
existe déjà dans cette investigation pour le même `targetedPartyId`, la réponse porte un
champ non bloquant `secondAuditionWarning`. **Non bloquant** — `schedule()` réussit
toujours, conformément au texte (« déconseillée », pas « interdite »).

Implémentation : réutilise `auditionRepository.findByInvestigationIdOrderByScheduledAtAsc`
(déjà chargée pour le calcul de l'ordre) filtrée en mémoire — pas de nouvelle méthode de
repository, la taille de liste par investigation reste modeste dans ce domaine.

### 4. Réparation `AuditionStatus.NO_SHOW`

Nouvelle méthode `Audition.markNoShow(String note)` — statut `NO_SHOW`, nouveau champ
nullable `noShowNote` (**distinct** de `cancellationReason` — même raisonnement que la
séparation `cancellationReason`/`carenceReason` de `VisiteTerrain` : une annulation est
une décision prise à l'avance, une absence est un constat fait le jour dit ; ne pas
réutiliser un champ pour deux sémantiques différentes). Précondition `status ==
SCHEDULED` identique à `conduct()`/`cancel()`. Nouvel endpoint
`PATCH /api/v1/investigations/{investigationId}/auditions/{auditionId}/no-show`,
`@RequestParam(required = false) String note` (pas de DTO — même patron que
`VisiteTerrainController.cancel`/`carence`), `WRITE_ROLES` identiques aux autres
actions d'écriture de ce module.

### 5. Renommage `VisiteTerrain.conductedBy` → `plannedBy`

Renommage **au niveau Java uniquement** (champ d'entité, DTO
`VisiteTerrainResponse.conductedByName` → `plannedByName`, mapper, service, contrôleur
si applicable) — **aucune migration** : le nom de colonne base de données
(`conducted_by_id`) ne fuite jamais à travers l'API JSON et n'a pas besoin de changer.
Documente au passage la sémantique réelle (« l'agent qui a planifié la visite », pas
nécessairement celui qui l'a effectivement conduite) plutôt que de la corriger
fonctionnellement (aucune demande utilisateur de mettre à jour ce champ à `conduct()`).

## Migration 026

- `ALTER TABLE witness ADD COLUMN possibly_implicated BOOLEAN NOT NULL DEFAULT false`
- `ALTER TABLE audition ADD COLUMN no_show_note TEXT`
- `CREATE TABLE audition_investigator (audition_id UUID NOT NULL REFERENCES audition(id), agent_id UUID NOT NULL REFERENCES agent(id), PRIMARY KEY (audition_id, agent_id))`
- Backfill : `INSERT INTO audition_investigator (audition_id, agent_id) SELECT id, conducted_by_id FROM audition WHERE conducted_by_id IS NOT NULL` — préserve l'unique enquêteur historique de chaque audition existante dans la nouvelle collection, même si cela laisse ces lignes historiques sous le nouveau minimum de 2 (le contrôle ne s'applique qu'à la création, pas rétroactivement).
- `ALTER TABLE audition DROP COLUMN conducted_by_id`

Aucune contrainte CHECK auto-générée à gérer : `audition.interviewee_type` et
`audition.status` sont des `VARCHAR` sans CHECK (confirmé migration `006`), et
`possibly_implicated`/`no_show_note` sont des colonnes neuves sur des tables
pré-existantes sans lien avec un enum `@Enumerated`.

## Hors périmètre

- `PVAudition` (correction justifiée, relecture tracée) — sous-chantier 4/6.
- `RegistreAuditions` (registre confidentiel au DEI) — sous-chantier 4/6.
- Vérification `Agent.departement.code == 'DEI'` pour un rôle DEI dédié — référentiel
  `Departement` jamais semé (même trou que le Lot 0, noté ailleurs dans le backlog).
- Toute notification automatique liée à `orderWarning`/`secondAuditionWarning` (portail,
  email) — restent des champs de réponse synchrones, pas des événements diffusés.
- Contrainte de non-duplication sur `investigatorIds` (un même agent listé deux fois) —
  non mentionné par le texte, laissé tel quel.

## Tests

- `AuditionServiceImplTest` :
  - `schedule` — succès pour les 3 `IntervieweeType` (dont `DECLARANT`), rejet si
    dossier anonyme pour `DECLARANT`, rejet si `investigatorIds` a moins de 2 éléments,
    `secondAuditionWarning` posé quand un `TARGETED_PARTY` a déjà une audition
    `CONDUCTED`, absent pour un témoin dans le même cas.
  - `conduct` — `orderWarning` posé quand une audition de rang inférieur est encore
    `SCHEDULED`, absent sinon ; rejet si statut déjà terminal (comportement existant
    inchangé).
  - `markNoShow` — succès (statut `NO_SHOW`, `noShowNote` posé), rejet si statut déjà
    terminal.
- `WitnessServiceImplTest` — **aucun fichier de test n'existe aujourd'hui pour
  `WitnessServiceImpl`** (vérifié par recherche, contrairement à ce qu'une première
  rédaction de cette section supposait à tort). Nouveau fichier créé par ce
  sous-chantier, limité au périmètre de cette spec : `create`/`update` —
  `possiblyImplicated` correctement persisté et restitué (par défaut `false` si omis,
  mise à jour prise en compte si fourni). Pas de couverture exhaustive du service
  existant (`findByDossierId`, masquage anonyme, `delete`) — hors périmètre de ce
  sous-chantier, qui ne fait qu'ajouter un champ.
