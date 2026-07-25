# Référentiels versionnés — Types d'infraction et Paramètres de délai — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded infraction-delay constants scattered in `Dossier`/`Investigation`/`InvestigationServiceImpl` and the absent infraction nomenclature with two admin-manageable referential tables (`ParametreDelai`, `TypeInfraction`), per §2.2 and §7 of the ASCE-LC "Plan de travail" specification.

**Architecture:** Two new simple JPA entities extending `AuditEntity`, each with a repository, a service, and a REST controller — following the existing `Departement`/`TypeDeclarantConfig`/`PortalConfig` referential pattern already in this codebase. `ParametreDelai` resolution is centralized in a new `ParametreDelaiService` that `DossierServiceImpl` and `InvestigationServiceImpl` call instead of hardcoding day counts. Both referentials are seeded via Liquibase with only the values that are independently verifiable from the existing codebase (see Global Constraints) — additional rows are added later through the admin CRUD endpoints built in this plan, not fabricated here.

**Tech Stack:** Spring Boot 3.3.5 / Java 21, Spring Data JPA, Liquibase (raw SQL changesets), Lombok (`@SuperBuilder`), JUnit 5 + Mockito (`spring-boot-starter-test`, already on the classpath — no live datasource needed for these tests).

## Global Constraints

- Package root: `gov.bf.ascelc.univers_audits`.
- New entities extend `abstracts.AuditEntity` (gives `id`, `createdAt`, `updatedAt`, `createdById`, `updatedById`, `version` for free — do **not** redeclare any of these fields).
- Entity style: `@Getter @Setter @Entity @SuperBuilder @NoArgsConstructor @AllArgsConstructor`, explicit `@Table(name=..., indexes = {@Index(...)})`, `@Builder.Default` on every column with a Java-side default.
- Repositories: `interface X extends JpaRepository<X, UUID>` in package `repository`, annotated `@Repository`.
- Migrations live in `src/main/resources/db/changelog/migrations/` and are picked up automatically via the `includeAll` directive in `db.changelog-master.yaml` — no manual `include:` entry needed for files in that directory. Next free numeric prefix is `004` (existing: `001`, `002`, `003`).
- Exceptions: not-found → `shared.exceptions.ResourceNotFoundException` (404, handled centrally by `GlobalExceptionHandler`); duplicate `code` → `shared.exceptions.ConflictException` (409).
- Referential management (create/update/deactivate) is restricted to `CGEA` and `ADMIN_DDIC` roles — the spec's §10 names CGEA as "gestionnaire du référentiel". Use `@PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")`, matching the existing convention in `NotificationController`.
- Read access to both referentials is open to any authenticated agent (`@PreAuthorize("isAuthenticated()")`) — every role needs to read delay/infraction labels when building or reviewing a dossier.
- **No fabricated legal citations.** The specification pasted earlier in this project listed a large infraction nomenclature and delay table, but its exact wording is not available to reproduce accurately in this plan. Seeding only includes values independently confirmed from the current codebase (file:line citations below). Do not invent `articleCodePenal`/`articleLoi004` references or additional `TypeInfraction`/`ParametreDelai` codes — the admin endpoints built in Tasks 3 and 7 are how the remaining rows from the spec get entered once transcribed accurately.
- Verified `ParametreDelai` seed data (source of truth — do not change without re-checking the cited line):
  - `code=ACCUSE_RECEPTION`, `valeurJours=7` — from `Dossier.java:195` (`plusSeconds(7L * 24 * 3600)`).
  - `code=DEMANDE_COMPLEMENT`, `valeurJours=14` — from `Dossier.java:197` (`plusSeconds(14L * 24 * 3600)`).
  - `code=INVESTIGATION_DUREE_DEFAUT`, `valeurJours=90` — from `InvestigationServiceImpl.java` (`plannedDurationDays(... : 90)`, in the `open(...)` method).
  - `code=APPROBATION_CGE`, `valeurJours=20` — from the existing Javadoc comment in `DossierStatus.java` ("CGE (20j)").
- Out of scope (flag as follow-up, do not build now): effective-dated referential versioning (spec §10's "un dossier reste rattaché à la version applicable à son ouverture"). This plan seeds a flat, currently-active referential only.

---

## File Structure

```
src/main/java/gov/bf/ascelc/univers_audits/
  model/entity/ParametreDelai.java                (new)
  model/entity/TypeInfraction.java                (new)
  model/dto/request/ParametreDelaiRequest.java     (new)
  model/dto/request/TypeInfractionRequest.java     (new)
  repository/ParametreDelaiRepository.java         (new)
  repository/TypeInfractionRepository.java         (new)
  service/ParametreDelaiService.java               (new, interface)
  service/impl/ParametreDelaiServiceImpl.java       (new)
  service/TypeInfractionService.java               (new, interface)
  service/impl/TypeInfractionServiceImpl.java       (new)
  controller/ParametreDelaiController.java         (new)
  controller/TypeInfractionController.java         (new)
  model/entity/Dossier.java                        (modify: registerReception signature)
  model/entity/Investigation.java                  (no change — start() already consumes plannedDurationDays)
  service/impl/DossierServiceImpl.java              (modify: inject ParametreDelaiService, resolve delays)
  service/impl/InvestigationServiceImpl.java        (modify: inject ParametreDelaiService, remove hardcoded 90)

src/main/resources/db/changelog/migrations/
  004-create-parametre-delai.sql                    (new)
  005-create-type-infraction.sql                    (new)

src/test/java/gov/bf/ascelc/univers_audits/
  service/impl/ParametreDelaiServiceImplTest.java   (new)
  service/impl/TypeInfractionServiceImplTest.java   (new)
  model/entity/DossierTest.java                     (new)
```

---

### Task 1: `ParametreDelai` entity, repository, and migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/ParametreDelai.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/ParametreDelaiRepository.java`
- Create: `src/main/resources/db/changelog/migrations/004-create-parametre-delai.sql`

**Interfaces:**
- Produces: `ParametreDelai` entity with fields `code:String`, `libelle:String`, `valeurJours:Integer`, `joursOuvrables:Boolean`, `actif:Boolean`. `ParametreDelaiRepository.findByCode(String):Optional<ParametreDelai>`, `findByActifTrueOrderByCodeAsc():List<ParametreDelai>`.

- [ ] **Step 1: Create the entity**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "parametre_delai", indexes = {
        @Index(name = "idx_parametre_delai_code",
                columnList = "code", unique = true)
})
public class ParametreDelai extends AuditEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 300)
    private String libelle;

    @Column(name = "valeur_jours")
    private Integer valeurJours;

    @Column(name = "jours_ouvrables", nullable = false)
    @Builder.Default
    private Boolean joursOuvrables = true;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;
}
```

- [ ] **Step 2: Create the repository**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParametreDelaiRepository
        extends JpaRepository<ParametreDelai, UUID> {

    Optional<ParametreDelai> findByCode(String code);

    List<ParametreDelai> findByActifTrueOrderByCodeAsc();
}
```

- [ ] **Step 3: Create the migration with the verified seed rows**

```sql
--liquibase formatted sql
--changeset dev:004-create-parametre-delai

CREATE TABLE parametre_delai (
    id               UUID PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL,
    libelle          VARCHAR(300) NOT NULL,
    valeur_jours     INTEGER,
    jours_ouvrables  BOOLEAN      NOT NULL DEFAULT TRUE,
    actif            BOOLEAN      NOT NULL DEFAULT TRUE,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP,
    created_by_id    VARCHAR(100),
    updated_by_id    VARCHAR(100)
);

CREATE UNIQUE INDEX idx_parametre_delai_code ON parametre_delai (code);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'ACCUSE_RECEPTION', 'Délai d''accusé de réception du dossier', 7, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_COMPLEMENT', 'Délai de réponse à une demande de complément d''information', 14, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'INVESTIGATION_DUREE_DEFAUT', 'Durée par défaut d''une investigation', 90, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'APPROBATION_CGE', 'Délai d''approbation du CGE', 20, TRUE, TRUE, 0, now());
```

- [ ] **Step 4: Compile to verify no syntax errors**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/ParametreDelai.java src/main/java/gov/bf/ascelc/univers_audits/repository/ParametreDelaiRepository.java src/main/resources/db/changelog/migrations/004-create-parametre-delai.sql
git commit -m "feat: add ParametreDelai referential entity, repository and migration"
```

---

### Task 2: `ParametreDelaiService` with resolution logic and unit tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ParametreDelaiRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/ParametreDelaiService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImplTest.java`

**Interfaces:**
- Consumes: `ParametreDelaiRepository.findByCode`, `.findByActifTrueOrderByCodeAsc`, `.findAll`, `.save` (from Task 1).
- Produces: `ParametreDelaiService.resolveDelaiJours(String code):int` (throws `ResourceNotFoundException` if missing/inactive — this is what Task 4 will call), `.findAllActifs():List<ParametreDelai>`, `.findAll():List<ParametreDelai>`, `.update(String code, ParametreDelaiRequest req):ParametreDelai`.

- [ ] **Step 1: Write the failing tests**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.ParametreDelaiRequest;
import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;
import gov.bf.ascelc.univers_audits.repository.ParametreDelaiRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParametreDelaiServiceImplTest {

    @Mock
    private ParametreDelaiRepository repository;

    @InjectMocks
    private ParametreDelaiServiceImpl service;

    @Test
    void resolveDelaiJours_returnsValueForActiveCode() {
        ParametreDelai delai = ParametreDelai.builder()
                .code("ACCUSE_RECEPTION")
                .libelle("Délai d'accusé de réception")
                .valeurJours(7)
                .actif(true)
                .build();
        when(repository.findByCode("ACCUSE_RECEPTION"))
                .thenReturn(Optional.of(delai));

        int result = service.resolveDelaiJours("ACCUSE_RECEPTION");

        assertThat(result).isEqualTo(7);
    }

    @Test
    void resolveDelaiJours_throwsWhenCodeUnknown() {
        when(repository.findByCode("INCONNU"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveDelaiJours("INCONNU"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveDelaiJours_throwsWhenCodeInactive() {
        ParametreDelai delai = ParametreDelai.builder()
                .code("ACCUSE_RECEPTION")
                .libelle("Délai d'accusé de réception")
                .valeurJours(7)
                .actif(false)
                .build();
        when(repository.findByCode("ACCUSE_RECEPTION"))
                .thenReturn(Optional.of(delai));

        assertThatThrownBy(() -> service.resolveDelaiJours("ACCUSE_RECEPTION"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_changesValeurJoursAndSaves() {
        ParametreDelai existing = ParametreDelai.builder()
                .code("ACCUSE_RECEPTION")
                .libelle("Délai d'accusé de réception")
                .valeurJours(7)
                .actif(true)
                .build();
        when(repository.findByCode("ACCUSE_RECEPTION"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(ParametreDelai.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ParametreDelaiRequest request = ParametreDelaiRequest.builder()
                .libelle("Délai d'accusé de réception")
                .valeurJours(10)
                .joursOuvrables(true)
                .actif(true)
                .build();

        ParametreDelai result = service.update("ACCUSE_RECEPTION", request);

        assertThat(result.getValeurJours()).isEqualTo(10);
        verify(repository).save(existing);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail (compile error — types don't exist yet)**

Run: `mvn -q -Dtest=ParametreDelaiServiceImplTest test`
Expected: FAIL — compilation error, `ParametreDelaiRequest`/`ParametreDelaiService`/`ParametreDelaiServiceImpl` not found

- [ ] **Step 3: Create the request DTO**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametreDelaiRequest {

    @NotBlank(message = "Le libellé est obligatoire")
    @Size(max = 300)
    private String libelle;

    private Integer valeurJours;

    @NotNull(message = "L'indicateur jours ouvrables est obligatoire")
    private Boolean joursOuvrables;

    @NotNull(message = "L'indicateur actif est obligatoire")
    private Boolean actif;
}
```

- [ ] **Step 4: Create the service interface**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ParametreDelaiRequest;
import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;

import java.util.List;

public interface ParametreDelaiService {

    int resolveDelaiJours(String code);

    List<ParametreDelai> findAllActifs();

    List<ParametreDelai> findAll();

    ParametreDelai update(String code, ParametreDelaiRequest request);
}
```

- [ ] **Step 5: Implement the service**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.ParametreDelaiRequest;
import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;
import gov.bf.ascelc.univers_audits.repository.ParametreDelaiRepository;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametreDelaiServiceImpl implements ParametreDelaiService {

    private final ParametreDelaiRepository repository;

    @Override
    public int resolveDelaiJours(String code) {
        ParametreDelai delai = repository.findByCode(code)
                .filter(ParametreDelai::getActif)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paramètre de délai introuvable ou inactif : " + code));

        if (delai.getValeurJours() == null) {
            throw new ResourceNotFoundException(
                    "Paramètre de délai sans valeur configurée : " + code);
        }
        return delai.getValeurJours();
    }

    @Override
    public List<ParametreDelai> findAllActifs() {
        return repository.findByActifTrueOrderByCodeAsc();
    }

    @Override
    public List<ParametreDelai> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public ParametreDelai update(String code, ParametreDelaiRequest request) {
        ParametreDelai delai = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paramètre de délai introuvable : " + code));

        delai.setLibelle(request.getLibelle());
        delai.setValeurJours(request.getValeurJours());
        delai.setJoursOuvrables(request.getJoursOuvrables());
        delai.setActif(request.getActif());

        ParametreDelai saved = repository.save(delai);
        log.info("[ParametreDelai] '{}' mis à jour", code);
        return saved;
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -q -Dtest=ParametreDelaiServiceImplTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ParametreDelaiRequest.java src/main/java/gov/bf/ascelc/univers_audits/service/ParametreDelaiService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImplTest.java
git commit -m "feat: add ParametreDelai resolution service with unit tests"
```

---

### Task 3: `ParametreDelaiController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/ParametreDelaiController.java`

**Interfaces:**
- Consumes: `ParametreDelaiService.findAll()`, `.findAllActifs()`, `.update(String, ParametreDelaiRequest)` (from Task 2).

- [ ] **Step 1: Create the controller**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ParametreDelaiRequest;
import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/parametres-delai")
@RequiredArgsConstructor
public class ParametreDelaiController {

    private final ParametreDelaiService parametreDelaiService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ParametreDelai>> getActifs() {
        return ResponseEntity.ok(parametreDelaiService.findAllActifs());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<ParametreDelai>> getAll() {
        return ResponseEntity.ok(parametreDelaiService.findAll());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<ParametreDelai> update(
            @PathVariable String code,
            @Valid @RequestBody ParametreDelaiRequest request) {
        return ResponseEntity.ok(parametreDelaiService.update(code, request));
    }
}
```

- [ ] **Step 2: Compile to verify no syntax errors**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/ParametreDelaiController.java
git commit -m "feat: add ParametreDelai REST endpoints"
```

---

### Task 4: Wire `ParametreDelaiService` into `Dossier` and `Investigation` — remove hardcoded delays

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java:191-199`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java:42-53` (constructor field) and `:222-235` (`registerReception` call site)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java:39-48` (constructor field) and the `open(...)` method around line 159-167
- Test: `src/test/java/gov/bf/ascelc/univers_audits/model/entity/DossierTest.java`

**Interfaces:**
- Consumes: `ParametreDelaiService.resolveDelaiJours(String):int` (from Task 2).
- Produces: `Dossier.registerReception(Agent agent, int accuseReceptionJours, int demandeComplementJours)` — the new signature every caller (only `DossierServiceImpl` today) must use.

- [ ] **Step 1: Write the failing test for the new `Dossier.registerReception` signature**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DossierTest {

    @Test
    void registerReception_setsDeadlinesFromGivenDelays() {
        Dossier dossier = Dossier.builder().build();
        Agent agent = Agent.builder().build();

        Instant before = Instant.now();
        dossier.registerReception(agent, 7, 14);
        Instant after = Instant.now();

        assertThat(dossier.getReceptionDate()).isBetween(before, after);
        assertThat(dossier.getAcknowledgmentDeadline())
                .isCloseTo(dossier.getReceptionDate().plus(Duration.ofDays(7)),
                        org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(dossier.getAdditionalInfoDeadline())
                .isCloseTo(dossier.getReceptionDate().plus(Duration.ofDays(14)),
                        org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(dossier.getStatus()).isEqualTo(DossierStatus.RECU);
        assertThat(dossier.getAgentInCharge()).isEqualTo(agent);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=DossierTest test`
Expected: FAIL — compilation error, `registerReception(Agent, int, int)` does not exist (current signature is `registerReception(Agent)`)

- [ ] **Step 3: Change `Dossier.registerReception` to accept explicit delay parameters**

In `model/entity/Dossier.java`, replace lines 191-199:

```java
    public void registerReception(Agent agent) {
        this.receptionDate        = Instant.now();
        this.agentInCharge        = agent;
        this.acknowledgmentDeadline =
                receptionDate.plusSeconds(7L * 24 * 3600);
        this.additionalInfoDeadline =
                receptionDate.plusSeconds(14L * 24 * 3600);
        this.status = DossierStatus.RECU;
    }
```

with:

```java
    public void registerReception(Agent agent,
                                   int accuseReceptionJours,
                                   int demandeComplementJours) {
        this.receptionDate        = Instant.now();
        this.agentInCharge        = agent;
        this.acknowledgmentDeadline =
                receptionDate.plusSeconds(accuseReceptionJours * 24L * 3600);
        this.additionalInfoDeadline =
                receptionDate.plusSeconds(demandeComplementJours * 24L * 3600);
        this.status = DossierStatus.RECU;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=DossierTest test`
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: Inject `ParametreDelaiService` into `DossierServiceImpl` and update the call site**

In `service/impl/DossierServiceImpl.java`, add the field alongside the other injected collaborators (after line 53, `private final DossierAuditRecorder auditRecorder;`):

```java
    private final ParametreDelaiService          parametreDelaiService;
```

Add the import near the other `service` imports:

```java
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
```

Replace line 235 (`dossier.registerReception(agent);`) with:

```java
        int accuseReceptionJours = parametreDelaiService
                .resolveDelaiJours("ACCUSE_RECEPTION");
        int demandeComplementJours = parametreDelaiService
                .resolveDelaiJours("DEMANDE_COMPLEMENT");
        dossier.registerReception(agent, accuseReceptionJours, demandeComplementJours);
```

- [ ] **Step 6: Inject `ParametreDelaiService` into `InvestigationServiceImpl` and remove the hardcoded `90`**

In `service/impl/InvestigationServiceImpl.java`, add the field alongside the other injected collaborators (after line 48, `private final DossierAuditRecorder auditRecorder;`):

```java
    private final ParametreDelaiService parametreDelaiService;
```

Add the import near the other `service` imports:

```java
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
```

Replace the `plannedDurationDays(...)` block in the `open(...)` method:

```java
                .plannedDurationDays(
                        request.getPlannedDurationDays() != null
                                ? request.getPlannedDurationDays()
                                : 90)
```

with:

```java
                .plannedDurationDays(
                        request.getPlannedDurationDays() != null
                                ? request.getPlannedDurationDays()
                                : parametreDelaiService.resolveDelaiJours(
                                        "INVESTIGATION_DUREE_DEFAUT"))
```

- [ ] **Step 7: Compile the full project to verify both services wire correctly**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 8: Run the full unit test suite**

Run: `mvn -q -Dtest=DossierTest,ParametreDelaiServiceImplTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/model/entity/DossierTest.java
git commit -m "refactor: resolve dossier/investigation delays from ParametreDelai referential"
```

---

### Task 5: `TypeInfraction` entity, repository, and migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/TypeInfraction.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/TypeInfractionRepository.java`
- Create: `src/main/resources/db/changelog/migrations/005-create-type-infraction.sql`

**Interfaces:**
- Produces: `TypeInfraction` entity with fields `code:String`, `libelle:String`, `articleCodePenal:String`, `articleLoi004:String`, `impliqueDdip:Boolean`, `actif:Boolean`, `ordre:Integer`. `TypeInfractionRepository.findByCode(String):Optional<TypeInfraction>`, `findByActifTrueOrderByOrdreAsc():List<TypeInfraction>`.

- [ ] **Step 1: Create the entity**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "type_infraction", indexes = {
        @Index(name = "idx_type_infraction_code",
                columnList = "code", unique = true)
})
public class TypeInfraction extends AuditEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 300)
    private String libelle;

    @Column(name = "article_code_penal", length = 100)
    private String articleCodePenal;

    @Column(name = "article_loi_004", length = 100)
    private String articleLoi004;

    @Column(name = "implique_ddip", nullable = false)
    @Builder.Default
    private Boolean impliqueDdip = false;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(name = "ordre")
    @Builder.Default
    private Integer ordre = 0;
}
```

- [ ] **Step 2: Create the repository**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TypeInfractionRepository
        extends JpaRepository<TypeInfraction, UUID> {

    Optional<TypeInfraction> findByCode(String code);

    List<TypeInfraction> findByActifTrueOrderByOrdreAsc();
}
```

- [ ] **Step 3: Create the migration**

Only the two entries whose exact label and DDIP-referral flag were confirmed earlier in this project are seeded; the full nomenclature must be entered via the admin endpoint (Task 7) once transcribed accurately from the source specification.

```sql
--liquibase formatted sql
--changeset dev:005-create-type-infraction

CREATE TABLE type_infraction (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL,
    libelle             VARCHAR(300) NOT NULL,
    article_code_penal  VARCHAR(100),
    article_loi_004     VARCHAR(100),
    implique_ddip       BOOLEAN      NOT NULL DEFAULT FALSE,
    actif               BOOLEAN      NOT NULL DEFAULT TRUE,
    ordre               INTEGER      DEFAULT 0,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by_id       VARCHAR(100),
    updated_by_id       VARCHAR(100)
);

CREATE UNIQUE INDEX idx_type_infraction_code ON type_infraction (code);

INSERT INTO type_infraction (id, code, libelle, implique_ddip, actif, ordre, version, created_at)
VALUES
    (gen_random_uuid(), 'ENRICHISSEMENT_ILLICITE', 'Enrichissement illicite', TRUE, TRUE, 1, 0, now()),
    (gen_random_uuid(), 'DEFAUT_FAUSSE_DECLARATION_PATRIMOINE', 'Défaut ou fausse déclaration d''intérêt ou de patrimoine', TRUE, TRUE, 2, 0, now());
```

- [ ] **Step 4: Compile to verify no syntax errors**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/TypeInfraction.java src/main/java/gov/bf/ascelc/univers_audits/repository/TypeInfractionRepository.java src/main/resources/db/changelog/migrations/005-create-type-infraction.sql
git commit -m "feat: add TypeInfraction referential entity, repository and migration"
```

---

### Task 6: `TypeInfractionService` with unit tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/TypeInfractionRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/TypeInfractionService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/TypeInfractionServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/TypeInfractionServiceImplTest.java`

**Interfaces:**
- Consumes: `TypeInfractionRepository.findByCode`, `.findByActifTrueOrderByOrdreAsc`, `.findAll`, `.save`, `.existsByCode` (from Task 5 — `existsByCode` needs to be added to the repository in Step 3 below).
- Produces: `TypeInfractionService.findAllActifs():List<TypeInfraction>`, `.findAll():List<TypeInfraction>`, `.create(TypeInfractionRequest):TypeInfraction`, `.update(String code, TypeInfractionRequest):TypeInfraction`.

- [ ] **Step 1: Write the failing tests**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.TypeInfractionRequest;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TypeInfractionServiceImplTest {

    @Mock
    private TypeInfractionRepository repository;

    @InjectMocks
    private TypeInfractionServiceImpl service;

    @Test
    void create_savesNewInfractionType() {
        when(repository.existsByCode("TRAFIC_INFLUENCE")).thenReturn(false);
        when(repository.save(any(TypeInfraction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TypeInfractionRequest request = TypeInfractionRequest.builder()
                .code("TRAFIC_INFLUENCE")
                .libelle("Trafic d'influence")
                .impliqueDdip(false)
                .actif(true)
                .ordre(3)
                .build();

        TypeInfraction result = service.create(request);

        assertThat(result.getCode()).isEqualTo("TRAFIC_INFLUENCE");
        assertThat(result.getLibelle()).isEqualTo("Trafic d'influence");
        verify(repository).save(any(TypeInfraction.class));
    }

    @Test
    void create_throwsConflictWhenCodeAlreadyExists() {
        when(repository.existsByCode("ENRICHISSEMENT_ILLICITE")).thenReturn(true);

        TypeInfractionRequest request = TypeInfractionRequest.builder()
                .code("ENRICHISSEMENT_ILLICITE")
                .libelle("Enrichissement illicite")
                .impliqueDdip(true)
                .actif(true)
                .ordre(1)
                .build();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_throwsWhenCodeUnknown() {
        when(repository.findByCode("INCONNU")).thenReturn(Optional.empty());

        TypeInfractionRequest request = TypeInfractionRequest.builder()
                .code("INCONNU")
                .libelle("Libellé")
                .impliqueDdip(false)
                .actif(true)
                .ordre(1)
                .build();

        assertThatThrownBy(() -> service.update("INCONNU", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=TypeInfractionServiceImplTest test`
Expected: FAIL — compilation error, `TypeInfractionRequest`/`TypeInfractionService`/`TypeInfractionServiceImpl` and `TypeInfractionRepository.existsByCode` not found

- [ ] **Step 3: Add `existsByCode` to the repository**

In `repository/TypeInfractionRepository.java`, add:

```java
    boolean existsByCode(String code);
```

- [ ] **Step 4: Create the request DTO**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypeInfractionRequest {

    @NotBlank(message = "Le code est obligatoire")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Le libellé est obligatoire")
    @Size(max = 300)
    private String libelle;

    @Size(max = 100)
    private String articleCodePenal;

    @Size(max = 100)
    private String articleLoi004;

    @NotNull(message = "L'indicateur DDIP est obligatoire")
    private Boolean impliqueDdip;

    @NotNull(message = "L'indicateur actif est obligatoire")
    private Boolean actif;

    private Integer ordre;
}
```

- [ ] **Step 5: Create the service interface**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.TypeInfractionRequest;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;

import java.util.List;

public interface TypeInfractionService {

    List<TypeInfraction> findAllActifs();

    List<TypeInfraction> findAll();

    TypeInfraction create(TypeInfractionRequest request);

    TypeInfraction update(String code, TypeInfractionRequest request);
}
```

- [ ] **Step 6: Implement the service**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.TypeInfractionRequest;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.service.TypeInfractionService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TypeInfractionServiceImpl implements TypeInfractionService {

    private final TypeInfractionRepository repository;

    @Override
    public List<TypeInfraction> findAllActifs() {
        return repository.findByActifTrueOrderByOrdreAsc();
    }

    @Override
    public List<TypeInfraction> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public TypeInfraction create(TypeInfractionRequest request) {
        if (repository.existsByCode(request.getCode())) {
            throw new ConflictException(
                    "Un type d'infraction avec ce code existe déjà : " + request.getCode());
        }

        TypeInfraction infraction = TypeInfraction.builder()
                .code(request.getCode())
                .libelle(request.getLibelle())
                .articleCodePenal(request.getArticleCodePenal())
                .articleLoi004(request.getArticleLoi004())
                .impliqueDdip(request.getImpliqueDdip())
                .actif(request.getActif())
                .ordre(request.getOrdre() != null ? request.getOrdre() : 0)
                .build();

        TypeInfraction saved = repository.save(infraction);
        log.info("[TypeInfraction] '{}' créé", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public TypeInfraction update(String code, TypeInfractionRequest request) {
        TypeInfraction infraction = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Type d'infraction introuvable : " + code));

        infraction.setLibelle(request.getLibelle());
        infraction.setArticleCodePenal(request.getArticleCodePenal());
        infraction.setArticleLoi004(request.getArticleLoi004());
        infraction.setImpliqueDdip(request.getImpliqueDdip());
        infraction.setActif(request.getActif());
        infraction.setOrdre(request.getOrdre() != null ? request.getOrdre() : 0);

        TypeInfraction saved = repository.save(infraction);
        log.info("[TypeInfraction] '{}' mis à jour", code);
        return saved;
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn -q -Dtest=TypeInfractionServiceImplTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 8: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/repository/TypeInfractionRepository.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/TypeInfractionRequest.java src/main/java/gov/bf/ascelc/univers_audits/service/TypeInfractionService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/TypeInfractionServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/TypeInfractionServiceImplTest.java
git commit -m "feat: add TypeInfraction service with create/update and unit tests"
```

---

### Task 7: `TypeInfractionController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/TypeInfractionController.java`

**Interfaces:**
- Consumes: `TypeInfractionService.findAll()`, `.findAllActifs()`, `.create(TypeInfractionRequest)`, `.update(String, TypeInfractionRequest)` (from Task 6).

- [ ] **Step 1: Create the controller**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.TypeInfractionRequest;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.service.TypeInfractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/types-infraction")
@RequiredArgsConstructor
public class TypeInfractionController {

    private final TypeInfractionService typeInfractionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TypeInfraction>> getActifs() {
        return ResponseEntity.ok(typeInfractionService.findAllActifs());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<TypeInfraction>> getAll() {
        return ResponseEntity.ok(typeInfractionService.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<TypeInfraction> create(
            @Valid @RequestBody TypeInfractionRequest request) {
        return ResponseEntity.ok(typeInfractionService.create(request));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<TypeInfraction> update(
            @PathVariable String code,
            @Valid @RequestBody TypeInfractionRequest request) {
        return ResponseEntity.ok(typeInfractionService.update(code, request));
    }
}
```

- [ ] **Step 2: Compile the full project**

Run: `mvn -q -pl . compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Run the entire new test suite together**

Run: `mvn -q -Dtest=ParametreDelaiServiceImplTest,TypeInfractionServiceImplTest,DossierTest test`
Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/TypeInfractionController.java
git commit -m "feat: add TypeInfraction REST endpoints"
```

---

## Self-Review

**Spec coverage:**
- §2.2 (qualifications pénales / infraction nomenclature) → `TypeInfraction` entity + admin CRUD (Tasks 5-7). Full nomenclature transcription intentionally deferred to admin data entry (see Global Constraints) rather than fabricated.
- §7 (délais) → `ParametreDelai` entity + `resolveDelaiJours` (Tasks 1-2), wired into the two places that currently hardcode delays (Task 4).
- §10 ("CGEA = gestionnaire du référentiel") → both admin write endpoints restricted to `CGEA`/`ADMIN_DDIC` (Tasks 3, 7).
- §13.1 arbitration on the CGE approval delay → resolved using the code's own existing 20-day Javadoc comment, seeded as `APPROBATION_CGE` (Task 1) — not yet consumed by any call site because no code currently enforces that specific deadline; consuming it is future work once that transition is implemented.
- Effective-dated referential versioning (§10) → explicitly out of scope, flagged in Global Constraints.

**Placeholder scan:** no TBD/TODO, no "add appropriate error handling", no code omitted — every step shows complete, compilable code.

**Type consistency:** `Dossier.registerReception(Agent, int, int)` (Task 4, Step 3) matches its only call site update (Task 4, Step 5). `ParametreDelaiService.resolveDelaiJours(String):int` (Task 2) matches its two call sites (Task 4, Steps 5-6). `TypeInfractionRepository.existsByCode` (Task 6, Step 3) matches its use in `TypeInfractionServiceImpl.create` (Task 6, Step 6).

---

**Plan complete and saved to `docs/superpowers/plans/2026-07-25-referentiels-delais-infractions.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
