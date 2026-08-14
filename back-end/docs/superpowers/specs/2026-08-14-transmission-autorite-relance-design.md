# Transmission aux autorités et relance des suites — Design

## Statut

**Lot 6 — Post-investigation**, découpé en 5 sous-chantiers (le texte source liste 7 entités,
regroupées par proximité fonctionnelle) :

1. **Transmission + relance** (`TransmissionAutorite` + `RelanceSuites`) ← ce document (en cours)
2. Plan d'actions + notes d'avancement (`PlanActions` + `NoteAvancement`) — à venir
3. Mission de suivi (`MissionSuivi`) — à venir
4. Suivi de la procédure pénale (`SuiviProcedurePenale`) — à venir
5. Constitution de partie civile (`ConstitutionPartieCivile`, art. 58 loi 082-2015) — à venir

Lot 6 était entièrement absent du code avant ce chantier (confirmé par grep). Il démarre juste
après le Lot 5 (jalon pilote), lui-même entièrement terminé et mergé.

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, ligne 373) : « Transmission
aux autorités administratives et judiciaires par le CGE via son secrétariat et le DSRAJ. Relance
formelle à 30 jours. » Le §7 (délais) précise : « Relance des suites données par les autorités :
30 jours après transmission. » Aucun rôle DSRAJ dédié n'existe dans ce dépôt (confirmé par grep sur
tous les `hasAnyRole` des contrôleurs) — même situation déjà actée pour DEI ailleurs dans ce Lot.

Ce sous-chantier est la suite directe du Lot 5 sous-chantier 4/4 (`RequeteParquet`), qui laissait
explicitement « la transmission effective au Parquet » hors périmètre, la renvoyant à un « futur
Lot (Post-investigation) ». C'est ce Lot.

## Objectif

- `TransmissionAutorite` : enregistrement factuel, unique par investigation, de la transmission
  du dossier décidé (`RequeteParquet` pour une saisine judiciaire, ou le dossier de décision pour
  une sanction administrative) à l'autorité destinataire — qui, quand, par qui.
- `RelanceSuites` : liste de relances formelles envoyées si l'autorité ne répond pas, rattachée à
  la transmission. Échéance informative à J+30, même patron dégradé déjà établi dans ce Lot
  (`ParametreDelaiService` + try/catch, jamais d'exception, jamais de scheduler).

## Hors périmètre

- **Référentiel structuré des institutions destinataires** : `Dossier.autreInstitutionNom`/
  `autreInstitutionAdresse` (Lot 1) utilisent déjà un champ texte libre faute de référentiel —
  même choix ici pour `autoriteDestinataire`, pas d'invention d'un `Institution` structuré (gap
  déjà noté comme Lot 0, non comblé).
- **Nouveau `DossierStatus`** : le texte source (§6) décrit une transition
  `APPROBATION_CGE -> TRANSMIS_AUTORITES -> SUIVI_POST_RAPPORT -> CLOTURE`, mais ce dépôt a déjà
  tranché, pour tout ce Lot, que le `DossierStatus` réel reste plus grossier que le texte littéral
  — les sous-étapes vivent sur des champs d'entité dédiés, jamais sur de nouveaux statuts (décision
  actée au Lot 3, sous-chantier « gate-equipe-plan-valide »). Le statut du dossier reste
  `DECISION_RENDUE` ; la clôture finale (`close()`, déjà existante) n'est pas modifiée par ce
  sous-chantier.
- **Escalade automatique / notification à J+30** : ce dépôt n'a que 2 `@Scheduled` (dans
  `NotificationServiceImpl`, sans rapport). L'échéance de relance reste informative, exposée dans
  la réponse API — pas de tâche planifiée, pas d'email automatique. Mêmes limites déjà actées pour
  `DemandeDocuments`/`PlanInvestigation`/le circuit de validation.
- **Suivi DSRAJ trimestriel** (§7) : c'est un rapport agrégé multi-dossiers, relève du Lot 7
  (Reporting et pilotage), pas de ce sous-chantier.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| `TransmissionAutorite` : structure | Entité factuelle, pas de brouillon éditable — `autoriteDestinataire` renseigné à la création, `transmittedAt`/`transmittedBy` posés automatiquement | Contrairement à `RapportEnquete`/`RequeteParquet` (rédaction progressive), une transmission est un événement daté, pas un document qu'on édite |
| Création : `POST` pas `PUT` | Une seule création possible par investigation (contrainte unique en base), refusée si une transmission existe déjà | Mirroir du patron déjà utilisé pour les actions de création pure dans ce dépôt (ex. `SeanceCtadpController.addDossier`), pas d'upsert car il n'y a rien à mettre à jour après coup |
| Gate de création | Refusé si `investigation.getCgeApprovedAt() == null` (décision finale CGE pas encore rendue) | Même verrou que celui qui vient de figer `RequeteParquet` — la transmission ne peut logiquement suivre que la décision qu'elle transmet |
| `RelanceSuites` : entité séparée ou liste rattachée | Liste rattachée (`@OneToMany` sur `TransmissionAutorite`), pas d'entité 1:1 | Le texte source permet plusieurs relances dans le temps (« relance formelle » peut se répéter tant que l'autorité ne répond pas) — une liste, pas un singleton |
| Échéance de relance | Calculée à la volée (`transmittedAt` + délai `RELANCE_SUITES_TRANSMISSION` = 30j), `overdue = true` seulement si aucune relance n'a encore été envoyée | Même patron que les échéances du circuit de validation (Lot 5, 3/4) — une relance déjà envoyée neutralise le dépassement, comme une approbation neutralise une échéance de circuit |
| Masquage confidentialité en écriture | Appliqué dès la conception à `creerTransmission` ET `ajouterRelance` (pas seulement en lecture) | Leçon tirée de la revue finale du sous-chantier précédent (Lot 5, 4/4) : un garde de lecture seul avait laissé une écriture aveugle possible sur dossier confidentiel — corrigé après coup là-bas, appliqué dès le départ ici |
| Rôles écriture | `CGE`,`ADMIN_DDIC` | « Transmission par le CGE via son secrétariat » — pas de rôle DSRAJ dédié (confirmé par grep, même approximation déjà actée pour DEI) |
| Emplacement du code | Service concret sans interface, contrôleur dédié nesté sous `/investigations/{id}` | Suit le précédent le plus proche (`RequeteParquetService`/`RequeteParquetController`) |

## Composants

### 1. Entités

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/TransmissionAutorite.java`

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
@Table(name = "transmission_autorite")
public class TransmissionAutorite extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "autorite_destinataire", nullable = false, length = 300)
    private String autoriteDestinataire;

    @Column(name = "transmitted_at", nullable = false)
    private Instant transmittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transmitted_by_id", nullable = false)
    private Agent transmittedBy;

    @OneToMany(mappedBy = "transmissionAutorite",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<RelanceSuites> relances = new ArrayList<>();
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/RelanceSuites.java`

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
@Table(name = "relance_suites")
public class RelanceSuites extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transmission_autorite_id", nullable = false)
    private TransmissionAutorite transmissionAutorite;

    @Column(name = "relance_at", nullable = false)
    private Instant relanceAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;
}
```

### 2. Repositories

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/TransmissionAutoriteRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.TransmissionAutorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransmissionAutoriteRepository extends JpaRepository<TransmissionAutorite, UUID> {
    Optional<TransmissionAutorite> findByInvestigationId(UUID investigationId);
}
```

Pas de repository dédié pour `RelanceSuites` : accédée uniquement via `TransmissionAutorite.relances`
(collection déjà chargée), même patron que `SeanceCtadpDossier` (pas de repository séparé, géré via
le parent).

### 3. DTOs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/TransmissionAutoriteRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransmissionAutoriteRequest {
    @NotBlank(message = "L'autorité destinataire est obligatoire")
    private String autoriteDestinataire;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RelanceSuitesRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelanceSuitesRequest {
    private String contenu;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RelanceSuitesResponse.java`

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
public class RelanceSuitesResponse {
    private UUID id;
    private Instant relanceAt;
    private String agentNom;
    private String contenu;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/TransmissionAutoriteResponse.java`

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
public class TransmissionAutoriteResponse {
    private UUID id;
    private UUID investigationId;
    private String autoriteDestinataire;
    private Instant transmittedAt;
    private String transmittedByNom;
    private Instant relanceDueAt;
    private Boolean relanceOverdue;
    private List<RelanceSuitesResponse> relances;
}
```

`autoriteDestinataire` porte `@NotBlank` (contrairement à `RequeteParquetRequest.contenu`) : une
transmission est un événement daté, jamais un brouillon partiel — il n'y a pas de notion de
« transmission incomplète » à sauvegarder. `RelanceSuitesRequest.contenu` reste optionnel, une
relance minimale (juste l'horodatage + l'agent) reste un événement valide.

### 4. Service `TransmissionAutoriteService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/TransmissionAutoriteService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.RelanceSuitesRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TransmissionAutoriteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.RelanceSuitesResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.TransmissionAutoriteResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TransmissionAutoriteRepository;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransmissionAutoriteService {

    private final TransmissionAutoriteRepository transmissionAutoriteRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final ParametreDelaiService parametreDelaiService;

    @Transactional
    public TransmissionAutoriteResponse creer(UUID investigationId, TransmissionAutoriteRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "La transmission n'est possible qu'après la décision finale du CGE.");
        }
        if (transmissionAutoriteRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Ce dossier a déjà été transmis à une autorité.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire(request.getAutoriteDestinataire())
                .transmittedAt(Instant.now())
                .transmittedBy(agent)
                .build();

        TransmissionAutorite saved = transmissionAutoriteRepository.save(transmission);
        log.info("Transmission à l'autorité enregistrée — investigation: {}", investigationId);
        return toResponse(saved);
    }

    public TransmissionAutoriteResponse getOrThrow(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new ResourceNotFoundException(
                    "Aucune transmission enregistrée pour cette investigation : " + investigationId);
        }

        TransmissionAutorite transmission = transmissionAutoriteRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune transmission enregistrée pour cette investigation : " + investigationId));
        return toResponse(transmission);
    }

    @Transactional
    public TransmissionAutoriteResponse ajouterRelance(UUID investigationId, RelanceSuitesRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        TransmissionAutorite transmission = transmissionAutoriteRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucune transmission enregistrée pour cette investigation — "
                                + "impossible d'ajouter une relance."));

        Agent agent = agentContextResolver.getCurrentAgent();
        RelanceSuites relance = RelanceSuites.builder()
                .transmissionAutorite(transmission)
                .relanceAt(Instant.now())
                .agent(agent)
                .contenu(request.getContenu())
                .build();
        transmission.getRelances().add(relance);

        TransmissionAutorite saved = transmissionAutoriteRepository.save(transmission);
        log.info("Relance ajoutée — investigation: {}", investigationId);
        return toResponse(saved);
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private TransmissionAutoriteResponse toResponse(TransmissionAutorite t) {
        Instant relanceDueAt = resolveDeadline(t.getTransmittedAt(), "RELANCE_SUITES_TRANSMISSION");
        boolean relanceOverdue = relanceDueAt != null
                && t.getRelances().isEmpty()
                && Instant.now().isAfter(relanceDueAt);

        return TransmissionAutoriteResponse.builder()
                .id(t.getId())
                .investigationId(t.getInvestigation().getId())
                .autoriteDestinataire(t.getAutoriteDestinataire())
                .transmittedAt(t.getTransmittedAt())
                .transmittedByNom(t.getTransmittedBy().getNomComplet())
                .relanceDueAt(relanceDueAt)
                .relanceOverdue(relanceOverdue)
                .relances(t.getRelances().stream().map(this::toRelanceResponse).toList())
                .build();
    }

    private RelanceSuitesResponse toRelanceResponse(RelanceSuites r) {
        return RelanceSuitesResponse.builder()
                .id(r.getId())
                .relanceAt(r.getRelanceAt())
                .agentNom(r.getAgent().getNomComplet())
                .contenu(r.getContenu())
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

Contrairement à `RequeteParquetService`/`ChecklistDossierTravailService`, ce service construit
directement le DTO `*Response` (pas seulement l'entité) — nécessaire ici parce que
`relanceDueAt`/`relanceOverdue` sont calculés côté service, pas dans le contrôleur, pour rester
cohérent avec le patron déjà établi (calcul d'échéance uniquement en service, jamais au
contrôleur).

### 5. Contrôleur `TransmissionAutoriteController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/TransmissionAutoriteController.java`

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.RelanceSuitesRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TransmissionAutoriteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.TransmissionAutoriteResponse;
import gov.bf.ascelc.univers_audits.service.TransmissionAutoriteService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/transmission-autorite")
public class TransmissionAutoriteController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CGE','ADMIN_DDIC')";

    private final TransmissionAutoriteService transmissionAutoriteService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<TransmissionAutoriteResponse> getTransmission(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(transmissionAutoriteService.getOrThrow(id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<TransmissionAutoriteResponse> creerTransmission(
            @PathVariable UUID id,
            @Valid @RequestBody TransmissionAutoriteRequest request) {

        log.info("Enregistrement transmission à l'autorité — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transmissionAutoriteService.creer(id, request));
    }

    @PostMapping("/relances")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<TransmissionAutoriteResponse> ajouterRelance(
            @PathVariable UUID id,
            @Valid @RequestBody RelanceSuitesRequest request) {

        log.info("Ajout d'une relance — investigation {}", id);
        return ResponseEntity.ok(transmissionAutoriteService.ajouterRelance(id, request));
    }
}
```

## Migration

Fichier : `src/main/resources/db/changelog/migrations/035-create-transmission-autorite.sql`

Numéro confirmé libre (dernier existant : `034`).

```sql
--liquibase formatted sql
--changeset dev:035-create-transmission-autorite

CREATE TABLE transmission_autorite (
    id                    UUID         PRIMARY KEY,
    investigation_id      UUID         NOT NULL UNIQUE REFERENCES investigation(id),
    autorite_destinataire VARCHAR(300) NOT NULL,
    transmitted_at        TIMESTAMP    NOT NULL,
    transmitted_by_id     UUID         NOT NULL REFERENCES agent(id),
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP,
    created_by_id         VARCHAR(100),
    updated_by_id         VARCHAR(100)
);

CREATE TABLE relance_suites (
    id                       UUID      PRIMARY KEY,
    transmission_autorite_id UUID      NOT NULL REFERENCES transmission_autorite(id),
    relance_at               TIMESTAMP NOT NULL,
    agent_id                 UUID      NOT NULL REFERENCES agent(id),
    contenu                  TEXT,
    version                  BIGINT    NOT NULL DEFAULT 0,
    created_at               TIMESTAMP NOT NULL,
    updated_at               TIMESTAMP,
    created_by_id            VARCHAR(100),
    updated_by_id            VARCHAR(100)
);

CREATE INDEX idx_relance_suites_transmission
    ON relance_suites (transmission_autorite_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'RELANCE_SUITES_TRANSMISSION', 'Délai avant relance formelle des suites données par l''autorité', 30, TRUE, TRUE, 0, now());

COMMENT ON TABLE transmission_autorite IS 'Transmission du dossier decide a l autorite competente (Lot 6 sous-chantier 1/5) - evenement factuel, une seule par investigation';
COMMENT ON TABLE relance_suites IS 'Relances formelles envoyees si l autorite destinataire ne repond pas - rattachees a une transmission_autorite';
```

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| `POST /transmission-autorite` alors que `cgeApprovedAt == null` | `BusinessException` |
| `POST /transmission-autorite` alors qu'une transmission existe déjà | `BusinessException` |
| `POST /transmission-autorite` ou `POST .../relances` sur dossier confidentiel sans privilège | `BusinessException` (rejet, pas masquage — c'est une écriture) |
| `POST .../relances` alors qu'aucune transmission n'existe | `BusinessException` |
| `GET /transmission-autorite` alors que rien n'a encore été transmis | `204 No Content` |
| `GET /transmission-autorite` sur dossier confidentiel sans privilège | `204 No Content` (masquage) |
| Délai `RELANCE_SUITES_TRANSMISSION` indisponible | Dégradation silencieuse — `relanceDueAt`/`relanceOverdue` à `null`, jamais d'exception |
| Investigation inexistante | `ResourceNotFoundException` → 404 |

## Tests

- **`TransmissionAutoriteServiceTest`** (nouveau, Mockito) :
  - `creer_rejetteSiDecisionFinaleNonRendue`
  - `creer_rejetteSiTransmissionDejaExistante`
  - `creer_rejetteSiDossierConfidentielEtAgentNonPrivilegie`
  - `creer_succeedsEtRenseigneTransmittedAtEtTransmittedBy`
  - `getOrThrow_leveResourceNotFoundExceptionSiAucuneTransmission`
  - `getOrThrow_leveResourceNotFoundExceptionSiDossierConfidentielEtAgentNonPrivilegie`
  - `ajouterRelance_rejetteSiAucuneTransmission`
  - `ajouterRelance_rejetteSiDossierConfidentielEtAgentNonPrivilegie`
  - `ajouterRelance_ajouteALaListeExistante`
  - `relanceOverdue_vraiSiEcheanceDepasseeEtAucuneRelanceEnvoyee`
  - `relanceOverdue_fauxSiUneRelanceDejaEnvoyee` (neutralise le dépassement, même logique que
    l'échéance du circuit de validation)
  - `relanceOverdue_degradeVersNullSiParametreIndisponible`

## Risques et points d'attention pour le plan d'implémentation

- **Ordre des tâches** : entités + repository + migration doivent exister avant le service.
- **`RelanceSuites` n'a pas de repository dédié** — accédée uniquement via
  `TransmissionAutorite.relances`, chargée en même temps que son parent (`@OneToMany` sans
  `FetchType.LAZY` explicite différent — le défaut `LAZY` s'applique, mais la collection est
  systématiquement consommée dans `toResponse` donc toujours chargée dans le contexte
  transactionnel du service, cohérent avec `@Transactional`/`@Transactional(readOnly = true)`
  déjà en place).
- **Masquage confidentialité en écriture appliqué dès la conception** (pas seulement en lecture)
  — ne pas régresser vers le patron incomplet du sous-chantier précédent qui avait dû être
  corrigé après coup.
