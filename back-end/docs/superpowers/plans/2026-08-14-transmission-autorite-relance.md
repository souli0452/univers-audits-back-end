# Transmission aux autorités et relance des suites (Lot 6, sous-chantier 1/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter l'enregistrement factuel de la transmission d'un dossier décidé à l'autorité compétente (`TransmissionAutorite`) et la liste des relances formelles si l'autorité ne répond pas (`RelanceSuites`) — premier sous-chantier du Lot 6, entièrement absent du code avant ce chantier.

**Architecture:** Deux nouvelles entités JPA (`TransmissionAutorite` 1:1 avec `Investigation`, `RelanceSuites` en liste `@OneToMany` rattachée à `TransmissionAutorite`, sans repository dédié), un service concret qui construit directement les DTO de réponse (y compris le calcul d'échéance de relance), un contrôleur dédié nesté sous `/investigations/{id}/transmission-autorite`.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok (`@SuperBuilder`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-14-transmission-autorite-relance-design.md`

## Global Constraints

- Migration Liquibase : fichier `035-create-transmission-autorite.sql`, format `--liquibase formatted sql` / `--changeset dev:035-create-transmission-autorite` (numéro confirmé libre, dernier existant = `034`).
- Nouveau code `ParametreDelai` : `RELANCE_SUITES_TRANSMISSION` (30 jours, `jours_ouvrables=TRUE`, `actif=TRUE`).
- Création de `TransmissionAutorite` refusée si `investigation.getCgeApprovedAt() == null` (décision finale CGE pas encore rendue) OU si une transmission existe déjà pour cette investigation (unique par investigation, contrainte en base).
- `TransmissionAutoriteRequest.autoriteDestinataire` porte `@NotBlank` — contrairement aux entités éditables progressivement de ce Lot (`RapportEnquete`, `RequeteParquet`), une transmission est un événement daté, pas un brouillon.
- Masquage confidentialité appliqué dès la conception aux DEUX méthodes d'écriture (`creer` ET `ajouterRelance`), pas seulement en lecture — rejet `BusinessException`, pas un masquage `ResourceNotFoundException` (c'est une action bloquée, pas une ressource cachée).
- `relanceOverdue` (booléen) : `true` seulement si l'échéance (`transmittedAt` + délai) est dépassée ET qu'aucune relance n'a encore été envoyée (`relances.isEmpty()`) — jamais `null`, contrairement à `relanceDueAt` qui peut être `null` si le paramètre de délai est indisponible (dégradation silencieuse, `try/catch` sur `ResourceNotFoundException`, jamais de propagation).
- Rôles : écriture (`POST /transmission-autorite`, `POST .../relances`) = `hasAnyRole('CGE','ADMIN_DDIC')`. Lecture (`GET /transmission-autorite`) = `hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')`.
- Aucun nouveau `DossierStatus` — le statut du dossier reste `DECISION_RENDUE`, inchangé par ce sous-chantier.
- Aucun test de contrôleur : confirmé par `find . -iname "*ControllerTest.java"` → 0 résultat dans tout le projet.

---

### Task 1: Entités, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/TransmissionAutorite.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/RelanceSuites.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/TransmissionAutoriteRepository.java`
- Create: `src/main/resources/db/changelog/migrations/035-create-transmission-autorite.sql`

**Interfaces:**
- Consumes: `AuditEntity` (`gov.bf.ascelc.univers_audits.abstracts.AuditEntity`), `Investigation`/`Agent` (`gov.bf.ascelc.univers_audits.model.entity`, champ `id` existant).
- Produces: `TransmissionAutorite.{getRelances(): List<RelanceSuites>, ...}`, `RelanceSuites.{...}`, `TransmissionAutoriteRepository.findByInvestigationId(UUID): Optional<TransmissionAutorite>`, `ParametreDelai` code `RELANCE_SUITES_TRANSMISSION` en base — consommés par la Tâche 2.

Pas de test d'entité dédié : ni `TransmissionAutorite` ni `RelanceSuites` n'ont de logique
métier propre (contrairement à `RapportEnquete.isComplet()`) — de simples porteurs de données,
même situation que `DecisionCGE`/`SeanceCTADP` qui n'ont pas de test d'entité dans ce dépôt.

- [ ] **Step 1: Créer l'entité `TransmissionAutorite`**

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

- [ ] **Step 2: Créer l'entité `RelanceSuites`**

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

- [ ] **Step 3: Créer `TransmissionAutoriteRepository`**

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

- [ ] **Step 4: Créer la migration `035-create-transmission-autorite.sql`**

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

- [ ] **Step 5: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/TransmissionAutorite.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/RelanceSuites.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/TransmissionAutoriteRepository.java \
        src/main/resources/db/changelog/migrations/035-create-transmission-autorite.sql
git commit -m "feat: add TransmissionAutorite/RelanceSuites entities, repository and migration"
```

---

### Task 2: DTOs et `TransmissionAutoriteService`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/TransmissionAutoriteRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RelanceSuitesRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RelanceSuitesResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/TransmissionAutoriteResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/TransmissionAutoriteService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/TransmissionAutoriteServiceTest.java`

**Interfaces:**
- Consumes: `TransmissionAutoriteRepository.{findByInvestigationId, save}` (Task 1), `InvestigationRepository.findById(UUID): Optional<Investigation>` (existant), `DossierAccessGuard.{checkReadAccess(Dossier), canSeeConfidential()}` (existant), `AgentContextResolver.getCurrentAgent(): Agent` (existant), `ParametreDelaiService.resolveDelaiJours(String): int` (existant, lève `ResourceNotFoundException`), `Investigation.getCgeApprovedAt()` (existant).
- Produces: `TransmissionAutoriteService.{creer(UUID, TransmissionAutoriteRequest): TransmissionAutoriteResponse, getOrThrow(UUID): TransmissionAutoriteResponse, ajouterRelance(UUID, RelanceSuitesRequest): TransmissionAutoriteResponse}` — consommés par la Tâche 3.

- [ ] **Step 1: Créer les DTOs**

Fichier `TransmissionAutoriteRequest.java` :

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

Fichier `RelanceSuitesRequest.java` :

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

Fichier `RelanceSuitesResponse.java` :

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

Fichier `TransmissionAutoriteResponse.java` :

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

- [ ] **Step 2: Écrire les tests de `TransmissionAutoriteService` (échouent, la classe n'existe pas encore)**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.RelanceSuitesRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TransmissionAutoriteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.TransmissionAutoriteResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TransmissionAutoriteRepository;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransmissionAutoriteServiceTest {

    @Mock private TransmissionAutoriteRepository transmissionAutoriteRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private ParametreDelaiService parametreDelaiService;

    @InjectMocks
    private TransmissionAutoriteService service;

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

    @Test
    void creer_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        TransmissionAutoriteRequest request = TransmissionAutoriteRequest.builder()
                .autoriteDestinataire("Procureur du Faso").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(transmissionAutoriteRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiTransmissionDejaExistante() {
        TransmissionAutorite existante = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur")
                .transmittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(existante));

        TransmissionAutoriteRequest request = TransmissionAutoriteRequest.builder()
                .autoriteDestinataire("Autre autorité").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(transmissionAutoriteRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        TransmissionAutoriteRequest request = TransmissionAutoriteRequest.builder()
                .autoriteDestinataire("Procureur du Faso").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(transmissionAutoriteRepository, never()).save(any());
    }

    @Test
    void creer_succeedsEtRenseigneTransmittedAtEtTransmittedBy() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(transmissionAutoriteRepository.save(any(TransmissionAutorite.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransmissionAutoriteRequest request = TransmissionAutoriteRequest.builder()
                .autoriteDestinataire("Procureur du Faso").build();

        TransmissionAutoriteResponse result = service.creer(investigationId, request);

        assertThat(result.getAutoriteDestinataire()).isEqualTo("Procureur du Faso");
        assertThat(result.getTransmittedAt()).isNotNull();
        assertThat(result.getTransmittedByNom()).isEqualTo("Jean Ouedraogo");
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiAucuneTransmission() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transmissionAutoriteRepository, never()).findByInvestigationId(any());
    }

    @Test
    void ajouterRelance_rejetteSiAucuneTransmission() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        RelanceSuitesRequest request = RelanceSuitesRequest.builder().contenu("Relance").build();

        assertThatThrownBy(() -> service.ajouterRelance(investigationId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ajouterRelance_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        RelanceSuitesRequest request = RelanceSuitesRequest.builder().contenu("Relance").build();

        assertThatThrownBy(() -> service.ajouterRelance(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(transmissionAutoriteRepository, never()).save(any());
    }

    @Test
    void ajouterRelance_ajouteALaListeExistante() {
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur du Faso")
                .transmittedAt(Instant.now())
                .build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(transmission));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(transmissionAutoriteRepository.save(any(TransmissionAutorite.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RelanceSuitesRequest request = RelanceSuitesRequest.builder().contenu("Relance formelle").build();

        TransmissionAutoriteResponse result = service.ajouterRelance(investigationId, request);

        assertThat(result.getRelances()).hasSize(1);
        assertThat(result.getRelances().get(0).getContenu()).isEqualTo("Relance formelle");
        assertThat(result.getRelances().get(0).getAgentNom()).isEqualTo("Awa Sawadogo");
    }

    @Test
    void relanceOverdue_vraiSiEcheanceDepasseeEtAucuneRelanceEnvoyee() {
        Instant transmittedAt = Instant.now().minusSeconds(40L * 24 * 3600);
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur du Faso")
                .transmittedAt(transmittedAt)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(transmission));
        when(parametreDelaiService.resolveDelaiJours("RELANCE_SUITES_TRANSMISSION")).thenReturn(30);

        TransmissionAutoriteResponse result = service.getOrThrow(investigationId);

        assertThat(result.getRelanceOverdue()).isTrue();
    }

    @Test
    void relanceOverdue_fauxSiUneRelanceDejaEnvoyee() {
        Instant transmittedAt = Instant.now().minusSeconds(40L * 24 * 3600);
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur du Faso")
                .transmittedAt(transmittedAt)
                .build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        transmission.getRelances().add(RelanceSuites.builder()
                .transmissionAutorite(transmission)
                .relanceAt(Instant.now())
                .agent(agent)
                .contenu("Relance envoyée")
                .build());
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(transmission));
        when(parametreDelaiService.resolveDelaiJours("RELANCE_SUITES_TRANSMISSION")).thenReturn(30);

        TransmissionAutoriteResponse result = service.getOrThrow(investigationId);

        assertThat(result.getRelanceOverdue()).isFalse();
    }

    @Test
    void relanceOverdue_degradeVersNullSiParametreIndisponible() {
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur du Faso")
                .transmittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(transmission));
        when(parametreDelaiService.resolveDelaiJours("RELANCE_SUITES_TRANSMISSION"))
                .thenThrow(new ResourceNotFoundException("Paramètre introuvable"));

        TransmissionAutoriteResponse result = service.getOrThrow(investigationId);

        assertThat(result.getRelanceDueAt()).isNull();
        assertThat(result.getRelanceOverdue()).isFalse();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvnw -Dtest=TransmissionAutoriteServiceTest test`
Expected: FAIL (compilation error — `TransmissionAutoriteService` n'existe pas)

- [ ] **Step 4: Créer `TransmissionAutoriteService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.RelanceSuitesRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TransmissionAutoriteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.RelanceSuitesResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.TransmissionAutoriteResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TransmissionAutoriteRepository;
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

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvnw -Dtest=TransmissionAutoriteServiceTest test`
Expected: PASS (12 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/TransmissionAutoriteRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RelanceSuitesRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RelanceSuitesResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/TransmissionAutoriteResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/TransmissionAutoriteService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/TransmissionAutoriteServiceTest.java
git commit -m "feat: add TransmissionAutoriteService"
```

---

### Task 3: Contrôleur `TransmissionAutoriteController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/TransmissionAutoriteController.java`

**Interfaces:**
- Consumes: `TransmissionAutoriteService.{creer, getOrThrow, ajouterRelance}` (Task 2), `ApiUrls.INVESTIGATIONS` (existant).
- Produces: endpoints `GET/POST /api/v1/investigations/{id}/transmission-autorite`, `POST /api/v1/investigations/{id}/transmission-autorite/relances` — dernière tâche du chantier.

Pas de test de contrôleur (convention déjà établie).

- [ ] **Step 1: Créer `TransmissionAutoriteController`**

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

- [ ] **Step 2: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run the full test suite**

Run: `mvnw -q -Dtest='!UniversAuditsApplicationTests' test`
Expected: BUILD SUCCESS (exclut le seul test d'intégration pré-existant qui échoue en environnement
local faute de DataSource — sans rapport avec ce chantier, déjà confirmé sur la branche de base)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/TransmissionAutoriteController.java
git commit -m "feat: add transmission-autorite REST endpoints"
```
