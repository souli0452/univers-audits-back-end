# Contrôle d'accès en écriture — `DossierServiceImpl.update()` — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Empêcher un agent AGENT_BRPD/CONSEILLER_JURIDIQUE non affecté à un dossier
d'en modifier le contenu via `PUT /{id}`, en réutilisant le contrôle d'habilitation déjà en
place pour la lecture.

**Architecture:** Un seul appel ajouté — `DossierAccessGuard.checkReadAccess(dossier)` — en
tête de `DossierServiceImpl.update()`, avant toute autre logique métier. Aucun nouveau
mécanisme.

**Tech Stack:** Spring Boot 3 / Java 17, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Un seul fichier de production touché : `DossierServiceImpl.java`. Un seul fichier de test :
  `DossierServiceImplTest.java`.
- Ne pas toucher aux 4 endpoints "file de département" ni aux 6 endpoints déjà réservés
  CGE/CGEA/ADMIN_DDIC — hors périmètre, voir la spec.
- `accessGuard.checkReadAccess(dossier)` doit être appelé avant la vérification `isClosed()`
  et avant la vérification de protection lanceur d'alerte déjà présentes dans `update()`.

Spec de référence : `docs/superpowers/specs/2026-08-02-controle-acces-ecriture-dossier-design.md`

---

### Task 1: Ajouter le contrôle d'habilitation à `update()`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java`

**Interfaces:**
- Consumes: `DossierAccessGuard.checkReadAccess(Dossier)` (déjà existant, déjà utilisé par
  `findById`/`findByAccessCode`/`enrichAndMaskDetail`-adjacent call sites dans cette même
  classe — signature inchangée, aucun nouveau code à écrire dans `DossierAccessGuard`).

- [ ] **Step 1: Ajouter l'appel au garde d'accès**

Dans `DossierServiceImpl.java`, méthode `update()` (actuellement lignes 605-630), remplacer :

```java
    @Override
    @Transactional
    public DossierResponse update(UUID dossierId, DossierUpdateRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);

        if (dossier.isClosed()) {
```

par :

```java
    @Override
    @Transactional
    public DossierResponse update(UUID dossierId, DossierUpdateRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        if (dossier.isClosed()) {
```

Le reste de la méthode (vérification `isClosed()`, protection lanceur d'alerte, mapping,
sauvegarde) reste identique.

- [ ] **Step 2: Test — agent habilité peut toujours éditer**

Ajouter dans `DossierServiceImplTest.java` (le mock `accessGuard` existe déjà dans cette
classe — `@Mock private DossierAccessGuard accessGuard;` — son comportement par défaut
Mockito pour une méthode `void` non stubbée est un no-op, donc "habilité" n'a besoin d'aucun
stub explicite) :

```java
    @Test
    void update_succeedsWhenAgentIsHabilitated() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        DossierUpdateRequest request = DossierUpdateRequest.builder()
                .object("Objet corrigé")
                .build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());
        when(securityUtils.hasRole(anyString())).thenReturn(false);

        assertThatCode(() -> service.update(dossierId, request)).doesNotThrowAnyException();

        verify(accessGuard).checkReadAccess(dossier);
        verify(dossierRepository).save(dossier);
    }
```

Note : `update()` retourne `enrichAndMaskDetail(dossierRepository.save(dossier))`, qui appelle
en interne `enrichAndMask(dossier)` — donc `dossierMapper.toResponse(dossier)` (confirmé en
lisant `enrichAndMask`, PAS `mapToResponse` qui est la méthode MapStruct interne enveloppée
par `toResponse`) — puis `dossier.getWitnesses()`/`getTargetedParties()`/`getObservations()`/
`getAttachments()`/`getNotifications()` sur l'entité (collections `@Builder.Default` vides par
défaut sur `Dossier.builder().id(dossierId).build()` — aucun stub supplémentaire nécessaire,
même pattern que les tests `findById_*` déjà présents dans ce fichier).

- [ ] **Step 3: Test — agent non habilité ne peut pas éditer**

```java
    @Test
    void update_propagatesGuardRejectionWithoutSaving() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        DossierUpdateRequest request = DossierUpdateRequest.builder()
                .object("Tentative de modification")
                .build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.update(dossierId, request))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }
```

Ce test suit le même style que `findById_propagatesGuardRejection`, déjà présent dans ce
fichier — s'en inspirer pour la structure exacte des mocks si besoin.

- [ ] **Step 4: Lancer les tests**

```
mvn test -q -Dtest=DossierServiceImplTest
```

Expected: BUILD SUCCESS, y compris les 2 nouveaux tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java
git commit -m "fix: require dossier habilitation to edit content via update()"
```

---

### Task 2: Mise à jour du backlog mémoire (point d'arbitrage, pas de code)

**Files:** mémoire projet `project_asce_backlog_2026_07_30.md` (hors dépôt git, système de
mémoire persistant).

- [ ] **Step 1: Marquer `update()` comme livré dans la section B (Contrôle d'accès en
  écriture)**, et ajouter le point d'arbitrage métier restant (modèle de file partagée par
  rôle vs affectation nominative pour `startOpportunityStudy`/`requestComplement`/
  `complementReceived`/`submitToCtadp`) à la section D, avec le contexte exact découvert lors
  de l'audit du 2026-08-02 (aucune habilitation n'est actuellement accordée à ces rôles avant
  leur intervention).
