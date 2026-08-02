# Séance CTADP (Lot 2, sous-chantier 2/4) — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modéliser la séance hebdomadaire du CTADP (§11 du plan de travail) comme une entité
de premier niveau regroupant plusieurs dossiers, avec leur recommandation individuelle
(`DecisionCTADP`), sans déclencher aucune transition automatique du statut des dossiers.

**Architecture:** Deux entités liées, `SeanceCTADP` (1) — `SeanceCtadpDossier` (n), même patron
que `Investigation`/`InvestigationMember` déjà présent dans ce dépôt : contrôleur/service/
mapper dédiés de premier niveau (pas nesté sous `/dossiers/{id}`), collection `@OneToMany`
avec `cascade = ALL, orphanRemoval = true`.

**Tech Stack:** Spring Boot 3 / Java 17, MapStruct, Liquibase, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Migration Liquibase `017-add-seance-ctadp.sql`, deux tables `seance_ctadp` et
  `seance_ctadp_dossier`, contrainte `UNIQUE(seance_ctadp_id, dossier_id)` sur la seconde.
- Colonnes héritées de `AuditEntity` : `id` (UUID PK), `version` (BIGINT NOT NULL), `created_at`/
  `updated_at` (TIMESTAMP, `created_at` NOT NULL), `created_by_id`/`updated_by_id`
  (VARCHAR(100)) — mêmes types que la migration 016, ne pas redériver.
- Ajouter une soumission d'un dossier à une séance (`POST .../dossiers`) : rejette si
  `dossier.getStatus() != DossierStatus.EN_REVUE_CTADP`, si la séance n'est pas `PLANIFIEE`, ou
  si le dossier est déjà présent à l'ordre du jour de cette séance.
- Enregistrer une recommandation (`PUT .../dossiers/{dossierId}`) : **aucune** vérification du
  statut courant du dossier — voir spec §3.
- Marquer la séance `TENUE` (`PATCH .../tenir`) : rejette si la séance n'est pas `PLANIFIEE`.
- Aucun `DossierAccessGuard.checkReadAccess` sur les écritures — rôles déjà privilégiés
  (CGEA/CONSEILLER_JURIDIQUE/ADMIN_DDIC), voir spec §3.
- Enregistrer une recommandation ne déclenche **aucune** transition de `DossierStatus` — hors
  périmètre, sous-chantier `DecisionCGE` à venir.

Spec de référence : `docs/superpowers/specs/2026-08-02-seance-ctadp-design.md`
Document source : `docs/reference/plan-de-travail-asce-lc.md` (§3, §5, §6, §7, §11)

---

### Task 1: Enums, entités, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/StatutSeanceCtadp.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/RecommandationCtadp.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/SeanceCTADP.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/SeanceCtadpDossier.java`
- Create: `src/main/resources/db/changelog/migrations/017-add-seance-ctadp.sql`

- [ ] **Step 1: Créer les deux enums**

`StatutSeanceCtadp.java` :

```java
package gov.bf.ascelc.univers_audits.enums;

public enum StatutSeanceCtadp {
    PLANIFIEE,
    TENUE,
    ANNULEE
}
```

`RecommandationCtadp.java` :

```java
package gov.bf.ascelc.univers_audits.enums;

public enum RecommandationCtadp {
    VALIDATION_INVESTIGATION,
    CLASSEMENT,
    TRANSMISSION_INSTITUTION_PARTENAIRE,
    ORIENTATION_ADMINISTRATIVE
}
```

- [ ] **Step 2: Créer l'entité `SeanceCTADP`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
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
@Table(name = "seance_ctadp")
public class SeanceCTADP extends AuditEntity {

    @Column(name = "date_seance", nullable = false)
    private Instant dateSeance;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private StatutSeanceCtadp statut = StatutSeanceCtadp.PLANIFIEE;

    @Column(name = "participants", length = 2000)
    private String participants;

    @Column(name = "proces_verbal", length = 5000)
    private String procesVerbal;

    @OneToMany(mappedBy = "seanceCtadp",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<SeanceCtadpDossier> dossiers = new ArrayList<>();
}
```

- [ ] **Step 3: Créer l'entité `SeanceCtadpDossier`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "seance_ctadp_dossier", indexes = {
        @Index(name = "idx_seance_ctadp_dossier_seance",
                columnList = "seance_ctadp_id"),
        @Index(name = "idx_seance_ctadp_dossier_dossier",
                columnList = "dossier_id"),
        @Index(name = "idx_seance_ctadp_dossier_unique",
                columnList = "seance_ctadp_id, dossier_id", unique = true)
})
public class SeanceCtadpDossier extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seance_ctadp_id", nullable = false)
    private SeanceCTADP seanceCtadp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommandation", length = 40)
    private RecommandationCtadp recommandation;

    @Column(name = "commentaire", length = 2000)
    private String commentaire;
}
```

- [ ] **Step 4: Écrire la migration**

```sql
--liquibase formatted sql
--changeset dev:017-add-seance-ctadp

CREATE TABLE seance_ctadp (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    date_seance       TIMESTAMP NOT NULL,
    statut            VARCHAR(20) NOT NULL DEFAULT 'PLANIFIEE',
    participants      VARCHAR(2000),
    proces_verbal     VARCHAR(5000)
);

CREATE TABLE seance_ctadp_dossier (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    seance_ctadp_id   UUID NOT NULL REFERENCES seance_ctadp(id),
    dossier_id        UUID NOT NULL REFERENCES dossier(id),
    recommandation    VARCHAR(40),
    commentaire       VARCHAR(2000),
    CONSTRAINT uk_seance_ctadp_dossier UNIQUE (seance_ctadp_id, dossier_id)
);

CREATE INDEX idx_seance_ctadp_dossier_seance ON seance_ctadp_dossier(seance_ctadp_id);
CREATE INDEX idx_seance_ctadp_dossier_dossier ON seance_ctadp_dossier(dossier_id);

COMMENT ON TABLE seance_ctadp IS 'Seance hebdomadaire du comite CTADP (Lot 2, plan de travail S11/S3) - regroupe plusieurs dossiers examines ensemble';
COMMENT ON TABLE seance_ctadp_dossier IS 'Un dossier a l ordre du jour d une seance, avec sa recommandation (DecisionCTADP) - un dossier peut revenir a une seance ulterieure, jamais deux fois a la meme';
```

- [ ] **Step 5: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/StatutSeanceCtadp.java \
        src/main/java/gov/bf/ascelc/univers_audits/enums/RecommandationCtadp.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/SeanceCTADP.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/SeanceCtadpDossier.java \
        src/main/resources/db/changelog/migrations/017-add-seance-ctadp.sql
git commit -m "feat: add SeanceCTADP/SeanceCtadpDossier entities and migration (Lot 2)"
```

---

### Task 2: DTOs et mapper dédié

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/SeanceCtadpCreateRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/AddDossierToSeanceRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RecommandationCtadpRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/TenirSeanceRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SeanceCtadpResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SeanceCtadpDossierResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/mapper/SeanceCtadpMapper.java`

**Interfaces:**
- Produces: `SeanceCtadpMapper.toResponse(SeanceCTADP)`, `.toDossierResponse(SeanceCtadpDossier)`
  — utilisés par la Task 3.

- [ ] **Step 1: Créer `SeanceCtadpCreateRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeanceCtadpCreateRequest {

    @NotNull(message = "La date de la séance est obligatoire")
    private Instant dateSeance;

    @Size(max = 2000)
    private String participants;
}
```

- [ ] **Step 2: Créer `AddDossierToSeanceRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddDossierToSeanceRequest {

    @NotNull(message = "L'ID du dossier est obligatoire")
    private UUID dossierId;
}
```

- [ ] **Step 3: Créer `RecommandationCtadpRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommandationCtadpRequest {

    @NotNull(message = "La recommandation est obligatoire")
    private RecommandationCtadp recommandation;

    @Size(max = 2000)
    private String commentaire;
}
```

- [ ] **Step 4: Créer `TenirSeanceRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenirSeanceRequest {

    @Size(max = 5000)
    private String procesVerbal;
}
```

- [ ] **Step 5: Créer `SeanceCtadpDossierResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeanceCtadpDossierResponse {

    private UUID id;
    private UUID dossierId;
    private String dossierNumber;
    private String dossierObject;
    private RecommandationCtadp recommandation;
    private String commentaire;
}
```

- [ ] **Step 6: Créer `SeanceCtadpResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeanceCtadpResponse {

    private UUID id;
    private Instant dateSeance;
    private StatutSeanceCtadp statut;
    private String participants;
    private String procesVerbal;
    private List<SeanceCtadpDossierResponse> dossiers;
}
```

- [ ] **Step 7: Créer `SeanceCtadpMapper`**

Suivre le patron déjà établi dans ce dépôt pour `InvestigationMapper.java` (lu avant d'écrire ce
fichier — même wrapper `default toResponse(...)` contournant le bug MapStruct/Lombok déjà
documenté ailleurs dans ce dépôt : `@AfterMapping` jamais invoqué quand la cible est un
`@Builder` Lombok, ce qui est le cas de `SeanceCtadpResponse`) :

```java
package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpDossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCTADP;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import org.mapstruct.*;

import java.util.Collections;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface SeanceCtadpMapper {

    // NOTE — bug MapStruct/Lombok confirme (voir DeclarantMapper/DossierMapper/
    // DossierDetailsMapper/InvestigationMapper) : @AfterMapping jamais invoque
    // quand la cible est un @Builder Lombok. SeanceCtadpResponse en est un —
    // on enveloppe donc la methode generee dans une methode default qui
    // remplit la liste des dossiers nous-memes.
    default SeanceCtadpResponse toResponse(SeanceCTADP seance) {
        SeanceCtadpResponse response = mapToResponse(seance);
        if (response != null) {
            fillDossiers(seance, response);
        }
        return response;
    }

    @Mapping(target = "dossiers", ignore = true)
    SeanceCtadpResponse mapToResponse(SeanceCTADP seance);

    default void fillDossiers(SeanceCTADP seance, SeanceCtadpResponse response) {
        List<SeanceCtadpDossier> dossiers = seance.getDossiers();
        response.setDossiers(
                dossiers != null
                        ? dossiers.stream().map(this::toDossierResponse).toList()
                        : Collections.emptyList());
    }

    @Mapping(target = "dossierId",     source = "dossier.id")
    @Mapping(target = "dossierNumber", source = "dossier.number")
    @Mapping(target = "dossierObject", source = "dossier.object")
    SeanceCtadpDossierResponse toDossierResponse(SeanceCtadpDossier dossier);
}
```

- [ ] **Step 8: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS, aucun avertissement de propriété non mappée (`unmappedTargetPolicy =
ReportingPolicy.WARN`).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/SeanceCtadpCreateRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/AddDossierToSeanceRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RecommandationCtadpRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/TenirSeanceRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SeanceCtadpResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SeanceCtadpDossierResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/mapper/SeanceCtadpMapper.java
git commit -m "feat: add SeanceCTADP DTOs and dedicated mapper"
```

---

### Task 3: Repositories, service

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/SeanceCtadpRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/SeanceCtadpDossierRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/SeanceCtadpService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/SeanceCtadpServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/SeanceCtadpServiceImplTest.java`

**Interfaces:**
- Consumes: `SeanceCtadpMapper` (Task 2), `DossierRepository` (existant, pour résoudre
  `dossierId` → `Dossier`).
- Produces: `SeanceCtadpService.create/findById/findAll/addDossier/recordRecommandation/tenir`
  — utilisés par la Task 4 (contrôleur).

- [ ] **Step 1: Créer `SeanceCtadpRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SeanceCTADP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeanceCtadpRepository extends JpaRepository<SeanceCTADP, UUID> {

    @Query("""
            SELECT DISTINCT s FROM SeanceCTADP s
            LEFT JOIN FETCH s.dossiers sd
            LEFT JOIN FETCH sd.dossier
            WHERE s.id = :id
            """)
    Optional<SeanceCTADP> findById(@Param("id") UUID id);
}
```

Note : la pagination (`findAll(Pageable)`) reste celle héritée de `JpaRepository` — pas de
fetch-join sur `dossiers` pour la liste paginée (même raison que
`DossierMapper.mapToResponse` ignore les collections en liste paginée : éviter un N+1 sur
chaque ligne). Le mapper (Task 2) gère déjà `dossiers == null` proprement via `fillDossiers`.

- [ ] **Step 2: Créer `SeanceCtadpDossierRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeanceCtadpDossierRepository
        extends JpaRepository<SeanceCtadpDossier, UUID> {

    boolean existsBySeanceCtadpIdAndDossierId(UUID seanceCtadpId, UUID dossierId);

    Optional<SeanceCtadpDossier> findBySeanceCtadpIdAndDossierId(
            UUID seanceCtadpId, UUID dossierId);
}
```

- [ ] **Step 3: Créer l'interface de service**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.AddDossierToSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RecommandationCtadpRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SeanceCtadpCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TenirSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SeanceCtadpService {

    SeanceCtadpResponse create(SeanceCtadpCreateRequest request);

    SeanceCtadpResponse findById(UUID id);

    Page<SeanceCtadpResponse> findAll(Pageable pageable);

    SeanceCtadpResponse addDossier(UUID seanceId, AddDossierToSeanceRequest request);

    SeanceCtadpResponse recordRecommandation(
            UUID seanceId, UUID dossierId, RecommandationCtadpRequest request);

    SeanceCtadpResponse tenir(UUID seanceId, TenirSeanceRequest request);
}
```

- [ ] **Step 4: Implémenter le service**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import gov.bf.ascelc.univers_audits.mapper.SeanceCtadpMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AddDossierToSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RecommandationCtadpRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SeanceCtadpCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TenirSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCTADP;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpRepository;
import gov.bf.ascelc.univers_audits.service.SeanceCtadpService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeanceCtadpServiceImpl implements SeanceCtadpService {

    private final SeanceCtadpRepository        seanceCtadpRepository;
    private final SeanceCtadpDossierRepository seanceCtadpDossierRepository;
    private final DossierRepository            dossierRepository;
    private final SeanceCtadpMapper             mapper;

    @Override
    @Transactional
    public SeanceCtadpResponse create(SeanceCtadpCreateRequest request) {
        SeanceCTADP seance = SeanceCTADP.builder()
                .dateSeance(request.getDateSeance())
                .participants(request.getParticipants())
                .build();

        SeanceCTADP saved = seanceCtadpRepository.save(seance);
        log.info("Séance CTADP créée — id: {}, date: {}", saved.getId(), saved.getDateSeance());
        return mapper.toResponse(saved);
    }

    @Override
    public SeanceCtadpResponse findById(UUID id) {
        return mapper.toResponse(getSeanceOrThrow(id));
    }

    @Override
    public Page<SeanceCtadpResponse> findAll(Pageable pageable) {
        return seanceCtadpRepository.findAll(pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public SeanceCtadpResponse addDossier(UUID seanceId, AddDossierToSeanceRequest request) {
        SeanceCTADP seance = getSeanceOrThrow(seanceId);

        if (seance.getStatut() != StatutSeanceCtadp.PLANIFIEE) {
            throw new BusinessException(
                    "Impossible d'ajouter un dossier à une séance qui n'est plus planifiée");
        }

        Dossier dossier = dossierRepository.findById(request.getDossierId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + request.getDossierId()));

        if (dossier.getStatus() != DossierStatus.EN_REVUE_CTADP) {
            throw new BusinessException(
                    "Ce dossier n'est pas en attente de revue CTADP (statut actuel : "
                            + dossier.getStatus() + ")");
        }

        if (seanceCtadpDossierRepository.existsBySeanceCtadpIdAndDossierId(
                seanceId, request.getDossierId())) {
            throw new BusinessException(
                    "Ce dossier est déjà à l'ordre du jour de cette séance");
        }

        SeanceCtadpDossier entry = SeanceCtadpDossier.builder()
                .seanceCtadp(seance)
                .dossier(dossier)
                .build();
        seance.getDossiers().add(entry);

        SeanceCTADP saved = seanceCtadpRepository.save(seance);
        log.info("Dossier {} ajouté à l'ordre du jour de la séance {}",
                dossier.getId(), seanceId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SeanceCtadpResponse recordRecommandation(
            UUID seanceId, UUID dossierId, RecommandationCtadpRequest request) {

        getSeanceOrThrow(seanceId);

        SeanceCtadpDossier entry = seanceCtadpDossierRepository
                .findBySeanceCtadpIdAndDossierId(seanceId, dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ce dossier n'est pas à l'ordre du jour de cette séance"));

        entry.setRecommandation(request.getRecommandation());
        entry.setCommentaire(request.getCommentaire());
        seanceCtadpDossierRepository.save(entry);

        log.info("Recommandation enregistrée — séance: {}, dossier: {}, recommandation: {}",
                seanceId, dossierId, request.getRecommandation());
        return findById(seanceId);
    }

    @Override
    @Transactional
    public SeanceCtadpResponse tenir(UUID seanceId, TenirSeanceRequest request) {
        SeanceCTADP seance = getSeanceOrThrow(seanceId);

        if (seance.getStatut() != StatutSeanceCtadp.PLANIFIEE) {
            throw new BusinessException(
                    "Seule une séance planifiée peut être marquée tenue (statut actuel : "
                            + seance.getStatut() + ")");
        }

        seance.setStatut(StatutSeanceCtadp.TENUE);
        seance.setProcesVerbal(request.getProcesVerbal());

        SeanceCTADP saved = seanceCtadpRepository.save(seance);
        log.info("Séance CTADP {} marquée TENUE", seanceId);
        return mapper.toResponse(saved);
    }

    private SeanceCTADP getSeanceOrThrow(UUID id) {
        return seanceCtadpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Séance CTADP introuvable : " + id));
    }
}
```

Note sur `recordRecommandation` : la méthode relit `findById(seanceId)` en fin d'exécution
plutôt que de reconstruire la réponse à la main, pour renvoyer l'état complet et à jour de la
séance (tous les dossiers, pas seulement celui modifié) — cohérent avec ce que `findById`
retourne déjà ailleurs dans ce service.

- [ ] **Step 5: Écrire les tests**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import gov.bf.ascelc.univers_audits.mapper.SeanceCtadpMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AddDossierToSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RecommandationCtadpRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TenirSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCTADP;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeanceCtadpServiceImplTest {

    @Mock private SeanceCtadpRepository        seanceCtadpRepository;
    @Mock private SeanceCtadpDossierRepository seanceCtadpDossierRepository;
    @Mock private DossierRepository            dossierRepository;
    @Mock private SeanceCtadpMapper             mapper;

    @InjectMocks
    private SeanceCtadpServiceImpl service;

    @Test
    void addDossier_succeedsWhenDossierEligibleAndSeancePlanifiee() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.PLANIFIEE).build();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        AddDossierToSeanceRequest request = AddDossierToSeanceRequest.builder()
                .dossierId(dossierId).build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(seanceCtadpDossierRepository.existsBySeanceCtadpIdAndDossierId(seanceId, dossierId))
                .thenReturn(false);
        when(seanceCtadpRepository.save(any(SeanceCTADP.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(SeanceCTADP.class)))
                .thenReturn(SeanceCtadpResponse.builder().build());

        service.addDossier(seanceId, request);

        verify(seanceCtadpRepository).save(argThat(s -> s.getDossiers().size() == 1
                && s.getDossiers().get(0).getDossier() == dossier));
    }

    @Test
    void addDossier_rejectsWhenDossierNotInCorrectStatus() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.PLANIFIEE).build();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.RECU).build();
        AddDossierToSeanceRequest request = AddDossierToSeanceRequest.builder()
                .dossierId(dossierId).build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.addDossier(seanceId, request))
                .isInstanceOf(gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException.class);

        verify(seanceCtadpRepository, never()).save(any());
    }

    @Test
    void addDossier_rejectsWhenAlreadyOnAgenda() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.PLANIFIEE).build();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        AddDossierToSeanceRequest request = AddDossierToSeanceRequest.builder()
                .dossierId(dossierId).build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(seanceCtadpDossierRepository.existsBySeanceCtadpIdAndDossierId(seanceId, dossierId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addDossier(seanceId, request))
                .isInstanceOf(gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException.class);

        verify(seanceCtadpRepository, never()).save(any());
    }

    @Test
    void recordRecommandation_updatesEntryAndReturnsFullSeance() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId).build();
        SeanceCtadpDossier entry = SeanceCtadpDossier.builder().id(UUID.randomUUID()).build();
        RecommandationCtadpRequest request = RecommandationCtadpRequest.builder()
                .recommandation(RecommandationCtadp.VALIDATION_INVESTIGATION)
                .commentaire("Preuves suffisantes")
                .build();

        when(seanceCtadpRepository.findById(seanceId))
                .thenReturn(Optional.of(seance), Optional.of(seance));
        when(seanceCtadpDossierRepository.findBySeanceCtadpIdAndDossierId(seanceId, dossierId))
                .thenReturn(Optional.of(entry));
        when(mapper.toResponse(seance)).thenReturn(SeanceCtadpResponse.builder().build());

        service.recordRecommandation(seanceId, dossierId, request);

        verify(seanceCtadpDossierRepository).save(argThat(e ->
                e.getRecommandation() == RecommandationCtadp.VALIDATION_INVESTIGATION
                        && "Preuves suffisantes".equals(e.getCommentaire())));
    }

    @Test
    void recordRecommandation_rejectsWhenDossierNotOnAgenda() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId).build();
        RecommandationCtadpRequest request = RecommandationCtadpRequest.builder()
                .recommandation(RecommandationCtadp.CLASSEMENT).build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(seanceCtadpDossierRepository.findBySeanceCtadpIdAndDossierId(seanceId, dossierId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordRecommandation(seanceId, dossierId, request))
                .isInstanceOf(gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException.class);

        verify(seanceCtadpDossierRepository, never()).save(any());
    }

    @Test
    void tenir_succeedsWhenPlanifiee() {
        UUID seanceId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.PLANIFIEE).build();
        TenirSeanceRequest request = TenirSeanceRequest.builder()
                .procesVerbal("Compte-rendu de la séance").build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(seanceCtadpRepository.save(any(SeanceCTADP.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(SeanceCTADP.class)))
                .thenReturn(SeanceCtadpResponse.builder().build());

        service.tenir(seanceId, request);

        verify(seanceCtadpRepository).save(argThat(s ->
                s.getStatut() == StatutSeanceCtadp.TENUE
                        && "Compte-rendu de la séance".equals(s.getProcesVerbal())));
    }

    @Test
    void tenir_rejectsWhenAlreadyTenue() {
        UUID seanceId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.TENUE).build();
        TenirSeanceRequest request = TenirSeanceRequest.builder().build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));

        assertThatThrownBy(() -> service.tenir(seanceId, request))
                .isInstanceOf(gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException.class);

        verify(seanceCtadpRepository, never()).save(any());
    }
}
```

- [ ] **Step 6: Lancer les tests**

```
mvn test -q -Dtest=SeanceCtadpServiceImplTest
```

Expected: BUILD SUCCESS, 7/7 tests passent.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/repository/SeanceCtadpRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/SeanceCtadpDossierRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/SeanceCtadpService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/SeanceCtadpServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/SeanceCtadpServiceImplTest.java
git commit -m "feat: add SeanceCtadpService with agenda/recommandation/tenir workflow"
```

---

### Task 4: Contrôleur REST

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/ApiUrls.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/SeanceCtadpController.java`

**Interfaces:**
- Consumes: `SeanceCtadpService` (Task 3).

- [ ] **Step 1: Ajouter la constante d'URL**

Dans `ApiUrls.java`, à côté du bloc `INVESTIGATIONS` :

```java
    // ── Séances CTADP ─────────────────────────────────────────

    public static final String SEANCES_CTADP = BASE + "/seances-ctadp";
```

- [ ] **Step 2: Créer le contrôleur**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.AddDossierToSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RecommandationCtadpRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SeanceCtadpCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TenirSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.service.SeanceCtadpService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiUrls.SEANCES_CTADP)
@RequiredArgsConstructor
public class SeanceCtadpController {

    private final SeanceCtadpService seanceCtadpService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> create(
            @Valid @RequestBody SeanceCtadpCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                seanceCtadpService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'MEMBRE_CTADP','CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<Page<SeanceCtadpResponse>> findAll(
            @PageableDefault(size = 20, sort = "dateSeance") Pageable pageable) {
        return ResponseEntity.ok(seanceCtadpService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'MEMBRE_CTADP','CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(seanceCtadpService.findById(id));
    }

    @PostMapping("/{id}/dossiers")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> addDossier(
            @PathVariable UUID id,
            @Valid @RequestBody AddDossierToSeanceRequest request) {
        return ResponseEntity.ok(seanceCtadpService.addDossier(id, request));
    }

    @PutMapping("/{id}/dossiers/{dossierId}")
    @PreAuthorize("hasAnyRole('CGEA','CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> recordRecommandation(
            @PathVariable UUID id,
            @PathVariable UUID dossierId,
            @Valid @RequestBody RecommandationCtadpRequest request) {
        return ResponseEntity.ok(
                seanceCtadpService.recordRecommandation(id, dossierId, request));
    }

    @PatchMapping("/{id}/tenir")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> tenir(
            @PathVariable UUID id,
            @RequestBody TenirSeanceRequest request) {
        return ResponseEntity.ok(seanceCtadpService.tenir(id, request));
    }
}
```

- [ ] **Step 3: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/utils/ApiUrls.java \
        src/main/java/gov/bf/ascelc/univers_audits/controller/SeanceCtadpController.java
git commit -m "feat: add SeanceCtadpController"
```

---

### Task 5: Suite complète + mise à jour du backlog

**Files:** mémoire projet `project_asce_backlog_2026_07_30.md`.

- [ ] **Step 1: Lancer la suite complète**

```
mvn test -q
```

Expected: 0 échec (l'erreur `UniversAuditsApplicationTests.contextLoads` — absence de base de
données dans cet environnement — est un baseline connu, sans rapport avec ce chantier).

- [ ] **Step 2: Mettre à jour le backlog mémoire**

Marquer le sous-chantier "SeanceCTADP" comme livré (2/4 du Lot 2) et rappeler les 2
sous-chantiers restants (DecisionCGE + branche ORIENTEE_ADMINISTRATIF, génération PDF).
