# Escalade automatique vers CGEA/CGE — Design

**Sous-chantier 3/4 (dernier) du chantier transversal "jours ouvrables réels"
ASCE-LC.** Suite de [[2026-09-18-alertes-delai-j3-design]] (sous-chantier 2/4,
livré) et [[2026-09-17-jours-ouvrables-calendrier-design]] (sous-chantier 1/4,
livré). Le sous-chantier 4/4 (tableau des dépassements par acteur) est
également livré. Texte source (plan de travail, section 7, ligne 260) :
"...escalade automatique au supérieur hiérarchique...".

## Décisions métier (tranchées avec l'utilisateur)

1. **Cible de l'escalade** : "supérieur hiérarchique" est simplifié en
   "toujours CGEA/CGE" — pas de notion d'agent supérieur désigné
   individuellement (qui n'existe pas dans le modèle `Agent`/`Departement`).
   Diffusion **uniforme** à tous les agents actifs ayant le rôle Keycloak
   `CGEA` ou `CGE`, sur les 4 catégories de dépassement — y compris pour
   investigation/demande-documents où un CGE précis est pourtant déjà
   identifiable via `Mandat.agentCGE` (choix de simplicité et de cohérence
   plutôt qu'un ciblage différencié par catégorie).
2. **Déclencheur** : un délai de grâce après le dépassement, pas simultané à
   l'alerte "à échéance" du sous-chantier 2/4 — l'escalade signifie que le
   premier signal (à l'agent en charge) n'a pas suffi. Délai de grâce fixe :
   **3 jours calendaires** après la date d'échéance déjà dépassée, configurable
   via un nouveau code `ParametreDelai` (`ESCALADE_DELAI_GRACE`), suivant la
   convention déjà établie du dépôt plutôt qu'une valeur codée en dur.

## Périmètre

Les 4 mêmes échéances "tête d'affiche" que le sous-chantier 2/4 : AR,
complément, investigation, demande de documents. Toujours hors périmètre :
les 5 échéances de circuit interne (`REVUE_CJ_RAPPORT`, etc.).

## État actuel (découverte)

- Les rôles Keycloak ne sont **pas** stockés en base applicative — `Agent`
  n'a aucune colonne de rôle. Résolution uniquement via le realm Keycloak au
  moment de la requête (`SecurityUtils.hasRole`, côté agent connecté
  uniquement — pas de résolution "tous les agents ayant le rôle X").
- Aucun mécanisme de notification par rôle n'existe dans ce dépôt aujourd'hui
  — le seul patron proche (`InvestigationServiceImpl.notifyCgeOfIncident`)
  cible un CGE **individuellement mandaté** sur un dossier précis, pas "tous
  les CGE".
- `KeycloakAdminService` (déjà intégré, `keycloak-admin-client` déjà en
  dépendance Maven) expose déjà `getAvailableRoles()`/`getUserRoles(keycloakId)`
  mais **aucune méthode "utilisateurs d'un rôle donné"** — à ajouter. Cette
  classe n'a **aucun test aujourd'hui** (0 fichier
  `KeycloakAdminServiceTest.java`).
- `Notification.recipient` est toujours un `keycloakId` unique (jamais un
  email ni une liste) — une notification = un destinataire. Atteindre
  plusieurs personnes (CGEA + CGE) pour un même dossier nécessite donc
  plusieurs lignes `Notification`, une par destinataire résolu.
- `Notification.dossier` reste une FK obligatoire — chaque notification
  d'escalade reste rattachée au dossier concerné.
- Aucun champ "escaladé"/"escaladeAt" n'existe sur `Dossier` — pas nécessaire
  pour ce sous-chantier (la traçabilité vit dans les lignes `Notification`
  elles-mêmes, comme pour les alertes du 2/4).

## Conception

### 1. Résolution des destinataires — nouvelle méthode Keycloak

Ajout de `KeycloakAdminService.getUserIdsByRole(String roleName): List<String>`,
suivant le patron déjà établi (`try/catch` large, log + liste vide en cas
d'erreur — ne jamais faire planter le job planifié à cause d'un souci
Keycloak transitoire). L'implémentation exacte utilise l'API Admin Keycloak
déjà cliente dans cette classe (`realmResource().roles().get(roleName)...`) —
**la méthode exacte de récupération des membres d'un rôle sur la version de
`keycloak-admin-client` utilisée par ce dépôt doit être vérifiée à
l'implémentation** (signature non garantie ici, à confirmer contre la
Javadoc/le code source de la dépendance réellement résolue par Maven — ne
pas suivre aveuglément un nom de méthode deviné).

Les `keycloakId` résolus pour `CGEA` et `CGE` sont fusionnés en un ensemble
dédupliqué (un agent ne reçoit pas deux notifications s'il cumule les deux
rôles), puis convertis en `Agent` via `AgentRepository.findByKeycloakId`
(méthode déjà existante) — seuls les agents `actif = true` et retrouvés en
base sont retenus comme destinataires réels.

### 2. Nouvelles requêtes "en dépassement depuis plus de N jours"

Miroir inversé des requêtes J-3 du sous-chantier 2/4 (`deadline BETWEEN :now
AND :in3Days`) : ici `deadline < :graceThreshold` où `graceThreshold =
deadlineCalculator.addCalendarDays(Instant.now(), -delaiGraceJours)` (ou,
plus simplement, calculé comme `now` moins le délai de grâce). 4 nouvelles
méthodes, une par catégorie, mêmes jointures/filtres de statut que leurs
équivalents "overdue"/"dueWithin" déjà écrits :

- `DossierRepository.findAcknowledgmentsOverdueBeyondGrace(Instant threshold)`
- `DossierRepository.findComplementsOverdueBeyondGrace(Instant threshold)`
- `InvestigationRepository.findOverdueBeyondGrace(Instant threshold)`
  (réutilise `LEFT JOIN FETCH i.members m LEFT JOIN FETCH m.agent` comme
  `findOverdue` existant, mêmes critères de statut/échéance)
- `DemandeDocumentsRepository.findOverdueBeyondGrace(Instant threshold)`

### 3. Nouveaux types d'alerte — **et correction du CHECK constraint dans la
   même migration**

4 nouvelles valeurs `NotificationType` : `ESCALADE_AR`, `ESCALADE_COMPLEMENT`,
`ESCALADE_INVESTIGATION`, `ESCALADE_DEMANDE_DOCUMENTS` (la plus longue,
30 caractères, tient dans `length = 35`).

**Leçon appliquée directement depuis le Critical du sous-chantier 2/4** : la
migration qui ajoute ces 4 valeurs élargit **dans le même changeset** le
CHECK constraint `notification_type_check` (actuellement à 16 valeurs après
la migration `013`) pour inclure les 4 nouvelles — ne pas répéter l'oubli qui
avait fait annuler des transactions entières en production. Ne **jamais**
éditer les migrations `001`/`011`/`012`/`013` déjà `EXECUTED` — tout correctif
de schéma est une nouvelle migration additive (`014`).

### 4. Déduplication

Comme pour `DemandeDocuments` au sous-chantier 2/4 (corrigé en vague de fix
finale) : dédupliquer par **récence**, pas par existence à vie. Pour chaque
dossier/demande en dépassement au-delà du délai de grâce, vérifier si une
notification d'escalade du type concerné existe déjà **depuis la date de
l'échéance actuelle** (`existsBy...AndCreatedAtAfter(id, type, echeance)`) —
si le dossier a déjà été escaladé pour CETTE échéance, ne pas réescalader
chaque jour ; si l'échéance a changé depuis (prolongation d'investigation,
nouvelle demande de complément, reset d'escalade `DemandeDocuments`), une
nouvelle escalade doit pouvoir se déclencher pour le nouveau cycle. Cette
vérification se fait **une fois par dossier/demande** avant le fan-out — pas
une fois par destinataire résolu (sinon un agent CGEA déjà notifié
bloquerait à tort la notification d'un CGE qui ne l'aurait pas encore été,
ou l'inverse).

### 5. Job planifié — méthode séparée

`sendDeadlineAlerts()` compte déjà 8 blocs quasi-identiques (signalé comme
piste de refactor future par la revue finale du 2/4, non traité). L'escalade
a une nature différente (destinataires résolus dynamiquement via Keycloak,
fenêtre temporelle différente) : nouvelle méthode `escaladeVersSuperieurs()`
dans `NotificationServiceImpl`, propre `@Scheduled` (même cron que l'existant,
`0 0 8 * * MON-FRI`, ou un horaire légèrement décalé si on préfère séparer
visuellement les deux runs dans les logs — à trancher à l'implémentation,
détail mineur).

Structure par catégorie (répétée 4 fois, même patron que les blocs
existants) : requête "overdue beyond grace" → pour chaque dossier/demande →
vérifier dédup → si pas déjà escaladé, résoudre les destinataires (CGEA ∪
CGE) → créer une `Notification` par destinataire résolu.

### 6. Contenu des alertes

Nouvelles clés `portal_config` (patron identique à l'existant), incluant le
nom de l'agent en charge défaillant dans le message (placeholder
`{agentEnCharge}` en plus de `{numero}`), pour que CGEA/CGE sachent
immédiatement qui relancer :

- `notif_subject_escalade_ar` / `notif_content_escalade_ar`
- `notif_subject_escalade_complement` / `notif_content_escalade_complement`
- `notif_subject_escalade_investigation` / `notif_content_escalade_investigation`
- `notif_subject_escalade_demande_documents` / `notif_content_escalade_demande_documents`

### 7. Nouveau paramètre de délai

Nouveau code `ParametreDelai` : `ESCALADE_DELAI_GRACE`, valeur 3,
`jours_ouvrables = FALSE` (délai de grâce simple, cohérent avec la fenêtre
J-3 qui est elle aussi calendaire), `actif = TRUE`. Seedé par la même
migration `014` que le reste.

## Tests

**Gap de couverture à combler, pas à contourner** : `KeycloakAdminService`
n'a aujourd'hui aucun test. La nouvelle méthode `getUserIdsByRole` doit être
testée au niveau service consommateur (`NotificationServiceImplTest`) en
mockant `KeycloakAdminService` comme une classe concrète standard (patron
déjà utilisé dans ce dépôt pour d'autres services sans interface, ex.
`PortalConfigService`) — pas besoin d'un vrai realm Keycloak pour les tests
unitaires de la logique d'escalade.

Couverture minimale exigée :

- Pour chacune des 4 catégories : un test "dépassement au-delà du délai de
  grâce déclenche l'escalade" vérifiant que des notifications sont créées
  pour CHAQUE destinataire résolu (CGEA et CGE), avec le bon type et le bon
  contenu (clé `portal_config` exacte).
- Un test de fusion/déduplication des destinataires : un agent cumulant les
  rôles CGEA et CGE ne reçoit qu'**une seule** notification, pas deux.
- Un test de non-duplication : dossier déjà escaladé pour cette échéance
  précise → pas de nouvelle notification créée.
- Un test de re-déclenchement : dossier déjà escaladé, puis échéance changée
  (nouvelle valeur de `getSentAt()`/deadline) → nouvelle escalade créée
  (même logique que le correctif `DemandeDocuments` du sous-chantier 2/4).
- Un test vérifiant qu'un agent trouvé dans Keycloak mais absent de la table
  `agent` (ou `actif = false`) est correctement exclu des destinataires.

## Hors périmètre (rappel)

- Les 5 échéances de circuit interne — gap distinct, non traité.
- Toute notion de hiérarchie/supérieur désigné par agent — explicitement
  écartée par la décision utilisateur "toujours CGEA/CGE".
- Un canal d'envoi autre que `NotificationChannel.PORTAL` — inchangé.
- Un champ "escaladé" persistant sur `Dossier` — la traçabilité vit dans les
  lignes `Notification`, comme pour les alertes du 2/4.
