# Check-list du dossier de travail (Lot 5, sous-chantier 2/4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter une check-list configurable des 22 points du dossier de travail, avec un état de coche par investigation, qui bloque `submitReport()` tant qu'un point actif n'est pas coché.

**Architecture:** Deux entités JPA — un référentiel global `PointChecklistDossierTravail` (administrable, jamais supprimé en dur) et un état par investigation `ChecklistDossierTravailCoche` (upsert à la volée, une ligne par couple investigation/point) — deux services concrets sans interface, deux contrôleurs REST séparés, puis modification de `InvestigationServiceImpl.submitReport()` pour ajouter une troisième vérification de complétude après celles déjà en place (rapport, note de recommandations).

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok (`@SuperBuilder`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-13-checklist-dossier-travail-design.md`

## Global Constraints

- Migration Liquibase : fichier `032-create-checklist-dossier-travail.sql`, format `--liquibase formatted sql` / `--changeset dev:032-create-checklist-dossier-travail` (numéro confirmé libre, dernier existant = `031`).
- Pas de `DELETE` dur sur le référentiel `PointChecklistDossierTravail` — désactivation via `actif=false` uniquement (principe §8.2 du plan de travail : suppression logique seulement).
- Le contenu initial des 22 points est **provisoire** (`libelle` = "Point de contrôle N — contenu à confirmer avec le manuel de procédures ASCE-LC", `categorie` = `PROVISOIRE`) — ne pas inventer un contenu plus élaboré pendant l'implémentation, c'est un choix délibéré documenté dans la spec.
- État de coche créé/mis à jour à la volée par `PUT .../checklist/{pointCode}` — rien n'est pré-créé à l'ouverture de l'investigation.
- Rôles écriture (coche investigation) = `hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')`, identiques à `RapportEnqueteController.WRITE_ROLES`. Rôles lecture (coche investigation) = `hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')`, identiques à `RapportEnqueteController.READ_ROLES`. Rôles référentiel admin = `hasAnyRole('ADMIN_DDIC','CGEA')`, identiques à `ParametreDelaiController`.
- Contrôle d'accès de l'état par investigation : `ChecklistDossierTravailService` appelle `DossierAccessGuard.checkReadAccess(investigation.getDossier())` sur ses méthodes de lecture ET d'écriture, plus masquage confidentialité (`isConfidential && !canSeeConfidential()` → liste vide) sur la lecture — même patron que `InvestigationServiceImpl.getIncidents()` (`InvestigationServiceImpl.java:920-936`). Le référentiel global (`PointChecklistDossierTravailController`) n'appelle **pas** `DossierAccessGuard` — ce n'est pas une sous-ressource de dossier.
- Convention de service déjà établie dans ce dépôt (`ParametreDelaiServiceImpl`) : classe annotée `@Transactional(readOnly = true)`, chaque méthode d'écriture réannotée `@Transactional`.
- Aucun test de contrôleur (`@WebMvcTest`/MockMvc) : confirmé par `find . -iname "*ControllerTest.java"` → 0 résultat dans tout le projet. Les contrôleurs ne sont jamais testés directement dans ce codebase.
- `InvestigationServiceImpl` gagne une nouvelle dépendance `ChecklistDossierTravailService` (pas les repositories bruts) injectée via le constructeur `@RequiredArgsConstructor` existant — la logique de comptage "tous les points actifs sont-ils cochés" vit une seule fois, dans le service, pas dupliquée dans `InvestigationServiceImpl`.

---

### Task 1: Entités, repositories, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/PointChecklistDossierTravail.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/ChecklistDossierTravailCoche.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/PointChecklistDossierTravailRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/ChecklistDossierTravailCocheRepository.java`
- Create: `src/main/resources/db/changelog/migrations/032-create-checklist-dossier-travail.sql`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/model/entity/PointChecklistDossierTravailTest.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/model/entity/ChecklistDossierTravailCocheTest.java`

**Interfaces:**
- Consumes: `AuditEntity` (`gov.bf.ascelc.univers_audits.abstracts.AuditEntity`), `Investigation` (`gov.bf.ascelc.univers_audits.model.entity.Investigation`, champ `id`), `Agent` (`gov.bf.ascelc.univers_audits.model.entity.Agent`, champ `id`).
- Produces: `PointChecklistDossierTravail` (champs `code`, `libelle`, `categorie`, `ordre`, `actif`), `ChecklistDossierTravailCoche` (champs `investigation`, `point`, `coche`, `cochePar`, `cocheAt`, `commentaire`), `PointChecklistDossierTravailRepository.{findByActifTrueOrderByOrdreAsc, findAllByOrderByOrdreAsc, findByCode, existsByCode}`, `ChecklistDossierTravailCocheRepository.{findByInvestigationId, findByInvestigationIdAndPointId, countByInvestigationIdAndCocheTrue}` — consommés par les tâches 2 et 3.

- [ ] **Step 1: Créer l'entité `PointChecklistDossierTravail`**

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
@Table(name = "point_checklist_dossier_travail", indexes = {
        @Index(name = "idx_point_checklist_code",
                columnList = "code", unique = true)
})
public class PointChecklistDossierTravail extends AuditEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 500)
    private String libelle;

    @Column(name = "categorie", length = 100)
    private String categorie;

    @Column(name = "ordre", nullable = false)
    private Integer ordre;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;
}
```

- [ ] **Step 2: Écrire le test de l'entité `PointChecklistDossierTravail`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointChecklistDossierTravailTest {

    @Test
    void builder_actifEstVraiParDefaut() {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .code("PT-01")
                .libelle("Point de contrôle 1")
                .ordre(1)
                .build();

        assertThat(point.getActif()).isTrue();
    }

    @Test
    void builder_actifPeutEtreDesactiveExplicitement() {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .code("PT-01")
                .libelle("Point de contrôle 1")
                .ordre(1)
                .actif(false)
                .build();

        assertThat(point.getActif()).isFalse();
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `mvnw -Dtest=PointChecklistDossierTravailTest test`
Expected: PASS (2 tests)

- [ ] **Step 4: Créer l'entité `ChecklistDossierTravailCoche`**

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
@Table(name = "checklist_dossier_travail_coche", indexes = {
        @Index(name = "idx_checklist_coche_investigation",
                columnList = "investigation_id"),
        @Index(name = "idx_checklist_coche_unique",
                columnList = "investigation_id, point_id", unique = true)
})
public class ChecklistDossierTravailCoche extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_id", nullable = false)
    private PointChecklistDossierTravail point;

    @Column(name = "coche", nullable = false)
    @Builder.Default
    private Boolean coche = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coche_par_id")
    private Agent cochePar;

    @Column(name = "coche_at")
    private Instant cocheAt;

    @Column(name = "commentaire", length = 2000)
    private String commentaire;
}
```

- [ ] **Step 5: Écrire le test de l'entité `ChecklistDossierTravailCoche`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistDossierTravailCocheTest {

    @Test
    void builder_cocheEstFauxParDefaut() {
        ChecklistDossierTravailCoche etat = ChecklistDossierTravailCoche.builder()
                .investigation(Investigation.builder().build())
                .point(PointChecklistDossierTravail.builder().build())
                .build();

        assertThat(etat.getCoche()).isFalse();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvnw -Dtest=ChecklistDossierTravailCocheTest test`
Expected: PASS (1 test)

- [ ] **Step 7: Créer `PointChecklistDossierTravailRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PointChecklistDossierTravailRepository
        extends JpaRepository<PointChecklistDossierTravail, UUID> {
    List<PointChecklistDossierTravail> findByActifTrueOrderByOrdreAsc();
    List<PointChecklistDossierTravail> findAllByOrderByOrdreAsc();
    Optional<PointChecklistDossierTravail> findByCode(String code);
    boolean existsByCode(String code);
}
```

- [ ] **Step 8: Créer `ChecklistDossierTravailCocheRepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.ChecklistDossierTravailCoche;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChecklistDossierTravailCocheRepository
        extends JpaRepository<ChecklistDossierTravailCoche, UUID> {
    List<ChecklistDossierTravailCoche> findByInvestigationId(UUID investigationId);
    Optional<ChecklistDossierTravailCoche> findByInvestigationIdAndPointId(
            UUID investigationId, UUID pointId);
    long countByInvestigationIdAndCocheTrue(UUID investigationId);
}
```

- [ ] **Step 9: Créer la migration `032-create-checklist-dossier-travail.sql`**

```sql
--liquibase formatted sql
--changeset dev:032-create-checklist-dossier-travail

CREATE TABLE point_checklist_dossier_travail (
    id            UUID         PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,
    libelle       VARCHAR(500) NOT NULL,
    categorie     VARCHAR(100),
    ordre         INTEGER      NOT NULL,
    actif         BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    created_by_id VARCHAR(100),
    updated_by_id VARCHAR(100),
    CONSTRAINT uq_point_checklist_code UNIQUE (code)
);

CREATE TABLE checklist_dossier_travail_coche (
    id              UUID      PRIMARY KEY,
    investigation_id UUID     NOT NULL REFERENCES investigation(id),
    point_id        UUID      NOT NULL REFERENCES point_checklist_dossier_travail(id),
    coche           BOOLEAN   NOT NULL DEFAULT FALSE,
    coche_par_id    UUID      REFERENCES agent(id),
    coche_at        TIMESTAMP,
    commentaire     VARCHAR(2000),
    version         BIGINT    NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by_id   VARCHAR(100),
    updated_by_id   VARCHAR(100),
    CONSTRAINT uq_checklist_coche_investigation_point UNIQUE (investigation_id, point_id)
);

CREATE INDEX idx_checklist_coche_investigation
    ON checklist_dossier_travail_coche (investigation_id);

-- Contenu PROVISOIRE : le manuel de procédures ASCE-LC listant les 22 points réels
-- n'est pas disponible dans ce dépôt (voir spec 2026-08-13). Ces libellés doivent être
-- corrigés via PUT /api/v1/points-checklist-dossier-travail/{code} avant tout usage réel.
INSERT INTO point_checklist_dossier_travail (id, code, libelle, categorie, ordre, actif, version, created_at)
SELECT gen_random_uuid(),
       'PT-' || LPAD(n::text, 2, '0'),
       'Point de contrôle ' || n || ' — contenu à confirmer avec le manuel de procédures ASCE-LC',
       'PROVISOIRE',
       n,
       TRUE,
       0,
       now()
FROM generate_series(1, 22) AS n;

COMMENT ON TABLE point_checklist_dossier_travail IS 'Referentiel configurable des points de la check-list du dossier de travail (Lot 5 sous-chantier 2/4) - contenu initial PROVISOIRE, a corriger via l administration avant usage reel';
COMMENT ON TABLE checklist_dossier_travail_coche IS 'Etat de coche par investigation pour chaque point actif du referentiel - bloque submitReport() tant qu un point actif reste non coche';
```

- [ ] **Step 10: Vérifier que le module compile avec les nouveaux fichiers**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 11: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/PointChecklistDossierTravail.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/ChecklistDossierTravailCoche.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/PointChecklistDossierTravailRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/ChecklistDossierTravailCocheRepository.java \
        src/main/resources/db/changelog/migrations/032-create-checklist-dossier-travail.sql \
        src/test/java/gov/bf/ascelc/univers_audits/model/entity/PointChecklistDossierTravailTest.java \
        src/test/java/gov/bf/ascelc/univers_audits/model/entity/ChecklistDossierTravailCocheTest.java
git commit -m "feat: add PointChecklistDossierTravail/ChecklistDossierTravailCoche entities and migration"
```

---

### Task 2: Référentiel — DTOs et `PointChecklistDossierTravailService`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PointChecklistDossierTravailRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/PointChecklistDossierTravailService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/PointChecklistDossierTravailServiceTest.java`

**Interfaces:**
- Consumes: `PointChecklistDossierTravailRepository` (Task 1) — `findByActifTrueOrderByOrdreAsc()`, `findAllByOrderByOrdreAsc()`, `findByCode(String)`, `existsByCode(String)`, `save(PointChecklistDossierTravail)`.
- Produces: `PointChecklistDossierTravailService.{findAllActifs(): List<PointChecklistDossierTravail>, findAll(): List<PointChecklistDossierTravail>, create(PointChecklistDossierTravailRequest): PointChecklistDossierTravail, update(String code, PointChecklistDossierTravailRequest): PointChecklistDossierTravail}` — consommé par Task 4 (`PointChecklistDossierTravailController`).

- [ ] **Step 1: Créer `PointChecklistDossierTravailRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointChecklistDossierTravailRequest {
    @NotBlank(message = "Le libellé est obligatoire")
    private String libelle;
    private String categorie;
    @NotNull(message = "L'ordre est obligatoire")
    private Integer ordre;
    private Boolean actif;
}
```

- [ ] **Step 2: Écrire les tests de `PointChecklistDossierTravailService` (échouent, la classe n'existe pas encore)**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PointChecklistDossierTravailRequest;
import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import gov.bf.ascelc.univers_audits.repository.PointChecklistDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointChecklistDossierTravailServiceTest {

    @Mock
    private PointChecklistDossierTravailRepository repository;

    @InjectMocks
    private PointChecklistDossierTravailService service;

    @Test
    void create_genereLePremierCodeLibreQuandLeReferentielEstVide() {
        when(repository.existsByCode("PT-01")).thenReturn(false);
        when(repository.save(any(PointChecklistDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("Premier point").ordre(1).build();

        PointChecklistDossierTravail result = service.create(request);

        assertThat(result.getCode()).isEqualTo("PT-01");
        assertThat(result.getLibelle()).isEqualTo("Premier point");
        assertThat(result.getActif()).isTrue();
    }

    @Test
    void create_sauteLesCodesDejaPris() {
        when(repository.existsByCode("PT-01")).thenReturn(true);
        when(repository.existsByCode("PT-02")).thenReturn(true);
        when(repository.existsByCode("PT-03")).thenReturn(false);
        when(repository.save(any(PointChecklistDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("Troisième point").ordre(3).build();

        PointChecklistDossierTravail result = service.create(request);

        assertThat(result.getCode()).isEqualTo("PT-03");
    }

    @Test
    void create_actifFauxExplicitementRespecte() {
        when(repository.existsByCode("PT-01")).thenReturn(false);
        when(repository.save(any(PointChecklistDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("Point désactivé").ordre(1).actif(false).build();

        PointChecklistDossierTravail result = service.create(request);

        assertThat(result.getActif()).isFalse();
    }

    @Test
    void update_metAJourLesChampsDuPointExistant() {
        PointChecklistDossierTravail existant = PointChecklistDossierTravail.builder()
                .code("PT-01").libelle("Ancien libellé").ordre(1).actif(true).build();
        when(repository.findByCode("PT-01")).thenReturn(Optional.of(existant));
        when(repository.save(any(PointChecklistDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("Nouveau libellé").categorie("PRISE_CONNAISSANCE").ordre(2).actif(false)
                .build();

        PointChecklistDossierTravail result = service.update("PT-01", request);

        assertThat(result.getLibelle()).isEqualTo("Nouveau libellé");
        assertThat(result.getCategorie()).isEqualTo("PRISE_CONNAISSANCE");
        assertThat(result.getOrdre()).isEqualTo(2);
        assertThat(result.getActif()).isFalse();
    }

    @Test
    void update_leveResourceNotFoundExceptionSiCodeInconnu() {
        when(repository.findByCode("PT-99")).thenReturn(Optional.empty());

        PointChecklistDossierTravailRequest request = PointChecklistDossierTravailRequest.builder()
                .libelle("X").ordre(1).build();

        assertThatThrownBy(() -> service.update("PT-99", request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void findAllActifs_delegueAuRepository() {
        List<PointChecklistDossierTravail> actifs = List.of(
                PointChecklistDossierTravail.builder().code("PT-01").ordre(1).build());
        when(repository.findByActifTrueOrderByOrdreAsc()).thenReturn(actifs);

        assertThat(service.findAllActifs()).isEqualTo(actifs);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvnw -Dtest=PointChecklistDossierTravailServiceTest test`
Expected: FAIL (compilation error — `PointChecklistDossierTravailService` n'existe pas)

- [ ] **Step 4: Créer `PointChecklistDossierTravailService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PointChecklistDossierTravailRequest;
import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import gov.bf.ascelc.univers_audits.repository.PointChecklistDossierTravailRepository;
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
public class PointChecklistDossierTravailService {

    private final PointChecklistDossierTravailRepository repository;

    public List<PointChecklistDossierTravail> findAllActifs() {
        return repository.findByActifTrueOrderByOrdreAsc();
    }

    public List<PointChecklistDossierTravail> findAll() {
        return repository.findAllByOrderByOrdreAsc();
    }

    @Transactional
    public PointChecklistDossierTravail create(PointChecklistDossierTravailRequest request) {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .code(nextCode())
                .libelle(request.getLibelle())
                .categorie(request.getCategorie())
                .ordre(request.getOrdre())
                .actif(request.getActif() == null || request.getActif())
                .build();
        PointChecklistDossierTravail saved = repository.save(point);
        log.info("[PointChecklistDossierTravail] '{}' créé", saved.getCode());
        return saved;
    }

    @Transactional
    public PointChecklistDossierTravail update(String code, PointChecklistDossierTravailRequest request) {
        PointChecklistDossierTravail point = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Point de check-list introuvable : " + code));
        point.setLibelle(request.getLibelle());
        point.setCategorie(request.getCategorie());
        point.setOrdre(request.getOrdre());
        if (request.getActif() != null) {
            point.setActif(request.getActif());
        }
        PointChecklistDossierTravail saved = repository.save(point);
        log.info("[PointChecklistDossierTravail] '{}' mis à jour", code);
        return saved;
    }

    private String nextCode() {
        int n = 1;
        String candidate;
        do {
            candidate = String.format("PT-%02d", n++);
        } while (repository.existsByCode(candidate));
        return candidate;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvnw -Dtest=PointChecklistDossierTravailServiceTest test`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PointChecklistDossierTravailRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/PointChecklistDossierTravailService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/PointChecklistDossierTravailServiceTest.java
git commit -m "feat: add PointChecklistDossierTravailService (referentiel admin)"
```

---

### Task 3: État par investigation — DTOs et `ChecklistDossierTravailService`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ChecklistCocheRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/ChecklistDossierTravailItemResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/ChecklistDossierTravailService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/ChecklistDossierTravailServiceTest.java`

**Interfaces:**
- Consumes: `InvestigationRepository.findById(UUID): Optional<Investigation>` (existant), `PointChecklistDossierTravailRepository.findByActifTrueOrderByOrdreAsc()`/`findByCode(String)` (Task 1), `ChecklistDossierTravailCocheRepository.{findByInvestigationId, findByInvestigationIdAndPointId, countByInvestigationIdAndCocheTrue}` (Task 1), `DossierAccessGuard.{checkReadAccess(Dossier), canSeeConfidential()}` (existant, `gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard`), `AgentContextResolver.getCurrentAgent(): Agent` (existant, `gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver`).
- Produces: `ChecklistDossierTravailService.{getChecklist(UUID investigationId): List<ChecklistDossierTravailItemResponse>, setCoche(UUID investigationId, String pointCode, ChecklistCocheRequest): ChecklistDossierTravailItemResponse, isComplete(UUID investigationId): boolean}` — `getChecklist`/`setCoche` consommés par Task 4 (`ChecklistDossierTravailController`), `isComplete` consommé par Task 5 (`InvestigationServiceImpl.submitReport()`).

- [ ] **Step 1: Créer `ChecklistCocheRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistCocheRequest {
    @NotNull(message = "L'état coché/non coché est obligatoire")
    private Boolean coche;
    private String commentaire;
}
```

- [ ] **Step 2: Créer `ChecklistDossierTravailItemResponse`**

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
public class ChecklistDossierTravailItemResponse {
    private UUID pointId;
    private String code;
    private String libelle;
    private String categorie;
    private Integer ordre;
    private boolean coche;
    private String cocheParNom;
    private Instant cocheAt;
    private String commentaire;
}
```

- [ ] **Step 3: Écrire les tests de `ChecklistDossierTravailService` (échouent, la classe n'existe pas encore)**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ChecklistCocheRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ChecklistDossierTravailItemResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.ChecklistDossierTravailCocheRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.PointChecklistDossierTravailRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChecklistDossierTravailServiceTest {

    @Mock private InvestigationRepository investigationRepository;
    @Mock private PointChecklistDossierTravailRepository pointRepository;
    @Mock private ChecklistDossierTravailCocheRepository cocheRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private ChecklistDossierTravailService service;

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
    void getChecklist_fusionneReferentielEtEtatExistant() {
        PointChecklistDossierTravail point1 = PointChecklistDossierTravail.builder()
                .id(UUID.randomUUID()).code("PT-01").libelle("Point 1").ordre(1).build();
        PointChecklistDossierTravail point2 = PointChecklistDossierTravail.builder()
                .id(UUID.randomUUID()).code("PT-02").libelle("Point 2").ordre(2).build();
        ChecklistDossierTravailCoche etatPoint1 = ChecklistDossierTravailCoche.builder()
                .point(point1).coche(true).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(pointRepository.findByActifTrueOrderByOrdreAsc()).thenReturn(List.of(point1, point2));
        when(cocheRepository.findByInvestigationId(investigationId)).thenReturn(List.of(etatPoint1));

        List<ChecklistDossierTravailItemResponse> result = service.getChecklist(investigationId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("PT-01");
        assertThat(result.get(0).isCoche()).isTrue();
        assertThat(result.get(1).getCode()).isEqualTo("PT-02");
        assertThat(result.get(1).isCoche()).isFalse();
    }

    @Test
    void getChecklist_leveSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.getChecklist(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(pointRepository, never()).findByActifTrueOrderByOrdreAsc();
    }

    @Test
    void getChecklist_renvoieListeVideSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<ChecklistDossierTravailItemResponse> result = service.getChecklist(investigationId);

        assertThat(result).isEmpty();
        verify(pointRepository, never()).findByActifTrueOrderByOrdreAsc();
    }

    @Test
    void setCoche_creeUneNouvelleLigneEtRenseigneCocheParEtCocheAt() {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .id(UUID.randomUUID()).code("PT-01").libelle("Point 1").ordre(1).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(pointRepository.findByCode("PT-01")).thenReturn(Optional.of(point));
        when(cocheRepository.findByInvestigationIdAndPointId(investigationId, point.getId()))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(cocheRepository.save(any(ChecklistDossierTravailCoche.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ChecklistCocheRequest request = ChecklistCocheRequest.builder().coche(true).build();
        ChecklistDossierTravailItemResponse result = service.setCoche(investigationId, "PT-01", request);

        assertThat(result.isCoche()).isTrue();
        assertThat(result.getCocheAt()).isNotNull();
    }

    @Test
    void setCoche_decocherRemetCocheParEtCocheAtANull() {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .id(UUID.randomUUID()).code("PT-01").libelle("Point 1").ordre(1).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        ChecklistDossierTravailCoche existant = ChecklistDossierTravailCoche.builder()
                .investigation(investigation).point(point).coche(true)
                .cochePar(agent).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(pointRepository.findByCode("PT-01")).thenReturn(Optional.of(point));
        when(cocheRepository.findByInvestigationIdAndPointId(investigationId, point.getId()))
                .thenReturn(Optional.of(existant));
        when(cocheRepository.save(any(ChecklistDossierTravailCoche.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ChecklistCocheRequest request = ChecklistCocheRequest.builder().coche(false).build();
        ChecklistDossierTravailItemResponse result = service.setCoche(investigationId, "PT-01", request);

        assertThat(result.isCoche()).isFalse();
        assertThat(result.getCocheAt()).isNull();
        assertThat(result.getCocheParNom()).isNull();
        verify(agentContextResolver, never()).getCurrentAgent();
    }

    @Test
    void setCoche_leveResourceNotFoundExceptionSiCodePointInconnu() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(pointRepository.findByCode("PT-99")).thenReturn(Optional.empty());

        ChecklistCocheRequest request = ChecklistCocheRequest.builder().coche(true).build();

        assertThatThrownBy(() -> service.setCoche(investigationId, "PT-99", request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(cocheRepository, never()).save(any());
    }

    @Test
    void isComplete_retourneVraiSiAucunPointActif() {
        when(pointRepository.findByActifTrueOrderByOrdreAsc()).thenReturn(List.of());

        assertThat(service.isComplete(investigationId)).isTrue();
    }

    @Test
    void isComplete_retourneFauxSiUnPointActifNestPasCoche() {
        PointChecklistDossierTravail point1 = PointChecklistDossierTravail.builder().id(UUID.randomUUID()).build();
        PointChecklistDossierTravail point2 = PointChecklistDossierTravail.builder().id(UUID.randomUUID()).build();
        when(pointRepository.findByActifTrueOrderByOrdreAsc()).thenReturn(List.of(point1, point2));
        when(cocheRepository.countByInvestigationIdAndCocheTrue(investigationId)).thenReturn(1L);

        assertThat(service.isComplete(investigationId)).isFalse();
    }

    @Test
    void isComplete_retourneVraiSiTousLesPointsActifsSontCoches() {
        PointChecklistDossierTravail point1 = PointChecklistDossierTravail.builder().id(UUID.randomUUID()).build();
        PointChecklistDossierTravail point2 = PointChecklistDossierTravail.builder().id(UUID.randomUUID()).build();
        when(pointRepository.findByActifTrueOrderByOrdreAsc()).thenReturn(List.of(point1, point2));
        when(cocheRepository.countByInvestigationIdAndCocheTrue(investigationId)).thenReturn(2L);

        assertThat(service.isComplete(investigationId)).isTrue();
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvnw -Dtest=ChecklistDossierTravailServiceTest test`
Expected: FAIL (compilation error — `ChecklistDossierTravailService` n'existe pas)

- [ ] **Step 5: Créer `ChecklistDossierTravailService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ChecklistCocheRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ChecklistDossierTravailItemResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.ChecklistDossierTravailCocheRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.PointChecklistDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistDossierTravailService {

    private final InvestigationRepository investigationRepository;
    private final PointChecklistDossierTravailRepository pointRepository;
    private final ChecklistDossierTravailCocheRepository cocheRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    public List<ChecklistDossierTravailItemResponse> getChecklist(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        List<PointChecklistDossierTravail> points = pointRepository.findByActifTrueOrderByOrdreAsc();
        Map<UUID, ChecklistDossierTravailCoche> etatParPoint = cocheRepository
                .findByInvestigationId(investigationId).stream()
                .collect(Collectors.toMap(c -> c.getPoint().getId(), c -> c));

        return points.stream()
                .map(point -> toItemResponse(point, etatParPoint.get(point.getId())))
                .toList();
    }

    @Transactional
    public ChecklistDossierTravailItemResponse setCoche(
            UUID investigationId, String pointCode, ChecklistCocheRequest request) {

        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        PointChecklistDossierTravail point = pointRepository.findByCode(pointCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Point de check-list introuvable : " + pointCode));

        ChecklistDossierTravailCoche etat = cocheRepository
                .findByInvestigationIdAndPointId(investigationId, point.getId())
                .orElseGet(() -> ChecklistDossierTravailCoche.builder()
                        .investigation(investigation)
                        .point(point)
                        .build());

        etat.setCoche(request.getCoche());
        etat.setCommentaire(request.getCommentaire());
        if (Boolean.TRUE.equals(request.getCoche())) {
            etat.setCochePar(agentContextResolver.getCurrentAgent());
            etat.setCocheAt(Instant.now());
        } else {
            etat.setCochePar(null);
            etat.setCocheAt(null);
        }

        ChecklistDossierTravailCoche saved = cocheRepository.save(etat);
        log.info("Check-list dossier de travail — investigation {}, point {}, coché={}",
                investigationId, pointCode, request.getCoche());
        return toItemResponse(point, saved);
    }

    /** Utilisé par InvestigationServiceImpl.submitReport() — aucun contrôle d'accès ici,
     *  submitReport() a déjà résolu et vérifié l'investigation avant cet appel. */
    public boolean isComplete(UUID investigationId) {
        List<PointChecklistDossierTravail> actifs = pointRepository.findByActifTrueOrderByOrdreAsc();
        if (actifs.isEmpty()) {
            return true;
        }
        long coches = cocheRepository.countByInvestigationIdAndCocheTrue(investigationId);
        return coches >= actifs.size();
    }

    private ChecklistDossierTravailItemResponse toItemResponse(
            PointChecklistDossierTravail point, ChecklistDossierTravailCoche etat) {
        return ChecklistDossierTravailItemResponse.builder()
                .pointId(point.getId())
                .code(point.getCode())
                .libelle(point.getLibelle())
                .categorie(point.getCategorie())
                .ordre(point.getOrdre())
                .coche(etat != null && Boolean.TRUE.equals(etat.getCoche()))
                .cocheParNom(etat != null && etat.getCochePar() != null
                        ? etat.getCochePar().getNomComplet() : null)
                .cocheAt(etat != null ? etat.getCocheAt() : null)
                .commentaire(etat != null ? etat.getCommentaire() : null)
                .build();
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvnw -Dtest=ChecklistDossierTravailServiceTest test`
Expected: PASS (9 tests)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ChecklistCocheRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/ChecklistDossierTravailItemResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/ChecklistDossierTravailService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/ChecklistDossierTravailServiceTest.java
git commit -m "feat: add ChecklistDossierTravailService (etat par investigation)"
```

---

### Task 4: Contrôleurs REST

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/ChecklistDossierTravailController.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/PointChecklistDossierTravailController.java`

**Interfaces:**
- Consumes: `ChecklistDossierTravailService.{getChecklist, setCoche}` (Task 3), `PointChecklistDossierTravailService.{findAllActifs, findAll, create, update}` (Task 2), `ApiUrls.INVESTIGATIONS` (existant, `gov.bf.ascelc.univers_audits.shared.utils.ApiUrls`, valeur `/api/v1/investigations`).
- Produces: endpoints `GET/PUT /api/v1/investigations/{id}/checklist[/{pointCode}]` et `GET/GET admin/POST/PUT /api/v1/points-checklist-dossier-travail[/{code}]` — pas d'autre tâche consommatrice (terminal du chantier côté code), vérifiés par Task 5 uniquement pour la compilation d'ensemble.

Pas de test de contrôleur (confirmé par `find . -iname "*ControllerTest.java"` → 0 résultat dans tout le projet — convention de ce dépôt).

- [ ] **Step 1: Créer `ChecklistDossierTravailController`**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ChecklistCocheRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ChecklistDossierTravailItemResponse;
import gov.bf.ascelc.univers_audits.service.ChecklistDossierTravailService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/checklist")
public class ChecklistDossierTravailController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final ChecklistDossierTravailService checklistDossierTravailService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<ChecklistDossierTravailItemResponse>> getChecklist(
            @PathVariable UUID id) {
        return ResponseEntity.ok(checklistDossierTravailService.getChecklist(id));
    }

    @PutMapping("/{pointCode}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ChecklistDossierTravailItemResponse> setCoche(
            @PathVariable UUID id,
            @PathVariable String pointCode,
            @Valid @RequestBody ChecklistCocheRequest request) {

        log.info("Check-list — investigation {}, point {}", id, pointCode);
        return ResponseEntity.ok(
                checklistDossierTravailService.setCoche(id, pointCode, request));
    }
}
```

- [ ] **Step 2: Créer `PointChecklistDossierTravailController`**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.PointChecklistDossierTravailRequest;
import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import gov.bf.ascelc.univers_audits.service.PointChecklistDossierTravailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/points-checklist-dossier-travail")
@RequiredArgsConstructor
public class PointChecklistDossierTravailController {

    private final PointChecklistDossierTravailService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PointChecklistDossierTravail>> getActifs() {
        return ResponseEntity.ok(service.findAllActifs());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<PointChecklistDossierTravail>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<PointChecklistDossierTravail> create(
            @Valid @RequestBody PointChecklistDossierTravailRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<PointChecklistDossierTravail> update(
            @PathVariable String code,
            @Valid @RequestBody PointChecklistDossierTravailRequest request) {
        return ResponseEntity.ok(service.update(code, request));
    }
}
```

- [ ] **Step 3: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/ChecklistDossierTravailController.java \
        src/main/java/gov/bf/ascelc/univers_audits/controller/PointChecklistDossierTravailController.java
git commit -m "feat: add checklist dossier de travail REST endpoints"
```

---

### Task 5: Blocage `submitReport()` sur la check-list

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `ChecklistDossierTravailService.isComplete(UUID): boolean` (Task 3, `gov.bf.ascelc.univers_audits.service.ChecklistDossierTravailService`).
- Produces: aucune (dernière tâche du chantier).

- [ ] **Step 1: Ajouter le nouveau test `submitReport_rejetteSiChecklistIncomplete` dans `InvestigationServiceImplTest`**

Ajouter l'import en haut du fichier, avec les autres imports `gov.bf.ascelc.univers_audits.service.*` (après `import gov.bf.ascelc.univers_audits.service.SectionDossierTravailService;`) :

```java
import gov.bf.ascelc.univers_audits.service.ChecklistDossierTravailService;
```

Ajouter le mock, dans la liste des `@Mock` existants (après `@Mock private NoteRecommandationsRepository   noteRecommandationsRepository;`) :

```java
    @Mock private ChecklistDossierTravailService checklistDossierTravailService;
```

Ajouter le nouveau test, après `submitReport_rejetteSiAucuneNoteRedigee` (avant `submitReport_succeedsAvecRapportEtNoteComplets`) :

```java
    @Test
    void submitReport_rejetteSiChecklistIncomplete() {
        Investigation investigation = buildInProgressInvestigation();
        RapportEnquete rapportComplet = buildRapportComplet(investigation);
        NoteRecommandations noteComplete = NoteRecommandations.builder()
                .rapportEnquete(rapportComplet)
                .contenu("Recommandation n°1")
                .build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(rapportComplet));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapportComplet.getId()))
                .thenReturn(Optional.of(noteComplete));
        when(checklistDossierTravailService.isComplete(investigation.getId())).thenReturn(false);

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        assertThatThrownBy(() -> service.submitReport(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }
```

Modifier le test existant `submitReport_succeedsAvecRapportEtNoteComplets` pour stuber le nouveau mock — ajouter cette ligne juste après
`when(noteRecommandationsRepository.findByRapportEnqueteId(rapportComplet.getId())).thenReturn(Optional.of(noteComplete));` :

```java
        when(checklistDossierTravailService.isComplete(investigation.getId())).thenReturn(true);
```

- [ ] **Step 2: Run tests to verify the new test fails and the existing success test fails too (mock manquant côté production)**

Run: `mvnw -Dtest=InvestigationServiceImplTest#submitReport_rejetteSiChecklistIncomplete,InvestigationServiceImplTest#submitReport_succeedsAvecRapportEtNoteComplets test`
Expected: `submitReport_rejetteSiChecklistIncomplete` FAIL (aucune exception levée, le gate n'existe pas encore) ; `submitReport_succeedsAvecRapportEtNoteComplets` reste PASS (le mock ajouté est simplement inutilisé pour l'instant) — les deux compilent, ce qui confirme que Step 1 est correct avant de toucher le code de production.

- [ ] **Step 3: Injecter `ChecklistDossierTravailService` dans `InvestigationServiceImpl` et ajouter le gate**

Ajouter l'import, avec les autres imports `gov.bf.ascelc.univers_audits.service.*` (après `import gov.bf.ascelc.univers_audits.service.SectionDossierTravailService;`) :

```java
import gov.bf.ascelc.univers_audits.service.ChecklistDossierTravailService;
```

Ajouter le champ, après `private final NoteRecommandationsRepository   noteRecommandationsRepository;` :

```java
    private final ChecklistDossierTravailService checklistDossierTravailService;
```

Dans `submitReport()`, insérer le nouveau contrôle juste après le bloc existant

```java
        if (!note.isComplet()) {
            throw new BusinessException("La note de recommandations est vide.");
        }
```

et juste avant

```java
        inv.setOutcome(request.getOutcome());
```

ajouter :

```java

        if (!checklistDossierTravailService.isComplete(investigationId)) {
            throw new BusinessException(
                    "La check-list du dossier de travail n'est pas entièrement cochée — "
                            + "tous les points actifs doivent être validés avant soumission.");
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvnw -Dtest=InvestigationServiceImplTest test`
Expected: PASS (toute la classe, y compris les 2 tests de check-list et les tests `submitReport_*` existants)

- [ ] **Step 5: Run the full test suite**

Run: `mvnw -q test`
Expected: BUILD SUCCESS, 0 failure, 0 error

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: block submitReport() until dossier de travail checklist is complete"
```
