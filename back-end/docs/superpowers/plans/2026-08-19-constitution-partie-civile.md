# Constitution de partie civile (Lot 6, sous-chantier 5/5, dernier) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter l'enregistrement de l'acte par lequel l'ASCE-LC se constitue partie civile au nom de l'État (`ConstitutionPartieCivile`) — cinquième et dernier sous-chantier du Lot 6.

**Architecture:** Une nouvelle entité JPA (`ConstitutionPartieCivile`, entrée unique 1:1 avec `Investigation`, FK unique — même patron que `TransmissionAutorite`), un service concret suivant exactement le patron `TransmissionAutoriteService` (rejet confidentiel classique en écriture, masquage par exception en lecture), un contrôleur dédié nesté sous `/investigations/{id}/constitution-partie-civile`.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok (`@SuperBuilder`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-19-constitution-partie-civile-design.md`

## Global Constraints

- Migration Liquibase : fichier `039-create-constitution-partie-civile.sql`, format `--liquibase formatted sql` / `--changeset dev:039-create-constitution-partie-civile` (numéro confirmé libre, dernier existant = `038`).
- **Aucun nouveau code `ParametreDelai`** — cette entité n'a volontairement aucun délai. Ne pas injecter `ParametreDelaiService`, ne pas ajouter de champ `dueAt`/`overdue`.
- Cardinalité **1:1** : `investigation_id` NOT NULL UNIQUE (contrainte en base ET annotation JPA `unique = true`) — contrairement à `MissionSuivi`/`SuiviProcedurePenale` (listes 0..n), c'est le même patron que `TransmissionAutorite`/`PlanActions`.
- Création refusée si `investigation.getCgeApprovedAt() == null` (`BusinessException`) OU si une constitution existe déjà pour cette investigation (`BusinessException`). Pas de dépendance à `SuiviProcedurePenale`.
- `ConstitutionPartieCivileRequest.justification` porte `@NotBlank` ; `montantReclame` (`BigDecimal`) reste libre, **sans** `@NotNull`/`@DecimalMin` — le montant n'est souvent pas connu au moment de l'acte.
- **Patron de confidentialité classique `TransmissionAutorite`, PAS la divergence des sous-chantiers 3/5-4/5** : écriture rejetée par `checkNotConfidentialMasked` (méthode privée qui lève `BusinessException`), lecture masquée par `ResourceNotFoundException` intercepté dans le contrôleur (`GET` → `204`). `CGE` étant déjà dans l'ensemble privilégié de `DossierAccessGuard.canSeeConfidential()`, il n'y a pas lieu d'appliquer ici la divergence checkReadAccess-seul adoptée pour `MissionSuivi`/`SuiviProcedurePenale`. Ne PAS retirer `checkNotConfidentialMasked` par réflexe de cohérence avec le sous-chantier précédent — ce serait une régression, pas un alignement.
- Rôles : écriture (`POST /constitution-partie-civile`) = `hasAnyRole('CGE','ADMIN_DDIC')`. Lecture (`GET /constitution-partie-civile`) = `hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')`.
- Aucun nouveau `DossierStatus`.
- Aucun test de contrôleur : convention déjà établie sur tout le dépôt (0 fichier `*ControllerTest.java`).

---

### Task 1: Entité, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/ConstitutionPartieCivile.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/ConstitutionPartieCivileRepository.java`
- Create: `src/main/resources/db/changelog/migrations/039-create-constitution-partie-civile.sql`

**Interfaces:**
- Consumes: `AuditEntity` (`gov.bf.ascelc.univers_audits.abstracts.AuditEntity`), `Investigation`/`Agent` (`gov.bf.ascelc.univers_audits.model.entity`, champ `id` existant).
- Produces: `ConstitutionPartieCivile.{getInvestigation(), getConstitueAt(), getMontantReclame(), getJustification(), getConstitueePar(), getSubmittedAt()}`, `ConstitutionPartieCivileRepository.findByInvestigationId(UUID): Optional<ConstitutionPartieCivile>` — consommés par la Tâche 2.

Pas de test d'entité dédié : `ConstitutionPartieCivile` n'a pas de logique métier propre (même
situation que `TransmissionAutorite`/`PlanActions`).

- [ ] **Step 1: Créer l'entité `ConstitutionPartieCivile`**

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

- [ ] **Step 2: Créer `ConstitutionPartieCivileRepository`**

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

- [ ] **Step 3: Créer la migration `039-create-constitution-partie-civile.sql`**

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

- [ ] **Step 4: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/ConstitutionPartieCivile.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/ConstitutionPartieCivileRepository.java \
        src/main/resources/db/changelog/migrations/039-create-constitution-partie-civile.sql
git commit -m "feat: add ConstitutionPartieCivile entity, repository and migration"
```

---

### Task 2: DTOs et `ConstitutionPartieCivileService`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ConstitutionPartieCivileRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/ConstitutionPartieCivileResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/ConstitutionPartieCivileService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/ConstitutionPartieCivileServiceTest.java`

**Interfaces:**
- Consumes: `ConstitutionPartieCivileRepository.{findByInvestigationId, save}` (Task 1), `InvestigationRepository.findById(UUID): Optional<Investigation>` (existant), `DossierAccessGuard.{checkReadAccess(Dossier), canSeeConfidential()}` (existant), `AgentContextResolver.getCurrentAgent(): Agent` (existant), `Investigation.getCgeApprovedAt()` (existant).
- Produces: `ConstitutionPartieCivileService.{creer(UUID, ConstitutionPartieCivileRequest): ConstitutionPartieCivileResponse, getOrThrow(UUID): ConstitutionPartieCivileResponse}` — consommés par la Tâche 3.

- [ ] **Step 1: Créer les DTOs**

Fichier `ConstitutionPartieCivileRequest.java` :

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

Fichier `ConstitutionPartieCivileResponse.java` :

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

- [ ] **Step 2: Écrire les tests de `ConstitutionPartieCivileService` (échouent, la classe n'existe pas encore)**

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConstitutionPartieCivileServiceTest {

    @Mock private ConstitutionPartieCivileRepository constitutionPartieCivileRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private ConstitutionPartieCivileService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .dossier(dossier)
                .cgeApprovedAt(Instant.now())
                .build();
    }

    private ConstitutionPartieCivileRequest.ConstitutionPartieCivileRequestBuilder validRequest() {
        return ConstitutionPartieCivileRequest.builder()
                .justification("Prejudice financier direct subi par l'Etat, preuve suffisante au dossier")
                .montantReclame(new BigDecimal("15000000.00"));
    }

    @Test
    void creer_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.creer(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(constitutionPartieCivileRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiConstitutionDejaExistante() {
        ConstitutionPartieCivile existante = ConstitutionPartieCivile.builder()
                .investigation(investigation)
                .constitueAt(Instant.now())
                .justification("Deja constitue")
                .submittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(constitutionPartieCivileRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(existante));

        assertThatThrownBy(() -> service.creer(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(constitutionPartieCivileRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.creer(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(constitutionPartieCivileRepository, never()).save(any());
    }

    @Test
    void creer_succeedsEtRenseigneConstitueAtEtConstitueePar() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(constitutionPartieCivileRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(constitutionPartieCivileRepository.save(any(ConstitutionPartieCivile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConstitutionPartieCivileResponse result = service.creer(investigationId, validRequest().build());

        assertThat(result.getConstitueeParNom()).isEqualTo("Jean Ouedraogo");
        assertThat(result.getSubmittedAt()).isNotNull();
        verify(constitutionPartieCivileRepository).save(argThat(c ->
                c.getInvestigation() == investigation
                        && c.getConstitueePar() == agent
                        && c.getConstitueAt() != null
                        && c.getJustification().equals(
                                "Prejudice financier direct subi par l'Etat, preuve suffisante au dossier")));
    }

    @Test
    void creer_succeedsSansMontantReclame() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(constitutionPartieCivileRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(constitutionPartieCivileRepository.save(any(ConstitutionPartieCivile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConstitutionPartieCivileRequest request = ConstitutionPartieCivileRequest.builder()
                .justification("Montant du prejudice non encore chiffre par les experts")
                .build();

        ConstitutionPartieCivileResponse result = service.creer(investigationId, request);

        assertThat(result.getMontantReclame()).isNull();
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiAucuneConstitution() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(constitutionPartieCivileRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrThrow_masqueSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(constitutionPartieCivileRepository, never()).findByInvestigationId(any());
    }

    @Test
    void getOrThrow_leveBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(constitutionPartieCivileRepository, never()).findByInvestigationId(any());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvnw -Dtest=ConstitutionPartieCivileServiceTest test`
Expected: FAIL (compilation error — `ConstitutionPartieCivileService` n'existe pas)

- [ ] **Step 4: Créer `ConstitutionPartieCivileService`**

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

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvnw -Dtest=ConstitutionPartieCivileServiceTest test`
Expected: PASS (8 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ConstitutionPartieCivileRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/ConstitutionPartieCivileResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/ConstitutionPartieCivileService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/ConstitutionPartieCivileServiceTest.java
git commit -m "feat: add ConstitutionPartieCivileService"
```

---

### Task 3: Contrôleur `ConstitutionPartieCivileController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/ConstitutionPartieCivileController.java`

**Interfaces:**
- Consumes: `ConstitutionPartieCivileService.{creer, getOrThrow}` (Task 2), `ApiUrls.INVESTIGATIONS` (existant).
- Produces: endpoints `GET/POST /api/v1/investigations/{id}/constitution-partie-civile` — dernière tâche du chantier, et dernière tâche du Lot 6.

Pas de test de contrôleur (convention déjà établie).

- [ ] **Step 1: Créer `ConstitutionPartieCivileController`**

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

Contrairement à `PlanActionsController.getStatus`/`MissionSuiviController.lister`/
`SuiviProcedurePenaleController.lister` (toujours 200), `getConstitution` fait un `try/catch
ResourceNotFoundException` → 204, exactement comme `TransmissionAutoriteController.getTransmission`
— ce n'est pas un oubli de la divergence des sous-chantiers 2/5-3/5-4/5, cette entité n'a aucune
échéance à exposer avant dépôt.

- [ ] **Step 2: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run the full test suite**

Run: `mvnw -q -Dtest='!UniversAuditsApplicationTests' test`
Expected: BUILD SUCCESS (exclut le seul test d'intégration pré-existant qui échoue en environnement
local faute de DataSource — sans rapport avec ce chantier, déjà confirmé sur la branche de base)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/ConstitutionPartieCivileController.java
git commit -m "feat: add constitution-partie-civile REST endpoints"
```
