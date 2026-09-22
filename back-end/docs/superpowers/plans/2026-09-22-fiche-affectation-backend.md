# Fiche d'affectation des dossiers — Plan d'implémentation (Backend)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer le circuit d'affectation des dossiers (BRPD → Cabinet CGE →
CGEA → département/conseiller juridique désigné → suivi), sous forme d'une
nouvelle sous-ressource REST de `Dossier`, avec notification automatique du
destinataire résolu.

**Architecture:** Nouvelle entité `FicheAffectation` (relation 1-à-1 avec
`Dossier`, patron identique à `DecisionCGE`), un service concret
`FicheAffectationService` (pas d'interface, patron `RapportEnqueteService`),
un controller `FicheAffectationController` avec mapping manuel réponse
(patron `RapportEnqueteController`), 4 endpoints correspondant aux 4
sections du document papier. Migration additive `016` (table + élargissement
CHECK constraint `notification_type_check`) et `017` (templates
`portal_config`).

**Tech Stack:** Spring Boot 3 / Java 17, JPA/Hibernate (`ddl-auto=validate`),
Liquibase (`--liquibase formatted sql`), JUnit 5 + Mockito + AssertJ,
PostgreSQL (base Docker partagée `asce_postgres`).

**Spec:** `docs/superpowers/specs/2026-09-22-fiche-affectation-design.md`

## Global Constraints

- **Jamais éditer une migration déjà `EXECUTED`** — toute correction de
  schéma est une nouvelle migration additive. Dernière migration existante :
  `015-seed-notification-templates-escalade.sql`.
- **Toute nouvelle valeur `NotificationType` doit élargir le CHECK
  constraint `notification_type_check` dans la MÊME migration** —
  `ddl-auto=validate` ne vérifie jamais les CHECK constraints ; leur oubli a
  déjà causé un défaut Critical documenté 2 fois dans ce dépôt (migrations
  013, 014). Ne pas répéter cette erreur une 3<sup>e</sup> fois.
- La fiche d'affectation est **non bloquante** : aucune méthode de ce plan
  ne doit modifier `DossierStatus` ni empêcher une transition de statut
  existante.
- Seuls les départements de code `DEI` et `DAC` sont des cibles valides pour
  `typeDesignation = DEPARTEMENT` (le référentiel `Departement` contient
  aussi `DCP`, `DIP`, `DSRAJ`, `DSI`, `DRH`, non éligibles ici).
- `BRPD` et le rôle `CONSEILLER_JURIDIQUE` n'existent **que** côté Keycloak
  (rôles realm), jamais dans le référentiel `Departement` ni comme colonne
  sur `Agent`.
- Tests service en Mockito pur (`@ExtendWith(MockitoExtension.class)`,
  `@Mock`/`@InjectMocks`, assertions AssertJ) — patron exact de
  `RapportEnqueteServiceTest.java`. Aucun test `@WebMvcTest`/MockMvc n'existe
  dans ce dépôt à ce jour : ne pas en introduire un nouveau pour ce chantier,
  rester sur la couverture service.
- Vérifier le suite complète (`mvn test`) après chaque tâche touchant le
  schéma ou une migration — la base Docker partagée (`asce_postgres`) est
  commune à toutes les worktrees de ce dépôt.

---

### Task 1: Enums, entité `FicheAffectation`, migration `016`, repository

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/DecisionCgeAffectation.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/TypeDesignation.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/EtatAvancementAffectation.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/enums/NotificationType.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/FicheAffectation.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/FicheAffectationRepository.java`
- Create: `src/main/resources/db/changelog/migrations/016-fiche-affectation.sql`

**Interfaces:**
- Produces: `FicheAffectation` (entité JPA, tous les getters/setters via
  Lombok `@Getter @Setter @SuperBuilder`), `FicheAffectationRepository`
  avec `findByDossierId(UUID): Optional<FicheAffectation>` et
  `existsByDossierId(UUID): boolean` — consommés par toutes les tâches
  suivantes.

- [ ] **Step 1: Créer les 3 nouveaux enums**

```java
// src/main/java/gov/bf/ascelc/univers_audits/enums/DecisionCgeAffectation.java
package gov.bf.ascelc.univers_audits.enums;

public enum DecisionCgeAffectation {
    AFFECTATION_DIRECTE_CGEA,
    ECHANGE_PREALABLE
}
```

```java
// src/main/java/gov/bf/ascelc/univers_audits/enums/TypeDesignation.java
package gov.bf.ascelc.univers_audits.enums;

public enum TypeDesignation {
    DEPARTEMENT,
    AGENT_CJ,
    BRPD
}
```

```java
// src/main/java/gov/bf/ascelc/univers_audits/enums/EtatAvancementAffectation.java
package gov.bf.ascelc.univers_audits.enums;

public enum EtatAvancementAffectation {
    EN_COURS,
    CLOTURE,
    AUTRE
}
```

- [ ] **Step 2: Ajouter `AFFECTATION_DOSSIER` à `NotificationType`**

Modifier `src/main/java/gov/bf/ascelc/univers_audits/enums/NotificationType.java`
en ajoutant la 21<sup>e</sup> valeur à la fin de l'enum existant :

```java
public enum NotificationType {
    RECEIPT_B4,
    ACKNOWLEDGMENT_B5,
    COMPLEMENT_REQUEST,
    INADMISSIBILITY_DECISION,
    TRANSFER_DECISION,
    FINAL_DECISION,
    DEADLINE_ALERT,
    INTERNAL_ALERT,
    STATUS_UPDATE,
    INVESTIGATION_ALERT,
    INVESTIGATION_ASSIGNMENT,
    DEADLINE_ALERT_J3,
    COMPLEMENT_ALERT_J3,
    INVESTIGATION_ALERT_J3,
    DEMANDE_DOCUMENTS_ALERT,
    DEMANDE_DOCUMENTS_ALERT_J3,
    ESCALADE_AR,
    ESCALADE_COMPLEMENT,
    ESCALADE_INVESTIGATION,
    ESCALADE_DEMANDE_DOCUMENTS,
    AFFECTATION_DOSSIER
}
```

- [ ] **Step 3: Créer l'entité `FicheAffectation`**

```java
// src/main/java/gov/bf/ascelc/univers_audits/model/entity/FicheAffectation.java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation;
import gov.bf.ascelc.univers_audits.enums.TypeDesignation;
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
@Table(name = "fiche_affectation", indexes = {
        @Index(name = "idx_fiche_affectation_dossier",
                columnList = "dossier_id", unique = true)
})
public class FicheAffectation extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false, unique = true)
    private Dossier dossier;

    // ── Section 2 : Transmission par le Cabinet du CGE ──────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_cge", length = 30)
    private DecisionCgeAffectation decisionCge;

    @Column(name = "observations_cge", columnDefinition = "TEXT")
    private String observationsCge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cge_id")
    private Agent agentCge;

    @Column(name = "date_decision_cge")
    private Instant dateDecisionCge;

    // ── Section 3 : Affectation par le CGEA ─────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "type_designation", length = 20)
    private TypeDesignation typeDesignation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departement_designe_id")
    private Departement departementDesigne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_designe_id")
    private Agent agentDesigne;

    @Column(name = "observations_cgea", columnDefinition = "TEXT")
    private String observationsCgea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cgea_id")
    private Agent agentCgea;

    @Column(name = "date_imputation")
    private Instant dateImputation;

    // ── Section 4 : Suivi et traçabilité ────────────────────────────
    @Column(name = "date_retour")
    private Instant dateRetour;

    @Enumerated(EnumType.STRING)
    @Column(name = "etat_avancement", length = 20)
    private EtatAvancementAffectation etatAvancement;

    @Column(name = "etat_avancement_precision", length = 300)
    private String etatAvancementPrecision;

    @Column(name = "commentaires_suivi", columnDefinition = "TEXT")
    private String commentairesSuivi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_suivi_id")
    private Agent agentSuivi;
}
```

- [ ] **Step 4: Créer le repository**

```java
// src/main/java/gov/bf/ascelc/univers_audits/repository/FicheAffectationRepository.java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FicheAffectationRepository extends JpaRepository<FicheAffectation, UUID> {
    Optional<FicheAffectation> findByDossierId(UUID dossierId);
    boolean existsByDossierId(UUID dossierId);
}
```

- [ ] **Step 5: Écrire la migration `016`**

```sql
--liquibase formatted sql
--changeset dev:016-fiche-affectation

-- Nouvelle table pour la fiche speciale d'affectation des dossiers
-- (circuit BRPD -> Cabinet CGE -> CGEA -> departement/conseiller juridique
-- -> suivi). Voir docs/superpowers/specs/2026-09-22-fiche-affectation-design.md.
CREATE TABLE fiche_affectation (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    dossier_id                  uuid NOT NULL UNIQUE REFERENCES dossier(id),

    decision_cge                character varying(30),
    observations_cge            text,
    agent_cge_id                uuid REFERENCES agent(id),
    date_decision_cge           timestamp(6) with time zone,

    type_designation            character varying(20),
    departement_designe_id      uuid REFERENCES departement(id),
    agent_designe_id            uuid REFERENCES agent(id),
    observations_cgea           text,
    agent_cgea_id                uuid REFERENCES agent(id),
    date_imputation             timestamp(6) with time zone,

    date_retour                 timestamp(6) with time zone,
    etat_avancement             character varying(20),
    etat_avancement_precision   character varying(300),
    commentaires_suivi          text,
    agent_suivi_id               uuid REFERENCES agent(id),

    created_at                  timestamp(6) with time zone NOT NULL,
    updated_at                  timestamp(6) with time zone,
    created_by_id                character varying(100),
    updated_by_id                character varying(100),
    version                     bigint NOT NULL DEFAULT 0,

    CONSTRAINT fiche_affectation_decision_cge_check
        CHECK (decision_cge IN ('AFFECTATION_DIRECTE_CGEA','ECHANGE_PREALABLE')),
    CONSTRAINT fiche_affectation_type_designation_check
        CHECK (type_designation IN ('DEPARTEMENT','AGENT_CJ','BRPD')),
    CONSTRAINT fiche_affectation_etat_avancement_check
        CHECK (etat_avancement IN ('EN_COURS','CLOTURE','AUTRE'))
);

CREATE INDEX idx_fiche_affectation_dossier_id ON fiche_affectation(dossier_id);

-- Nouveau type de notification pour l'affectation d'un dossier (fan-out au
-- departement/agent/BRPD designe). Elargissement du CHECK constraint DANS LE
-- MEME changeset -- lecon deja documentee 2 fois dans ce depot (migrations
-- 013, 014) : ne jamais separer l'ajout d'une valeur d'enum NotificationType
-- de l'elargissement du CHECK constraint SQL correspondant.
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN (
        'RECEIPT_B4','ACKNOWLEDGMENT_B5','COMPLEMENT_REQUEST',
        'INADMISSIBILITY_DECISION','TRANSFER_DECISION','FINAL_DECISION',
        'DEADLINE_ALERT','INTERNAL_ALERT','STATUS_UPDATE',
        'INVESTIGATION_ALERT','INVESTIGATION_ASSIGNMENT',
        'DEADLINE_ALERT_J3','COMPLEMENT_ALERT_J3','INVESTIGATION_ALERT_J3',
        'DEMANDE_DOCUMENTS_ALERT','DEMANDE_DOCUMENTS_ALERT_J3',
        'ESCALADE_AR','ESCALADE_COMPLEMENT','ESCALADE_INVESTIGATION',
        'ESCALADE_DEMANDE_DOCUMENTS','AFFECTATION_DOSSIER'));
```

Avant d'écrire ce fichier, vérifier qu'aucune migration `016-*.sql` n'a été
ajoutée entretemps par un autre travail sur ce dépôt
(`ls src/main/resources/db/changelog/migrations/ | sort | tail -5`) — sinon
utiliser le numéro suivant réellement disponible et l'appliquer partout dans
ce plan (nom de fichier, `--changeset dev:NNN-...`).

- [ ] **Step 6: Vérifier que le contexte Spring démarre et que la suite complète passe**

Run: `mvn test`
Expected: BUILD SUCCESS — `ddl-auto=validate` doit valider `FicheAffectation`
contre la table `fiche_affectation` fraîchement créée, et la suite complète
existante doit rester verte (aucune régression sur le CHECK constraint
`notification_type_check` élargi).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/DecisionCgeAffectation.java \
        src/main/java/gov/bf/ascelc/univers_audits/enums/TypeDesignation.java \
        src/main/java/gov/bf/ascelc/univers_audits/enums/EtatAvancementAffectation.java \
        src/main/java/gov/bf/ascelc/univers_audits/enums/NotificationType.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/FicheAffectation.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/FicheAffectationRepository.java \
        src/main/resources/db/changelog/migrations/016-fiche-affectation.sql
git commit -m "feat(fiche-affectation): entité, migration et repository"
```

---

### Task 2: DTOs (request/response)

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationCreateRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationAffectationRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationSuiviRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/FicheAffectationResponse.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationRequestsTest.java`

**Interfaces:**
- Consumes: `FicheAffectation` entité (Task 1), `DecisionCgeAffectation`,
  `TypeDesignation`, `EtatAvancementAffectation` (Task 1).
- Produces: les 4 classes DTO ci-dessous, avec exactement ces noms de champs
  — consommées telles quelles par le service (Task 4/5/6) et le controller
  (Task 7).

- [ ] **Step 1: Écrire le test de construction des DTO (garde-fou Bean Validation)**

```java
// src/test/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationRequestsTest.java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation;
import gov.bf.ascelc.univers_audits.enums.TypeDesignation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FicheAffectationRequestsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequest_rejetteDecisionCgeManquante() {
        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder().build();
        Set<ConstraintViolation<FicheAffectationCreateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void affectationRequest_rejetteTypeDesignationManquant() {
        FicheAffectationAffectationRequest request = FicheAffectationAffectationRequest.builder().build();
        Set<ConstraintViolation<FicheAffectationAffectationRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void suiviRequest_rejetteEtatAvancementManquant() {
        FicheAffectationSuiviRequest request = FicheAffectationSuiviRequest.builder().build();
        Set<ConstraintViolation<FicheAffectationSuiviRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void requests_acceptentDesValeursValides() {
        FicheAffectationCreateRequest create = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.AFFECTATION_DIRECTE_CGEA)
                .build();
        assertThat(validator.validate(create)).isEmpty();

        FicheAffectationAffectationRequest affectation = FicheAffectationAffectationRequest.builder()
                .typeDesignation(TypeDesignation.BRPD)
                .build();
        assertThat(validator.validate(affectation)).isEmpty();

        FicheAffectationSuiviRequest suivi = FicheAffectationSuiviRequest.builder()
                .etatAvancement(EtatAvancementAffectation.EN_COURS)
                .build();
        assertThat(validator.validate(suivi)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=FicheAffectationRequestsTest`
Expected: FAIL avec des erreurs de compilation (les classes DTO n'existent pas encore).

- [ ] **Step 3: Créer les 4 classes DTO**

```java
// src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationCreateRequest.java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheAffectationCreateRequest {

    @NotNull
    private DecisionCgeAffectation decisionCge;

    private String observationsCge;
}
```

```java
// src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationAffectationRequest.java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.TypeDesignation;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheAffectationAffectationRequest {

    @NotNull
    private TypeDesignation typeDesignation;

    /** Requis si typeDesignation = DEPARTEMENT (code DEI ou DAC uniquement). */
    private UUID departementDesigneId;

    /** Requis si typeDesignation = AGENT_CJ (agent actif, rôle CONSEILLER_JURIDIQUE). */
    private UUID agentDesigneId;

    private String observationsCgea;
}
```

```java
// src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationSuiviRequest.java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheAffectationSuiviRequest {

    @NotNull
    private EtatAvancementAffectation etatAvancement;

    /** Requis si etatAvancement = AUTRE. */
    private String etatAvancementPrecision;

    private String commentairesSuivi;
}
```

```java
// src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/FicheAffectationResponse.java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation;
import gov.bf.ascelc.univers_audits.enums.TypeDesignation;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheAffectationResponse {

    private UUID id;
    private UUID dossierId;

    private DecisionCgeAffectation decisionCge;
    private String observationsCge;
    private String agentCgeNom;
    private Instant dateDecisionCge;

    private TypeDesignation typeDesignation;
    private UUID departementDesigneId;
    private String departementDesigneLibelle;
    private UUID agentDesigneId;
    private String agentDesigneNom;
    private String observationsCgea;
    private String agentCgeaNom;
    private Instant dateImputation;

    private Instant dateRetour;
    private EtatAvancementAffectation etatAvancement;
    private String etatAvancementPrecision;
    private String commentairesSuivi;
    private String agentSuiviNom;

    private Instant createdAt;
    private Instant updatedAt;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=FicheAffectationRequestsTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationCreateRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationAffectationRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationSuiviRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/FicheAffectationResponse.java \
        src/test/java/gov/bf/ascelc/univers_audits/model/dto/request/FicheAffectationRequestsTest.java
git commit -m "feat(fiche-affectation): DTOs request/response"
```

---

### Task 3: Liste des agents par rôle Keycloak (`AgentService`/`AgentController`)

Nécessaire pour que le CGEA puisse sélectionner un conseiller juridique
nommé (section 3, cas `AGENT_CJ`) — endpoint générique réutilisable, pas
câblé en dur à `CONSEILLER_JURIDIQUE`.

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/AgentService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/AgentController.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/AgentServiceTest.java`

**Interfaces:**
- Consumes: `KeycloakAdminService.getUserIdsByRole(String): List<String>`
  (existant, `src/main/java/gov/bf/ascelc/univers_audits/service/KeycloakAdminService.java:216`),
  `AgentRepository.findByKeycloakId(String): Optional<Agent>` (existant).
- Produces: `AgentService.findActiveByKeycloakRole(String roleName): List<Agent>`
  — consommé par le frontend via `GET /api/v1/agents/by-role/{roleName}`
  (pas directement par le backend `FicheAffectationService`, qui valide
  l'agent désigné différemment, voir Task 5).

- [ ] **Step 1: Écrire le test (aucun fichier `AgentServiceTest.java` n'existe encore)**

```java
// src/test/java/gov/bf/ascelc/univers_audits/service/AgentServiceTest.java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;
    @Mock
    private KeycloakAdminService keycloakAdminService;

    @InjectMocks
    private AgentService service;

    @Test
    void findActiveByKeycloakRole_neRetourneQueLesAgentsActifsRetrouvesEnBase() {
        Agent actif = Agent.builder().keycloakId("kc-1").actif(true).build();

        when(keycloakAdminService.getUserIdsByRole("CONSEILLER_JURIDIQUE"))
                .thenReturn(List.of("kc-1", "kc-2", "kc-3"));
        when(agentRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(actif));
        // kc-2 : Keycloak connaît le user mais aucun Agent correspondant en base
        when(agentRepository.findByKeycloakId("kc-2")).thenReturn(Optional.empty());
        // kc-3 : Agent trouvé mais inactif
        when(agentRepository.findByKeycloakId("kc-3"))
                .thenReturn(Optional.of(Agent.builder().keycloakId("kc-3").actif(false).build()));

        List<Agent> result = service.findActiveByKeycloakRole("CONSEILLER_JURIDIQUE");

        assertThat(result).containsExactly(actif);
    }

    @Test
    void findActiveByKeycloakRole_retourneListeVideSiAucunMembre() {
        when(keycloakAdminService.getUserIdsByRole("ROLE_INCONNU")).thenReturn(List.of());

        List<Agent> result = service.findActiveByKeycloakRole("ROLE_INCONNU");

        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AgentServiceTest`
Expected: FAIL — `findActiveByKeycloakRole` n'existe pas encore sur `AgentService`.

- [ ] **Step 3: Ajouter la méthode à `AgentService`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/service/AgentService.java`,
ajouter l'import `java.util.ArrayList` et la méthode suivante (par exemple
juste après `getAgentKeycloakRoles`) :

```java
public List<Agent> findActiveByKeycloakRole(String roleName) {
    List<Agent> result = new ArrayList<>();
    for (String keycloakId : keycloakAdminService.getUserIdsByRole(roleName)) {
        agentRepository.findByKeycloakId(keycloakId)
                .filter(Agent::getActif)
                .ifPresent(result::add);
    }
    return result;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AgentServiceTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Ajouter l'endpoint au controller**

Dans `src/main/java/gov/bf/ascelc/univers_audits/controller/AgentController.java`,
ajouter après `getAvailableRoles` :

```java
@GetMapping("/by-role/{roleName}")
@PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
public ResponseEntity<List<AgentSummaryResponse>> findByRole(@PathVariable String roleName) {
    return ResponseEntity.ok(
            agentService.findActiveByKeycloakRole(roleName).stream()
                    .map(agentMapper::toSummaryResponse)
                    .toList()
    );
}
```

- [ ] **Step 6: Run full suite**

Run: `mvn test`
Expected: BUILD SUCCESS, aucune régression.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/AgentService.java \
        src/main/java/gov/bf/ascelc/univers_audits/controller/AgentController.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/AgentServiceTest.java
git commit -m "feat(agents): endpoint de recherche par rôle Keycloak"
```

---

### Task 4: `FicheAffectationService` — création (section 2) et lecture

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/FicheAffectationService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/FicheAffectationServiceTest.java`

**Interfaces:**
- Consumes: `FicheAffectationRepository` (Task 1), `DossierRepository`
  (existant, méthode standard `findById`), `DossierAccessGuard.checkReadAccess(Dossier)`
  (existant, `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuard.java:44`),
  `SecurityUtils.getCurrentKeycloakId(): Optional<String>` (existant),
  `AgentRepository.findByKeycloakId(String): Optional<Agent>` (existant),
  `FicheAffectationCreateRequest` (Task 2).
- Produces: `FicheAffectationService.creer(UUID, FicheAffectationCreateRequest): FicheAffectation`,
  `FicheAffectationService.getFicheOrThrow(UUID): FicheAffectation` (privée,
  réutilisée en Task 5/6), `getCurrentAgentOrThrow(): Agent` (privée,
  réutilisée en Task 5/6) — consommées par `FicheAffectationController` (Task 7).

- [ ] **Step 1: Écrire les tests**

```java
// src/test/java/gov/bf/ascelc/univers_audits/service/FicheAffectationServiceTest.java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationCreateRequest;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DepartementRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.FicheAffectationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
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
class FicheAffectationServiceTest {

    @Mock private FicheAffectationRepository ficheAffectationRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private DepartementRepository departementRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PortalConfigService portalConfigService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private SecurityUtils securityUtils;
    @Mock private DossierAccessGuard accessGuard;

    @InjectMocks
    private FicheAffectationService service;

    private UUID dossierId;
    private Dossier dossier;
    private Agent agentCourant;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        dossier = Dossier.builder().id(dossierId).number("ASCE-2026-001").build();
        agentCourant = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-cge").actif(true).build();
    }

    private void stubAgentCourant() {
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(Optional.of(agentCourant));
    }

    @Test
    void creer_construitLaFicheAvecLaSection2() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(ficheAffectationRepository.existsByDossierId(dossierId)).thenReturn(false);
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.ECHANGE_PREALABLE)
                .observationsCge("À rediscuter avec le CGEA")
                .build();

        FicheAffectation result = service.creer(dossierId, request);

        assertThat(result.getDossier()).isEqualTo(dossier);
        assertThat(result.getDecisionCge()).isEqualTo(DecisionCgeAffectation.ECHANGE_PREALABLE);
        assertThat(result.getObservationsCge()).isEqualTo("À rediscuter avec le CGEA");
        assertThat(result.getAgentCge()).isEqualTo(agentCourant);
        assertThat(result.getDateDecisionCge()).isNotNull();
        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void creer_rejetteSiUneFicheExisteDejaPourCeDossier() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(ficheAffectationRepository.existsByDossierId(dossierId)).thenReturn(true);

        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.AFFECTATION_DIRECTE_CGEA)
                .build();

        assertThatThrownBy(() -> service.creer(dossierId, request))
                .isInstanceOf(ConflictException.class);
        verify(ficheAffectationRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiDossierIntrouvable() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.empty());

        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.AFFECTATION_DIRECTE_CGEA)
                .build();

        assertThatThrownBy(() -> service.creer(dossierId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void creer_rejetteSiAgentCourantIntrouvable() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(ficheAffectationRepository.existsByDossierId(dossierId)).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());

        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.AFFECTATION_DIRECTE_CGEA)
                .build();

        assertThatThrownBy(() -> service.creer(dossierId, request))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=FicheAffectationServiceTest`
Expected: FAIL — `FicheAffectationService` n'existe pas encore.

- [ ] **Step 3: Écrire le squelette du service (méthodes de Task 4 uniquement)**

```java
// src/main/java/gov/bf/ascelc/univers_audits/service/FicheAffectationService.java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationCreateRequest;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DepartementRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.FicheAffectationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FicheAffectationService {

    private final FicheAffectationRepository ficheAffectationRepository;
    private final DossierRepository dossierRepository;
    private final DepartementRepository departementRepository;
    private final AgentRepository agentRepository;
    private final NotificationRepository notificationRepository;
    private final PortalConfigService portalConfigService;
    private final KeycloakAdminService keycloakAdminService;
    private final SecurityUtils securityUtils;
    private final DossierAccessGuard accessGuard;

    @Transactional
    public FicheAffectation creer(UUID dossierId, FicheAffectationCreateRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        if (ficheAffectationRepository.existsByDossierId(dossierId)) {
            throw new ConflictException(
                    "Une fiche d'affectation existe déjà pour ce dossier : " + dossierId);
        }

        Agent agentCge = getCurrentAgentOrThrow();

        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .decisionCge(request.getDecisionCge())
                .observationsCge(request.getObservationsCge())
                .agentCge(agentCge)
                .dateDecisionCge(Instant.now())
                .build();

        FicheAffectation saved = ficheAffectationRepository.save(fiche);
        log.info("Fiche d'affectation créée — dossier: {}", dossierId);
        return saved;
    }

    private Agent getCurrentAgentOrThrow() {
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        return agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));
    }

    private FicheAffectation getFicheOrThrow(UUID dossierId) {
        return ficheAffectationRepository.findByDossierId(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune fiche d'affectation n'existe pour ce dossier : " + dossierId));
    }

    private Dossier getDossierOrThrow(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));
    }
}
```

**Note pour l'implémenteur** : les champs `departementRepository`,
`notificationRepository`, `portalConfigService`, `keycloakAdminService`
sont injectés dès cette étape mais **pas encore utilisés** — ils le seront
dans les Tasks 5/6 de ce même fichier. Un warning de compilation "champ non
utilisé" est attendu et normal à ce stade ; ne pas les supprimer.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=FicheAffectationServiceTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/FicheAffectationService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/FicheAffectationServiceTest.java
git commit -m "feat(fiche-affectation): service — création (section 2)"
```

---

### Task 5: `FicheAffectationService` — affectation CGEA (section 3) + notification + templates

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/FicheAffectationService.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/FicheAffectationServiceTest.java`
- Create: `src/main/resources/db/changelog/migrations/017-seed-notification-templates-affectation.sql`

**Interfaces:**
- Consumes: `PortalConfigService.resolveNotificationText(String key, Map<String,String> placeholders): String`
  (existant, déjà utilisé dans `NotificationServiceImpl`), `KeycloakAdminService.getUserIdsByRole(String): List<String>`
  (existant, Task précédente du chantier escalade), `Departement.getAgents(): List<Agent>`
  (existant, relation `@OneToMany` déjà chargée), `NotificationRepository.save(Notification)`
  (existant), `Agent.getKeycloakId()`/`getNomComplet()` (existants).
- Produces: `FicheAffectationService.affecter(UUID, FicheAffectationAffectationRequest): FicheAffectation`
  — consommée par `FicheAffectationController` (Task 7).

- [ ] **Step 1: Écrire la migration `017` (templates de notification)**

```sql
--liquibase formatted sql
--changeset dev:017-seed-notification-templates-affectation

-- Modele de texte pour la notification d'affectation d'un dossier
-- (NotificationType.AFFECTATION_DOSSIER, ajoute en migration 016).
-- Voir docs/superpowers/specs/2026-09-22-fiche-affectation-design.md.
INSERT INTO portal_config
(
    id, config_key, config_value, label, description,
    value_type, group_name, updated_at, version
)
VALUES
(gen_random_uuid(), 'notif_subject_affectation_dossier',
 'Dossier {numero} affecté — {departementOuAgent}',
 'Affectation dossier — sujet',
 'Notification envoyée au département/agent désigné quand le CGEA valide l''affectation d''un dossier. Utilisez {numero} pour le numéro du dossier et {departementOuAgent} pour le libellé du destinataire (département ou agent nommé).',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_affectation_dossier',
 'Le dossier {numero} vous a été affecté ({departementOuAgent}) suite à la décision du CGEA. Merci de le prendre en charge.',
 'Affectation dossier — contenu',
 'Notification envoyée au département/agent désigné quand le CGEA valide l''affectation d''un dossier. Utilisez {numero} pour le numéro du dossier et {departementOuAgent} pour le libellé du destinataire (département ou agent nommé).',
 'TEXT', 'NOTIFICATIONS', now(), 0);
```

Comme pour la Task 1, vérifier avant d'écrire que `017-*.sql` est bien le
prochain numéro libre.

- [ ] **Step 2: Ajouter les tests de `affecter()`**

Ajouter à `FicheAffectationServiceTest.java` (après les tests de `creer`) :

```java
    @Test
    void affecter_departementEligible_metAJourLaFicheEtNotifieLesAgentsDuDepartement() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        UUID departementId = UUID.randomUUID();
        Agent agentDep1 = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-dep-1").actif(true).build();
        Agent agentDep2Inactif = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-dep-2").actif(false).build();
        gov.bf.ascelc.univers_audits.model.entity.Departement dei =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(departementId).code("DEI").libelle("Enquête et Investigation")
                        .agents(java.util.List.of(agentDep1, agentDep2Inactif))
                        .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(departementRepository.findById(departementId)).thenReturn(Optional.of(dei));
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(portalConfigService.resolveNotificationText(eq("notif_subject_affectation_dossier"), any()))
                .thenReturn("sujet");
        when(portalConfigService.resolveNotificationText(eq("notif_content_affectation_dossier"), any()))
                .thenReturn("contenu");

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                        .departementDesigneId(departementId)
                        .build();

        FicheAffectation result = service.affecter(dossierId, request);

        assertThat(result.getDepartementDesigne()).isEqualTo(dei);
        assertThat(result.getAgentCgea()).isEqualTo(agentCourant);
        assertThat(result.getDateImputation()).isNotNull();
        // Un seul agent actif dans le département -> une seule notification créée
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void affecter_departementNonEligible_estRejete() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        UUID departementId = UUID.randomUUID();
        gov.bf.ascelc.univers_audits.model.entity.Departement dsi =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(departementId).code("DSI").libelle("Systèmes d'information")
                        .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(departementRepository.findById(departementId)).thenReturn(Optional.of(dsi));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                        .departementDesigneId(departementId)
                        .build();

        assertThatThrownBy(() -> service.affecter(dossierId, request))
                .isInstanceOf(BusinessException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void affecter_agentCjValideEtActif_estAccepteEtNotifieSeul() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        UUID agentCjId = UUID.randomUUID();
        Agent conseiller = Agent.builder().id(agentCjId).keycloakId("kc-cj").actif(true).build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(agentRepository.findById(agentCjId)).thenReturn(Optional.of(conseiller));
        when(keycloakAdminService.getUserRoles("kc-cj")).thenReturn(java.util.List.of("CONSEILLER_JURIDIQUE"));
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(portalConfigService.resolveNotificationText(anyString(), any())).thenReturn("texte");

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.AGENT_CJ)
                        .agentDesigneId(agentCjId)
                        .build();

        FicheAffectation result = service.affecter(dossierId, request);

        assertThat(result.getAgentDesigne()).isEqualTo(conseiller);
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void affecter_agentSansRoleConseillerJuridique_estRejete() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        UUID agentId = UUID.randomUUID();
        Agent autreAgent = Agent.builder().id(agentId).keycloakId("kc-autre").actif(true).build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(autreAgent));
        when(keycloakAdminService.getUserRoles("kc-autre")).thenReturn(java.util.List.of("AGENT_BRPD"));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.AGENT_CJ)
                        .agentDesigneId(agentId)
                        .build();

        assertThatThrownBy(() -> service.affecter(dossierId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void affecter_brpd_resoutLesDestinatairesViaKeycloakEtNeCibleAucuneEntiteLocale() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        Agent agentBrpd = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-brpd").actif(true).build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(keycloakAdminService.getUserIdsByRole("AGENT_BRPD")).thenReturn(java.util.List.of("kc-brpd"));
        when(agentRepository.findByKeycloakId("kc-brpd")).thenReturn(Optional.of(agentBrpd));
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(portalConfigService.resolveNotificationText(anyString(), any())).thenReturn("texte");

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                        .build();

        FicheAffectation result = service.affecter(dossierId, request);

        assertThat(result.getTypeDesignation()).isEqualTo(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD);
        assertThat(result.getDepartementDesigne()).isNull();
        assertThat(result.getAgentDesigne()).isNull();
        verify(notificationRepository, times(1)).save(any());
    }
```

Ajouter les imports statiques manquants en tête de fichier :
`import static org.mockito.ArgumentMatchers.anyString;` et
`import static org.mockito.ArgumentMatchers.eq;`.

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=FicheAffectationServiceTest`
Expected: FAIL — `service.affecter(...)` n'existe pas encore.

- [ ] **Step 4: Implémenter `affecter()` et ses méthodes privées de résolution**

Ajouter à `FicheAffectationService.java` (après `creer`), ainsi que les
imports `java.util.ArrayList`, `java.util.List`, `java.util.Map`,
`gov.bf.ascelc.univers_audits.enums.TypeDesignation`,
`gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest`,
`gov.bf.ascelc.univers_audits.model.entity.Departement`,
`gov.bf.ascelc.univers_audits.model.entity.Notification`,
`gov.bf.ascelc.univers_audits.enums.NotificationChannel`,
`gov.bf.ascelc.univers_audits.enums.NotificationType`,
`java.util.Set` :

```java
    private static final java.util.Set<String> DEPARTEMENTS_ELIGIBLES = java.util.Set.of("DEI", "DAC");

    @Transactional
    public FicheAffectation affecter(UUID dossierId, FicheAffectationAffectationRequest request) {
        FicheAffectation fiche = getFicheOrThrow(dossierId);
        accessGuard.checkReadAccess(fiche.getDossier());

        List<Agent> destinataires = resolveDestinataires(request);

        fiche.setTypeDesignation(request.getTypeDesignation());
        fiche.setDepartementDesigne(
                request.getTypeDesignation() == TypeDesignation.DEPARTEMENT
                        ? getDepartementEligibleOrThrow(request.getDepartementDesigneId())
                        : null);
        fiche.setAgentDesigne(
                request.getTypeDesignation() == TypeDesignation.AGENT_CJ
                        ? getConseillerJuridiqueOrThrow(request.getAgentDesigneId())
                        : null);
        fiche.setObservationsCgea(request.getObservationsCgea());
        fiche.setAgentCgea(getCurrentAgentOrThrow());
        fiche.setDateImputation(Instant.now());

        FicheAffectation saved = ficheAffectationRepository.save(fiche);
        notifierDestinataires(saved, destinataires);
        log.info("Fiche d'affectation renseignée (section CGEA) — dossier: {}, type: {}",
                dossierId, request.getTypeDesignation());
        return saved;
    }

    private List<Agent> resolveDestinataires(FicheAffectationAffectationRequest request) {
        return switch (request.getTypeDesignation()) {
            case DEPARTEMENT -> getDepartementEligibleOrThrow(request.getDepartementDesigneId())
                    .getAgents().stream().filter(Agent::getActif).toList();
            case AGENT_CJ -> List.of(getConseillerJuridiqueOrThrow(request.getAgentDesigneId()));
            case BRPD -> resolveActiveAgentsByRole("AGENT_BRPD");
        };
    }

    private void notifierDestinataires(FicheAffectation fiche, List<Agent> destinataires) {
        if (destinataires.isEmpty()) {
            log.warn("[FicheAffectation] Aucun destinataire résolu pour la notification — dossier: {}",
                    fiche.getDossier().getId());
            return;
        }
        Map<String, String> placeholders = Map.of(
                "numero", fiche.getDossier().getNumber(),
                "departementOuAgent", libelleDesignation(fiche));
        String subject = portalConfigService.resolveNotificationText(
                "notif_subject_affectation_dossier", placeholders);
        String content = portalConfigService.resolveNotificationText(
                "notif_content_affectation_dossier", placeholders);

        for (Agent destinataire : destinataires) {
            Notification notification = Notification.builder()
                    .dossier(fiche.getDossier())
                    .type(NotificationType.AFFECTATION_DOSSIER)
                    .channel(NotificationChannel.PORTAL)
                    .recipient(destinataire.getKeycloakId())
                    .subject(subject)
                    .content(content)
                    .scheduledAt(Instant.now())
                    .build();
            notificationRepository.save(notification);
        }
        log.info("[FicheAffectation] Notification d'affectation envoyée — dossier: {}, destinataires: {}",
                fiche.getDossier().getId(), destinataires.size());
    }

    private String libelleDesignation(FicheAffectation fiche) {
        return switch (fiche.getTypeDesignation()) {
            case DEPARTEMENT -> "Département " + fiche.getDepartementDesigne().getLibelle();
            case AGENT_CJ -> fiche.getAgentDesigne().getNomComplet() + " (Conseiller Juridique)";
            case BRPD -> "BRPD";
        };
    }

    private List<Agent> resolveActiveAgentsByRole(String roleName) {
        List<Agent> agents = new ArrayList<>();
        for (String keycloakId : keycloakAdminService.getUserIdsByRole(roleName)) {
            agentRepository.findByKeycloakId(keycloakId)
                    .filter(Agent::getActif)
                    .ifPresent(agents::add);
        }
        return agents;
    }

    private Departement getDepartementEligibleOrThrow(UUID departementId) {
        if (departementId == null) {
            throw new BusinessException(
                    "Le département désigné est requis quand le type de désignation est DEPARTEMENT.");
        }
        Departement departement = departementRepository.findById(departementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Département introuvable : " + departementId));
        if (!DEPARTEMENTS_ELIGIBLES.contains(departement.getCode())) {
            throw new BusinessException(
                    "Seuls les départements DEI et DAC peuvent être désignés via la fiche d'affectation, reçu : "
                            + departement.getCode());
        }
        return departement;
    }

    private Agent getConseillerJuridiqueOrThrow(UUID agentId) {
        if (agentId == null) {
            throw new BusinessException(
                    "L'agent désigné est requis quand le type de désignation est AGENT_CJ.");
        }
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent introuvable : " + agentId));
        if (!Boolean.TRUE.equals(agent.getActif())) {
            throw new BusinessException("L'agent désigné doit être actif : " + agentId);
        }
        if (agent.getKeycloakId() == null
                || !keycloakAdminService.getUserRoles(agent.getKeycloakId()).contains("CONSEILLER_JURIDIQUE")) {
            throw new BusinessException(
                    "L'agent désigné doit avoir le rôle Conseiller Juridique : " + agentId);
        }
        return agent;
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=FicheAffectationServiceTest`
Expected: PASS (9 tests)

- [ ] **Step 6: Run full suite (migration + CHECK constraint)**

Run: `mvn test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/FicheAffectationService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/FicheAffectationServiceTest.java \
        src/main/resources/db/changelog/migrations/017-seed-notification-templates-affectation.sql
git commit -m "feat(fiche-affectation): service — affectation CGEA (section 3) et notification"
```

---

### Task 6: `FicheAffectationService` — suivi (section 4) et lecture avec autorisation dynamique

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/FicheAffectationService.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/FicheAffectationServiceTest.java`

**Interfaces:**
- Consumes: `DossierAccessGuard.canSeeConfidential(): boolean` (existant,
  `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/DossierAccessGuard.java:37`),
  `SecurityUtils.hasRole(String): boolean` (existant), `Agent.getDepartement(): Departement`
  (existant, relation déjà présente sur `Agent`).
- Produces: `FicheAffectationService.suivre(UUID, FicheAffectationSuiviRequest): FicheAffectation`,
  `FicheAffectationService.getOrThrow(UUID): FicheAffectation` — consommées
  par `FicheAffectationController` (Task 7).

- [ ] **Step 1: Ajouter les tests**

Ajouter à `FicheAffectationServiceTest.java` :

```java
    @Test
    void suivre_agentDuDepartementDesigne_estAutorise() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(UUID.randomUUID()).code("DEI").build();
        Agent agentDuDepartement = Agent.builder()
                .id(UUID.randomUUID()).keycloakId("kc-dep")
                .departement(dei).build();
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                .departementDesigne(dei)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-dep"));
        when(agentRepository.findByKeycloakId("kc-dep")).thenReturn(Optional.of(agentDuDepartement));
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.CLOTURE)
                        .commentairesSuivi("Investigation menée à son terme")
                        .build();

        FicheAffectation result = service.suivre(dossierId, request);

        assertThat(result.getEtatAvancement())
                .isEqualTo(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.CLOTURE);
        assertThat(result.getAgentSuivi()).isEqualTo(agentDuDepartement);
        assertThat(result.getDateRetour()).isNotNull();
    }

    @Test
    void suivre_agentDunAutreDepartement_estRejete() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(UUID.randomUUID()).code("DEI").build();
        gov.bf.ascelc.univers_audits.model.entity.Departement dac =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(UUID.randomUUID()).code("DAC").build();
        Agent agentDac = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-dac").departement(dac).build();
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                .departementDesigne(dei)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-dac"));
        when(agentRepository.findByKeycloakId("kc-dac")).thenReturn(Optional.of(agentDac));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        assertThatThrownBy(() -> service.suivre(dossierId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void suivre_agentDesigneNommeHorsDepartement_estAutorise() {
        Agent conseillerDesigne = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-cj").build();
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.AGENT_CJ)
                .agentDesigne(conseillerDesigne)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-cj"));
        when(agentRepository.findByKeycloakId("kc-cj")).thenReturn(Optional.of(conseillerDesigne));
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        FicheAffectation result = service.suivre(dossierId, request);

        assertThat(result.getAgentSuivi()).isEqualTo(conseillerDesigne);
    }

    @Test
    void suivre_roleAgentBrpd_estAutoriseQuandBrpdDesigne() {
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                .build();
        Agent agentBrpd = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-brpd").build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-brpd"));
        when(agentRepository.findByKeycloakId("kc-brpd")).thenReturn(Optional.of(agentBrpd));
        when(securityUtils.hasRole("AGENT_BRPD")).thenReturn(true);
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        FicheAffectation result = service.suivre(dossierId, request);

        assertThat(result.getAgentSuivi()).isEqualTo(agentBrpd);
    }

    @Test
    void suivre_roleprivilegie_estToujoursAutorise() {
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(true);
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        FicheAffectation result = service.suivre(dossierId, request);

        assertThat(result).isNotNull();
    }

    @Test
    void suivre_etatAutreSansPrecision_estRejete() {
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(true);

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.AUTRE)
                        .build();

        assertThatThrownBy(() -> service.suivre(dossierId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void suivre_sectionCgeaPasEncoreRenseignee_estRejetePourNonPrivilegie() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build(); // typeDesignation == null

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        assertThatThrownBy(() -> service.suivre(dossierId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getOrThrow_appliqueLaMemeAutorisationDynamiqueQueSuivre() {
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.hasRole("AGENT_BRPD")).thenReturn(false);

        assertThatThrownBy(() -> service.getOrThrow(dossierId))
                .isInstanceOf(BusinessException.class);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=FicheAffectationServiceTest`
Expected: FAIL — `suivre`/`getOrThrow` n'existent pas encore.

- [ ] **Step 3: Implémenter `suivre()`, `getOrThrow()` et `checkSuiviAccess()`**

Ajouter à `FicheAffectationService.java` (après `affecter` et ses méthodes
privées), avec l'import
`gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest`
et `gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation` :

```java
    @Transactional
    public FicheAffectation suivre(UUID dossierId, FicheAffectationSuiviRequest request) {
        FicheAffectation fiche = getFicheOrThrow(dossierId);
        checkSuiviAccess(fiche);

        if (request.getEtatAvancement() == EtatAvancementAffectation.AUTRE
                && (request.getEtatAvancementPrecision() == null
                    || request.getEtatAvancementPrecision().isBlank())) {
            throw new BusinessException(
                    "Une précision est requise quand l'état d'avancement est \"Autre\".");
        }

        fiche.setDateRetour(Instant.now());
        fiche.setEtatAvancement(request.getEtatAvancement());
        fiche.setEtatAvancementPrecision(request.getEtatAvancementPrecision());
        fiche.setCommentairesSuivi(request.getCommentairesSuivi());
        fiche.setAgentSuivi(getCurrentAgentOrThrow());

        FicheAffectation saved = ficheAffectationRepository.save(fiche);
        log.info("Fiche d'affectation — suivi renseigné — dossier: {}, état: {}",
                dossierId, request.getEtatAvancement());
        return saved;
    }

    public FicheAffectation getOrThrow(UUID dossierId) {
        FicheAffectation fiche = getFicheOrThrow(dossierId);
        checkSuiviAccess(fiche);
        return fiche;
    }

    private void checkSuiviAccess(FicheAffectation fiche) {
        if (accessGuard.canSeeConfidential()) {
            return; // CGE/CGEA/ADMIN_DDIC : accès toujours autorisé
        }
        if (fiche.getTypeDesignation() == null) {
            throw new BusinessException(
                    "Accès refusé — la section d'affectation n'a pas encore été renseignée par le CGEA");
        }
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        Agent agent = agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));

        boolean autorise = switch (fiche.getTypeDesignation()) {
            case DEPARTEMENT -> fiche.getDepartementDesigne() != null
                    && agent.getDepartement() != null
                    && agent.getDepartement().getId().equals(fiche.getDepartementDesigne().getId());
            case AGENT_CJ -> fiche.getAgentDesigne() != null
                    && agent.getId().equals(fiche.getAgentDesigne().getId());
            case BRPD -> securityUtils.hasRole("AGENT_BRPD");
        };
        if (!autorise) {
            throw new BusinessException(
                    "Accès refusé — ce dossier ne vous a pas été affecté via la fiche d'affectation");
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=FicheAffectationServiceTest`
Expected: PASS (17 tests)

- [ ] **Step 5: Run full suite**

Run: `mvn test`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/FicheAffectationService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/FicheAffectationServiceTest.java
git commit -m "feat(fiche-affectation): service — suivi (section 4) et autorisation dynamique"
```

---

### Task 7: `FicheAffectationController`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/FicheAffectationController.java`

**Interfaces:**
- Consumes: `FicheAffectationService.creer/affecter/suivre/getOrThrow`
  (Tasks 4/5/6), `ApiUrls.DOSSIERS` (existant,
  `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/ApiUrls.java:18`).
- Produces: 4 endpoints REST, consommés par le plan frontend (voir
  `docs/superpowers/plans/2026-09-22-fiche-affectation-frontend.md`) :
  - `POST /api/v1/dossiers/{id}/fiche-affectation`
  - `PATCH /api/v1/dossiers/{id}/fiche-affectation/affectation`
  - `PATCH /api/v1/dossiers/{id}/fiche-affectation/suivi`
  - `GET /api/v1/dossiers/{id}/fiche-affectation`

Ce dépôt n'a pas de convention `@WebMvcTest`/MockMvc établie (vérifié —
aucun fichier existant n'utilise ce patron) : ce controller est un mapping
fin au-dessus d'un service déjà entièrement testé (Tasks 4-6), sans logique
propre au-delà du mapping réponse. Pas de test dédié pour cette tâche —
cohérent avec `RapportEnqueteController`, qui n'a pas non plus de test
dédié dans ce dépôt.

- [ ] **Step 1: Écrire le controller**

```java
// src/main/java/gov/bf/ascelc/univers_audits/controller/FicheAffectationController.java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.FicheAffectationResponse;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.service.FicheAffectationService;
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
@RequestMapping(ApiUrls.DOSSIERS + "/{id}/fiche-affectation")
public class FicheAffectationController {

    private final FicheAffectationService ficheAffectationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<FicheAffectationResponse> creer(
            @PathVariable UUID id,
            @Valid @RequestBody FicheAffectationCreateRequest request) {

        log.info("Création fiche d'affectation — dossier {}", id);
        FicheAffectation fiche = ficheAffectationService.creer(id, request);
        return ResponseEntity.status(201).body(toResponse(fiche));
    }

    @PatchMapping("/affectation")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<FicheAffectationResponse> affecter(
            @PathVariable UUID id,
            @Valid @RequestBody FicheAffectationAffectationRequest request) {

        log.info("Affectation CGEA — dossier {}, type {}", id, request.getTypeDesignation());
        FicheAffectation fiche = ficheAffectationService.affecter(id, request);
        return ResponseEntity.ok(toResponse(fiche));
    }

    @PatchMapping("/suivi")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FicheAffectationResponse> suivre(
            @PathVariable UUID id,
            @Valid @RequestBody FicheAffectationSuiviRequest request) {

        log.info("Suivi fiche d'affectation — dossier {}, état {}", id, request.getEtatAvancement());
        FicheAffectation fiche = ficheAffectationService.suivre(id, request);
        return ResponseEntity.ok(toResponse(fiche));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FicheAffectationResponse> get(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(ficheAffectationService.getOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            // Patron identique à RapportEnqueteController.getRapport : l'absence
            // de fiche pour ce dossier est un état normal (pas encore créée par
            // le CGE), pas une erreur — 204 plutôt que 404.
            return ResponseEntity.noContent().build();
        }
    }

    private FicheAffectationResponse toResponse(FicheAffectation f) {
        return FicheAffectationResponse.builder()
                .id(f.getId())
                .dossierId(f.getDossier().getId())
                .decisionCge(f.getDecisionCge())
                .observationsCge(f.getObservationsCge())
                .agentCgeNom(f.getAgentCge() != null ? f.getAgentCge().getNomComplet() : null)
                .dateDecisionCge(f.getDateDecisionCge())
                .typeDesignation(f.getTypeDesignation())
                .departementDesigneId(f.getDepartementDesigne() != null ? f.getDepartementDesigne().getId() : null)
                .departementDesigneLibelle(f.getDepartementDesigne() != null ? f.getDepartementDesigne().getLibelle() : null)
                .agentDesigneId(f.getAgentDesigne() != null ? f.getAgentDesigne().getId() : null)
                .agentDesigneNom(f.getAgentDesigne() != null ? f.getAgentDesigne().getNomComplet() : null)
                .observationsCgea(f.getObservationsCgea())
                .agentCgeaNom(f.getAgentCgea() != null ? f.getAgentCgea().getNomComplet() : null)
                .dateImputation(f.getDateImputation())
                .dateRetour(f.getDateRetour())
                .etatAvancement(f.getEtatAvancement())
                .etatAvancementPrecision(f.getEtatAvancementPrecision())
                .commentairesSuivi(f.getCommentairesSuivi())
                .agentSuiviNom(f.getAgentSuivi() != null ? f.getAgentSuivi().getNomComplet() : null)
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 2: Run full suite**

Run: `mvn test`
Expected: BUILD SUCCESS — le contexte Spring doit démarrer avec le nouveau
controller (vérifie que le mapping des routes ne collisionne avec aucune
route existante de `DossierController`).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/FicheAffectationController.java
git commit -m "feat(fiche-affectation): controller REST (4 endpoints)"
```

---

## Vérification finale (avant revue de branche)

- [ ] `mvn test` complet — 0 échec.
- [ ] Lecture live du CHECK constraint `notification_type_check` sur la
  base Docker partagée pour confirmer qu'il contient bien
  `AFFECTATION_DOSSIER` (patron déjà établi 2 fois dans ce dépôt — voir
  `docs/superpowers/specs/2026-09-22-escalade-automatique-design.md` pour
  la requête `SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
  WHERE conname = 'notification_type_check'`).
- [ ] Relire chaque `Ruling:` consigné pendant l'exécution (numéro de
  migration réellement utilisé s'il diffère de `016`/`017`, tout écart par
  rapport à ce plan) et les reporter dans le ledger SDD.
