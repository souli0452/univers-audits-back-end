# Étude d'opportunité (Lot 2, sous-chantier 1/4) — Design

Statut : approuvé par l'utilisateur le 2026-08-02. Périmètre : premier sous-chantier du Lot 2
(Étude d'opportunité et priorisation), §11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Les 3 autres sous-chantiers (SeanceCTADP,
DecisionCGE + branche ORIENTEE_ADMINISTRATIF, génération accusé de réception/réponse motivée)
sont hors périmètre, spécifiés séparément.

## Contexte

Texte exact du Lot 2 (§11) : « Avis du conseiller juridique avec grille de questions
structurée du manuel (existence d'une réelle préoccupation, compétence de l'ASCE-LC ou d'une
autre institution, qualification pénale, suffisance des preuves, nécessité d'une enquête
complémentaire, urgence de mise en sécurité des preuves, opportunité de saisir le Procureur,
secteur sensible ou prioritaire, solidité de l'allégation). »

Modèle de domaine (§5) : `EtudeOpportunite` (avis du conseiller juridique + grille de
questions) fait partie du bloc « Instruction », aux côtés de `SeanceCTADP`, `DecisionCTADP`,
`DecisionCGE` (hors périmètre ici).

État actuel du code : `DossierServiceImpl.startOpportunityStudy()` bascule uniquement le
statut du dossier vers `EN_ETUDE_OPPORTUNITE` et enregistre le texte libre de
`request.getReason()` dans une `Observation` de type `ADMISSIBILITY_ANALYSIS` — aucune entité
structurée, aucune des 9 questions n'est capturée individuellement.

Deux référentiels pertinents existent déjà : `TypeInfraction` (qualifications pénales, Lot 0
partiel) et les champs `decisionJusticeExistante`/`autreInstitutionSaisie` sur `Dossier`
(ajoutés au Lot 1, alimentent en amont les questions 1/2 mais restent des déclarations du
déposant, distinctes du jugement du conseiller juridique). `Secteur` (référentiel du Lot 0,
§2.4) n'existe pas encore.

## Décisions

### 1. Entité `EtudeOpportunite`

Table séparée, liée au dossier par `@ManyToOne` avec contrainte `UNIQUE` sur `dossier_id`
(1:1 appliqué en base, même style que `Witness`/`TargetedParty` — pas de collection ajoutée
sur `Dossier` lui-même).

| Champ | Type | Question |
|---|---|---|
| `dossier` | FK unique | — |
| `preoccupationReelle` / `preoccupationReelleCommentaire` | Boolean / String(2000) | Q1 |
| `competenceAsceLc` / `competenceAsceLcCommentaire` | Boolean / String(2000) | Q2 — jugement du conseiller, distinct de `Dossier.autreInstitutionSaisie` (déclaration du déposant) |
| `natureQualification` | enum `PENALE`/`ADMINISTRATIVE` | Q3, axe 1 (§2.3) |
| `typeInfraction` | FK `TypeInfraction`, nullable | Q3, si `PENALE` |
| `qualificationNonPenale` | enum `IRREGULARITE`/`FRAUDE`/`ACTE_COLLUSION`/`ACTES_ILLICITES`, nullable | Q3, si `ADMINISTRATIVE` (§2.3) — prépare la branche `ORIENTEE_ADMINISTRATIF` du sous-chantier DecisionCGE |
| `preuvesSuffisantes` / `preuvesSuffisantesCommentaire` | Boolean / String(2000) | Q4 |
| `enqueteComplementaireNecessaire` / `...Commentaire` | Boolean / String(2000) | Q5 |
| `urgenceSecurisationPreuves` / `...Commentaire` | Boolean / String(2000) | Q6 |
| `opportuniteSaisirProcureur` / `...Commentaire` | Boolean / String(2000) | Q7 |
| `secteurSensible` / `secteurPrecision` | Boolean / String(300) | Q8 — pas de FK vers un référentiel `Secteur` (n'existe pas, Lot 0) ; simplification assumée, à revoir quand le Lot 0 construira ce référentiel |
| `soliditeAllegation` / `soliditeAllegationCommentaire` | Boolean / String(2000) | Q9 |
| `avisGeneral` | String(5000), nullable | synthèse libre du conseiller |

Tous les champs sont nullables : la grille se remplit progressivement (le conseiller dispose
de 7 jours, §7), pas en un seul appel.

### 2. API — `EtudeOpportuniteController`

Nouveau contrôleur dédié, `@RequestMapping("/api/v1/dossiers/{dossierId}/etude-opportunite")`
— même style que `WitnessController`/`TargetedPartyController` :

- `GET` : lecture, rôles `AGENT_BRPD, CONSEILLER_JURIDIQUE, MEMBRE_CTADP, CGEA, CGE,
  CONTROLEUR_ETAT, ADMIN_DDIC` (mêmes rôles que `DossierController.findById`).
- `PUT` : création ou mise à jour partielle (les champs non fournis dans la requête restent
  inchangés — mêmes sémantiques que `DossierMapper.updateEntity`,
  `NullValuePropertyMappingStrategy.IGNORE`), réservé `CONSEILLER_JURIDIQUE, ADMIN_DDIC`
  (mêmes rôles que `DossierController.startOpportunityStudy`).

Contrairement à `WitnessServiceImpl.create/update` (qui n'appliquent aucun contrôle
d'habilitation nominative au-delà du rôle), le `PUT` ici appelle
`DossierAccessGuard.checkReadAccess(dossier)` avant toute écriture — l'étude d'opportunité est
une analyse juridique sensible, pas une simple fiche de témoin ; on ne reproduit pas
volontairement le trou déjà corrigé sur `Dossier.update()`.

Contrainte de statut : le `PUT` n'est autorisé que si `dossier.getStatus() ==
DossierStatus.EN_ETUDE_OPPORTUNITE` (couvre aussi le retour depuis `EN_ATTENTE_COMPLEMENT`,
qui repasse par ce statut via `complementReceived()`) — sinon `BusinessException`.

### 3. Intégration à la réponse du dossier

`DossierResponse.etudeOpportunite` (nouveau champ, `EtudeOpportuniteResponse`, nullable),
peuplé dans `DossierServiceImpl.enrichAndMaskDetail()` comme `witnesses`/`targetedParties`.
Masqué (mis à `null`) dans `maskSensitiveData()` au même endroit que les autres sous-ressources
quand le dossier est confidentiel et le rôle courant non habilité.

### 4. Garde-fou sur `submitToCtadp` — règle légère

`DossierServiceImpl.submitToCtadp()` vérifie qu'une `EtudeOpportunite` existe pour ce dossier
avant d'autoriser la transition (`BusinessException` sinon). **Pas** de vérification que les 9
champs sont individuellement renseignés — le texte ne l'exige pas explicitement, et une règle
plus stricte serait une invention métier non vérifiée (même logique que le point d'arbitrage
sur les endpoints « file de département »).

## Hors périmètre

- `SeanceCTADP` (convocation, ordre du jour, PV) — sous-chantier séparé.
- `DecisionCTADP`/`DecisionCGE` structurées et la branche `ORIENTEE_ADMINISTRATIF` manquante
  de la machine à états — sous-chantier séparé. `qualificationNonPenale` est modélisé
  maintenant pour ne pas avoir à migrer `EtudeOpportunite` de nouveau plus tard, mais aucune
  transition n'exploite encore ce champ.
- Génération PDF de l'accusé de réception/suites à donner ou de la réponse motivée —
  sous-chantier séparé (mais réutilisera `AsceLcInstitutionalInfo`/le style déjà en place pour
  le récépissé).
- Référentiel `Secteur` structuré (Lot 0).
- Contrôle d'accès en écriture sur `Witness`/`TargetedParty` (`WitnessServiceImpl.create/update`
  n'appliquent pas `checkReadAccess`) — trou pré-existant, hors périmètre de ce chantier,
  distinct du choix fait au point 2 pour `EtudeOpportunite` (nouvelle entité, pas de dette à
  reproduire).

## Tests

- `EtudeOpportuniteServiceTest` (ou équivalent) : création si absente, mise à jour partielle
  (un champ fourni ne doit pas écraser les autres déjà renseignés), rejet si dossier fermé/hors
  statut `EN_ETUDE_OPPORTUNITE`, rejet si agent non habilité (mock `DossierAccessGuard`).
- `DossierServiceImplTest` : `submitToCtadp` rejette si aucune `EtudeOpportunite` n'existe pour
  le dossier ; accepte si une `EtudeOpportunite` existe (même partiellement remplie).
- Migration `016` : vérifier la contrainte `UNIQUE(dossier_id)`.
