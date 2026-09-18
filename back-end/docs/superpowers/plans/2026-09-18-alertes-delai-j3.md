# Alertes de délai J-3 et à échéance — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Étendre le job planifié existant `NotificationServiceImpl.sendDeadlineAlerts()`
pour créer des alertes J-3 (3 jours avant échéance) sur les 3 délais déjà couverts
à échéance dépassée (AR, complément, investigation), et ajouter la couverture
complète (à échéance + J-3) pour les demandes de documents, qui n'ont
actuellement aucune alerte.

**Architecture:** Un seul job étendu (même cron `0 0 8 * * MON-FRI`), 5 nouveaux
blocs suivant exactement le patron des 3 blocs existants (requête → boucle →
déduplication par `existsBy...AndType` → `Notification.builder()` → save). Une
FK nullable `demande_documents_id` sur `Notification` corrige un défaut de
déduplication qui affecterait les demandes de documents concurrentes.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Liquibase, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-18-alertes-delai-j3-design.md`

## Global Constraints

- Périmètre : 4 échéances tête d'affiche uniquement (AR, complément, durée
  investigation, demande de documents). Les 5 échéances de circuit interne
  (`REVUE_CJ_RAPPORT`, etc.) restent hors périmètre.
- Fenêtre J-3 : `now <= deadline <= now + 3 jours calendaires`, calculée via
  `deadlineCalculator.addCalendarDays(Instant.now(), 3)` — jamais un nouveau
  calcul en jours ouvrables.
- Déduplication : `existsByDossierIdAndType` pour les 3 échéances portées par
  `Dossier` (comme l'existant) ; `existsByDemandeDocumentsIdAndType` (nouveau)
  pour les 2 échéances portées par `DemandeDocuments`.
- Canal : `NotificationChannel.PORTAL` pour toutes les nouvelles alertes,
  comme l'existant — aucun changement de canal.
- `resolveNotificationText` ne lève jamais si une clé manque (retombe sur
  `""`) — ne pas ajouter de try/catch autour de ces appels.
- Tests Mockito : `@Mock(answer = Answers.CALLS_REAL_METHODS) DeadlineCalculator`
  — sûr sans stub car `addCalendarDays` n'a aucune dépendance interne
  (contrairement à `addBusinessDays`, non utilisé dans ce sous-chantier).
- `sendDeadlineAlerts()` appelle inconditionnellement toutes ses requêtes de
  recherche à chaque exécution — tout test doit stubber les 8 méthodes de
  recherche (6 après la Task 2, 8 après la Task 3), sous peine de `NullPointerException`
  sur la boucle `for` (un `List` non stubbé sur un `@Mock` renvoie `null`, pas
  une liste vide).

---

### Task 1: Schéma, entité et requêtes de repository

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/enums/NotificationType.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Notification.java`
- Create: `src/main/resources/db/changelog/migrations/011-add-demande-documents-to-notification.sql`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/DemandeDocumentsRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/NotificationRepository.java`

**Interfaces:**
- Produces: `NotificationType.DEADLINE_ALERT_J3`, `NotificationType.COMPLEMENT_ALERT_J3`,
  `NotificationType.INVESTIGATION_ALERT_J3`, `NotificationType.DEMANDE_DOCUMENTS_ALERT`,
  `NotificationType.DEMANDE_DOCUMENTS_ALERT_J3` (enum constants, consommés par
  Task 2 et Task 3).
- Produces: `Notification.getDemandeDocuments()` / `Notification.setDemandeDocuments(DemandeDocuments)`
  (via `@Getter`/`@Setter` Lombok déjà sur la classe) et le champ builder
  `.demandeDocuments(DemandeDocuments)` (via `@SuperBuilder` déjà sur la
  classe) — consommés par Task 3.
- Produces: `DossierRepository.findAcknowledgmentsDueWithin(Instant now, Instant in3Days): List<Dossier>`,
  `DossierRepository.findComplementsDueWithin(Instant now, Instant in3Days): List<Dossier>`,
  `DossierRepository.findInvestigationsDueWithin(Instant now, Instant in3Days): List<Dossier>`
  — consommés par Task 2.
- Produces: `DemandeDocumentsRepository.findOverdue(Instant now): List<DemandeDocuments>`,
  `DemandeDocumentsRepository.findDueWithin(Instant now, Instant in3Days): List<DemandeDocuments>`
  — consommés par Task 3.
- Produces: `NotificationRepository.existsByDemandeDocumentsIdAndType(UUID demandeDocumentsId, NotificationType type): boolean`
  — consommé par Task 3.

- [ ] **Step 1: Ajouter les 5 nouveaux types d'alerte**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/enums/NotificationType.java`.
Remplacer son contenu entier par :

```java
package gov.bf.ascelc.univers_audits.enums;

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
    DEMANDE_DOCUMENTS_ALERT_J3
}
```

- [ ] **Step 2: Ajouter la FK nullable sur l'entité `Notification`**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Notification.java`.
Trouver ce bloc (juste après le champ `dossier`) :

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 35)
    private NotificationType type;
```

Le remplacer par :

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Dossier dossier;

    // Nulle pour tous les types d'alerte sauf DEMANDE_DOCUMENTS_ALERT/
    // DEMANDE_DOCUMENTS_ALERT_J3 — une investigation peut avoir plusieurs
    // demandes de documents concurrentes, dedupliquer uniquement par dossier
    // supprimerait a tort l'alerte d'une deuxieme demande.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_documents_id")
    private DemandeDocuments demandeDocuments;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 35)
    private NotificationType type;
```

(`DemandeDocuments` est dans le même package `model.entity` — aucun nouvel
import requis.)

- [ ] **Step 3: Créer la migration de schéma**

Créer `src/main/resources/db/changelog/migrations/011-add-demande-documents-to-notification.sql` :

```sql
--liquibase formatted sql
--changeset dev:011-add-demande-documents-to-notification

-- FK nullable pour dedupliquer correctement les alertes de DemandeDocuments
-- (une investigation peut avoir plusieurs demandes de documents concurrentes
-- non recues ; existsByDossierIdAndType seul supprimerait a tort l'alerte
-- d'une deuxieme demande). Nulle pour tous les types d'alerte existants.
-- Voir docs/superpowers/specs/2026-09-18-alertes-delai-j3-design.md.

ALTER TABLE notification
    ADD COLUMN demande_documents_id UUID;

ALTER TABLE notification
    ADD CONSTRAINT fk_notification_demande_documents
        FOREIGN KEY (demande_documents_id) REFERENCES demande_documents(id);
```

- [ ] **Step 4: Ajouter les 3 requêtes J-3 sur `DossierRepository`**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java`.
Trouver la méthode `findOverdueInvestigations` :

```java
    @Query("""
            SELECT d FROM Dossier d
            JOIN Investigation i ON i.dossier.id = d.id
            WHERE d.status = gov.bf.ascelc.univers_audits.enums.DossierStatus.EN_INVESTIGATION
            AND i.status IN (
                gov.bf.ascelc.univers_audits.enums.InvestigationStatus.INITIATED,
                gov.bf.ascelc.univers_audits.enums.InvestigationStatus.IN_PROGRESS
            )
            AND COALESCE(i.extendedDeadline, i.plannedEndDate) < :now
            ORDER BY i.plannedEndDate ASC
            """)
    List<Dossier> findOverdueInvestigations(@Param("now") Instant now);
```

Juste après cette méthode (avant le prochain `@Query` / `countByStatus`),
insérer :

```java

    @Query("""
            SELECT d FROM Dossier d
            WHERE d.acknowledgmentDeadline BETWEEN :now AND :in3Days
            AND d.status NOT IN (
                gov.bf.ascelc.univers_audits.enums.DossierStatus.CLOS,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.CLASSE,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.IRRECEVABLE,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.TRANSFERE,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.ORIENTEE_ADMINISTRATIF
            )
            ORDER BY d.acknowledgmentDeadline ASC
            """)
    List<Dossier> findAcknowledgmentsDueWithin(
            @Param("now") Instant now, @Param("in3Days") Instant in3Days);

    @Query("""
            SELECT d FROM Dossier d
            WHERE d.status = gov.bf.ascelc.univers_audits.enums.DossierStatus.EN_ATTENTE_COMPLEMENT
            AND d.additionalInfoDeadline BETWEEN :now AND :in3Days
            ORDER BY d.additionalInfoDeadline ASC
            """)
    List<Dossier> findComplementsDueWithin(
            @Param("now") Instant now, @Param("in3Days") Instant in3Days);

    @Query("""
            SELECT d FROM Dossier d
            JOIN Investigation i ON i.dossier.id = d.id
            WHERE d.status = gov.bf.ascelc.univers_audits.enums.DossierStatus.EN_INVESTIGATION
            AND i.status IN (
                gov.bf.ascelc.univers_audits.enums.InvestigationStatus.INITIATED,
                gov.bf.ascelc.univers_audits.enums.InvestigationStatus.IN_PROGRESS
            )
            AND COALESCE(i.extendedDeadline, i.plannedEndDate) BETWEEN :now AND :in3Days
            ORDER BY i.plannedEndDate ASC
            """)
    List<Dossier> findInvestigationsDueWithin(
            @Param("now") Instant now, @Param("in3Days") Instant in3Days);
```

- [ ] **Step 5: Ajouter les 2 requêtes sur `DemandeDocumentsRepository`**

Remplacer le contenu entier de
`src/main/java/gov/bf/ascelc/univers_audits/repository/DemandeDocumentsRepository.java` par :

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DemandeDocumentsRepository extends JpaRepository<DemandeDocuments, UUID> {

    List<DemandeDocuments> findByInvestigationIdOrderBySentAtDesc(UUID investigationId);

    @Query("""
            SELECT dd FROM DemandeDocuments dd
            WHERE dd.received = false
            AND dd.deadline < :now
            ORDER BY dd.deadline ASC
            """)
    List<DemandeDocuments> findOverdue(@Param("now") Instant now);

    @Query("""
            SELECT dd FROM DemandeDocuments dd
            WHERE dd.received = false
            AND dd.deadline BETWEEN :now AND :in3Days
            ORDER BY dd.deadline ASC
            """)
    List<DemandeDocuments> findDueWithin(
            @Param("now") Instant now, @Param("in3Days") Instant in3Days);
}
```

- [ ] **Step 6: Ajouter la méthode de dédup sur `NotificationRepository`**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/repository/NotificationRepository.java`.
Trouver la ligne :

```java
    boolean existsByDossierIdAndType(UUID dossierId, NotificationType type);
```

Juste après, insérer :

```java

    boolean existsByDemandeDocumentsIdAndType(UUID demandeDocumentsId, NotificationType type);
```

- [ ] **Step 7: Compiler et vérifier l'absence de régression**

```bash
./mvnw.cmd -q compile
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Attendu : compilation propre, suite complète toujours verte (505/505 — cette
task n'ajoute aucun test, elle ajoute uniquement du schéma/entité/repository
inertes tant que rien ne les appelle). Si `contextLoads` échoue, lire la
chaîne `Caused by` complète dans `target/surefire-reports/*.txt` avant de
conclure quoi que ce soit (voir CLAUDE.md / mémoire du dépôt — ne jamais
supposer une cause sans l'avoir lue en entier).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/NotificationType.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/Notification.java \
        src/main/resources/db/changelog/migrations/011-add-demande-documents-to-notification.sql \
        src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/DemandeDocumentsRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/NotificationRepository.java
git commit -m "feat(alertes-delai): schema et requetes pour les alertes J-3 et demande-documents"
```

---

### Task 2: Alertes J-3 pour AR, complément et investigation

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java`
- Create: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java`

**Interfaces:**
- Consumes: `DossierRepository.findAcknowledgmentsDueWithin`/`findComplementsDueWithin`/`findInvestigationsDueWithin`
  (Task 1). `DeadlineCalculator.addCalendarDays(Instant, int): Instant` (déjà
  existant, `shared/utils/DeadlineCalculator.java`, livré au sous-chantier 1/4
  — ne dépend d'aucun repository interne, sûr à laisser s'exécuter réellement
  sur un mock `CALLS_REAL_METHODS`). `NotificationType.DEADLINE_ALERT_J3`/`COMPLEMENT_ALERT_J3`/`INVESTIGATION_ALERT_J3`
  (Task 1).
- Produces: `NotificationServiceImpl` reçoit un nouveau champ constructeur
  `DeadlineCalculator deadlineCalculator` (position : dernier champ, après
  `portalConfigService`) — Task 3 ajoutera un second champ juste après.

- [ ] **Step 1: Écrire le fichier de test avec les tests de non-régression et les 3 nouveaux tests J-3**

Créer `src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java` :

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private DossierDetailsMapper detailsMapper;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private PortalConfigService portalConfigService;
    @Mock(answer = Answers.CALLS_REAL_METHODS) private DeadlineCalculator deadlineCalculator;

    @InjectMocks
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(dossierRepository.findOverdueAcknowledgments(any())).thenReturn(List.of());
        lenient().when(dossierRepository.findOverdueComplementRequests(any())).thenReturn(List.of());
        lenient().when(dossierRepository.findOverdueInvestigations(any())).thenReturn(List.of());
        lenient().when(dossierRepository.findAcknowledgmentsDueWithin(any(), any())).thenReturn(List.of());
        lenient().when(dossierRepository.findComplementsDueWithin(any(), any())).thenReturn(List.of());
        lenient().when(dossierRepository.findInvestigationsDueWithin(any(), any())).thenReturn(List.of());
        lenient().when(portalConfigService.resolveNotificationText(anyString(), anyMap()))
                .thenReturn("texte");
    }

    // ── Non-régression : les 3 blocs "à échéance dépassée" existants ──

    @Test
    void sendDeadlineAlerts_createsAlertForOverdueAcknowledgment() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0001").build();
        when(dossierRepository.findOverdueAcknowledgments(any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEADLINE_ALERT && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_createsAlertForOverdueComplement() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0002").build();
        when(dossierRepository.findOverdueComplementRequests(any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.INTERNAL_ALERT && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_createsAlertForOverdueInvestigation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0003").build();
        when(dossierRepository.findOverdueInvestigations(any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.INVESTIGATION_ALERT && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_doesNotDuplicateOverdueAcknowledgmentAlert() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0004").build();
        when(dossierRepository.findOverdueAcknowledgments(any())).thenReturn(List.of(dossier));
        when(notificationRepository.existsByDossierIdAndType(
                dossier.getId(), NotificationType.DEADLINE_ALERT)).thenReturn(true);

        service.sendDeadlineAlerts();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.DEADLINE_ALERT));
    }

    // ── Nouveau : alertes J-3 ──

    @Test
    void sendDeadlineAlerts_createsJ3AlertForAcknowledgmentDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0005").build();
        when(dossierRepository.findAcknowledgmentsDueWithin(any(), any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEADLINE_ALERT_J3 && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_createsJ3AlertForComplementDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0006").build();
        when(dossierRepository.findComplementsDueWithin(any(), any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.COMPLEMENT_ALERT_J3 && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_createsJ3AlertForInvestigationDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0007").build();
        when(dossierRepository.findInvestigationsDueWithin(any(), any())).thenReturn(List.of(dossier));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.INVESTIGATION_ALERT_J3 && n.getDossier() == dossier));
    }

    @Test
    void sendDeadlineAlerts_doesNotDuplicateJ3AcknowledgmentAlert() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0008").build();
        when(dossierRepository.findAcknowledgmentsDueWithin(any(), any())).thenReturn(List.of(dossier));
        when(notificationRepository.existsByDossierIdAndType(
                dossier.getId(), NotificationType.DEADLINE_ALERT_J3)).thenReturn(true);

        service.sendDeadlineAlerts();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.DEADLINE_ALERT_J3));
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent (méthodes/types inexistants côté service)**

```bash
./mvnw.cmd -q test -Dtest=NotificationServiceImplTest
```

Attendu : ÉCHEC de compilation — `NotificationServiceImpl` n'a pas encore de
champ `deadlineCalculator` compatible avec `@InjectMocks`, et les 4 tests J-3
échoueront (aucune alerte créée, car le service ne lit pas encore
`findAcknowledgmentsDueWithin`/`findComplementsDueWithin`/`findInvestigationsDueWithin`).
Les 4 tests de non-régression, eux, doivent déjà passer puisque le
comportement existant n'est pas encore modifié — c'est attendu et normal
tant que l'étape suivante n'est pas faite.

- [ ] **Step 3: Injecter `DeadlineCalculator` et ajouter les 3 blocs J-3**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java`.

Ajouter l'import, juste après les imports existants de `service.PortalConfigService`
et avant `shared.exceptions.BusinessException` :

```java
import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;
```

Trouver le bloc de champs :

```java
    private final NotificationRepository notificationRepository;
    private final DossierRepository      dossierRepository;
    private final AgentRepository        agentRepository;
    private final DossierDetailsMapper   detailsMapper;
    private final DossierAccessGuard     accessGuard;
    private final PortalConfigService    portalConfigService;
```

Le remplacer par :

```java
    private final NotificationRepository notificationRepository;
    private final DossierRepository      dossierRepository;
    private final AgentRepository        agentRepository;
    private final DossierDetailsMapper   detailsMapper;
    private final DossierAccessGuard     accessGuard;
    private final PortalConfigService    portalConfigService;
    private final DeadlineCalculator     deadlineCalculator;
```

Trouver le début de la méthode `sendDeadlineAlerts()` :

```java
    @Override
    @Scheduled(cron = "0 0 8 * * MON-FRI")
    @Transactional
    public void sendDeadlineAlerts() {
        log.info("[Notification] Envoi des alertes de délai dépassé...");

        List<Dossier> overdueAcknowledgments =
                dossierRepository.findOverdueAcknowledgments(Instant.now());
```

Le remplacer par (ajoute `in3Days`, calculé une seule fois) :

```java
    @Override
    @Scheduled(cron = "0 0 8 * * MON-FRI")
    @Transactional
    public void sendDeadlineAlerts() {
        log.info("[Notification] Envoi des alertes de délai dépassé...");

        Instant now = Instant.now();
        Instant in3Days = deadlineCalculator.addCalendarDays(now, 3);

        List<Dossier> overdueAcknowledgments =
                dossierRepository.findOverdueAcknowledgments(now);
```

Les 2 autres appels `Instant.now()` du bloc "à échéance dépassée" existant
(`findOverdueComplementRequests(Instant.now())` et
`findOverdueInvestigations(Instant.now())`) restent inchangés pour l'instant
— remplacer uniquement le premier comme ci-dessus. (Ils seront harmonisés
naturellement par la lecture, mais ce n'est pas requis fonctionnellement :
`Instant.now()` appelé à quelques millisecondes d'écart n'a aucun impact sur
une fenêtre de calcul en jours.)

Trouver la fin de la méthode — le bloc "investigations en dépassement" suivi
du `log.info` final :

```java
        List<Dossier> overdueInvestigations =
                dossierRepository.findOverdueInvestigations(Instant.now());

        for (Dossier dossier : overdueInvestigations) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.INVESTIGATION_ALERT);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.INVESTIGATION_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_investigation",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_investigation",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte investigation créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        log.info("[Notification] Alertes traitées — {} AR, {} compléments, {} investigations",
                overdueAcknowledgments.size(),
                overdueComplements.size(),
                overdueInvestigations.size());
    }
```

Le remplacer par (ajoute les 3 blocs J-3 juste avant le `log.info` final, qui
reste identique pour l'instant — Task 3 le complètera) :

```java
        List<Dossier> overdueInvestigations =
                dossierRepository.findOverdueInvestigations(Instant.now());

        for (Dossier dossier : overdueInvestigations) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.INVESTIGATION_ALERT);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.INVESTIGATION_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_investigation",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_investigation",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte investigation créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<Dossier> dueAcknowledgments =
                dossierRepository.findAcknowledgmentsDueWithin(now, in3Days);

        for (Dossier dossier : dueAcknowledgments) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.DEADLINE_ALERT_J3);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.DEADLINE_ALERT_J3)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_ar_j3",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_ar_j3",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte AR J-3 créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<Dossier> dueComplements =
                dossierRepository.findComplementsDueWithin(now, in3Days);

        for (Dossier dossier : dueComplements) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.COMPLEMENT_ALERT_J3);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.COMPLEMENT_ALERT_J3)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_complement_j3",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_complement_j3",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte complément J-3 créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<Dossier> dueInvestigations =
                dossierRepository.findInvestigationsDueWithin(now, in3Days);

        for (Dossier dossier : dueInvestigations) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.INVESTIGATION_ALERT_J3);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.INVESTIGATION_ALERT_J3)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_investigation_j3",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_investigation_j3",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte investigation J-3 créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        log.info("[Notification] Alertes traitées — {} AR, {} compléments, {} investigations",
                overdueAcknowledgments.size(),
                overdueComplements.size(),
                overdueInvestigations.size());
    }
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

```bash
./mvnw.cmd -q test -Dtest=NotificationServiceImplTest
```

Attendu : 8/8 tests passent (4 non-régression + 4 J-3).

- [ ] **Step 5: Suite complète**

```bash
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Attendu : aucune régression (505 tests précédents + 8 nouveaux = 513, 0 échec).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java
git commit -m "feat(alertes-delai): alertes J-3 pour AR, complement et investigation"
```

---

### Task 3: Alertes à échéance et J-3 pour les demandes de documents

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java`

**Interfaces:**
- Consumes: `DemandeDocumentsRepository.findOverdue`/`findDueWithin` (Task 1),
  `NotificationRepository.existsByDemandeDocumentsIdAndType` (Task 1),
  `NotificationType.DEMANDE_DOCUMENTS_ALERT`/`DEMANDE_DOCUMENTS_ALERT_J3`
  (Task 1), `Notification.demandeDocuments` (Task 1),
  `DemandeDocuments.getInvestigation().getDossier()` (existant,
  `Investigation.dossier` est un `@OneToOne`).
- Produces: `NotificationServiceImpl` reçoit un second nouveau champ
  constructeur `DemandeDocumentsRepository demandeDocumentsRepository`
  (dernier champ, après `deadlineCalculator`).

- [ ] **Step 1: Ajouter les tests DemandeDocuments (échouent d'abord)**

Ouvrir `src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java`.

Ajouter l'import, avec les autres imports `gov.bf.ascelc...` :

```java
import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
```

Ajouter le champ mock, juste après `deadlineCalculator` :

```java
    @Mock private DemandeDocumentsRepository demandeDocumentsRepository;
```

Dans `setUp()`, ajouter les 2 nouveaux stubs par défaut :

```java
        lenient().when(demandeDocumentsRepository.findOverdue(any())).thenReturn(List.of());
        lenient().when(demandeDocumentsRepository.findDueWithin(any(), any())).thenReturn(List.of());
```

À la fin de la classe (avant la dernière accolade fermante), ajouter :

```java

    // ── Nouveau : demande de documents (a echeance + J-3) ──

    private DemandeDocuments buildDemande(Dossier dossier) {
        Investigation investigation = Investigation.builder()
                .id(UUID.randomUUID())
                .dossier(dossier)
                .build();
        return DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .received(false)
                .build();
    }

    @Test
    void sendDeadlineAlerts_createsAlertForOverdueDemandeDocuments() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0009").build();
        DemandeDocuments demande = buildDemande(dossier);
        when(demandeDocumentsRepository.findOverdue(any())).thenReturn(List.of(demande));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT
                        && n.getDossier() == dossier
                        && n.getDemandeDocuments() == demande));
    }

    @Test
    void sendDeadlineAlerts_createsJ3AlertForDemandeDocumentsDueSoon() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0010").build();
        DemandeDocuments demande = buildDemande(dossier);
        when(demandeDocumentsRepository.findDueWithin(any(), any())).thenReturn(List.of(demande));

        service.sendDeadlineAlerts();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT_J3
                        && n.getDossier() == dossier
                        && n.getDemandeDocuments() == demande));
    }

    @Test
    void sendDeadlineAlerts_doesNotDuplicateDemandeDocumentsAlert() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0011").build();
        DemandeDocuments demande = buildDemande(dossier);
        when(demandeDocumentsRepository.findOverdue(any())).thenReturn(List.of(demande));
        when(notificationRepository.existsByDemandeDocumentsIdAndType(
                demande.getId(), NotificationType.DEMANDE_DOCUMENTS_ALERT)).thenReturn(true);

        service.sendDeadlineAlerts();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT));
    }

    @Test
    void sendDeadlineAlerts_alertsSecondConcurrentDemandeDocumentsIndependently() {
        // Preuve directe du motif de la FK ajoutee sur Notification : deux
        // demandes de documents non recues sur le MEME dossier, chacune en
        // depassement, doivent recevoir chacune leur propre alerte — la
        // premiere deja alertee ne doit pas supprimer l'alerte de la seconde.
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0012").build();
        DemandeDocuments firstDemande = buildDemande(dossier);
        DemandeDocuments secondDemande = buildDemande(dossier);
        when(demandeDocumentsRepository.findOverdue(any()))
                .thenReturn(List.of(firstDemande, secondDemande));
        when(notificationRepository.existsByDemandeDocumentsIdAndType(
                firstDemande.getId(), NotificationType.DEMANDE_DOCUMENTS_ALERT)).thenReturn(true);
        when(notificationRepository.existsByDemandeDocumentsIdAndType(
                secondDemande.getId(), NotificationType.DEMANDE_DOCUMENTS_ALERT)).thenReturn(false);

        service.sendDeadlineAlerts();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getDemandeDocuments() == firstDemande));
        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.DEMANDE_DOCUMENTS_ALERT
                        && n.getDemandeDocuments() == secondDemande));
    }
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./mvnw.cmd -q test -Dtest=NotificationServiceImplTest
```

Attendu : ÉCHEC de compilation (`@InjectMocks` ne peut pas encore satisfaire
`demandeDocumentsRepository` dans le constructeur de `NotificationServiceImpl`
— le champ n'existe pas côté service tant que l'étape suivante n'est pas
faite). C'est attendu.

- [ ] **Step 3: Injecter `DemandeDocumentsRepository` et ajouter les 2 blocs**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java`.

Ajouter l'import, avec les autres imports `repository.*` :

```java
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
```

Trouver le champ ajouté à la Task 2 :

```java
    private final DeadlineCalculator     deadlineCalculator;
```

Le remplacer par :

```java
    private final DeadlineCalculator     deadlineCalculator;
    private final DemandeDocumentsRepository demandeDocumentsRepository;
```

Trouver le `log.info` final (identique à celui laissé par la Task 2) :

```java
        log.info("[Notification] Alertes traitées — {} AR, {} compléments, {} investigations",
                overdueAcknowledgments.size(),
                overdueComplements.size(),
                overdueInvestigations.size());
    }
```

Le remplacer par (ajoute les 2 blocs `DemandeDocuments` juste avant, et
enrichit le `log.info` final avec les 8 compteurs) :

```java
        List<gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments> overdueDemandeDocuments =
                demandeDocumentsRepository.findOverdue(now);

        for (var demande : overdueDemandeDocuments) {
            gov.bf.ascelc.univers_audits.model.entity.Dossier dossier =
                    demande.getInvestigation().getDossier();
            boolean alreadyAlerted = notificationRepository
                    .existsByDemandeDocumentsIdAndType(
                            demande.getId(),
                            NotificationType.DEMANDE_DOCUMENTS_ALERT);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .demandeDocuments(demande)
                        .type(NotificationType.DEMANDE_DOCUMENTS_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_demande_documents",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_demande_documents",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte demande de documents créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments> dueDemandeDocuments =
                demandeDocumentsRepository.findDueWithin(now, in3Days);

        for (var demande : dueDemandeDocuments) {
            gov.bf.ascelc.univers_audits.model.entity.Dossier dossier =
                    demande.getInvestigation().getDossier();
            boolean alreadyAlerted = notificationRepository
                    .existsByDemandeDocumentsIdAndType(
                            demande.getId(),
                            NotificationType.DEMANDE_DOCUMENTS_ALERT_J3);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .demandeDocuments(demande)
                        .type(NotificationType.DEMANDE_DOCUMENTS_ALERT_J3)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_demande_documents_j3",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_demande_documents_j3",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte demande de documents J-3 créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        log.info("[Notification] Alertes traitées — {} AR, {} compléments, {} investigations, "
                        + "{} AR J-3, {} compléments J-3, {} investigations J-3, "
                        + "{} demandes documents, {} demandes documents J-3",
                overdueAcknowledgments.size(),
                overdueComplements.size(),
                overdueInvestigations.size(),
                dueAcknowledgments.size(),
                dueComplements.size(),
                dueInvestigations.size(),
                overdueDemandeDocuments.size(),
                dueDemandeDocuments.size());
    }
```

(Les noms de types entièrement qualifiés `gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments`/
`Dossier` évitent un conflit avec l'import déjà présent de `Dossier` dans ce
fichier — `Dossier` est déjà importé au niveau du fichier, donc `Dossier
dossier = demande.getInvestigation().getDossier();` peut aussi s'écrire sans
préfixe complet si préféré ; les deux formes compilent, le préfixe complet
est ici uniquement par prudence de copier-coller exact.)

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

```bash
./mvnw.cmd -q test -Dtest=NotificationServiceImplTest
```

Attendu : 12/12 tests passent.

- [ ] **Step 5: Suite complète**

```bash
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Attendu : 517 tests (505 + 12 nouveaux), 0 échec.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java
git commit -m "feat(alertes-delai): alertes a echeance et J-3 pour les demandes de documents"
```

---

### Task 4: Contenu des alertes (seed `portal_config`) et vérification finale

**Files:**
- Create: `src/main/resources/db/changelog/migrations/012-seed-notification-templates-j3.sql`

**Interfaces:**
- Consumes: les 10 clés `config_key` appelées par `NotificationServiceImpl`
  depuis les Tasks 2 et 3 : `notif_subject_deadline_ar_j3`,
  `notif_content_deadline_ar_j3`, `notif_subject_deadline_complement_j3`,
  `notif_content_deadline_complement_j3`, `notif_subject_deadline_investigation_j3`,
  `notif_content_deadline_investigation_j3`, `notif_subject_deadline_demande_documents`,
  `notif_content_deadline_demande_documents`, `notif_subject_deadline_demande_documents_j3`,
  `notif_content_deadline_demande_documents_j3`.

- [ ] **Step 1: Créer la migration de seed**

Créer `src/main/resources/db/changelog/migrations/012-seed-notification-templates-j3.sql` :

```sql
--liquibase formatted sql
--changeset dev:012-seed-notification-templates-j3

-- Modeles de texte pour les 5 nouvelles alertes du sous-chantier "alertes de
-- delai J-3 et a echeance" (voir docs/superpowers/specs/2026-09-18-alertes-delai-j3-design.md).
-- Meme structure de colonnes que 005-seed-portal-config.sql. PortalConfig
-- n'etend pas AuditEntity : id/updated_at/version doivent etre fournis
-- explicitement (pas de DEFAULT au niveau base).

INSERT INTO portal_config
(
    id,
    config_key,
    config_value,
    label,
    description,
    value_type,
    group_name,
    updated_at,
    version
)
VALUES
(gen_random_uuid(), 'notif_subject_deadline_ar_j3',
 'ALERTE : Accusé de réception à échéance dans 3 jours — {numero}',
 'Délai AR à échéance J-3 — sujet',
 'Alerte automatique 3 jours avant l''échéance du délai légal d''accusé de réception. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_ar_j3',
 'Le délai légal de 7 jours pour l''envoi de l''accusé de réception B5 arrive à échéance dans 3 jours pour le dossier {numero}. Traiter avant le dépassement.',
 'Délai AR à échéance J-3 — contenu',
 'Alerte automatique 3 jours avant l''échéance du délai légal d''accusé de réception. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_complement_j3',
 'ALERTE : Complément d''information à échéance dans 3 jours — {numero}',
 'Délai complément à échéance J-3 — sujet',
 'Alerte automatique 3 jours avant l''échéance du délai de réception d''un complément d''information. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_complement_j3',
 'Le délai de réception du complément d''information arrive à échéance dans 3 jours pour le dossier {numero}.',
 'Délai complément à échéance J-3 — contenu',
 'Alerte automatique 3 jours avant l''échéance du délai de réception d''un complément d''information. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_investigation_j3',
 'ALERTE : Investigation à échéance dans 3 jours — {numero}',
 'Délai investigation à échéance J-3 — sujet',
 'Alerte automatique 3 jours avant l''échéance du délai réglementaire d''investigation. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_investigation_j3',
 'L''investigation du dossier {numero} arrive à échéance dans 3 jours. Anticiper une éventuelle prolongation avec le CGEA et le CGE.',
 'Délai investigation à échéance J-3 — contenu',
 'Alerte automatique 3 jours avant l''échéance du délai réglementaire d''investigation. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_demande_documents',
 'ALERTE : Délai de réponse à une demande de documents dépassé — {numero}',
 'Délai demande documents dépassé — sujet',
 'Alerte automatique quand le délai de réponse à une demande de documents est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_demande_documents',
 'Le délai de réponse du destinataire à une demande de documents est dépassé pour le dossier {numero}. Une escalade peut être engagée.',
 'Délai demande documents dépassé — contenu',
 'Alerte automatique quand le délai de réponse à une demande de documents est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_demande_documents_j3',
 'ALERTE : Délai de réponse à une demande de documents à échéance dans 3 jours — {numero}',
 'Délai demande documents à échéance J-3 — sujet',
 'Alerte automatique 3 jours avant l''échéance du délai de réponse à une demande de documents. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_demande_documents_j3',
 'Le délai de réponse du destinataire à une demande de documents arrive à échéance dans 3 jours pour le dossier {numero}.',
 'Délai demande documents à échéance J-3 — contenu',
 'Alerte automatique 3 jours avant l''échéance du délai de réponse à une demande de documents. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0);
```

- [ ] **Step 2: Vérifier que les 10 lignes sont bien insérées**

```bash
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Puis, avec la stack Docker locale démarrée (`docker compose up -d`), vérifier
directement en base :

```bash
docker exec -it asce_postgres psql -U postgres -d univers_audits -c \
  "SELECT config_key FROM portal_config WHERE config_key LIKE 'notif_%_j3' OR config_key LIKE '%demande_documents%' ORDER BY config_key;"
```

Attendu : exactement les 10 clés listées dans l'étape 1 (nom de conteneur et
base de données à ajuster si différents de la convention du dépôt — voir
`docker-compose.yml`).

- [ ] **Step 3: Suite complète, vérification finale du sous-chantier**

```bash
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Attendu : 517/517 tests, 0 échec — identique à la fin de la Task 3 (cette
task n'ajoute aucun nouveau test, seulement du contenu de données).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/changelog/migrations/012-seed-notification-templates-j3.sql
git commit -m "feat(alertes-delai): modeles de texte pour les alertes J-3 et demande-documents"
```
