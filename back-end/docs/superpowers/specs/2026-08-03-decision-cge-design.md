# Décision CGE (Lot 2, sous-chantier 3/4) — Design

Statut : approuvé par l'utilisateur le 2026-08-03. Périmètre : troisième sous-chantier du
Lot 2 (Étude d'opportunité et priorisation), §11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Les sous-chantiers 1/4 (EtudeOpportunite) et
2/4 (SeanceCTADP) sont livrés et fusionnés. Le sous-chantier 4/4 (génération PDF) reste hors
périmètre.

## Contexte

Texte exact du Lot 2 (§11) pertinent : « Décision CGE. » — sommaire. Le détail vient de la
machine à états (§6) :

```
EXAMINEE_CTADP
     |- CLASSEE (reponse motivee au plaignant)
     |- TRANSMISE_INSTITUTION_PARTENAIRE
     |- ORIENTEE_ADMINISTRATIF (irregularite -> autorite hierarchique)
     \- VALIDEE_POUR_INVESTIGATION
```

Le code existant (`DossierServiceImpl`) implémente déjà **3 des 4 issues** — `declareAdmissible`
(→`RECEVABLE`, ≈ `VALIDEE_POUR_INVESTIGATION`), `declareInadmissible` (→`IRRECEVABLE`, ≈
`CLASSEE`), `transfer` (→`TRANSFERE`, ≈ `TRANSMISE_INSTITUTION_PARTENAIRE`) — chacune avec son
propre endpoint `DossierController`, déjà en production. **Il manque uniquement** :

1. La 4ᵉ issue, `ORIENTEE_ADMINISTRATIF` (irrégularité → autorité hiérarchique) — absente de
   `DossierStatus` et de `DossierController`.
2. Un enregistrement **structuré** de la décision — aujourd'hui, chacune des 3 méthodes
   existantes se contente de changer le statut et d'écrire une `Observation` en texte libre
   (`ObservationType.CGE_DECISION`), même défaut que celui corrigé pour l'étude d'opportunité
   (sous-chantier 1/4) avant sa structuration.

`close()` (qui clôture `IRRECEVABLE`/`TRANSFERE` vers `CLASSE`, ou `DECISION_RENDUE` vers
`CLOS`) fonctionne déjà de façon générique — sa condition `previousStatus ==
DossierStatus.DECISION_RENDUE ? CLOS : CLASSE` couvre automatiquement n'importe quel autre
statut, donc `ORIENTEE_ADMINISTRATIF` s'y intègre sans modifier `close()`.

## Décision

**Périmètre retenu** (choisi par l'utilisateur) : garder les 3 endpoints existants inchangés
côté contrat API — pas de breaking change sur une API déjà prévue côté frontend (voir backlog
mémoire section A). Ajouter un 4ᵉ endpoint miroir pour l'issue manquante, et enrichir les 4
méthodes (3 existantes + 1 nouvelle) pour qu'elles créent chacune un enregistrement structuré
`DecisionCGE`, en plus du changement de statut déjà en place.

### 1. `DossierStatus` — ajout de `ORIENTEE_ADMINISTRATIF`

Intégré aux mêmes règles de `validateTransition()` que `RECEVABLE`/`IRRECEVABLE`/`TRANSFERE` :
- Entrée : `case RECEVABLE, IRRECEVABLE, TRANSFERE, ORIENTEE_ADMINISTRATIF -> dossier.getStatus() == DossierStatus.EN_REVUE_CTADP`
- Sortie vers clôture : `case CLASSE -> dossier.getStatus() == IRRECEVABLE || dossier.getStatus() == TRANSFERE || dossier.getStatus() == ORIENTEE_ADMINISTRATIF`

### 2. `DossierServiceImpl.orientAdministratif()` — nouvelle méthode

Miroir exact de `declareInadmissible()` (motif obligatoire, même structure de notification et
d'audit), transition vers `ORIENTEE_ADMINISTRATIF` au lieu de `IRRECEVABLE`.

Endpoint `PATCH /api/v1/dossiers/{id}/orient-administratif`, réservé `CGE, ADMIN_DDIC` — mêmes
rôles que `declareAdmissible`/`declareInadmissible`.

### 3. Entité `DecisionCGE`

1:1 avec `Dossier` (FK unique), même style que `EtudeOpportunite` (sous-ressource d'UN
dossier, pas d'entité groupée comme `SeanceCTADP` — une décision CGE concerne toujours un seul
dossier).

| Champ | Type |
|---|---|
| `dossier` | FK unique, non nul |
| `decision` | enum `RecommandationCtadp` (réutilisé tel quel — mêmes 4 valeurs exactes que la recommandation CTADP, simple réutilisation de type, pas de duplication d'enum) |
| `motif` | `String(2000)`, nullable |
| `dateDecision` | `Instant`, non nul |
| `agentCGE` | FK `Agent`, non nul (qui a rendu la décision — `agentContextResolver.getCurrentAgent()`, déjà résolu dans chacune des 4 méthodes) |

Pas de FK vers `SeanceCtadpDossier` (la recommandation CTADP ayant informé cette décision) —
hors périmètre, voir plus bas.

Chacune des 4 méthodes crée ou remplace ce record (`DecisionCGE` créé s'il n'existe pas encore
pour ce dossier — un dossier ne devrait recevoir qu'une seule décision CGE dans le
fonctionnement nominal, mais le code ne l'empêche pas explicitement en cas de correction).

### 4. Intégration à la réponse du dossier

`DossierResponse.decisionCGE` (nullable), peuplé dans `enrichAndMaskDetail()`, masqué dans
`maskSensitiveData()` — même patron que `etudeOpportunite`.

## Hors périmètre

- Lien traçable entre la recommandation CTADP (`SeanceCtadpDossier.recommandation`) et la
  décision CGE effectivement rendue — un dossier peut passer par plusieurs séances
  (réexamens), et `SeanceCtadpDossierRepository` n'a aujourd'hui pas de méthode pour retrouver
  "la" recommandation pertinente pour un dossier donné sans ambiguïté. Pas d'hypothèse non
  vérifiée à coder ici.
- Unification des 3 endpoints existants en un seul endpoint paramétré — écarté par
  l'utilisateur, breaking change non justifié pour ce chantier.
- Génération PDF de la décision CGE (accusé de réception/suites à donner ou réponse motivée) —
  sous-chantier 4/4.
- Délai d'approbation CGE (10 vs 20 jours ouvrables, §13 point 1 du plan de travail) — point
  d'arbitrage métier non tranché, non traité ici (aucun compteur de délai n'existe encore pour
  cette étape).

## Tests

- `DossierServiceImplTest` : `orientAdministratif` — succès (statut → `ORIENTEE_ADMINISTRATIF`,
  `DecisionCGE` créé avec `decision = ORIENTATION_ADMINISTRATIVE`), rejet si motif absent,
  rejet si transition invalide (dossier pas en `EN_REVUE_CTADP`). Régression sur les 3
  méthodes existantes : chacune crée bien un `DecisionCGE` avec la valeur de `decision`
  attendue, en plus du changement de statut déjà couvert par les tests existants.
- Migration `018` : vérifier la contrainte `UNIQUE(dossier_id)` sur `decision_cge`.
