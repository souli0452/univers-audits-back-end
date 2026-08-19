# Constitution de partie civile — Design

## Statut

**Lot 6 — Post-investigation**, découpé en 5 sous-chantiers :

1. Transmission + relance (`TransmissionAutorite` + `RelanceSuites`) — livré et mergé
2. Plan d'actions + notes d'avancement (`PlanActions` + `NoteAvancement`) — livré et mergé
3. Mission de suivi (`MissionSuivi`) — livré et mergé
4. Suivi de la procédure pénale (`SuiviProcedurePenale`) — livré et mergé
5. **Constitution de partie civile** (`ConstitutionPartieCivile`, art. 58 loi 082-2015) ← ce document
   (en cours) — **dernier sous-chantier du Lot 6**

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, ligne 373) : « ... **Constitution
de partie civile au nom de l'État.** » C'est la seule phrase source pour ce sous-chantier. Ligne 177
(§5, modèle de domaine) confirme `ConstitutionPartieCivile` comme entité séparée de
`SuiviProcedurePenale`, avec la référence « art. 58 loi 082-2015 ». Le contenu de cet article n'est
cité nulle part dans le texte source fourni — il apparaît uniquement en §2.1 dans la liste des textes
juridiques de référence embarqués dans les visas des documents générés (table `TexteJuridique`, qui
n'existe pas encore dans ce dépôt — gap Lot 0 déjà noté, même situation déjà rencontrée pour les
autres sous-chantiers du Lot 6).

Contrairement à `SuiviProcedurePenale` (suivi chronologique de plusieurs phases dans le temps), le
texte source désigne ici un **acte juridique unique et formel** : le moment où l'ASCE-LC se constitue
partie civile au nom de l'État. Trois arbitrages de conception ont été tranchés avec l'utilisateur
avant de concevoir :

- **Cardinalité 1:1**, comme `TransmissionAutorite`/`PlanActions` — pas une liste comme
  `MissionSuivi`/`SuiviProcedurePenale`. On se constitue partie civile une fois, pas plusieurs fois ;
  c'est le sens juridique usuel du terme.
- **Gate de création : `cgeApprovedAt != null` seul**, même point de référence que le reste du Lot 6 —
  pas de dépendance à `SuiviProcedurePenale`, que rien dans le texte source ne mandate explicitement.
- **Rôle d'écriture : `CGE`** (+ `ADMIN_DDIC`) — un acte « au nom de l'État » est une décision de plus
  haut niveau qu'un suivi de routine, cohérent avec le rôle déjà donné au CGE pour les actes formels
  engageant l'institution (`TransmissionAutorite`, décisions en dernier ressort).

**Conséquence directe de ce choix de rôle sur le correctif transversal en attente** : `CGE` appartient
déjà à l'ensemble privilégié de `DossierAccessGuard.canSeeConfidential()` (= `CGE`, `CGEA`,
`ADMIN_DDIC`). Ce sous-chantier **ne reproduit donc pas** l'incohérence de masquage relevée sur
`MissionSuivi`/`SuiviProcedurePenale` (où le rôle substitut DSRAJ n'était pas privilégié) — le patron
classique de `TransmissionAutorite` (écriture rejetée par `BusinessException`, lecture masquée par
`ResourceNotFoundException` → 204) s'applique ici sans divergence. Le correctif transversal identifié
au sous-chantier 4/5 reste circonscrit à `MissionSuiviService`/`SuiviProcedurePenaleService`.

## Objectif

- `ConstitutionPartieCivile` : enregistrement factuel, unique par investigation, de l'acte par lequel
  l'ASCE-LC se constitue partie civile au nom de l'État — qui, quand, avec quelle justification et
  quel montant réclamé (s'il est déjà connu).

## Hors périmètre

- **Révision de l'acte** : décision actée, un seul dépôt par investigation, comme
  `TransmissionAutorite`/`PlanActions`.
- **Référentiel structuré des textes juridiques** (`TexteJuridique`, art. 58) : gap Lot 0 déjà noté
  pour tout ce Lot — la référence légale reste une constante documentaire, pas une donnée du modèle.
- **Suivi du montant effectivement obtenu** (par opposition au montant réclamé) : hors périmètre,
  rattaché conceptuellement au suivi de la procédure pénale (`SuiviProcedurePenale`), pas à l'acte de
  constitution lui-même.
- **Nouveau `DossierStatus`** : aucune transition de statut ajoutée, même décision déjà actée pour
  tout ce Lot.
- **Indicateur d'échéance/`overdue`** : aucun délai n'est associé à cet acte dans le §7 du plan de
  travail — pas de `ParametreDelaiService`, pas de champ `dueAt`/`overdue`.
- **Calcul du délai en jours ouvrables réel** : sans objet ici (aucun délai), et le chantier
  transversal jours-ouvrables reste de toute façon reporté après la fin complète du Lot 6.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Cardinalité | Entrée unique (1:1), FK `investigation_id` NOT NULL UNIQUE, même patron que `TransmissionAutorite`/`PlanActions` | Décision utilisateur — sens juridique usuel du terme, contrairement à `MissionSuivi`/`SuiviProcedurePenale` |
| Gate de création | Refusé si `investigation.getCgeApprovedAt() == null`. Pas de dépendance à `SuiviProcedurePenale` | Décision utilisateur — cohérent avec le reste du Lot, rien dans le texte source ne mandate cette dépendance |
| Rôle d'écriture | `CGE`,`ADMIN_DDIC` | Décision utilisateur — acte « au nom de l'État » de plus haut niveau, cohérent avec le rôle déjà donné au CGE pour `TransmissionAutorite` |
| Rôles lecture | `CGEA`,`CGE`,`CONSEILLER_JURIDIQUE`,`CONTROLEUR_ETAT`,`MEMBRE_CTADP`,`ADMIN_DDIC` | Même ensemble que tous les contrôleurs sœurs du Lot 6 |
| Masquage confidentialité | Patron classique `TransmissionAutorite` (écriture `BusinessException`, lecture `ResourceNotFoundException` → 204) — **pas** la divergence checkReadAccess-seul de `MissionSuivi`/`SuiviProcedurePenale` | `CGE` est déjà dans l'ensemble privilégié `canSeeConfidential()` — la divergence transversale n'a pas lieu d'être ici, le patron classique suffit et reste cohérent avec le sous-chantier 1/5 (même rôle d'écriture) |
| `montantReclame` | `BigDecimal`, nullable, `precision=15, scale=2` | Précédent direct : `Dossier.estimatedLoss` (même type, même précision) ; nullable car le montant n'est pas toujours chiffrable au moment de l'acte |
| `justification` | TEXT, `@NotBlank` | Un acte formel « au nom de l'État » nécessite une motivation écrite, cohérent avec `PlanActions.contenu` (`@NotBlank`) |
| Emplacement du code | Service concret sans interface, contrôleur dédié nesté sous `/investigations/{id}` | Suit le précédent direct (`TransmissionAutoriteService`/`TransmissionAutoriteController`) |

## Composants

### 1. Entité

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/ConstitutionPartieCivile.java`

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "constitution_partie_civile")
public class ConstitutionPartieCivile extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "constitue_at", nullable = false)
    private Instant constitueAt;

    @Column(name = "montant_reclame", precision = 15, scale = 2)
    private BigDecimal montantReclame;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "constituee_par_id", nullable = false)
    private Agent constitueePar;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
```

### 2. Repository

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/ConstitutionPartieCivileRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.ConstitutionPartieCivile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConstitutionPartieCivileRepository extends JpaRepository<ConstitutionPartieCivile, UUID> {
    Optional<ConstitutionPartieCivile> findByInvestigationId(UUID investigationId);
}
```

### 3. DTOs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ConstitutionPartieCivileRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstitutionPartieCivileRequest {
    @NotBlank(message = "La justification est obligatoire")
    private String justification;

    private BigDecimal montantReclame;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/ConstitutionPartieCivileResponse.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstitutionPartieCivileResponse {
    private UUID id;
    private UUID investigationId;
    private Instant constitueAt;
    private BigDecimal montantReclame;
    private String justification;
    private String constitueeParNom;
    private Instant submittedAt;
}
```

### 4. Service `ConstitutionPartieCivileService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/ConstitutionPartieCivileService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ConstitutionPartieCivileRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ConstitutionPartieCivileResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.ConstitutionPartieCivileRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConstitutionPartieCivileService {

    private final ConstitutionPartieCivileRepository constitutionPartieCivileRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public ConstitutionPartieCivileResponse creer(UUID investigationId, ConstitutionPartieCivileRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "La constitution de partie civile n'est possible qu'après la décision finale du CGE.");
        }
        if (constitutionPartieCivileRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "L'ASCE-LC s'est déjà constituée partie civile pour ce dossier.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        ConstitutionPartieCivile constitution = ConstitutionPartieCivile.builder()
                .investigation(investigation)
                .constitueAt(Instant.now())
                .montantReclame(request.getMontantReclame())
                .justification(request.getJustification())
                .constitueePar(agent)
                .submittedAt(Instant.now())
                .build();

        ConstitutionPartieCivile saved = constitutionPartieCivileRepository.save(constitution);
        log.info("Constitution de partie civile enregistrée — investigation: {}", investigationId);
        return toResponse(saved);
    }

    public ConstitutionPartieCivileResponse getOrThrow(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new ResourceNotFoundException(
                    "Aucune constitution de partie civile enregistrée pour cette investigation : " + investigationId);
        }

        ConstitutionPartieCivile constitution = constitutionPartieCivileRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune constitution de partie civile enregistrée pour cette investigation : " + investigationId));
        return toResponse(constitution);
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private ConstitutionPartieCivileResponse toResponse(ConstitutionPartieCivile c) {
        return ConstitutionPartieCivileResponse.builder()
                .id(c.getId())
                .investigationId(c.getInvestigation().getId())
                .constitueAt(c.getConstitueAt())
                .montantReclame(c.getMontantReclame())
                .justification(c.getJustification())
                .constitueeParNom(c.getConstitueePar().getNomComplet())
                .submittedAt(c.getSubmittedAt())
                .build();
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
```

`checkNotConfidentialMasked` (écriture) rejette explicitement, comme `TransmissionAutorite`/
`PlanActions` — cohérent puisque `CGE` est déjà privilégié, la divergence checkReadAccess-seul des
sous-chantiers 3/5-4/5 ne s'applique pas ici (voir Contexte).

### 5. Contrôleur `ConstitutionPartieCivileController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/ConstitutionPartieCivileController.java`

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ConstitutionPartieCivileRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ConstitutionPartieCivileResponse;
import gov.bf.ascelc.univers_audits.service.ConstitutionPartieCivileService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/constitution-partie-civile")
public class ConstitutionPartieCivileController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CGE','ADMIN_DDIC')";

    private final ConstitutionPartieCivileService constitutionPartieCivileService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<ConstitutionPartieCivileResponse> getConstitution(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(constitutionPartieCivileService.getOrThrow(id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ConstitutionPartieCivileResponse> creerConstitution(
            @PathVariable UUID id,
            @Valid @RequestBody ConstitutionPartieCivileRequest request) {

        log.info("Enregistrement constitution de partie civile — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(constitutionPartieCivileService.creer(id, request));
    }
}
```

`getConstitution` fait un `try/catch ResourceNotFoundException` → 204, comme
`TransmissionAutoriteController.getTransmission` — **pas** la divergence always-200 des
sous-chantiers 2/5-3/5-4/5 (qui n'ont de sens que pour exposer une échéance avant dépôt ; sans objet
ici puisqu'il n'y a aucun délai).

## Migration

Fichier : `src/main/resources/db/changelog/migrations/039-create-constitution-partie-civile.sql`

Numéro confirmé libre (dernier existant : `038`).

```sql
--liquibase formatted sql
--changeset dev:039-create-constitution-partie-civile

CREATE TABLE constitution_partie_civile (
    id                UUID           PRIMARY KEY,
    investigation_id  UUID           NOT NULL UNIQUE REFERENCES investigation(id),
    constitue_at      TIMESTAMP      NOT NULL,
    montant_reclame   NUMERIC(15,2),
    justification     TEXT           NOT NULL,
    constituee_par_id UUID           NOT NULL REFERENCES agent(id),
    submitted_at      TIMESTAMP      NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

COMMENT ON TABLE constitution_partie_civile IS 'Acte de constitution de partie civile au nom de l Etat (Lot 6 sous-chantier 5/5, dernier du Lot 6) - evenement factuel unique par investigation, art. 58 loi organique 082-2015';
```

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| `POST /constitution-partie-civile` alors que `cgeApprovedAt == null` | `BusinessException` |
| `POST /constitution-partie-civile` alors qu'une constitution existe déjà | `BusinessException` |
| `POST /constitution-partie-civile` sur dossier confidentiel sans privilège | `BusinessException` (rejet, pas masquage — écriture) |
| `GET /constitution-partie-civile` alors qu'aucune constitution n'existe | `204 No Content` |
| `GET /constitution-partie-civile` sur dossier confidentiel sans privilège | `204 No Content` (masquage — `ResourceNotFoundException` interceptée) |
| Investigation inexistante | `ResourceNotFoundException` → 404 |

## Tests

- **`ConstitutionPartieCivileServiceTest`** (nouveau, Mockito, même style que `TransmissionAutoriteServiceTest`) :
  - `creer_rejetteSiDecisionFinaleNonRendue`
  - `creer_rejetteSiConstitutionDejaExistante`
  - `creer_rejetteSiDossierConfidentielEtAgentNonPrivilegie`
  - `creer_succeedsEtRenseigneConstitueAtEtConstitueePar` (avec assertion sur `getInvestigation()`,
    leçon du sous-chantier 3/5)
  - `creer_succeedsSansMontantReclame` (montant nullable, la constitution reste valide sans lui)
  - `getOrThrow_leveResourceNotFoundExceptionSiAucuneConstitution`
  - `getOrThrow_masqueSiDossierConfidentielEtAgentNonPrivilegie`
  - `getOrThrow_leveBusinessExceptionSiAccesRefuse` (`checkReadAccess` échoue avant tout calcul)

## Risques et points d'attention pour le plan d'implémentation

- **Ordre des tâches** : entité + repository + migration doivent exister avant le service.
- **Ne pas ajouter d'indicateur d'échéance/`overdue`** — cette entité n'a volontairement aucun délai
  (contrairement à `PlanActions`/`MissionSuivi`), un implémenteur qui a vu ces deux précédents
  pourrait en ajouter un par réflexe.
- **Ne pas retirer `checkNotConfidentialMasked`** — contrairement à `MissionSuivi`/
  `SuiviProcedurePenale` (sous-chantiers immédiatement précédents), ce service garde le patron
  classique de rejet en écriture, car `CGE` est déjà privilégié. Un implémenteur qui vient d'implémenter
  4/5 pourrait retirer cette garde par réflexe de cohérence avec le sous-chantier précédent — ce
  serait une régression, pas un alignement.
- **`montantReclame` reste nullable** — ne pas ajouter `@NotNull`/`@DecimalMin` par réflexe de
  rigueur : le montant n'est souvent pas connu au moment de l'acte.
