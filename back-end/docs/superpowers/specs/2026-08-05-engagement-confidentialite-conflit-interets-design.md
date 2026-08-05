# Engagement de confidentialité + Déclaration de conflit d'intérêts (Lot 3, sous-chantier 2/6) — Design

Statut : approuvé par l'utilisateur le 2026-08-05. Deuxième sous-chantier du Lot 3
(Lancement de mission), §8.1/§8.4/§11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Le sous-chantier 1/6 (Constitution
d'équipe + Mandat) est livré et mergé. Découpage du Lot 3 en 6 sous-chantiers (voir
mémoire backlog) :
1. Constitution d'équipe + Mandat — livré
2. Engagement de confidentialité + Déclaration de conflit d'intérêts (ce document)
3. Plan d'investigation
4. Incident d'objectivité
5. Procédure d'urgence + mesures conservatoires
6. Insertion effective de la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` dans `open()`/`start()`

## Contexte

Texte exact :
- §8.1 (Confidentialité et cloisonnement) : « Signature électronique de l'engagement
  de confidentialité préalable à tout accès à un dossier — pour les membres de
  l'équipe **et** pour les personnes auditionnées. »
- §8.4 (Objectivité) : « Fonctionnalité déclaration d'incident d'objectivité :
  accessible à tout agent affecté à un dossier, notifié directement le CGE, tracée,
  permanente. Distincte de la déclaration de conflit d'intérêts, qui est **préalable
  à l'affectation**. »
- §11 (Lot 3) : « constitution d'équipe (...), **contrôle de conflit d'intérêts**,
  appréciation finale et délivrance des mandats par le CGE, **engagements de
  confidentialité**, rédaction du plan d'investigation... »
- §9 (Documents générés) : la « lettre d'engagement de confidentialité » fait partie
  de la liste des documents PDF générés par le système (mandat, recepisse, etc. en
  font aussi partie).

**Portée retenue (décision utilisateur, 2026-08-05)** — trois choix de conception
tranchés avant la rédaction de ce spec :
1. **Self-service par l'agent** : c'est l'agent candidat lui-même qui déclare/signe
   (via son propre JWT), pas le CGEA qui l'ajoute à l'équipe. Cohérent avec le texte
   (« signature électronique... préalable ») et avec le précédent déjà établi pour
   `Mandat` (le CGE authentifié EST le signataire, jamais un champ de requête).
2. **Action combinée** : une seule entité/un seul endpoint portent à la fois la
   déclaration de conflit d'intérêts ET la signature de l'engagement de
   confidentialité — pas deux actions séparées. Le texte les traite comme deux
   exigences distinctes mais ne dit jamais qu'il faut deux gestes API séparés ; les
   combiner colle à un seul geste pratique côté agent et simplifie le sous-chantier.
3. **PDF reporté** : ce sous-chantier livre le modèle de données et les endpoints,
   pas la génération PDF de la lettre (§9). Elle pourra être ajoutée plus tard en
   suivant exactement le patron déjà établi (`PdfExportService`, style institutionnel
   générique comme le récépissé) une fois le modèle de données stabilisé.

**Hors périmètre explicite** :
- Signature de l'engagement de confidentialité pour les **personnes auditionnées**
  (§8.1 le prévoit aussi) — relève du module `Audition`/`PVAudition` (Lot 4), pas
  touché ici. Vérifié : `Audition.java` n'a aujourd'hui aucun champ lié à la
  confidentialité.
- Génération PDF de la lettre d'engagement (décision ci-dessus).
- Réémission/révision d'une déclaration déjà soumise — immuable une fois soumise,
  même précédent que `Mandat` (« pas de réémission modélisée »).
- Toute forme d'application/blocage d'accès au dossier fondée sur la signature de
  confidentialité (le texte le prévoit — « préalable à tout accès à un dossier » —
  mais appliquer ceci changerait le comportement de lecture/écriture sur TOUT le
  système, même catégorie de risque que la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE`
  déjà reportée au sous-chantier 6/6 ; à traiter ensemble ou dans un chantier dédié
  une fois la porte principale posée).
- Déclaration d'incident d'objectivité (§8.4) — explicitement distincte dans le
  texte, c'est le sous-chantier 4/6.

## Décision

### 1. Entité `EngagementConfidentialite`

1:N avec `Investigation` (un agent peut apparaître dans plusieurs investigations,
chacune nécessite sa propre déclaration), 1:N avec `Agent`. Contrainte d'unicité sur
la paire `(investigation, agent)` — une seule déclaration par agent et par
investigation, jamais réémise.

| Champ | Type |
|---|---|
| `investigation` | FK, non nul |
| `agent` | FK, non nul (le déclarant/signataire — toujours l'agent authentifié courant) |
| `hasConflictOfInterest` | `boolean`, non nul |
| `conflictDetails` | `String` (texte), nul sauf si `hasConflictOfInterest = true` (validation applicative, pas de contrainte SQL) |
| `signedAt` | `Instant`, non nul |

Nom retenu (`EngagementConfidentialite`, pas un nom composé) : le champ conflit
d'intérêts est une donnée satellite portée par le même acte, mais le document nommé
dans le texte (§9, liste des PDF générés) reste « engagement de confidentialité » —
garder ce nom garde la cohérence avec cette liste et avec le suivi déjà existant en
mémoire backlog.

### 2. Endpoint de déclaration/signature

`POST /api/v1/investigations/{id}/engagement-prealable` — self-service, réservé à
tout agent authentifié (`@PreAuthorize("isAuthenticated()")`, même précédent que
`GET /api/v1/agents/active`). L'agent signataire est toujours
`agentContextResolver.getCurrentAgent()`, jamais un champ de requête.

Requête : `{ hasConflictOfInterest: boolean, conflictDetails: string (optionnel) }`.

Règles :
- Rejette si `hasConflictOfInterest = true` et `conflictDetails` vide/blanc
  (« Veuillez préciser la nature du conflit d'intérêts déclaré »).
- Rejette si une déclaration existe déjà pour ce couple (investigation, agent)
  (« Une déclaration a déjà été soumise pour cet agent sur cette investigation »).

`GET /api/v1/investigations/{id}/engagement-prealable/{agentId}` — lecture, mêmes
rôles que `GET .../mandat` (`CGEA, CGE, CONTROLEUR_ETAT, MEMBRE_CTADP, ADMIN_DDIC`) —
permet au CGEA de vérifier l'état d'un agent candidat avant de tenter de l'ajouter à
l'équipe.

### 3. Précondition sur `addMember`

`InvestigationServiceImpl.addMember` rejette désormais si :
- Aucune `EngagementConfidentialite` n'existe pour (investigation, `request.getAgentId()`)
  (« L'agent doit d'abord déclarer l'absence de conflit d'intérêts et signer
  l'engagement de confidentialité avant d'être affecté à l'équipe »).
- La déclaration existante a `hasConflictOfInterest = true`
  (« Cet agent a déclaré un conflit d'intérêts et ne peut pas être affecté à cette
  investigation »).

Cette vérification s'applique identiquement à un nouvel ajout et à une réactivation
(pas de distinction — l'entité `EngagementConfidentialite` est indépendante de l'état
`active`/`inactive` d'un `InvestigationMember`).

## Tests

- `InvestigationServiceImplTest` : `addMember` — rejet si aucune déclaration
  n'existe, rejet si déclaration avec conflit, succès si déclaration sans conflit
  existe (nouveau membre ET réactivation).
- Nouveau test pour l'endpoint de déclaration (service) : succès, rejet si
  `conflictDetails` manquant avec `hasConflictOfInterest=true`, rejet si déclaration
  déjà existante.
- Migration `020` : contrainte d'unicité `(investigation_id, agent_id)`.
