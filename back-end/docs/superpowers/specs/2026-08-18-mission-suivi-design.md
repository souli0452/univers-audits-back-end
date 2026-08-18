# Mission de suivi — Design

## Statut

**Lot 6 — Post-investigation**, découpé en 5 sous-chantiers :

1. Transmission + relance (`TransmissionAutorite` + `RelanceSuites`) — livré et mergé le 2026-08-18
2. Plan d'actions + notes d'avancement (`PlanActions` + `NoteAvancement`) — livré et mergé le 2026-08-18
3. **Mission de suivi** (`MissionSuivi`) ← ce document (en cours)
4. Suivi de la procédure pénale (`SuiviProcedurePenale`) — à venir
5. Constitution de partie civile (`ConstitutionPartieCivile`, art. 58 loi 082-2015) — à venir

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, ligne 373) : « ... Dépôt et
suivi des plans d'actions par les entités contrôlées, notes d'avancement, **missions de suivi** et
rapport structuré (objectifs, synthèse des recommandations appliquées et non appliquées avec
causes, nouvelles recommandations). ... ». Le §7 (délais), ligne 258 : « Mission de suivi des plans
d'actions : **dans l'année suivant l'intervention**. » Ligne 177 (§5, modèle de domaine) confirme
`MissionSuivi` comme entité séparée de `PlanActions`/`NoteAvancement` dans la section « Suivi ».

Ce sous-chantier est la suite directe du sous-chantier 2/5 (`PlanActions`/`NoteAvancement`), livré
juste avant. Trois arbitrages de conception ont été tranchés avec l'utilisateur avant de concevoir :

- **`MissionSuivi` est une liste (0..n)**, pas un dépôt unique — le texte source parle de « missions
  de suivi » au pluriel, et une vérification terrain peut se répéter dans l'année suivant
  l'intervention. Diverge donc de `TransmissionAutorite`/`PlanActions` (dépôt unique), se rapproche
  de `RelanceSuites`/`NoteAvancement` (liste).
- **La création exige qu'un `PlanActions` existe déjà** pour l'investigation — le texte dit
  explicitement « mission de suivi **des plans d'actions** » : on vérifie l'exécution d'un plan, il
  doit donc exister.
- **Rôle d'écriture : `CONTROLEUR_ETAT`** (+ `ADMIN_DDIC`) plutôt que `CGEA`. Le texte de référence
  attribue le suivi des recommandations au DSRAJ (acteur §3), mais ce rôle **n'existe pas** dans le
  système — seuls `ADMIN_DDIC`, `CGE`, `CGEA`, `AGENT_BRPD`, `CONSEILLER_JURIDIQUE`,
  `CONTROLEUR_ETAT`, `MEMBRE_CTADP` sont seedés (`004-create-role-permission-tables.sql`), un gap
  déjà existant, pas propre à ce sous-chantier. `CONTROLEUR_ETAT` est retenu car une mission de
  suivi est une vérification terrain, du même registre qu'une `VisiteTerrain` menée par ce rôle
  (contrairement à `CGEA`, plutôt superviseur/administratif dans le modèle actuel).

## Objectif

- `MissionSuivi` : enregistrement factuel d'une mission de vérification terrain menée après le
  dépôt d'un plan d'actions — qui a mené la mission, quand, et le rapport structuré associé
  (objectifs, synthèse des recommandations appliquées/non appliquées avec causes, nouvelles
  recommandations).
- Plusieurs missions possibles par investigation, chacune un événement daté et immuable.
- Un indicateur de retard (`missionSuiviOverdue`) visible dès que le délai d'un an après le dépôt du
  plan d'actions est dépassé sans qu'aucune mission de suivi n'ait été enregistrée.

## Hors périmètre

- **Rôle DSRAJ** : introduction d'un nouveau rôle dans `role_definition` — hors périmètre de ce
  sous-chantier, substitué par `CONTROLEUR_ETAT` (décision utilisateur, voir Contexte). Si le rôle
  DSRAJ est introduit plus tard (p. ex. au sous-chantier 4/5, `SuiviProcedurePenale`, où DSRAJ est
  également l'acteur naturel), les rôles d'écriture ici devront être revus.
- **Structuration par recommandation individuelle** : `syntheseRecommandations` reste un champ texte
  libre, pas une liste d'objets `{recommandation, appliquee, cause}` — aucune entité
  `Recommandation` individuelle n'existe déjà dans le modèle (`NoteRecommandations.contenu` est
  également un champ texte libre), cohérent avec l'existant.
- **Planification/statut de mission** (`SCHEDULED`/`CONDUCTED`/`CANCELLED` comme `VisiteTerrain`) :
  `MissionSuivi` est un dépôt a posteriori du rapport de mission déjà menée, pas un outil de
  planification. Pas de champ `status`.
- **Nouveau `DossierStatus`** : aucune transition de statut ajoutée, même décision déjà actée pour
  tout ce Lot.
- **Calcul du délai en jours ouvrables réel** (calendrier de jours fériés, alerte J-3, escalade) :
  gap spec connu (plan de travail §7 ligne 260), délibérément reporté à un chantier transversal
  après la fin du Lot 6. `MissionSuivi` utilise le même patron dégradé que `TransmissionAutorite`/
  `PlanActions` (calcul calendaire, `try/catch`/`log.warn`).
- **Mise à jour ou suppression d'une mission** : dépôt factuel immuable, comme les autres entités du
  Lot 6.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Cardinalité | Liste (0..n) rattachée directement à `Investigation` (FK non-unique), pas de parent intermédiaire | Décision utilisateur — le texte source est au pluriel, plusieurs vérifications possibles dans l'année |
| Gate de création | Refusé si `investigation.getCgeApprovedAt() == null` **ou** si aucun `PlanActions` n'existe pour l'investigation | Décision utilisateur — cohérent avec le texte « mission de suivi des plans d'actions » : on vérifie l'exécution d'un plan déjà déposé |
| `GET /missions-suivi` : réponse toujours présente vs liste vide simple | **Divergence assumée, dans la continuité de `PlanActions`** : toujours `200` avec `MissionSuiviListResponse` (`missions: []` si aucune), incluant `missionSuiviDueAt`/`missionSuiviOverdue` | Le délai d'un an doit rester visible même sans mission enregistrée, même raisonnement déjà retenu pour `planActionsDueAt`/`planActionsOverdue` |
| `missionSuiviDueAt`/`missionSuiviOverdue` : calcul | Ancré sur `PlanActions.submittedAt` (pas `investigation.getReportSubmittedAt()`) + délai `MISSION_SUIVI_PLAN_ACTIONS` (365 jours, `jours_ouvrables = FALSE`) ; `overdue` neutralisé dès qu'au moins une mission existe ; si aucun `PlanActions` n'existe encore, `dueAt = null` et `overdue = false` | Le texte ancre explicitement le délai sur « l'intervention » du plan d'actions, pas sur le rapport d'enquête — c'est la date de dépôt du plan qui déclenche le décompte d'un an. `jours_ouvrables = FALSE` car le texte dit « dans l'année », pas « X jours ouvrables » comme les autres lignes du §7 |
| Rôle d'écriture | `CONTROLEUR_ETAT`,`ADMIN_DDIC` | Décision utilisateur (voir Contexte) |
| Rôles lecture | `CGEA`,`CGE`,`CONSEILLER_JURIDIQUE`,`CONTROLEUR_ETAT`,`MEMBRE_CTADP`,`ADMIN_DDIC` | Même ensemble que `PlanActionsController.READ_ROLES`/`TransmissionAutoriteController.READ_ROLES` |
| Masquage confidentialité en lecture | Réponse « vide » (`missions: []`, `missionSuiviDueAt: null`, `missionSuiviOverdue: false`) plutôt qu'une erreur, pour un agent non privilégié sur dossier confidentiel | Cohérent avec `PlanActionsService.getStatus` — la ressource répond toujours, la confidentialité ne change que le contenu retourné |
| Masquage confidentialité en écriture | Appliqué dès la conception (rejet `BusinessException`) | Même leçon déjà appliquée dès le départ aux deux sous-chantiers précédents de ce Lot |
| Emplacement du code | Service concret sans interface, contrôleur dédié nesté sous `/investigations/{id}` | Suit le précédent direct (`PlanActionsService`/`PlanActionsController`) |

## Composants

### 1. Entité

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/MissionSuivi.java`

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mission_suivi", indexes = {
        @Index(name = "idx_mission_suivi_investigation",
                columnList = "investigation_id")
})
public class MissionSuivi extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "mission_date", nullable = false)
    private Instant missionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id", nullable = false)
    private Agent conductedBy;

    @Column(name = "objectifs", nullable = false, columnDefinition = "TEXT")
    private String objectifs;

    @Column(name = "synthese_recommandations", nullable = false, columnDefinition = "TEXT")
    private String syntheseRecommandations;

    @Column(name = "nouvelles_recommandations", columnDefinition = "TEXT")
    private String nouvellesRecommandations;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
```

Pas de FK unique sur `investigation_id` (contrairement à `PlanActions`/`TransmissionAutorite`) :
plusieurs missions par investigation sont attendues. Index explicite sur la FK, même patron que
`VisiteTerrain`.

### 2. Repository

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/MissionSuiviRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.MissionSuivi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MissionSuiviRepository extends JpaRepository<MissionSuivi, UUID> {
    List<MissionSuivi> findByInvestigationIdOrderByMissionDateDesc(UUID investigationId);
}
```

### 3. DTOs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/MissionSuiviRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionSuiviRequest {
    @NotNull(message = "La date de la mission est obligatoire")
    private Instant missionDate;

    @NotBlank(message = "Les objectifs de la mission sont obligatoires")
    private String objectifs;

    @NotBlank(message = "La synthèse des recommandations est obligatoire")
    private String syntheseRecommandations;

    private String nouvellesRecommandations;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MissionSuiviResponse.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionSuiviResponse {
    private UUID id;
    private Instant missionDate;
    private String conductedByNom;
    private String objectifs;
    private String syntheseRecommandations;
    private String nouvellesRecommandations;
    private Instant submittedAt;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/MissionSuiviListResponse.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionSuiviListResponse {
    private UUID investigationId;
    private Instant missionSuiviDueAt;
    private boolean missionSuiviOverdue;
    private List<MissionSuiviResponse> missions;
}
```

`missions` est toujours une liste non-nulle (vide si aucune mission ou masquage confidentiel),
jamais `null` — même garantie que `PlanActionsStatusResponse.avancements`.

### 4. Service `MissionSuiviService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/MissionSuiviService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.MissionSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviListResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.MissionSuiviRepository;
import gov.bf.ascelc.univers_audits.repository.PlanActionsRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionSuiviService {

    private final MissionSuiviRepository missionSuiviRepository;
    private final PlanActionsRepository planActionsRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final ParametreDelaiService parametreDelaiService;

    @Transactional
    public MissionSuiviListResponse ajouter(UUID investigationId, MissionSuiviRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "L'enregistrement d'une mission de suivi n'est possible qu'après la décision finale du CGE.");
        }
        PlanActions planActions = planActionsRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'actions déposé pour cette investigation — "
                                + "impossible d'enregistrer une mission de suivi."));

        Agent agent = agentContextResolver.getCurrentAgent();
        MissionSuivi mission = MissionSuivi.builder()
                .investigation(investigation)
                .missionDate(request.getMissionDate())
                .conductedBy(agent)
                .objectifs(request.getObjectifs())
                .syntheseRecommandations(request.getSyntheseRecommandations())
                .nouvellesRecommandations(request.getNouvellesRecommandations())
                .submittedAt(Instant.now())
                .build();

        missionSuiviRepository.save(mission);
        log.info("Mission de suivi enregistrée — investigation: {}", investigationId);
        return toListResponse(investigation, planActions);
    }

    public MissionSuiviListResponse lister(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return MissionSuiviListResponse.builder()
                    .investigationId(investigationId)
                    .missionSuiviOverdue(false)
                    .missions(List.of())
                    .build();
        }

        Optional<PlanActions> planActions = planActionsRepository.findByInvestigationId(investigationId);
        return toListResponse(investigation, planActions.orElse(null));
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private MissionSuiviListResponse toListResponse(Investigation investigation, PlanActions planActions) {
        List<MissionSuivi> missions =
                missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigation.getId());

        Instant dueAt = planActions != null
                ? resolveDeadline(planActions.getSubmittedAt(), "MISSION_SUIVI_PLAN_ACTIONS")
                : null;
        boolean overdue = missions.isEmpty()
                && dueAt != null
                && Instant.now().isAfter(dueAt);

        return MissionSuiviListResponse.builder()
                .investigationId(investigation.getId())
                .missionSuiviDueAt(dueAt)
                .missionSuiviOverdue(overdue)
                .missions(missions.stream().map(this::toResponse).toList())
                .build();
    }

    private MissionSuiviResponse toResponse(MissionSuivi m) {
        return MissionSuiviResponse.builder()
                .id(m.getId())
                .missionDate(m.getMissionDate())
                .conductedByNom(m.getConductedBy().getNomComplet())
                .objectifs(m.getObjectifs())
                .syntheseRecommandations(m.getSyntheseRecommandations())
                .nouvellesRecommandations(m.getNouvellesRecommandations())
                .submittedAt(m.getSubmittedAt())
                .build();
    }

    private Instant resolveDeadline(Instant from, String delaiCode) {
        try {
            int delaiJours = parametreDelaiService.resolveDelaiJours(delaiCode);
            return from.plusSeconds((long) delaiJours * 24 * 3600);
        } catch (ResourceNotFoundException e) {
            log.warn("Délai {} indisponible — échéance non calculée : {}", delaiCode, e.getMessage());
            return null;
        }
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
```

`toListResponse` est appelée aussi bien depuis `ajouter` (avec le `PlanActions` déjà chargé, jamais
`null` à ce point puisque `ajouter` vient de vérifier son existence) que depuis `lister` (avec
`planActions.orElse(null)`, potentiellement `null`) — c'est la méthode qui porte toute la logique
« échéance calculée vs pas ». Toujours relire la liste des missions depuis le repository dans
`toListResponse` plutôt que de la reconstruire en mémoire, pour rester correct même en cas d'accès
concurrent.

### 5. Contrôleur `MissionSuiviController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/MissionSuiviController.java`

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.MissionSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviListResponse;
import gov.bf.ascelc.univers_audits.service.MissionSuiviService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/missions-suivi")
public class MissionSuiviController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final MissionSuiviService missionSuiviService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<MissionSuiviListResponse> lister(@PathVariable UUID id) {
        return ResponseEntity.ok(missionSuiviService.lister(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<MissionSuiviListResponse> ajouter(
            @PathVariable UUID id,
            @Valid @RequestBody MissionSuiviRequest request) {

        log.info("Enregistrement mission de suivi — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(missionSuiviService.ajouter(id, request));
    }
}
```

Comme `PlanActionsController.getStatus`, `lister` n'a pas de `try/catch` sur
`ResourceNotFoundException` — le service ne lève jamais cette exception pour « absence », il répond
toujours `200` avec `missions: []`.

## Migration

Fichier : `src/main/resources/db/changelog/migrations/037-create-mission-suivi.sql`

Numéro confirmé libre (dernier existant : `036`).

```sql
--liquibase formatted sql
--changeset dev:037-create-mission-suivi

CREATE TABLE mission_suivi (
    id                        UUID         PRIMARY KEY,
    investigation_id         UUID         NOT NULL REFERENCES investigation(id),
    mission_date              TIMESTAMP    NOT NULL,
    conducted_by_id           UUID         NOT NULL REFERENCES agent(id),
    objectifs                 TEXT         NOT NULL,
    synthese_recommandations  TEXT         NOT NULL,
    nouvelles_recommandations TEXT,
    submitted_at               TIMESTAMP    NOT NULL,
    version                    BIGINT       NOT NULL DEFAULT 0,
    created_at                 TIMESTAMP    NOT NULL,
    updated_at                 TIMESTAMP,
    created_by_id              VARCHAR(100),
    updated_by_id               VARCHAR(100)
);

CREATE INDEX idx_mission_suivi_investigation
    ON mission_suivi (investigation_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'MISSION_SUIVI_PLAN_ACTIONS', 'Délai pour mener une mission de suivi après le dépôt du plan d''actions', 365, FALSE, TRUE, 0, now());

COMMENT ON TABLE mission_suivi IS 'Missions de verification terrain de l execution des plans d actions (Lot 6 sous-chantier 3/5) - liste, plusieurs missions possibles par investigation, contrairement a PlanActions/TransmissionAutorite';
```

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| `POST /missions-suivi` alors que `cgeApprovedAt == null` | `BusinessException` |
| `POST /missions-suivi` alors qu'aucun `PlanActions` n'existe | `BusinessException` |
| `POST /missions-suivi` sur dossier confidentiel sans privilège | `BusinessException` (rejet, pas masquage — écriture) |
| `GET /missions-suivi` alors qu'aucune mission n'a encore été enregistrée | `200`, `missions: []`, `missionSuiviDueAt`/`missionSuiviOverdue` calculés si un `PlanActions` existe |
| `GET /missions-suivi` alors qu'aucun `PlanActions` n'existe encore | `200`, `missions: []`, `missionSuiviDueAt: null`, `missionSuiviOverdue: false` |
| `GET /missions-suivi` sur dossier confidentiel sans privilège | `200`, `missions: []`, `missionSuiviOverdue: false` (masquage, ne fuit pas si le délai est dépassé) |
| Délai `MISSION_SUIVI_PLAN_ACTIONS` indisponible | Dégradation silencieuse — `missionSuiviDueAt: null`, `missionSuiviOverdue: false`, jamais d'exception |
| Investigation inexistante | `ResourceNotFoundException` → 404 |

## Tests

- **`MissionSuiviServiceTest`** (nouveau, Mockito, même style que `PlanActionsServiceTest`) :
  - `ajouter_rejetteSiDecisionFinaleNonRendue`
  - `ajouter_rejetteSiAucunPlanActions`
  - `ajouter_rejetteSiDossierConfidentielEtAgentNonPrivilegie`
  - `ajouter_succeedsEtRenseigneConductedByEtSubmittedAt`
  - `ajouter_permetPlusieursMissionsPourLaMemeInvestigation`
  - `lister_listeVideSiAucuneMission`
  - `lister_masqueSiDossierConfidentielEtAgentNonPrivilegie` (vérifie `missions=[]`,
    `missionSuiviOverdue=false`, sans dépendre de la vraie échéance)
  - `lister_leveBusinessExceptionSiAccesRefuse` (`checkReadAccess` échoue avant tout calcul)
  - `lister_dueAtNullSiAucunPlanActions`
  - `missionSuiviOverdue_vraiSiEcheanceDepasseeEtAucuneMission`
  - `missionSuiviOverdue_fauxSiAuMoinsUneMissionExiste` (neutralise le dépassement même si la
    première mission a été enregistrée après l'échéance — même raisonnement que
    `planActionsOverdue_fauxSiPlanDejaDepose`)
  - `missionSuiviOverdue_degradeVersFauxSiParametreIndisponible`

## Risques et points d'attention pour le plan d'implémentation

- **Ordre des tâches** : entité + repository + migration doivent exister avant le service.
- **`toListResponse` accepte `planActions == null`** — bien tester le chemin où aucun `PlanActions`
  n'existe séparément du chemin normal, ce sont deux branches de logique distinctes.
- **`MissionSuiviService` dépend de `PlanActionsRepository`** (pas seulement de son propre
  repository) — vérifier que ce couplage inter-service reste à sens unique (`MissionSuivi` lit
  `PlanActions`, jamais l'inverse).
- **Ne pas copier le patron `try/catch ResourceNotFoundException` du contrôleur
  `TransmissionAutorite`** — `MissionSuiviController.lister` n'en a pas besoin, le service ne lève
  jamais cette exception pour signaler une absence.
- **`findByInvestigationIdOrderByMissionDateDesc` retourne toujours une liste, jamais `null`** —
  Spring Data JPA garantit une liste vide sur absence de résultat, pas de garde nécessaire.
