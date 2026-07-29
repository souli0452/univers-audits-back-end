# Habilitation nominative par dossier (lecture) — Design

Statut : approuvé par l'utilisateur le 2026-07-29. Périmètre : deuxième volet de la
fermeture du Lot 1 du plan de travail ASCE-LC (le premier volet, la dérivation
NatureSaisine, a été livré — voir `2026-07-28-nature-saisine-derivation-design.md`).

## Contexte

Le manuel ASCE-LC (§3, principe directeur 9) impose : *« le cloisonnement se fait
par dossier, jamais par rôle seul. L'appartenance à un rôle ne donne accès à aucun
dossier ; l'affectation est nominative, tracée, révocable. »*

Le code actuel a deux systèmes de contrôle d'accès qui ne se parlent pas :

- **Lecture** (`DossierAccessGuard.checkReadAccess`, utilisé par ~13 fichiers —
  contrôleurs et services de sous-ressources d'un dossier) : autorise uniquement
  l'agent unique `Dossier.agentInCharge` ou les rôles CGE/CGEA/ADMIN_DDIC
  (`canSeeConfidential()`). Un enquêteur ajouté à l'équipe de mission
  (`InvestigationMember`, actif) mais qui n'est pas `agentInCharge` ne peut pas
  lire son propre dossier — bug concret constaté en code.
- **Écriture** (toutes les transitions de statut de `DossierController`) : gardées
  uniquement par rôle Keycloak (`@PreAuthorize("hasAnyRole(...)")`), sans aucune
  vérification d'affectation au dossier.

De plus, `DossierServiceImpl.findById/findByStatus/findAll` dupliquent leur propre
logique `agentInCharge`-only au lieu de passer par `DossierAccessGuard` — une
source de divergence déjà documentée dans un commentaire du guard lui-même.

## Décisions

### 1. Périmètre : lecture seulement

Le contrôle d'accès en écriture reste géré par rôle uniquement, comme aujourd'hui.
Raison (arbitrée avec l'utilisateur, 2026-07-29) : chaque transition d'écriture a
une sémantique d'autorisation propre (ex. une décision CGE dépend de l'autorité du
rôle CGE, pas d'une affectation nominative au dossier), contrairement à la
lecture qui est une question uniforme (« puis-je voir ce dossier ? »). Traiter
l'écriture correctement demande d'analyser chacune des ~20 transitions
individuellement — hors périmètre de ce chantier, à traiter en chantier séparé
après livraison et validation de celui-ci.

De même, aucune distinction de rôle d'équipe supplémentaire (personnes-ressources,
DEI) n'est ajoutée à `TeamRole` — ces cas sont couverts par l'octroi manuel
(décision 3) en attendant qu'un besoin concret se confirme.

### 2. Modèle de données : `DossierHabilitation`

Nouvelle entité (`AuditEntity`) :

| Champ | Type | Notes |
|---|---|---|
| `dossier` | FK Dossier | |
| `agent` | FK Agent | l'agent habilité |
| `source` | enum `HabilitationSource{AGENT_IN_CHARGE, INVESTIGATION_TEAM, MANUAL}` | |
| `grantedAt` | Instant | |
| `grantedBy` | FK Agent, nullable | nul pour les octrois système (auto) |
| `revokedAt` | Instant, nullable | |
| `revokedBy` | FK Agent, nullable | |
| `reason` | texte, nullable | **obligatoire** pour `source=MANUAL` |

**Append-only** : un octroi = une ligne, jamais mise à jour sauf pour la révoquer
(`revokedAt`/`revokedBy`). Un nouvel octroi après révocation crée une nouvelle
ligne. Cohérent avec la convention déjà en place pour `Dossier.observations` /
`statusHistory` (traces immuables).

« A un accès actif » = il existe une ligne `dossier=X, agent=Y, revokedAt IS NULL`.

### 3. Déclenchement automatique

- `DossierServiceImpl.registerReception()` : après `dossier.registerReception(agent, ...)`,
  octroie une habilitation `AGENT_IN_CHARGE` à cet agent. Pas de révocation
  automatique associée : il n'existe aujourd'hui aucune méthode de réaffectation de
  `agentInCharge` dans le code (le champ n'est jamais changé après ce point), donc
  aucun trigger de révocation à écrire pour ce cas.
- `InvestigationServiceImpl.addMember()` : octroie une habilitation
  `INVESTIGATION_TEAM` à l'agent ajouté, pour `investigation.getDossier()`. Couvre
  aussi bien le réactivation d'un membre existant que l'ajout d'un nouveau.
- `InvestigationServiceImpl.removeMember()` : révoque l'habilitation
  `INVESTIGATION_TEAM` active de cet agent sur ce dossier.

### 4. Octroi / révocation manuels

Nouveau `DossierHabilitationService` + `DossierHabilitationController`, routes
`/api/v1/dossiers/{dossierId}/habilitations` :

- `POST` (body : `agentId`, `reason` obligatoire) — octroie `MANUAL`. Restreint à
  CGE/CGEA/ADMIN_DDIC.
- `DELETE /{agentId}` (body/param : `reason`) — révoque l'habilitation active de
  cet agent sur ce dossier, quelle qu'en soit la source. Mêmes rôles.
- `GET` — liste les habilitations actives du dossier (transparence/traçabilité).
  Mêmes rôles.

### 5. Le garde-fou de lecture

`DossierAccessGuard.checkReadAccess` : remplace le test
`agentInCharge == agent courant` par une vérification d'existence sur
`DossierHabilitation` (dossier, agent courant, `revokedAt IS NULL`). Le
court-circuit `canSeeConfidential()` (CGE/CGEA/ADMIN_DDIC) reste inchangé — ces
rôles gardent un accès global, ce qui est cohérent avec leur position au sommet
du circuit dans le manuel.

### 6. Unification avec `DossierServiceImpl`

`findById` et la branche restreinte de `findAll`/`findByStatus` (aujourd'hui
`agentInCharge`-only, dupliquée en dehors du guard) passeront par la même
vérification d'habilitation que le guard. Ce n'est pas une extension de
périmètre : sans cette correction, la voie principale pour ouvrir un dossier
resterait bloquée pour les membres d'équipe même après la correction du guard.

`findMyDossiers` (sémantiquement « mes dossiers en tant qu'agent en charge »)
reste basé sur `agentInCharge` — ce n'est pas la même question et il n'y a pas
de raison de la changer.

### 7. Migration et backfill

Nouveau changelog Liquibase `010-dossier-habilitation.sql` (suite de `009-...`
livré avec la dérivation NatureSaisine) :

- crée la table `dossier_habilitation`
- **backfill obligatoire** : une ligne `AGENT_IN_CHARGE` pour chaque dossier ayant
  déjà un `agent_in_charge_id` non nul ; une ligne `INVESTIGATION_TEAM` pour
  chaque `investigation_member` actif existant

Sans ce backfill, tout le monde perdrait l'accès à ses dossiers actuels au
déploiement — c'est un point bloquant de la migration, pas une amélioration
optionnelle.

## Hors périmètre de cette spec

- Contrôle d'accès en écriture (transitions de statut) — chantier séparé futur,
  voir décision 1.
- Rôles d'équipe supplémentaires (personnes-ressources, DEI) — voir décision 1.
- Révocation automatique de `AGENT_IN_CHARGE` — aucune méthode de réaffectation
  n'existe dans le code actuel ; si une telle méthode est ajoutée plus tard, elle
  devra déclencher la révocation correspondante.

## Tests

- Unitaires `DossierAccessGuard` : accès autorisé/refusé selon habilitation
  active/révoquée/absente, court-circuit `canSeeConfidential()` inchangé.
- Unitaires `DossierHabilitationService` : octroi, révocation, rejet si motif
  manquant pour un octroi manuel, rejet si l'agent cible n'existe pas.
- Unitaires sur les points de déclenchement : `registerReception` (octroi
  `AGENT_IN_CHARGE`), `addMember` (octroi `INVESTIGATION_TEAM`, y compris
  réactivation), `removeMember` (révocation).
- Unitaires `DossierServiceImpl.findById` avec un agent habilité uniquement via
  `INVESTIGATION_TEAM` (le cas que ce chantier corrige).
- Pas de test de migration automatisé (même limite déjà documentée pour la spec
  NatureSaisine — pas d'infrastructure Testcontainers dans ce dépôt) ;
  vérification manuelle du backfill avant déploiement, à documenter dans le plan
  comme suivi manuel.
