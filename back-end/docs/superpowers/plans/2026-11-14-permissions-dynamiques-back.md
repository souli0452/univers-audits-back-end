# Permissions dynamiques — plan 1/3 : le serveur

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Le serveur décide des accès à partir de permissions lues en base (rôle → permissions), et non plus de noms de rôles écrits dans le code, sans qu'aucun rôle actuel ne gagne ou ne perde un accès.

**Architecture:** Les permissions sont un catalogue fermé (enum `AppPermission`, une constante par action métier). Les rôles restent attribués dans Keycloak ; à chaque requête, un `PermissionResolver` (cache court, invalidé à chaque modification) transforme les rôles du jeton en autorités `PERM_xxx`. Les contrôleurs se protègent avec `@HasPermission(AppPermission.X)`. Un instantané « avant » (golden) de tous les accès actuels, commité, prouve l'équivalence.

**Tech Stack:** Spring Boot 3.3.5 (Spring Security 6.3 : modèles d'annotation), JPA, Liquibase, JUnit 5 + AssertJ + Mockito, PostgreSQL local (Docker `asce_postgres`).

**Spec:** conversation du 2026-11-13/14 (décisions : rôles dans Keycloak conservés, ADMIN_DDIC garde un socle non retirable, règles d'habilitation par dossier hors périmètre). Plans suivants : 2/3 front (menus, routes, `permissions` exposées au client), 3/3 écran Rôles & Permissions actif.

## Global Constraints

- Branche de travail : `feature/permissions-dynamiques` créée depuis `feature/workflow-denociation-asce-fix` (jamais de commit direct sur la branche de déploiement avant la fin du plan).
- Aucun comportement visible ne change : pour chaque point d'entrée, l'ensemble des rôles autorisés après migration est identique à l'instantané `access-golden.json`.
- Nommage des permissions : `MODULE_ACTION` en majuscules (`^[A-Z]+(_[A-Z]+)+$`, 80 caractères max), autorité Spring = `PERM_` + nom.
- Refus par défaut : rôle inconnu, permission inactive ou base injoignable → aucune permission accordée (sauf le socle ADMIN_DDIC, Task 4).
- Messages utilisateur et libellés en français ; code et tests au style existant du dépôt.
- Tests locaux : `cd back-end; set -a; . ./.env; set +a; SPRING_PROFILES_ACTIVE=dev ./mvnw -q -o test`.
- Rien n'est déployé sans validation explicite de l'utilisateur après la Task 6 (équivalence verte).

## Review Focus

- Jeton avec des rôles techniques (`default-roles-asce-lc`, `offline_access`) ou un rôle absent de `role_definition` (ex. `DCP` sans permissions) : aucune erreur, aucune permission.
- Utilisateur à plusieurs rôles : union des permissions, sans doublon.
- Permission modifiée par un administrateur : effet immédiat (cache invalidé), pas après expiration du TTL.
- Base injoignable pendant la résolution : refus (403), jamais d'accès accordé par défaut ; ADMIN_DDIC garde son socle pour pouvoir réparer.
- Administrateur qui retire une permission protégée à ADMIN_DDIC (y compris à lui-même) : refus explicite, aucune écriture.
- Nouvel endpoint sans `@PreAuthorize` ajouté plus tard : l'instantané échoue et force une décision.

## File Structure

| Fichier (sous `back-end/src/main/java/gov/bf/ascelc/univers_audits/`) | Responsabilité |
|---|---|
| `security/access/EndpointAccess.java`, `EndpointAccessCollector.java` | Lire, pour chaque point d'entrée, son contrôle d'accès (utilisé par la matrice et l'instantané) |
| `security/permission/AppPermission.java` | Catalogue fermé des permissions |
| `security/permission/HasPermission.java` | Annotation à placer sur les contrôleurs |
| `security/permission/PermissionResolver.java` | Rôles → permissions, cache, socle admin |
| `shared/config/SecurityConfig.java` (modifié) | Autorités `ROLE_x` + `PERM_y`, `AnnotationTemplateExpressionDefaults` |
| `service/RoleManagementService.java` (modifié) | Garde-fous, invalidation du cache, synchronisation Keycloak |
| `resources/db/changelog/migrations/028-permissions-fines.sql` | Catalogue et attributions de départ (générés) |
| `src/test/resources/security/access-golden.json`, `endpoint-permission.csv` | Instantané « avant » et table point d'entrée → permission |

---

### Task 1: Instantané des accès actuels

**Files:**
- Create: `security/access/EndpointAccess.java`, `security/access/EndpointAccessCollector.java`
- Modify: `service/AccessMatrixService.java` (`collecterEndpoints` délègue au collecteur)
- Test: `src/test/java/.../security/access/AccessGoldenIntegrationTest.java`, `src/test/resources/security/access-golden.json`

**Interfaces:**
- Produces: `record EndpointAccess(String handler, String httpMethods, Kind kind, SortedSet<String> roles, String expression)` ; `enum Kind { NONE, AUTHENTICATED, ROLES, PERMISSION, SPECIFIC }` ; `List<EndpointAccess> EndpointAccessCollector.collect()` triée par `handler` puis `httpMethods`. `handler` = `Controller#methode(TypesParametres)`.

- [ ] **Step 1: Écrire le test** `AccessGoldenIntegrationTest.instantane_correspond_au_code()` : charge `access-golden.json`, compare à `collect()` ; si `-Dgolden.update=true`, réécrit le fichier au lieu de comparer. Un second test `aucun_point_dentree_sans_controle_hors_liste_publique()` exige que tout `Kind.NONE` soit dans une liste blanche explicite (`/api/v1/public/**`, actuator, config publique).
- [ ] **Step 2: Lancer, constater l'échec** (collecteur absent) : `./mvnw -q -o test -Dtest=AccessGoldenIntegrationTest`.
- [ ] **Step 3: Implémenter** `EndpointAccessCollector` en extrayant la logique de `AccessMatrixService.collecterEndpoints` et `roles(String)` ; `SPECIFIC` = expression sans rôle ni `isAuthenticated()` (conservée dans `expression`).
- [ ] **Step 4: Refactorer `AccessMatrixService`** pour s'appuyer sur le collecteur ; `AccessMatrixServiceTest` et `AccessMatrixIntegrationTest` restent verts sans modification de leurs assertions.
- [ ] **Step 5: Générer l'instantané** avec `-Dgolden.update=true`, relire le fichier (208 entrées protégées attendues, plus les publiques), puis relancer sans le drapeau : vert.
- [ ] **Step 6: Commit** `test(security): instantané des accès par point d'entrée`.

### Task 2: Catalogue de permissions et annotation

**Files:**
- Create: `security/permission/AppPermission.java`, `security/permission/HasPermission.java`
- Modify: `shared/config/SecurityConfig.java` (bean `AnnotationTemplateExpressionDefaults`)
- Test: `.../security/permission/AppPermissionTest.java`, `HasPermissionTest.java`

**Interfaces:**
- Produces: `enum AppPermission { DOSSIER_VOIR, ... }` avec `String key()` (= `name()`), `String authority()` (= `"PERM_" + name()`), `String category()`, `String label()`, `boolean protegee()` ; `@interface HasPermission { AppPermission value(); }` méta-annoté `@PreAuthorize("hasAuthority('PERM_{value}')")`.
- Catalogue : 60 à 80 constantes, regroupées par catégorie (Dossiers, Fiches d'affectation, Étude d'opportunité, Séances CTADP, Investigations, Auditions, Rapports, Statistiques, Leçons, Administration, Audit, Portail, Notifications). Les constantes protégées : `ROLE_GERER`, `AGENT_GERER`, `AUDIT_JOURNAL_VOIR`, `ACCES_MATRICE_VOIR`.

- [ ] **Step 1: Tests de forme** `AppPermissionTest` : clés conformes à `^[A-Z]+(_[A-Z]+)+$`, ≤ 80 caractères, uniques ; libellé et catégorie non vides ; `authority()` préfixé `PERM_` ; les quatre permissions protégées existent.
- [ ] **Step 2: Test d'effet** `HasPermissionTest` (contexte web minimal avec un contrôleur de test) : `@WithMockUser(authorities = "PERM_DOSSIER_VOIR")` → 200 ; `@WithMockUser(roles = "ADMIN_DDIC")` seul → 403 ; sans authentification → 401.
- [ ] **Step 3: Lancer** : échec attendu (types absents).
- [ ] **Step 4: Implémenter** l'enum (catalogue à partir de l'inventaire des modules et de `AccessMatrixService.LIBELLES`), l'annotation et le bean de modèle d'annotation.
- [ ] **Step 5: Lancer** : vert. **Commit** `feat(security): catalogue de permissions et annotation @HasPermission`.

### Task 3: Table point d'entrée → permission, et migration générée

**Files:**
- Create: `src/test/resources/security/endpoint-permission.csv` (colonnes `handler;permission`), `src/test/java/.../security/access/AccessMigrationGenerator.java` (outil de test, lancé à la demande), `src/main/resources/db/changelog/migrations/028-permissions-fines.sql`
- Test: `.../security/access/PermissionMigrationConsistencyTest.java`

**Interfaces:**
- Consumes: `access-golden.json` (Task 1), `AppPermission` (Task 2).
- Produces: pour chaque permission, l'ensemble de rôles qui la reçoivent = rôles du golden de ses points d'entrée ; migration 028 : désactive (`active = false`, sans supprimer) les 20 permissions indicatives historiques, insère les nouvelles, remplit `role_permission` pour `ADMIN_DDIC, CGE, CGEA, CONSEILLER_JURIDIQUE, CONTROLEUR_ETAT, MEMBRE_CTADP, AGENT_BRPD, DCP`.

- [ ] **Step 1: Écrire `PermissionMigrationConsistencyTest`** : (a) toute ligne du CSV référence un `handler` du golden et une constante existante ; (b) tout `handler` protégé par rôles du golden figure au CSV ; (c) deux points d'entrée qui partagent une permission ont exactement le même ensemble de rôles (sinon message : « catalogue trop grossier : scinder X ») ; (d) les clés insérées par `028` sont exactement celles de `AppPermission`.
- [ ] **Step 2: Produire le CSV** (une permission par ligne de décision métier, relue par l'humain) ; lancer le test : (c) et (d) échouent tant que le catalogue et la migration ne sont pas ajustés.
- [ ] **Step 3: Ajuster le catalogue** jusqu'à ce que (c) soit vert (scinder les permissions trop larges), puis lancer le générateur pour écrire `028-permissions-fines.sql` (idempotent : `ON CONFLICT DO NOTHING`).
- [ ] **Step 4: Point de contrôle humain** : présenter le CSV regroupé par permission avec les rôles qui la reçoivent ; attendre la validation de l'utilisateur avant la Task 4.
- [ ] **Step 5: Lancer** le test + le démarrage de l'application sur la base locale (Liquibase applique 028) : vert. **Commit** `feat(security): table endpoint-permission et migration 028`.

### Task 4: Résolution des permissions et socle administrateur

**Files:**
- Create: `security/permission/PermissionResolver.java`
- Modify: `repository/RoleDefinitionRepository.java`, `shared/config/SecurityConfig.java` (convertisseur du jeton), `shared/utils/SecurityUtils.java`
- Test: `.../security/permission/PermissionResolverTest.java`, `SecurityConfigAuthoritiesTest.java`

**Interfaces:**
- Produces: `Set<String> PermissionResolver.resolve(Collection<String> roleKeys)` (autorités `PERM_x`), `void invalidate()` ; `Duration` du cache injectée (5 min par défaut, propriété `app.security.permissions-cache-ttl`), `Clock` injectée ; `boolean SecurityUtils.hasPermission(AppPermission)`.
- Consumes: `List<RoleDefinition> RoleDefinitionRepository.findActiveByRoleKeyInWithPermissions(Collection<String> roleKeys)` (nouvelle requête, `active = true`, `JOIN FETCH permissions`).

- [ ] **Step 1: Tests du résolveur** (dépôt simulé, horloge fixe) : union sur plusieurs rôles ; rôle inconnu et rôles techniques ignorés ; permission inactive ignorée ; rôle inactif ignoré ; deuxième appel servi par le cache ; après TTL, relecture ; `invalidate()` force la relecture ; dépôt en erreur → ensemble vide, sauf `ADMIN_DDIC` qui reçoit les permissions `protegee()` ; l'erreur est journalisée une fois.
- [ ] **Step 2: Test du convertisseur** : un jeton `realm_access.roles = [CGE, offline_access]` produit `ROLE_CGE`, `ROLE_offline_access` et les `PERM_` de CGE ; sans `realm_access` : liste vide.
- [ ] **Step 3: Lancer** : échec. **Step 4: Implémenter** (cache en mémoire thread-safe : un seul conteneur backend en production ; `ConcurrentHashMap` + horodatage).
- [ ] **Step 5: Lancer** : vert. **Commit** `feat(security): résolution des permissions depuis la base`.

### Task 5: Conversion des contrôleurs

**Files:** les 40 fichiers contrôleurs/services listés par `grep -rl "@PreAuthorize" java` ; `.../AccessGoldenIntegrationTest` (le golden est étendu avec la permission portée par chaque point d'entrée).

**Interfaces:**
- Consumes: `endpoint-permission.csv`. Produces : plus aucune expression `hasRole/hasAnyRole` dans les contrôleurs, hors cas `SPECIFIC` traités Task 6.

- [ ] **Step 1: Test d'équivalence** `AccessEquivalenceIntegrationTest.chaque_role_garde_exactement_ses_acces()` : pour chaque point d'entrée du golden et chaque rôle connu, « autorisé » via (`@HasPermission` → `PermissionResolver.resolve(role)` sur la base migrée) doit égaler « autorisé » dans l'instantané « avant ». Échec attendu tant que les contrôleurs ne sont pas convertis.
- [ ] **Step 2: Convertir en 4 lots**, chaque lot validé par le test d'équivalence restreint à ses contrôleurs puis commité séparément : (1) Dossiers, fiches, étude d'opportunité, pièces, PDF ; (2) investigations, auditions, témoins, rapports, indices, visites ; (3) séances CTADP, informations préoccupantes, transmissions, procédures, plans d'actions, leçons, checklists ; (4) administration, statistiques, notifications, profil, paramètres. Un script jetable (hors dépôt) réécrit les annotations à partir du CSV ; on relit le diff.
- [ ] **Step 3: Lancer toute la suite** : `689` tests précédents + nouveaux, tous verts, équivalence verte sur les 208 points d'entrée.
- [ ] **Step 4: Commits** `refactor(security): contrôleurs sur @HasPermission (lot N/4)`.

### Task 6: Contrôles cachés dans le code

**Files:** `service/impl/DossierServiceImpl.java` (l.121, 689-690, 718-719, 752, 1051-1053), `shared/utils/DossierAccessGuard.java`, `service/FicheAffectationService.java:161`, `shared/utils/AuditionDisplayNameMasker.java`, `service/impl/NotificationServiceImpl.java:584`, `service/SuperieursResolver.java:29`, plus les points d'entrée `SPECIFIC` du golden.

**Interfaces:**
- Consumes: `SecurityUtils.hasPermission(AppPermission)`. Produces : nouvelles constantes d'`AppPermission` pour chaque décision (ex. `DOSSIER_VOIR_TOUS`, `DOSSIER_IMPUTER`, `AUDITION_VOIR_NOMS`), ajoutées au CSV et à la migration 028 (régénérée).
- Rappel : l'habilitation par dossier reste une règle de données, non une permission. `getUserIdsByRole("CGE")` (qui notifier) reste une règle de destinataires : décision documentée, pas convertie.

- [ ] **Step 1: Pour chaque site**, écrire d'abord le test de comportement existant avec le rôle (déjà présent ou ajouté), vérifier qu'il passe, puis le réécrire avec `@WithMockUser(authorities = "PERM_...")` : échec.
- [ ] **Step 2: Remplacer** `securityUtils.hasRole("X")` par `hasPermission(...)` ; régénérer 028 ; relancer l'équivalence (golden étendu aux méthodes de service concernées via des tests ciblés).
- [ ] **Step 3: Suite complète verte. Commit** `refactor(security): contrôles de rôle internes sur les permissions`.
- [ ] **Step 4: Point de contrôle** : résumé à l'utilisateur (diff des accès : « aucun écart »), accord avant tout déploiement.

### Task 7: Garde-fous d'administration

**Files:**
- Modify: `service/RoleManagementService.java`, `controller/RoleManagementController.java`
- Test: `.../service/RoleManagementServiceTest.java` (cas ajoutés)

**Interfaces:**
- Produces: `RoleManagementService.update(...)`/`create(...)`/`delete(...)` : refusent (`BusinessException`, message en français) de retirer une permission `protegee()` à `ADMIN_DDIC`, de supprimer un rôle `isProtected`, ou d'attribuer une permission inconnue/inactive ; appellent `PermissionResolver.invalidate()` après écriture ; journalisent dans `AuditService` l'ancien et le nouvel ensemble de permissions (`MODIFIER_PERMISSIONS_ROLE`).
- Création/suppression d'un rôle : crée/supprime aussi le rôle realm dans Keycloak (`KeycloakAdminService`), avec compensation si la seconde écriture échoue (aucune ligne orpheline en base).

- [ ] **Step 1: Tests** : retrait d'une permission protégée à ADMIN_DDIC → refus et aucune écriture ; modification valide → `invalidate()` appelé et une entrée d'audit contient avant/après ; création → rôle Keycloak créé ; échec Keycloak → rollback base ; suppression d'un rôle encore porté par des agents → refus.
- [ ] **Step 2: Lancer** : échec. **Step 3: Implémenter. Step 4: Lancer** : vert.
- [ ] **Step 5: Commit** `feat(roles): garde-fous, audit et synchronisation Keycloak`.

### Task 8: Matrice depuis la base et documentation

**Files:**
- Modify: `service/AccessMatrixService.java` (rôles et agents lus avec les permissions réelles), `controller/` expose `GET /api/v1/auth/permissions` (permissions de l'utilisateur courant, pour le plan 2)
- Create: `docs/adr/0003-permissions-dynamiques.md`
- Test: `AccessMatrixServiceTest` (cas ajoutés), `AuthPermissionsControllerTest`

**Interfaces:**
- Produces: `GET /api/v1/auth/permissions` → `{"permissions": ["DOSSIER_VOIR", ...]}` (clés sans préfixe), accessible à tout utilisateur connecté, ne renvoie que les siennes.

- [ ] **Step 1: Tests** : l'endpoint renvoie l'union des permissions des rôles du jeton, vide pour un jeton sans rôle métier ; la matrice reflète une permission retirée en base.
- [ ] **Step 2: Lancer** : échec. **Step 3: Implémenter. Step 4: Lancer** : vert.
- [ ] **Step 5: ADR** : contexte, décision, conséquences, procédure d'ajout d'une permission (enum + CSV + migration + test), procédure d'urgence (socle ADMIN_DDIC). **Commit** `docs(security): ADR permissions dynamiques`.

---

## Auto-relecture

- Couverture : socle de départ (T3), résolution (T4), conversion (T5), contrôles cachés (T6), garde-fous/audit/Keycloak (T7), exposition au front (T8). Les règles de cumul de rôles modifiables en base sont **hors de ce plan** (plan 3) : elles ne sont pas nécessaires à l'équivalence.
- Types : `AppPermission.authority()`, `PermissionResolver.resolve/invalidate`, `SecurityUtils.hasPermission` utilisés avec les mêmes noms dans les Tasks 2, 4, 5, 6, 7, 8.
- Risque principal : Task 3 (le découpage du catalogue). Le test (c) l'impose mécaniquement ; le point de contrôle humain de la Task 3 évite de découvrir un mauvais découpage après la conversion.
