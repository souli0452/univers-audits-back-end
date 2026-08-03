# Décision CGE (Lot 2, sous-chantier 3/4) — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter la 4ᵉ issue manquante (`ORIENTEE_ADMINISTRATIF`) de la machine à états du
dossier, et structurer la décision CGE (aujourd'hui un simple changement de statut + texte
libre) en un enregistrement `DecisionCGE` queryable, sans casser les 3 endpoints CGE déjà en
production.

**Architecture:** Nouvelle entité `DecisionCGE` (1:1 avec `Dossier`, même style que
`EtudeOpportunite`). Nouvelle méthode de service `orientAdministratif()` miroir exact de
`declareInadmissible()`. Les 4 méthodes de décision CGE (3 existantes + 1 nouvelle) créent
chacune un `DecisionCGE`, en plus du changement de statut déjà en place — aucun changement de
contrat API sur les 3 endpoints existants.

**Tech Stack:** Spring Boot 3 / Java 17, MapStruct, Liquibase, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Migration Liquibase `018-add-decision-cge.sql`, table `decision_cge`, FK `dossier_id`
  `UNIQUE`. Colonnes héritées de `AuditEntity` identiques aux migrations 016/017 (`id` UUID PK,
  `version BIGINT NOT NULL DEFAULT 0`, `created_at TIMESTAMP NOT NULL`, `updated_at
  TIMESTAMP`, `created_by_id`/`updated_by_id VARCHAR(100)`).
- `DecisionCGE.decision` réutilise l'enum `RecommandationCtadp` (déjà créé au sous-chantier
  2/4) — ne pas créer de nouvel enum dupliqué.
- Aucun changement de contrat sur `declareAdmissible`/`declareInadmissible`/`transfer`
  (endpoints, rôles, corps de requête/réponse inchangés) — uniquement enrichis pour créer un
  `DecisionCGE` en plus de ce qu'ils font déjà.
- `orientAdministratif()` est un miroir exact de `declareInadmissible()` (motif obligatoire,
  même structure de notification/audit), transition vers `ORIENTEE_ADMINISTRATIF` au lieu de
  `IRRECEVABLE`.
- `close()` n'est **pas** modifié — sa condition existante couvre déjà `ORIENTEE_ADMINISTRATIF`
  génériquement.

Spec de référence : `docs/superpowers/specs/2026-08-03-decision-cge-design.md`
Document source : `docs/reference/plan-de-travail-asce-lc.md` (§6, §11)

---

### Task 1: `DossierStatus` + entité `DecisionCGE` + migration

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/enums/DossierStatus.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/DecisionCGE.java`
- Create: `src/main/resources/db/changelog/migrations/018-add-decision-cge.sql`

- [ ] **Step 1: Ajouter `ORIENTEE_ADMINISTRATIF` à `DossierStatus`**

Dans `DossierStatus.java`, ajouter la valeur d'enum juste après `TRANSFERE` :

```java
    SOUMIS,
    RECU,
    EN_ETUDE_OPPORTUNITE,
    EN_ATTENTE_COMPLEMENT,
    EN_REVUE_CTADP,
    RECEVABLE,
    IRRECEVABLE,
    TRANSFERE,
    ORIENTEE_ADMINISTRATIF,
    EN_INVESTIGATION,
    RAPPORT_PRODUIT,
    DECISION_RENDUE,
    CLOS,
    CLASSE
```

Ne pas toucher au commentaire Javadoc du workflow en tête de fichier dans cette étape — la
Task 3 (qui touche `validateTransition`) documentera la nouvelle branche là où c'est pertinent
en code, pas dans ce commentaire descriptif séparé.

- [ ] **Step 2: Créer l'entité `DecisionCGE`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
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
@Table(name = "decision_cge", indexes = {
        @Index(name = "idx_decision_cge_dossier",
                columnList = "dossier_id", unique = true)
})
public class DecisionCGE extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false, unique = true)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 40)
    private RecommandationCtadp decision;

    @Column(name = "motif", length = 2000)
    private String motif;

    @Column(name = "date_decision", nullable = false)
    private Instant dateDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cge_id", nullable = false)
    private Agent agentCGE;
}
```

- [ ] **Step 3: Écrire la migration**

```sql
--liquibase formatted sql
--changeset dev:018-add-decision-cge

CREATE TABLE decision_cge (
    id             UUID PRIMARY KEY,
    version        BIGINT NOT NULL DEFAULT 0,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP,
    created_by_id  VARCHAR(100),
    updated_by_id  VARCHAR(100),
    dossier_id     UUID NOT NULL UNIQUE REFERENCES dossier(id),
    decision       VARCHAR(40) NOT NULL,
    motif          VARCHAR(2000),
    date_decision  TIMESTAMP NOT NULL,
    agent_cge_id   UUID NOT NULL REFERENCES agent(id)
);

COMMENT ON TABLE decision_cge IS 'Decision formelle du CGE sur un dossier (Lot 2, plan de travail S6/S11) - une par dossier, reutilise l enum RecommandationCtadp du sous-chantier SeanceCTADP';
```

Table `agent` confirmée (nom exact vérifié dans `Agent.java`, `@Table(name = "agent")`).

- [ ] **Step 4: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/DossierStatus.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/DecisionCGE.java \
        src/main/resources/db/changelog/migrations/018-add-decision-cge.sql
git commit -m "feat: add ORIENTEE_ADMINISTRATIF status and DecisionCGE entity (Lot 2)"
```

---

### Task 2: DTO réponse et mapper

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DecisionCGEResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`

**Interfaces:**
- Produces: `DossierDetailsMapper.toResponse(DecisionCGE)` — utilisé par la Task 3.

- [ ] **Step 1: Créer `DecisionCGEResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionCGEResponse {

    private UUID id;
    private RecommandationCtadp decision;
    private String motif;
    private Instant dateDecision;
    private UUID agentCGEId;
    private String agentCGENom;
}
```

- [ ] **Step 2: Ajouter le mapping dans `DossierDetailsMapper`**

Suivre exactement le même patron que `toResponse(EtudeOpportunite)` déjà présent dans ce
fichier (lu avant d'écrire cette étape — wrapper `default` requis à cause du bug MapStruct/
Lombok déjà documenté en tête de ce fichier, `DecisionCGEResponse` étant un `@Builder` Lombok) :

```java
    default DecisionCGEResponse toResponse(DecisionCGE decision) {
        DecisionCGEResponse response = mapToResponse(decision);
        if (response != null) {
            fillDecisionCGE(decision, response);
        }
        return response;
    }

    @Mapping(target = "agentCGEId",  ignore = true)
    @Mapping(target = "agentCGENom", ignore = true)
    DecisionCGEResponse mapToResponse(DecisionCGE decision);

    default void fillDecisionCGE(
            DecisionCGE decisionCge,
            DecisionCGEResponse response) {
        if (decisionCge.getAgentCGE() != null) {
            response.setAgentCGEId(decisionCge.getAgentCGE().getId());
            response.setAgentCGENom(decisionCge.getAgentCGE().getNomComplet());
        }
    }
```

`Agent.getNomComplet()` existe déjà (vérifié dans `Agent.java`), pas de vérification
supplémentaire nécessaire.

- [ ] **Step 3: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS, aucun avertissement de propriété non mappée.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DecisionCGEResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java
git commit -m "feat: add DecisionCGE response DTO and mapper"
```

---

### Task 3: Repository, service, intégration `DossierServiceImpl`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/DecisionCGERepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/enums/DossierStatus.java` (déjà fait
  Task 1 — pas de nouvelle modification ici, listé pour mémoire du contexte)
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/DossierService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java`

**Interfaces:**
- Consumes: `DossierDetailsMapper.toResponse(DecisionCGE)` (Task 2).
- Produces: `DossierService.orientAdministratif(UUID, StatusTransitionRequest, String)` —
  utilisé par la Task 4 (contrôleur).

- [ ] **Step 1: Créer `DecisionCGERepository`**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.DecisionCGE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisionCGERepository extends JpaRepository<DecisionCGE, UUID> {

    Optional<DecisionCGE> findByDossierId(UUID dossierId);
}
```

- [ ] **Step 2: Ajouter la méthode à l'interface `DossierService`**

Dans `DossierService.java`, juste après `transfer(...)` :

```java
    DossierResponse orientAdministratif(UUID dossierId,
                                        StatusTransitionRequest request,
                                        String ipAddress);
```

- [ ] **Step 3: Ajouter le champ à `DossierResponse`**

Dans `DossierResponse.java`, à côté de `etudeOpportunite` :

```java
    private DecisionCGEResponse          decisionCGE;
```

- [ ] **Step 4: Étendre `validateTransition` dans `DossierServiceImpl`**

Remplacer :

```java
            case RECEVABLE, IRRECEVABLE, TRANSFERE ->
                    dossier.getStatus() == DossierStatus.EN_REVUE_CTADP;
```

par :

```java
            case RECEVABLE, IRRECEVABLE, TRANSFERE, ORIENTEE_ADMINISTRATIF ->
                    dossier.getStatus() == DossierStatus.EN_REVUE_CTADP;
```

Et remplacer :

```java
            case CLASSE ->
                    dossier.getStatus() == DossierStatus.IRRECEVABLE
                            || dossier.getStatus() == DossierStatus.TRANSFERE;
```

par :

```java
            case CLASSE ->
                    dossier.getStatus() == DossierStatus.IRRECEVABLE
                            || dossier.getStatus() == DossierStatus.TRANSFERE
                            || dossier.getStatus() == DossierStatus.ORIENTEE_ADMINISTRATIF;
```

- [ ] **Step 5: Ajouter le champ repository et la méthode privée de création**

Ajouter `private final DecisionCGERepository decisionCGERepository;` à la **toute fin** de la
liste des champs `private final ...` de `DossierServiceImpl` (même raison qu'à chaque
chantier précédent touchant ce fichier : `@RequiredArgsConstructor` dérive l'ordre du
constructeur de l'ordre des champs, et un test,
`DossierServiceImplTest.findById_succeedsForAgentWhoseOnlyHabilitationIsInvestigationTeam`,
construit `DossierServiceImpl` avec un appel de constructeur explicite listant chaque argument
dans l'ordre des champs — ajouter le nouveau champ en dernier permet de simplement ajouter le
nouveau mock en dernier argument de cet appel, sans réordonner les arguments existants).

Ajouter cette méthode privée, réutilisée par les 4 méthodes de décision :

```java
    private void recordDecisionCGE(Dossier dossier,
                                   RecommandationCtadp decision,
                                   String motif,
                                   Agent agent) {
        DecisionCGE record = decisionCGERepository.findByDossierId(dossier.getId())
                .orElseGet(() -> DecisionCGE.builder().dossier(dossier).build());
        record.setDecision(decision);
        record.setMotif(motif);
        record.setDateDecision(Instant.now());
        record.setAgentCGE(agent);
        decisionCGERepository.save(record);
    }
```

Ajouter les imports nécessaires (`gov.bf.ascelc.univers_audits.enums.RecommandationCtadp`,
`gov.bf.ascelc.univers_audits.model.entity.DecisionCGE`) en tête de fichier s'ils ne sont pas
déjà présents.

- [ ] **Step 6: Appeler `recordDecisionCGE` depuis les 3 méthodes existantes**

Dans `declareAdmissible()`, juste après le bloc `auditRecorder.addObservation(...)` existant et
avant `Dossier saved = dossierRepository.save(dossier);` :

```java
        recordDecisionCGE(dossier, RecommandationCtadp.VALIDATION_INVESTIGATION,
                request.getReason(), agent);
```

Dans `declareInadmissible()`, au même endroit relatif :

```java
        recordDecisionCGE(dossier, RecommandationCtadp.CLASSEMENT,
                request.getReason(), agent);
```

Dans `transfer()`, au même endroit relatif :

```java
        recordDecisionCGE(dossier, RecommandationCtadp.TRANSMISSION_INSTITUTION_PARTENAIRE,
                request.getReason(), agent);
```

- [ ] **Step 7: Ajouter `orientAdministratif()` — miroir exact de `declareInadmissible()`**

Juste après `transfer()` dans `DossierServiceImpl.java` :

```java
    @Override
    @Transactional
    public DossierResponse orientAdministratif(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.ORIENTEE_ADMINISTRATIF);
        Agent agent = agentContextResolver.getCurrentAgent();

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(
                    "Le motif d'orientation administrative est obligatoire");
        }

        dossier.setStatus(DossierStatus.ORIENTEE_ADMINISTRATIF);
        dossier.setEligibilityDecisionDate(Instant.now());

        auditRecorder.addObservation(dossier,
                ObservationType.CGE_DECISION,
                "Dossier orienté vers l'autorité hiérarchique (irrégularité). Motif : "
                        + request.getReason(),
                true, agent);

        recordDecisionCGE(dossier, RecommandationCtadp.ORIENTATION_ADMINISTRATIVE,
                request.getReason(), agent);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved, "ORIENTEE_ADMINISTRATIF", request.getReason());

        auditRecorder.recordStatusChange(saved,
                DossierStatus.EN_REVUE_CTADP, DossierStatus.ORIENTEE_ADMINISTRATIF,
                request.getReason(), agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }
```

- [ ] **Step 8: Peupler et masquer `decisionCGE` dans la réponse**

Dans `enrichAndMaskDetail(Dossier dossier)`, juste après le bloc `response.setEtudeOpportunite(...)`
déjà présent :

```java
        response.setDecisionCGE(
                decisionCGERepository.findByDossierId(dossier.getId())
                        .map(dossierDetailsMapper::toResponse)
                        .orElse(null));
```

Dans le bloc de masquage confidentiel de `maskSensitiveData()` (celui qui nullifie déjà
`response.setEtudeOpportunite(null)`), ajouter juste après :

```java
            response.setDecisionCGE(null);
```

- [ ] **Step 9: Tests**

Ajouter dans `DossierServiceImplTest.java` (mock `@Mock private DecisionCGERepository
decisionCGERepository;` à ajouter à la liste des mocks — et **appeler `decisionCGERepository`
en dernier argument** de l'appel de constructeur explicite mentionné à l'étape 5) :

```java
    @Test
    void orientAdministratif_succeedsWithReason() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder()
                .reason("Irrégularité administrative, hors compétence pénale").build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(decisionCGERepository.findByDossierId(dossierId)).thenReturn(Optional.empty());
        when(decisionCGERepository.save(any(DecisionCGE.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());
        when(securityUtils.hasRole(anyString())).thenReturn(false);

        service.orientAdministratif(dossierId, request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d ->
                d.getStatus() == DossierStatus.ORIENTEE_ADMINISTRATIF));
        verify(decisionCGERepository).save(argThat(dc ->
                dc.getDecision() == RecommandationCtadp.ORIENTATION_ADMINISTRATIVE
                        && dc.getDossier() == dossier));
    }

    @Test
    void orientAdministratif_rejectsWhenReasonMissing() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder().build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.orientAdministratif(dossierId, request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void orientAdministratif_rejectsWhenDossierNotInCorrectStatus() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.RECU).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder()
                .reason("Motif").build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.orientAdministratif(dossierId, request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }
```

Vérifié : `DossierServiceImplTest.java` ne contient aujourd'hui **aucun** test existant pour
`declareAdmissible`/`declareInadmissible`/`transfer` — rien à adapter de ce côté, les 3 tests
`orientAdministratif_*` ci-dessus sont les seuls nécessaires pour cette tâche.

- [ ] **Step 10: Lancer les tests**

```
mvn test -q -Dtest=DossierServiceImplTest
```

Expected: BUILD SUCCESS, y compris les 3 nouveaux tests et tout test existant déjà présent sur
`declareAdmissible`/`declareInadmissible`/`transfer`.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/repository/DecisionCGERepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/DossierService.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java
git commit -m "feat: add orientAdministratif and structured DecisionCGE recording"
```

---

### Task 4: Contrôleur — endpoint `orient-administratif`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/DossierController.java`

**Interfaces:**
- Consumes: `DossierService.orientAdministratif(...)` (Task 3).

- [ ] **Step 1: Ajouter l'endpoint**

Dans `DossierController.java`, juste après la méthode `declareInadmissible` existante, avant
`transfer` :

```java
    @PatchMapping("/{id}/orient-administratif")
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> orientAdministratif(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("CGE oriente dossier {} vers l'autorité hiérarchique (irrégularité)", id);
        DossierResponse result = dossierService.orientAdministratif(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "ORIENTER_ADMINISTRATIF", "DOSSIER", id.toString(),
                "Dossier orienté vers l'autorité hiérarchique", AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }
```

- [ ] **Step 2: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/DossierController.java
git commit -m "feat: add orient-administratif endpoint"
```

---

### Task 5: Suite complète + mise à jour du backlog

**Files:** mémoire projet `project_asce_backlog_2026_07_30.md`.

- [ ] **Step 1: Lancer la suite complète**

```
mvn test -q
```

Expected: 0 échec (l'erreur `UniversAuditsApplicationTests.contextLoads` — absence de base de
données dans cet environnement — est un baseline connu, sans rapport avec ce chantier).

- [ ] **Step 2: Mettre à jour le backlog mémoire**

Marquer le sous-chantier "DecisionCGE" comme livré (3/4 du Lot 2) et rappeler le dernier
sous-chantier restant (génération PDF de l'accusé de réception/suites à donner ou de la
réponse motivée, 4/4).
