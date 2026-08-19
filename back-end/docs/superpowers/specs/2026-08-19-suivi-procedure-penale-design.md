# Suivi de la procédure pénale — Design

## Statut

**Lot 6 — Post-investigation**, découpé en 5 sous-chantiers :

1. Transmission + relance (`TransmissionAutorite` + `RelanceSuites`) — livré et mergé
2. Plan d'actions + notes d'avancement (`PlanActions` + `NoteAvancement`) — livré et mergé
3. Mission de suivi (`MissionSuivi`) — livré et mergé
4. **Suivi de la procédure pénale** (`SuiviProcedurePenale`) ← ce document (en cours)
5. Constitution de partie civile (`ConstitutionPartieCivile`, art. 58 loi 082-2015) — à venir

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, ligne 373) : « ... **Suivi de
la procédure pénale à toutes ses phases.** Constitution de partie civile au nom de l'État. » C'est
la **seule** phrase source pour ce sous-chantier — extrêmement mince, aucune énumération des
« phases » attendues. Ligne 177 (§5, modèle de domaine) confirme `SuiviProcedurePenale` comme
entité séparée de `ConstitutionPartieCivile` dans la section « Suivi ». Ligne 106 (§3, acteurs) :
« DSRAJ | Propose la saisine judiciaire, **suit les recommandations et les actions en justice**. »
Ligne 257 (§7, délais) : « Suivi DSRAJ : rapport trimestriel » — trop imprécis pour en dériver une
échéance fiable (voir Hors périmètre).

`RequeteParquet` (Lot 5) documente déjà, dans son propre Javadoc, que « la transmission effective au
Parquet (changement de statut, notification) n'est pas modélisée ici — hors périmètre de ce
sous-chantier, appartient à un futur Lot (Post-investigation) ». Ce sous-chantier 4/5 est ce futur
lot : il ne modélise pas la transmission elle-même, mais le suivi de ce qu'il advient de la
procédure une fois enclenchée, quelle que soit la voie (Parquet via `RequeteParquet`, ou autorité
judiciaire via `TransmissionAutorite`).

Trois arbitrages de conception ont été tranchés avec l'utilisateur avant de concevoir :

- **`SuiviProcedurePenale` est une liste (0..n)**, comme `MissionSuivi` — le texte source dit « à
  toutes ses phases » (plusieurs étapes dans le temps), et aucun enum juridique de phases n'est
  vérifiable dans le texte source burkinabè fourni : inventer une liste fermée serait un risque
  juridique non maîtrisé. Chaque entrée est un événement daté, texte libre pour le libellé de
  phase.
- **Gate de création : `cgeApprovedAt != null` uniquement**, pas de dépendance à `RequeteParquet` —
  contrairement à `MissionSuivi` (qui exige un `PlanActions`), un dossier peut suivre une voie
  purement judiciaire sans jamais passer par une requête au Parquet rédigée dans ce système (ex.
  soit-transmis direct, commission rogatoire), donc lier la gate à `RequeteParquet` exclurait des
  cas légitimes.
- **Rôle d'écriture : `CONSEILLER_JURIDIQUE`** (+ `ADMIN_DDIC`), différent du substitut
  `CONTROLEUR_ETAT` retenu pour `MissionSuivi` (vérification terrain). Le suivi de procédure
  pénale est une fonction juridique/judiciaire, prolongement naturel du rôle déjà attribué à
  `CONSEILLER_JURIDIQUE` dans ce dépôt (revue du rapport, rédaction de la requête au Parquet,
  Lot 5) — plus proche de DSRAJ fonctionnellement que `CGEA` ou `ADMIN_DDIC` seul.

**Arbitrage transversal déjà tranché (2026-08-19), appliqué ici dès la conception** : l'écriture
n'est masquée que par `checkReadAccess` (habilitation nominative) — pas de rejet confidentiel
supplémentaire pour un agent habilité, même patron que `VisiteTerrainServiceImpl`/
`MissionSuiviService` (corrigé après-coup sur ce dernier, appliqué ici dès le départ). La lecture
(`lister`) reste masquée par `canSeeConfidential()`.

## Objectif

- `SuiviProcedurePenale` : liste d'événements datés traçant l'évolution d'une procédure pénale
  (qui a enregistré la mise à jour, quand, quelle phase, avec un commentaire libre).
- Plusieurs entrées possibles par investigation, chacune un événement daté et immuable.

## Hors périmètre

- **Enum fermé de phases juridiques** : `phase` reste un champ texte libre, même choix déjà fait
  pour `TransmissionAutorite.autoriteDestinataire` — aucun référentiel de phases de procédure
  pénale n'existe dans ce dépôt, et le texte source ne les énumère pas.
- **Indicateur d'échéance/`overdue`** : contrairement à `PlanActions`/`MissionSuivi`, aucun délai
  précis n'est attaché à cette liste dans le §7. « Suivi DSRAJ : rapport trimestriel » (ligne 257)
  est trop imprécis pour en dériver une échéance fiable (rapport trimestriel de qui, sur quoi,
  ancré sur quelle date ?) — même limite déjà actée pour la cadence des `NoteAvancement`
  (sous-chantier 2/5 : « le texte source ne donne aucun délai pour la fréquence... pas d'échéance
  calculée sur cette liste »).
- **Lien structurel avec `RequeteParquet`/`TransmissionAutorite`** : `SuiviProcedurePenale` se
  rattache directement à `Investigation`, pas à l'une ou l'autre de ces entités — un dossier peut
  avoir une procédure pénale suivie sans `RequeteParquet` rédigée dans ce système (gate décidée
  ci-dessus).
- **Nouveau `DossierStatus`** : aucune transition de statut ajoutée, même décision déjà actée pour
  tout ce Lot.
- **Calcul du délai en jours ouvrables réel** : sans objet ici, cette entité n'a pas de délai (voir
  ci-dessus) — et le chantier transversal jours-ouvrables reste de toute façon reporté après la fin
  du Lot 6 (arbitrage 2026-08-19).
- **Mise à jour ou suppression d'une entrée** : dépôt factuel immuable, comme le reste du Lot 6.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Cardinalité | Liste (0..n) rattachée directement à `Investigation` (FK non-unique), même patron que `MissionSuivi` | Décision utilisateur — « à toutes ses phases » implique plusieurs étapes dans le temps |
| Gate de création | Refusé si `investigation.getCgeApprovedAt() == null`. **Pas** de dépendance à `RequeteParquet` | Décision utilisateur — un dossier peut suivre une voie judiciaire sans requête au Parquet rédigée dans ce système |
| Champ `phase` | Texte libre (`@NotBlank`, 300 car. max) | Décision utilisateur — aucun référentiel de phases juridiques vérifiable dans le texte source |
| `commentaire` | Texte libre, nullable | Précisions optionnelles, même patron que `RelanceSuites.contenu`/`NoteAvancement.contenu` |
| Indicateur d'échéance | Aucun — liste chronologique simple, pas de `dueAt`/`overdue` | Aucun délai exploitable dans le §7 pour cette entité spécifique |
| Rôle d'écriture | `CONSEILLER_JURIDIQUE`,`ADMIN_DDIC` | Décision utilisateur — fonction juridique/judiciaire, prolongement du rôle déjà attribué à `CONSEILLER_JURIDIQUE` (revue de rapport, requête au Parquet) |
| Rôles lecture | `CGEA`,`CGE`,`CONSEILLER_JURIDIQUE`,`CONTROLEUR_ETAT`,`MEMBRE_CTADP`,`ADMIN_DDIC` | Même ensemble que les contrôleurs sœurs du Lot 6 |
| Masquage confidentialité en écriture | **Aucun rejet supplémentaire** — `checkReadAccess` seul suffit | Arbitrage transversal tranché le 2026-08-19, appliqué ici dès la conception (voir Contexte) |
| Masquage confidentialité en lecture | Liste vide (`suivis: []`) plutôt qu'une erreur, pour un agent non privilégié sur dossier confidentiel | Cohérent avec `MissionSuiviService.lister`/`VisiteTerrainServiceImpl.findByInvestigationId` |
| Emplacement du code | Service concret sans interface, contrôleur dédié nesté sous `/investigations/{id}` | Suit le précédent direct (`MissionSuiviService`/`MissionSuiviController`) |

## Composants

### 1. Entité

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/SuiviProcedurePenale.java`

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
@Table(name = "suivi_procedure_penale", indexes = {
        @Index(name = "idx_suivi_procedure_penale_investigation",
                columnList = "investigation_id")
})
public class SuiviProcedurePenale extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "phase_at", nullable = false)
    private Instant phaseAt;

    @Column(name = "phase", nullable = false, length = 300)
    private String phase;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
```

Pas de FK unique sur `investigation_id` (liste 0..n, même patron que `MissionSuivi`). Index
explicite sur la FK.

### 2. Repository

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/SuiviProcedurePenaleRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SuiviProcedurePenale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SuiviProcedurePenaleRepository extends JpaRepository<SuiviProcedurePenale, UUID> {
    List<SuiviProcedurePenale> findByInvestigationIdOrderByPhaseAtDesc(UUID investigationId);
}
```

### 3. DTOs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/SuiviProcedurePenaleRequest.java`

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
public class SuiviProcedurePenaleRequest {
    @NotNull(message = "La date de la phase est obligatoire")
    private Instant phaseAt;

    @NotBlank(message = "La phase est obligatoire")
    private String phase;

    private String commentaire;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SuiviProcedurePenaleResponse.java`

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
public class SuiviProcedurePenaleResponse {
    private UUID id;
    private Instant phaseAt;
    private String phase;
    private String commentaire;
    private String agentNom;
    private Instant submittedAt;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SuiviProcedurePenaleListResponse.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuiviProcedurePenaleListResponse {
    private UUID investigationId;
    private List<SuiviProcedurePenaleResponse> suivis;
}
```

`suivis` est toujours une liste non-nulle (vide si aucune entrée ou masquage confidentiel), jamais
`null`.

### 4. Service `SuiviProcedurePenaleService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/SuiviProcedurePenaleService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.SuiviProcedurePenaleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SuiviProcedurePenaleListResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.SuiviProcedurePenaleResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.SuiviProcedurePenaleRepository;
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
public class SuiviProcedurePenaleService {

    private final SuiviProcedurePenaleRepository suiviProcedurePenaleRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public SuiviProcedurePenaleListResponse ajouter(UUID investigationId, SuiviProcedurePenaleRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "L'enregistrement d'un suivi de procédure pénale n'est possible qu'après la décision finale du CGE.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        SuiviProcedurePenale suivi = SuiviProcedurePenale.builder()
                .investigation(investigation)
                .phaseAt(request.getPhaseAt())
                .phase(request.getPhase())
                .commentaire(request.getCommentaire())
                .agent(agent)
                .submittedAt(Instant.now())
                .build();

        suiviProcedurePenaleRepository.save(suivi);
        log.info("Suivi de procédure pénale enregistré — investigation: {}", investigationId);
        return toListResponse(investigation);
    }

    public SuiviProcedurePenaleListResponse lister(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return SuiviProcedurePenaleListResponse.builder()
                    .investigationId(investigationId)
                    .suivis(List.of())
                    .build();
        }

        return toListResponse(investigation);
    }

    private SuiviProcedurePenaleListResponse toListResponse(Investigation investigation) {
        List<SuiviProcedurePenale> suivis =
                suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigation.getId());

        return SuiviProcedurePenaleListResponse.builder()
                .investigationId(investigation.getId())
                .suivis(suivis.stream().map(this::toResponse).toList())
                .build();
    }

    private SuiviProcedurePenaleResponse toResponse(SuiviProcedurePenale s) {
        return SuiviProcedurePenaleResponse.builder()
                .id(s.getId())
                .phaseAt(s.getPhaseAt())
                .phase(s.getPhase())
                .commentaire(s.getCommentaire())
                .agentNom(s.getAgent().getNomComplet())
                .submittedAt(s.getSubmittedAt())
                .build();
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
```

Pas de `ParametreDelaiService` injecté — aucun délai à résoudre pour cette entité (voir Hors
périmètre). `ajouter` n'appelle volontairement **pas** de méthode de masquage confidentiel
supplémentaire après `checkReadAccess` — c'est l'arbitrage transversal du 2026-08-19, pas un oubli.

### 5. Contrôleur `SuiviProcedurePenaleController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/SuiviProcedurePenaleController.java`

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.SuiviProcedurePenaleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SuiviProcedurePenaleListResponse;
import gov.bf.ascelc.univers_audits.service.SuiviProcedurePenaleService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/suivi-procedure-penale")
public class SuiviProcedurePenaleController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')";

    private final SuiviProcedurePenaleService suiviProcedurePenaleService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<SuiviProcedurePenaleListResponse> lister(@PathVariable UUID id) {
        return ResponseEntity.ok(suiviProcedurePenaleService.lister(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<SuiviProcedurePenaleListResponse> ajouter(
            @PathVariable UUID id,
            @Valid @RequestBody SuiviProcedurePenaleRequest request) {

        log.info("Enregistrement suivi de procédure pénale — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suiviProcedurePenaleService.ajouter(id, request));
    }
}
```

## Migration

Fichier : `src/main/resources/db/changelog/migrations/038-create-suivi-procedure-penale.sql`

Numéro confirmé libre (dernier existant : `037`).

```sql
--liquibase formatted sql
--changeset dev:038-create-suivi-procedure-penale

CREATE TABLE suivi_procedure_penale (
    id                UUID         PRIMARY KEY,
    investigation_id  UUID         NOT NULL REFERENCES investigation(id),
    phase_at          TIMESTAMP    NOT NULL,
    phase             VARCHAR(300) NOT NULL,
    commentaire       TEXT,
    agent_id          UUID         NOT NULL REFERENCES agent(id),
    submitted_at      TIMESTAMP    NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE INDEX idx_suivi_procedure_penale_investigation
    ON suivi_procedure_penale (investigation_id);

COMMENT ON TABLE suivi_procedure_penale IS 'Suivi chronologique des phases de la procedure penale (Lot 6 sous-chantier 4/5) - liste, plusieurs entrees possibles par investigation, pas de gate sur RequeteParquet (un dossier peut suivre une voie judiciaire sans requete au Parquet redigee dans ce systeme)';
```

Aucun nouveau code `ParametreDelai` — cette entité n'a pas de délai (voir Hors périmètre).

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| `POST /suivi-procedure-penale` alors que `cgeApprovedAt == null` | `BusinessException` |
| `POST /suivi-procedure-penale` sur dossier confidentiel, agent habilité (`checkReadAccess` passe) | **Autorisé** — pas de rejet confidentiel supplémentaire (arbitrage 2026-08-19) |
| `POST /suivi-procedure-penale` sur dossier auquel l'agent n'est pas assigné | `BusinessException` (`checkReadAccess` échoue) |
| `GET /suivi-procedure-penale` alors qu'aucune entrée n'existe | `200`, `suivis: []` |
| `GET /suivi-procedure-penale` sur dossier confidentiel sans privilège | `200`, `suivis: []` (masquage, lecture reste masquée) |
| Investigation inexistante | `ResourceNotFoundException` → 404 |

## Tests

- **`SuiviProcedurePenaleServiceTest`** (nouveau, Mockito, même style que `MissionSuiviServiceTest`) :
  - `ajouter_rejetteSiDecisionFinaleNonRendue`
  - `ajouter_succeedsSurDossierConfidentielSiAgentHabilite` (vérifie explicitement
    `verify(accessGuard, never()).canSeeConfidential()` en écriture — même garde-fou que le
    correctif appliqué a posteriori sur `MissionSuiviService`, à faire correct dès le départ ici)
  - `ajouter_succeedsEtRenseigneAgentEtSubmittedAt`
  - `ajouter_permetPlusieursEntreesPourLaMemeInvestigation` (avec assertion sur
    `m.getInvestigation()`, pas seulement la taille de la liste — leçon du sous-chantier 3/5 où ce
    garde-fou manquait initialement)
  - `ajouter_neDependPasDeRequeteParquet` (une investigation sans `RequeteParquet` peut recevoir un
    suivi de procédure pénale — verrouille l'absence de gate sur cette entité)
  - `lister_listeVideSiAucuneEntree`
  - `lister_masqueSiDossierConfidentielEtAgentNonPrivilegie`
  - `lister_leveBusinessExceptionSiAccesRefuse`

## Risques et points d'attention pour le plan d'implémentation

- **Ordre des tâches** : entité + repository + migration doivent exister avant le service.
- **Ne pas ajouter de `checkNotConfidentialMasked` en écriture** — c'est l'arbitrage transversal du
  2026-08-19, un implémenteur qui a vu le patron `PlanActions`/`TransmissionAutorite` pourrait le
  réintroduire par réflexe. Le test `ajouter_succeedsSurDossierConfidentielSiAgentHabilite` est le
  garde-fou contre cette régression.
- **Ne pas dépendre de `RequeteParquet`** — même remarque, un implémenteur qui a vu le patron
  `MissionSuivi` (qui exige `PlanActions`) pourrait reproduire une gate similaire par réflexe. Le
  test `ajouter_neDependPasDeRequeteParquet` couvre cette régression.
- **Ne pas ajouter de `ParametreDelaiService`** ni de champ `dueAt`/`overdue` — cette entité n'a
  volontairement aucun délai (voir Hors périmètre).
