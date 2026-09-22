# Escalade automatique vers CGEA/CGE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quand un dossier reste en dépassement au-delà d'un délai de grâce
(après l'alerte "à échéance" déjà envoyée à l'agent en charge), notifier
automatiquement tous les agents actifs ayant le rôle CGEA ou CGE.

**Architecture:** Nouvelle méthode planifiée `escaladeVersSuperieurs()` dans
`NotificationServiceImpl`, séparée de `sendDeadlineAlerts()`. Résout les
destinataires via une nouvelle méthode `KeycloakAdminService.getUserIdsByRole`,
réutilise les 4 requêtes "overdue" existantes sous une forme "au-delà du délai
de grâce", et crée une `Notification` par destinataire résolu (fan-out).

**Tech Stack:** Spring Boot 3, Spring Data JPA, Liquibase, Keycloak Admin
Client 25.0.2, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-22-escalade-automatique-design.md`

## Global Constraints

- Cible : diffusion uniforme à tous les agents actifs (`Agent.actif = true`)
  ayant le rôle Keycloak `CGEA` ou `CGE`, dédupliqués si un agent cumule les
  deux rôles — sur les 4 catégories de dépassement, sans distinction.
- Déclencheur : délai de grâce configurable via `ParametreDelai` (code
  `ESCALADE_DELAI_GRACE`, valeur 3, `jours_ouvrables = FALSE`) — calendaire,
  pas un nouveau calcul en jours ouvrables.
- **Toute migration ajoutant des valeurs `NotificationType` DOIT élargir le
  CHECK constraint `notification_type_check` dans le MÊME changeset** — leçon
  du Critical trouvé par la revue finale du sous-chantier précédent
  (`alertes-delai-j3`, migration `013`). Ne jamais éditer les migrations déjà
  `EXECUTED` (`001` à `013`) — tout correctif est une nouvelle migration
  additive (`014`).
- Déduplication : `AR`/`COMPLEMENT`/`INVESTIGATION` réutilisent la méthode
  déjà existante `existsByDossierIdAndType` (dédup à vie, cohérent avec leurs
  équivalents J-3 déjà acceptés comme tels). `DEMANDE_DOCUMENTS` réutilise
  `existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter` (dédup par récence,
  déjà existante depuis le sous-chantier précédent) — **aucune nouvelle
  méthode de dédup à créer**, les deux existent déjà dans
  `NotificationRepository`.
- `KeycloakAdminService` n'a aucun test aujourd'hui — la mocker comme classe
  concrète standard dans les tests (patron déjà utilisé pour
  `PortalConfigService` dans ce dépôt, pas d'interface requise).
- Canal `NotificationChannel.PORTAL`, comme l'existant.

---

### Task 1: Schéma, requêtes et intégration Keycloak

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/enums/NotificationType.java`
- Create: `src/main/resources/db/changelog/migrations/014-escalade-automatique.sql`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/InvestigationRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/DemandeDocumentsRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/KeycloakAdminService.java`

**Interfaces:**
- Produces: `NotificationType.ESCALADE_AR`, `ESCALADE_COMPLEMENT`,
  `ESCALADE_INVESTIGATION`, `ESCALADE_DEMANDE_DOCUMENTS` (consommés par
  Task 2 et Task 3).
- Produces: `DossierRepository.findAcknowledgmentsOverdueBeyondGrace(Instant graceThreshold): List<Dossier>`,
  `DossierRepository.findComplementsOverdueBeyondGrace(Instant graceThreshold): List<Dossier>`
  (consommés par Task 2).
- Produces: `InvestigationRepository.findOverdueBeyondGrace(Instant graceThreshold): List<Investigation>`
  (consommé par Task 2).
- Produces: `DemandeDocumentsRepository.findOverdueBeyondGrace(Instant graceThreshold): List<DemandeDocuments>`
  (consommé par Task 3).
- Produces: `KeycloakAdminService.getUserIdsByRole(String roleName): List<String>`
  (consommé par Task 2).
- Produces : le code `ParametreDelai` `ESCALADE_DELAI_GRACE` (consommé par
  Task 2 via `parametreDelaiService.resolveDelaiJours("ESCALADE_DELAI_GRACE")`).

- [ ] **Step 1: Ajouter les 4 nouveaux types d'alerte**

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
    DEMANDE_DOCUMENTS_ALERT_J3,
    ESCALADE_AR,
    ESCALADE_COMPLEMENT,
    ESCALADE_INVESTIGATION,
    ESCALADE_DEMANDE_DOCUMENTS
}
```

- [ ] **Step 2: Créer la migration (constraint + seed du délai de grâce)**

Créer `src/main/resources/db/changelog/migrations/014-escalade-automatique.sql` :

```sql
--liquibase formatted sql
--changeset dev:014-escalade-automatique

-- 4 nouveaux NotificationType pour l'escalade automatique vers CGEA/CGE.
-- Elargissement du CHECK constraint DANS LE MEME changeset que l'ajout des
-- valeurs -- lecon du Critical trouve par la revue finale du sous-chantier
-- precedent (alertes-delai-j3, migration 013) : un ajout de valeur d'enum
-- sans elargissement synchrone du CHECK constraint SQL fait echouer et
-- annuler toute transaction qui tente de l'utiliser.
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
        'ESCALADE_DEMANDE_DOCUMENTS'));

-- Delai de grace avant escalade (jours calendaires entre le depassement
-- d'une echeance et la notification automatique a CGEA/CGE).
INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'ESCALADE_DELAI_GRACE', 'Délai de grâce avant escalade automatique vers CGEA/CGE', 3, FALSE, TRUE, 0, now());
```

- [ ] **Step 3: Ajouter les 2 requêtes "au-delà du délai de grâce" sur `DossierRepository`**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java`.
Trouver la méthode `findInvestigationsDueWithin` :

```java
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

Juste après cette méthode (avant `countByStatus`), insérer :

```java

    @Query("""
            SELECT d FROM Dossier d
            WHERE d.acknowledgmentDeadline < :graceThreshold
            AND d.status NOT IN (
                gov.bf.ascelc.univers_audits.enums.DossierStatus.CLOS,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.CLASSE,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.IRRECEVABLE,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.TRANSFERE,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.ORIENTEE_ADMINISTRATIF
            )
            ORDER BY d.acknowledgmentDeadline ASC
            """)
    List<Dossier> findAcknowledgmentsOverdueBeyondGrace(
            @Param("graceThreshold") Instant graceThreshold);

    @Query("""
            SELECT d FROM Dossier d
            WHERE d.status = gov.bf.ascelc.univers_audits.enums.DossierStatus.EN_ATTENTE_COMPLEMENT
            AND d.additionalInfoDeadline < :graceThreshold
            ORDER BY d.additionalInfoDeadline ASC
            """)
    List<Dossier> findComplementsOverdueBeyondGrace(
            @Param("graceThreshold") Instant graceThreshold);
```

- [ ] **Step 4: Ajouter la requête "au-delà du délai de grâce" sur `InvestigationRepository`**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/repository/InvestigationRepository.java`.
Trouver la méthode `findOverdue` :

```java
    @Query("""
            SELECT DISTINCT i FROM Investigation i
            LEFT JOIN FETCH i.members m
            LEFT JOIN FETCH m.agent
            WHERE i.status = 'IN_PROGRESS'
            AND (
                (i.extendedDeadline IS NOT NULL
                 AND i.extendedDeadline < :now)
                OR
                (i.extendedDeadline IS NULL
                 AND i.plannedEndDate IS NOT NULL
                 AND i.plannedEndDate < :now)
            )
            ORDER BY i.plannedEndDate ASC
            """)
    List<Investigation> findOverdue(@Param("now") Instant now);
```

Juste après cette méthode, insérer (avec `LEFT JOIN FETCH i.dossier` en plus
de `findOverdue` — évite un aller-retour lazy supplémentaire par ligne quand
le service accède ensuite à `investigation.getDossier()`) :

```java

    @Query("""
            SELECT DISTINCT i FROM Investigation i
            LEFT JOIN FETCH i.dossier
            WHERE i.status = 'IN_PROGRESS'
            AND (
                (i.extendedDeadline IS NOT NULL
                 AND i.extendedDeadline < :graceThreshold)
                OR
                (i.extendedDeadline IS NULL
                 AND i.plannedEndDate IS NOT NULL
                 AND i.plannedEndDate < :graceThreshold)
            )
            ORDER BY i.plannedEndDate ASC
            """)
    List<Investigation> findOverdueBeyondGrace(
            @Param("graceThreshold") Instant graceThreshold);
```

- [ ] **Step 5: Ajouter la requête "au-delà du délai de grâce" sur `DemandeDocumentsRepository`**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/repository/DemandeDocumentsRepository.java`.
Trouver la méthode `findDueWithin` :

```java
    @Query("""
            SELECT dd FROM DemandeDocuments dd
            JOIN FETCH dd.investigation i
            JOIN FETCH i.dossier
            WHERE dd.received = false
            AND dd.deadline BETWEEN :now AND :in3Days
            ORDER BY dd.deadline ASC
            """)
    List<DemandeDocuments> findDueWithin(
            @Param("now") Instant now, @Param("in3Days") Instant in3Days);
```

Juste après cette méthode (avant la dernière accolade fermante de
l'interface), insérer :

```java

    @Query("""
            SELECT dd FROM DemandeDocuments dd
            JOIN FETCH dd.investigation i
            JOIN FETCH i.dossier
            WHERE dd.received = false
            AND dd.deadline < :graceThreshold
            ORDER BY dd.deadline ASC
            """)
    List<DemandeDocuments> findOverdueBeyondGrace(
            @Param("graceThreshold") Instant graceThreshold);
```

- [ ] **Step 6: Ajouter `getUserIdsByRole` à `KeycloakAdminService`**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/service/KeycloakAdminService.java`.
Trouver la méthode `getAvailableRoles` :

```java
    public List<String> getAvailableRoles() {
        return realmResource().roles().list().stream()
                .map(RoleRepresentation::getName)
                .filter(name -> !SYSTEM_ROLES.contains(name))
                .sorted()
                .toList();
    }
```

Juste après, insérer :

```java

    /**
     * Retourne les keycloakId de tous les utilisateurs affectés au rôle
     * realm donné. Ne lève jamais — un rôle introuvable ou une erreur
     * Keycloak transitoire renvoie une liste vide plutôt que de faire
     * échouer l'appelant (typiquement un job planifié qui ne doit pas
     * planter à cause d'un souci Keycloak passager).
     */
    public List<String> getUserIdsByRole(String roleName) {
        try {
            return realmResource().roles().get(roleName).getUserMembers()
                    .stream()
                    .map(UserRepresentation::getId)
                    .toList();
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.warn("Rôle Keycloak introuvable : {}", roleName);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Erreur lecture des membres du rôle '{}': {}", roleName, e.getMessage());
            return Collections.emptyList();
        }
    }
```

(`UserRepresentation` et `Collections` sont déjà importés dans ce fichier —
aucun nouvel import requis. Signature vérifiée directement dans le jar
`keycloak-admin-client-25.0.2.jar` : `RoleResource.getUserMembers(): List<UserRepresentation>`.)

- [ ] **Step 7: Compiler et vérifier l'absence de régression**

```bash
./mvnw.cmd -q compile
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Attendu : compilation propre, suite complète toujours verte (528/528 — cette
task n'ajoute aucun test, elle ajoute uniquement du schéma/repository/
intégration Keycloak inertes tant que rien ne les appelle).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/NotificationType.java \
        src/main/resources/db/changelog/migrations/014-escalade-automatique.sql \
        src/main/java/gov/bf/ascelc/univers_audits/repository/DossierRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/InvestigationRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/DemandeDocumentsRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/KeycloakAdminService.java
git commit -m "feat(escalade): schema, requetes et resolution des roles CGEA/CGE"
```

---

### Task 2: Escalade AR / complément / investigation

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java`

**Interfaces:**
- Consumes: `DossierRepository.findAcknowledgmentsOverdueBeyondGrace`/`findComplementsOverdueBeyondGrace`,
  `InvestigationRepository.findOverdueBeyondGrace`, `KeycloakAdminService.getUserIdsByRole`
  (Task 1). `ParametreDelaiService.resolveDelaiJours(String): int` (existant,
  déjà utilisé ailleurs dans le dépôt). `AgentRepository.findByKeycloakId(String): Optional<Agent>`
  (existant). `NotificationType.ESCALADE_AR`/`ESCALADE_COMPLEMENT`/`ESCALADE_INVESTIGATION`
  (Task 1).
- Produces: `NotificationServiceImpl` reçoit 3 nouveaux champs constructeur
  (ordre : après `demandeDocumentsRepository`) : `InvestigationRepository investigationRepository`,
  `ParametreDelaiService parametreDelaiService`, `KeycloakAdminService keycloakAdminService`.
  Produces les méthodes privées `resolveSuperieurs(): List<Agent>`,
  `escaladeDossiers(List<Dossier>, NotificationType, String, String, List<Agent>): int`,
  `creerEscalades(Dossier, DemandeDocuments, NotificationType, String, String, List<Agent>): void`,
  `nomAgentEnCharge(Dossier): String` — Task 3 réutilise `creerEscalades` et
  ajoute son propre appelant pour `DemandeDocuments`.

- [ ] **Step 1: Écrire les tests (échouent d'abord)**

Ouvrir `src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java`.

Ajouter les imports, avec les autres imports `gov.bf.ascelc...` :

```java
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.service.KeycloakAdminService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
```

Ajouter les 3 nouveaux champs mock, juste après `demandeDocumentsRepository` :

```java
    @Mock private InvestigationRepository investigationRepository;
    @Mock private ParametreDelaiService parametreDelaiService;
    @Mock private KeycloakAdminService keycloakAdminService;
```

Dans `setUp()`, ajouter les stubs par défaut (délai de grâce neutre, aucun
CGEA/CGE résolu par défaut, aucune requête "beyond grace" ne renvoie rien) :

```java
        lenient().when(parametreDelaiService.resolveDelaiJours("ESCALADE_DELAI_GRACE"))
                .thenReturn(3);
        lenient().when(keycloakAdminService.getUserIdsByRole(anyString())).thenReturn(List.of());
        lenient().when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of());
        lenient().when(dossierRepository.findComplementsOverdueBeyondGrace(any())).thenReturn(List.of());
        lenient().when(investigationRepository.findOverdueBeyondGrace(any())).thenReturn(List.of());
```

À la fin de la classe (avant la dernière accolade fermante), ajouter :

```java

    // ── Nouveau : escalade automatique vers CGEA/CGE ──

    private Agent buildSuperieur(String keycloakId, String matricule) {
        return Agent.builder()
                .id(UUID.randomUUID())
                .keycloakId(keycloakId)
                .matricule(matricule)
                .firstName("Prénom")
                .lastName("Nom")
                .actif(true)
                .build();
    }

    @Test
    void escaladeVersSuperieurs_notifieChaqueSuperieurResoluPourUnDepassementAR() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0020").build();
        Agent cgea = buildSuperieur("kc-cgea", "M100");
        Agent cge = buildSuperieur("kc-cge", "M101");
        when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGEA")).thenReturn(List.of("kc-cgea"));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cgea")).thenReturn(java.util.Optional.of(cgea));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_AR
                        && n.getDossier() == dossier
                        && "kc-cgea".equals(n.getRecipient())));
        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_AR
                        && n.getDossier() == dossier
                        && "kc-cge".equals(n.getRecipient())));
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_escalade_ar"), anyMap());
        verify(portalConfigService).resolveNotificationText(eq("notif_content_escalade_ar"), anyMap());
    }

    @Test
    void escaladeVersSuperieurs_dedupliqueUnAgentCumulantCgeaEtCge() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0021").build();
        Agent cumulard = buildSuperieur("kc-cumulard", "M102");
        when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGEA")).thenReturn(List.of("kc-cumulard"));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cumulard"));
        when(agentRepository.findByKeycloakId("kc-cumulard")).thenReturn(java.util.Optional.of(cumulard));

        service.escaladeVersSuperieurs();

        verify(notificationRepository, times(1)).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_AR
                        && "kc-cumulard".equals(n.getRecipient())));
    }

    @Test
    void escaladeVersSuperieurs_notifieChaqueSuperieurPourUnDepassementComplement() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0022").build();
        Agent cge = buildSuperieur("kc-cge", "M103");
        when(dossierRepository.findComplementsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_COMPLEMENT
                        && n.getDossier() == dossier
                        && "kc-cge".equals(n.getRecipient())));
    }

    @Test
    void escaladeVersSuperieurs_resoutLeDossierDUneInvestigationEtNotifie() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0023").build();
        Investigation investigation = Investigation.builder()
                .id(UUID.randomUUID()).dossier(dossier).build();
        Agent cge = buildSuperieur("kc-cge", "M104");
        when(investigationRepository.findOverdueBeyondGrace(any())).thenReturn(List.of(investigation));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_INVESTIGATION
                        && n.getDossier() == dossier
                        && "kc-cge".equals(n.getRecipient())));
    }

    @Test
    void escaladeVersSuperieurs_doesNotDuplicateAlreadyEscaladedDossier() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0024").build();
        Agent cge = buildSuperieur("kc-cge", "M105");
        when(dossierRepository.findAcknowledgmentsOverdueBeyondGrace(any())).thenReturn(List.of(dossier));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));
        when(notificationRepository.existsByDossierIdAndType(
                dossier.getId(), NotificationType.ESCALADE_AR)).thenReturn(true);

        service.escaladeVersSuperieurs();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_AR));
    }

    @Test
    void escaladeVersSuperieurs_ignoreUnAgentKeycloakSansCorrespondanceEnBase() {
        // Le seul keycloakId resolu par Keycloak n'a pas d'Agent correspondant
        // en base -> resolveSuperieurs() renvoie une liste vide -> la garde de
        // sortie anticipee empeche toute requete d'echeance et toute sauvegarde.
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-inconnu"));
        when(agentRepository.findByKeycloakId("kc-inconnu")).thenReturn(java.util.Optional.empty());

        service.escaladeVersSuperieurs();

        verify(notificationRepository, never()).save(any());
        verify(dossierRepository, never()).findAcknowledgmentsOverdueBeyondGrace(any());
    }

    @Test
    void escaladeVersSuperieurs_ignoreUnAgentInactif() {
        // Meme raisonnement : le seul keycloakId resolu correspond a un Agent
        // inactif, filtre par resolveSuperieurs() -> liste vide -> sortie
        // anticipee.
        Agent inactif = buildSuperieur("kc-inactif", "M106");
        inactif.setActif(false);
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-inactif"));
        when(agentRepository.findByKeycloakId("kc-inactif")).thenReturn(java.util.Optional.of(inactif));

        service.escaladeVersSuperieurs();

        verify(notificationRepository, never()).save(any());
        verify(dossierRepository, never()).findAcknowledgmentsOverdueBeyondGrace(any());
    }

    @Test
    void escaladeVersSuperieurs_neInterrogeAucuneEcheanceSiAucunSuperieurResolu() {
        // Garde de sortie anticipee : sans agent CGEA/CGE resolu, inutile
        // d'interroger les echeances (rien ne pourrait etre notifie).
        service.escaladeVersSuperieurs();

        verify(dossierRepository, never()).findAcknowledgmentsOverdueBeyondGrace(any());
        verify(parametreDelaiService).resolveDelaiJours("ESCALADE_DELAI_GRACE");
    }

    @Test
    void escaladeVersSuperieurs_calculeLeSeuilAvecLeDelaiConfigure() {
        Agent cge = buildSuperieur("kc-cge", "M110");
        when(parametreDelaiService.resolveDelaiJours("ESCALADE_DELAI_GRACE")).thenReturn(5);
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(dossierRepository).findAcknowledgmentsOverdueBeyondGrace(any());
        verify(parametreDelaiService).resolveDelaiJours("ESCALADE_DELAI_GRACE");
    }
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./mvnw.cmd -q test -Dtest=NotificationServiceImplTest
```

Attendu : ÉCHEC de compilation — `NotificationServiceImpl` n'a pas encore de
méthode `escaladeVersSuperieurs()` ni les champs `investigationRepository`/
`parametreDelaiService`/`keycloakAdminService` compatibles avec
`@InjectMocks`. C'est attendu.

- [ ] **Step 3: Implémenter `escaladeVersSuperieurs()` et ses helpers**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java`.

Ajouter les imports, avec les autres imports `gov.bf.ascelc...` (avant
`lombok.RequiredArgsConstructor`) :

```java
import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.service.KeycloakAdminService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
```

Ajouter les imports de collections, en complétant le bloc `java.util.*`
existant :

```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
```

Trouver le bloc de champs :

```java
    private final NotificationRepository notificationRepository;
    private final DossierRepository      dossierRepository;
    private final AgentRepository        agentRepository;
    private final DossierDetailsMapper   detailsMapper;
    private final DossierAccessGuard     accessGuard;
    private final PortalConfigService    portalConfigService;
    private final DeadlineCalculator     deadlineCalculator;
    private final DemandeDocumentsRepository demandeDocumentsRepository;
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
    private final DemandeDocumentsRepository demandeDocumentsRepository;
    private final InvestigationRepository    investigationRepository;
    private final ParametreDelaiService      parametreDelaiService;
    private final KeycloakAdminService       keycloakAdminService;
```

Trouver le début de la méthode privée `doSend` (juste après la fin de
`sendDeadlineAlerts()`) :

```java
    private void doSend(Notification notif) {
```

Juste avant cette ligne, insérer la nouvelle méthode planifiée et ses
helpers :

```java
    @Override
    @Scheduled(cron = "0 30 8 * * MON-FRI")
    @Transactional
    public void escaladeVersSuperieurs() {
        log.info("[Notification] Escalade automatique vers CGEA/CGE...");

        int delaiGraceJours = parametreDelaiService.resolveDelaiJours("ESCALADE_DELAI_GRACE");
        Instant graceThreshold = deadlineCalculator.addCalendarDays(Instant.now(), -delaiGraceJours);

        List<Agent> superieurs = resolveSuperieurs();
        if (superieurs.isEmpty()) {
            log.warn("[Notification] Aucun agent CGEA/CGE résolu — escalade ignorée pour ce passage");
            return;
        }

        int escaladesAR = escaladeDossiers(
                dossierRepository.findAcknowledgmentsOverdueBeyondGrace(graceThreshold),
                NotificationType.ESCALADE_AR,
                "notif_subject_escalade_ar", "notif_content_escalade_ar", superieurs);

        int escaladesComplement = escaladeDossiers(
                dossierRepository.findComplementsOverdueBeyondGrace(graceThreshold),
                NotificationType.ESCALADE_COMPLEMENT,
                "notif_subject_escalade_complement", "notif_content_escalade_complement", superieurs);

        List<Dossier> dossiersInvestigation = investigationRepository
                .findOverdueBeyondGrace(graceThreshold).stream()
                .map(Investigation::getDossier)
                .toList();
        int escaladesInvestigation = escaladeDossiers(
                dossiersInvestigation,
                NotificationType.ESCALADE_INVESTIGATION,
                "notif_subject_escalade_investigation", "notif_content_escalade_investigation", superieurs);

        log.info("[Notification] Escalades traitées — {} AR, {} compléments, {} investigations",
                escaladesAR, escaladesComplement, escaladesInvestigation);
    }

    private List<Agent> resolveSuperieurs() {
        Set<String> keycloakIds = new LinkedHashSet<>();
        keycloakIds.addAll(keycloakAdminService.getUserIdsByRole("CGEA"));
        keycloakIds.addAll(keycloakAdminService.getUserIdsByRole("CGE"));

        List<Agent> superieurs = new ArrayList<>();
        for (String keycloakId : keycloakIds) {
            agentRepository.findByKeycloakId(keycloakId)
                    .filter(Agent::getActif)
                    .ifPresent(superieurs::add);
        }
        return superieurs;
    }

    private int escaladeDossiers(List<Dossier> dossiers, NotificationType type,
                                  String subjectKey, String contentKey, List<Agent> superieurs) {
        int count = 0;
        for (Dossier dossier : dossiers) {
            boolean dejaEscalade = notificationRepository
                    .existsByDossierIdAndType(dossier.getId(), type);
            if (!dejaEscalade) {
                creerEscalades(dossier, null, type, subjectKey, contentKey, superieurs);
                count++;
            }
        }
        return count;
    }

    private void creerEscalades(Dossier dossier, DemandeDocuments demande,
                                 NotificationType type, String subjectKey, String contentKey,
                                 List<Agent> superieurs) {
        Map<String, String> placeholders = Map.of(
                "numero", dossier.getNumber(),
                "agentEnCharge", nomAgentEnCharge(dossier));

        for (Agent superieur : superieurs) {
            Notification escalade = Notification.builder()
                    .dossier(dossier)
                    .demandeDocuments(demande)
                    .type(type)
                    .channel(NotificationChannel.PORTAL)
                    .recipient(superieur.getKeycloakId())
                    .subject(portalConfigService.resolveNotificationText(subjectKey, placeholders))
                    .content(portalConfigService.resolveNotificationText(contentKey, placeholders))
                    .scheduledAt(Instant.now())
                    .build();
            notificationRepository.save(escalade);
        }
        log.warn("[Notification] Escalade {} créée — dossier: {}", type, dossier.getNumber());
    }

    private String nomAgentEnCharge(Dossier dossier) {
        Agent agent = dossier.getAgentInCharge();
        return agent != null ? agent.getNomComplet() : "agent non identifié";
    }

```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

```bash
./mvnw.cmd -q test -Dtest=NotificationServiceImplTest
```

Attendu : 22/22 tests passent (13 existants + 9 nouveaux de cette task).

- [ ] **Step 5: Suite complète**

```bash
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Attendu : aucune régression (528 tests précédents + 9 nouveaux = 537, 0 échec).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java
git commit -m "feat(escalade): escalade AR, complement et investigation vers CGEA/CGE"
```

---

### Task 3: Escalade demande de documents

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java`

**Interfaces:**
- Consumes: `DemandeDocumentsRepository.findOverdueBeyondGrace` (Task 1),
  `NotificationType.ESCALADE_DEMANDE_DOCUMENTS` (Task 1),
  `NotificationRepository.existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter`
  (déjà existant, sous-chantier précédent), `creerEscalades(Dossier, DemandeDocuments, NotificationType, String, String, List<Agent>)`
  et `resolveSuperieurs()` (Task 2 — réutilisés tels quels, aucune modification
  de signature).

- [ ] **Step 1: Ajouter les tests (échouent d'abord)**

Ouvrir `src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java`.

Dans `setUp()`, ajouter le stub par défaut pour la nouvelle requête :

```java
        lenient().when(demandeDocumentsRepository.findOverdueBeyondGrace(any())).thenReturn(List.of());
```

À la fin de la classe (avant la dernière accolade fermante), ajouter :

```java

    @Test
    void escaladeVersSuperieurs_notifieChaqueSuperieurPourUneDemandeDocumentsEnDepassement() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0027").build();
        DemandeDocuments demande = buildDemande(dossier);
        Agent cge = buildSuperieur("kc-cge", "M107");
        when(demandeDocumentsRepository.findOverdueBeyondGrace(any())).thenReturn(List.of(demande));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));

        service.escaladeVersSuperieurs();

        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_DEMANDE_DOCUMENTS
                        && n.getDossier() == dossier
                        && n.getDemandeDocuments() == demande
                        && "kc-cge".equals(n.getRecipient())));
        verify(portalConfigService).resolveNotificationText(eq("notif_subject_escalade_demande_documents"), anyMap());
        verify(portalConfigService).resolveNotificationText(eq("notif_content_escalade_demande_documents"), anyMap());
    }

    @Test
    void escaladeVersSuperieurs_doesNotDuplicateDemandeDocumentsEscaladeeDansLeMemeCycle() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0028").build();
        DemandeDocuments demande = buildDemande(dossier);
        Agent cge = buildSuperieur("kc-cge", "M108");
        when(demandeDocumentsRepository.findOverdueBeyondGrace(any())).thenReturn(List.of(demande));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));
        when(notificationRepository.existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                eq(demande.getId()), eq(NotificationType.ESCALADE_DEMANDE_DOCUMENTS), any()))
                .thenReturn(true);

        service.escaladeVersSuperieurs();

        verify(notificationRepository, never()).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_DEMANDE_DOCUMENTS));
    }

    @Test
    void escaladeVersSuperieurs_reEscaladeApresUnNouveauCycleDeSentAt() {
        // Meme raisonnement que sendDeadlineAlerts_reAlertsDemandeDocumentsAfterEscalationResetsSentAt
        // (sous-chantier precedent) : une DemandeDocuments escaladee manuellement
        // (RELANCE, SOMMATION...) reinitialise sentAt sur la meme ligne. La
        // dedup doit s'appuyer sur le sentAt COURANT, pas sur l'existence a vie
        // d'une notification ESCALADE_DEMANDE_DOCUMENTS pour cet id.
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).number("2026-0029").build();
        Instant currentCycleSentAt = Instant.now();
        DemandeDocuments demande = buildDemande(dossier);
        demande.setSentAt(currentCycleSentAt);
        Agent cge = buildSuperieur("kc-cge", "M109");
        when(demandeDocumentsRepository.findOverdueBeyondGrace(any())).thenReturn(List.of(demande));
        when(keycloakAdminService.getUserIdsByRole("CGE")).thenReturn(List.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(java.util.Optional.of(cge));
        when(notificationRepository.existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                eq(demande.getId()), eq(NotificationType.ESCALADE_DEMANDE_DOCUMENTS), eq(currentCycleSentAt)))
                .thenReturn(false);

        service.escaladeVersSuperieurs();

        verify(notificationRepository).existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                demande.getId(), NotificationType.ESCALADE_DEMANDE_DOCUMENTS, currentCycleSentAt);
        verify(notificationRepository).save(argThat(n ->
                n.getType() == NotificationType.ESCALADE_DEMANDE_DOCUMENTS
                        && n.getDemandeDocuments() == demande));
    }
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./mvnw.cmd -q test -Dtest=NotificationServiceImplTest
```

Attendu : ÉCHEC — `escaladeVersSuperieurs()` n'appelle pas encore
`demandeDocumentsRepository.findOverdueBeyondGrace`, donc aucune notification
`ESCALADE_DEMANDE_DOCUMENTS` n'est créée. C'est attendu.

- [ ] **Step 3: Ajouter le bloc `DemandeDocuments` dans `escaladeVersSuperieurs()`**

Ouvrir `src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java`.

Trouver la fin de `escaladeVersSuperieurs()` :

```java
        List<Dossier> dossiersInvestigation = investigationRepository
                .findOverdueBeyondGrace(graceThreshold).stream()
                .map(Investigation::getDossier)
                .toList();
        int escaladesInvestigation = escaladeDossiers(
                dossiersInvestigation,
                NotificationType.ESCALADE_INVESTIGATION,
                "notif_subject_escalade_investigation", "notif_content_escalade_investigation", superieurs);

        log.info("[Notification] Escalades traitées — {} AR, {} compléments, {} investigations",
                escaladesAR, escaladesComplement, escaladesInvestigation);
    }
```

Le remplacer par (ajoute le bloc `DemandeDocuments` et enrichit le
`log.info` final avec son compteur) :

```java
        List<Dossier> dossiersInvestigation = investigationRepository
                .findOverdueBeyondGrace(graceThreshold).stream()
                .map(Investigation::getDossier)
                .toList();
        int escaladesInvestigation = escaladeDossiers(
                dossiersInvestigation,
                NotificationType.ESCALADE_INVESTIGATION,
                "notif_subject_escalade_investigation", "notif_content_escalade_investigation", superieurs);

        int escaladesDemandeDocuments = escaladeDemandesDocuments(
                demandeDocumentsRepository.findOverdueBeyondGrace(graceThreshold), superieurs);

        log.info("[Notification] Escalades traitées — {} AR, {} compléments, {} investigations, "
                        + "{} demandes documents",
                escaladesAR, escaladesComplement, escaladesInvestigation, escaladesDemandeDocuments);
    }

    private int escaladeDemandesDocuments(List<DemandeDocuments> demandes, List<Agent> superieurs) {
        int count = 0;
        for (DemandeDocuments demande : demandes) {
            boolean dejaEscalade = notificationRepository
                    .existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                            demande.getId(),
                            NotificationType.ESCALADE_DEMANDE_DOCUMENTS,
                            demande.getSentAt());
            if (!dejaEscalade) {
                creerEscalades(demande.getInvestigation().getDossier(), demande,
                        NotificationType.ESCALADE_DEMANDE_DOCUMENTS,
                        "notif_subject_escalade_demande_documents",
                        "notif_content_escalade_demande_documents", superieurs);
                count++;
            }
        }
        return count;
    }
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

```bash
./mvnw.cmd -q test -Dtest=NotificationServiceImplTest
```

Attendu : 25/25 tests passent.

- [ ] **Step 5: Suite complète**

```bash
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Attendu : 540 tests (537 + 3 nouveaux), 0 échec.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/NotificationServiceImplTest.java
git commit -m "feat(escalade): escalade demande de documents vers CGEA/CGE"
```

---

### Task 4: Contenu des alertes (seed `portal_config`) et vérification finale

**Files:**
- Create: `src/main/resources/db/changelog/migrations/015-seed-notification-templates-escalade.sql`

**Interfaces:**
- Consumes: les 8 clés `config_key` appelées par `NotificationServiceImpl`
  depuis les Tasks 2 et 3 : `notif_subject_escalade_ar`,
  `notif_content_escalade_ar`, `notif_subject_escalade_complement`,
  `notif_content_escalade_complement`, `notif_subject_escalade_investigation`,
  `notif_content_escalade_investigation`, `notif_subject_escalade_demande_documents`,
  `notif_content_escalade_demande_documents`.

- [ ] **Step 1: Créer la migration de seed**

Créer `src/main/resources/db/changelog/migrations/015-seed-notification-templates-escalade.sql` :

```sql
--liquibase formatted sql
--changeset dev:015-seed-notification-templates-escalade

-- Modeles de texte pour les 4 nouvelles alertes d'escalade automatique
-- vers CGEA/CGE (voir docs/superpowers/specs/2026-09-22-escalade-automatique-design.md).
-- Meme structure de colonnes que 005-seed-portal-config.sql/012-seed-notification-templates-j3.sql.
-- PortalConfig n'etend pas AuditEntity : id/updated_at/version doivent etre
-- fournis explicitement (pas de DEFAULT au niveau base).

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
(gen_random_uuid(), 'notif_subject_escalade_ar',
 'ESCALADE : Accusé de réception toujours en retard — {numero}',
 'Escalade AR — sujet',
 'Alerte automatique vers CGEA/CGE quand un dossier reste en dépassement de délai d''accusé de réception au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_escalade_ar',
 'Le dossier {numero} dépasse le délai légal d''accusé de réception depuis plusieurs jours, malgré l''alerte envoyée à {agentEnCharge}. Une intervention de la hiérarchie est requise.',
 'Escalade AR — contenu',
 'Alerte automatique vers CGEA/CGE quand un dossier reste en dépassement de délai d''accusé de réception au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_escalade_complement',
 'ESCALADE : Complément d''information toujours en retard — {numero}',
 'Escalade complément — sujet',
 'Alerte automatique vers CGEA/CGE quand un dossier reste en attente de complément d''information au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_escalade_complement',
 'Le dossier {numero} reste en attente de complément d''information au-delà du délai de grâce, malgré l''alerte envoyée à {agentEnCharge}. Une intervention de la hiérarchie est requise.',
 'Escalade complément — contenu',
 'Alerte automatique vers CGEA/CGE quand un dossier reste en attente de complément d''information au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_escalade_investigation',
 'ESCALADE : Investigation toujours en dépassement — {numero}',
 'Escalade investigation — sujet',
 'Alerte automatique vers CGEA/CGE quand une investigation dépasse son échéance au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_escalade_investigation',
 'L''investigation du dossier {numero}, menée par {agentEnCharge}, dépasse son échéance au-delà du délai de grâce. Une intervention de la hiérarchie est requise.',
 'Escalade investigation — contenu',
 'Alerte automatique vers CGEA/CGE quand une investigation dépasse son échéance au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_escalade_demande_documents',
 'ESCALADE : Demande de documents toujours sans réponse — {numero}',
 'Escalade demande documents — sujet',
 'Alerte automatique vers CGEA/CGE quand une demande de documents reste sans réponse au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_escalade_demande_documents',
 'La demande de documents du dossier {numero}, suivie par {agentEnCharge}, reste sans réponse au-delà du délai de grâce. Une intervention de la hiérarchie est requise.',
 'Escalade demande documents — contenu',
 'Alerte automatique vers CGEA/CGE quand une demande de documents reste sans réponse au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0);
```

- [ ] **Step 2: Vérifier que les 8 lignes sont bien insérées**

```bash
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Puis, avec la stack Docker locale démarrée (`docker compose up -d`) :

```bash
docker exec -it asce_postgres psql -U postgres -d bd_univers_audit -c \
  "SELECT config_key FROM portal_config WHERE config_key LIKE 'notif_%_escalade_%' ORDER BY config_key;"
```

Attendu : exactement les 8 clés listées à l'étape 1.

- [ ] **Step 3: Suite complète, vérification finale du sous-chantier**

```bash
./mvnw.cmd -q test -Dspring.profiles.active=dev
```

Attendu : 540/540 tests, 0 échec — identique à la fin de la Task 3 (cette
task n'ajoute aucun nouveau test, seulement du contenu de données).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/changelog/migrations/015-seed-notification-templates-escalade.sql
git commit -m "feat(escalade): modeles de texte pour les alertes d'escalade CGEA/CGE"
```


