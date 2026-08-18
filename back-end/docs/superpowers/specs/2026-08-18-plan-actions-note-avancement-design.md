# Plan d'actions et notes d'avancement — Design

## Statut

**Lot 6 — Post-investigation**, découpé en 5 sous-chantiers :

1. Transmission + relance (`TransmissionAutorite` + `RelanceSuites`) — livré et mergé le 2026-08-18
2. **Plan d'actions + notes d'avancement** (`PlanActions` + `NoteAvancement`) ← ce document (en cours)
3. Mission de suivi (`MissionSuivi`) — à venir
4. Suivi de la procédure pénale (`SuiviProcedurePenale`) — à venir
5. Constitution de partie civile (`ConstitutionPartieCivile`, art. 58 loi 082-2015) — à venir

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, ligne 373) : « Dépôt et
suivi des plans d'actions par les entités contrôlées, notes d'avancement, missions de suivi et
rapport structuré (...) ». Le §7 (délais) précise : « Plan d'actions de l'entité contrôlée : 20
jours ouvrables après note de recommandations. » Aucun référentiel d'institutions structuré
n'existe dans ce dépôt (gap Lot 0 déjà noté) — même situation déjà rencontrée pour
`TransmissionAutorite.autoriteDestinataire`.

Ce sous-chantier est la suite directe du sous-chantier 1/5 (`TransmissionAutorite`), livré juste
avant. Trois arbitrages de conception ont été tranchés avec l'utilisateur avant de concevoir :

- **Le délai de 20 jours est suivi**, y compris un indicateur de retard visible *avant même* que
  l'entité contrôlée ait déposé son plan (contrairement au délai CGE 10-vs-20j, resté ouvert au
  sous-chantier précédent).
- **`PlanActions` est un dépôt unique et non modifiable**, même patron que `TransmissionAutorite` —
  toute évolution dans le temps passe par `NoteAvancement`, jamais par une révision du plan
  lui-même.
- **Gate de création identique à `TransmissionAutorite`** : `investigation.getCgeApprovedAt() !=
  null` (décision finale rendue) — un seul point de référence déjà établi dans ce Lot, plutôt que
  le texte littéral « après note de recommandations » qui aurait introduit un second point
  d'ancrage.

## Objectif

- `PlanActions` : enregistrement factuel, unique par investigation, du dépôt du plan d'actions par
  l'entité contrôlée — qui, quand, par quel agent ASCE-LC la réception a été enregistrée.
- `NoteAvancement` : liste de notes de suivi rattachées au plan, permettant de tracer son
  exécution dans le temps.
- Un indicateur de retard (`planActionsOverdue`) visible dès que le délai de 20 jours ouvrables
  est dépassé, que le plan ait été déposé ou non.

## Hors périmètre

- **Révision du plan lui-même** : décision actée, un seul dépôt par investigation.
- **Référentiel structuré des entités contrôlées** : `entiteControlee` reste un champ texte libre,
  même choix déjà fait pour `TransmissionAutorite.autoriteDestinataire`.
- **Nouveau `DossierStatus`** : aucune transition de statut ajoutée, même décision déjà actée pour
  tout ce Lot (les sous-étapes vivent sur des champs d'entité dédiés).
- **Escalade automatique / notification à échéance** : même limite déjà actée pour
  `RelanceSuites.relanceOverdue` — l'indicateur reste informatif, exposé dans la réponse API, pas
  de tâche planifiée.
- **Cadence obligatoire des notes d'avancement** : le texte source ne donne aucun délai pour la
  fréquence des `NoteAvancement` (contrairement au dépôt initial du plan) — pas d'échéance calculée
  sur cette liste.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| `PlanActions` : structure | Dépôt factuel unique, comme `TransmissionAutorite` — `entiteControlee` + `contenu` (`@NotBlank` tous les deux) posés à la création, `submittedAt`/`receivedBy` automatiques | Décision utilisateur : pas de révision, un événement daté avec contenu obligatoire (contrairement à `RequeteParquet`, brouillon progressif sans `@NotBlank`) |
| `NoteAvancement` : entité séparée ou liste rattachée | Liste rattachée (`@OneToMany` sur `PlanActions`), pas d'entité 1:1, pas de repository dédié | Même patron exact que `RelanceSuites` sur `TransmissionAutorite` |
| Gate de création | Refusé si `investigation.getCgeApprovedAt() == null` | Décision utilisateur — cohérence avec `TransmissionAutorite`, un seul point de référence pour « la décision est définitive » dans ce Lot |
| `GET /plan-actions` : réponse toujours présente vs `204`/`404` sur absence | **Divergence assumée** : toujours `200` avec `PlanActionsStatusResponse` (`exists: boolean`), jamais `204`/`404` | Le délai de 20 jours doit rester visible avant même le dépôt — un `204` ne peut pas porter `planActionsDueAt`/`planActionsOverdue`. Documenté explicitement pour qu'un futur implémenteur ne le prenne pas pour un oubli du patron `RequeteParquet`/`TransmissionAutorite` |
| `planActionsDueAt`/`planActionsOverdue` : calcul | Ancré sur `investigation.getReportSubmittedAt()` (pas sur `NoteRecommandations` elle-même) + délai `PLAN_ACTIONS_ENTITE_CONTROLEE` (20j) ; `overdue` neutralisé dès que `PlanActions` existe | Même ancrage déjà utilisé pour `cjRevueDeadline` (sous-chantier 3/4, circuit de validation) — `reportSubmittedAt` est le moment où `NoteRecommandations` devient définitive en pratique (`submitReport()` exige sa complétude) |
| Masquage confidentialité en lecture | Réponse « vide » (`exists: false`, aucun contenu, `planActionsOverdue: false`) plutôt qu'une erreur, pour un agent non privilégié sur dossier confidentiel | Cohérent avec le patron déjà établi pour les ressources de type liste/statut (`InventairePieces`, `ChecklistDossierTravailService.getChecklist`) — `getStatus` répond toujours, la confidentialité ne change que le contenu retourné |
| Masquage confidentialité en écriture | Appliqué dès la conception à `creer` ET `ajouterAvancement` (rejet `BusinessException`) | Même leçon déjà appliquée dès le départ au sous-chantier précédent (`TransmissionAutorite`), après le trou corrigé après coup au sous-chantier Lot 5 4/4 |
| Rôles écriture | `CGEA`,`ADMIN_DDIC` (distinct de `TransmissionAutorite`, qui est `CGE`,`ADMIN_DDIC`) | Décision utilisateur : le suivi des plans d'actions est un travail administratif de suivi, cohérent avec le rôle déjà donné au CGEA sur les étapes de suivi ailleurs dans ce Lot |
| Rôles lecture | `CGEA`,`CGE`,`CONSEILLER_JURIDIQUE`,`CONTROLEUR_ETAT`,`MEMBRE_CTADP`,`ADMIN_DDIC` | Même ensemble que `TransmissionAutoriteController.READ_ROLES` |
| Emplacement du code | Service concret sans interface, contrôleur dédié nesté sous `/investigations/{id}` | Suit le précédent direct (`TransmissionAutoriteService`/`TransmissionAutoriteController`) |

## Composants

### 1. Entités

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/PlanActions.java`

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "plan_actions")
public class PlanActions extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "entite_controlee", nullable = false, length = 300)
    private String entiteControlee;

    @Column(name = "contenu", nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_id", nullable = false)
    private Agent receivedBy;

    @OneToMany(mappedBy = "planActions",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<NoteAvancement> avancements = new ArrayList<>();
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/NoteAvancement.java`

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
@Table(name = "note_avancement")
public class NoteAvancement extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_actions_id", nullable = false)
    private PlanActions planActions;

    @Column(name = "note_at", nullable = false)
    private Instant noteAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;
}
```

### 2. Repository

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/PlanActionsRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PlanActions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanActionsRepository extends JpaRepository<PlanActions, UUID> {
    Optional<PlanActions> findByInvestigationId(UUID investigationId);
}
```

Pas de repository pour `NoteAvancement` — accédée uniquement via `PlanActions.avancements`, même
patron que `RelanceSuites`.

### 3. DTOs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PlanActionsRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanActionsRequest {
    @NotBlank(message = "L'entité contrôlée est obligatoire")
    private String entiteControlee;

    @NotBlank(message = "Le contenu du plan d'actions est obligatoire")
    private String contenu;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/NoteAvancementRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteAvancementRequest {
    private String contenu;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/NoteAvancementResponse.java`

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
public class NoteAvancementResponse {
    private UUID id;
    private Instant noteAt;
    private String agentNom;
    private String contenu;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PlanActionsStatusResponse.java`

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
public class PlanActionsStatusResponse {
    private UUID investigationId;
    private boolean exists;
    private Instant planActionsDueAt;
    private boolean planActionsOverdue;
    private UUID id;
    private String entiteControlee;
    private String contenu;
    private Instant submittedAt;
    private String receivedByNom;
    private List<NoteAvancementResponse> avancements;
}
```

`avancements` est toujours une liste non-nulle (vide si `exists == false` ou masquage
confidentiel), jamais `null` — évite un `NullPointerException` côté consommateur de l'API.

### 4. Service `PlanActionsService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/PlanActionsService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteAvancementRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanActionsRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.NoteAvancementResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanActionsStatusResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
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
public class PlanActionsService {

    private final PlanActionsRepository planActionsRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final ParametreDelaiService parametreDelaiService;

    @Transactional
    public PlanActionsStatusResponse creer(UUID investigationId, PlanActionsRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "Le dépôt du plan d'actions n'est possible qu'après la décision finale du CGE.");
        }
        if (planActionsRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Un plan d'actions a déjà été déposé pour ce dossier.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        PlanActions planActions = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee(request.getEntiteControlee())
                .contenu(request.getContenu())
                .submittedAt(Instant.now())
                .receivedBy(agent)
                .build();

        PlanActions saved = planActionsRepository.save(planActions);
        log.info("Plan d'actions enregistré — investigation: {}", investigationId);
        return toResponse(investigation, saved);
    }

    public PlanActionsStatusResponse getStatus(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return PlanActionsStatusResponse.builder()
                    .investigationId(investigationId)
                    .exists(false)
                    .planActionsOverdue(false)
                    .avancements(List.of())
                    .build();
        }

        Optional<PlanActions> planActions = planActionsRepository.findByInvestigationId(investigationId);
        return toResponse(investigation, planActions.orElse(null));
    }

    @Transactional
    public PlanActionsStatusResponse ajouterAvancement(UUID investigationId, NoteAvancementRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        PlanActions planActions = planActionsRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'actions déposé pour cette investigation — "
                                + "impossible d'ajouter une note d'avancement."));

        Agent agent = agentContextResolver.getCurrentAgent();
        NoteAvancement note = NoteAvancement.builder()
                .planActions(planActions)
                .noteAt(Instant.now())
                .agent(agent)
                .contenu(request.getContenu())
                .build();
        planActions.getAvancements().add(note);

        PlanActions saved = planActionsRepository.save(planActions);
        log.info("Note d'avancement ajoutée — investigation: {}", investigationId);
        return toResponse(investigation, saved);
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private PlanActionsStatusResponse toResponse(Investigation investigation, PlanActions planActions) {
        Instant dueAt = investigation.getReportSubmittedAt() != null
                ? resolveDeadline(investigation.getReportSubmittedAt(), "PLAN_ACTIONS_ENTITE_CONTROLEE")
                : null;
        boolean overdue = planActions == null
                && dueAt != null
                && Instant.now().isAfter(dueAt);

        PlanActionsStatusResponse.PlanActionsStatusResponseBuilder builder =
                PlanActionsStatusResponse.builder()
                        .investigationId(investigation.getId())
                        .exists(planActions != null)
                        .planActionsDueAt(dueAt)
                        .planActionsOverdue(overdue)
                        .avancements(List.of());

        if (planActions != null) {
            builder.id(planActions.getId())
                    .entiteControlee(planActions.getEntiteControlee())
                    .contenu(planActions.getContenu())
                    .submittedAt(planActions.getSubmittedAt())
                    .receivedByNom(planActions.getReceivedBy().getNomComplet())
                    .avancements(planActions.getAvancements().stream()
                            .map(this::toAvancementResponse)
                            .toList());
        }

        return builder.build();
    }

    private NoteAvancementResponse toAvancementResponse(NoteAvancement n) {
        return NoteAvancementResponse.builder()
                .id(n.getId())
                .noteAt(n.getNoteAt())
                .agentNom(n.getAgent().getNomComplet())
                .contenu(n.getContenu())
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

`toResponse` est appelée aussi bien avec un `PlanActions` réel (`creer`/`ajouterAvancement`,
jamais `null`) qu'avec `null` (`getStatus` quand rien n'existe) — c'est la méthode qui porte toute
la logique « exists vs pas ». `investigation.getReportSubmittedAt()` peut être `null` en théorie
(investigation jamais soumise) même si en pratique `cgeApprovedAt != null` implique déjà que
`submitReport()` a été appelé — la garde `!= null` reste défensive, cohérente avec le style
dégradé déjà en place dans ce Lot.

### 5. Contrôleur `PlanActionsController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/PlanActionsController.java`

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteAvancementRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanActionsRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanActionsStatusResponse;
import gov.bf.ascelc.univers_audits.service.PlanActionsService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/plan-actions")
public class PlanActionsController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CGEA','ADMIN_DDIC')";

    private final PlanActionsService planActionsService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<PlanActionsStatusResponse> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(planActionsService.getStatus(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PlanActionsStatusResponse> creerPlanActions(
            @PathVariable UUID id,
            @Valid @RequestBody PlanActionsRequest request) {

        log.info("Enregistrement plan d'actions — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planActionsService.creer(id, request));
    }

    @PostMapping("/avancements")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PlanActionsStatusResponse> ajouterAvancement(
            @PathVariable UUID id,
            @Valid @RequestBody NoteAvancementRequest request) {

        log.info("Ajout d'une note d'avancement — investigation {}", id);
        return ResponseEntity.ok(planActionsService.ajouterAvancement(id, request));
    }
}
```

Contrairement à `TransmissionAutoriteController.getTransmission`, `getStatus` n'a pas de
`try/catch` sur `ResourceNotFoundException` — le service ne lève jamais cette exception pour
« absence », il répond toujours `200` avec `exists=false`.

## Migration

Fichier : `src/main/resources/db/changelog/migrations/036-create-plan-actions.sql`

Numéro confirmé libre (dernier existant : `035`).

```sql
--liquibase formatted sql
--changeset dev:036-create-plan-actions

CREATE TABLE plan_actions (
    id                UUID         PRIMARY KEY,
    investigation_id  UUID         NOT NULL UNIQUE REFERENCES investigation(id),
    entite_controlee  VARCHAR(300) NOT NULL,
    contenu           TEXT         NOT NULL,
    submitted_at      TIMESTAMP    NOT NULL,
    received_by_id    UUID         NOT NULL REFERENCES agent(id),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE TABLE note_avancement (
    id              UUID      PRIMARY KEY,
    plan_actions_id UUID      NOT NULL REFERENCES plan_actions(id),
    note_at         TIMESTAMP NOT NULL,
    agent_id        UUID      NOT NULL REFERENCES agent(id),
    contenu         TEXT,
    version         BIGINT    NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by_id   VARCHAR(100),
    updated_by_id   VARCHAR(100)
);

CREATE INDEX idx_note_avancement_plan_actions
    ON note_avancement (plan_actions_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'PLAN_ACTIONS_ENTITE_CONTROLEE', 'Délai de dépôt du plan d''actions par l''entité contrôlée après note de recommandations', 20, TRUE, TRUE, 0, now());

COMMENT ON TABLE plan_actions IS 'Depot du plan d actions par l entite controlee (Lot 6 sous-chantier 2/5) - evenement factuel, une seule par investigation, contrairement a TransmissionAutorite le delai de depot est suivi via planActionsOverdue meme avant depot';
COMMENT ON TABLE note_avancement IS 'Notes de suivi de l execution du plan d actions - rattachees a un plan_actions';
```

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| `POST /plan-actions` alors que `cgeApprovedAt == null` | `BusinessException` |
| `POST /plan-actions` alors qu'un plan existe déjà | `BusinessException` |
| `POST /plan-actions` ou `POST .../avancements` sur dossier confidentiel sans privilège | `BusinessException` (rejet, pas masquage — écriture) |
| `POST .../avancements` alors qu'aucun plan n'existe | `BusinessException` |
| `GET /plan-actions` alors que rien n'a encore été déposé | `200`, `exists: false`, `planActionsDueAt`/`planActionsOverdue` calculés normalement |
| `GET /plan-actions` sur dossier confidentiel sans privilège | `200`, `exists: false`, `planActionsOverdue: false` (masquage, ne fuit pas si le délai est dépassé) |
| Délai `PLAN_ACTIONS_ENTITE_CONTROLEE` indisponible | Dégradation silencieuse — `planActionsDueAt: null`, `planActionsOverdue: false`, jamais d'exception |
| Investigation inexistante | `ResourceNotFoundException` → 404 |

## Tests

- **`PlanActionsServiceTest`** (nouveau, Mockito, même style que `TransmissionAutoriteServiceTest`) :
  - `creer_rejetteSiDecisionFinaleNonRendue`
  - `creer_rejetteSiPlanDejaExistant`
  - `creer_rejetteSiDossierConfidentielEtAgentNonPrivilegie`
  - `creer_succeedsEtRenseigneSubmittedAtEtReceivedBy`
  - `getStatus_existsFauxSiAucunPlan`
  - `getStatus_masqueSiDossierConfidentielEtAgentNonPrivilegie` (vérifie `exists=false`,
    `planActionsOverdue=false`, sans dépendre de la vraie échéance)
  - `getStatus_leveBusinessExceptionSiAccesRefuse` (`checkReadAccess` échoue avant tout calcul)
  - `ajouterAvancement_rejetteSiAucunPlan`
  - `ajouterAvancement_rejetteSiDossierConfidentielEtAgentNonPrivilegie`
  - `ajouterAvancement_ajouteALaListeExistante`
  - `planActionsOverdue_vraiSiEcheanceDepasseeEtAucunPlanDepose`
  - `planActionsOverdue_fauxSiPlanDejaDepose` (neutralise le dépassement même si la date réelle
    du dépôt est postérieure à l'échéance — le dépôt tardif reste visible via `submittedAt`, mais
    `overdue` ne s'applique qu'à l'absence de dépôt)
  - `planActionsOverdue_degradeVersFauxSiParametreIndisponible`

## Risques et points d'attention pour le plan d'implémentation

- **Ordre des tâches** : entité + repository + migration doivent exister avant le service.
- **`toResponse` accepte `null`** — bien tester le chemin `planActions == null` séparément du
  chemin avec un `PlanActions` réel, ce sont deux branches de logique distinctes dans la même
  méthode.
- **Ne pas copier le patron `try/catch ResourceNotFoundException` du contrôleur précédent** —
  `PlanActionsController.getStatus` n'en a pas besoin, le service ne lève jamais cette exception
  pour signaler une absence.
