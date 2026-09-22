# Fiche spéciale d'affectation des dossiers — Design

**Chantier issu de la revue de conformité aux documents officiels ASCE-LC**
(audit mené le 2026-09-22, comparant le code back-end/front-end aux 3
documents fournis par l'utilisateur : *Processus D — Reporting et
communication*, *Fiche spéciale d'affectation des dossiers de plaintes et
de dénonciations*, *Formulaire d'enregistrement des dénonciations et des
plaintes*). Le document d'affectation a été **récemment modifié par le
CGE** — c'est le chantier le plus urgent identifié par l'audit, et le
premier des 11 manques confirmés à être traité.

Rappel de décision antérieure ([[project_asce_revue_fin_projet_2026_09_17]],
point 7) : **CJ/OR et CJ/OO ne sont pas des départements** au sens
organisationnel — "CJ" est le rôle Conseiller Juridique, "/OR" et "/OO" sont
les initiales du conseiller juridique précis en charge. Cette conception en
tient compte directement (voir section "Désignation").

## Décisions métier (tranchées avec l'utilisateur)

1. **Position dans le cycle de vie** : la fiche d'affectation est une
   entité **distincte** de `DecisionCGE` (qui reste la décision finale
   CTADP — valider l'investigation / classer / transmettre / orienter,
   bien plus tard dans le cycle). Elle se situe juste après la réception
   BRPD (`DossierStatus.RECU`), avant l'étude d'opportunité.
2. **Non bloquant** : la fiche est un suivi parallèle de traçabilité/
   reporting. Elle ne conditionne **aucune** transition de statut du
   dossier — un conseiller juridique peut démarrer l'étude d'opportunité
   (`start-study`) indépendamment de l'état de la fiche. Choix assumé pour
   un premier livrable simple ; un lien plus fort (bloquant) pourrait être
   envisagé dans un chantier ultérieur si le besoin se confirme à l'usage.
3. **Désignation département vs conseiller juridique nommé** : plutôt que
   de reproduire littéralement les 5 cases fixes du papier (`DEI`, `DAC`,
   `CJ/OR`, `CJ/OO`, `BRPD`), le CGEA choisit **soit un département
   (`DEI`/`DAC`), soit le BRPD lui-même, soit un agent précis parmi ceux
   ayant le rôle `CONSEILLER_JURIDIQUE`** (liste dynamique résolue à
   l'exécution, pas figée à deux personnes nommées — robuste si le
   personnel change).
4. **Section 4 (suivi et traçabilité)** remplie par **le département/agent
   désigné lui-même**, pas par le CGEA — celui qui a reçu l'affectation
   met à jour l'état d'avancement quand son travail est fait.
5. **Notification automatique** à la validation de la section 3 (CGEA) :
   le(s) destinataire(s) résolu(s) sont notifiés via le système de
   notifications existant, cohérent avec le reste de l'application
   (investigation, alertes, escalade).

## Périmètre

Une fiche d'affectation par dossier (relation 1-à-1), créée à l'initiative
d'un utilisateur ayant le rôle `CGE`, complétée par `CGEA`, puis par le
département/agent désigné. Reproduit les 4 sections du document papier
(informations générales, transmission CGE, affectation CGEA, suivi).

## État actuel (découverte)

- Aucune entité ne modélise ce circuit aujourd'hui. `DecisionCGE.java`
  (1-à-1 avec `Dossier`, `dossier_id` unique) est le seul précédent de ce
  type de relation — modèle réutilisé pour `FicheAffectation`.
- `Departement.java` existe et contient 7 codes actifs/inactifs (`DAC`,
  `DEI`, `DCP`, `DIP`, `DSRAJ`, `DSI`, `DRH`) — **ni `BRPD` ni `CJ` n'y
  figurent**, conformément à la décision antérieure. Seuls `DEI` et `DAC`
  sont des cibles valides pour cette fonctionnalité (validation métier en
  service, pas une contrainte DB, le référentiel restant partagé avec
  d'autres usages).
- `Agent.java` a une relation `@ManyToOne Departement departement` mais
  **aucune colonne de rôle applicatif** — le rôle `CONSEILLER_JURIDIQUE`
  n'existe que côté Keycloak (confirmé par l'usage
  `@PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')")` sur
  `DossierController.startOpportunityStudy`,
  `DossierController.java:157-158`). Résoudre "la liste des conseillers
  juridiques" pour peupler un menu déroulant nécessite donc
  `KeycloakAdminService.getUserIdsByRole("CONSEILLER_JURIDIQUE")` (déjà
  ajoutée au chantier "escalade automatique",
  [[project_asce_jours_ouvrables_2026_09_17]]) puis une résolution vers
  `Agent` via `AgentRepository.findByKeycloakId`, filtrée sur `actif =
  true` — même patron que la résolution des destinataires d'escalade.
- `NotificationType` compte aujourd'hui 20 valeurs
  (`src/main/java/gov/bf/ascelc/univers_audits/enums/NotificationType.java`).
  Ajouter une valeur exige d'élargir le CHECK constraint
  `notification_type_check` **dans la même migration** — leçon tirée du
  Critical du sous-chantier "alertes-délai-j3" et déjà appliquée 2 fois
  depuis (migrations `013`, `014`). À ne pas oublier une 3<sup>e</sup> fois.
- `AuditEntity` (classe mère, `abstracts/AuditEntity.java`) fournit déjà
  `createdAt`/`createdById`/`updatedAt`/`updatedById` via Spring Data
  Auditing — mais un **seul** couple création/dernière-modification pour
  toute l'entité. Comme la fiche a 3 auteurs à 3 moments distincts (CGE,
  CGEA, département), ces champs génériques ne suffisent pas : la
  conception ajoute des champs explicites par section (voir ci-dessous).
- Côté frontend, `dossier-detail.ts` a déjà un système d'onglets (`activeTab`,
  liste d'onglets avec `key`/`label`/`icon`, ex. `{ key: 'attachments',
  label: 'Pièces jointes', icon: 'pi pi-paperclip' }` à la ligne 146-147) —
  patron direct à suivre pour un nouvel onglet "Affectation".

## Conception

### 1. Entité `FicheAffectation`

```java
@Entity
@Table(name = "fiche_affectation", indexes = {
        @Index(name = "idx_fiche_affectation_dossier",
                columnList = "dossier_id", unique = true)
})
public class FicheAffectation extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false, unique = true)
    private Dossier dossier;

    // ── Section 2 : Transmission par le Cabinet du CGE ──────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_cge", length = 30)
    private DecisionCgeAffectation decisionCge;   // AFFECTATION_DIRECTE_CGEA | ECHANGE_PREALABLE

    @Column(name = "observations_cge", columnDefinition = "TEXT")
    private String observationsCge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cge_id")
    private Agent agentCge;

    @Column(name = "date_decision_cge")
    private Instant dateDecisionCge;

    // ── Section 3 : Affectation par le CGEA ─────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "type_designation", length = 20)
    private TypeDesignation typeDesignation;      // DEPARTEMENT | AGENT_CJ | BRPD

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departement_designe_id")
    private Departement departementDesigne;       // renseigné si typeDesignation = DEPARTEMENT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_designe_id")
    private Agent agentDesigne;                   // renseigné si typeDesignation = AGENT_CJ

    @Column(name = "observations_cgea", columnDefinition = "TEXT")
    private String observationsCgea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cgea_id")
    private Agent agentCgea;

    @Column(name = "date_imputation")
    private Instant dateImputation;

    // ── Section 4 : Suivi et traçabilité ────────────────────────────
    @Column(name = "date_retour")
    private Instant dateRetour;

    @Enumerated(EnumType.STRING)
    @Column(name = "etat_avancement", length = 20)
    private EtatAvancementAffectation etatAvancement;   // EN_COURS | CLOTURE | AUTRE

    @Column(name = "etat_avancement_precision", length = 300)
    private String etatAvancementPrecision;       // renseigné si etatAvancement = AUTRE

    @Column(name = "commentaires_suivi", columnDefinition = "TEXT")
    private String commentairesSuivi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_suivi_id")
    private Agent agentSuivi;
}
```

Nouveaux enums :

```java
public enum DecisionCgeAffectation { AFFECTATION_DIRECTE_CGEA, ECHANGE_PREALABLE }

public enum TypeDesignation { DEPARTEMENT, AGENT_CJ, BRPD }

public enum EtatAvancementAffectation { EN_COURS, CLOTURE, AUTRE }
```

Toutes les colonnes de section 3/4 sont **nullables** : la fiche existe dès
la section 2 remplie (créée par CGE) et se complète progressivement — la
section 1 ("informations générales") n'a pas de colonnes propres, elle se
lit directement depuis `dossier.number`/`dossier.receptionDate`.

**Validation métier (service, pas contrainte DB)** :
- Si `typeDesignation = DEPARTEMENT`, `departementDesigne` doit être non
  nul et son `code` doit être `DEI` ou `DAC` (rejet sinon — les 5 autres
  codes du référentiel ne sont pas des cibles valides pour ce circuit).
- Si `typeDesignation = AGENT_CJ`, `agentDesigne` doit être non nul, actif,
  et posséder le rôle Keycloak `CONSEILLER_JURIDIQUE` (vérifié à l'écriture
  via `KeycloakAdminService.getUserIdsByRole("CONSEILLER_JURIDIQUE")` —
  cohérence à l'instant T, pas une contrainte perpétuelle si le rôle change
  après coup).
- Si `typeDesignation = BRPD`, ni `departementDesigne` ni `agentDesigne` ne
  sont renseignés.
- Si `etatAvancement = AUTRE`, `etatAvancementPrecision` doit être non vide.

### 2. Migration

Une nouvelle migration additive, `016-fiche-affectation.sql` (dernier
numéro constaté : `015-seed-notification-templates-escalade.sql` — à
reconfirmer à l'implémentation si d'autres migrations sont arrivées
entretemps, ne jamais réutiliser un numéro déjà `EXECUTED`) :
- `CREATE TABLE fiche_affectation (...)` avec les colonnes ci-dessus, FK
  vers `dossier` (unique), `departement`, `agent` (×3 : cge/cgea/designe/suivi
  — 4 FK vers `agent` en tout : `agent_cge_id`, `agent_cgea_id`,
  `agent_designe_id`, `agent_suivi_id`).
- Ajout de la valeur `AFFECTATION_DOSSIER` à `NotificationType` **et**
  élargissement du CHECK constraint `notification_type_check` dans la
  **même** migration (21<sup>e</sup> valeur).

### 3. Endpoints REST

Nouveau `FicheAffectationController`, sous-ressource de `Dossier` :

- `POST /api/v1/dossiers/{id}/fiche-affectation` — crée la fiche, section 2
  uniquement (`decisionCge`, `observationsCge`). Rôle :
  `@PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")`. Échoue si une fiche
  existe déjà pour ce dossier (409/`BusinessException`, cohérent avec le
  patron `DecisionCGE`/`RapportEnquete` déjà en place).
- `PATCH /api/v1/dossiers/{id}/fiche-affectation/affectation` — renseigne
  la section 3 (`typeDesignation`, `departementDesigne`/`agentDesigne`,
  `observationsCgea`). Rôle : `@PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")`.
  Déclenche la notification (section 5). Échoue si la section 2 n'est pas
  encore remplie.
- `PATCH /api/v1/dossiers/{id}/fiche-affectation/suivi` — renseigne la
  section 4 (`dateRetour`, `etatAvancement`, `commentairesSuivi`).
  **Autorisation dynamique, pas un simple `@PreAuthorize` statique** : le
  service vérifie que l'agent authentifié appartient au département
  désigné (si `DEPARTEMENT`), est l'agent désigné lui-même (si `AGENT_CJ`),
  ou possède le rôle `AGENT_BRPD` (si `BRPD`) — sinon `AccessDeniedException`.
  `ADMIN_DDIC` et `CGEA` passent toujours (supervision).
- `GET /api/v1/dossiers/{id}/fiche-affectation` — lecture. Rôle : `CGE`,
  `CGEA`, `ADMIN_DDIC`, ou le département/agent désigné (même logique
  d'autorisation dynamique que le PATCH suivi, en lecture).

### 4. Service

`FicheAffectationService`/`FicheAffectationServiceImpl`, patron identique à
`RapportEnqueteService` (déjà existant) : validations métier ci-dessus,
mapping DTO via un `FicheAffectationMapper` (MapStruct, cohérent avec le
reste du dépôt).

### 5. Notification automatique (section 3 validée)

Nouveau `NotificationType.AFFECTATION_DOSSIER`. Résolution des
destinataires selon `typeDesignation`, dans `FicheAffectationServiceImpl`
(pas dans `NotificationServiceImpl` — ce n'est pas un job planifié, c'est
déclenché synchroniquement à l'action CGEA) :

- `DEPARTEMENT` → tous les agents actifs de `departementDesigne.getAgents()`
  (relation déjà chargée par `Departement.agents`).
- `AGENT_CJ` → uniquement `agentDesigne` (un seul destinataire).
- `BRPD` → tous les agents résolus par
  `KeycloakAdminService.getUserIdsByRole("AGENT_BRPD")`, convertis en
  `Agent` actifs (même patron que la résolution CGEA/CGE du chantier
  escalade).

Une `Notification` par destinataire résolu (patron `creerEscalades` du
chantier précédent), `channel = PORTAL`, nouvelles clés `portal_config` :
`notif_subject_affectation_dossier` / `notif_content_affectation_dossier`,
placeholders `{numero}` et `{departementOuAgent}` (libellé lisible :
"Département DEI", "M./Mme Ouédraogo (Conseiller Juridique)", ou "BRPD").

### 6. Frontend

- **Modèle** : `FicheAffectation` (interface TS) dans un nouveau
  `fiche-affectation.model.ts` (ou ajout à `dossier.model.ts`, à trancher
  à l'implémentation selon la taille), miroir des DTO backend.
- **Service** : `FicheAffectationService` (`src/app/core/services/`),
  méthodes `create()`, `updateAffectation()`, `updateSuivi()`, `get()`,
  patron HTTP identique à `StatistiqueService`/`AttachmentService`.
- **UI** : nouvel onglet `{ key: 'affectation', label: 'Affectation',
  icon: 'pi pi-sitemap' }` dans `dossier-detail.ts` (aux côtés de l'onglet
  "Pièces jointes" existant, ligne 146-147), avec 3 blocs correspondant aux
  sections 2/3/4, chacun visible/éditable selon le rôle de l'utilisateur
  connecté (réutilise `KeycloakService.hasAnyRole` déjà utilisé partout
  dans ce dépôt frontend) et l'état de complétion de la fiche.
- Le sélecteur "agent désigné" (section 3, cas `AGENT_CJ`) charge la liste
  des conseillers juridiques via un nouvel endpoint (ou paramètre) —
  **à définir à l'implémentation** : soit un endpoint dédié
  `GET /api/v1/agents?role=CONSEILLER_JURIDIQUE`, soit une extension du
  `AgentService` existant. Le plan d'implémentation devra trancher ce
  détail contre l'API `AgentController` réellement disponible.

## Tests

- `FicheAffectationServiceImplTest` : création (section 2), un test par
  branche de validation de la section 3 (département valide DEI/DAC,
  département invalide rejeté, agent CJ valide, agent sans le rôle
  CONSEILLER_JURIDIQUE rejeté, BRPD sans FK superflue), un test par cas de
  résolution des destinataires de notification (département → N agents,
  agent CJ → 1 agent, BRPD → agents résolus Keycloak), un test
  d'autorisation dynamique pour la section 4 (agent du bon département
  autorisé, agent d'un autre département rejeté, agent désigné nommé
  autorisé même hors département, ADMIN_DDIC toujours autorisé).
- `FicheAffectationControllerTest` (ou test d'intégration `@WebMvcTest`,
  patron déjà utilisé ailleurs dans ce dépôt) : 403 si rôle incorrect sur
  chaque endpoint, 409 si double création.
- Test de migration : vérifier que le CHECK constraint
  `notification_type_check` inclut bien `AFFECTATION_DOSSIER` (patron déjà
  établi — lecture live du DB pendant la revue finale de branche, comme
  pour les 2 derniers sous-chantiers du chantier jours-ouvrables).

## Hors périmètre

- Rendre la fiche bloquante pour la progression du dossier — décision 2
  ci-dessus, à reconsidérer plus tard si le besoin se confirme.
- Signatures numériques/images (le papier a des blocs de signature CGE/CGE
  Adjoint) — remplacées par la traçabilité `agentCge`/`agentCgea`/`agentSuivi`
  + horodatage, cohérent avec le reste de l'application (aucune signature
  image n'existe ailleurs dans ce dépôt).
- Étendre le référentiel `Departement` pour y ajouter `BRPD`/`CJ` — décision
  déjà tranchée de garder ces notions hors du référentiel organisationnel.
- Un écran de liste/recherche des fiches d'affectation (statistiques,
  tableau de bord) — pourrait être un chantier de suivi une fois cette
  fiche livrée et utilisée en production.
- Les 10 autres manques identifiés par l'audit du 2026-09-22 (paniers
  voie enquête/investigation, destination finale DAC/DEI/externalisé,
  résumé auto à la clôture, "fiche de traitement", cycle de vie en API,
  personne visée au dépôt initial, récépissé imprimable sur le formulaire
  web, catégories de recommandations, filtres anonyme/motif, fiche
  d'analyse première) — chantiers distincts, traités séparément selon la
  priorisation de l'utilisateur.
