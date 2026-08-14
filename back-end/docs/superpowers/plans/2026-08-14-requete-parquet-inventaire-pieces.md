# Requête au Parquet et inventaire des pièces (Lot 5, sous-chantier 4/4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter une entité `RequeteParquet` éditable (rédigée par le conseiller juridique, uniquement pour une issue de saisine judiciaire) et un endpoint de lecture `InventairePieces` dérivé des pièces jointes existantes — dernier sous-chantier du Lot 5.

**Architecture:** `RequeteParquet` suit exactement le patron `RapportEnquete`/`NoteRecommandations` (entité 1:1 avec `Investigation`, service concret sans interface, contrôleur dédié nesté sous `/investigations/{id}`). `InventairePieces` n'a aucune nouvelle table — un service+contrôleur en lecture seule dérivent la réponse de `AttachmentRepository.findByInvestigationId`, déjà existant.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok (`@SuperBuilder`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-14-requete-parquet-inventaire-pieces-design.md`

## Global Constraints

- Migration Liquibase : fichier `034-create-requete-parquet.sql`, format `--liquibase formatted sql` / `--changeset dev:034-create-requete-parquet` (numéro confirmé libre, dernier existant = `033`). Aucune migration pour `InventairePieces` (vue calculée, pas de table).
- `RequeteParquet.contenu` : un seul champ texte libre, pas de contrainte `NOT NULL`/`@NotBlank` — un brouillon partiel doit pouvoir être enregistré.
- Gate d'édition (`RequeteParquetService.enregistrer`) : refusé si `investigation.getOutcome() != InvestigationOutcome.JUDICIAL_REFERRAL`, refusé si `investigation.getCgeApprovedAt() != null` (décision finale CGE déjà rendue).
- Rôles lecture (`RequeteParquet` et `InventairePieces`) : `hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')`. Rôles écriture (`RequeteParquet` uniquement) : `hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')`.
- Contrôle d'accès dans la couche **service**, jamais au contrôleur seul (`DossierAccessGuard.checkReadAccess` + masquage confidentialité) — correctif déjà acté aux sous-chantiers précédents de ce Lot, appliqué ici dès la conception.
- `InventairePieces` restitue exactement 5 champs par pièce (déjà tous présents sur `Attachment`) : `code`, `description`, `source` (`AttachmentSource`), `uploadedAt` (`LocalDateTime` — pas `Instant`), `modeObtention` (`ModeObtention`).
- Aucun test de contrôleur : confirmé par `find . -iname "*ControllerTest.java"` → 0 résultat dans tout le projet.

---

### Task 1: Entité `RequeteParquet`, repository, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/RequeteParquet.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/RequeteParquetRepository.java`
- Create: `src/main/resources/db/changelog/migrations/034-create-requete-parquet.sql`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/model/entity/RequeteParquetTest.java`

**Interfaces:**
- Consumes: `AuditEntity` (`gov.bf.ascelc.univers_audits.abstracts.AuditEntity`), `Investigation` (`gov.bf.ascelc.univers_audits.model.entity.Investigation`, champ `id` existant).
- Produces: `RequeteParquet.isComplet(): boolean`, `RequeteParquetRepository.findByInvestigationId(UUID): Optional<RequeteParquet>` — consommés par la Tâche 2.

- [ ] **Step 1: Créer l'entité `RequeteParquet`**

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
@Table(name = "requete_parquet")
public class RequeteParquet extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;

    public boolean isComplet() {
        return contenu != null && !contenu.isBlank();
    }
}
```

- [ ] **Step 2: Écrire le test de l'entité**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequeteParquetTest {

    @Test
    void isComplet_retourneVraiSiContenuRenseigne() {
        RequeteParquet requete = RequeteParquet.builder().contenu("Faits et qualification").build();

        assertThat(requete.isComplet()).isTrue();
    }

    @Test
    void isComplet_retourneFauxSiContenuVide() {
        RequeteParquet requete = RequeteParquet.builder().contenu("   ").build();

        assertThat(requete.isComplet()).isFalse();
    }

    @Test
    void isComplet_retourneFauxSiContenuNull() {
        RequeteParquet requete = RequeteParquet.builder().build();

        assertThat(requete.isComplet()).isFalse();
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `mvnw -Dtest=RequeteParquetTest test`
Expected: PASS (3 tests)

- [ ] **Step 4: Créer `RequeteParquetRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RequeteParquetRepository extends JpaRepository<RequeteParquet, UUID> {
    Optional<RequeteParquet> findByInvestigationId(UUID investigationId);
}
```

- [ ] **Step 5: Créer la migration `034-create-requete-parquet.sql`**

```sql
--liquibase formatted sql
--changeset dev:034-create-requete-parquet

CREATE TABLE requete_parquet (
    id                UUID      PRIMARY KEY,
    investigation_id  UUID      NOT NULL UNIQUE REFERENCES investigation(id),
    contenu           TEXT,
    version           BIGINT    NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

COMMENT ON TABLE requete_parquet IS 'Requete au Parquet (Lot 5 sous-chantier 4/4) - redigee par le conseiller juridique, uniquement pour une issue JUDICIAL_REFERRAL';
```

- [ ] **Step 6: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/RequeteParquet.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/RequeteParquetRepository.java \
        src/main/resources/db/changelog/migrations/034-create-requete-parquet.sql \
        src/test/java/gov/bf/ascelc/univers_audits/model/entity/RequeteParquetTest.java
git commit -m "feat: add RequeteParquet entity, repository and migration"
```

---

### Task 2: DTOs et `RequeteParquetService`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RequeteParquetRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RequeteParquetResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/RequeteParquetService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/RequeteParquetServiceTest.java`

**Interfaces:**
- Consumes: `RequeteParquetRepository.{findByInvestigationId, save}` (Task 1), `InvestigationRepository.findById(UUID): Optional<Investigation>` (existant), `DossierAccessGuard.{checkReadAccess(Dossier), canSeeConfidential()}` (existant), `Investigation.{getOutcome(), getCgeApprovedAt(), getDossier()}` (existant).
- Produces: `RequeteParquetService.{enregistrer(UUID, RequeteParquetRequest): RequeteParquet, getOrThrow(UUID): RequeteParquet}` — consommés par la Tâche 3.

- [ ] **Step 1: Créer les DTOs**

Fichier `RequeteParquetRequest.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequeteParquetRequest {
    private String contenu;
}
```

Fichier `RequeteParquetResponse.java` :

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
public class RequeteParquetResponse {
    private UUID id;
    private UUID investigationId;
    private String contenu;
    private boolean complet;
    private Instant createdAt;
    private Instant updatedAt;
}
```

- [ ] **Step 2: Écrire les tests de `RequeteParquetService` (échouent, la classe n'existe pas encore)**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import gov.bf.ascelc.univers_audits.model.dto.request.RequeteParquetRequest;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.RequeteParquetRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequeteParquetServiceTest {

    @Mock
    private RequeteParquetRepository requeteParquetRepository;
    @Mock
    private InvestigationRepository investigationRepository;
    @Mock
    private DossierAccessGuard accessGuard;

    @InjectMocks
    private RequeteParquetService service;

    private Investigation investigation;
    private UUID investigationId;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .outcome(InvestigationOutcome.JUDICIAL_REFERRAL)
                .dossier(dossier)
                .build();
    }

    private RequeteParquetRequest buildRequest() {
        return RequeteParquetRequest.builder()
                .contenu("Exposé des faits et qualification pénale")
                .build();
    }

    @Test
    void enregistrer_creeUneNouvelleRequeteSiAucuneNExisteEncore() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(requeteParquetRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(requeteParquetRepository.save(any(RequeteParquet.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RequeteParquet result = service.enregistrer(investigationId, buildRequest());

        assertThat(result.getInvestigation()).isEqualTo(investigation);
        assertThat(result.getContenu()).isEqualTo("Exposé des faits et qualification pénale");
        verify(requeteParquetRepository).save(any(RequeteParquet.class));
    }

    @Test
    void enregistrer_metAJourLaRequeteExistanteAuDeuxiemeAppel() {
        RequeteParquet existante = RequeteParquet.builder()
                .investigation(investigation)
                .contenu("Ancien contenu")
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(requeteParquetRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(existante));
        when(requeteParquetRepository.save(any(RequeteParquet.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RequeteParquet result = service.enregistrer(investigationId, buildRequest());

        assertThat(result).isSameAs(existante);
        assertThat(result.getContenu()).isEqualTo("Exposé des faits et qualification pénale");
        verify(requeteParquetRepository).save(existante);
    }

    @Test
    void enregistrer_rejetteSiOutcomeNestPasJudicialReferral() {
        investigation.setOutcome(InvestigationOutcome.ARCHIVED);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.enregistrer(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
        verify(requeteParquetRepository, never()).save(any());
    }

    @Test
    void enregistrer_rejetteSiDecisionFinaleDejaRendue() {
        investigation.setCgeApprovedAt(Instant.now());
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.enregistrer(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
        verify(requeteParquetRepository, never()).save(any());
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiAucuneRequete() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(requeteParquetRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrThrow_propageBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(investigation.getDossier());

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(requeteParquetRepository, never()).findByInvestigationId(any());
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiDossierConfidentielEtAgentNonPrivilegie() {
        investigation.getDossier().setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(requeteParquetRepository, never()).findByInvestigationId(any());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvnw -Dtest=RequeteParquetServiceTest test`
Expected: FAIL (compilation error — `RequeteParquetService` n'existe pas)

- [ ] **Step 4: Créer `RequeteParquetService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import gov.bf.ascelc.univers_audits.model.dto.request.RequeteParquetRequest;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.RequeteParquetRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequeteParquetService {

    private final RequeteParquetRepository requeteParquetRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;

    @Transactional
    public RequeteParquet enregistrer(UUID investigationId, RequeteParquetRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkEditable(investigation);

        RequeteParquet requete = requeteParquetRepository.findByInvestigationId(investigationId)
                .orElseGet(() -> RequeteParquet.builder().investigation(investigation).build());

        requete.setContenu(request.getContenu());

        RequeteParquet saved = requeteParquetRepository.save(requete);
        log.info("Requête Parquet enregistrée — investigation: {}", investigationId);
        return saved;
    }

    public RequeteParquet getOrThrow(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new ResourceNotFoundException(
                    "Aucune requête Parquet n'a été rédigée pour cette investigation : "
                            + investigationId);
        }

        return requeteParquetRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune requête Parquet n'a été rédigée pour cette investigation : "
                                + investigationId));
    }

    private void checkEditable(Investigation investigation) {
        if (investigation.getOutcome() != InvestigationOutcome.JUDICIAL_REFERRAL) {
            throw new BusinessException(
                    "La requête au Parquet n'est applicable que pour une issue de saisine "
                            + "judiciaire (JUDICIAL_REFERRAL).");
        }
        if (investigation.getCgeApprovedAt() != null) {
            throw new BusinessException(
                    "La requête au Parquet n'est plus modifiable après la décision finale du CGE.");
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

Run: `mvnw -Dtest=RequeteParquetServiceTest test`
Expected: PASS (7 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RequeteParquetRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RequeteParquetResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/RequeteParquetService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/RequeteParquetServiceTest.java
git commit -m "feat: add RequeteParquetService"
```

---

### Task 3: Contrôleur `RequeteParquetController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/RequeteParquetController.java`

**Interfaces:**
- Consumes: `RequeteParquetService.{enregistrer, getOrThrow}` (Task 2), `ApiUrls.INVESTIGATIONS` (existant, `gov.bf.ascelc.univers_audits.shared.utils.ApiUrls`).
- Produces: endpoints `GET/PUT /api/v1/investigations/{id}/requete-parquet` — aucune tâche suivante n'en dépend.

Pas de test de contrôleur (convention déjà établie).

- [ ] **Step 1: Créer `RequeteParquetController`**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.RequeteParquetRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.RequeteParquetResponse;
import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import gov.bf.ascelc.univers_audits.service.RequeteParquetService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}")
public class RequeteParquetController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')";

    private final RequeteParquetService requeteParquetService;

    @GetMapping("/requete-parquet")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<RequeteParquetResponse> getRequeteParquet(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(requeteParquetService.getOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/requete-parquet")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<RequeteParquetResponse> putRequeteParquet(
            @PathVariable UUID id,
            @Valid @RequestBody RequeteParquetRequest request) {

        log.info("Enregistrement requête Parquet — investigation {}", id);
        RequeteParquet saved = requeteParquetService.enregistrer(id, request);
        return ResponseEntity.ok(toResponse(saved));
    }

    private RequeteParquetResponse toResponse(RequeteParquet r) {
        return RequeteParquetResponse.builder()
                .id(r.getId())
                .investigationId(r.getInvestigation().getId())
                .contenu(r.getContenu())
                .complet(r.isComplet())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 2: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/RequeteParquetController.java
git commit -m "feat: add requete-parquet REST endpoints"
```

---

### Task 4: DTO et `InventairePiecesService`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InventairePieceItemResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/InventairePiecesService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/InventairePiecesServiceTest.java`

**Interfaces:**
- Consumes: `AttachmentRepository.findByInvestigationId(UUID): List<Attachment>` (existant), `InvestigationRepository.findById(UUID): Optional<Investigation>` (existant), `DossierAccessGuard.{checkReadAccess(Dossier), canSeeConfidential()}` (existant), `Attachment.{getId(), getCode(), getDescription(), getSource(), getUploadedAt(), getModeObtention()}` (existant).
- Produces: `InventairePiecesService.getInventaire(UUID investigationId): List<InventairePieceItemResponse>` — consommé par la Tâche 5.

- [ ] **Step 1: Créer `InventairePieceItemResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventairePieceItemResponse {
    private UUID attachmentId;
    private String code;
    private String description;
    private AttachmentSource source;
    private LocalDateTime uploadedAt;
    private ModeObtention modeObtention;
}
```

- [ ] **Step 2: Écrire les tests de `InventairePiecesService` (échouent, la classe n'existe pas encore)**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import gov.bf.ascelc.univers_audits.model.dto.response.InventairePieceItemResponse;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventairePiecesServiceTest {

    @Mock
    private InvestigationRepository investigationRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private DossierAccessGuard accessGuard;

    @InjectMocks
    private InventairePiecesService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder().id(investigationId).dossier(dossier).build();
    }

    @Test
    void getInventaire_mappeLesChampsDesPiecesJointes() {
        LocalDateTime uploadedAt1 = LocalDateTime.of(2026, 1, 10, 9, 0);
        LocalDateTime uploadedAt2 = LocalDateTime.of(2026, 1, 12, 14, 30);
        Attachment piece1 = Attachment.builder()
                .id(UUID.randomUUID())
                .code("ACC-S-00001")
                .description("Relevé bancaire")
                .source(AttachmentSource.INITIAL_SUBMISSION)
                .uploadedAt(uploadedAt1)
                .modeObtention(ModeObtention.VOLONTAIRE)
                .build();
        Attachment piece2 = Attachment.builder()
                .id(UUID.randomUUID())
                .code("ACC-T-00002")
                .description("Facture saisie sur site")
                .source(AttachmentSource.FIELD_INVESTIGATION)
                .uploadedAt(uploadedAt2)
                .modeObtention(ModeObtention.REQUISITION)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(attachmentRepository.findByInvestigationId(investigationId))
                .thenReturn(List.of(piece1, piece2));

        List<InventairePieceItemResponse> result = service.getInventaire(investigationId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("ACC-S-00001");
        assertThat(result.get(0).getDescription()).isEqualTo("Relevé bancaire");
        assertThat(result.get(0).getSource()).isEqualTo(AttachmentSource.INITIAL_SUBMISSION);
        assertThat(result.get(0).getUploadedAt()).isEqualTo(uploadedAt1);
        assertThat(result.get(0).getModeObtention()).isEqualTo(ModeObtention.VOLONTAIRE);
        assertThat(result.get(1).getCode()).isEqualTo("ACC-T-00002");
        assertThat(result.get(1).getModeObtention()).isEqualTo(ModeObtention.REQUISITION);
    }

    @Test
    void getInventaire_renvoieListeVideSiAucunePieceJointe() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(attachmentRepository.findByInvestigationId(investigationId)).thenReturn(List.of());

        assertThat(service.getInventaire(investigationId)).isEmpty();
    }

    @Test
    void getInventaire_renvoieListeVideSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<InventairePieceItemResponse> result = service.getInventaire(investigationId);

        assertThat(result).isEmpty();
        verify(attachmentRepository, never()).findByInvestigationId(any());
    }

    @Test
    void getInventaire_leveSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.getInventaire(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(attachmentRepository, never()).findByInvestigationId(any());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvnw -Dtest=InventairePiecesServiceTest test`
Expected: FAIL (compilation error — `InventairePiecesService` n'existe pas)

- [ ] **Step 4: Créer `InventairePiecesService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.InventairePieceItemResponse;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventairePiecesService {

    private final InvestigationRepository investigationRepository;
    private final AttachmentRepository attachmentRepository;
    private final DossierAccessGuard accessGuard;

    public List<InventairePieceItemResponse> getInventaire(UUID investigationId) {
        Investigation investigation = investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return attachmentRepository.findByInvestigationId(investigationId).stream()
                .map(this::toItemResponse)
                .toList();
    }

    private InventairePieceItemResponse toItemResponse(Attachment attachment) {
        return InventairePieceItemResponse.builder()
                .attachmentId(attachment.getId())
                .code(attachment.getCode())
                .description(attachment.getDescription())
                .source(attachment.getSource())
                .uploadedAt(attachment.getUploadedAt())
                .modeObtention(attachment.getModeObtention())
                .build();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvnw -Dtest=InventairePiecesServiceTest test`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InventairePieceItemResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/InventairePiecesService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/InventairePiecesServiceTest.java
git commit -m "feat: add InventairePiecesService"
```

---

### Task 5: Contrôleur `InventairePiecesController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/InventairePiecesController.java`

**Interfaces:**
- Consumes: `InventairePiecesService.getInventaire(UUID): List<InventairePieceItemResponse>` (Task 4), `ApiUrls.INVESTIGATIONS` (existant).
- Produces: endpoint `GET /api/v1/investigations/{id}/inventaire-pieces` — dernière tâche du chantier.

Pas de test de contrôleur.

- [ ] **Step 1: Créer `InventairePiecesController`**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.InventairePieceItemResponse;
import gov.bf.ascelc.univers_audits.service.InventairePiecesService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}")
public class InventairePiecesController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";

    private final InventairePiecesService inventairePiecesService;

    @GetMapping("/inventaire-pieces")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<InventairePieceItemResponse>> getInventaire(
            @PathVariable UUID id) {
        return ResponseEntity.ok(inventairePiecesService.getInventaire(id));
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
git add src/main/java/gov/bf/ascelc/univers_audits/controller/InventairePiecesController.java
git commit -m "feat: add inventaire-pieces REST endpoint"
```
