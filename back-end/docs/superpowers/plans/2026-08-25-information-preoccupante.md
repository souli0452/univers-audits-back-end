# Information préoccupante Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter le module d'information préoccupante ASCE-LC : journalisation d'un signal de veille, rattachement a posteriori à un ou plusieurs dossiers, déclenchement d'auto-saisine.

**Architecture:** 2 nouvelles entités JPA (`InformationPreoccupante`, `InformationPreoccupanteDossier`, patron `SeanceCtadpDossier`), un service qui réutilise intégralement `DossierService.submit()` existant pour le déclenchement d'auto-saisine (zéro duplication de logique de création de dossier), un contrôleur REST.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, Liquibase, JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-25-information-preoccupante-design.md`

## Global Constraints

- Le champ `source` de `InformationPreoccupante` réutilise l'enum `AutoReferralSource` déjà existant — ne pas créer de nouvel enum redondant.
- Le rattachement a posteriori suit exactement le patron `SeanceCtadpDossier` (entité de jonction dédiée, index unique composite, pas de champs `linkedAt`/`linkedBy` redondants — `AuditEntity` fournit déjà `createdAt`/`createdById`).
- `declencherAutoSaisine` doit appeler `DossierService.submit()` existant, jamais dupliquer sa logique de création/validation de dossier.
- `SubmissionMode.AUDIT_REPORT` est la valeur fixée pour le `submissionMode` du dossier créé par auto-saisine (décision de conception documentée dans la spec, pas à deviner autrement).
- Accès : `@PreAuthorize("hasAnyRole('AGENT_BRPD','ADMIN_DDIC')")` sur tous les endpoints — pas de nouveau rôle DCP.
- Pas de test de contrôleur (convention du dépôt).

---

### Task 1 : Enum + entités + migration + repositories

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/StatutInformationPreoccupante.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/InformationPreoccupante.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/InformationPreoccupanteDossier.java`
- Create: `src/main/resources/db/changelog/migrations/040-create-information-preoccupante.sql`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/InformationPreoccupanteRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/InformationPreoccupanteDossierRepository.java`

**Interfaces:**
- Produces: entités `InformationPreoccupante`/`InformationPreoccupanteDossier` (champs listés
  ci-dessous), `InformationPreoccupanteRepository.findAllByOrderByDateReceptionDesc(Pageable):
  Page<InformationPreoccupante>`, `InformationPreoccupanteDossierRepository
  .findByInformationPreoccupanteId(UUID): List<InformationPreoccupanteDossier>`,
  `.existsByInformationPreoccupanteIdAndDossierId(UUID, UUID): boolean` — consommés par Task 2.

- [ ] **Step 1: Créer l'enum de statut**

Créer `src/main/java/gov/bf/ascelc/univers_audits/enums/StatutInformationPreoccupante.java` :

```java
package gov.bf.ascelc.univers_audits.enums;

public enum StatutInformationPreoccupante {
    NOUVELLE,
    RATTACHEE,
    AUTO_SAISINE_DECLENCHEE,
    CLASSEE_SANS_SUITE
}
```

- [ ] **Step 2: Créer l'entité `InformationPreoccupante`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/entity/InformationPreoccupante.java` :

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import gov.bf.ascelc.univers_audits.enums.StatutInformationPreoccupante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "information_preoccupante")
public class InformationPreoccupante extends AuditEntity {

    @Column(name = "objet", nullable = false, length = 500)
    private String objet;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private AutoReferralSource source;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Column(name = "date_reception", nullable = false)
    private Instant dateReception;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutInformationPreoccupante statut;
}
```

- [ ] **Step 3: Créer l'entité de jonction `InformationPreoccupanteDossier`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/entity/InformationPreoccupanteDossier.java` :

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "information_preoccupante_dossier", indexes = {
        @Index(name = "idx_ip_dossier_information",
                columnList = "information_preoccupante_id"),
        @Index(name = "idx_ip_dossier_dossier",
                columnList = "dossier_id"),
        @Index(name = "idx_ip_dossier_unique",
                columnList = "information_preoccupante_id, dossier_id", unique = true)
})
public class InformationPreoccupanteDossier extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "information_preoccupante_id", nullable = false)
    private InformationPreoccupante informationPreoccupante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Column(name = "commentaire", length = 2000)
    private String commentaire;
}
```

- [ ] **Step 4: Créer la migration Liquibase**

Créer `src/main/resources/db/changelog/migrations/040-create-information-preoccupante.sql` :

```sql
--liquibase formatted sql
--changeset dev:040-create-information-preoccupante

CREATE TABLE information_preoccupante (
    id                UUID         PRIMARY KEY,
    objet             VARCHAR(500) NOT NULL,
    description       TEXT         NOT NULL,
    source            VARCHAR(40)  NOT NULL,
    source_reference  VARCHAR(500),
    date_reception    TIMESTAMP    NOT NULL,
    statut            VARCHAR(30)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE TABLE information_preoccupante_dossier (
    id                            UUID         PRIMARY KEY,
    information_preoccupante_id  UUID         NOT NULL REFERENCES information_preoccupante(id),
    dossier_id                   UUID         NOT NULL REFERENCES dossier(id),
    commentaire                  VARCHAR(2000),
    version                      BIGINT       NOT NULL DEFAULT 0,
    created_at                   TIMESTAMP    NOT NULL,
    updated_at                   TIMESTAMP,
    created_by_id                VARCHAR(100),
    updated_by_id                VARCHAR(100)
);

CREATE UNIQUE INDEX idx_ip_dossier_unique
    ON information_preoccupante_dossier (information_preoccupante_id, dossier_id);

CREATE INDEX idx_ip_dossier_information
    ON information_preoccupante_dossier (information_preoccupante_id);

CREATE INDEX idx_ip_dossier_dossier
    ON information_preoccupante_dossier (dossier_id);

COMMENT ON TABLE information_preoccupante IS 'Signaux de veille (Lot 9 sous-chantier 1/3) - rattachables a posteriori a un ou plusieurs dossiers, ou pouvant declencher une auto-saisine';
COMMENT ON TABLE information_preoccupante_dossier IS 'Rattachement N-N information preoccupante / dossier, patron SeanceCtadpDossier';
```

Ramassée automatiquement par `includeAll` du changelog maître
(`src/main/resources/db/changelog/db.changelog-master.yaml`) — aucune inscription manuelle
nécessaire, `039-create-constitution-partie-civile.sql` est le dernier numéro existant.

- [ ] **Step 5: Créer `InformationPreoccupanteRepository`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/repository/InformationPreoccupanteRepository.java` :

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InformationPreoccupanteRepository
        extends JpaRepository<InformationPreoccupante, UUID> {

    Page<InformationPreoccupante> findAllByOrderByDateReceptionDesc(Pageable pageable);
}
```

- [ ] **Step 6: Créer `InformationPreoccupanteDossierRepository`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/repository/InformationPreoccupanteDossierRepository.java` :

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupanteDossier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InformationPreoccupanteDossierRepository
        extends JpaRepository<InformationPreoccupanteDossier, UUID> {

    List<InformationPreoccupanteDossier> findByInformationPreoccupanteId(
            UUID informationPreoccupanteId);

    boolean existsByInformationPreoccupanteIdAndDossierId(
            UUID informationPreoccupanteId, UUID dossierId);
}
```

- [ ] **Step 7: Vérifier la compilation**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS. (Pas de test dédié à cette étape — entités/migration/repositories
purs, pas de logique métier ; vérifiés par la compilation et par les tests de service de la
Task 2, qui les exercent via Mockito.)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/StatutInformationPreoccupante.java src/main/java/gov/bf/ascelc/univers_audits/model/entity/InformationPreoccupante.java src/main/java/gov/bf/ascelc/univers_audits/model/entity/InformationPreoccupanteDossier.java src/main/resources/db/changelog/migrations/040-create-information-preoccupante.sql src/main/java/gov/bf/ascelc/univers_audits/repository/InformationPreoccupanteRepository.java src/main/java/gov/bf/ascelc/univers_audits/repository/InformationPreoccupanteDossierRepository.java
git commit -m "feat(information-preoccupante): add entities, migration and repositories"
```

---

### Task 2 : DTOs + service + tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/InformationPreoccupanteCreateRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RattacherDossierRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InformationPreoccupanteResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/InformationPreoccupanteService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InformationPreoccupanteServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InformationPreoccupanteServiceImplTest.java`

**Interfaces:**
- Consumes : entités et repositories de la Task 1 (voir signatures ci-dessus) ;
  `DossierService.submit(DossierCreateRequest, String): DossierResponse` (déjà existant) ;
  `DossierRepository.findById(UUID): Optional<Dossier>` et
  `.getReferenceById(UUID): Dossier` (déjà existants, `JpaRepository` standard).
- Produces : `InformationPreoccupanteService.{create, findById, findAll, rattacherDossier,
  declencherAutoSaisine, classerSansSuite}` — consommé par Task 3.

- [ ] **Step 1: Créer les DTOs**

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/InformationPreoccupanteCreateRequest.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformationPreoccupanteCreateRequest {

    @NotBlank(message = "L'objet est obligatoire")
    @Size(max = 500, message = "L'objet ne doit pas dépasser 500 caractères")
    private String objet;

    @NotBlank(message = "La description est obligatoire")
    @Size(max = 10000, message = "La description ne doit pas dépasser 10000 caractères")
    private String description;

    @NotNull(message = "La source est obligatoire")
    private AutoReferralSource source;

    @Size(max = 500, message = "La référence source ne doit pas dépasser 500 caractères")
    private String sourceReference;

    @NotNull(message = "La date de réception est obligatoire")
    private Instant dateReception;
}
```

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RattacherDossierRequest.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

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
public class RattacherDossierRequest {

    @Size(max = 2000, message = "Le commentaire ne doit pas dépasser 2000 caractères")
    private String commentaire;
}
```

Créer `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InformationPreoccupanteResponse.java` :

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import gov.bf.ascelc.univers_audits.enums.StatutInformationPreoccupante;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InformationPreoccupanteResponse {

    private UUID id;
    private String objet;
    private String description;
    private AutoReferralSource source;
    private String sourceReference;
    private Instant dateReception;
    private StatutInformationPreoccupante statut;
    private Instant createdAt;
    private List<DossierRattacheResponse> dossiersRattaches;

    @Data
    @Builder
    public static class DossierRattacheResponse {
        private UUID dossierId;
        private String dossierNumber;
        private String commentaire;
        private Instant linkedAt;
    }
}
```

- [ ] **Step 2: Créer l'interface du service**

Créer `src/main/java/gov/bf/ascelc/univers_audits/service/InformationPreoccupanteService.java` :

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.InformationPreoccupanteCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RattacherDossierRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InformationPreoccupanteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InformationPreoccupanteService {

    InformationPreoccupanteResponse create(InformationPreoccupanteCreateRequest request);

    InformationPreoccupanteResponse findById(UUID id);

    Page<InformationPreoccupanteResponse> findAll(Pageable pageable);

    InformationPreoccupanteResponse rattacherDossier(
            UUID informationPreoccupanteId, UUID dossierId, RattacherDossierRequest request);

    DossierResponse declencherAutoSaisine(UUID informationPreoccupanteId, String ipAddress);

    InformationPreoccupanteResponse classerSansSuite(UUID informationPreoccupanteId);
}
```

- [ ] **Step 3: Écrire le test (échec de compilation attendu, l'implémentation n'existe pas encore)**

Créer `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InformationPreoccupanteServiceImplTest.java` :

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import gov.bf.ascelc.univers_audits.enums.StatutInformationPreoccupante;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InformationPreoccupanteCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RattacherDossierRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InformationPreoccupanteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupante;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InformationPreoccupanteDossierRepository;
import gov.bf.ascelc.univers_audits.repository.InformationPreoccupanteRepository;
import gov.bf.ascelc.univers_audits.service.DossierService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InformationPreoccupanteServiceImplTest {

    @Mock private InformationPreoccupanteRepository informationPreoccupanteRepository;
    @Mock private InformationPreoccupanteDossierRepository informationPreoccupanteDossierRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private DossierService dossierService;

    @InjectMocks
    private InformationPreoccupanteServiceImpl service;

    private InformationPreoccupante buildInfo(StatutInformationPreoccupante statut) {
        return InformationPreoccupante.builder()
                .id(UUID.randomUUID())
                .objet("Signalement presse")
                .description("Article évoquant des irrégularités")
                .source(AutoReferralSource.WRITTEN_PRESS)
                .dateReception(Instant.now())
                .statut(statut)
                .build();
    }

    @Test
    void create_creeUneInformationPreoccupanteAvecStatutNouvelle() {
        InformationPreoccupanteCreateRequest request = InformationPreoccupanteCreateRequest.builder()
                .objet("Signalement presse")
                .description("Article évoquant des irrégularités")
                .source(AutoReferralSource.WRITTEN_PRESS)
                .dateReception(Instant.now())
                .build();

        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InformationPreoccupanteResponse result = service.create(request);

        assertThat(result.getStatut()).isEqualTo(StatutInformationPreoccupante.NOUVELLE);
        assertThat(result.getObjet()).isEqualTo("Signalement presse");
    }

    @Test
    void rattacherDossier_passeLeStatutARattacheeSiNouvelle() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).number("ASCE-2026-000001").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(informationPreoccupanteDossierRepository
                .existsByInformationPreoccupanteIdAndDossierId(info.getId(), dossierId))
                .thenReturn(false);
        when(informationPreoccupanteDossierRepository.findByInformationPreoccupanteId(info.getId()))
                .thenReturn(List.of());
        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InformationPreoccupanteResponse result = service.rattacherDossier(
                info.getId(), dossierId, RattacherDossierRequest.builder().commentaire("lien").build());

        assertThat(result.getStatut()).isEqualTo(StatutInformationPreoccupante.RATTACHEE);
    }

    @Test
    void rattacherDossier_neRetrogradePasUnStatutAutoSaisineDeclenchee() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).number("ASCE-2026-000002").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(informationPreoccupanteDossierRepository
                .existsByInformationPreoccupanteIdAndDossierId(info.getId(), dossierId))
                .thenReturn(false);
        when(informationPreoccupanteDossierRepository.findByInformationPreoccupanteId(info.getId()))
                .thenReturn(List.of());

        InformationPreoccupanteResponse result = service.rattacherDossier(info.getId(), dossierId, null);

        assertThat(result.getStatut()).isEqualTo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);
        verify(informationPreoccupanteRepository, never()).save(any(InformationPreoccupante.class));
    }

    @Test
    void rattacherDossier_refuseSiClasseeSansSuite() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.CLASSEE_SANS_SUITE);
        UUID dossierId = UUID.randomUUID();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));

        assertThatThrownBy(() -> service.rattacherDossier(info.getId(), dossierId, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rattacherDossier_refuseSiDejaRattacheAuMemeDossier() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).number("ASCE-2026-000003").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(informationPreoccupanteDossierRepository
                .existsByInformationPreoccupanteIdAndDossierId(info.getId(), dossierId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.rattacherDossier(info.getId(), dossierId, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void declencherAutoSaisine_appelleDossierServiceSubmitAvecLesBonsChamps() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        DossierResponse created = DossierResponse.builder().id(dossierId).number("ASCE-2026-000004").build();
        Dossier dossierRef = Dossier.builder().id(dossierId).number("ASCE-2026-000004").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierService.submit(any(DossierCreateRequest.class), anyString())).thenReturn(created);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossierRef));
        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.declencherAutoSaisine(info.getId(), "127.0.0.1");

        ArgumentCaptor<DossierCreateRequest> captor = ArgumentCaptor.forClass(DossierCreateRequest.class);
        verify(dossierService).submit(captor.capture(), eq("127.0.0.1"));
        DossierCreateRequest sent = captor.getValue();
        assertThat(sent.getSubmissionMode()).isEqualTo(SubmissionMode.AUDIT_REPORT);
        assertThat(sent.getAutoReferralSource()).isEqualTo(AutoReferralSource.WRITTEN_PRESS);
        assertThat(sent.getObject()).isEqualTo("Signalement presse");
        assertThat(sent.getDeclarantData().getTypeDeclarant()).isEqualTo(TypeDeclarant.ASCE_SELF_REFERRAL);
    }

    @Test
    void declencherAutoSaisine_passeLeStatutAAutoSaisineDeclenchee() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        DossierResponse created = DossierResponse.builder().id(dossierId).number("ASCE-2026-000005").build();
        Dossier dossierRef = Dossier.builder().id(dossierId).number("ASCE-2026-000005").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierService.submit(any(DossierCreateRequest.class), anyString())).thenReturn(created);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossierRef));
        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.declencherAutoSaisine(info.getId(), "127.0.0.1");

        assertThat(info.getStatut()).isEqualTo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);
        verify(informationPreoccupanteDossierRepository, times(1)).save(any());
    }

    @Test
    void declencherAutoSaisine_refuseSiDejaDeclenchee() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));

        assertThatThrownBy(() -> service.declencherAutoSaisine(info.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(dossierService, never()).submit(any(), anyString());
    }

    @Test
    void declencherAutoSaisine_refuseSiClasseeSansSuite() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.CLASSEE_SANS_SUITE);

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));

        assertThatThrownBy(() -> service.declencherAutoSaisine(info.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(dossierService, never()).submit(any(), anyString());
    }

    @Test
    void classerSansSuite_refuseSiAutoSaisineDejaDeclenchee() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));

        assertThatThrownBy(() -> service.classerSansSuite(info.getId()))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./mvnw -q -Dtest=InformationPreoccupanteServiceImplTest test`
Expected: FAIL — erreur de compilation, `InformationPreoccupanteServiceImpl` n'existe pas.

- [ ] **Step 5: Créer l'implémentation**

Créer `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InformationPreoccupanteServiceImpl.java` :

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.StatutInformationPreoccupante;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.dto.request.DeclarantCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InformationPreoccupanteCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RattacherDossierRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InformationPreoccupanteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupante;
import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupanteDossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InformationPreoccupanteDossierRepository;
import gov.bf.ascelc.univers_audits.repository.InformationPreoccupanteRepository;
import gov.bf.ascelc.univers_audits.service.DossierService;
import gov.bf.ascelc.univers_audits.service.InformationPreoccupanteService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InformationPreoccupanteServiceImpl implements InformationPreoccupanteService {

    private final InformationPreoccupanteRepository informationPreoccupanteRepository;
    private final InformationPreoccupanteDossierRepository informationPreoccupanteDossierRepository;
    private final DossierRepository dossierRepository;
    private final DossierService dossierService;

    @Override
    public InformationPreoccupanteResponse create(InformationPreoccupanteCreateRequest request) {
        InformationPreoccupante entity = InformationPreoccupante.builder()
                .objet(request.getObjet())
                .description(request.getDescription())
                .source(request.getSource())
                .sourceReference(request.getSourceReference())
                .dateReception(request.getDateReception())
                .statut(StatutInformationPreoccupante.NOUVELLE)
                .build();

        InformationPreoccupante saved = informationPreoccupanteRepository.save(entity);
        return toResponse(saved, List.of());
    }

    @Override
    public InformationPreoccupanteResponse findById(UUID id) {
        InformationPreoccupante entity = getOrThrow(id);
        List<InformationPreoccupanteDossier> links =
                informationPreoccupanteDossierRepository.findByInformationPreoccupanteId(id);
        return toResponse(entity, links);
    }

    @Override
    public Page<InformationPreoccupanteResponse> findAll(Pageable pageable) {
        return informationPreoccupanteRepository.findAllByOrderByDateReceptionDesc(pageable)
                .map(entity -> toResponse(entity,
                        informationPreoccupanteDossierRepository
                                .findByInformationPreoccupanteId(entity.getId())));
    }

    @Override
    public InformationPreoccupanteResponse rattacherDossier(
            UUID informationPreoccupanteId, UUID dossierId, RattacherDossierRequest request) {

        InformationPreoccupante info = getOrThrow(informationPreoccupanteId);

        if (info.getStatut() == StatutInformationPreoccupante.CLASSEE_SANS_SUITE) {
            throw new BusinessException(
                    "Impossible de rattacher un dossier à une information classée sans suite.");
        }

        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));

        if (informationPreoccupanteDossierRepository
                .existsByInformationPreoccupanteIdAndDossierId(informationPreoccupanteId, dossierId)) {
            throw new BusinessException(
                    "Ce dossier est déjà rattaché à cette information préoccupante.");
        }

        InformationPreoccupanteDossier link = InformationPreoccupanteDossier.builder()
                .informationPreoccupante(info)
                .dossier(dossier)
                .commentaire(request != null ? request.getCommentaire() : null)
                .build();
        informationPreoccupanteDossierRepository.save(link);

        if (info.getStatut() == StatutInformationPreoccupante.NOUVELLE) {
            info.setStatut(StatutInformationPreoccupante.RATTACHEE);
            informationPreoccupanteRepository.save(info);
        }

        List<InformationPreoccupanteDossier> links = informationPreoccupanteDossierRepository
                .findByInformationPreoccupanteId(informationPreoccupanteId);
        return toResponse(info, links);
    }

    @Override
    public DossierResponse declencherAutoSaisine(UUID informationPreoccupanteId, String ipAddress) {

        InformationPreoccupante info = getOrThrow(informationPreoccupanteId);

        if (info.getStatut() == StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE) {
            throw new BusinessException(
                    "Une auto-saisine a déjà été déclenchée pour cette information préoccupante.");
        }
        if (info.getStatut() == StatutInformationPreoccupante.CLASSEE_SANS_SUITE) {
            throw new BusinessException(
                    "Impossible de déclencher une auto-saisine depuis une information classée sans suite.");
        }

        DossierCreateRequest dossierRequest = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.AUDIT_REPORT)
                .autoReferralSource(info.getSource())
                .object(info.getObjet())
                .description(info.getDescription())
                .declarantData(DeclarantCreateRequest.builder()
                        .typeDeclarant(TypeDeclarant.ASCE_SELF_REFERRAL)
                        .build())
                .build();

        DossierResponse created = dossierService.submit(dossierRequest, ipAddress);

        Dossier dossier = dossierRepository.findById(created.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier nouvellement créé introuvable : " + created.getId()));

        InformationPreoccupanteDossier link = InformationPreoccupanteDossier.builder()
                .informationPreoccupante(info)
                .dossier(dossier)
                .commentaire("Dossier créé par déclenchement d'auto-saisine")
                .build();
        informationPreoccupanteDossierRepository.save(link);

        info.setStatut(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);
        informationPreoccupanteRepository.save(info);

        return created;
    }

    @Override
    public InformationPreoccupanteResponse classerSansSuite(UUID informationPreoccupanteId) {

        InformationPreoccupante info = getOrThrow(informationPreoccupanteId);

        if (info.getStatut() == StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE) {
            throw new BusinessException(
                    "Impossible de classer sans suite une information ayant déjà déclenché une auto-saisine.");
        }

        info.setStatut(StatutInformationPreoccupante.CLASSEE_SANS_SUITE);
        InformationPreoccupante saved = informationPreoccupanteRepository.save(info);

        List<InformationPreoccupanteDossier> links = informationPreoccupanteDossierRepository
                .findByInformationPreoccupanteId(informationPreoccupanteId);
        return toResponse(saved, links);
    }

    private InformationPreoccupante getOrThrow(UUID id) {
        return informationPreoccupanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Information préoccupante introuvable : " + id));
    }

    private InformationPreoccupanteResponse toResponse(
            InformationPreoccupante entity, List<InformationPreoccupanteDossier> links) {

        List<InformationPreoccupanteResponse.DossierRattacheResponse> dossiersRattaches = links.stream()
                .map(link -> InformationPreoccupanteResponse.DossierRattacheResponse.builder()
                        .dossierId(link.getDossier().getId())
                        .dossierNumber(link.getDossier().getNumber())
                        .commentaire(link.getCommentaire())
                        .linkedAt(link.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return InformationPreoccupanteResponse.builder()
                .id(entity.getId())
                .objet(entity.getObjet())
                .description(entity.getDescription())
                .source(entity.getSource())
                .sourceReference(entity.getSourceReference())
                .dateReception(entity.getDateReception())
                .statut(entity.getStatut())
                .createdAt(entity.getCreatedAt())
                .dossiersRattaches(dossiersRattaches)
                .build();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./mvnw -q -Dtest=InformationPreoccupanteServiceImplTest test`
Expected: PASS — 10 tests, 0 échec.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/InformationPreoccupanteCreateRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RattacherDossierRequest.java src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InformationPreoccupanteResponse.java src/main/java/gov/bf/ascelc/univers_audits/service/InformationPreoccupanteService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/InformationPreoccupanteServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/InformationPreoccupanteServiceImplTest.java
git commit -m "feat(information-preoccupante): add service with auto-saisine trigger"
```

---

### Task 3 : Endpoint REST

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/ApiUrls.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/InformationPreoccupanteController.java`

**Interfaces:**
- Consumes: `InformationPreoccupanteService.{create, findById, findAll, rattacherDossier,
  declencherAutoSaisine, classerSansSuite}` (Task 2).

- [ ] **Step 1: Ajouter la constante de route**

Dans `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/ApiUrls.java`, ajouter après le
bloc `STATS` existant (ligne 34) :

```java

    // ── Informations préoccupantes ────────────────────────────

    public static final String INFORMATIONS_PREOCCUPANTES = BASE + "/informations-preoccupantes";
```

- [ ] **Step 2: Créer le contrôleur**

Créer `src/main/java/gov/bf/ascelc/univers_audits/controller/InformationPreoccupanteController.java` :

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.InformationPreoccupanteCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RattacherDossierRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InformationPreoccupanteResponse;
import gov.bf.ascelc.univers_audits.service.InformationPreoccupanteService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INFORMATIONS_PREOCCUPANTES)
public class InformationPreoccupanteController {

    private final InformationPreoccupanteService informationPreoccupanteService;

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank())
                ? xff.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','ADMIN_DDIC')")
    public ResponseEntity<InformationPreoccupanteResponse> create(
            @Valid @RequestBody InformationPreoccupanteCreateRequest request) {
        log.info("Création d'une information préoccupante — objet : {}", request.getObjet());
        InformationPreoccupanteResponse result = informationPreoccupanteService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','ADMIN_DDIC')")
    public ResponseEntity<Page<InformationPreoccupanteResponse>> findAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(informationPreoccupanteService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','ADMIN_DDIC')")
    public ResponseEntity<InformationPreoccupanteResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(informationPreoccupanteService.findById(id));
    }

    @PostMapping("/{id}/rattacher-dossier/{dossierId}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','ADMIN_DDIC')")
    public ResponseEntity<InformationPreoccupanteResponse> rattacherDossier(
            @PathVariable UUID id,
            @PathVariable UUID dossierId,
            @RequestBody(required = false) RattacherDossierRequest request) {
        log.info("Rattachement dossier {} à l'information préoccupante {}", dossierId, id);
        return ResponseEntity.ok(
                informationPreoccupanteService.rattacherDossier(id, dossierId, request));
    }

    @PostMapping("/{id}/declencher-auto-saisine")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> declencherAutoSaisine(
            @PathVariable UUID id, HttpServletRequest httpRequest) {
        log.info("Déclenchement auto-saisine depuis l'information préoccupante {}", id);
        DossierResponse result = informationPreoccupanteService
                .declencherAutoSaisine(id, getClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{id}/classer-sans-suite")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','ADMIN_DDIC')")
    public ResponseEntity<InformationPreoccupanteResponse> classerSansSuite(@PathVariable UUID id) {
        log.info("Classement sans suite de l'information préoccupante {}", id);
        return ResponseEntity.ok(informationPreoccupanteService.classerSansSuite(id));
    }
}
```

- [ ] **Step 3: Compiler et lancer la suite de tests complète**

Run: `./mvnw -q test`
Expected: PASS — tous les tests existants plus les 10 nouveaux de
`InformationPreoccupanteServiceImplTest`, 0 échec. Pas de test de contrôleur (convention du
dépôt).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/utils/ApiUrls.java src/main/java/gov/bf/ascelc/univers_audits/controller/InformationPreoccupanteController.java
git commit -m "feat(information-preoccupante): expose REST endpoints"
```
