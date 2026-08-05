# Procédure d'urgence + mesures conservatoires (Lot 3, sous-chantier 5/6) — Design

Statut : approuvé par l'utilisateur le 2026-08-05. Cinquième sous-chantier du Lot 3
(Lancement de mission), §5/§6/§11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Les sous-chantiers 1/6 (Constitution
d'équipe + Mandat), 2/6 (Engagement de confidentialité + conflit d'intérêts), 3/6
(Plan d'investigation) et 4/6 (Incident d'objectivité) sont livrés et mergés.
Découpage du Lot 3 en 6 sous-chantiers (voir mémoire backlog) :
1. Constitution d'équipe + Mandat — livré
2. Engagement de confidentialité + Déclaration de conflit d'intérêts — livré
3. Plan d'investigation — livré
4. Incident d'objectivité — livré
5. Procédure d'urgence + mesures conservatoires (ce document)
6. Insertion effective de la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` dans `open()`/`start()`

## Contexte

Texte exact :
- §5 (Modèle de domaine, section « Conduite ») : `ProcedureUrgence` — `MesureConservatoire`
  listées ensemble.
- §6 (Machine à états), « Transitions exceptionnelles » : « Déclenchement d'une
  procédure d'urgence (demande DEI + aval CGE) depuis **tout état d'investigation**. »
  Cette transition exceptionnelle est listée aux côtés de deux autres : le
  remplacement d'équipe défaillante (hors périmètre — concept distinct, pas
  `ProcedureUrgence`/`MesureConservatoire`) et la prolongation motivée du délai
  d'enquête (**déjà livré** — `InvestigationServiceImpl.extendDeadline`, existant
  avant cette session, ne modifie **aucun** statut, seulement un champ de délai).

**Interprétation retenue (décision utilisateur, 2026-08-05)** : le déclenchement d'une
procédure d'urgence est classé par le texte comme une « transition exceptionnelle » au
même titre que la prolongation de délai — qui, elle, ne change **aucun** statut
(`Investigation.status` ni `Dossier.status`), juste un champ. Le texte dit aussi
explicitement que ce déclenchement est possible « depuis tout état d'investigation »,
ce qui exclut par construction que ce soit un nœud de la machine à états (un nœud
n'est atteignable que depuis des états précis, pas « tout état »). Décision : le
déclenchement (proposition DEI + décision CGE) est un **enregistrement/événement
tracé**, sans effet sur `Investigation.status` ni `Dossier.status` — cohérent avec le
traitement déjà réservé à la prolongation de délai, et évite d'inventer un nœud non
décrit par §6.

**« Demande DEI »** : même convention déjà établie deux fois dans ce Lot
(`approveDei`, sous-chantier 3/6 pour la validation du plan) — aucun rôle Keycloak
« DEI » n'existe dans ce dépôt ; c'est le rôle `CGEA` qui en tient lieu
fonctionnellement.

**« Aval CGE »** : rôle `CGE`, même convention que `deliverMandat`
(sous-chantier 1/6) — décision réservée au CGE, acteur distinct de celui qui
propose.

**Lien `MesureConservatoire` → `ProcedureUrgence` (décision utilisateur)** : créer une
mesure conservatoire exige qu'une procédure d'urgence **approuvée** existe déjà pour
l'investigation — même patron de chaînage de précondition déjà établi trois fois dans
ce Lot (`Mandat` → `start()`, `EngagementConfidentialite` → `addMember()`). Vérifié à
la création, pas de lien de clé étrangère vers une procédure d'urgence précise (une
mesure peut être justifiée par « une » procédure d'urgence approuvée existante, sans
devoir choisir laquelle si plusieurs ont été approuvées).

## Décision

### 1. Entité `ProcedureUrgence`

N:1 avec `Investigation` — plusieurs procédures d'urgence possibles par investigation
(le texte ne limite pas à une seule ; une investigation de 90 jours peut faire face à
plusieurs situations d'urgence distinctes).

| Champ | Type |
|---|---|
| `investigation` | FK, non nul |
| `justification` | texte, non nul — motif de la demande DEI |
| `requestedBy` | FK `Agent`, non nul — toujours l'agent authentifié courant |
| `requestedAt` | `Instant`, non nul |
| `status` | enum `StatutProcedureUrgence` : `EN_ATTENTE` / `APPROUVEE` / `REJETEE`, non nul, défaut `EN_ATTENTE` |
| `decidedBy` | FK `Agent`, nul tant que non décidée |
| `decidedAt` | `Instant`, nul tant que non décidée |
| `motifDecision` | texte, nul — obligatoire au rejet, optionnel à l'approbation |

### 2. Entité `MesureConservatoire`

N:1 avec `Investigation` — plusieurs mesures possibles, non liées à une procédure
d'urgence précise par clé étrangère (voir Contexte).

| Champ | Type |
|---|---|
| `investigation` | FK, non nul |
| `description` | texte, non nul — nature de la mesure (ex. mise sous scellés, gel) |
| `takenBy` | FK `Agent`, non nul — toujours l'agent authentifié courant |
| `takenAt` | `Instant`, non nul |

### 3. API

- `POST /api/v1/investigations/{id}/procedures-urgence` — demande DEI. Réservé
  `CGEA, ADMIN_DDIC`. Crée avec `status=EN_ATTENTE`.
- `PATCH /api/v1/investigations/{id}/procedures-urgence/{procedureId}/approuver` —
  aval CGE. Réservé `CGE, ADMIN_DDIC`. Rejette si la procédure n'est plus
  `EN_ATTENTE` (déjà décidée). `motifDecision` optionnel.
- `PATCH /api/v1/investigations/{id}/procedures-urgence/{procedureId}/rejeter` —
  rejet CGE. Réservé `CGE, ADMIN_DDIC`. Rejette si la procédure n'est plus
  `EN_ATTENTE`. `motifDecision` **obligatoire**.
- `GET /api/v1/investigations/{id}/procedures-urgence` — liste. Mêmes rôles de
  lecture que le reste de ce Lot (`CGEA, CGE, CONTROLEUR_ETAT, MEMBRE_CTADP,
  ADMIN_DDIC`), avec le filtre confidentialité déjà établi au sous-chantier 4/6
  (`isConfidential && !canSeeConfidential()` → liste vide).
- `POST /api/v1/investigations/{id}/mesures-conservatoires` — déclaration d'une
  mesure. Réservé `CONTROLEUR_ETAT, CGEA, ADMIN_DDIC` (même ensemble que
  `AuditionController.WRITE_ROLES` — action de terrain, pas une décision de
  gouvernance). Rejette si aucune `ProcedureUrgence` au statut `APPROUVEE` n'existe
  pour cette investigation.
- `GET /api/v1/investigations/{id}/mesures-conservatoires` — liste. Mêmes rôles de
  lecture + même filtre confidentialité.

## Hors périmètre

- Tout changement de `Investigation.status`/`Dossier.status` lors du déclenchement
  d'une procédure d'urgence (décision utilisateur — événement tracé seulement).
- Remplacement d'équipe défaillante (§6, autre transition exceptionnelle, concept
  distinct de `ProcedureUrgence`/`MesureConservatoire`).
- Prolongation de délai — déjà livrée avant cette session (`extendDeadline`).
- Lien de clé étrangère entre `MesureConservatoire` et une `ProcedureUrgence`
  précise (décision utilisateur — juste une précondition d'existence).
- Insertion de tout effet de `ProcedureUrgence`/`MesureConservatoire` dans `open()`/
  `start()` — sous-chantier 6/6, non touché ici.
- Génération PDF — non mentionnée pour ces deux entités dans la liste §9 des
  documents générés (contrairement à `Mandat`, `EngagementConfidentialite`, `plan
  d'investigation`) ; pas de document à produire ici.

## Tests

- `InvestigationServiceImplTest` : `demanderProcedureUrgence` — succès (statut
  `EN_ATTENTE`). `approuverProcedureUrgence` — succès, rejet si déjà décidée.
  `rejeterProcedureUrgence` — succès, rejet si motif vide, rejet si déjà décidée.
  `declarerMesureConservatoire` — rejet si aucune procédure approuvée, succès si une
  procédure approuvée existe. `getProcedures/getMesures` — vérifier le filtre
  confidentialité (même patron que le sous-chantier 4/6).
