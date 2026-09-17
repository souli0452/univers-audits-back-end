# Calendrier des jours fériés + calculateur de délais ouvrables Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer le calcul de délai purement calendaire par un calcul en jours ouvrables réels (samedis/dimanches/jours fériés exclus) partout où `ParametreDelai.joursOuvrables = true`, sans rien changer pour les délais marqués `false`.

**Architecture:** Un nouveau référentiel administrable `JourFerie` (dates fixes, saisies année par année) alimente un composant partagé `DeadlineCalculator` (`shared/utils`), qui remplace le calcul `plusSeconds` dupliqué dans 4 services existants (5 sites d'appel au total).

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase, Lombok, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-17-jours-ouvrables-calendrier-design.md`

## Global Constraints

- `JourFerie` : référentiel administrable (`extends AuditEntity`), champs `date`/`libelle`/`actif` uniquement — pas de récurrence, une ligne par date exacte.
- Week-end fixe (samedi + dimanche), non paramétrable.
- `DeadlineCalculator.addBusinessDays`/`addCalendarDays` sont les deux seules méthodes publiques — pas de logique métier au-delà du calcul de date.
- Aucun des 5 sites d'appel refactorés ne doit changer de comportement pour `MISSION_SUIVI_PLAN_ACTIONS` (`joursOuvrables = false`) — vérifié par test de non-régression.
- Les tests existants des 4 services refactorés (`PlanActionsServiceTest`, `TransmissionAutoriteServiceTest`, `MissionSuiviServiceTest`, `InvestigationServiceImplTest`) stubbent déjà `parametreDelaiService.resolveDelaiJours(...)` sans stubber un éventuel `resolveJoursOuvrables(...)` — Mockito renvoie `false` par défaut pour une méthode `boolean` non stubbée, ce qui route vers `addCalendarDays` (comportement identique à aujourd'hui). **Aucun test existant ne doit donc être modifié pour ce chantier** — seuls de nouveaux tests ciblés sont ajoutés.
- Migration suivante : `009-create-jour-ferie.sql` (dernier fichier existant : `008-seed-indice-fraude.sql`).

---

### Task 1: Entité `JourFerie`, migration et repository

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/JourFerie.java`
- Create: `src/main/resources/db/changelog/migrations/009-create-jour-ferie.sql`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/JourFerieRepository.java`

**Interfaces:**
- Produces: entité `JourFerie` (champs `date: LocalDate`, `libelle: String`, `actif: Boolean`, plus les champs hérités de `AuditEntity` : `id: UUID`, `createdAt`, `updatedAt`, `createdById`, `updatedById`, `version: Long`) ; repository `JourFerieRepository` avec `existsByDateAndActifTrue(LocalDate): boolean`, `existsByDate(LocalDate): boolean`, `findByActifTrueOrderByDateAsc(): List<JourFerie>`, `findAll(): List<JourFerie>` (héritée de `JpaRepository`). Ces signatures sont consommées par les Tasks 2 et 3.

- [ ] **Step 1: Créer l'entité `JourFerie`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "jour_ferie", indexes = {
        @Index(name = "idx_jour_ferie_date",
                columnList = "date", unique = true)
})
public class JourFerie extends AuditEntity {

    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "libelle", nullable = false, length = 300)
    private String libelle;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;
}
```

- [ ] **Step 2: Créer la migration Liquibase**

```sql
--liquibase formatted sql
--changeset dev:009-create-jour-ferie

CREATE TABLE jour_ferie (
    id             UUID          PRIMARY KEY,
    date           DATE          NOT NULL,
    libelle        VARCHAR(300)  NOT NULL,
    actif          BOOLEAN       NOT NULL DEFAULT TRUE,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP,
    created_by_id  VARCHAR(100),
    updated_by_id  VARCHAR(100)
);

CREATE UNIQUE INDEX idx_jour_ferie_date ON jour_ferie(date);

COMMENT ON TABLE jour_ferie IS 'Referentiel des jours feries burkinabe, dates fixes saisies annee par annee (pas de recurrence) - consomme par DeadlineCalculator pour le calcul des delais en jours ouvrables (chantier transversal "jours ouvrables reels", sous-chantier 1/4)';
```

- [ ] **Step 3: Créer le repository**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.JourFerie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface JourFerieRepository
        extends JpaRepository<JourFerie, UUID> {

    boolean existsByDateAndActifTrue(LocalDate date);

    boolean existsByDate(LocalDate date);

    List<JourFerie> findByActifTrueOrderByDateAsc();
}
```

- [ ] **Step 4: Compiler pour vérifier l'absence d'erreur**

Run: `mvn -q compile` (depuis `back-end/`)
Expected: `BUILD SUCCESS`, aucune erreur de compilation.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/JourFerie.java src/main/resources/db/changelog/migrations/009-create-jour-ferie.sql src/main/java/gov/bf/ascelc/univers_audits/repository/JourFerieRepository.java
git commit -m "feat(jours-ouvrables): entite, migration et repository du referentiel des jours feries"
```

---

### Task 2: DTO, service et contrôleur du référentiel `JourFerie`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/JourFerieRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/JourFerieService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/JourFerieServiceImpl.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/JourFerieController.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/JourFerieServiceImplTest.java`

**Interfaces:**
- Consumes: entité `JourFerie` et `JourFerieRepository` (Task 1), exceptions existantes `gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException` et `gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException` (constructeur `String message`).
- Produces: `JourFerieService` avec `findAllActifs(): List<JourFerie>`, `findAll(): List<JourFerie>`, `create(JourFerieRequest): JourFerie`, `update(UUID id, JourFerieRequest): JourFerie`. Non consommé par les tâches suivantes (Task 3 utilise directement `JourFerieRepository`, pas ce service) — livré pour l'administration du référentiel uniquement.

- [ ] **Step 1: Créer le DTO `JourFerieRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourFerieRequest {

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    @NotBlank(message = "Le libellé est obligatoire")
    private String libelle;

    @NotNull(message = "L'indicateur actif est obligatoire")
    private Boolean actif;
}
```

- [ ] **Step 2: Créer l'interface de service**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.JourFerieRequest;
import gov.bf.ascelc.univers_audits.model.entity.JourFerie;

import java.util.List;
import java.util.UUID;

public interface JourFerieService {

    List<JourFerie> findAllActifs();

    List<JourFerie> findAll();

    JourFerie create(JourFerieRequest request);

    JourFerie update(UUID id, JourFerieRequest request);
}
```

- [ ] **Step 3: Écrire les tests (ils échoueront tant que l'implémentation n'existe pas)**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.JourFerieRequest;
import gov.bf.ascelc.univers_audits.model.entity.JourFerie;
import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JourFerieServiceImplTest {

    @Mock
    private JourFerieRepository repository;

    @InjectMocks
    private JourFerieServiceImpl service;

    @Test
    void create_savesNewJourFerie() {
        when(repository.existsByDate(LocalDate.of(2027, 1, 1))).thenReturn(false);
        when(repository.save(any(JourFerie.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourFerieRequest request = JourFerieRequest.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Jour de l'An")
                .actif(true)
                .build();

        JourFerie result = service.create(request);

        assertThat(result.getDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(result.getLibelle()).isEqualTo("Jour de l'An");
        verify(repository).save(any(JourFerie.class));
    }

    @Test
    void create_throwsConflictWhenDateAlreadyExists() {
        when(repository.existsByDate(LocalDate.of(2027, 1, 1))).thenReturn(true);

        JourFerieRequest request = JourFerieRequest.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Jour de l'An")
                .actif(true)
                .build();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_updatesExistingJourFerie() {
        UUID id = UUID.randomUUID();
        JourFerie existing = JourFerie.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Ancien libellé")
                .actif(true)
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(JourFerie.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JourFerieRequest request = JourFerieRequest.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Nouveau libellé")
                .actif(false)
                .build();

        JourFerie result = service.update(id, request);

        assertThat(result.getLibelle()).isEqualTo("Nouveau libellé");
        assertThat(result.getActif()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void update_throwsWhenIdUnknown() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        JourFerieRequest request = JourFerieRequest.builder()
                .date(LocalDate.of(2027, 1, 1))
                .libelle("Libellé")
                .actif(true)
                .build();

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils échouent**

Run: `mvn -q -Dtest=JourFerieServiceImplTest test` (depuis `back-end/`)
Expected: `COMPILATION ERROR` — `JourFerieServiceImpl` n'existe pas encore.

- [ ] **Step 5: Créer l'implémentation du service**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.JourFerieRequest;
import gov.bf.ascelc.univers_audits.model.entity.JourFerie;
import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import gov.bf.ascelc.univers_audits.service.JourFerieService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JourFerieServiceImpl implements JourFerieService {

    private final JourFerieRepository repository;

    @Override
    public List<JourFerie> findAllActifs() {
        return repository.findByActifTrueOrderByDateAsc();
    }

    @Override
    public List<JourFerie> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public JourFerie create(JourFerieRequest request) {
        if (repository.existsByDate(request.getDate())) {
            throw new ConflictException(
                    "Un jour férié existe déjà pour cette date : " + request.getDate());
        }

        JourFerie jourFerie = JourFerie.builder()
                .date(request.getDate())
                .libelle(request.getLibelle())
                .actif(request.getActif())
                .build();

        JourFerie saved = repository.save(jourFerie);
        log.info("[JourFerie] '{}' créé", saved.getDate());
        return saved;
    }

    @Override
    @Transactional
    public JourFerie update(UUID id, JourFerieRequest request) {
        JourFerie jourFerie = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Jour férié introuvable : " + id));

        jourFerie.setDate(request.getDate());
        jourFerie.setLibelle(request.getLibelle());
        jourFerie.setActif(request.getActif());

        JourFerie saved = repository.save(jourFerie);
        log.info("[JourFerie] '{}' mis à jour", id);
        return saved;
    }
}
```

- [ ] **Step 6: Lancer les tests pour vérifier qu'ils passent**

Run: `mvn -q -Dtest=JourFerieServiceImplTest test` (depuis `back-end/`)
Expected: `BUILD SUCCESS`, 4 tests passés.

- [ ] **Step 7: Créer le contrôleur**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.JourFerieRequest;
import gov.bf.ascelc.univers_audits.model.entity.JourFerie;
import gov.bf.ascelc.univers_audits.service.JourFerieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jours-feries")
@RequiredArgsConstructor
public class JourFerieController {

    private final JourFerieService jourFerieService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<JourFerie>> getActifs() {
        return ResponseEntity.ok(jourFerieService.findAllActifs());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<JourFerie>> getAll() {
        return ResponseEntity.ok(jourFerieService.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<JourFerie> create(
            @Valid @RequestBody JourFerieRequest request) {
        return ResponseEntity.ok(jourFerieService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<JourFerie> update(
            @PathVariable UUID id,
            @Valid @RequestBody JourFerieRequest request) {
        return ResponseEntity.ok(jourFerieService.update(id, request));
    }
}
```

- [ ] **Step 8: Compiler l'ensemble du module**

Run: `mvn -q compile` (depuis `back-end/`)
Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/JourFerieRequest.java src/main/java/gov/bf/ascelc/univers_audits/service/JourFerieService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/JourFerieServiceImpl.java src/main/java/gov/bf/ascelc/univers_audits/controller/JourFerieController.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/JourFerieServiceImplTest.java
git commit -m "feat(jours-ouvrables): referentiel administrable des jours feries (CRUD)"
```

---

### Task 3: `DeadlineCalculator` partagé et `ParametreDelaiService.resolveJoursOuvrables`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/DeadlineCalculator.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/shared/utils/DeadlineCalculatorTest.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/ParametreDelaiService.java` (ajout d'une méthode à l'interface)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImpl.java` (implémentation)
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImplTest.java` (si le fichier n'existe pas déjà, le créer ; s'il existe, y ajouter les 2 tests ci-dessous)

**Interfaces:**
- Consumes: `JourFerieRepository.existsByDateAndActifTrue(LocalDate)` (Task 1) ; `ParametreDelaiRepository.findByCode(String): Optional<ParametreDelai>` (déjà existant).
- Produces: `DeadlineCalculator` avec `addBusinessDays(Instant from, int joursOuvrables): Instant` et `addCalendarDays(Instant from, int jours): Instant` ; `ParametreDelaiService.resolveJoursOuvrables(String code): boolean`. Ces deux signatures sont consommées par la Task 4 et la Task 5.

- [ ] **Step 1: Écrire les tests du `DeadlineCalculator` (ils échoueront tant que la classe n'existe pas)**

```java
package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadlineCalculatorTest {

    @Mock
    private JourFerieRepository jourFerieRepository;

    @InjectMocks
    private DeadlineCalculator calculator;

    @Test
    void addBusinessDays_skipsWeekend() {
        // Vendredi 2027-01-01 (verifie : 2027-01-01 est un vendredi)
        Instant vendredi = LocalDate.of(2027, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Instant result = calculator.addBusinessDays(vendredi, 1);

        // +1 jour ouvrable depuis vendredi doit sauter samedi/dimanche -> lundi 2027-01-04
        assertThat(result).isEqualTo(
                LocalDate.of(2027, 1, 4).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void addBusinessDays_skipsActiveJourFerie() {
        // Lundi 2027-01-04, jour ferie actif le mardi 2027-01-05
        Instant lundi = LocalDate.of(2027, 1, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(jourFerieRepository.existsByDateAndActifTrue(LocalDate.of(2027, 1, 5)))
                .thenReturn(true);
        when(jourFerieRepository.existsByDateAndActifTrue(LocalDate.of(2027, 1, 6)))
                .thenReturn(false);

        Instant result = calculator.addBusinessDays(lundi, 1);

        // +1 jour ouvrable depuis lundi doit sauter le mardi ferie -> mercredi 2027-01-06
        assertThat(result).isEqualTo(
                LocalDate.of(2027, 1, 6).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void addBusinessDays_ignoresInactiveJourFerie() {
        // Lundi 2027-01-04, jour ferie INACTIF le mardi -> compte comme jour ouvrable normal
        Instant lundi = LocalDate.of(2027, 1, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(jourFerieRepository.existsByDateAndActifTrue(LocalDate.of(2027, 1, 5)))
                .thenReturn(false);

        Instant result = calculator.addBusinessDays(lundi, 1);

        assertThat(result).isEqualTo(
                LocalDate.of(2027, 1, 5).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void addBusinessDays_zeroDays_returnsFromUnchanged() {
        Instant lundi = LocalDate.of(2027, 1, 4).atStartOfDay(ZoneOffset.UTC).toInstant();

        Instant result = calculator.addBusinessDays(lundi, 0);

        assertThat(result).isEqualTo(lundi);
    }

    @Test
    void addCalendarDays_simpleDelegation() {
        Instant from = Instant.parse("2027-01-01T10:00:00Z");

        Instant result = calculator.addCalendarDays(from, 5);

        assertThat(result).isEqualTo(from.plusSeconds(5L * 24 * 3600));
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

Run: `mvn -q -Dtest=DeadlineCalculatorTest test` (depuis `back-end/`)
Expected: `COMPILATION ERROR` — `DeadlineCalculator` n'existe pas encore.

- [ ] **Step 3: Créer `DeadlineCalculator`**

```java
package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class DeadlineCalculator {

    private final JourFerieRepository jourFerieRepository;

    /**
     * Avance depuis {@code from} jusqu'à avoir franchi {@code joursOuvrables} jours
     * ouvrables (ni samedi, ni dimanche, ni jour férié actif).
     */
    public Instant addBusinessDays(Instant from, int joursOuvrables) {
        LocalDate current = from.atZone(ZoneOffset.UTC).toLocalDate();
        int remaining = joursOuvrables;

        while (remaining > 0) {
            current = current.plusDays(1);
            if (isBusinessDay(current)) {
                remaining--;
            }
        }

        return current.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Calcul calendaire simple, sans exclusion de jours — centralise le calcul déjà
     * utilisé partout avant ce chantier, pour que les appelants n'aient qu'une seule
     * dépendance (ce composant) au lieu de dupliquer {@code plusSeconds(...)}.
     */
    public Instant addCalendarDays(Instant from, int jours) {
        return from.plusSeconds((long) jours * 24 * 3600);
    }

    private boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !jourFerieRepository.existsByDateAndActifTrue(date);
    }
}
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

Run: `mvn -q -Dtest=DeadlineCalculatorTest test` (depuis `back-end/`)
Expected: `BUILD SUCCESS`, 5 tests passés.

- [ ] **Step 5: Ajouter `resolveJoursOuvrables` à l'interface `ParametreDelaiService`**

Modifier `src/main/java/gov/bf/ascelc/univers_audits/service/ParametreDelaiService.java` pour ajouter cette méthode à l'interface (à côté de `resolveDelaiJours`) :

```java
    boolean resolveJoursOuvrables(String code);
```

Le fichier complet après modification :

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ParametreDelaiRequest;
import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;

import java.util.List;

public interface ParametreDelaiService {

    int resolveDelaiJours(String code);

    boolean resolveJoursOuvrables(String code);

    List<ParametreDelai> findAllActifs();

    List<ParametreDelai> findAll();

    ParametreDelai update(String code, ParametreDelaiRequest request);
}
```

- [ ] **Step 6: Écrire les tests de `resolveJoursOuvrables` (échoueront tant que l'implémentation n'existe pas)**

Si `src/test/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImplTest.java` existe déjà, y ajouter ces 2 méthodes de test (garder le reste du fichier inchangé). S'il n'existe pas, le créer avec ce contenu minimal :

```java
package gov.bf.ascelc.univers_audits.service.impl;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParametreDelaiServiceImplTest {

    @Mock
    private ParametreDelaiRepository repository;

    @InjectMocks
    private ParametreDelaiServiceImpl service;

    @Test
    void resolveJoursOuvrables_returnsTrueFlag() {
        ParametreDelai delai = ParametreDelai.builder()
                .code("APPROBATION_CGE")
                .valeurJours(20)
                .joursOuvrables(true)
                .actif(true)
                .build();
        when(repository.findByCode("APPROBATION_CGE")).thenReturn(Optional.of(delai));

        assertThat(service.resolveJoursOuvrables("APPROBATION_CGE")).isTrue();
    }

    @Test
    void resolveJoursOuvrables_returnsFalseFlag() {
        ParametreDelai delai = ParametreDelai.builder()
                .code("MISSION_SUIVI_PLAN_ACTIONS")
                .valeurJours(365)
                .joursOuvrables(false)
                .actif(true)
                .build();
        when(repository.findByCode("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(Optional.of(delai));

        assertThat(service.resolveJoursOuvrables("MISSION_SUIVI_PLAN_ACTIONS")).isFalse();
    }

    @Test
    void resolveJoursOuvrables_throwsWhenCodeUnknown() {
        when(repository.findByCode("INCONNU")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveJoursOuvrables("INCONNU"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 7: Implémenter `resolveJoursOuvrables` dans `ParametreDelaiServiceImpl`**

Ajouter cette méthode dans `src/main/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImpl.java`, juste après `resolveDelaiJours` :

```java
    @Override
    public boolean resolveJoursOuvrables(String code) {
        ParametreDelai delai = repository.findByCode(code)
                .filter(ParametreDelai::getActif)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paramètre de délai introuvable ou inactif : " + code));
        return Boolean.TRUE.equals(delai.getJoursOuvrables());
    }
```

- [ ] **Step 8: Lancer les tests pour vérifier qu'ils passent**

Run: `mvn -q -Dtest=DeadlineCalculatorTest,ParametreDelaiServiceImplTest test` (depuis `back-end/`)
Expected: `BUILD SUCCESS`, 8 tests passés (5 + 3).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/utils/DeadlineCalculator.java src/test/java/gov/bf/ascelc/univers_audits/shared/utils/DeadlineCalculatorTest.java src/main/java/gov/bf/ascelc/univers_audits/service/ParametreDelaiService.java src/main/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/ParametreDelaiServiceImplTest.java
git commit -m "feat(jours-ouvrables): calculateur de delais partage (DeadlineCalculator) et resolveJoursOuvrables"
```

---

### Task 4: Refactor de `PlanActionsService`, `TransmissionAutoriteService`, `MissionSuiviService`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/PlanActionsService.java:154-162` (méthode `resolveDeadline`)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/TransmissionAutoriteService.java:139-147` (méthode `resolveDeadline`)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/MissionSuiviService.java:115-123` (méthode `resolveDeadline`)
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/PlanActionsServiceTest.java` (ajout d'un test)
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/TransmissionAutoriteServiceTest.java` (ajout d'un test)
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/MissionSuiviServiceTest.java` (ajout d'un test)

**Interfaces:**
- Consumes: `DeadlineCalculator.addBusinessDays(Instant, int)`/`addCalendarDays(Instant, int)` et `ParametreDelaiService.resolveJoursOuvrables(String)` (Task 3).

Ce changement est mécanique et identique dans les 3 services — traité en un seul lot.

- [ ] **Step 1: Ajouter le champ `DeadlineCalculator` et modifier `resolveDeadline` dans `PlanActionsService`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/service/PlanActionsService.java` :
1. Ajouter l'import : `import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;`
2. Ajouter le champ après `private final ParametreDelaiService parametreDelaiService;` (ligne 34) :
   ```java
       private final DeadlineCalculator deadlineCalculator;
   ```
3. Remplacer la méthode `resolveDeadline` (lignes 154-162) par :
   ```java
       private Instant resolveDeadline(Instant from, String delaiCode) {
           try {
               int delaiJours = parametreDelaiService.resolveDelaiJours(delaiCode);
               boolean joursOuvrables = parametreDelaiService.resolveJoursOuvrables(delaiCode);
               return joursOuvrables
                       ? deadlineCalculator.addBusinessDays(from, delaiJours)
                       : deadlineCalculator.addCalendarDays(from, delaiJours);
           } catch (ResourceNotFoundException e) {
               log.warn("Délai {} indisponible — échéance non calculée : {}", delaiCode, e.getMessage());
               return null;
           }
       }
   ```

- [ ] **Step 2: Répéter à l'identique dans `TransmissionAutoriteService`**

Même 3 modifications (import, champ après `parametreDelaiService` ligne 32, remplacement de `resolveDeadline` lignes 139-147) avec le même corps de méthode.

- [ ] **Step 3: Répéter à l'identique dans `MissionSuiviService`**

Même 3 modifications (import, champ après `parametreDelaiService` ligne 35, remplacement de `resolveDeadline` lignes 115-123) avec le même corps de méthode.

- [ ] **Step 4: Ajouter un test ciblé dans `PlanActionsServiceTest`**

Ce fichier a déjà un `@BeforeEach setUp()` qui initialise les champs d'instance `investigation`/`investigationId`/`dossier` (voir `PlanActionsServiceTest.java:38-51` : `dossier = Dossier.builder().id(UUID.randomUUID()).build()`, `investigation = Investigation.builder().id(investigationId).dossier(dossier).cgeApprovedAt(Instant.now()).reportSubmittedAt(Instant.now().minusSeconds(5L*24*3600)).build()`). Réutiliser ces champs, ne pas en recréer.

Ajouter `@Mock private DeadlineCalculator deadlineCalculator;` à la liste des `@Mock` existante (ligne 34, à côté de `parametreDelaiService`), et l'import `gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator`. Puis ajouter cette méthode de test, sur le modèle exact de `getStatus_existsFauxSiAucunPlan` déjà présente dans ce fichier :

```java
    @Test
    void getStatus_delegatesToBusinessDaysWhenJoursOuvrablesTrue() {
        // Ce test verifie uniquement le routage de resolveDeadline vers le bon calcul,
        // pas le calcul lui-meme (deja couvert par DeadlineCalculatorTest).
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);
        when(parametreDelaiService.resolveJoursOuvrables("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(true);
        when(deadlineCalculator.addBusinessDays(any(), eq(20)))
                .thenReturn(Instant.parse("2027-02-01T00:00:00Z"));

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.getPlanActionsDueAt()).isEqualTo(Instant.parse("2027-02-01T00:00:00Z"));
        verify(deadlineCalculator).addBusinessDays(any(), eq(20));
        verify(deadlineCalculator, never()).addCalendarDays(any(), anyInt());
    }
```

Ajouter les imports statiques `org.mockito.ArgumentMatchers.eq` et `org.mockito.ArgumentMatchers.anyInt` si absents (le fichier importe déjà `static org.mockito.Mockito.*`, qui ne couvre pas `ArgumentMatchers.eq`/`anyInt` sous tous les IDE/configurations — ajouter les imports explicites pour être sûr).

- [ ] **Step 5: Ajouter un test équivalent dans `TransmissionAutoriteServiceTest` et `MissionSuiviServiceTest`**

Même principe, réutilisant les champs d'instance déjà initialisés par le `@BeforeEach` de chacun de ces 2 fichiers (lire leur `setUp()` avant d'écrire le test, pour reprendre leurs champs exacts plutôt que d'en recréer — même discipline qu'à l'étape précédente). Un test qui stubbe `resolveJoursOuvrables(...)` à `true`, stubbe `deadlineCalculator.addBusinessDays(...)`, et vérifie que `addBusinessDays` est appelé (pas `addCalendarDays`). Utiliser le code de délai propre à chaque service (`RELANCE_SUITES_TRANSMISSION` pour `TransmissionAutoriteService`, `MISSION_SUIVI_PLAN_ACTIONS` pour `MissionSuiviService`).

**Second test pour `MissionSuiviServiceTest` uniquement** : `MISSION_SUIVI_PLAN_ACTIONS` a `joursOuvrables = false` en donnée réelle (seul des 3 codes de ce lot dans ce cas) — ajouter un test stubbant `resolveJoursOuvrables("MISSION_SUIVI_PLAN_ACTIONS")` à `false`, vérifiant que `deadlineCalculator.addCalendarDays(...)` est appelé et `addBusinessDays` jamais, pour couvrir explicitement la non-régression sur ce délai précis (calendaire, "dans l'année") plutôt que de la déduire seulement du test générique.

- [ ] **Step 6: Ajouter le mock `DeadlineCalculator` dans les 3 fichiers de test**

Dans chacun des 3 fichiers de test, ajouter `@Mock private DeadlineCalculator deadlineCalculator;` à côté des autres `@Mock` existants (nécessaire pour que `@InjectMocks` construise le service avec ce nouveau champ).

- [ ] **Step 7: Lancer la suite complète des 3 fichiers de test**

Run: `mvn -q -Dtest=PlanActionsServiceTest,TransmissionAutoriteServiceTest,MissionSuiviServiceTest test` (depuis `back-end/`)
Expected: `BUILD SUCCESS`, tous les tests passent (existants + nouveaux), aucun test existant modifié en dehors de l'ajout du mock `@Mock private DeadlineCalculator`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/PlanActionsService.java src/main/java/gov/bf/ascelc/univers_audits/service/TransmissionAutoriteService.java src/main/java/gov/bf/ascelc/univers_audits/service/MissionSuiviService.java src/test/java/gov/bf/ascelc/univers_audits/service/PlanActionsServiceTest.java src/test/java/gov/bf/ascelc/univers_audits/service/TransmissionAutoriteServiceTest.java src/test/java/gov/bf/ascelc/univers_audits/service/MissionSuiviServiceTest.java
git commit -m "refactor(jours-ouvrables): PlanActionsService/TransmissionAutoriteService/MissionSuiviService utilisent DeadlineCalculator"
```

---

### Task 5: Refactor de `InvestigationServiceImpl`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java:1295-1303` (méthode `resolveDeadline`)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java:1436-1445` (calcul inline dans `toPlanInvestigationResponse`)
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java` (ajout de tests)

**Interfaces:**
- Consumes: `DeadlineCalculator.addBusinessDays(Instant, int)`/`addCalendarDays(Instant, int)` et `ParametreDelaiService.resolveJoursOuvrables(String)` (Task 3).

- [ ] **Step 1: Ajouter le champ `DeadlineCalculator`**

Ajouter l'import `gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator` et un champ `private final DeadlineCalculator deadlineCalculator;` à côté du champ `parametreDelaiService` existant dans `InvestigationServiceImpl`.

- [ ] **Step 2: Remplacer la méthode `resolveDeadline` (lignes 1295-1303)**

```java
    private Instant resolveDeadline(Instant from, String delaiCode) {
        try {
            int delaiJours = parametreDelaiService.resolveDelaiJours(delaiCode);
            boolean joursOuvrables = parametreDelaiService.resolveJoursOuvrables(delaiCode);
            return joursOuvrables
                    ? deadlineCalculator.addBusinessDays(from, delaiJours)
                    : deadlineCalculator.addCalendarDays(from, delaiJours);
        } catch (ResourceNotFoundException e) {
            log.warn("Délai {} indisponible — échéance non calculée : {}", delaiCode, e.getMessage());
            return null;
        }
    }
```

Ce changement couvre à lui seul les 4 sites d'appel de `fillCircuitValidationDeadlines` (lignes 1271/1277/1283/1289) puisqu'ils passent tous par cette même méthode privée.

- [ ] **Step 3: Convertir le calcul inline de `toPlanInvestigationResponse` (lignes 1436-1445)**

Remplacer :
```java
            try {
                int delaiJours = parametreDelaiService.resolveDelaiJours(
                        "VALIDATION_PLAN_INVESTIGATION_DEI");
                validationDeadline = dateDelivrance.get().plusSeconds((long) delaiJours * 24 * 3600);
            } catch (ResourceNotFoundException e) {
                log.warn("Délai VALIDATION_PLAN_INVESTIGATION_DEI indisponible — "
                        + "échéance de validation non calculée : {}", e.getMessage());
            }
```

par un appel à la méthode privée `resolveDeadline` déjà présente dans la classe :
```java
            validationDeadline = resolveDeadline(dateDelivrance.get(), "VALIDATION_PLAN_INVESTIGATION_DEI");
```

Le bloc `try/catch` disparaît entièrement ici — `resolveDeadline` gère déjà l'exception et retourne `null` (comportement identique à l'ancien code qui laissait `validationDeadline` à `null` en cas d'erreur).

- [ ] **Step 4: Ajouter un test ciblé dans `InvestigationServiceImplTest`**

Ajouter `@Mock private DeadlineCalculator deadlineCalculator;` à la liste des mocks existants du fichier (nécessaire pour `@InjectMocks`). Ajouter un test vérifiant que `resolveDeadline` route vers `addBusinessDays` quand `resolveJoursOuvrables` renvoie `true`, sur le modèle exact du test ajouté à `PlanActionsServiceTest` (Task 4, Step 4) — **lire les tests existants de ce fichier avant d'écrire celui-ci pour respecter ses conventions de construction d'`Investigation`/mocks**, ce plan ne peut pas connaître l'état exact du fichier à l'exécution. Utiliser le code `"APPROBATION_CGE"` pour ce test (l'un des 4 codes utilisés par `fillCircuitValidationDeadlines`).

Ajouter un second test pour le site converti de `toPlanInvestigationResponse` : vérifie qu'un plan d'investigation avec un mandat délivré calcule bien `validationDeadline` via `resolveDeadline("VALIDATION_PLAN_INVESTIGATION_DEI")` (même patron de vérification que ci-dessus, code `"VALIDATION_PLAN_INVESTIGATION_DEI"`).

- [ ] **Step 5: Lancer la suite complète du fichier de test**

Run: `mvn -q -Dtest=InvestigationServiceImplTest test` (depuis `back-end/`)
Expected: `BUILD SUCCESS`, tous les tests passent (existants + nouveaux).

- [ ] **Step 6: Lancer la suite complète du projet pour confirmer l'absence de régression globale**

Run: `mvn -q test` (depuis `back-end/`)
Expected: tous les tests passent hormis l'échec pré-existant connu et sans rapport
(`UniversAuditsApplicationTests.contextLoads`, si la base/Redis ne sont pas accessibles
dans le shell d'exécution — sinon 0 échec, 0 erreur).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "refactor(jours-ouvrables): InvestigationServiceImpl utilise DeadlineCalculator (4 sites + calcul inline converti)"
```
