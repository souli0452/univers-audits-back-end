# Incident d'objectivité (Lot 3, sous-chantier 4/6) — Design

Statut : approuvé par l'utilisateur le 2026-08-05. Quatrième sous-chantier du Lot 3
(Lancement de mission), §5/§8.4/§11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Les sous-chantiers 1/6 (Constitution
d'équipe + Mandat), 2/6 (Engagement de confidentialité + conflit d'intérêts) et 3/6
(Plan d'investigation) sont livrés et mergés. Découpage du Lot 3 en 6 sous-chantiers
(voir mémoire backlog) :
1. Constitution d'équipe + Mandat — livré
2. Engagement de confidentialité + Déclaration de conflit d'intérêts — livré
3. Plan d'investigation — livré
4. Incident d'objectivité (ce document)
5. Procédure d'urgence + mesures conservatoires
6. Insertion effective de la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` dans `open()`/`start()`

## Contexte

Texte exact :
- §8.4 (Objectivité, principe directeur 8) : « Fonctionnalité **déclaration
  d'incident d'objectivité** : accessible à tout agent affecté à un dossier, notifié
  directement le CGE, tracée, permanente. Distincte de la déclaration de conflit
  d'intérêts, qui est préalable à l'affectation. »
- §5 (Modèle de domaine, section « Mission ») : `IncidentObjectivite` listé aux côtés
  de `Mandat`/`EngagementConfidentialite`/`DeclarationConflitInterets` — toutes des
  entités déjà livrées comme sous-ressources d'`Investigation` dans ce Lot.

**Tension entre §5 et §8.4, tranchée par l'utilisateur (2026-08-05)** : §8.4 parle de
« tout agent affecté à **un dossier** » (large, pourrait s'appliquer hors investigation
active), mais §5 place l'entité sous « Mission » avec les autres entités déjà
construites, toutes scopées `Investigation`. Décision : **`IncidentObjectivite` reste
une sous-ressource d'`Investigation`**, cohérent avec le reste de ce sous-chantier et
avec S5 ; l'accès « agent affecté à un dossier » de S8.4 est honoré via
`DossierAccessGuard.checkReadAccess(investigation.getDossier())` — mécanisme déjà
existant et déjà utilisé exactement dans ce but par `AuditionServiceImpl` (habilitation
nominative sur le dossier, en plus d'un filtre de rôle fonctionnel large côté
contrôleur), sans exiger que le déclarant soit formellement membre de l'équipe
d'investigation (`InvestigationMember`).

**« Notifie directement le CGE » — contrainte technique vérifiée** : ce dépôt n'a
**aucun mécanisme pour interroger « tous les agents ayant le rôle CGE »** — les rôles
vivent uniquement dans les claims Keycloak (JWT), pas en base de données ;
`Agent` n'a pas de champ rôle, `AgentRepository` n'a aucune méthode `findByRole`.
Décision utilisateur : cibler `Mandat.agentCGE` — le CGE spécifique qui a délivré le
mandat de **cette** investigation (déjà stocké depuis le sous-chantier 1/6) — plutôt
que d'inventer une diffusion par rôle. Dégrade silencieusement (pas de notification) si
aucun mandat n'existe encore pour l'investigation — situation possible mais rare (un
incident d'objectivité déclaré avant même la délivrance du mandat).

**Portée du canal de notification, décision utilisateur** : notification **portail
uniquement** (entité `Notification` existante, même mécanisme que
`sendMemberAddedNotifications`), **pas d'email** — ajouter un email exigerait une
nouvelle méthode sur `EmailService` (`EmailServiceImpl`), un service partagé hors
périmètre de ce sous-chantier. Texte de notification **codé en dur** (pas de nouvelle
clé `PortalConfigService.resolveNotificationText`) — cohérent avec le principe déjà
appliqué ailleurs dans cette session de ne pas étendre une infrastructure partagée pour
un seul chantier sans nécessité forte.

## Décision

### 1. Entité `IncidentObjectivite`

N:1 avec `Investigation` (plusieurs incidents possibles par investigation, par le même
agent ou des agents différents — aucune contrainte d'unicité). **Permanente et
immuable** — aucune action de modification ou de suppression n'est exposée, cohérent
avec « tracée, permanente » et avec le principe directeur 10 déjà rencontré (§8.2,
conservation de toute information).

| Champ | Type |
|---|---|
| `investigation` | FK, non nul |
| `declaredBy` | FK `Agent`, non nul — toujours l'agent authentifié courant, jamais un champ de requête (même précédent que `Mandat`/`EngagementConfidentialite`) |
| `description` | texte, non nul — nature de l'incident |
| `declaredAt` | `Instant`, non nul |

### 2. API

- `POST /api/v1/investigations/{id}/incidents-objectivite` — déclaration. Réservé aux
  rôles fonctionnels susceptibles d'être affectés à un dossier
  (`CGEA, CGE, CONTROLEUR_ETAT, MEMBRE_CTADP, CONSEILLER_JURIDIQUE, ADMIN_DDIC` — même
  liste que `AuditionController.READ_ROLES`), **plus** une vérification interne
  `DossierAccessGuard.checkReadAccess(investigation.getDossier())` avant toute écriture
  — un agent tenant un de ces rôles mais sans habilitation nominative sur ce dossier
  précis est rejeté. Crée l'incident, déclenche la notification CGE (point 3), écrit
  une observation dans le fil du dossier (légitime ici, contrairement à la fuite
  corrigée au sous-chantier 2/6 : l'accès est déjà vérifié par habilitation nominative,
  pas un `isAuthenticated()` ouvert).
- `GET /api/v1/investigations/{id}/incidents-objectivite` — liste des incidents de
  cette investigation. Mêmes rôles + même vérification `DossierAccessGuard`.

### 3. Notification CGE

À la déclaration, si `Mandat.findByInvestigationId(investigationId)` retourne un
mandat : crée une `Notification` (`channel=PORTAL`, `type=INTERNAL_ALERT`,
`recipient=mandat.getAgentCGE().getKeycloakId()`, sujet/contenu codés en dur en
français, mentionnant le numéro de dossier et l'agent déclarant). Si aucun mandat
n'existe : aucune notification, l'incident est tout de même créé et tracé (dégradation
silencieuse, cohérente avec le comportement déjà adopté pour
`validationDeadline`/`overdue` au sous-chantier 3/6).

## Hors périmètre

- Notification par email (décision utilisateur — portail uniquement).
- Diffusion à « tous les agents CGE » — techniquement impossible sans étendre le modèle
  de données des rôles (rôles vivent uniquement dans Keycloak).
- Modification ou suppression d'un incident déjà déclaré — permanent par construction.
- Tout mécanisme d'escalade ou de suivi de traitement de l'incident (ex. le CGE
  « répond » ou « clôt » l'incident) — non prévu par le texte, qui ne décrit que la
  déclaration et la notification.
- Élargissement du scope à « tout agent affecté à un dossier » indépendamment d'une
  investigation active (décision utilisateur — reste scopé `Investigation`).

## Tests

- `InvestigationServiceImplTest` : `declareIncident` — succès (incident créé,
  `declaredBy` = agent courant), notification CGE créée quand un mandat existe,
  aucune notification créée quand aucun mandat n'existe (pas d'exception). `getIncidents`
  — retourne la liste des incidents d'une investigation.
- Vérifier que l'accès `DossierAccessGuard.checkReadAccess` est bien invoqué avant
  toute écriture (test unitaire avec le mock du guard qui lève `BusinessException`,
  confirmer que l'exception se propage et qu'aucun incident n'est créé).
