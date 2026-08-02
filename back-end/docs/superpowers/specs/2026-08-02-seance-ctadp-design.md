# Séance CTADP (Lot 2, sous-chantier 2/4) — Design

Statut : approuvé par l'utilisateur le 2026-08-02. Périmètre : deuxième sous-chantier du Lot 2
(Étude d'opportunité et priorisation), §11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Le sous-chantier 1/4 (EtudeOpportunite) est
livré (branche `etude-opportunite`, fusionnée). Les sous-chantiers 3/4 (DecisionCGE + branche
ORIENTEE_ADMINISTRATIF) et 4/4 (génération PDF) restent hors périmètre.

## Contexte

Texte exact du Lot 2 (§11) pertinent : « Convocation, ordre du jour et procès-verbal de séance
CTADP. Décision CGE. » Texte des acteurs (§3) : « CTADP (CGEA + 5 chefs de département +
conseiller juridique) : Séance hebdomadaire, priorisation, recommandation. » Délai (§7) :
« Séance CTADP : hebdomadaire. »

Le modèle de domaine (§5) liste `SeanceCTADP` et `DecisionCTADP` comme deux concepts distincts
dans le bloc « Instruction » (aux côtés de `EtudeOpportunite` et `DecisionCGE`). Ce
sous-chantier couvre les deux : `DecisionCTADP` est modélisé comme la recommandation
enregistrée par dossier au sein d'une séance, pas comme une entité séparée — une séance sans
dossier associé n'a pas de sens, et une recommandation n'existe que dans le contexte d'une
séance précise.

Contrairement à l'Étude d'opportunité (qui est une sous-ressource d'UN dossier), le texte
décrit clairement une réunion collective qui examine plusieurs dossiers à la fois — modèle
approuvé par l'utilisateur : `SeanceCTADP` est une entité de premier niveau, pas une
sous-ressource de `Dossier`.

Aucun code existant (`SeanceCTADP`, `CTADP`, `Comite`) ne modélise cette réunion aujourd'hui —
seul le statut `DossierStatus.EN_REVUE_CTADP` et la transition `submitToCtadp()` existent déjà,
sans aucun lien vers une séance particulière.

## Décisions

### 1. Entité `SeanceCTADP`

| Champ | Type |
|---|---|
| `dateSeance` | `Instant`, non nul |
| `statut` | enum `StatutSeanceCtadp{PLANIFIEE, TENUE, ANNULEE}`, non nul, défaut `PLANIFIEE` |
| `participants` | `String(2000)`, nullable — texte libre (pas de référentiel des "5 chefs de département", n'existe pas encore, même simplification que `secteurSensible` sur `EtudeOpportunite`) |
| `procesVerbal` | `String(5000)`, nullable — compte-rendu général, renseigné quand la séance passe à `TENUE` |

### 2. Entité de liaison `SeanceCtadpDossier`

Une ligne par dossier examiné à une séance donnée. L'« ordre du jour » est cette liste
elle-même (pas de champ séparé) — les dossiers apparaissent dans l'ordre où ils sont ajoutés.

| Champ | Type |
|---|---|
| `seanceCtadp` | FK, non nul |
| `dossier` | FK, non nul |
| `recommandation` | enum `RecommandationCtadp{VALIDATION_INVESTIGATION, CLASSEMENT, TRANSMISSION_INSTITUTION_PARTENAIRE, ORIENTATION_ADMINISTRATIVE}`, nullable (renseignée après la réunion) — les 4 valeurs correspondent exactement aux 4 issues du §6 (machine à états) |
| `commentaire` | `String(2000)`, nullable |

Contrainte `UNIQUE(seance_ctadp_id, dossier_id)` — un dossier ne peut apparaître qu'une seule
fois à l'ordre du jour d'UNE séance donnée, mais peut revenir à une séance ultérieure (report,
réexamen) : pas de contrainte d'unicité globale sur `dossier_id` seul.

### 3. Workflow et API

Nouveau contrôleur de premier niveau `/api/v1/seances-ctadp` (pas nesté sous `/dossiers/{id}`,
contrairement à `EtudeOpportunite` — une séance n'appartient à aucun dossier en particulier) :

- `POST /api/v1/seances-ctadp` — créer une séance (`dateSeance`), rôles `CGEA, ADMIN_DDIC`.
- `GET /api/v1/seances-ctadp` — lister (paginé), rôles `AGENT_BRPD, CONSEILLER_JURIDIQUE,
  MEMBRE_CTADP, CGEA, CGE, CONTROLEUR_ETAT, ADMIN_DDIC` (mêmes rôles que la lecture de
  dossier).
- `GET /api/v1/seances-ctadp/{id}` — détail avec la liste des dossiers et leurs
  recommandations, mêmes rôles.
- `POST /api/v1/seances-ctadp/{id}/dossiers` — ajouter un dossier à l'ordre du jour (corps :
  `dossierId`), rôles `CGEA, ADMIN_DDIC`. Rejette si `dossier.getStatus() !=
  DossierStatus.EN_REVUE_CTADP`, ou si la séance n'est pas `PLANIFIEE`, ou si le dossier est
  déjà présent à l'ordre du jour de cette séance.
- `PUT /api/v1/seances-ctadp/{id}/dossiers/{dossierId}` — enregistrer/modifier la
  recommandation pour ce dossier (corps : `recommandation`, `commentaire`), rôles `CGEA,
  CONSEILLER_JURIDIQUE, ADMIN_DDIC`. **Pas** de revérification du statut du dossier à cette
  étape — au moment où la recommandation est saisie après la réunion, le CGE a pu déjà agir
  sur le dossier séparément ; on enregistre ce qui s'est dit en séance, pas une contrainte sur
  l'état courant du dossier.
- `PATCH /api/v1/seances-ctadp/{id}/tenir` — marquer la séance `TENUE` (corps :
  `procesVerbal`), rôles `CGEA, ADMIN_DDIC`. Rejette si la séance n'est pas `PLANIFIEE`.

Aucun contrôle d'habilitation nominative par dossier (`DossierAccessGuard.checkReadAccess`) sur
`POST .../dossiers` ni `PUT .../dossiers/{dossierId}` : ces actions sont réservées aux rôles
CGEA/CONSEILLER_JURIDIQUE/ADMIN_DDIC, qui bypassent déjà l'habilitation partout ailleurs dans
ce dépôt (`DossierAccessGuard.canSeeConfidential()` inclut CGEA/ADMIN_DDIC ; CONSEILLER_JURIDIQUE
n'y figure pas mais n'a accès qu'en écriture de recommandation, jamais en lecture élargie via
ce chantier) — même raisonnement que les 6 endpoints déjà réservés CGE/CGEA/ADMIN_DDIC de
`DossierController`, identifiés comme corrects tels quels lors du chantier contrôle d'accès en
écriture.

### 4. Ce que ce chantier NE fait PAS

Enregistrer une `recommandation` ne déclenche **aucune** transition automatique du statut du
dossier. `declareAdmissible`/`declareInadmissible`/`transfer` restent des actions CGE
indépendantes, manuelles, non liées à cette recommandation. Le lien recommandation CTADP →
décision CGE automatique, et la branche `ORIENTEE_ADMINISTRATIF` manquante de la machine à
états, sont le sujet du sous-chantier 3/4 (`DecisionCGE`), pas de celui-ci.

## Hors périmètre

- Lien automatique recommandation → transition de statut du dossier (sous-chantier
  `DecisionCGE`).
- Branche `ORIENTEE_ADMINISTRATIF` de la machine à états (sous-chantier `DecisionCGE`).
- Référentiel structuré des "5 chefs de département" pour `participants` (Lot 0, hors
  périmètre).
- Génération PDF de convocation/ordre du jour/PV (sous-chantier 4/4, génération de documents).
- Modification/suppression d'un dossier déjà à l'ordre du jour d'une séance (pas de `DELETE`
  sur `SeanceCtadpDossier` — si un dossier est ajouté par erreur, aucune correction prévue dans
  ce chantier ; à traiter si le besoin se confirme).

## Tests

- `SeanceCtadpServiceTest` : création, ajout d'un dossier (succès + rejet si statut dossier
  incorrect + rejet si séance non `PLANIFIEE` + rejet si doublon), enregistrement de
  recommandation, passage à `TENUE` (succès + rejet si déjà `TENUE`/`ANNULEE`).
- Migration `017` : vérifier la contrainte `UNIQUE(seance_ctadp_id, dossier_id)`.
