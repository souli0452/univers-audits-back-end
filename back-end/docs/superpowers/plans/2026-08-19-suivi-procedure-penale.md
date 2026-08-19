# Suivi de la procédure pénale (Lot 6, sous-chantier 4/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter l'enregistrement d'un suivi chronologique des phases d'une procédure pénale (`SuiviProcedurePenale`) — quatrième sous-chantier du Lot 6.

**Architecture:** Une nouvelle entité JPA (`SuiviProcedurePenale`, liste `0..n` rattachée directement à `Investigation`, FK non-unique — même patron que `MissionSuivi`), un service concret sans indicateur d'échéance (cette entité n'a pas de délai), un contrôleur dédié nesté sous `/investigations/{id}/suivi-procedure-penale`.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok (`@SuperBuilder`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-19-suivi-procedure-penale-design.md`

## Global Constraints

- Migration Liquibase : fichier `038-create-suivi-procedure-penale.sql`, format `--liquibase formatted sql` / `--changeset dev:038-create-suivi-procedure-penale` (numéro confirmé libre, dernier existant = `037`).
- **Aucun nouveau code `ParametreDelai`** — cette entité n'a volontairement aucun délai/échéance (le §7 du plan de travail ne donne aucune valeur exploitable pour ce suivi). Ne pas injecter `ParametreDelaiService` dans le service, ne pas ajouter de champ `dueAt`/`overdue`.
- Création de `SuiviProcedurePenale` refusée si `investigation.getCgeApprovedAt() == null` (`BusinessException`). **Aucune autre condition** — en particulier, **pas** de dépendance à l'existence d'un `RequeteParquet` (contrairement à `MissionSuivi` qui exige un `PlanActions`) : un dossier peut suivre une voie judiciaire sans requête au Parquet rédigée dans ce système. Pas de contrainte d'unicité — plusieurs entrées possibles par investigation.
- `SuiviProcedurePenaleRequest.phaseAt` porte `@NotNull` ; `phase` porte `@NotBlank` ; `commentaire` reste libre (nullable, aucune contrainte).
- **Écriture masquée seulement par `accessGuard.checkReadAccess(...)`** — pas de rejet confidentiel supplémentaire après (arbitrage transversal du 2026-08-19, déjà appliqué à `MissionSuiviService.ajouter` après correctif). Ne PAS ajouter de méthode `checkNotConfidentialMasked`/équivalent dans ce service — c'est une régression connue à éviter, pas un oubli à corriger.
- Masquage confidentialité en lecture (`lister`) : réponse « vide » (`suivis=List.of()`), jamais une exception — même patron que `MissionSuiviService.lister`.
- Rôles : écriture (`POST /suivi-procedure-penale`) = `hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')`. Lecture (`GET /suivi-procedure-penale`) = `hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')`.
- Aucun nouveau `DossierStatus`.
- Aucun test de contrôleur : convention déjà établie sur tout le dépôt (0 fichier `*ControllerTest.java`).

---

### Task 1: Entité, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/SuiviProcedurePenale.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/SuiviProcedurePenaleRepository.java`
- Create: `src/main/resources/db/changelog/migrations/038-create-suivi-procedure-penale.sql`

**Interfaces:**
- Consumes: `AuditEntity` (`gov.bf.ascelc.univers_audits.abstracts.AuditEntity`), `Investigation`/`Agent` (`gov.bf.ascelc.univers_audits.model.entity`, champ `id` existant).
- Produces: `SuiviProcedurePenale.{getInvestigation(), getPhaseAt(), getPhase(), getCommentaire(), getAgent(), getSubmittedAt()}`, `SuiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(UUID): List<SuiviProcedurePenale>` — consommés par la Tâche 2.

Pas de test d'entité dédié : `SuiviProcedurePenale` n'a pas de logique métier propre (même situation
que `MissionSuivi`/`PlanActions`/`TransmissionAutorite`).

- [ ] **Step 1: Créer l'entité `SuiviProcedurePenale`**

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

- [ ] **Step 2: Créer `SuiviProcedurePenaleRepository`**

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

- [ ] **Step 3: Créer la migration `038-create-suivi-procedure-penale.sql`**

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

- [ ] **Step 4: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/SuiviProcedurePenale.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/SuiviProcedurePenaleRepository.java \
        src/main/resources/db/changelog/migrations/038-create-suivi-procedure-penale.sql
git commit -m "feat: add SuiviProcedurePenale entity, repository and migration"
```

---

### Task 2: DTOs et `SuiviProcedurePenaleService`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/SuiviProcedurePenaleRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SuiviProcedurePenaleResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SuiviProcedurePenaleListResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/SuiviProcedurePenaleService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/SuiviProcedurePenaleServiceTest.java`

**Interfaces:**
- Consumes: `SuiviProcedurePenaleRepository.{findByInvestigationIdOrderByPhaseAtDesc, save}` (Task 1), `InvestigationRepository.findById(UUID): Optional<Investigation>` (existant), `DossierAccessGuard.{checkReadAccess(Dossier), canSeeConfidential()}` (existant), `AgentContextResolver.getCurrentAgent(): Agent` (existant), `Investigation.getCgeApprovedAt()` (existant).
- Produces: `SuiviProcedurePenaleService.{ajouter(UUID, SuiviProcedurePenaleRequest): SuiviProcedurePenaleListResponse, lister(UUID): SuiviProcedurePenaleListResponse}` — consommés par la Tâche 3.

- [ ] **Step 1: Créer les DTOs**

Fichier `SuiviProcedurePenaleRequest.java` :

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

Fichier `SuiviProcedurePenaleResponse.java` :

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

Fichier `SuiviProcedurePenaleListResponse.java` :

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

- [ ] **Step 2: Écrire les tests de `SuiviProcedurePenaleService` (échouent, la classe n'existe pas encore)**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.SuiviProcedurePenaleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SuiviProcedurePenaleListResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.SuiviProcedurePenaleRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuiviProcedurePenaleServiceTest {

    @Mock private SuiviProcedurePenaleRepository suiviProcedurePenaleRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private SuiviProcedurePenaleService service;

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

    private SuiviProcedurePenaleRequest.SuiviProcedurePenaleRequestBuilder validRequest() {
        return SuiviProcedurePenaleRequest.builder()
                .phaseAt(Instant.now())
                .phase("Instruction ouverte")
                .commentaire("Dossier transmis au juge d'instruction");
    }

    @Test
    void ajouter_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.ajouter(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(suiviProcedurePenaleRepository, never()).save(any());
    }

    @Test
    void ajouter_succeedsSurDossierConfidentielSiAgentHabilite() {
        dossier.setIsConfidential(true);
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(suiviProcedurePenaleRepository.save(any(SuiviProcedurePenale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of());

        SuiviProcedurePenaleListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result.getInvestigationId()).isEqualTo(investigationId);
        verify(suiviProcedurePenaleRepository).save(any(SuiviProcedurePenale.class));
        verify(accessGuard, never()).canSeeConfidential();
    }

    @Test
    void ajouter_succeedsEtRenseigneAgentEtSubmittedAt() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(suiviProcedurePenaleRepository.save(any(SuiviProcedurePenale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of());

        service.ajouter(investigationId, validRequest().build());

        verify(suiviProcedurePenaleRepository).save(argThat(s ->
                s.getInvestigation() == investigation
                        && s.getAgent() == agent
                        && s.getSubmittedAt() != null
                        && s.getPhase().equals("Instruction ouverte")));
    }

    @Test
    void ajouter_permetPlusieursEntreesPourLaMemeInvestigation() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        SuiviProcedurePenale entreeExistante = SuiviProcedurePenale.builder()
                .investigation(investigation)
                .phaseAt(Instant.now().minusSeconds(30L * 24 * 3600))
                .phase("Requête déposée")
                .agent(agent)
                .submittedAt(Instant.now().minusSeconds(30L * 24 * 3600))
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(suiviProcedurePenaleRepository.save(any(SuiviProcedurePenale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of(entreeExistante));

        SuiviProcedurePenaleListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result.getSuivis()).hasSize(1);
        verify(suiviProcedurePenaleRepository).save(argThat(s -> s.getInvestigation() == investigation));
    }

    @Test
    void ajouter_neDependPasDeRequeteParquet() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(suiviProcedurePenaleRepository.save(any(SuiviProcedurePenale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of());

        SuiviProcedurePenaleListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result).isNotNull();
        verify(suiviProcedurePenaleRepository).save(any(SuiviProcedurePenale.class));
    }

    @Test
    void lister_listeVideSiAucuneEntree() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of());

        SuiviProcedurePenaleListResponse result = service.lister(investigationId);

        assertThat(result.getSuivis()).isEmpty();
    }

    @Test
    void lister_masqueSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        SuiviProcedurePenaleListResponse result = service.lister(investigationId);

        assertThat(result.getSuivis()).isEmpty();
        verify(suiviProcedurePenaleRepository, never()).findByInvestigationIdOrderByPhaseAtDesc(any());
    }

    @Test
    void lister_leveBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.lister(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(suiviProcedurePenaleRepository, never()).findByInvestigationIdOrderByPhaseAtDesc(any());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvnw -Dtest=SuiviProcedurePenaleServiceTest test`
Expected: FAIL (compilation error — `SuiviProcedurePenaleService` n'existe pas)

- [ ] **Step 4: Créer `SuiviProcedurePenaleService`**

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

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvnw -Dtest=SuiviProcedurePenaleServiceTest test`
Expected: PASS (8 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/SuiviProcedurePenaleRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SuiviProcedurePenaleResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/SuiviProcedurePenaleListResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/SuiviProcedurePenaleService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/SuiviProcedurePenaleServiceTest.java
git commit -m "feat: add SuiviProcedurePenaleService"
```

---

### Task 3: Contrôleur `SuiviProcedurePenaleController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/SuiviProcedurePenaleController.java`

**Interfaces:**
- Consumes: `SuiviProcedurePenaleService.{ajouter, lister}` (Task 2), `ApiUrls.INVESTIGATIONS` (existant).
- Produces: endpoints `GET/POST /api/v1/investigations/{id}/suivi-procedure-penale` — dernière tâche du chantier.

Pas de test de contrôleur (convention déjà établie).

- [ ] **Step 1: Créer `SuiviProcedurePenaleController`**

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

- [ ] **Step 2: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run the full test suite**

Run: `mvnw -q -Dtest='!UniversAuditsApplicationTests' test`
Expected: BUILD SUCCESS (exclut le seul test d'intégration pré-existant qui échoue en environnement
local faute de DataSource — sans rapport avec ce chantier, déjà confirmé sur la branche de base)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/SuiviProcedurePenaleController.java
git commit -m "feat: add suivi-procedure-penale REST endpoints"
```
