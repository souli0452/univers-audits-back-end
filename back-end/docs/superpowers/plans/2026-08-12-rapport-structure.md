# Rapport structuré (Lot 5, sous-chantier 1/4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer les champs texte libres `Investigation.finalReport`/`conclusions`/`recommendations` par deux entités structurées (`RapportEnquete`, `NoteRecommandations`), éditables progressivement pendant l'investigation, et faire de `submitReport()` un contrôle de complétude plutôt qu'une simple prise de texte brut.

**Architecture:** Deux nouvelles entités JPA (1:1 via `@ManyToOne` + `@JoinColumn(unique = true)`, pattern déjà utilisé par `Mandat`), leurs repositories, un service dédié `RapportEnqueteService` et un contrôleur dédié `RapportEnqueteController` nichés sous `/api/v1/investigations/{id}`, puis modification du flux existant `InvestigationServiceImpl.submitReport()` pour valider la complétude des deux nouvelles entités avant de terminer l'investigation.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok (`@SuperBuilder`), JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Migration Liquibase : fichier `031-create-rapport-enquete-note-recommandations.sql`, format `--liquibase formatted sql` / `--changeset dev:031-create-rapport-enquete-note-recommandations` (numéro 031 confirmé libre, dernier existant = 030).
- Aucune contrainte `NOT NULL` en base sur les champs de contenu de `rapport_enquete`/`note_recommandations` — validation de complétude uniquement applicative, via `isComplet()`, au moment de `submitReport()`.
- Aucune annotation Bean Validation (`@NotBlank`) sur `RapportEnqueteRequest`/`NoteRecommandationsRequest` — un brouillon partiel doit pouvoir être enregistré tant que l'investigation est `IN_PROGRESS`.
- Relation `RapportEnquete` → `Investigation` et `NoteRecommandations` → `RapportEnquete` : `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn(nullable = false, unique = true)`, identique au pattern déjà utilisé par `Mandat.java` — pas de vrai `@OneToOne`.
- Aucun champ `soumisAt` dédié sur les deux nouvelles entités : le verrou d'édition post-soumission s'appuie uniquement sur `Investigation.status != IN_PROGRESS`.
- `RapportEnqueteController` n'appelle pas `DossierAccessGuard` — contrôle d'accès par rôles `@PreAuthorize` uniquement, cohérent avec le reste de `InvestigationController` (qui n'utilise jamais `DossierAccessGuard`, contrairement à `SectionDossierTravailController` qui est niché sous `/dossiers/{id}`).
- Rôles en lecture (`READ_ROLES`) : `hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')` — identiques à `InvestigationController.findById`.
- Rôles en écriture (`WRITE_ROLES`) : `hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')` — identiques à `InvestigationController.submitReport`.
- Aucun test de contrôleur (`@WebMvcTest`/MockMvc) : confirmé par `find . -iname "*ControllerTest.java"` → 0 résultat dans tout le projet. Les contrôleurs ne sont jamais testés directement dans ce codebase.
- `Investigation.reportSubmittedAt`, `Investigation.outcome` et le circuit d'approbation existant (`approveDei`/`approveLegalAdvisor`/`approveCge`, lignes 407-513 de `InvestigationServiceImpl.java`) restent strictement inchangés.

---

### Task 1: Entités RapportEnquete/NoteRecommandations, repositories, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/RapportEnquete.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/NoteRecommandations.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/RapportEnqueteRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/NoteRecommandationsRepository.java`
- Create: `src/main/resources/db/changelog/migrations/031-create-rapport-enquete-note-recommandations.sql`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/model/entity/RapportEnqueteTest.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/model/entity/NoteRecommandationsTest.java`

**Interfaces:**
- Consumes: `AuditEntity` (`gov.bf.ascelc.univers_audits.abstracts.AuditEntity` — fournit `id`, `createdAt`, `updatedAt`, `createdById`, `updatedById`, `version`), `Investigation` (`gov.bf.ascelc.univers_audits.model.entity.Investigation`, champ `id` existant).
- Produces: `RapportEnquete.isComplet(): boolean`, `NoteRecommandations.isComplet(): boolean`, `RapportEnqueteRepository.findByInvestigationId(UUID): Optional<RapportEnquete>`, `NoteRecommandationsRepository.findByRapportEnqueteId(UUID): Optional<NoteRecommandations>` — consommés par les tâches suivantes.

- [ ] **Step 1: Créer l'entité `RapportEnquete`**

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
@Table(name = "rapport_enquete")
public class RapportEnquete extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "titre", columnDefinition = "TEXT")
    private String titre;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "methodologie", columnDefinition = "TEXT")
    private String methodologie;

    @Column(name = "informations_collectees", columnDefinition = "TEXT")
    private String informationsCollectees;

    @Column(name = "expose_factuel_anomalies", columnDefinition = "TEXT")
    private String exposeFactuelAnomalies;

    @Column(name = "quantification_prejudice", columnDefinition = "TEXT")
    private String quantificationPrejudice;

    @Column(name = "reserves", columnDefinition = "TEXT")
    private String reserves;

    @Column(name = "conclusions", columnDefinition = "TEXT")
    private String conclusions;

    /** Réserves exclues : c'est le seul champ facultatif du rapport. */
    public boolean isComplet() {
        return isPresent(titre)
                && isPresent(introduction)
                && isPresent(methodologie)
                && isPresent(informationsCollectees)
                && isPresent(exposeFactuelAnomalies)
                && isPresent(quantificationPrejudice)
                && isPresent(conclusions);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
```

- [ ] **Step 2: Créer l'entité `NoteRecommandations`**

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
@Table(name = "note_recommandations")
public class NoteRecommandations extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rapport_enquete_id", nullable = false, unique = true)
    private RapportEnquete rapportEnquete;

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;

    public boolean isComplet() {
        return contenu != null && !contenu.isBlank();
    }
}
```

- [ ] **Step 3: Écrire le test de `RapportEnquete.isComplet()`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RapportEnqueteTest {

    private RapportEnquete buildComplet() {
        return RapportEnquete.builder()
                .titre("Rapport d'enquête n°1")
                .introduction("Introduction")
                .methodologie("Méthodologie")
                .informationsCollectees("Informations collectées")
                .exposeFactuelAnomalies("Exposé factuel")
                .quantificationPrejudice("Préjudice estimé à 1 000 000 FCFA")
                .conclusions("Conclusions")
                .build();
    }

    @Test
    void isComplet_retourneVraiQuandTousLesChampsRequisSontRenseignes() {
        RapportEnquete rapport = buildComplet();

        assertThat(rapport.isComplet()).isTrue();
    }

    @Test
    void isComplet_resteVraiSiReservesEstVide() {
        RapportEnquete rapport = buildComplet();
        rapport.setReserves(null);

        assertThat(rapport.isComplet()).isTrue();
    }

    @Test
    void isComplet_retourneFauxSiUnChampRequisEstVide() {
        RapportEnquete rapport = buildComplet();
        rapport.setConclusions("   ");

        assertThat(rapport.isComplet()).isFalse();
    }

    @Test
    void isComplet_retourneFauxSiUnChampRequisEstNull() {
        RapportEnquete rapport = buildComplet();
        rapport.setTitre(null);

        assertThat(rapport.isComplet()).isFalse();
    }
}
```

- [ ] **Step 4: Écrire le test de `NoteRecommandations.isComplet()`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteRecommandationsTest {

    @Test
    void isComplet_retourneVraiQuandContenuRenseigne() {
        NoteRecommandations note = NoteRecommandations.builder()
                .contenu("Recommandation n°1 : ...")
                .build();

        assertThat(note.isComplet()).isTrue();
    }

    @Test
    void isComplet_retourneFauxQuandContenuVide() {
        NoteRecommandations note = NoteRecommandations.builder()
                .contenu("   ")
                .build();

        assertThat(note.isComplet()).isFalse();
    }

    @Test
    void isComplet_retourneFauxQuandContenuNull() {
        NoteRecommandations note = NoteRecommandations.builder().build();

        assertThat(note.isComplet()).isFalse();
    }
}
```

- [ ] **Step 5: Lancer les deux tests, vérifier qu'ils passent**

Run: `mvn test -Dtest=RapportEnqueteTest,NoteRecommandationsTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 6: Créer les repositories**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RapportEnqueteRepository extends JpaRepository<RapportEnquete, UUID> {
    Optional<RapportEnquete> findByInvestigationId(UUID investigationId);
}
```

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NoteRecommandationsRepository extends JpaRepository<NoteRecommandations, UUID> {
    Optional<NoteRecommandations> findByRapportEnqueteId(UUID rapportEnqueteId);
}
```

- [ ] **Step 7: Créer la migration Liquibase**

Fichier : `src/main/resources/db/changelog/migrations/031-create-rapport-enquete-note-recommandations.sql`

```sql
--liquibase formatted sql
--changeset dev:031-create-rapport-enquete-note-recommandations

CREATE TABLE rapport_enquete (
    id                        UUID      PRIMARY KEY,
    investigation_id          UUID      NOT NULL UNIQUE REFERENCES investigation(id),
    titre                     TEXT,
    introduction              TEXT,
    methodologie              TEXT,
    informations_collectees   TEXT,
    expose_factuel_anomalies  TEXT,
    quantification_prejudice  TEXT,
    reserves                  TEXT,
    conclusions               TEXT,
    version                   BIGINT    NOT NULL DEFAULT 0,
    created_at                TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP,
    created_by_id             VARCHAR(100),
    updated_by_id             VARCHAR(100)
);

CREATE TABLE note_recommandations (
    id                 UUID      PRIMARY KEY,
    rapport_enquete_id UUID      NOT NULL UNIQUE REFERENCES rapport_enquete(id),
    contenu            TEXT,
    version            BIGINT    NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP,
    created_by_id      VARCHAR(100),
    updated_by_id      VARCHAR(100)
);

ALTER TABLE investigation DROP COLUMN final_report;
ALTER TABLE investigation DROP COLUMN conclusions;
ALTER TABLE investigation DROP COLUMN recommendations;

COMMENT ON TABLE rapport_enquete IS 'Rapport d enquete structure (Lot 5 sous-chantier 1/4) - remplace les anciens champs texte libres finalReport/conclusions/recommendations de investigation';
COMMENT ON COLUMN rapport_enquete.reserves IS 'Seul champ facultatif du rapport - les autres sont requis avant soumission (submitReport)';
COMMENT ON TABLE note_recommandations IS 'Note de recommandations, document distinct du rapport d enquete conformement au plan de travail ASCE-LC (Lot 5)';
```

Aucune modification du changelog racine requise : `includeAll` référence déjà le dossier `migrations/`.

- [ ] **Step 8: Compiler le module pour vérifier qu'il n'y a pas d'erreur**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 9: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/RapportEnquete.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/NoteRecommandations.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/RapportEnqueteRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/NoteRecommandationsRepository.java \
        src/main/resources/db/changelog/migrations/031-create-rapport-enquete-note-recommandations.sql \
        src/test/java/gov/bf/ascelc/univers_audits/model/entity/RapportEnqueteTest.java \
        src/test/java/gov/bf/ascelc/univers_audits/model/entity/NoteRecommandationsTest.java
git commit -m "feat: add RapportEnquete and NoteRecommandations entities with migration"
```

---

### Task 2: Service `RapportEnqueteService` + DTOs

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RapportEnqueteRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RapportEnqueteResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/NoteRecommandationsRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/NoteRecommandationsResponse.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/RapportEnqueteService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/RapportEnqueteServiceTest.java`

**Interfaces:**
- Consumes: `RapportEnqueteRepository`, `NoteRecommandationsRepository` (Task 1), `InvestigationRepository` (`gov.bf.ascelc.univers_audits.repository.InvestigationRepository`, existant), `BusinessException`/`ResourceNotFoundException` (`gov.bf.ascelc.univers_audits.shared.exceptions`).
- Produces: `RapportEnqueteService.enregistrerRapport(UUID, RapportEnqueteRequest): RapportEnquete`, `RapportEnqueteService.getRapportOrThrow(UUID): RapportEnquete`, `RapportEnqueteService.enregistrerNote(UUID, NoteRecommandationsRequest): NoteRecommandations`, `RapportEnqueteService.getNoteOrThrow(UUID): NoteRecommandations` — consommés par le contrôleur (Task 3) et par `InvestigationServiceImpl.submitReport()` (Task 4, via les repositories directement).

- [ ] **Step 1: Créer les DTOs de requête et de réponse**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportEnqueteRequest {
    private String titre;
    private String introduction;
    private String methodologie;
    private String informationsCollectees;
    private String exposeFactuelAnomalies;
    private String quantificationPrejudice;
    private String reserves;
    private String conclusions;
}
```

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
public class RapportEnqueteResponse {
    private UUID id;
    private UUID investigationId;
    private String titre;
    private String introduction;
    private String methodologie;
    private String informationsCollectees;
    private String exposeFactuelAnomalies;
    private String quantificationPrejudice;
    private String reserves;
    private String conclusions;
    private boolean complet;
    private Instant createdAt;
    private Instant updatedAt;
}
```

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteRecommandationsRequest {
    private String contenu;
}
```

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
public class NoteRecommandationsResponse {
    private UUID id;
    private UUID rapportEnqueteId;
    private String contenu;
    private boolean complet;
    private Instant createdAt;
    private Instant updatedAt;
}
```

- [ ] **Step 2: Créer `RapportEnqueteService`**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.model.dto.request.NoteRecommandationsRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RapportEnqueteRequest;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NoteRecommandationsRepository;
import gov.bf.ascelc.univers_audits.repository.RapportEnqueteRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RapportEnqueteService {

    private final RapportEnqueteRepository rapportEnqueteRepository;
    private final NoteRecommandationsRepository noteRecommandationsRepository;
    private final InvestigationRepository investigationRepository;

    @Transactional
    public RapportEnquete enregistrerRapport(UUID investigationId, RapportEnqueteRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        checkEditable(investigation);

        RapportEnquete rapport = rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseGet(() -> RapportEnquete.builder().investigation(investigation).build());

        rapport.setTitre(request.getTitre());
        rapport.setIntroduction(request.getIntroduction());
        rapport.setMethodologie(request.getMethodologie());
        rapport.setInformationsCollectees(request.getInformationsCollectees());
        rapport.setExposeFactuelAnomalies(request.getExposeFactuelAnomalies());
        rapport.setQuantificationPrejudice(request.getQuantificationPrejudice());
        rapport.setReserves(request.getReserves());
        rapport.setConclusions(request.getConclusions());

        RapportEnquete saved = rapportEnqueteRepository.save(rapport);
        log.info("Rapport d'enquête enregistré — investigation: {}", investigationId);
        return saved;
    }

    public RapportEnquete getRapportOrThrow(UUID investigationId) {
        return rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun rapport d'enquête n'a été rédigé pour cette investigation : "
                                + investigationId));
    }

    @Transactional
    public NoteRecommandations enregistrerNote(UUID investigationId, NoteRecommandationsRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        checkEditable(investigation);

        RapportEnquete rapport = rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Rédigez d'abord le rapport d'enquête "
                                + "(PUT /investigations/{id}/rapport) avant la note de recommandations."));

        NoteRecommandations note = noteRecommandationsRepository
                .findByRapportEnqueteId(rapport.getId())
                .orElseGet(() -> NoteRecommandations.builder().rapportEnquete(rapport).build());

        note.setContenu(request.getContenu());

        NoteRecommandations saved = noteRecommandationsRepository.save(note);
        log.info("Note de recommandations enregistrée — investigation: {}", investigationId);
        return saved;
    }

    public NoteRecommandations getNoteOrThrow(UUID investigationId) {
        RapportEnquete rapport = getRapportOrThrow(investigationId);
        return noteRecommandationsRepository.findByRapportEnqueteId(rapport.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune note de recommandations n'a été rédigée pour cette investigation : "
                                + investigationId));
    }

    private void checkEditable(Investigation investigation) {
        if (investigation.getStatus() != InvestigationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Le rapport d'enquête et la note de recommandations ne sont modifiables "
                            + "que pendant que l'investigation est en cours.");
        }
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
```

- [ ] **Step 3: Écrire le test du service**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.model.dto.request.NoteRecommandationsRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RapportEnqueteRequest;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NoteRecommandationsRepository;
import gov.bf.ascelc.univers_audits.repository.RapportEnqueteRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RapportEnqueteServiceTest {

    @Mock
    private RapportEnqueteRepository rapportEnqueteRepository;
    @Mock
    private NoteRecommandationsRepository noteRecommandationsRepository;
    @Mock
    private InvestigationRepository investigationRepository;

    @InjectMocks
    private RapportEnqueteService service;

    private Investigation investigation;
    private UUID investigationId;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        investigation = Investigation.builder()
                .id(investigationId)
                .status(InvestigationStatus.IN_PROGRESS)
                .build();
    }

    private RapportEnqueteRequest buildRequest() {
        return RapportEnqueteRequest.builder()
                .titre("Titre")
                .introduction("Introduction")
                .methodologie("Méthodologie")
                .informationsCollectees("Infos")
                .exposeFactuelAnomalies("Anomalies")
                .quantificationPrejudice("Préjudice")
                .conclusions("Conclusions")
                .build();
    }

    @Test
    void enregistrerRapport_creeUnNouveauRapportSiAucunNExisteEncore() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(rapportEnqueteRepository.save(any(RapportEnquete.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RapportEnquete result = service.enregistrerRapport(investigationId, buildRequest());

        assertThat(result.getInvestigation()).isEqualTo(investigation);
        assertThat(result.getTitre()).isEqualTo("Titre");
        verify(rapportEnqueteRepository).save(any(RapportEnquete.class));
    }

    @Test
    void enregistrerRapport_metAJourLeRapportExistantAuDeuxiemeAppel() {
        RapportEnquete existant = RapportEnquete.builder()
                .investigation(investigation)
                .titre("Ancien titre")
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(existant));
        when(rapportEnqueteRepository.save(any(RapportEnquete.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RapportEnquete result = service.enregistrerRapport(investigationId, buildRequest());

        assertThat(result).isSameAs(existant);
        assertThat(result.getTitre()).isEqualTo("Titre");
        verify(rapportEnqueteRepository).save(existant);
    }

    @Test
    void enregistrerRapport_rejetteSiInvestigationNEstPasEnCours() {
        investigation.setStatus(InvestigationStatus.COMPLETED);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.enregistrerRapport(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
        verify(rapportEnqueteRepository, never()).save(any());
    }

    @Test
    void getRapportOrThrow_leveResourceNotFoundExceptionSiAucunRapport() {
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRapportOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void enregistrerNote_rejetteSiAucunRapportNExisteEncore() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        NoteRecommandationsRequest request = NoteRecommandationsRequest.builder()
                .contenu("Recommandation").build();

        assertThatThrownBy(() -> service.enregistrerNote(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(noteRecommandationsRepository, never()).save(any());
    }

    @Test
    void enregistrerNote_creeUneNouvelleNoteSiLeRapportExiste() {
        RapportEnquete rapport = RapportEnquete.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(rapport));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapport.getId())).thenReturn(Optional.empty());
        when(noteRecommandationsRepository.save(any(NoteRecommandations.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NoteRecommandationsRequest request = NoteRecommandationsRequest.builder()
                .contenu("Recommandation n°1").build();

        NoteRecommandations result = service.enregistrerNote(investigationId, request);

        assertThat(result.getRapportEnquete()).isEqualTo(rapport);
        assertThat(result.getContenu()).isEqualTo("Recommandation n°1");
    }

    @Test
    void enregistrerNote_rejetteSiInvestigationNEstPasEnCours() {
        investigation.setStatus(InvestigationStatus.SUSPENDED);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        NoteRecommandationsRequest request = NoteRecommandationsRequest.builder()
                .contenu("Recommandation").build();

        assertThatThrownBy(() -> service.enregistrerNote(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(noteRecommandationsRepository, never()).save(any());
    }

    @Test
    void getNoteOrThrow_leveResourceNotFoundExceptionSiAucuneNote() {
        RapportEnquete rapport = RapportEnquete.builder().id(UUID.randomUUID()).build();
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(rapport));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapport.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNoteOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 4: Lancer les tests, vérifier qu'ils passent**

Run: `mvn test -Dtest=RapportEnqueteServiceTest`
Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RapportEnqueteRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RapportEnqueteResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/NoteRecommandationsRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/NoteRecommandationsResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/RapportEnqueteService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/RapportEnqueteServiceTest.java
git commit -m "feat: add RapportEnqueteService with progressive draft editing"
```

---

### Task 3: Contrôleur `RapportEnqueteController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/RapportEnqueteController.java`

**Interfaces:**
- Consumes: `RapportEnqueteService` (Task 2, méthodes `enregistrerRapport`, `getRapportOrThrow`, `enregistrerNote`, `getNoteOrThrow`), `ApiUrls.INVESTIGATIONS` (`gov.bf.ascelc.univers_audits.shared.utils.ApiUrls`, valeur `/api/v1/investigations`).
- Produces: endpoints REST `GET/PUT /api/v1/investigations/{id}/rapport` et `GET/PUT /api/v1/investigations/{id}/note-recommandations`.

- [ ] **Step 1: Créer le contrôleur**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteRecommandationsRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RapportEnqueteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.NoteRecommandationsResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RapportEnqueteResponse;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.service.RapportEnqueteService;
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
public class RapportEnqueteController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final RapportEnqueteService rapportEnqueteService;

    @GetMapping("/rapport")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<RapportEnqueteResponse> getRapport(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(rapportEnqueteService.getRapportOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/rapport")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<RapportEnqueteResponse> putRapport(
            @PathVariable UUID id,
            @Valid @RequestBody RapportEnqueteRequest request) {

        log.info("Enregistrement rapport d'enquête — investigation {}", id);
        RapportEnquete saved = rapportEnqueteService.enregistrerRapport(id, request);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/note-recommandations")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<NoteRecommandationsResponse> getNote(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(rapportEnqueteService.getNoteOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/note-recommandations")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<NoteRecommandationsResponse> putNote(
            @PathVariable UUID id,
            @Valid @RequestBody NoteRecommandationsRequest request) {

        log.info("Enregistrement note de recommandations — investigation {}", id);
        NoteRecommandations saved = rapportEnqueteService.enregistrerNote(id, request);
        return ResponseEntity.ok(toResponse(saved));
    }

    private RapportEnqueteResponse toResponse(RapportEnquete r) {
        return RapportEnqueteResponse.builder()
                .id(r.getId())
                .investigationId(r.getInvestigation().getId())
                .titre(r.getTitre())
                .introduction(r.getIntroduction())
                .methodologie(r.getMethodologie())
                .informationsCollectees(r.getInformationsCollectees())
                .exposeFactuelAnomalies(r.getExposeFactuelAnomalies())
                .quantificationPrejudice(r.getQuantificationPrejudice())
                .reserves(r.getReserves())
                .conclusions(r.getConclusions())
                .complet(r.isComplet())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private NoteRecommandationsResponse toResponse(NoteRecommandations n) {
        return NoteRecommandationsResponse.builder()
                .id(n.getId())
                .rapportEnqueteId(n.getRapportEnquete().getId())
                .contenu(n.getContenu())
                .complet(n.isComplet())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
```

Pas de test dédié pour ce contrôleur : convention confirmée du projet (aucun `*ControllerTest.java` n'existe, voir Global Constraints).

- [ ] **Step 2: Compiler pour vérifier qu'il n'y a pas d'erreur**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/RapportEnqueteController.java
git commit -m "feat: add RapportEnqueteController REST endpoints"
```

---

### Task 4: Intégration dans `submitReport()` et suppression des anciens champs

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Investigation.java:72-80`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InvestigationResponse.java:33-35`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/InvestigationUpdateRequest.java` (remplacement intégral)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java:64-72,365-405`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `RapportEnqueteRepository.findByInvestigationId` (Task 1), `NoteRecommandationsRepository.findByRapportEnqueteId` (Task 1), `RapportEnquete.isComplet()`/`NoteRecommandations.isComplet()` (Task 1).
- Produces: `submitReport()` transformé en contrôle de complétude — aucune nouvelle interface publique, ce n'est plus un simple accepteur de texte brut.

Ce module ne compile qu'une fois ces quatre fichiers modifiés ensemble (retirer un champ de `Investigation`/`InvestigationResponse` avant d'avoir corrigé `InvestigationServiceImpl`/`InvestigationUpdateRequest` casse la compilation) : les étapes 1 à 5 doivent être appliquées avant toute vérification de compilation intermédiaire.

- [ ] **Step 1: Retirer les trois anciens champs de `Investigation`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Investigation.java`, supprimer ce bloc (lignes 72-80) :

```java
    @Column(name = "final_report", columnDefinition = "TEXT")
    private String finalReport;

    @Column(name = "conclusions", columnDefinition = "TEXT")
    private String conclusions;


    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;

```

Le champ `outcome` (juste après) et tout le reste du fichier restent inchangés.

- [ ] **Step 2: Retirer les trois champs correspondants de `InvestigationResponse`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InvestigationResponse.java`, supprimer ces trois lignes :

```java
    private String finalReport;
    private String conclusions;
    private String recommendations;
```

`outcome` et `reportSubmittedAt` restent inchangés. Aucune modification requise dans `InvestigationMapper.java` : le mapping MapStruct est implicite par nom de champ, sans `@Mapping` explicite pour ces trois-là.

- [ ] **Step 3: Réduire `InvestigationUpdateRequest` à `outcome`**

Remplacer intégralement le contenu de `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/InvestigationUpdateRequest.java` par :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationUpdateRequest {
    @NotNull(message = "Le résultat de l'investigation est obligatoire")
    private InvestigationOutcome outcome;
}
```

- [ ] **Step 4: Injecter les deux nouveaux repositories dans `InvestigationServiceImpl`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`, juste après la ligne :

```java
    private final SectionDossierTravailService sectionDossierTravailService;
```

ajouter :

```java
    private final RapportEnqueteRepository       rapportEnqueteRepository;
    private final NoteRecommandationsRepository   noteRecommandationsRepository;
```

Aucun nouvel import requis : le fichier importe déjà `gov.bf.ascelc.univers_audits.repository.*` (ligne 18), ce qui couvre `RapportEnqueteRepository` et `NoteRecommandationsRepository`.

- [ ] **Step 5: Réécrire le corps de `submitReport()`**

Remplacer le corps actuel de la méthode (lignes 365-405) par :

```java
    @Override
    @Transactional
    public InvestigationResponse submitReport(
            UUID investigationId,
            InvestigationUpdateRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getStatus() != InvestigationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Le rapport ne peut être soumis "
                            + "que pour une investigation en cours");
        }

        RapportEnquete rapport = rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun rapport d'enquête n'a été rédigé pour cette investigation. "
                                + "Renseignez-le via PUT /investigations/{id}/rapport avant soumission."));
        if (!rapport.isComplet()) {
            throw new BusinessException(
                    "Le rapport d'enquête est incomplet — tous les champs sont obligatoires "
                            + "(les réserves exceptées).");
        }

        NoteRecommandations note = noteRecommandationsRepository
                .findByRapportEnqueteId(rapport.getId())
                .orElseThrow(() -> new BusinessException(
                        "Aucune note de recommandations n'a été rédigée pour cette investigation. "
                                + "Renseignez-la via PUT /investigations/{id}/note-recommandations "
                                + "avant soumission."));
        if (!note.isComplet()) {
            throw new BusinessException("La note de recommandations est vide.");
        }

        inv.setOutcome(request.getOutcome());
        inv.complete();

        Dossier dossier = inv.getDossier();
        dossier.setStatus(DossierStatus.RAPPORT_PRODUIT);
        dossierRepository.save(dossier);

        auditRecorder.recordStatusChange(dossier,
                DossierStatus.EN_INVESTIGATION,
                DossierStatus.RAPPORT_PRODUIT,
                "Rapport d'investigation soumis",
                agentContextResolver.getCurrentAgent(), ipAddress);

        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(dossier,
                ObservationType.FIELD_FINDING,
                "Rapport final soumis. Conclusions : " + rapport.getConclusions(),
                true, agentContextResolver.getCurrentAgent());

        log.info("Rapport soumis — investigation: {}", investigationId);
        return investigationMapper.toResponse(saved);
    }
```

Le reste du fichier (les autres méthodes, y compris `approveDei`/`approveLegalAdvisor`/`approveCge`) n'est pas modifié.

- [ ] **Step 6: Compiler pour vérifier que les 5 fichiers modifiés forment un ensemble cohérent**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Ajouter les nouveaux mocks au test existant**

Dans `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`, ajouter ces deux imports après la ligne `import gov.bf.ascelc.univers_audits.enums.StatutProcedureUrgence;` :

```java
import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationUpdateRequest;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.repository.NoteRecommandationsRepository;
import gov.bf.ascelc.univers_audits.repository.RapportEnqueteRepository;
```

Puis, juste après la ligne :

```java
    @Mock private SectionDossierTravailService sectionDossierTravailService;
```

ajouter :

```java
    @Mock private RapportEnqueteRepository       rapportEnqueteRepository;
    @Mock private NoteRecommandationsRepository   noteRecommandationsRepository;
```

- [ ] **Step 8: Ajouter les tests de `submitReport()`**

Juste après la méthode existante `buildInvestigation(Dossier dossier)`, ajouter :

```java
    private Investigation buildInProgressInvestigation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setStatus(InvestigationStatus.IN_PROGRESS);
        return investigation;
    }

    private RapportEnquete buildRapportComplet(Investigation investigation) {
        return RapportEnquete.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .titre("Titre").introduction("Introduction").methodologie("Methodologie")
                .informationsCollectees("Infos").exposeFactuelAnomalies("Anomalies")
                .quantificationPrejudice("Prejudice").conclusions("Conclusions")
                .build();
    }
```

Puis, dans un bloc de tests dédié à `submitReport()`, ajouter ces quatre tests (n'importe quel endroit dans la classe, après les méthodes utilitaires ci-dessus) :

```java
    @Test
    void submitReport_rejetteSiAucunRapportRedige() {
        Investigation investigation = buildInProgressInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        assertThatThrownBy(() -> service.submitReport(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void submitReport_rejetteSiRapportIncomplet() {
        Investigation investigation = buildInProgressInvestigation();
        RapportEnquete rapportIncomplet = RapportEnquete.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .titre("Titre")
                .build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(rapportIncomplet));

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        assertThatThrownBy(() -> service.submitReport(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void submitReport_rejetteSiAucuneNoteRedigee() {
        Investigation investigation = buildInProgressInvestigation();
        RapportEnquete rapportComplet = buildRapportComplet(investigation);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(rapportComplet));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapportComplet.getId()))
                .thenReturn(Optional.empty());

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        assertThatThrownBy(() -> service.submitReport(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void submitReport_succeedsAvecRapportEtNoteComplets() {
        Investigation investigation = buildInProgressInvestigation();
        RapportEnquete rapportComplet = buildRapportComplet(investigation);
        NoteRecommandations noteComplete = NoteRecommandations.builder()
                .rapportEnquete(rapportComplet)
                .contenu("Recommandation n°1")
                .build();
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(rapportComplet));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapportComplet.getId()))
                .thenReturn(Optional.of(noteComplete));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(investigationMapper.toResponse(any(Investigation.class)))
                .thenReturn(InvestigationResponse.builder().build());

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        service.submitReport(investigation.getId(), request, "127.0.0.1");

        assertThat(investigation.getStatus()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(investigation.getOutcome()).isEqualTo(InvestigationOutcome.ARCHIVED);
        verify(dossierRepository).save(investigation.getDossier());
    }
```

- [ ] **Step 9: Lancer la suite de tests complète du module de service**

Run: `mvn test -Dtest=InvestigationServiceImplTest`
Expected: tous les tests passent, y compris les 4 nouveaux (aucune régression sur les tests existants).

- [ ] **Step 10: Lancer l'ensemble de la suite de tests unitaires du projet**

Run: `mvn test`
Expected: `BUILD SUCCESS` (l'échec pré-existant et confirmé de `UniversAuditsApplicationTests.contextLoads`, dû à l'absence de datasource dans cet environnement, n'est pas une régression de cette tâche).

- [ ] **Step 11: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/Investigation.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InvestigationResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/InvestigationUpdateRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: submitReport validates RapportEnquete/NoteRecommandations completeness"
```
