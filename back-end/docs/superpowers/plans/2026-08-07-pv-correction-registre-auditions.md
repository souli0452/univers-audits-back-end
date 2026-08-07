# Correction PV + RegistreAuditions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add historized correction + mandatory relecture to `PVAudition`, guard PV creation on audition status, and deliver `RegistreAuditions` — a paginated, DEI-restricted, cross-dossier read view over `Audition`/`PVAudition`.

**Architecture:** `PVAudition` gains `pvVersion`/`readBackAt`; a new `CorrectionPvAudition` entity (N:1) reproduces the exact `RevisionPlan` snapshot-before-overwrite pattern. The DECLARANT-masking logic currently private inside `AuditionServiceImpl` (from the previous sous-chantier) is extracted into a shared, independently-tested `AuditionDisplayNameMasker` component so `RegistreAuditionsServiceImpl` can reuse it without duplicating logic or depending on another service's implementation class. `RegistreAuditions` introduces no new writable data — it is a query over existing entities.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, MapStruct 1.5.5.Final, Liquibase (formatted SQL), Lombok `@SuperBuilder`, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Correction is **post-finalization only** — a PV can only be corrected via `PATCH .../pv/correction` once `pv.isFinalized() == true`; there is no separate pre-finalization edit endpoint (an unfinalized draft is simply not finalized yet).
- Every correction is historized, never destructive: `correct()` snapshots the PV's *current* `content`/`pvVersion` into a new `CorrectionPvAudition` row (`versionNumber`, `content`, `correctedAt`, `correctedBy`, `motifCorrection` — all required, `motifCorrection` is mandatory), then overwrites `PVAudition.content` and increments `pvVersion`.
- Relecture (`readBackAt`) is a **mandatory precondition** to `finalizeSignatures()` — a PV with no relecture recorded cannot be finalized. `markReadBack()` can only be called once (throws if `readBackAt` already set) and only before finalization (throws if already finalized).
- `PvAuditionServiceImpl.create()` requires `audition.getStatus() == AuditionStatus.CONDUCTED`, else `BusinessException` — mirrors the existing guard style on `Audition.conduct()`/`cancel()`.
- `RegistreAuditions` is read-only, paginated, sorted `scheduledAt DESC`, **no filters** in this sub-chantier (YAGNI — add if a real need surfaces). Restricted to `hasAnyRole('CGEA','ADMIN_DDIC')` — the same DEI-approximation precedent already established by `PlanInvestigation.valider` (no dedicated DEI role exists in this codebase).
- `RegistreAuditions`' masked interviewee name **reuses** `AuditionDisplayNameMasker.mask(Audition)` (extracted from `AuditionServiceImpl` in Task 2) — never a second copy of the masking logic.
- Migration numbering: `028`, the next after `027-audition-business-rules.sql`. `RegistreAuditions` needs no schema change — Task 2 ships no migration.
- No new roles anywhere beyond what's listed above; `AuditionController`'s existing `READ_ROLES`/`WRITE_ROLES` constants are reused unchanged for the two new PV endpoints.

---

### Task 1: `PVAudition` correction + relecture + creation guard

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/PVAudition.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/CorrectionPvAudition.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/CorrectionPvAuditionRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PvAuditionCorrectionRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/CorrectionPvAuditionResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PvAuditionResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/PvAuditionService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImpl.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java`
- Create: `src/main/resources/db/changelog/migrations/028-add-pv-audition-correction-relecture.sql`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImplTest.java`

**Interfaces:**
- Produces: `PVAudition.pvVersion: Integer` (default 1), `PVAudition.readBackAt: Instant`, `CorrectionPvAudition` entity, `PvAuditionService.markReadBack(UUID): PvAuditionResponse`, `PvAuditionService.correct(UUID, PvAuditionCorrectionRequest): PvAuditionResponse` — no other task in this plan consumes these; Task 2 is independent of Task 1.

- [ ] **Step 1: Add fields to `PVAudition`**

In `src/main/java/gov/bf/ascelc/univers_audits/model/entity/PVAudition.java`, add after the existing `finalizedAt` field (before `isFinalized()`):

```java
    @Column(name = "pv_version", nullable = false)
    @Builder.Default
    private Integer pvVersion = 1;

    @Column(name = "read_back_at")
    private Instant readBackAt;
```

- [ ] **Step 2: Create `CorrectionPvAudition` entity**

Create `src/main/java/gov/bf/ascelc/univers_audits/model/entity/CorrectionPvAudition.java`:

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
@Table(name = "correction_pv_audition", indexes = {
        @Index(name = "idx_correction_pv_audition_pv",
                columnList = "pv_audition_id")
})
public class CorrectionPvAudition extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pv_audition_id", nullable = false)
    private PVAudition pvAudition;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "corrected_at", nullable = false)
    private Instant correctedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_by_id", nullable = false)
    private Agent correctedBy;

    @Column(name = "motif_correction", nullable = false, columnDefinition = "TEXT")
    private String motifCorrection;
}
```

- [ ] **Step 3: Create `CorrectionPvAuditionRepository`**

Create `src/main/java/gov/bf/ascelc/univers_audits/repository/CorrectionPvAuditionRepository.java`:

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.CorrectionPvAudition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CorrectionPvAuditionRepository extends JpaRepository<CorrectionPvAudition, UUID> {

    List<CorrectionPvAudition> findByPvAuditionIdOrderByVersionNumberAsc(UUID pvAuditionId);
}
```

- [ ] **Step 4: Create `PvAuditionCorrectionRequest`**

Create `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PvAuditionCorrectionRequest.java`:

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvAuditionCorrectionRequest {

    @NotBlank(message = "Le contenu corrigé du procès-verbal est obligatoire")
    private String content;

    @NotBlank(message = "Le motif de la correction est obligatoire")
    private String motifCorrection;
}
```

- [ ] **Step 5: Create `CorrectionPvAuditionResponse`**

Create `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/CorrectionPvAuditionResponse.java`:

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
public class CorrectionPvAuditionResponse {
    private UUID id;
    private Integer versionNumber;
    private String content;
    private Instant correctedAt;
    private UUID correctedById;
    private String correctedByName;
    private String motifCorrection;
}
```

- [ ] **Step 6: Update `PvAuditionResponse`**

Replace the full content of `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PvAuditionResponse.java`:

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
public class PvAuditionResponse {
    private UUID id;
    private UUID auditionId;
    private String content;
    private String draftedByName;
    private Boolean intervieweeSigned;
    private Boolean intervieweeSignatureRefused;
    private Instant finalizedAt;
    private Integer pvVersion;
    private Instant readBackAt;
    private List<CorrectionPvAuditionResponse> corrections;
}
```

- [ ] **Step 7: Update `PvAuditionService` interface**

Replace the full content of `src/main/java/gov/bf/ascelc/univers_audits/service/PvAuditionService.java`:

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCorrectionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;

import java.util.UUID;

public interface PvAuditionService {

    PvAuditionResponse create(UUID auditionId, PvAuditionCreateRequest request);

    PvAuditionResponse markReadBack(UUID auditionId);

    PvAuditionResponse finalizeSignatures(UUID auditionId, PvAuditionFinalizeRequest request);

    PvAuditionResponse correct(UUID auditionId, PvAuditionCorrectionRequest request);

    PvAuditionResponse findByAuditionId(UUID auditionId);
}
```

- [ ] **Step 8: Rewrite `PvAuditionServiceImpl`**

Replace the full content of `src/main/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImpl.java`:

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCorrectionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.CorrectionPvAuditionResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.CorrectionPvAudition;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.CorrectionPvAuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.service.PvAuditionService;
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
public class PvAuditionServiceImpl implements PvAuditionService {

    private final PVAuditionRepository           pvAuditionRepository;
    private final CorrectionPvAuditionRepository correctionPvAuditionRepository;
    private final AuditionRepository             auditionRepository;
    private final DossierDetailsMapper           mapper;
    private final AgentContextResolver           agentContextResolver;
    private final DossierAccessGuard             accessGuard;

    @Override
    @Transactional
    public PvAuditionResponse create(UUID auditionId, PvAuditionCreateRequest request) {
        Audition audition = auditionRepository.findById(auditionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audition introuvable : " + auditionId));
        accessGuard.checkReadAccess(audition.getInvestigation().getDossier());

        if (audition.getStatus() != AuditionStatus.CONDUCTED) {
            throw new BusinessException(
                    "Un procès-verbal ne peut être rédigé que pour une audition tenue (statut actuel : "
                            + audition.getStatus() + ")");
        }
        if (pvAuditionRepository.findByAuditionId(auditionId).isPresent()) {
            throw new BusinessException(
                    "Un procès-verbal existe déjà pour cette audition");
        }

        PVAudition pv = PVAudition.builder()
                .audition(audition)
                .content(request.getContent())
                .draftedBy(agentContextResolver.getCurrentAgent())
                .build();

        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition créé — audition: {}, id: {}", auditionId, saved.getId());
        return toResponseWithCorrections(saved);
    }

    @Override
    @Transactional
    public PvAuditionResponse markReadBack(UUID auditionId) {
        PVAudition pv = getPvOrThrow(auditionId);
        accessGuard.checkReadAccess(pv.getAudition().getInvestigation().getDossier());

        if (pv.isFinalized()) {
            throw new BusinessException("Ce procès-verbal est déjà finalisé");
        }
        if (pv.getReadBackAt() != null) {
            throw new BusinessException(
                    "La relecture a déjà été enregistrée pour ce procès-verbal");
        }
        pv.setReadBackAt(Instant.now());
        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition relu à la personne auditionnée — audition: {}", auditionId);
        return toResponseWithCorrections(saved);
    }

    @Override
    @Transactional
    public PvAuditionResponse finalizeSignatures(UUID auditionId, PvAuditionFinalizeRequest request) {
        PVAudition pv = getPvOrThrow(auditionId);
        accessGuard.checkReadAccess(pv.getAudition().getInvestigation().getDossier());

        if (Boolean.TRUE.equals(request.getIntervieweeSigned())
                && Boolean.TRUE.equals(request.getIntervieweeSignatureRefused())) {
            throw new BusinessException(
                    "Un PV ne peut pas être à la fois signé et refusé par la personne auditionnée");
        }
        if (pv.isFinalized()) {
            throw new BusinessException("Ce procès-verbal est déjà finalisé");
        }
        if (pv.getReadBackAt() == null) {
            throw new BusinessException(
                    "Le procès-verbal doit être relu à la personne auditionnée avant signature");
        }

        pv.finalizeSignatures(
                Boolean.TRUE.equals(request.getIntervieweeSigned()),
                Boolean.TRUE.equals(request.getIntervieweeSignatureRefused()));

        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition finalisé — audition: {}", auditionId);
        return toResponseWithCorrections(saved);
    }

    @Override
    @Transactional
    public PvAuditionResponse correct(UUID auditionId, PvAuditionCorrectionRequest request) {
        PVAudition pv = getPvOrThrow(auditionId);
        accessGuard.checkReadAccess(pv.getAudition().getInvestigation().getDossier());

        if (!pv.isFinalized()) {
            throw new BusinessException(
                    "Seul un procès-verbal finalisé peut faire l'objet d'une correction");
        }

        CorrectionPvAudition correction = CorrectionPvAudition.builder()
                .pvAudition(pv)
                .versionNumber(pv.getPvVersion())
                .content(pv.getContent())
                .correctedAt(Instant.now())
                .correctedBy(agentContextResolver.getCurrentAgent())
                .motifCorrection(request.getMotifCorrection())
                .build();
        correctionPvAuditionRepository.save(correction);

        pv.setContent(request.getContent());
        pv.setPvVersion(pv.getPvVersion() + 1);
        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition corrigé — audition: {}, nouvelle version: {}",
                auditionId, saved.getPvVersion());
        return toResponseWithCorrections(saved);
    }

    @Override
    public PvAuditionResponse findByAuditionId(UUID auditionId) {
        PVAudition pv = getPvOrThrow(auditionId);
        Dossier dossier = pv.getAudition().getInvestigation().getDossier();
        accessGuard.checkReadAccess(dossier);

        if (Boolean.TRUE.equals(dossier.getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException(
                    "Accès refusé — le procès-verbal d'un dossier confidentiel n'est visible que par les rôles habilités");
        }

        return toResponseWithCorrections(pv);
    }

    private PvAuditionResponse toResponseWithCorrections(PVAudition pv) {
        PvAuditionResponse response = mapper.toResponse(pv);
        response.setCorrections(
                correctionPvAuditionRepository.findByPvAuditionIdOrderByVersionNumberAsc(pv.getId())
                        .stream()
                        .map(this::toCorrectionResponse)
                        .toList());
        return response;
    }

    private CorrectionPvAuditionResponse toCorrectionResponse(CorrectionPvAudition correction) {
        return CorrectionPvAuditionResponse.builder()
                .id(correction.getId())
                .versionNumber(correction.getVersionNumber())
                .content(correction.getContent())
                .correctedAt(correction.getCorrectedAt())
                .correctedById(correction.getCorrectedBy().getId())
                .correctedByName(correction.getCorrectedBy().getNomComplet())
                .motifCorrection(correction.getMotifCorrection())
                .build();
    }

    private PVAudition getPvOrThrow(UUID auditionId) {
        return pvAuditionRepository.findByAuditionId(auditionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procès-verbal introuvable pour l'audition : " + auditionId));
    }
}
```

- [ ] **Step 9: Update `DossierDetailsMapper`'s PVAudition mapping**

In `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`, replace:

```java
    @Mapping(target = "auditionId", source = "audition.id")
    @Mapping(target = "draftedByName", source = "draftedBy.nomComplet")
    PvAuditionResponse toResponse(PVAudition pvAudition);
```

with:

```java
    @Mapping(target = "auditionId", source = "audition.id")
    @Mapping(target = "draftedByName", source = "draftedBy.nomComplet")
    @Mapping(target = "corrections", ignore = true)
    PvAuditionResponse toResponse(PVAudition pvAudition);
```

(`pvVersion`/`readBackAt` are plain same-named fields on both sides — MapStruct auto-maps them without an explicit `@Mapping`. `corrections` is populated explicitly by `PvAuditionServiceImpl.toResponseWithCorrections`, same reasoning as `orderWarning`/`secondAuditionWarning` on `AuditionResponse`: it requires an extra repository call, not a pure function of the entity passed to the mapper.)

- [ ] **Step 10: Add PV endpoints to `AuditionController`**

In `src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java`, add the import:

```java
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCorrectionRequest;
```

Then add, after the existing `finalizePv` method (end of class, before the closing `}`):

```java

    @PatchMapping("/{auditionId}/pv/relecture")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PvAuditionResponse> markPvReadBack(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId) {
        return ResponseEntity.ok(pvAuditionService.markReadBack(auditionId));
    }

    @PatchMapping("/{auditionId}/pv/correction")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PvAuditionResponse> correctPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody PvAuditionCorrectionRequest request) {
        return ResponseEntity.ok(pvAuditionService.correct(auditionId, request));
    }
```

- [ ] **Step 11: Write migration 028**

Create `src/main/resources/db/changelog/migrations/028-add-pv-audition-correction-relecture.sql`:

```sql
--liquibase formatted sql
--changeset dev:028-add-pv-audition-correction-relecture

ALTER TABLE pv_audition ADD COLUMN pv_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE pv_audition ADD COLUMN read_back_at TIMESTAMP;

CREATE TABLE correction_pv_audition (
    id                 UUID PRIMARY KEY,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP,
    created_by_id       VARCHAR(100),
    updated_by_id       VARCHAR(100),
    pv_audition_id      UUID NOT NULL REFERENCES pv_audition(id),
    version_number      INTEGER NOT NULL,
    content             TEXT NOT NULL,
    corrected_at        TIMESTAMP NOT NULL,
    corrected_by_id     UUID NOT NULL REFERENCES agent(id),
    motif_correction    TEXT NOT NULL
);

CREATE INDEX idx_correction_pv_audition_pv
    ON correction_pv_audition (pv_audition_id);

COMMENT ON COLUMN pv_audition.pv_version IS 'Compteur de version metier du contenu du PV, incremente a chaque correction post-finalisation';
COMMENT ON COLUMN pv_audition.read_back_at IS 'Horodatage de la relecture du PV a la personne auditionnee, prealable obligatoire a la signature';
COMMENT ON TABLE correction_pv_audition IS 'Historique des corrections post-finalisation d un PV d audition - snapshot immuable du contenu remplace a chaque correction (Lot 4, plan de travail S4)';
```

This migration is auto-discovered via `includeAll` — no changelog-master.yaml edit needed. Column
types/constraints for the `AuditEntity` base columns (`version`, `created_at`, `updated_at`,
`created_by_id`, `updated_by_id`) copied verbatim from `revision_plan` in migration
`021-add-plan-investigation.sql`, the closest existing precedent for a snapshot-history table.

- [ ] **Step 12: Rewrite `PvAuditionServiceImplTest`**

Replace the full content of
`src/test/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImplTest.java`:

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCorrectionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.CorrectionPvAudition;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.CorrectionPvAuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PvAuditionServiceImplTest {

    @Mock private PVAuditionRepository pvAuditionRepository;
    @Mock private CorrectionPvAuditionRepository correctionPvAuditionRepository;
    @Mock private AuditionRepository auditionRepository;
    @Mock private DossierDetailsMapper mapper;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private DossierAccessGuard accessGuard;

    @InjectMocks
    private PvAuditionServiceImpl service;

    private Audition buildAudition(Dossier dossier) {
        return buildAudition(dossier, AuditionStatus.CONDUCTED);
    }

    private Audition buildAudition(Dossier dossier, AuditionStatus status) {
        return Audition.builder()
                .id(UUID.randomUUID())
                .status(status)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
    }

    @Test
    void create_savesPvWithContentAndDraftedBy() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));
        when(pvAuditionRepository.findByAuditionId(audition.getId())).thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(pvAuditionRepository.save(any(PVAudition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVAudition.class)))
                .thenReturn(PvAuditionResponse.builder().build());

        service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("Procès-verbal...").build());

        verify(pvAuditionRepository).save(argThat(pv ->
                pv.getContent().equals("Procès-verbal...")
                        && pv.getDraftedBy() == agent
                        && pv.getAudition() == audition));
    }

    @Test
    void create_throwsWhenPvAlreadyExistsForAudition() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));
        when(pvAuditionRepository.findByAuditionId(audition.getId()))
                .thenReturn(Optional.of(PVAudition.builder().build()));

        assertThatThrownBy(() -> service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_throwsWhenAuditionNotConducted() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier, AuditionStatus.NO_SHOW);
        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("x").build()))
                .isInstanceOf(BusinessException.class);

        verify(pvAuditionRepository, never()).save(any());
    }

    @Test
    void create_throwsWhenAgentLacksReadAccess() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition audition = Audition.builder().id(UUID.randomUUID())
                .status(AuditionStatus.CONDUCTED).investigation(investigation).build();

        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markReadBack_setsReadBackAt() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));
        when(pvAuditionRepository.save(any(PVAudition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVAudition.class)))
                .thenReturn(PvAuditionResponse.builder().build());

        service.markReadBack(pv.getId());

        assertThat(pv.getReadBackAt()).isNotNull();
    }

    @Test
    void markReadBack_throwsWhenAlreadySet() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition)
                .readBackAt(Instant.now()).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        assertThatThrownBy(() -> service.markReadBack(pv.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markReadBack_throwsWhenAlreadyFinalized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition)
                .finalizedAt(Instant.now()).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        assertThatThrownBy(() -> service.markReadBack(pv.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeSignatures_throwsWhenSignedAndRefusedBothTrue() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(true)
                .build();

        assertThatThrownBy(() -> service.finalizeSignatures(pv.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeSignatures_throwsWhenAlreadyFinalized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder()
                .id(UUID.randomUUID())
                .audition(audition)
                .finalizedAt(java.time.Instant.now())
                .build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(false)
                .build();

        assertThatThrownBy(() -> service.finalizeSignatures(pv.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeSignatures_throwsWhenNotReadBack() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(false)
                .build();

        assertThatThrownBy(() -> service.finalizeSignatures(pv.getId(), request))
                .isInstanceOf(BusinessException.class);

        assertThat(pv.isFinalized()).isFalse();
    }

    @Test
    void finalizeSignatures_succeedsWhenReadBackAtIsSet() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition)
                .readBackAt(Instant.now()).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));
        when(pvAuditionRepository.save(any(PVAudition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVAudition.class)))
                .thenReturn(PvAuditionResponse.builder().build());

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(false)
                .build();

        service.finalizeSignatures(pv.getId(), request);

        assertThat(pv.isFinalized()).isTrue();
    }

    @Test
    void correct_throwsWhenNotFinalized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        assertThatThrownBy(() -> service.correct(pv.getId(),
                PvAuditionCorrectionRequest.builder()
                        .content("Nouveau contenu")
                        .motifCorrection("Erreur de transcription")
                        .build()))
                .isInstanceOf(BusinessException.class);

        verify(correctionPvAuditionRepository, never()).save(any());
    }

    @Test
    void correct_archivesOldContentAndAppliesNewOne() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        PVAudition pv = PVAudition.builder()
                .id(UUID.randomUUID())
                .audition(audition)
                .content("Contenu original")
                .pvVersion(1)
                .finalizedAt(Instant.now())
                .build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(pvAuditionRepository.save(any(PVAudition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVAudition.class)))
                .thenReturn(PvAuditionResponse.builder().build());

        service.correct(pv.getId(), PvAuditionCorrectionRequest.builder()
                .content("Contenu corrigé")
                .motifCorrection("Erreur de transcription")
                .build());

        verify(correctionPvAuditionRepository).save(argThat(c ->
                c.getContent().equals("Contenu original")
                        && c.getVersionNumber().equals(1)
                        && c.getCorrectedBy() == agent
                        && c.getMotifCorrection().equals("Erreur de transcription")));
        assertThat(pv.getContent()).isEqualTo("Contenu corrigé");
        assertThat(pv.getPvVersion()).isEqualTo(2);
    }

    @Test
    void findByAuditionId_throwsWhenNoPvExists() {
        UUID auditionId = UUID.randomUUID();
        when(pvAuditionRepository.findByAuditionId(auditionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByAuditionId(auditionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByAuditionId_throwsWhenDossierConfidentialAndAgentCannotSeeConfidential() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition audition = Audition.builder().id(UUID.randomUUID())
                .status(AuditionStatus.CONDUCTED).investigation(investigation).build();
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();

        when(pvAuditionRepository.findByAuditionId(audition.getId())).thenReturn(Optional.of(pv));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.findByAuditionId(audition.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByAuditionId_returnsCorrectionsInVersionOrder() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();

        when(pvAuditionRepository.findByAuditionId(audition.getId())).thenReturn(Optional.of(pv));
        when(mapper.toResponse(pv)).thenReturn(PvAuditionResponse.builder().build());
        when(correctionPvAuditionRepository.findByPvAuditionIdOrderByVersionNumberAsc(pv.getId()))
                .thenReturn(List.of(
                        CorrectionPvAudition.builder()
                                .id(UUID.randomUUID())
                                .versionNumber(1)
                                .content("Contenu original")
                                .correctedAt(Instant.now())
                                .correctedBy(Agent.builder().id(UUID.randomUUID()).build())
                                .motifCorrection("Erreur de transcription")
                                .build()));

        PvAuditionResponse response = service.findByAuditionId(audition.getId());

        assertThat(response.getCorrections()).hasSize(1);
        assertThat(response.getCorrections().get(0).getVersionNumber()).isEqualTo(1);
    }
}
```

- [ ] **Step 13: Run the tests**

Run: `mvn -q test -Dtest=PvAuditionServiceImplTest`
Expected: BUILD SUCCESS, 16/16 tests passing.

- [ ] **Step 14: Compile the full project**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (confirms `AuditionController`/mapper/all call sites compile against
the changed entity/DTOs/service interface).

- [ ] **Step 15: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/PVAudition.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/CorrectionPvAudition.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/CorrectionPvAuditionRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PvAuditionCorrectionRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/CorrectionPvAuditionResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/PvAuditionResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/PvAuditionService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImpl.java \
        src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java \
        src/main/java/gov/bf/ascelc/univers_audits/controller/AuditionController.java \
        src/main/resources/db/changelog/migrations/028-add-pv-audition-correction-relecture.sql \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/PvAuditionServiceImplTest.java
git commit -m "feat: add historized PV correction, mandatory relecture, and CONDUCTED guard on PV creation"
```

---

### Task 2: Extract `AuditionDisplayNameMasker` + `RegistreAuditions`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/AuditionDisplayNameMasker.java`
- Create: `src/test/java/gov/bf/ascelc/univers_audits/shared/utils/AuditionDisplayNameMaskerTest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImplTest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/AuditionRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/PVAuditionRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RegistreAuditionEntryResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/RegistreAuditionsService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/RegistreAuditionsServiceImpl.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/RegistreAuditionsController.java`
- Create: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/RegistreAuditionsServiceImplTest.java`

**Interfaces:**
- Consumes: nothing from Task 1 — fully independent, may run before or after it.
- Produces: `AuditionDisplayNameMasker.mask(Audition): String` — a `@Component`, injectable by
  any service. No other task in this plan consumes it, but it is the reusable replacement for
  `AuditionServiceImpl`'s former private `maskedDeclarantAwareDisplayName` method.

- [ ] **Step 1: Extract `AuditionDisplayNameMasker`**

Create `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/AuditionDisplayNameMasker.java`:

```java
package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditionDisplayNameMasker {

    private final SecurityUtils securityUtils;

    public String mask(Audition audition) {
        String raw = audition.getIntervieweeDisplayName();
        if (audition.getIntervieweeType() != IntervieweeType.DECLARANT) {
            return raw;
        }
        Dossier dossier = audition.getInvestigation().getDossier();
        Declarant declarant = dossier.getDeclarant();
        if (declarant != null
                && Boolean.TRUE.equals(declarant.getProtectionRequested())
                && !securityUtils.hasRole("CGE")
                && !securityUtils.hasRole("CGEA")) {
            raw = "Lanceur d'alerte protégé (Loi N°010-2004/AN)";
        }
        if (Boolean.TRUE.equals(dossier.getAnonymous())) {
            raw = "Déclarant anonyme";
        }
        return raw;
    }
}
```

This is a verbatim move of `AuditionServiceImpl.maskedDeclarantAwareDisplayName` — same logic,
same precedence (protection checked first, anonymous checked second and wins if both apply,
matching `DossierServiceImpl.maskSensitiveData`'s existing order). Only the class shape changes:
from a private method to an injectable, independently-tested component.

- [ ] **Step 2: Write `AuditionDisplayNameMaskerTest`**

Create `src/test/java/gov/bf/ascelc/univers_audits/shared/utils/AuditionDisplayNameMaskerTest.java`:

```java
package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.Witness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditionDisplayNameMaskerTest {

    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private AuditionDisplayNameMasker masker;

    private Audition buildDeclarantAudition(Declarant declarant, boolean anonymous) {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID())
                .declarant(declarant).anonymous(anonymous).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        return Audition.builder()
                .intervieweeType(IntervieweeType.DECLARANT)
                .investigation(investigation)
                .build();
    }

    @Test
    void mask_returnsAnonymeForAnonymousDossier() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").build();
        Audition audition = buildDeclarantAudition(declarant, true);

        assertThat(masker.mask(audition)).isEqualTo("Déclarant anonyme");
    }

    @Test
    void mask_returnsProtectionMessageForProtectedDeclarantWithoutPrivilegedRole() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").protectionRequested(true).build();
        Audition audition = buildDeclarantAudition(declarant, false);
        when(securityUtils.hasRole(anyString())).thenReturn(false);

        assertThat(masker.mask(audition))
                .isEqualTo("Lanceur d'alerte protégé (Loi N°010-2004/AN)");
    }

    @Test
    void mask_preservesNameForProtectedDeclarantWhenCallerIsCge() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").protectionRequested(true).build();
        Audition audition = buildDeclarantAudition(declarant, false);
        when(securityUtils.hasRole("CGE")).thenReturn(true);

        assertThat(masker.mask(audition)).isEqualTo("Amidou Sawadogo");
    }

    @Test
    void mask_preservesNameForNormalDeclarant() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID())
                .firstName("Amidou").lastName("Sawadogo").build();
        Audition audition = buildDeclarantAudition(declarant, false);

        assertThat(masker.mask(audition)).isEqualTo("Amidou Sawadogo");
    }

    @Test
    void mask_returnsRawNameForNonDeclarantTypes() {
        Witness witness = Witness.builder().firstName("Jean").lastName("Kaboré").build();
        Audition audition = Audition.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witness(witness)
                .build();

        assertThat(masker.mask(audition)).isEqualTo("Jean Kaboré");
    }
}
```

- [ ] **Step 3: Run the new test**

Run: `mvn -q test -Dtest=AuditionDisplayNameMaskerTest`
Expected: BUILD SUCCESS, 5/5 tests passing.

- [ ] **Step 4: Wire `AuditionDisplayNameMasker` into `AuditionServiceImpl`**

In `src/main/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImpl.java`, replace
the import:

```java
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
```

with:

```java
import gov.bf.ascelc.univers_audits.shared.utils.AuditionDisplayNameMasker;
```

Replace the field:

```java
    private final SecurityUtils            securityUtils;
```

with:

```java
    private final AuditionDisplayNameMasker displayNameMasker;
```

Replace every one of the 6 call sites of `maskedDeclarantAwareDisplayName(...)` — in `schedule()`,
`conduct()`, `cancel()`, `markNoShow()`, `findByInvestigationId()`, and `computeOrderWarning()` —
with `displayNameMasker.mask(...)` (same argument in each case). For example, in `schedule()`:

```java
        response.setIntervieweeDisplayName(maskedDeclarantAwareDisplayName(saved));
```

becomes:

```java
        response.setIntervieweeDisplayName(displayNameMasker.mask(saved));
```

...and the same substitution in `conduct()`, `cancel()`, `markNoShow()`, the lambda inside
`findByInvestigationId()`, and inside `computeOrderWarning()`'s `.map(...)` call.

Finally, delete the now-unused private method entirely:

```java
    private String maskedDeclarantAwareDisplayName(Audition audition) {
        String raw = audition.getIntervieweeDisplayName();
        if (audition.getIntervieweeType() != IntervieweeType.DECLARANT) {
            return raw;
        }
        Dossier dossier = audition.getInvestigation().getDossier();
        Declarant declarant = dossier.getDeclarant();
        if (declarant != null
                && Boolean.TRUE.equals(declarant.getProtectionRequested())
                && !securityUtils.hasRole("CGE")
                && !securityUtils.hasRole("CGEA")) {
            raw = "Lanceur d'alerte protégé (Loi N°010-2004/AN)";
        }
        if (Boolean.TRUE.equals(dossier.getAnonymous())) {
            raw = "Déclarant anonyme";
        }
        return raw;
    }

```

- [ ] **Step 5: Update `AuditionServiceImplTest`**

In `src/test/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImplTest.java`,
replace the import:

```java
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
```

with:

```java
import gov.bf.ascelc.univers_audits.shared.utils.AuditionDisplayNameMasker;
```

Replace the field:

```java
    @Mock private SecurityUtils securityUtils;
```

with:

```java
    @Mock private AuditionDisplayNameMasker displayNameMasker;
```

Then replace all 4 masking-scenario tests — `schedule_masksIntervieweeDisplayNameForAnonymousDeclarant`,
`schedule_masksIntervieweeDisplayNameForProtectedDeclarantWithoutPrivilegedRole`,
`schedule_preservesIntervieweeDisplayNameForProtectedDeclarantWhenCallerIsCge`, and
`schedule_preservesIntervieweeDisplayNameForNormalDeclarant` (the block from
`schedule_masksIntervieweeDisplayNameForAnonymousDeclarant` through the end of
`schedule_preservesIntervieweeDisplayNameForNormalDeclarant`) — with a single delegation test,
since the masking scenarios themselves are now covered by `AuditionDisplayNameMaskerTest`:

```java
    @Test
    void schedule_appliesDisplayNameMaskerToResponse() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID()).build();
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).declarant(declarant).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of());
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());
        when(displayNameMasker.mask(any(Audition.class))).thenReturn("Nom masqué");

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.DECLARANT)
                .scheduledAt(Instant.now())
                .investigatorIds(investigatorIds)
                .build();

        AuditionResponse response = service.schedule(investigation.getId(), request);

        assertThat(response.getIntervieweeDisplayName()).isEqualTo("Nom masqué");
    }
```

- [ ] **Step 6: Run both test files**

Run: `mvn -q test -Dtest=AuditionServiceImplTest,AuditionDisplayNameMaskerTest`
Expected: BUILD SUCCESS, all tests passing (`AuditionServiceImplTest` count drops by 3 net —
4 masking tests removed, 1 delegation test added).

- [ ] **Step 7: Add the paginated fetch-joined query to `AuditionRepository`**

Replace the full content of
`src/main/java/gov/bf/ascelc/univers_audits/repository/AuditionRepository.java`:

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Audition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditionRepository extends JpaRepository<Audition, UUID> {

    List<Audition> findByInvestigationIdOrderByScheduledAtAsc(UUID investigationId);

    @Query("""
            SELECT a FROM Audition a
            LEFT JOIN FETCH a.investigation inv
            LEFT JOIN FETCH inv.dossier d
            LEFT JOIN FETCH d.declarant
            LEFT JOIN FETCH a.witness
            LEFT JOIN FETCH a.targetedParty
            ORDER BY a.scheduledAt DESC
            """)
    Page<Audition> findAllForRegistre(Pageable pageable);
}
```

Only `*-to-one` relations are fetch-joined (`investigation`, `dossier`, `declarant`, `witness`,
`targetedParty`) — never `investigators` (a `@ManyToMany` collection), which would force Hibernate
into in-memory pagination. The sort is baked into the JPQL itself (`ORDER BY a.scheduledAt DESC`);
the `Pageable` passed in by the controller carries only page/size, no `Sort`, to avoid a second,
conflicting `ORDER BY` being appended.

- [ ] **Step 8: Add the batch PV lookup to `PVAuditionRepository`**

In `src/main/java/gov/bf/ascelc/univers_audits/repository/PVAuditionRepository.java`, add the
import `java.util.List;` and this method after `findByAuditionId`:

```java

    List<PVAudition> findByAuditionIdIn(List<UUID> auditionIds);
```

- [ ] **Step 9: Create `RegistreAuditionEntryResponse`**

Create `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RegistreAuditionEntryResponse.java`:

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistreAuditionEntryResponse {
    private UUID auditionId;
    private UUID investigationId;
    private String dossierNumber;
    private IntervieweeType intervieweeType;
    private String intervieweeDisplayName;
    private Instant scheduledAt;
    private AuditionStatus status;
    private PvStatus pvStatus;
    private Integer pvVersion;

    public enum PvStatus {
        AUCUN_PV, BROUILLON, FINALISE
    }
}
```

- [ ] **Step 10: Create `RegistreAuditionsService`**

Create `src/main/java/gov/bf/ascelc/univers_audits/service/RegistreAuditionsService.java`:

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.RegistreAuditionEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RegistreAuditionsService {

    Page<RegistreAuditionEntryResponse> findAll(Pageable pageable);
}
```

- [ ] **Step 11: Create `RegistreAuditionsServiceImpl`**

Create `src/main/java/gov/bf/ascelc/univers_audits/service/impl/RegistreAuditionsServiceImpl.java`:

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.response.RegistreAuditionEntryResponse;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.service.RegistreAuditionsService;
import gov.bf.ascelc.univers_audits.shared.utils.AuditionDisplayNameMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistreAuditionsServiceImpl implements RegistreAuditionsService {

    private final AuditionRepository auditionRepository;
    private final PVAuditionRepository pvAuditionRepository;
    private final AuditionDisplayNameMasker displayNameMasker;

    @Override
    public Page<RegistreAuditionEntryResponse> findAll(Pageable pageable) {
        Page<Audition> auditions = auditionRepository.findAllForRegistre(pageable);

        Map<UUID, PVAudition> pvByAuditionId = pvAuditionRepository
                .findByAuditionIdIn(auditions.getContent().stream().map(Audition::getId).toList())
                .stream()
                .collect(Collectors.toMap(pv -> pv.getAudition().getId(), pv -> pv));

        return auditions.map(a -> toEntry(a, pvByAuditionId.get(a.getId())));
    }

    private RegistreAuditionEntryResponse toEntry(Audition audition, PVAudition pv) {
        RegistreAuditionEntryResponse.PvStatus pvStatus = RegistreAuditionEntryResponse.PvStatus.AUCUN_PV;
        Integer pvVersion = 0;
        if (pv != null) {
            pvStatus = pv.isFinalized()
                    ? RegistreAuditionEntryResponse.PvStatus.FINALISE
                    : RegistreAuditionEntryResponse.PvStatus.BROUILLON;
            pvVersion = pv.getPvVersion();
        }
        return RegistreAuditionEntryResponse.builder()
                .auditionId(audition.getId())
                .investigationId(audition.getInvestigation().getId())
                .dossierNumber(audition.getInvestigation().getDossier().getNumber())
                .intervieweeType(audition.getIntervieweeType())
                .intervieweeDisplayName(displayNameMasker.mask(audition))
                .scheduledAt(audition.getScheduledAt())
                .status(audition.getStatus())
                .pvStatus(pvStatus)
                .pvVersion(pvVersion)
                .build();
    }
}
```

- [ ] **Step 12: Create `RegistreAuditionsController`**

Create `src/main/java/gov/bf/ascelc/univers_audits/controller/RegistreAuditionsController.java`:

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.RegistreAuditionEntryResponse;
import gov.bf.ascelc.univers_audits.service.RegistreAuditionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/registre-auditions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
public class RegistreAuditionsController {

    private final RegistreAuditionsService registreAuditionsService;

    @GetMapping
    public ResponseEntity<Page<RegistreAuditionEntryResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                registreAuditionsService.findAll(PageRequest.of(page, size)));
    }
}
```

- [ ] **Step 13: Write `RegistreAuditionsServiceImplTest`**

Create `src/test/java/gov/bf/ascelc/univers_audits/service/impl/RegistreAuditionsServiceImplTest.java`:

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.model.dto.response.RegistreAuditionEntryResponse;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.shared.utils.AuditionDisplayNameMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistreAuditionsServiceImplTest {

    @Mock private AuditionRepository auditionRepository;
    @Mock private PVAuditionRepository pvAuditionRepository;
    @Mock private AuditionDisplayNameMasker displayNameMasker;

    @InjectMocks
    private RegistreAuditionsServiceImpl service;

    @Test
    void findAll_mapsAuditionsWithPvStatusAndMaskedName() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("ASCE-2026-000042").build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();

        Audition withFinalizedPv = Audition.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .intervieweeType(IntervieweeType.DECLARANT)
                .status(AuditionStatus.CONDUCTED)
                .scheduledAt(Instant.now())
                .build();
        Audition withoutPv = Audition.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .scheduledAt(Instant.now())
                .build();

        PVAudition finalizedPv = PVAudition.builder()
                .audition(withFinalizedPv)
                .pvVersion(2)
                .finalizedAt(Instant.now())
                .build();

        when(auditionRepository.findAllForRegistre(any()))
                .thenReturn(new PageImpl<>(List.of(withFinalizedPv, withoutPv)));
        when(pvAuditionRepository.findByAuditionIdIn(any()))
                .thenReturn(List.of(finalizedPv));
        when(displayNameMasker.mask(withFinalizedPv)).thenReturn("Déclarant anonyme");
        when(displayNameMasker.mask(withoutPv)).thenReturn("Partie visée X");

        Page<RegistreAuditionEntryResponse> page = service.findAll(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(2);
        RegistreAuditionEntryResponse entry1 = page.getContent().get(0);
        assertThat(entry1.getDossierNumber()).isEqualTo("ASCE-2026-000042");
        assertThat(entry1.getIntervieweeDisplayName()).isEqualTo("Déclarant anonyme");
        assertThat(entry1.getPvStatus()).isEqualTo(RegistreAuditionEntryResponse.PvStatus.FINALISE);
        assertThat(entry1.getPvVersion()).isEqualTo(2);

        RegistreAuditionEntryResponse entry2 = page.getContent().get(1);
        assertThat(entry2.getPvStatus()).isEqualTo(RegistreAuditionEntryResponse.PvStatus.AUCUN_PV);
        assertThat(entry2.getPvVersion()).isZero();
        assertThat(entry2.getIntervieweeDisplayName()).isEqualTo("Partie visée X");
    }
}
```

- [ ] **Step 14: Run the new test**

Run: `mvn -q test -Dtest=RegistreAuditionsServiceImplTest`
Expected: BUILD SUCCESS, 1/1 test passing.

- [ ] **Step 15: Compile and run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, same pre-existing `UniversAuditsApplicationTests.contextLoads`
environment-only failure (no live datasource) as every prior sub-chantier this session, no other
failures.

- [ ] **Step 16: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/utils/AuditionDisplayNameMasker.java \
        src/test/java/gov/bf/ascelc/univers_audits/shared/utils/AuditionDisplayNameMaskerTest.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/AuditionServiceImplTest.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/AuditionRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/PVAuditionRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RegistreAuditionEntryResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/RegistreAuditionsService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/RegistreAuditionsServiceImpl.java \
        src/main/java/gov/bf/ascelc/univers_audits/controller/RegistreAuditionsController.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/RegistreAuditionsServiceImplTest.java
git commit -m "feat: extract AuditionDisplayNameMasker and add RegistreAuditions read view"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** §1 (correction post-finalization, historisée) → Task 1 Steps 1-6, 8-9.
  §2 (relecture obligatoire) → Task 1 Step 8 (`markReadBack`/`finalizeSignatures`). §3 (garde de
  statut à la création) → Task 1 Step 8 (`create`). §4 (`RegistreAuditions`) → Task 2 Steps 7-13.
  Hors périmètre items (filtres sur le registre, `Departement.code=='DEI'`, notification à la
  correction, édition pré-finalisation) correctly absent from every task.
- **Task independence verified**: Task 2 does not consume anything from Task 1 (confirmed by
  re-reading both tasks' Interfaces blocks) — they may be dispatched in either order. Kept in
  plan order (1 then 2) only because Task 1 is smaller and lower-risk, no functional reason to
  reorder.
- **Compile-safety across task boundaries verified**: Task 1's file set (entity, repository,
  DTOs, service interface/impl, mapper, controller, migration, test) compiles standalone — no
  file outside this set references `PVAudition`/`PvAuditionService` in a way this task breaks.
  Task 2's file set is similarly self-contained; `AuditionServiceImpl`'s public interface
  (`AuditionService`) is untouched by the extraction, so no caller outside the file needs
  updating.
- **Type consistency verified**: `PVAudition.pvVersion`/`PvAuditionResponse.pvVersion`,
  `CorrectionPvAudition.motifCorrection`/`PvAuditionCorrectionRequest.motifCorrection`/
  `CorrectionPvAuditionResponse.motifCorrection`, and
  `AuditionDisplayNameMasker.mask(Audition): String` are named and typed identically everywhere
  they appear across both tasks.
- **Existing-test survival checked**: Task 1's `finalizeSignatures_throwsWhenSignedAndRefusedBothTrue`
  and `finalizeSignatures_throwsWhenAlreadyFinalized` both throw before reaching the new
  `readBackAt` precondition (first on the signed+refused check, second on the `isFinalized`
  check) — neither needed modification, both reproduced verbatim in Step 12's full-file rewrite.
