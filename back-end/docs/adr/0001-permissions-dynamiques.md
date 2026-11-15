# ADR 0001 — Permissions pilotées par la base de données

- Statut : accepté (2026-11-14)
- Périmètre : API (Spring Boot) ; l'interface lit les permissions via `GET /api/v1/auth/permissions`

## Contexte

Les accès étaient écrits en dur : 192 `@PreAuthorize("hasAnyRole(...)")` sur les contrôleurs et une douzaine de
tests `hasRole` cachés dans les services. Changer ce qu'un rôle peut faire exigeait de modifier le code et de
redéployer, et l'écran « Rôles & Permissions » n'était qu'indicatif : il ne commandait aucun accès.

## Décision

1. **Un catalogue fermé de permissions** : l'enum `AppPermission` (une constante par action métier, avec catégorie
   et libellé). Le code ne cite plus jamais un nom de rôle pour décider d'un accès.
2. **Les rôles restent attribués dans Keycloak** (identité) ; **ce que chaque rôle peut faire est en base**
   (tables `role_definition`, `permission`, `role_permission`).
3. **À chaque requête**, `KeycloakAuthoritiesConverter` transforme les rôles du jeton en autorités `PERM_xxx` via
   `PermissionResolver`. Les contrôleurs se protègent avec `@HasPermission(AppPermission.X)` ; les contrôles dans
   les services passent par `SecurityUtils.hasPermission`.
4. **Refus par défaut** : rôle inconnu ou inactif, permission inactive ou absente du catalogue, base injoignable →
   aucun droit. Une erreur de la base n'est jamais mise en cache.
5. **Cache court** : les droits d'un rôle sont gardés 5 minutes (`app.security.permissions-cache-ttl`) et le cache
   est vidé après le commit de toute modification de rôle. Un seul conteneur backend tourne en production ; avec
   plusieurs instances, il faudrait diffuser l'invalidation (Redis pub/sub).
6. **Socle de réparation** : `ADMIN_DDIC` garde en permanence les permissions *protégées* (`AppPermission#protegee`),
   même si la base les lui retire ou ne répond pas. `RoleManagementService` refuse de les lui retirer.
7. **Politique** : ADMIN_DDIC a tout (technique et métier) ; CGE et CGEA font le métier seulement, sans
   administration, paramétrage, audit ni file de notifications ; la DCP ne voit que des statistiques globales.
   L'identité d'un lanceur d'alerte protégé n'est visible que du CGE et du CGEA (permission
   `LANCEUR_ALERTE_PROTEGE_TRAITER`), pas de l'administrateur.

## Preuves d'équivalence

- `access-golden-avant.json` (figé, ne jamais régénérer) : instantané des 225 points d'entrée avant la bascule.
- `AccessEquivalenceIntegrationTest` : chaque rôle garde exactement ses accès, seule la politique du point 7 est
  tolérée.
- `PermissionEndToEndIntegrationTest` : appels HTTP réels avec un jeton, accès accordé et refusé.
- Un test interdit qu'un point d'entrée porte deux contrôles ou un contrôle par nom de rôle.

## Conséquences

- Modifier les droits d'un rôle ne demande plus de déploiement : écran « Rôles & Permissions » (`ROLE_MODIFIER`),
  effet immédiat après le commit, entrée d'audit `MODIFIER_PERMISSIONS_ROLE` avec l'ensemble avant/après.
- Créer un rôle le crée aussi dans Keycloak ; le supprimer est refusé tant que des agents le portent.
- Ajouter une permission est un changement de code, volontairement : le catalogue est fermé.
- L'interface ne fait que masquer ce qui serait refusé ; chaque requête est contrôlée côté API.

## Ajouter une permission

1. Ajouter la constante dans `AppPermission` (catégorie, libellé, `protegee` si elle conditionne la réparation).
2. Annoter le contrôleur : `@HasPermission(AppPermission.NOUVELLE)`.
3. Renseigner `src/test/resources/security/endpoint-permission.csv` (point d'entrée → permission).
4. **Écrire une nouvelle migration Liquibase** qui insère la permission et l'attribue aux rôles voulus.
   La migration `028-permissions-fines.sql` est générée et ne se modifie plus depuis son déploiement.
5. Lancer toute la suite : `PermissionMigrationConsistencyTest` vérifie que catalogue, table et migrations
   concordent.

## Procédure d'urgence

- Un rôle a perdu un accès par erreur : le rétablir dans l'écran « Rôles & Permissions » (ADMIN_DDIC), sans
  redéploiement.
- L'écran lui-même est inutilisable : ADMIN_DDIC garde les permissions protégées (`ROLE_VOIR`, `ROLE_MODIFIER`,
  `AGENT_ROLES_ATTRIBUER`, `AUDIT_JOURNAL_VOIR`, `ACCES_MATRICE_VOIR`) même si la base les lui a retirées.
- Base injoignable : plus aucun droit, sauf le socle de l'administrateur ; rien n'est ouvert par défaut.
- Droits à rétablir directement en base : réinsérer les lignes `role_permission` concernées, puis attendre 5 minutes
  ou redémarrer le backend (le cache est en mémoire).
