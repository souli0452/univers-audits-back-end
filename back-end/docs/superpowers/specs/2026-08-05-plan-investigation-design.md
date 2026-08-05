# Plan d'investigation (Lot 3, sous-chantier 3/6) — Design

Statut : approuvé par l'utilisateur le 2026-08-05. Troisième sous-chantier du Lot 3
(Lancement de mission), §5/§7/§11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Les sous-chantiers 1/6 (Constitution
d'équipe + Mandat) et 2/6 (Engagement de confidentialité + conflit d'intérêts) sont
livrés et mergés. Découpage du Lot 3 en 6 sous-chantiers (voir mémoire backlog) :
1. Constitution d'équipe + Mandat — livré
2. Engagement de confidentialité + Déclaration de conflit d'intérêts — livré
3. Plan d'investigation (ce document)
4. Incident d'objectivité
5. Procédure d'urgence + mesures conservatoires
6. Insertion effective de la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` dans `open()`/`start()`

## Contexte

Texte exact :
- §5 (Modèle de domaine, section « Planification ») : « `PlanInvestigation` —
  `PlanningProcedures` — `RevisionPlan` (historique complet, le plan n'est pas un
  cadre figé) ». Aucun autre détail structurel sur ces trois entités ailleurs dans le
  document — contrairement à d'autres entités du plan de travail (ex. `FicheAffectation`),
  aucune liste de champs n'est donnée.
- §3 (table des acteurs) : « Chef DEI — Valide le plan d'investigation, analyse les
  rapports, coordonne les enquêteurs. »
- §7 (Délais et alertes) : « Validation du plan d'investigation par le DEI | 8 jours
  ouvrables après mandats » — le délai court depuis la **délivrance du mandat**, pas
  depuis la soumission du plan. Règle transversale du §7 : « Tous les délais sont des
  paramètres administrables et versionnés, jamais codés en dur. »
- §9 (Documents générés) : le « plan d'investigation » fait partie des documents PDF
  générés (génération PDF déjà systématiquement reportée dans les sous-chantiers
  précédents de ce Lot — même traitement ici).

**Absence de rôle Keycloak dédié au « Chef DEI »** — déjà constaté et arbitré au
sous-chantier 1/6 : `InvestigationController.approveDei` (validation DEI d'un rapport,
Lot 5) est réservé à `hasAnyRole('CGEA','ADMIN_DDIC')`, pas à un rôle « DEI » qui
n'existe pas dans ce dépôt. Même convention reprise ici pour la validation du plan.
`InvestigationController.submitReport` (rédaction/soumission par le chef de mission)
est réservé à `hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')` — `CONTROLEUR_ETAT` est le
rôle Keycloak du chef de mission/enquêteur de terrain dans ce dépôt. Mêmes rôles
repris pour la soumission/révision du plan d'investigation.

**Aucun moteur générique de délais/alertes/escalade n'existe dans ce dépôt** — vérifié
en lisant `Investigation.isOverdue()`/`getRemainingDays()` (calcul à la volée depuis un
champ de date stocké, pas de scheduler, pas d'alerte automatisée) et en confirmant que
le mécanisme décrit au §7 (« compteur en jours ouvrables, alerte à J-3, alerte à
échéance, escalade automatique ») n'est implémenté nulle part — c'est un gap déjà connu
et documenté (Lot 7, `DemandeDocuments` bloqué avant SAISINE_JUDICIAIRE faute
d'automatisation). Ce sous-chantier suit le même précédent que `Investigation` :
calculer `overdue`/`validationDeadline` à la volée dans la réponse, purement
informatif, sans blocage ni alerte.

**Décisions utilisateur (2026-08-05)** — deux points de conception tranchés avant la
rédaction de ce spec :
1. **`PlanningProcedures` devient un champ texte libre sur `PlanInvestigation`**, pas
   une entité séparée — évite d'inventer une structure non étayée par le texte, et
   évite un doublon avec les vraies entités du Lot 4 (`Audition`/`DemandeDocuments`/
   `VisiteTerrain`) qui modéliseront les étapes concrètes le moment venu. Cohérent avec
   le gap déjà noté en mémoire (annexes du Processus C toujours en attente).
2. **Réviser un plan déjà validé réinitialise sa validation** (`validatedAt`/
   `validatedBy` remis à `null`) — le DEI valide un contenu précis à un instant T, pas
   un cadre permanent ; cohérent avec « le plan n'est pas un cadre figé ».

## Décision

### 1. Entité `PlanInvestigation`

1:1 avec `Investigation` (contenu **mutable** — c'est l'état courant du plan, pas un
historique). FK unique, non nulle.

| Champ | Type |
|---|---|
| `investigation` | FK unique, non nul |
| `objectifs` | texte, non nul |
| `methodologie` | texte, non nul |
| `moyensMobilises` | texte, nul |
| `planningProcedures` | texte, nul (décision utilisateur ci-dessus) |
| `version` | `Integer`, commence à 1, incrémenté à chaque révision |
| `submittedAt` | `Instant`, non nul (soumission **initiale**, jamais modifiée par une révision) |
| `submittedBy` | FK `Agent`, non nul |
| `validatedAt` | `Instant`, nul tant que non validé, remis à `null` à chaque révision |
| `validatedBy` | FK `Agent`, nul tant que non validé, remis à `null` à chaque révision |

### 2. Entité `RevisionPlan`

1:N avec `PlanInvestigation` — un snapshot **complet et immuable** du contenu
**avant** chaque révision (« historique complet »), pas un diff.

| Champ | Type |
|---|---|
| `planInvestigation` | FK, non nul |
| `versionNumber` | `Integer`, non nul (le numéro de version qui vient d'être remplacé) |
| `objectifs`, `methodologie`, `moyensMobilises`, `planningProcedures` | snapshot du contenu remplacé |
| `revisedAt` | `Instant`, non nul |
| `revisedBy` | FK `Agent`, non nul |
| `motifRevision` | texte, non nul (obligatoire — pourquoi le plan a été révisé) |

### 3. Workflow et API

Toutes les actions sont sous-ressources de `Investigation`, même patron que `Mandat`/
`EngagementConfidentialite`.

- `POST /api/v1/investigations/{id}/plan-investigation` — soumission initiale. Réservé
  `CONTROLEUR_ETAT, ADMIN_DDIC`. Rejette si aucun `Mandat` n'a été délivré pour cette
  investigation (le délai du §7 se compte depuis le mandat — soumettre un plan sans
  mandat n'a pas de sens métier). Rejette si un `PlanInvestigation` existe déjà (la
  première soumission ne se refait pas — utiliser la révision).
- `PUT /api/v1/investigations/{id}/plan-investigation` — révision. Réservé
  `CONTROLEUR_ETAT, ADMIN_DDIC`. Rejette si aucun plan n'existe encore. Exige un
  `motifRevision` non vide. Crée un `RevisionPlan` snapshot du contenu **actuel** avant
  d'écraser, incrémente `version`, remet `validatedAt`/`validatedBy` à `null`
  (décision utilisateur 2 ci-dessus). `submittedAt`/`submittedBy` ne changent jamais.
- `PATCH /api/v1/investigations/{id}/plan-investigation/valider` — validation DEI.
  Réservé `CGEA, ADMIN_DDIC` (même convention que `approveDei`). Rejette si aucun plan
  n'existe. Rejette si déjà validé (pas de double validation). Ne bloque **pas** si le
  délai de 8 jours ouvrables est dépassé — validation tardive autorisée, purement
  informatif via `overdue` dans la réponse.
- `GET /api/v1/investigations/{id}/plan-investigation` — lecture du plan courant. Mêmes
  rôles que les autres lectures d'investigation (`CGEA, CGE, CONTROLEUR_ETAT,
  MEMBRE_CTADP, ADMIN_DDIC`). Réponse inclut `validationDeadline` et `overdue` calculés
  à la volée : `validationDeadline = Mandat.dateDelivrance + délai configuré` (jours
  calendaires, même simplification que `Investigation.plannedEndDate`, qui ne fait pas
  non plus de vrai calcul en jours ouvrables malgré le flag `joursOuvrables` sur
  `ParametreDelai`) ; `overdue = maintenant > validationDeadline && validatedAt == null`.
- `GET /api/v1/investigations/{id}/plan-investigation/revisions` — historique complet
  des révisions. Mêmes rôles que la lecture du plan courant.

### 4. Délai configurable

Nouveau `ParametreDelai` (code `VALIDATION_PLAN_INVESTIGATION_DEI`, valeur 8,
`joursOuvrables=true`), seedé par la migration de ce sous-chantier — même patron que
les codes déjà seedés par chaque migration qui en introduit un nouveau (`004`, `008`).

## Hors périmètre

- `PlanningProcedures` en tant qu'entité structurée séparée (décision utilisateur —
  champ texte libre à la place).
- Génération PDF du « plan d'investigation » (§9) — reportée, même traitement que
  `Mandat`/`EngagementConfidentialite`.
- Tout moteur générique d'alerte/escalade sur le délai de validation — n'existe nulle
  part ailleurs dans ce dépôt, hors périmètre de ce sous-chantier.
- Blocage de `validatePlan` en cas de dépassement du délai — informatif seulement.
- Insertion de `PlanInvestigation.validatedAt != null` comme condition dans `start()` —
  c'est la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE`, sous-chantier 6/6.
- Vraie logique de calcul en jours ouvrables (calendrier des jours fériés burkinabè) —
  n'existe nulle part ailleurs dans ce dépôt (voir §7), le champ `joursOuvrables` sur
  `ParametreDelai` reste déclaratif, pas exploité par un calcul réel.

## Tests

- `InvestigationServiceImplTest` : `submitPlan` — rejet si aucun mandat, rejet si plan
  déjà existant, succès (version=1, validatedAt=null). `revisePlan` — rejet si aucun
  plan, rejet si motif vide, succès (version incrémentée, validation réinitialisée,
  `RevisionPlan` créé avec le contenu précédent). `validatePlan` — rejet si aucun plan,
  rejet si déjà validé, succès. `getPlan` — `overdue`/`validationDeadline` calculés
  correctement à partir de la date du mandat.
