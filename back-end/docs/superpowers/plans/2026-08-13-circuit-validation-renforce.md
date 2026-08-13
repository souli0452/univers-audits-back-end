# Circuit de validation renforcé du rapport (Lot 5, sous-chantier 3/4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Réordonner le circuit d'approbation existant du rapport d'enquête (`DEI -> Conseiller Juridique -> CGE`, 3 étapes) vers `Conseiller Juridique -> DEI -> CGEA -> CGE` (4 étapes), ajouter une capacité de rejet motivé par étape, et exposer des échéances informatives par étape.

**Architecture:** Extension in-place de `InvestigationServiceImpl`/`InvestigationController`/`Investigation` (pas de nouveau service — les 3 méthodes touchées existent déjà dans ces fichiers) : un nouveau champ `cgeaApprovedAt`/`cgeaApprovedBy` miroir des 3 champs existants, 4 méthodes d'approbation (3 réordonnées, 1 nouvelle), 3 méthodes de rejet (nouvelles, aucune pour CJ), et un calcul d'échéances par étape mutualisé dans le point d'enrichissement de réponse déjà existant (`buildResponseWithFreshMembers`).

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok (`@SuperBuilder`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-13-circuit-validation-renforce-design.md`

## Global Constraints

- Ordre du circuit : `Conseiller Juridique -> DEI -> CGEA -> CGE` (tranché par l'utilisateur, recommandation par défaut du plan de travail C.3.10).
- Aucun rejet ne modifie `InvestigationStatus` ni `DossierStatus` — un rejet efface uniquement l'horodatage/l'agent d'approbation de l'étape précédente, renvoyant le circuit un cran en arrière.
- Pas de méthode de rejet pour le Conseiller Juridique (1re étape) — aucune transition de retour n'est documentée depuis cette étape dans le plan de travail source.
- Délai CGE (10 vs 20 jours) : non tranché, valeur existante (`APPROBATION_CGE` = 20, migration `004`) **non modifiée**.
- Nouveaux codes `ParametreDelai` : `REVUE_CJ_RAPPORT` (10 jours), `ANALYSE_DEI_RAPPORT` (15 jours), `APPROBATION_CGEA_RAPPORT` (10 jours) — `jours_ouvrables=TRUE`, `actif=TRUE`.
- Rôles : `approveLegalAdvisor` = `CONSEILLER_JURIDIQUE`,`ADMIN_DDIC` (inchangé) ; `approveDei`/`approveCgea`/`rejectDei`/`rejectCgea` = `CGEA`,`ADMIN_DDIC` (même approximation DEI déjà actée ailleurs dans ce dépôt, pas de rôle DEI dédié) ; `approveCge`/`rejectCge` = `CGE`,`ADMIN_DDIC`.
- Calcul des échéances : même patron dégradé que `toPlanInvestigationResponse` (`InvestigationServiceImpl.java` ligne ~1262) — `try/catch` sur `ResourceNotFoundException`, `log.warn` + `null`, jamais de propagation. Jours calendaires (pas ouvrables — le flag `jours_ouvrables` est déjà décoratif partout ailleurs dans ce dépôt).
- Confirmé par grep (`grep -rn "approveDei\|approveLegalAdvisor\|approveCge" src/test/`) : **aucun test n'existe aujourd'hui** pour ces 3 méthodes — ce plan les teste pour la première fois, pas de test existant à casser sur ces méthodes précises.

---

### Task 1: Champ `cgeaApprovedAt`/`cgeaApprovedBy` et migration

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Investigation.java`
- Create: `src/main/resources/db/changelog/migrations/033-circuit-validation-renforce.sql`

**Interfaces:**
- Consumes: `Agent` (`gov.bf.ascelc.univers_audits.model.entity.Agent`, champ `id` existant), `AuditEntity` (déjà hérité par `Investigation`).
- Produces: `Investigation.{getCgeaApprovedAt(): Instant, setCgeaApprovedAt(Instant), getCgeaApprovedBy(): Agent, setCgeaApprovedBy(Agent)}` (générés par Lombok `@Getter`/`@Setter` déjà sur la classe) ; `ParametreDelai` codes `REVUE_CJ_RAPPORT`/`ANALYSE_DEI_RAPPORT`/`APPROBATION_CGEA_RAPPORT` en base — consommés par les tâches 2, 3, 4.

- [ ] **Step 1: Ajouter les champs à `Investigation`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Investigation.java`, ajouter juste
après le bloc existant `cgeApprovedBy` (repérable par `@Column(name = "cge_approved_by_id")`) :

```java
    @Column(name = "cgea_approved_at")
    private Instant cgeaApprovedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cgea_approved_by_id")
    private Agent cgeaApprovedBy;
```

- [ ] **Step 2: Créer la migration `033-circuit-validation-renforce.sql`**

```sql
--liquibase formatted sql
--changeset dev:033-circuit-validation-renforce

ALTER TABLE investigation ADD COLUMN cgea_approved_at TIMESTAMP;
ALTER TABLE investigation ADD COLUMN cgea_approved_by_id UUID REFERENCES agent(id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'REVUE_CJ_RAPPORT', 'Délai de revue du rapport par le conseiller juridique', 10, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'ANALYSE_DEI_RAPPORT', 'Délai d''analyse du rapport par le DEI', 15, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'APPROBATION_CGEA_RAPPORT', 'Délai d''approbation du rapport par le CGEA', 10, TRUE, TRUE, 0, now());

COMMENT ON COLUMN investigation.cgea_approved_at IS 'Circuit de validation du rapport (Lot 5 sous-chantier 3/4) - 3e etape, entre DEI et CGE';
```

- [ ] **Step 3: Vérifier que le module compile**

Run: `mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/entity/Investigation.java \
        src/main/resources/db/changelog/migrations/033-circuit-validation-renforce.sql
git commit -m "feat: add cgeaApprovedAt/cgeaApprovedBy to Investigation, seed circuit delays"
```

---

### Task 2: Circuit d'approbation réordonné — CJ -> DEI -> CGEA -> CGE

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `Investigation.{cgeaApprovedAt, cgeaApprovedBy}` (Task 1).
- Produces: `InvestigationService.approveCgea(UUID investigationId, String ipAddress): InvestigationResponse` — consommé par Task 5 (contrôleur). Les 3 signatures `approveDei`/`approveLegalAdvisor`/`approveCge` existent déjà et ne changent pas de forme, seul leur comportement change.

- [ ] **Step 1: Écrire les tests des 4 méthodes d'approbation (échouent — comportement pas encore en place)**

Ajouter dans `InvestigationServiceImplTest.java`, un nouveau helper juste après
`buildInProgressInvestigation()` :

```java
    private Investigation buildCompletedInvestigation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setStatus(InvestigationStatus.COMPLETED);
        return investigation;
    }
```

Puis ajouter les tests suivants (groupés, par exemple juste après les tests `submitReport_*`
existants) :

```java
    @Test
    void approveLegalAdvisor_rejetteSiStatusNestPasCompleted() {
        Investigation investigation = buildInProgressInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.approveLegalAdvisor(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void approveLegalAdvisor_succeeds() {
        Investigation investigation = buildCompletedInvestigation();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.approveLegalAdvisor(investigation.getId(), "127.0.0.1");

        assertThat(investigation.getLegalAdvisorApprovedAt()).isNotNull();
        assertThat(investigation.getLegalAdvisorApprovedBy()).isEqualTo(agent);
    }

    @Test
    void approveDei_rejetteSiConseillerJuridiqueNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.approveDei(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void approveDei_succeedsApresApprobationCj() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.approveDei(investigation.getId(), "127.0.0.1");

        assertThat(investigation.getDeiApprovedAt()).isNotNull();
        assertThat(investigation.getDeiApprovedBy()).isEqualTo(agent);
    }

    @Test
    void approveCgea_rejetteSiDeiNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.approveCgea(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void approveCgea_succeedsApresApprobationDei() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.approveCgea(investigation.getId(), "127.0.0.1");

        assertThat(investigation.getCgeaApprovedAt()).isNotNull();
        assertThat(investigation.getCgeaApprovedBy()).isEqualTo(agent);
    }

    @Test
    void approveCge_rejetteSiCgeaNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.approveCge(investigation.getId(), "motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void approveCge_succeedsApresApprobationCgea() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        investigation.setCgeaApprovedAt(Instant.now());
        investigation.setOutcome(InvestigationOutcome.ARCHIVED);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.approveCge(investigation.getId(), "Motif de clôture", "127.0.0.1");

        assertThat(investigation.getCgeApprovedAt()).isNotNull();
        assertThat(investigation.getCgeApprovedBy()).isEqualTo(agent);
        assertThat(investigation.getStatus()).isEqualTo(InvestigationStatus.ARCHIVED);
        verify(dossierRepository).save(investigation.getDossier());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvnw -Dtest=InvestigationServiceImplTest#approveLegalAdvisor_rejetteSiStatusNestPasCompleted+approveLegalAdvisor_succeeds+approveDei_rejetteSiConseillerJuridiqueNaPasApprouve+approveDei_succeedsApresApprobationCj+approveCgea_rejetteSiDeiNaPasApprouve+approveCgea_succeedsApresApprobationDei+approveCge_rejetteSiCgeaNaPasApprouve+approveCge_succeedsApresApprobationCgea test`
Expected: FAIL — `approveCgea` n'existe pas encore (erreur de compilation), et les préconditions
de `approveDei`/`approveLegalAdvisor`/`approveCge` ne correspondent pas encore à celles testées.

- [ ] **Step 3: Ajouter `approveCgea` à l'interface `InvestigationService`**

Dans `InvestigationService.java`, ajouter juste après la signature `approveCge` existante :

```java
    InvestigationResponse approveCgea(UUID investigationId, String ipAddress);
```

- [ ] **Step 4: Réécrire `approveLegalAdvisor` (1re étape désormais)**

Dans `InvestigationServiceImpl.java`, remplacer le corps de la méthode existante :

```java
    @Override
    @Transactional
    public InvestigationResponse approveLegalAdvisor(
            UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getStatus() != InvestigationStatus.COMPLETED) {
            throw new BusinessException(
                    "La revue du conseiller juridique n'est possible qu'après soumission du rapport.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setLegalAdvisorApprovedAt(Instant.now());
        inv.setLegalAdvisorApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.ADMISSIBILITY_ANALYSIS,
                "Rapport approuvé par le Conseiller Juridique "
                        + "(délai légal : 10 jours ouvrables).",
                true, agent);

        log.info("Conseiller juridique approuvé — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }
```

- [ ] **Step 5: Réécrire `approveDei` (2e étape désormais)**

Remplacer le corps de la méthode existante :

```java
    @Override
    @Transactional
    public InvestigationResponse approveDei(UUID investigationId, String ipAddress) {
        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getLegalAdvisorApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit d'abord être approuvé par le Conseiller Juridique.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setDeiApprovedAt(Instant.now());
        inv.setDeiApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport approuvé par le DEI (délai légal : 15 jours ouvrables).",
                true, agent);

        log.info("DEI approuvé — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }
```

- [ ] **Step 6: Ajouter `approveCgea` (nouvelle 3e étape)**

Ajouter juste après `approveDei` :

```java
    @Override
    @Transactional
    public InvestigationResponse approveCgea(UUID investigationId, String ipAddress) {
        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getDeiApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit d'abord être approuvé par le DEI.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setCgeaApprovedAt(Instant.now());
        inv.setCgeaApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport approuvé par le CGEA (délai légal : 10 jours ouvrables).",
                true, agent);

        log.info("CGEA approuvé — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }
```

- [ ] **Step 7: Modifier la précondition et le retour de `approveCge`**

Dans la méthode `approveCge` existante, remplacer le bloc de préconditions :

```java
        if (inv.getDeiApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit être approuvé par le DEI avant la décision CGE.");
        }
        if (inv.getLegalAdvisorApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit être approuvé par le Conseiller Juridique "
                            + "avant la décision CGE.");
        }
```

par :

```java
        if (inv.getCgeaApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit être approuvé par le CGEA avant la décision CGE.");
        }
```

Et remplacer la dernière ligne de la méthode :

```java
        return investigationMapper.toResponse(saved);
```

par :

```java
        return buildResponseWithFreshMembers(saved, investigationId);
```

Le reste du corps de `approveCge` (gestion de `outcome`, transition `DossierStatus`, audit) n'est
**pas** modifié.

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvnw -Dtest=InvestigationServiceImplTest#approveLegalAdvisor_rejetteSiStatusNestPasCompleted+approveLegalAdvisor_succeeds+approveDei_rejetteSiConseillerJuridiqueNaPasApprouve+approveDei_succeedsApresApprobationCj+approveCgea_rejetteSiDeiNaPasApprouve+approveCgea_succeedsApresApprobationDei+approveCge_rejetteSiCgeaNaPasApprouve+approveCge_succeedsApresApprobationCgea test`
Expected: PASS (8 tests)

- [ ] **Step 9: Run the full test class to make sure nothing else broke**

Run: `mvnw -Dtest=InvestigationServiceImplTest test`
Expected: PASS (toute la classe)

- [ ] **Step 10: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: reorder report approval circuit to CJ->DEI->CGEA->CGE, add CGEA step"
```

---

### Task 3: Rejet motivé par étape

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `Investigation.{legalAdvisorApprovedAt/By, deiApprovedAt/By, cgeaApprovedAt/By}` (existant + Task 1).
- Produces: `InvestigationService.{rejectDei(UUID, String, String): InvestigationResponse, rejectCgea(UUID, String, String): InvestigationResponse, rejectCge(UUID, String, String): InvestigationResponse}` (signature : `investigationId`, `motif`, `ipAddress`) — consommés par Task 5 (contrôleur).

- [ ] **Step 1: Écrire les tests des 3 méthodes de rejet (échouent — les méthodes n'existent pas)**

Ajouter dans `InvestigationServiceImplTest.java`, après les tests de la Tâche 2 :

```java
    @Test
    void rejectDei_rejetteSiConseillerJuridiqueNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectDei(investigation.getId(), "Motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectDei_rejetteSiMotifVide() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectDei(investigation.getId(), "   ", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectDei_remetLegalAdvisorApprovedAtANull() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setLegalAdvisorApprovedBy(Agent.builder().id(UUID.randomUUID()).build());
        Agent deiAgent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(deiAgent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.rejectDei(investigation.getId(), "Preuves insuffisantes", "127.0.0.1");

        assertThat(investigation.getLegalAdvisorApprovedAt()).isNull();
        assertThat(investigation.getLegalAdvisorApprovedBy()).isNull();
    }

    @Test
    void rejectCgea_rejetteSiDeiNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectCgea(investigation.getId(), "Motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectCgea_rejetteSiMotifVide() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectCgea(investigation.getId(), "", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectCgea_remetDeiApprovedAtANull() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        investigation.setDeiApprovedBy(Agent.builder().id(UUID.randomUUID()).build());
        Agent cgeaAgent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(cgeaAgent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.rejectCgea(investigation.getId(), "Analyse incomplète", "127.0.0.1");

        assertThat(investigation.getDeiApprovedAt()).isNull();
        assertThat(investigation.getDeiApprovedBy()).isNull();
    }

    @Test
    void rejectCge_rejetteSiCgeaNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectCge(investigation.getId(), "Motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectCge_rejetteSiMotifVide() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        investigation.setCgeaApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectCge(investigation.getId(), null, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectCge_remetCgeaApprovedAtANull() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        investigation.setCgeaApprovedAt(Instant.now());
        investigation.setCgeaApprovedBy(Agent.builder().id(UUID.randomUUID()).build());
        Agent cgeAgent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(cgeAgent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.rejectCge(investigation.getId(), "Éléments insuffisants pour trancher", "127.0.0.1");

        assertThat(investigation.getCgeaApprovedAt()).isNull();
        assertThat(investigation.getCgeaApprovedBy()).isNull();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvnw -Dtest=InvestigationServiceImplTest#rejectDei_rejetteSiConseillerJuridiqueNaPasApprouve+rejectDei_rejetteSiMotifVide+rejectDei_remetLegalAdvisorApprovedAtANull+rejectCgea_rejetteSiDeiNaPasApprouve+rejectCgea_rejetteSiMotifVide+rejectCgea_remetDeiApprovedAtANull+rejectCge_rejetteSiCgeaNaPasApprouve+rejectCge_rejetteSiMotifVide+rejectCge_remetCgeaApprovedAtANull test`
Expected: FAIL — compilation error, les 3 méthodes n'existent pas.

- [ ] **Step 3: Ajouter les 3 signatures à l'interface `InvestigationService`**

Ajouter après `approveCgea` (Task 2, Step 3) :

```java
    InvestigationResponse rejectDei(UUID investigationId, String motif, String ipAddress);
    InvestigationResponse rejectCgea(UUID investigationId, String motif, String ipAddress);
    InvestigationResponse rejectCge(UUID investigationId, String motif, String ipAddress);
```

- [ ] **Step 4: Implémenter les 3 méthodes dans `InvestigationServiceImpl`**

Ajouter après `approveCge` :

```java
    @Override
    @Transactional
    public InvestigationResponse rejectDei(
            UUID investigationId, String motif, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getLegalAdvisorApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit d'abord être approuvé par le Conseiller Juridique.");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setLegalAdvisorApprovedAt(null);
        inv.setLegalAdvisorApprovedBy(null);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport rejeté par le DEI, renvoyé au Conseiller Juridique. Motif : " + motif,
                true, agent);

        log.info("DEI rejeté — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }

    @Override
    @Transactional
    public InvestigationResponse rejectCgea(
            UUID investigationId, String motif, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getDeiApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit d'abord être approuvé par le DEI.");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setDeiApprovedAt(null);
        inv.setDeiApprovedBy(null);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport rejeté par le CGEA, renvoyé au DEI. Motif : " + motif,
                true, agent);

        log.info("CGEA rejeté — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }

    @Override
    @Transactional
    public InvestigationResponse rejectCge(
            UUID investigationId, String motif, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getCgeaApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit être approuvé par le CGEA avant la décision CGE.");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setCgeaApprovedAt(null);
        inv.setCgeaApprovedBy(null);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport rejeté par le CGE, renvoyé au CGEA. Motif : " + motif,
                true, agent);

        log.info("CGE rejeté — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvnw -Dtest=InvestigationServiceImplTest#rejectDei_rejetteSiConseillerJuridiqueNaPasApprouve+rejectDei_rejetteSiMotifVide+rejectDei_remetLegalAdvisorApprovedAtANull+rejectCgea_rejetteSiDeiNaPasApprouve+rejectCgea_rejetteSiMotifVide+rejectCgea_remetDeiApprovedAtANull+rejectCge_rejetteSiCgeaNaPasApprouve+rejectCge_rejetteSiMotifVide+rejectCge_remetCgeaApprovedAtANull test`
Expected: PASS (9 tests)

- [ ] **Step 6: Run the full test class**

Run: `mvnw -Dtest=InvestigationServiceImplTest test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: add motivated rejection at each report approval circuit step"
```

---

### Task 4: Échéances par étape

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InvestigationResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`

**Interfaces:**
- Consumes: `ParametreDelaiService.resolveDelaiJours(String code): int` (existant, lève
  `ResourceNotFoundException` si le code est inconnu/inactif/sans valeur — déjà injecté dans
  `InvestigationServiceImpl` sous le nom `parametreDelaiService`), `ParametreDelai` codes
  `REVUE_CJ_RAPPORT`/`ANALYSE_DEI_RAPPORT`/`APPROBATION_CGEA_RAPPORT` (Task 1),
  `APPROBATION_CGE` (existant).
- Produces: `InvestigationResponse.{cgeaApprovedAt, cjRevueDeadline, cjRevueOverdue,
  deiAnalyseDeadline, deiAnalyseOverdue, cgeaApprobationDeadline, cgeaApprobationOverdue,
  cgeApprobationDeadline, cgeApprobationOverdue}` — terminal, aucune tâche suivante n'en dépend.

- [ ] **Step 1: Écrire les tests des échéances (échouent — champs/logique pas encore en place)**

Ajouter dans `InvestigationServiceImplTest.java` :

```java
    @Test
    void findById_calculeEcheanceCjDepuisReportSubmittedAt() {
        Investigation investigation = buildCompletedInvestigation();
        Instant submittedAt = Instant.parse("2026-01-01T00:00:00Z");
        investigation.setReportSubmittedAt(submittedAt);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT")).thenReturn(10);

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCjRevueDeadline()).isEqualTo(submittedAt.plusSeconds(10L * 24 * 3600));
    }

    @Test
    void findById_cjRevueOverdueVraiSiEcheanceDepasseeEtPasEncoreApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        Instant submittedAt = Instant.now().minusSeconds(20L * 24 * 3600);
        investigation.setReportSubmittedAt(submittedAt);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT")).thenReturn(10);

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCjRevueOverdue()).isTrue();
    }

    @Test
    void findById_neCalculeAucuneEcheanceSiRapportPasEncoreSoumis() {
        Investigation investigation = buildInProgressInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCjRevueDeadline()).isNull();
        verify(parametreDelaiService, never()).resolveDelaiJours(any());
    }

    @Test
    void findById_degradeVersNullSiParametreDelaiIndisponible() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setReportSubmittedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT"))
                .thenThrow(new ResourceNotFoundException("Paramètre introuvable"));

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCjRevueDeadline()).isNull();
        assertThat(result.getCjRevueOverdue()).isFalse();
    }

    @Test
    void findById_calculeEcheanceDeiDepuisLegalAdvisorApprovedAtPasReportSubmittedAt() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setReportSubmittedAt(Instant.parse("2026-01-01T00:00:00Z"));
        Instant cjApprovedAt = Instant.parse("2026-01-05T00:00:00Z");
        investigation.setLegalAdvisorApprovedAt(cjApprovedAt);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT")).thenReturn(10);
        when(parametreDelaiService.resolveDelaiJours("ANALYSE_DEI_RAPPORT")).thenReturn(15);

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getDeiAnalyseDeadline()).isEqualTo(cjApprovedAt.plusSeconds(15L * 24 * 3600));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvnw -Dtest=InvestigationServiceImplTest#findById_calculeEcheanceCjDepuisReportSubmittedAt+findById_cjRevueOverdueVraiSiEcheanceDepasseeEtPasEncoreApprouve+findById_neCalculeAucuneEcheanceSiRapportPasEncoreSoumis+findById_degradeVersNullSiParametreDelaiIndisponible+findById_calculeEcheanceDeiDepuisLegalAdvisorApprovedAtPasReportSubmittedAt test`
Expected: FAIL — compilation error, `getCjRevueDeadline()`/`getDeiAnalyseDeadline()` n'existent
pas encore sur `InvestigationResponse`.

- [ ] **Step 3: Ajouter les champs à `InvestigationResponse`**

Dans `InvestigationResponse.java`, ajouter juste après le champ `cgeApprovedAt` existant :

```java
    private Instant cgeaApprovedAt;
    private Instant cjRevueDeadline;
    private Boolean cjRevueOverdue;
    private Instant deiAnalyseDeadline;
    private Boolean deiAnalyseOverdue;
    private Instant cgeaApprobationDeadline;
    private Boolean cgeaApprobationOverdue;
    private Instant cgeApprobationDeadline;
    private Boolean cgeApprobationOverdue;
```

(`cgeaApprovedAt` est mappé automatiquement par nom par MapStruct, aucune modification de
`InvestigationMapper` nécessaire — même mécanisme que `deiApprovedAt`/`legalAdvisorApprovedAt`/
`cgeApprovedAt` déjà en place.)

- [ ] **Step 4: Ajouter le calcul des échéances dans `InvestigationServiceImpl`**

Modifier `buildResponseWithFreshMembers` (repérable par sa signature
`private InvestigationResponse buildResponseWithFreshMembers`) — ajouter l'appel juste avant le
`return response;` final :

```java
        response.setMembers(memberResponses);
        response.setMemberCount(memberResponses.size());

        fillCircuitValidationDeadlines(response, inv);

        return response;
```

Ajouter les 3 nouvelles méthodes privées juste après `buildResponseWithFreshMembers` :

```java
    private void fillCircuitValidationDeadlines(InvestigationResponse response, Investigation inv) {
        if (inv.getReportSubmittedAt() != null) {
            response.setCjRevueDeadline(
                    resolveDeadline(inv.getReportSubmittedAt(), "REVUE_CJ_RAPPORT"));
            response.setCjRevueOverdue(isOverdue(
                    response.getCjRevueDeadline(), inv.getLegalAdvisorApprovedAt()));
        }
        if (inv.getLegalAdvisorApprovedAt() != null) {
            response.setDeiAnalyseDeadline(
                    resolveDeadline(inv.getLegalAdvisorApprovedAt(), "ANALYSE_DEI_RAPPORT"));
            response.setDeiAnalyseOverdue(isOverdue(
                    response.getDeiAnalyseDeadline(), inv.getDeiApprovedAt()));
        }
        if (inv.getDeiApprovedAt() != null) {
            response.setCgeaApprobationDeadline(
                    resolveDeadline(inv.getDeiApprovedAt(), "APPROBATION_CGEA_RAPPORT"));
            response.setCgeaApprobationOverdue(isOverdue(
                    response.getCgeaApprobationDeadline(), inv.getCgeaApprovedAt()));
        }
        if (inv.getCgeaApprovedAt() != null) {
            response.setCgeApprobationDeadline(
                    resolveDeadline(inv.getCgeaApprovedAt(), "APPROBATION_CGE"));
            response.setCgeApprobationOverdue(isOverdue(
                    response.getCgeApprobationDeadline(), inv.getCgeApprovedAt()));
        }
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

    private boolean isOverdue(Instant deadline, Instant completedAt) {
        return deadline != null && completedAt == null && Instant.now().isAfter(deadline);
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvnw -Dtest=InvestigationServiceImplTest#findById_calculeEcheanceCjDepuisReportSubmittedAt+findById_cjRevueOverdueVraiSiEcheanceDepasseeEtPasEncoreApprouve+findById_neCalculeAucuneEcheanceSiRapportPasEncoreSoumis+findById_degradeVersNullSiParametreDelaiIndisponible+findById_calculeEcheanceDeiDepuisLegalAdvisorApprovedAtPasReportSubmittedAt test`
Expected: PASS (5 tests)

- [ ] **Step 6: Run the full test class**

Run: `mvnw -Dtest=InvestigationServiceImplTest test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InvestigationResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: expose informative per-step deadlines on report approval circuit"
```

---

### Task 5: Endpoints REST

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java`

**Interfaces:**
- Consumes: `InvestigationService.{approveCgea(UUID, String), rejectDei(UUID, String, String),
  rejectCgea(UUID, String, String), rejectCge(UUID, String, String)}` (Tasks 2 et 3).
- Produces: aucune (dernière tâche du chantier).

Pas de test de contrôleur (convention déjà établie dans ce dépôt, confirmée par
`find . -iname "*ControllerTest.java"` → 0 résultat).

- [ ] **Step 1: Ajouter les 4 endpoints**

Dans `InvestigationController.java`, ajouter après la méthode `approveCge` existante (repérable
par `@PatchMapping("/{id}/approve-cge")`) :

```java
    @PatchMapping("/{id}/approve-cgea")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> approveCgea(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Approbation CGEA — investigation {}", id);
        InvestigationResponse result = investigationService.approveCgea(
                id, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "APPROUVER_CGEA", "INVESTIGATION", id.toString(),
                "Approbation CGEA", AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/reject-dei")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> rejectDei(
            @PathVariable UUID id,
            @RequestParam String motif,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Rejet DEI — investigation {}", id);
        InvestigationResponse result = investigationService.rejectDei(
                id, motif, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "REJETER_DEI", "INVESTIGATION", id.toString(),
                "Rejet DEI — " + motif, AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/reject-cgea")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> rejectCgea(
            @PathVariable UUID id,
            @RequestParam String motif,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Rejet CGEA — investigation {}", id);
        InvestigationResponse result = investigationService.rejectCgea(
                id, motif, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "REJETER_CGEA", "INVESTIGATION", id.toString(),
                "Rejet CGEA — " + motif, AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/reject-cge")
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> rejectCge(
            @PathVariable UUID id,
            @RequestParam String motif,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Rejet CGE — investigation {}", id);
        InvestigationResponse result = investigationService.rejectCge(
                id, motif, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "REJETER_CGE", "INVESTIGATION", id.toString(),
                "Rejet CGE — " + motif, AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
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
git add src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java
git commit -m "feat: add approve-cgea/reject-dei/reject-cgea/reject-cge endpoints"
```
