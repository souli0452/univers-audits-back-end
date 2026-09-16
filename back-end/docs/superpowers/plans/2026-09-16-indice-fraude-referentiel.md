# Référentiel des indices et typologies de fraude — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Créer le référentiel administrable des indices et typologies de fraude (Lot 9, sous-chantier 3/3, dernier sous-chantier du Lot 9) : entité JPA plate, migration, repository, service, contrôleur REST avec filtre par catégorie.

**Architecture:** Un référentiel autonome, calqué à l'identique sur le patron `TypeInfraction` déjà présent dans le dépôt (entité plate + code unique + libellé + actif/ordre pour l'admin, lecture publique aux authentifiés, écriture réservée aux rôles admin). Aucun rattachement à `Dossier`/`Investigation` — donc aucun `DossierAccessGuard` ni masquage de confidentialité dans ce sous-chantier.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (SQL changelog), Lombok, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-16-indice-fraude-referentiel-design.md`

## Global Constraints

- Aucun rattachement structurel à `Dossier`/`Investigation` — ne jamais ajouter `DossierAccessGuard` ni de vérification de confidentialité dans ce sous-chantier (arbitrage explicite de la spec).
- `categorie` est un champ texte libre (pas d'enum) — un administrateur doit pouvoir saisir n'importe quelle valeur.
- Pas de champ de niveau de risque / score de criticité — hors périmètre explicite.
- Une seule entité plate `IndiceFraude` — pas de hiérarchie `Typologie` parent / `Indice` enfant.
- Migration Liquibase : fichier `042-create-indice-fraude.sql` dans `src/main/resources/db/changelog/migrations/`, chargé automatiquement par `includeAll` — ne pas toucher `db.changelog-master.yaml`.
- `@Transactional(readOnly = true)` au niveau classe du service, `@Transactional` en override sur les méthodes d'écriture (`create`, `update`).
- Route REST : `/api/v1/indices-fraude` (mapping direct dans `@RequestMapping`, pas de constante `ApiUrls` — cohérent avec `TypeInfractionController`, le seul autre référentiel de ce type).

---

### Task 1: Entité `IndiceFraude`, migration et repository

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/IndiceFraude.java`
- Create: `src/main/resources/db/changelog/migrations/042-create-indice-fraude.sql`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/IndiceFraudeRepository.java`

**Interfaces:**
- Produces: entité `IndiceFraude` (champs `code: String`, `libelle: String`, `categorie: String`, `description: String`, `actif: Boolean`, `ordre: Integer`, plus les champs hérités de `AuditEntity` : `id: UUID`, `createdAt`, `updatedAt`, `createdById`, `updatedById`, `version: Long`) ; repository `IndiceFraudeRepository` avec `findByCode(String): Optional<IndiceFraude>`, `existsByCode(String): boolean`, `findByActifTrueOrderByOrdreAsc(): List<IndiceFraude>`, `findByActifTrueAndCategorieOrderByOrdreAsc(String): List<IndiceFraude>`. Ces signatures sont consommées par la Task 2.

- [ ] **Step 1: Créer l'entité `IndiceFraude`**

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
@Table(name = "indice_fraude", indexes = {
        @Index(name = "idx_indice_fraude_code",
                columnList = "code", unique = true)
})
public class IndiceFraude extends AuditEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 300)
    private String libelle;

    @Column(name = "categorie", length = 100)
    private String categorie;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(name = "ordre")
    @Builder.Default
    private Integer ordre = 0;
}
```

- [ ] **Step 2: Créer la migration Liquibase**

```sql
--liquibase formatted sql
--changeset dev:042-create-indice-fraude

CREATE TABLE indice_fraude (
    id             UUID          PRIMARY KEY,
    code           VARCHAR(50)   NOT NULL,
    libelle        VARCHAR(300)  NOT NULL,
    categorie      VARCHAR(100),
    description    TEXT,
    actif          BOOLEAN       NOT NULL DEFAULT TRUE,
    ordre          INTEGER,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP,
    created_by_id  VARCHAR(100),
    updated_by_id  VARCHAR(100)
);

CREATE UNIQUE INDEX idx_indice_fraude_code ON indice_fraude(code);

COMMENT ON TABLE indice_fraude IS 'Referentiel des indices et typologies de fraude, exploitable comme aide a l enquete et comme grille de cartographie des risques par categorie (Lot 9 sous-chantier 3/3)';
```

- [ ] **Step 3: Créer le repository**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IndiceFraudeRepository
        extends JpaRepository<IndiceFraude, UUID> {

    Optional<IndiceFraude> findByCode(String code);

    boolean existsByCode(String code);

    List<IndiceFraude> findByActifTrueOrderByOrdreAsc();

    List<IndiceFraude> findByActifTrueAndCategorieOrderByOrdreAsc(String categorie);
}
```

- [ ] **Step 4: Compiler pour vérifier l'absence d'erreur**

Run: `mvn -q -pl . compile` (depuis `back-end/`)
Expected: `BUILD SUCCESS`, aucune erreur de compilation.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/IndiceFraude.java src/main/resources/db/changelog/migrations/042-create-indice-fraude.sql src/main/java/gov/bf/ascelc/univers_audits/repository/IndiceFraudeRepository.java
git commit -m "feat(indice-fraude): entite, migration et repository du referentiel des indices et typologies"
```

---

### Task 2: DTO, service et tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/IndiceFraudeRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/IndiceFraudeService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/IndiceFraudeServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/IndiceFraudeServiceImplTest.java`

**Interfaces:**
- Consumes: entité `IndiceFraude` et `IndiceFraudeRepository` (Task 1), exceptions existantes `gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException` (constructeur `ConflictException(String message)`) et `gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException` (constructeur `ResourceNotFoundException(String message)`).
- Produces: `IndiceFraudeService` avec `findAllActifs(String categorie): List<IndiceFraude>`, `findAll(): List<IndiceFraude>`, `create(IndiceFraudeRequest): IndiceFraude`, `update(String code, IndiceFraudeRequest): IndiceFraude`. Ces signatures sont consommées par la Task 3.

- [ ] **Step 1: Créer le DTO `IndiceFraudeRequest`**

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
public class IndiceFraudeRequest {

    @NotBlank(message = "Le code est obligatoire")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Le libellé est obligatoire")
    @Size(max = 300)
    private String libelle;

    @Size(max = 100)
    private String categorie;

    private String description;

    @NotNull(message = "L'indicateur actif est obligatoire")
    private Boolean actif;

    private Integer ordre;
}
```

- [ ] **Step 2: Créer l'interface de service**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.IndiceFraudeRequest;
import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;

import java.util.List;

public interface IndiceFraudeService {

    List<IndiceFraude> findAllActifs(String categorie);

    List<IndiceFraude> findAll();

    IndiceFraude create(IndiceFraudeRequest request);

    IndiceFraude update(String code, IndiceFraudeRequest request);
}
```

- [ ] **Step 3: Écrire les tests (ils échoueront tant que l'implémentation n'existe pas)**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.IndiceFraudeRequest;
import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;
import gov.bf.ascelc.univers_audits.repository.IndiceFraudeRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
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
class IndiceFraudeServiceImplTest {

    @Mock
    private IndiceFraudeRepository repository;

    @InjectMocks
    private IndiceFraudeServiceImpl service;

    @Test
    void findAllActifs_sansCategorie_delegueAuxActifsOrdonnes() {
        IndiceFraude indice = IndiceFraude.builder().code("MP_SURFACTURATION").build();
        when(repository.findByActifTrueOrderByOrdreAsc())
                .thenReturn(List.of(indice));

        List<IndiceFraude> result = service.findAllActifs(null);

        assertThat(result).containsExactly(indice);
        verify(repository).findByActifTrueOrderByOrdreAsc();
        verify(repository, never()).findByActifTrueAndCategorieOrderByOrdreAsc(any());
    }

    @Test
    void findAllActifs_avecCategorie_delegueAuFiltreParCategorie() {
        IndiceFraude indice = IndiceFraude.builder()
                .code("MP_SURFACTURATION")
                .categorie("Marchés publics")
                .build();
        when(repository.findByActifTrueAndCategorieOrderByOrdreAsc("Marchés publics"))
                .thenReturn(List.of(indice));

        List<IndiceFraude> result = service.findAllActifs("Marchés publics");

        assertThat(result).containsExactly(indice);
        verify(repository).findByActifTrueAndCategorieOrderByOrdreAsc("Marchés publics");
        verify(repository, never()).findByActifTrueOrderByOrdreAsc();
    }

    @Test
    void create_savesNewIndice() {
        when(repository.existsByCode("MP_SURFACTURATION")).thenReturn(false);
        when(repository.save(any(IndiceFraude.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        IndiceFraudeRequest request = IndiceFraudeRequest.builder()
                .code("MP_SURFACTURATION")
                .libelle("Surfacturation sur marché public")
                .categorie("Marchés publics")
                .description("Ecart significatif entre prix facturé et prix de marché observé")
                .actif(true)
                .ordre(1)
                .build();

        IndiceFraude result = service.create(request);

        assertThat(result.getCode()).isEqualTo("MP_SURFACTURATION");
        assertThat(result.getCategorie()).isEqualTo("Marchés publics");
        verify(repository).save(any(IndiceFraude.class));
    }

    @Test
    void create_throwsConflictWhenCodeAlreadyExists() {
        when(repository.existsByCode("MP_SURFACTURATION")).thenReturn(true);

        IndiceFraudeRequest request = IndiceFraudeRequest.builder()
                .code("MP_SURFACTURATION")
                .libelle("Surfacturation sur marché public")
                .actif(true)
                .build();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_updatesExistingIndice() {
        IndiceFraude existing = IndiceFraude.builder()
                .code("MP_SURFACTURATION")
                .libelle("Ancien libellé")
                .actif(true)
                .ordre(1)
                .build();
        when(repository.findByCode("MP_SURFACTURATION")).thenReturn(Optional.of(existing));
        when(repository.save(any(IndiceFraude.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        IndiceFraudeRequest request = IndiceFraudeRequest.builder()
                .code("MP_SURFACTURATION")
                .libelle("Nouveau libellé")
                .categorie("Marchés publics")
                .description("Nouvelle description")
                .actif(false)
                .ordre(2)
                .build();

        IndiceFraude result = service.update("MP_SURFACTURATION", request);

        assertThat(result.getLibelle()).isEqualTo("Nouveau libellé");
        assertThat(result.getCategorie()).isEqualTo("Marchés publics");
        assertThat(result.getActif()).isFalse();
        assertThat(result.getOrdre()).isEqualTo(2);
        verify(repository).save(existing);
    }

    @Test
    void update_throwsWhenCodeUnknown() {
        when(repository.findByCode("INCONNU")).thenReturn(Optional.empty());

        IndiceFraudeRequest request = IndiceFraudeRequest.builder()
                .code("INCONNU")
                .libelle("Libellé")
                .actif(true)
                .build();

        assertThatThrownBy(() -> service.update("INCONNU", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils échouent (classe `IndiceFraudeServiceImpl` inexistante)**

Run: `mvn -q -Dtest=IndiceFraudeServiceImplTest test` (depuis `back-end/`)
Expected: `COMPILATION ERROR` — `IndiceFraudeServiceImpl` n'existe pas encore.

- [ ] **Step 5: Créer l'implémentation du service**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.IndiceFraudeRequest;
import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;
import gov.bf.ascelc.univers_audits.repository.IndiceFraudeRepository;
import gov.bf.ascelc.univers_audits.service.IndiceFraudeService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndiceFraudeServiceImpl implements IndiceFraudeService {

    private final IndiceFraudeRepository repository;

    @Override
    public List<IndiceFraude> findAllActifs(String categorie) {
        if (StringUtils.hasText(categorie)) {
            return repository.findByActifTrueAndCategorieOrderByOrdreAsc(categorie);
        }
        return repository.findByActifTrueOrderByOrdreAsc();
    }

    @Override
    public List<IndiceFraude> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public IndiceFraude create(IndiceFraudeRequest request) {
        if (repository.existsByCode(request.getCode())) {
            throw new ConflictException(
                    "Un indice de fraude avec ce code existe déjà : " + request.getCode());
        }

        IndiceFraude indice = IndiceFraude.builder()
                .code(request.getCode())
                .libelle(request.getLibelle())
                .categorie(request.getCategorie())
                .description(request.getDescription())
                .actif(request.getActif())
                .ordre(request.getOrdre() != null ? request.getOrdre() : 0)
                .build();

        IndiceFraude saved = repository.save(indice);
        log.info("[IndiceFraude] '{}' créé", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public IndiceFraude update(String code, IndiceFraudeRequest request) {
        IndiceFraude indice = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Indice de fraude introuvable : " + code));

        indice.setLibelle(request.getLibelle());
        indice.setCategorie(request.getCategorie());
        indice.setDescription(request.getDescription());
        indice.setActif(request.getActif());
        indice.setOrdre(request.getOrdre() != null ? request.getOrdre() : 0);

        IndiceFraude saved = repository.save(indice);
        log.info("[IndiceFraude] '{}' mis à jour", code);
        return saved;
    }
}
```

- [ ] **Step 6: Lancer les tests pour vérifier qu'ils passent**

Run: `mvn -q -Dtest=IndiceFraudeServiceImplTest test` (depuis `back-end/`)
Expected: `BUILD SUCCESS`, 6 tests passés.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/IndiceFraudeRequest.java src/main/java/gov/bf/ascelc/univers_audits/service/IndiceFraudeService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/IndiceFraudeServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/IndiceFraudeServiceImplTest.java
git commit -m "feat(indice-fraude): service du referentiel avec filtre par categorie"
```

---

### Task 3: Contrôleur REST

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/IndiceFraudeController.java`

**Interfaces:**
- Consumes: `IndiceFraudeService` (Task 2) — `findAllActifs(String)`, `findAll()`, `create(IndiceFraudeRequest)`, `update(String, IndiceFraudeRequest)` ; entité `IndiceFraude` (Task 1) retournée directement (pas de DTO de réponse séparé, cohérent avec `TypeInfractionController`).

- [ ] **Step 1: Créer le contrôleur**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.IndiceFraudeRequest;
import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;
import gov.bf.ascelc.univers_audits.service.IndiceFraudeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/indices-fraude")
@RequiredArgsConstructor
public class IndiceFraudeController {

    private final IndiceFraudeService indiceFraudeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<IndiceFraude>> getActifs(
            @RequestParam(required = false) String categorie) {
        return ResponseEntity.ok(indiceFraudeService.findAllActifs(categorie));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<IndiceFraude>> getAll() {
        return ResponseEntity.ok(indiceFraudeService.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<IndiceFraude> create(
            @Valid @RequestBody IndiceFraudeRequest request) {
        return ResponseEntity.ok(indiceFraudeService.create(request));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<IndiceFraude> update(
            @PathVariable String code,
            @Valid @RequestBody IndiceFraudeRequest request) {
        return ResponseEntity.ok(indiceFraudeService.update(code, request));
    }
}
```

- [ ] **Step 2: Compiler l'ensemble du module pour vérifier l'intégration**

Run: `mvn -q compile` (depuis `back-end/`)
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Lancer la suite de tests complète**

Run: `mvn -q test` (depuis `back-end/`)
Expected: tous les tests passent hormis l'échec pré-existant connu et sans rapport (`UniversAuditsApplicationTests.contextLoads`, environnement DB/Redis inaccessible dans ce shell).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/IndiceFraudeController.java
git commit -m "feat(indice-fraude): controleur REST du referentiel des indices et typologies"
```
