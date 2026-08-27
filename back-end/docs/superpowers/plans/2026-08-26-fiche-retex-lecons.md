# Fiche RETEX et leçons à partager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter la rédaction d'un bilan RETEX rétrospectif par investigation clôturée, et la publication de leçons consultables extraites de ces bilans.

**Architecture:** 2 nouvelles entités JPA (`FicheRetex` en `@OneToOne` sur `Investigation`, `LeconAPartager` en `@OneToOne` sur `FicheRetex`), 2 services suivant le patron `MissionSuiviService` déjà établi (`DossierAccessGuard`, `@Transactional`), 2 contrôleurs REST.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, Liquibase, JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-26-fiche-retex-lecons-design.md`

## Global Constraints

- `FicheRetex` se rattache à `Investigation` (`@OneToOne`), jamais à `MissionSuivi` ni `VisiteTerrain` — décision de conception documentée dans la spec (aucune des deux ne recoupe la sémantique RETEX).
- Une seule fiche RETEX par investigation, une seule leçon par fiche — vérifier explicitement l'absence d'un enregistrement existant avant insertion (pas seulement compter sur la contrainte SQL unique), même leçon que le sous-chantier précédent.
- Toute méthode de service qui charge une `Investigation` doit appeler `dossierAccessGuard.checkReadAccess(investigation.getDossier())` — **sauf** `LeconAPartagerService.lister()`, qui ne doit jamais faire ce contrôle (décision de conception explicite : une leçon publiée est consultable indépendamment de l'habilitation sur le dossier source).
- `@Transactional(readOnly = true)` au niveau classe, `@Transactional` sur les méthodes d'écriture uniquement — patron `MissionSuiviService`/`DossierServiceImpl`.
- Création d'une fiche RETEX possible uniquement si `investigation.getCgeApprovedAt() != null`.
- Accès fiche RETEX : lecture `hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')`, écriture `hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')`. Accès leçons : lecture `isAuthenticated()`.
- Pas de test de contrôleur (convention du dépôt).

---

### Task 1 : Entités + migration + repositories

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/FicheRetex.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/LeconAPartager.java`
- Create: `src/main/resources/db/changelog/migrations/041-create-fiche-retex-lecon.sql`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/FicheRetexRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/LeconAPartagerRepository.java`

**Interfaces:**
- Produces: entités `FicheRetex`/`LeconAPartager` (champs listés ci-dessous),
  `FicheRetexRepository.findByInvestigationId(UUID): Optional<FicheRetex>`,
  `LeconAPartagerRepository.findAllByOrderByCreatedAtDesc(Pageable): Page<LeconAPartager>`,
  `.existsByFicheRetexId(UUID): boolean` — consommés par Task 2 et Task 3.

- [ ] **Step 1: Créer l'entité `FicheRetex`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/entity/FicheRetex.java` :

```java
package gov.bf.ascelc.univers_audits.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fiche_retex", indexes = {
        @Index(name = "idx_fiche_retex_investigation",
                columnList = "investigation_id", unique = true)
})
public class FicheRetex extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_infraction_id")
    private TypeInfraction typeInfraction;

    @Column(name = "lieu", length = 300)
    private String lieu;

    @Column(name = "difficultes_rencontrees", columnDefinition = "TEXT")
    private String difficultesRencontrees;

    @Column(name = "origine_soupcons", columnDefinition = "TEXT")
    private String origineSoupcons;

    @Column(name = "impact_financier", precision = 15, scale = 2)
    private BigDecimal impactFinancier;

    @Column(name = "originalite_schemas", columnDefinition = "TEXT")
    private String originaliteSchemas;

    @Column(name = "collaborateurs_planifies", columnDefinition = "TEXT")
    private String collaborateursPlanifies;

    @Column(name = "jours_charges")
    private Integer joursCharges;

    @Column(name = "contexte", columnDefinition = "TEXT")
    private String contexte;

    @Column(name = "strategie_methodes", columnDefinition = "TEXT")
    private String strategieMethodes;

    @Column(name = "synthese_resultats", nullable = false, columnDefinition = "TEXT")
    private String syntheseResultats;

    @Column(name = "enseignements_axes_amelioration", nullable = false, columnDefinition = "TEXT")
    private String enseignementsAxesAmelioration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redige_par_id", nullable = false)
    private Agent redigePar;
}
```

- [ ] **Step 2: Créer l'entité `LeconAPartager`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/entity/LeconAPartager.java` :

```java
package gov.bf.ascelc.univers_audits.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lecon_a_partager", indexes = {
        @Index(name = "idx_lecon_fiche_retex",
                columnList = "fiche_retex_id", unique = true)
})
public class LeconAPartager extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiche_retex_id", nullable = false, unique = true)
    private FicheRetex ficheRetex;

    @Column(name = "titre", nullable = false, length = 300)
    private String titre;

    @Column(name = "resume", nullable = false, columnDefinition = "TEXT")
    private String resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publiee_par_id", nullable = false)
    private Agent publieePar;
}
```

- [ ] **Step 3: Créer la migration Liquibase**

Créer `src/main/resources/db/changelog/migrations/041-create-fiche-retex-lecon.sql` :

```sql
--liquibase formatted sql
--changeset dev:041-create-fiche-retex-lecon

CREATE TABLE fiche_retex (
    id                               UUID          PRIMARY KEY,
    investigation_id                 UUID          NOT NULL UNIQUE REFERENCES investigation(id),
    type_infraction_id               UUID          REFERENCES type_infraction(id),
    lieu                             VARCHAR(300),
    difficultes_rencontrees          TEXT,
    origine_soupcons                 TEXT,
    impact_financier                 NUMERIC(15,2),
    originalite_schemas              TEXT,
    collaborateurs_planifies         TEXT,
    jours_charges                    INTEGER,
    contexte                         TEXT,
    strategie_methodes               TEXT,
    synthese_resultats               TEXT          NOT NULL,
    enseignements_axes_amelioration  TEXT          NOT NULL,
    redige_par_id                    UUID          NOT NULL REFERENCES agent(id),
    version                          BIGINT        NOT NULL DEFAULT 0,
    created_at                       TIMESTAMP     NOT NULL,
    updated_at                       TIMESTAMP,
    created_by_id                    VARCHAR(100),
    updated_by_id                    VARCHAR(100)
);

CREATE TABLE lecon_a_partager (
    id                UUID         PRIMARY KEY,
    fiche_retex_id    UUID         NOT NULL UNIQUE REFERENCES fiche_retex(id),
    titre             VARCHAR(300) NOT NULL,
    resume            TEXT         NOT NULL,
    publiee_par_id    UUID         NOT NULL REFERENCES agent(id),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

COMMENT ON TABLE fiche_retex IS 'Bilan retrospectif RETEX par investigation cloturee (Lot 9 sous-chantier 2/3) - une fiche par investigation';
COMMENT ON TABLE lecon_a_partager IS 'Extrait publie d une fiche RETEX, consultable largement en interne';
```

Ramassée automatiquement par `includeAll` du changelog maître — aucune inscription manuelle
nécessaire, `040-create-information-preoccupante.sql` est le dernier numéro existant.

- [ ] **Step 4: Créer `FicheRetexRepository`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/repository/FicheRetexRepository.java` :

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FicheRetexRepository extends JpaRepository<FicheRetex, UUID> {

    Optional<FicheRetex> findByInvestigationId(UUID investigationId);
}
```

- [ ] **Step 5: Créer `LeconAPartagerRepository`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/repository/LeconAPartagerRepository.java` :

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.LeconAPartager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeconAPartagerRepository extends JpaRepository<LeconAPartager, UUID> {

    Page<LeconAPartager> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByFicheRetexId(UUID ficheRetexId);
}
```

- [ ] **Step 6: Vérifier la compilation**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/FicheRetex.java src/main/java/gov/bf/ascelc/univers_audits/model/entity/LeconAPartager.java src/main/resources/db/changelog/migrations/041-create-fiche-retex-lecon.sql src/main/java/gov/bf/ascelc/univers_audits/repository/FicheRetexRepository.java src/main/java/gov/bf/ascelc/univers_audits/repository/LeconAPartagerRepository.java
git commit -m "feat(fiche-retex): add entities, migration and repositories"
```

---

### Task 2 : `FicheRetexService` + DTOs + tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheRetexRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/FicheRetexResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/FicheRetexService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/FicheRetexServiceTest.java`

**Interfaces:**
- Consumes: entités/repositories Task 1 ; `InvestigationRepository.findById(UUID):
  Optional<Investigation>` (déjà existant) ; `TypeInfractionRepository.findById(UUID):
  Optional<TypeInfraction>` (déjà existant) ; `DossierAccessGuard.checkReadAccess(Dossier)`
  (déjà existant) ; `AgentContextResolver.getCurrentAgent(): Agent` (déjà existant).
- Produces: `FicheRetexService.{creer, obtenir}` — consommé par Task 4.

- [ ] **Step 1: Créer les DTOs**

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheRetexRequest.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheRetexRequest {

    private UUID typeInfractionId;

    @Size(max = 300, message = "Le lieu ne doit pas dépasser 300 caractères")
    private String lieu;

    private String difficultesRencontrees;

    private String origineSoupcons;

    private BigDecimal impactFinancier;

    private String originaliteSchemas;

    private String collaborateursPlanifies;

    private Integer joursCharges;

    private String contexte;

    private String strategieMethodes;

    @NotBlank(message = "La synthèse des résultats est obligatoire")
    private String syntheseResultats;

    @NotBlank(message = "Les enseignements et axes d'amélioration sont obligatoires")
    private String enseignementsAxesAmelioration;
}
```

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/FicheRetexResponse.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FicheRetexResponse {

    private UUID id;
    private UUID investigationId;
    private UUID typeInfractionId;
    private String typeInfractionLibelle;
    private String lieu;
    private String difficultesRencontrees;
    private String origineSoupcons;
    private BigDecimal impactFinancier;
    private String originaliteSchemas;
    private String collaborateursPlanifies;
    private Integer joursCharges;
    private String contexte;
    private String strategieMethodes;
    private String syntheseResultats;
    private String enseignementsAxesAmelioration;
    private String redigeParNom;
    private Instant createdAt;
}
```

- [ ] **Step 2: Écrire le test (échec de compilation attendu, `FicheRetexService` n'existe pas encore)**

Créer `src/test/java/gov/bf/ascelc/univers_audits/service/FicheRetexServiceTest.java` :

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheRetexRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.FicheRetexResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.FicheRetexRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FicheRetexServiceTest {

    @Mock private FicheRetexRepository ficheRetexRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private TypeInfractionRepository typeInfractionRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private FicheRetexService service;

    private Investigation buildInvestigation(UUID id, Instant cgeApprovedAt) {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        return Investigation.builder()
                .id(id)
                .dossier(dossier)
                .cgeApprovedAt(cgeApprovedAt)
                .build();
    }

    private FicheRetexRequest buildRequest() {
        return FicheRetexRequest.builder()
                .syntheseResultats("Synthèse des résultats")
                .enseignementsAxesAmelioration("Enseignements tirés")
                .build();
    }

    @Test
    void creer_creeUneFicheRetexApresDecisionCge() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(ficheRetexRepository.save(any(FicheRetex.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FicheRetexResponse result = service.creer(investigationId, buildRequest());

        assertThat(result.getSyntheseResultats()).isEqualTo("Synthèse des résultats");
        assertThat(result.getInvestigationId()).isEqualTo(investigationId);
    }

    @Test
    void creer_refuseSiCgeApprovedAtNull() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, null);

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.creer(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void creer_refuseSiFicheDejaExistante() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        FicheRetex existing = FicheRetex.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.creer(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void creer_resoutLeTypeInfractionSiFourni() {
        UUID investigationId = UUID.randomUUID();
        UUID typeInfractionId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        TypeInfraction typeInfraction = TypeInfraction.builder()
                .id(typeInfractionId)
                .libelle("Corruption")
                .build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        FicheRetexRequest request = FicheRetexRequest.builder()
                .typeInfractionId(typeInfractionId)
                .syntheseResultats("Synthèse")
                .enseignementsAxesAmelioration("Enseignements")
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(typeInfractionRepository.findById(typeInfractionId)).thenReturn(Optional.of(typeInfraction));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(ficheRetexRepository.save(any(FicheRetex.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FicheRetexResponse result = service.creer(investigationId, request);

        assertThat(result.getTypeInfractionLibelle()).isEqualTo("Corruption");
    }

    @Test
    void creer_refuseSiTypeInfractionIntrouvable() {
        UUID investigationId = UUID.randomUUID();
        UUID typeInfractionId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());

        FicheRetexRequest request = FicheRetexRequest.builder()
                .typeInfractionId(typeInfractionId)
                .syntheseResultats("Synthèse")
                .enseignementsAxesAmelioration("Enseignements")
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(typeInfractionRepository.findById(typeInfractionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void creer_verifieLeControleDaccesViaDossierAccessGuard() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(ficheRetexRepository.save(any(FicheRetex.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.creer(investigationId, buildRequest());

        verify(accessGuard).checkReadAccess(investigation.getDossier());
    }

    @Test
    void obtenir_retourneLaFicheExistante() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        FicheRetex fiche = FicheRetex.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .syntheseResultats("Synthèse")
                .enseignementsAxesAmelioration("Enseignements")
                .redigePar(Agent.builder().id(UUID.randomUUID()).build())
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(fiche));

        FicheRetexResponse result = service.obtenir(investigationId);

        assertThat(result.getSyntheseResultats()).isEqualTo("Synthèse");
    }

    @Test
    void obtenir_leveResourceNotFoundSiAucuneFiche() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw -q -Dtest=FicheRetexServiceTest test`
Expected: FAIL — erreur de compilation, `FicheRetexService` n'existe pas.

- [ ] **Step 4: Créer l'implémentation**

Créer `src/main/java/gov/bf/ascelc/univers_audits/service/FicheRetexService.java` :

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheRetexRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.FicheRetexResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.FicheRetexRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FicheRetexService {

    private final FicheRetexRepository ficheRetexRepository;
    private final InvestigationRepository investigationRepository;
    private final TypeInfractionRepository typeInfractionRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public FicheRetexResponse creer(UUID investigationId, FicheRetexRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "La rédaction d'une fiche RETEX n'est possible qu'après la décision finale du CGE.");
        }

        if (ficheRetexRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Une fiche RETEX existe déjà pour cette investigation.");
        }

        TypeInfraction typeInfraction = null;
        if (request.getTypeInfractionId() != null) {
            typeInfraction = typeInfractionRepository.findById(request.getTypeInfractionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Type d'infraction introuvable : " + request.getTypeInfractionId()));
        }

        Agent agent = agentContextResolver.getCurrentAgent();

        FicheRetex fiche = FicheRetex.builder()
                .investigation(investigation)
                .typeInfraction(typeInfraction)
                .lieu(request.getLieu())
                .difficultesRencontrees(request.getDifficultesRencontrees())
                .origineSoupcons(request.getOrigineSoupcons())
                .impactFinancier(request.getImpactFinancier())
                .originaliteSchemas(request.getOriginaliteSchemas())
                .collaborateursPlanifies(request.getCollaborateursPlanifies())
                .joursCharges(request.getJoursCharges())
                .contexte(request.getContexte())
                .strategieMethodes(request.getStrategieMethodes())
                .syntheseResultats(request.getSyntheseResultats())
                .enseignementsAxesAmelioration(request.getEnseignementsAxesAmelioration())
                .redigePar(agent)
                .build();

        FicheRetex saved = ficheRetexRepository.save(fiche);
        log.info("Fiche RETEX rédigée — investigation: {}", investigationId);
        return toResponse(saved);
    }

    public FicheRetexResponse obtenir(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        FicheRetex fiche = ficheRetexRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune fiche RETEX n'a été rédigée pour cette investigation."));
        return toResponse(fiche);
    }

    private FicheRetexResponse toResponse(FicheRetex fiche) {
        return FicheRetexResponse.builder()
                .id(fiche.getId())
                .investigationId(fiche.getInvestigation().getId())
                .typeInfractionId(fiche.getTypeInfraction() != null ? fiche.getTypeInfraction().getId() : null)
                .typeInfractionLibelle(fiche.getTypeInfraction() != null ? fiche.getTypeInfraction().getLibelle() : null)
                .lieu(fiche.getLieu())
                .difficultesRencontrees(fiche.getDifficultesRencontrees())
                .origineSoupcons(fiche.getOrigineSoupcons())
                .impactFinancier(fiche.getImpactFinancier())
                .originaliteSchemas(fiche.getOriginaliteSchemas())
                .collaborateursPlanifies(fiche.getCollaborateursPlanifies())
                .joursCharges(fiche.getJoursCharges())
                .contexte(fiche.getContexte())
                .strategieMethodes(fiche.getStrategieMethodes())
                .syntheseResultats(fiche.getSyntheseResultats())
                .enseignementsAxesAmelioration(fiche.getEnseignementsAxesAmelioration())
                .redigeParNom(fiche.getRedigePar().getNomComplet())
                .createdAt(fiche.getCreatedAt())
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

Run: `./mvnw -q -Dtest=FicheRetexServiceTest test`
Expected: PASS — 8 tests, 0 échec.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheRetexRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/FicheRetexResponse.java src/main/java/gov/bf/ascelc/univers_audits/service/FicheRetexService.java src/test/java/gov/bf/ascelc/univers_audits/service/FicheRetexServiceTest.java
git commit -m "feat(fiche-retex): add FicheRetexService"
```

---

### Task 3 : `LeconAPartagerService` + DTOs + tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PublierLeconRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/LeconAPartagerResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/LeconAPartagerService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/LeconAPartagerServiceTest.java`

**Interfaces:**
- Consumes: `FicheRetexRepository.findByInvestigationId(UUID): Optional<FicheRetex>` (Task 1) ;
  `LeconAPartagerRepository.{findAllByOrderByCreatedAtDesc, existsByFicheRetexId}` (Task 1) ;
  `InvestigationRepository.findById`, `DossierAccessGuard.checkReadAccess`,
  `AgentContextResolver.getCurrentAgent` (déjà existants).
- Produces: `LeconAPartagerService.{publier, lister}` — consommé par Task 4.

- [ ] **Step 1: Créer les DTOs**

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PublierLeconRequest.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublierLeconRequest {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 300, message = "Le titre ne doit pas dépasser 300 caractères")
    private String titre;

    @NotBlank(message = "Le résumé est obligatoire")
    private String resume;
}
```

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/LeconAPartagerResponse.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LeconAPartagerResponse {

    private UUID id;
    private String titre;
    private String resume;
    private String publieeParNom;
    private UUID investigationId;
    private Instant createdAt;
}
```

- [ ] **Step 2: Écrire le test (échec de compilation attendu, `LeconAPartagerService` n'existe pas encore)**

Créer `src/test/java/gov/bf/ascelc/univers_audits/service/LeconAPartagerServiceTest.java` :

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PublierLeconRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.LeconAPartagerResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.LeconAPartager;
import gov.bf.ascelc.univers_audits.repository.FicheRetexRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.LeconAPartagerRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeconAPartagerServiceTest {

    @Mock private LeconAPartagerRepository leconAPartagerRepository;
    @Mock private FicheRetexRepository ficheRetexRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private LeconAPartagerService service;

    private Investigation buildInvestigation(UUID id) {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        return Investigation.builder().id(id).dossier(dossier).build();
    }

    private PublierLeconRequest buildRequest() {
        return PublierLeconRequest.builder()
                .titre("Schéma de surfacturation détecté")
                .resume("Résumé de la leçon à partager")
                .build();
    }

    @Test
    void publier_creeUneLeconDepuisUneFicheExistante() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId);
        FicheRetex fiche = FicheRetex.builder().id(UUID.randomUUID()).investigation(investigation).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(fiche));
        when(leconAPartagerRepository.existsByFicheRetexId(fiche.getId())).thenReturn(false);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(leconAPartagerRepository.save(any(LeconAPartager.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeconAPartagerResponse result = service.publier(investigationId, buildRequest());

        assertThat(result.getTitre()).isEqualTo("Schéma de surfacturation détecté");
    }

    @Test
    void publier_refuseSiAucuneFicheRetex() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId);

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publier(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void publier_refuseSiLeconDejaPubliee() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId);
        FicheRetex fiche = FicheRetex.builder().id(UUID.randomUUID()).investigation(investigation).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(fiche));
        when(leconAPartagerRepository.existsByFicheRetexId(fiche.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.publier(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void publier_verifieLeControleDaccesViaDossierAccessGuard() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId);
        FicheRetex fiche = FicheRetex.builder().id(UUID.randomUUID()).investigation(investigation).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(fiche));
        when(leconAPartagerRepository.existsByFicheRetexId(fiche.getId())).thenReturn(false);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(leconAPartagerRepository.save(any(LeconAPartager.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.publier(investigationId, buildRequest());

        verify(accessGuard).checkReadAccess(investigation.getDossier());
    }

    @Test
    void lister_neFiltrePasParHabilitationDossier() {
        Pageable pageable = PageRequest.of(0, 20);
        Investigation investigation = buildInvestigation(UUID.randomUUID());
        FicheRetex fiche = FicheRetex.builder().id(UUID.randomUUID()).investigation(investigation).build();
        LeconAPartager lecon = LeconAPartager.builder()
                .id(UUID.randomUUID())
                .ficheRetex(fiche)
                .titre("Titre")
                .resume("Résumé")
                .publieePar(Agent.builder().id(UUID.randomUUID()).build())
                .build();

        when(leconAPartagerRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(lecon)));

        Page<LeconAPartagerResponse> result = service.lister(pageable);

        assertThat(result.getContent()).hasSize(1);
        verifyNoInteractions(accessGuard);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw -q -Dtest=LeconAPartagerServiceTest test`
Expected: FAIL — erreur de compilation, `LeconAPartagerService` n'existe pas.

- [ ] **Step 4: Créer l'implémentation**

Créer `src/main/java/gov/bf/ascelc/univers_audits/service/LeconAPartagerService.java` :

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PublierLeconRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.LeconAPartagerResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.LeconAPartager;
import gov.bf.ascelc.univers_audits.repository.FicheRetexRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.LeconAPartagerRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
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
public class LeconAPartagerService {

    private final LeconAPartagerRepository leconAPartagerRepository;
    private final FicheRetexRepository ficheRetexRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public LeconAPartagerResponse publier(UUID investigationId, PublierLeconRequest request) {
        Investigation investigation = investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
        accessGuard.checkReadAccess(investigation.getDossier());

        FicheRetex fiche = ficheRetexRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucune fiche RETEX n'a été rédigée pour cette investigation, "
                                + "impossible de publier une leçon."));

        if (leconAPartagerRepository.existsByFicheRetexId(fiche.getId())) {
            throw new BusinessException(
                    "Une leçon a déjà été publiée pour cette fiche RETEX.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();

        LeconAPartager lecon = LeconAPartager.builder()
                .ficheRetex(fiche)
                .titre(request.getTitre())
                .resume(request.getResume())
                .publieePar(agent)
                .build();

        LeconAPartager saved = leconAPartagerRepository.save(lecon);
        log.info("Leçon à partager publiée — fiche RETEX: {}", fiche.getId());
        return toResponse(saved, investigationId);
    }

    public Page<LeconAPartagerResponse> lister(Pageable pageable) {
        return leconAPartagerRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(lecon -> toResponse(lecon, lecon.getFicheRetex().getInvestigation().getId()));
    }

    private LeconAPartagerResponse toResponse(LeconAPartager lecon, UUID investigationId) {
        return LeconAPartagerResponse.builder()
                .id(lecon.getId())
                .titre(lecon.getTitre())
                .resume(lecon.getResume())
                .publieeParNom(lecon.getPublieePar().getNomComplet())
                .investigationId(investigationId)
                .createdAt(lecon.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw -q -Dtest=LeconAPartagerServiceTest test`
Expected: PASS — 5 tests, 0 échec.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PublierLeconRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/LeconAPartagerResponse.java src/main/java/gov/bf/ascelc/univers_audits/service/LeconAPartagerService.java src/test/java/gov/bf/ascelc/univers_audits/service/LeconAPartagerServiceTest.java
git commit -m "feat(fiche-retex): add LeconAPartagerService"
```

---

### Task 4 : Endpoints REST

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/ApiUrls.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/FicheRetexController.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/LeconAPartagerController.java`

**Interfaces:**
- Consumes: `FicheRetexService.{creer, obtenir}` (Task 2),
  `LeconAPartagerService.{publier, lister}` (Task 3).

- [ ] **Step 1: Ajouter la constante de route**

Dans `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/ApiUrls.java`, ajouter après le
bloc `INFORMATIONS_PREOCCUPANTES` existant :

```java

    // ── Leçons à partager ──────────────────────────────────────

    public static final String LECONS_A_PARTAGER = BASE + "/lecons-a-partager";
```

- [ ] **Step 2: Créer `FicheRetexController`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/controller/FicheRetexController.java` :

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheRetexRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PublierLeconRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.FicheRetexResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.LeconAPartagerResponse;
import gov.bf.ascelc.univers_audits.service.FicheRetexService;
import gov.bf.ascelc.univers_audits.service.LeconAPartagerService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/fiche-retex")
public class FicheRetexController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final FicheRetexService ficheRetexService;
    private final LeconAPartagerService leconAPartagerService;

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<FicheRetexResponse> creer(
            @PathVariable UUID id,
            @Valid @RequestBody FicheRetexRequest request) {
        log.info("Rédaction fiche RETEX — investigation: {}", id);
        FicheRetexResponse result = ficheRetexService.creer(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<FicheRetexResponse> obtenir(@PathVariable UUID id) {
        return ResponseEntity.ok(ficheRetexService.obtenir(id));
    }

    @PostMapping("/publier-lecon")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<LeconAPartagerResponse> publierLecon(
            @PathVariable UUID id,
            @Valid @RequestBody PublierLeconRequest request) {
        log.info("Publication leçon à partager — investigation: {}", id);
        LeconAPartagerResponse result = leconAPartagerService.publier(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
```

- [ ] **Step 3: Créer `LeconAPartagerController`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/controller/LeconAPartagerController.java` :

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.LeconAPartagerResponse;
import gov.bf.ascelc.univers_audits.service.LeconAPartagerService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.LECONS_A_PARTAGER)
public class LeconAPartagerController {

    private final LeconAPartagerService leconAPartagerService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<LeconAPartagerResponse>> lister(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(leconAPartagerService.lister(pageable));
    }
}
```

- [ ] **Step 4: Compiler et lancer la suite de tests complète**

Run: `./mvnw -q test`
Expected: PASS — tous les tests existants plus les 13 nouveaux (8
`FicheRetexServiceTest` + 5 `LeconAPartagerServiceTest`), 0 échec. Pas de test de
contrôleur (convention du dépôt).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/utils/ApiUrls.java src/main/java/gov/bf/ascelc/univers_audits/controller/FicheRetexController.java src/main/java/gov/bf/ascelc/univers_audits/controller/LeconAPartagerController.java
git commit -m "feat(fiche-retex): expose REST endpoints"
```
