# Déploiement ASCE-LC sur serveur Ubuntu (rpd) — conception

Date : 2026-09-23 (révisé après clarification : code **distant GitHub**, pas la copie locale)

## 1. Objectif et contexte

Premier déploiement (Spring Boot + Angular + Keycloak) sur le serveur Ubuntu `rpd` (Docker installé et testé,
`ufw` ouvert sur 22/80/443 uniquement).

- **Source du code = GitHub, tel quel**, cloné sur `rpd` :
  - back : `https://github.com/souli0452/univers-audits-back-end`, branche `feature/workflow-denociation-asce-fix`
    (dernier commit distant : `0f5c633`, 2026-07-30). La copie locale de l'utilisateur (293 commits d'avance) n'est **pas** utilisée.
  - front : `https://github.com/souli0452/univers-audits-front-end`, branche `dev-front` (2026-07-30) — la branche
    `feature/workflow-denociation-asce-fix` n'existe pas dans le dépôt front ; `dev-front` est l'équivalent de même date (à confirmer par l'utilisateur).
- Accès **réseau local uniquement**, DNS interne : `denoncer.asce-lc.bf` → IP de la VM. Un certificat SSL existe (hypothèse : PEM).
- `rpd` a accès à Internet (GitHub, Maven Central, npm, Docker Hub, quay.io vérifiés le 2026-09-23) : build **sur le serveur**.
- L'assistant ne se connecte pas à `rpd` : l'utilisateur exécute les commandes fournies et remonte les sorties.

**Critères de réussite** : page d'accueil en HTTPS ; connexion via Keycloak ; un appel API authentifié retourne 200 ;
`GET /actuator/health` = `UP` ; 8080/8081/5432/6379 injoignables depuis le réseau ; base initialisée sans erreur Liquibase.

## 2. Constats qui contraignent la conception

1. **Aucun `Dockerfile`** (back ni front) dans les dépôts distants.
2. **Base vide impossible avec le code distant** : aucune migration ne crée `agent`, `dossier`, `departement`
   (`ddl-auto=validate` → échec au démarrage). Le correctif de septembre n'est pas dans le code distant.
3. Le compose prod distant publie `8080`/`8081` ; **Docker contourne `ufw`** pour les ports publiés.
4. Keycloak en `start-dev` dans le compose « prod » ; realm limité à `localhost` ; front prod pointe vers des placeholders `*.asce-lc.bf`.
5. `app.frontend.url` absent (défaut `localhost:4200`), `app.api-base-url` = placeholder.
6. Cette version (juillet) **n'a pas** : rate-limiting des endpoints publics, sauvegarde Postgres automatique, cloisonnement réseau
   Docker, 2FA/durcissement Keycloak. Conséquence assumée par le choix de l'utilisateur.
7. `realm-export.json` versionné contient un mot de passe SMTP en clair (à vérifier sur la version distante).
8. Disque non chiffré ; pas de sauvegarde hors-site.

## 3. Principe : sources intactes + dossier de déploiement (« overlay »)

Les deux dépôts sont clonés **sans aucune modification**. Tout le reste vit dans un dossier séparé `deploy-rpd/`
(hors des deux dépôts, copié sur `rpd` par `scp` ou git), qui ajoute au moment du build :

| Besoin | Mécanisme (sans toucher aux sources) |
|---|---|
| Dockerfile back/front, nginx, compose prod | Fichiers de l'overlay, contexte de build = clone |
| Propriétés prod (issuer, jwk, urls) | **Variables d'environnement** Spring (relaxed binding) dans le compose |
| `environment.prod.ts` du front | Fichier de l'overlay copié **par-dessus** dans le Dockerfile front (étape de build) |
| Schéma de base manquant | Migration de départ générée et validée, injectée dans le Dockerfile back |
| Realm Keycloak pour le bon hôte | Copie générée par le script de déploiement |

Approche retenue : nginx dans Docker, un seul nom, chemins. Alternatives écartées : nginx sur l'hôte (peu reproductible) ;
sous-domaines (un seul nom DNS).

```
Navigateur ──HTTPS 443──▶ proxy (nginx + fichiers SPA)
                           ├─ /        → SPA Angular (fallback index.html)
                           ├─ /api/    → backend:8081
                           └─ /auth/   → keycloak:8080
                       (80 → 301 vers 443)
backend ──▶ postgres, redis, keycloak (réseau interne, non publié)
```

## 4. Composants

### 4.1 Back — `Dockerfile` (overlay)
Multi-stage : `maven:3.9-eclipse-temurin-17` (build `-DskipTests`) → `eclipse-temurin:17-jre`, non-root,
volumes `/app/uploads` et `/app/logs`. Tag `asce-lc-backend:latest`.

### 4.2 Schéma de départ (overlay)
Généré **avant livraison** dans un clone temporaire du code distant : démarrage sur une base vide avec Hibernate (`create`),
extraction `pg_dump --schema-only`, plus les données de référence nécessaires. Injecté dans l'image à la place/avant l'historique de
migrations existant (méthode déjà employée le 2026-09-17). **Validation** : le backend distant démarre sur une base neuve avec
`ddl-auto=validate` et Liquibase, puis un parcours minimal fonctionne. C'est le chantier à risque du plan.

### 4.3 Front — `Dockerfile` (overlay)
`node` (`npm ci`, `ng build`) → `nginx:alpine`. L'overlay remplace `src/environments/environment.prod.ts` :
`apiUrl: https://denoncer.asce-lc.bf/api/v1`, `keycloak.url: https://denoncer.asce-lc.bf/auth`, realm `asce-lc`, client `asce-lc-frontend`.

### 4.4 Proxy nginx (overlay)
80→443 ; TLS avec certificats montés en lecture seule depuis `/opt/asce/certs/` ; en-têtes `X-Forwarded-*` ;
`client_max_body_size 55m` (multipart 25/50 Mo) ; `/auth/` → Keycloak, `/api/` → backend, `/` → SPA (`try_files … /index.html`) ;
HSTS et en-têtes de sécurité. Proxy et SPA dans la même image nginx.

### 4.5 Keycloak (overlay)
`start` : `KC_HOSTNAME=https://denoncer.asce-lc.bf/auth`, `KC_HTTP_RELATIVE_PATH=/auth`, `KC_PROXY_HEADERS=xforwarded`,
`KC_HTTP_ENABLED=true`. Aucun port publié. Le script de déploiement génère une copie du realm (redirectUris, webOrigins, rootUrl,
post-logout de `asce-lc-frontend`/`asce-lc-backend` → `https://denoncer.asce-lc.bf/*`) ; le fichier des sources n'est pas modifié.

### 4.6 Backend — configuration par variables d'environnement (compose)
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://denoncer.asce-lc.bf/auth/realms/asce-lc` (émetteur porté par les jetons)
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://keycloak:8080/auth/realms/asce-lc/protocol/openid-connect/certs`
  (clés récupérées en interne ; sans cela, désaccord émetteur public/interne → 401 généralisés)
- `KEYCLOAK_ADMIN_SERVER_URL=http://keycloak:8080/auth`, `APP_API_BASE_URL` et `APP_FRONTEND_URL=https://denoncer.asce-lc.bf`
- CORS en dur dans le code : non modifié (même origine, non sollicité).

### 4.7 Compose de production (overlay)
Reprend les services du compose distant (postgres, redis, keycloak, backend) + `proxy` ; seul `proxy` publie 80/443 ;
réseaux internes ; secrets via `.env`. La sauvegarde Postgres n'existe pas dans cette version : ajout d'un service de sauvegarde
simple est **proposé** dans le plan (décision de l'utilisateur), pas imposé.

### 4.8 Livraison
`deploy.sh` (clone/pull des deux dépôts aux branches ci-dessus, génération du realm, build, `up -d`, attente `healthy`, contrôles de fumée)
et guide `deploiement-ubuntu.md` (préparation, jeton GitHub en lecture si dépôts privés, certificats, `.env` neuf, vérifications, mise à jour, retour arrière).
Le `.env` de production est créé **sur le serveur** avec de **nouveaux secrets**.

## 5. Flux et diagnostic
Auth : SPA → `/auth/` → jeton JWT (émetteur public) → `/api/` avec `Authorization` → backend valide signature (jwk interne) et émetteur (public).
401 généralisé = émetteur ; boucle de redirection Keycloak = `redirectUris`/hostname ; 413 = taille de corps nginx ; échec `validate` = schéma de départ.

## 6. Vérification
1. Avant livraison, sur le PC (Docker) : `docker compose config`, `nginx -t`, build des deux images depuis un **clone frais** des branches distantes,
   démarrage du back sur base vide (schéma de départ), sans toucher au dépôt de travail de l'utilisateur.
2. Sur `rpd` : critères du §1, plus tentative de connexion aux ports internes depuis un autre poste (doit échouer) et dépôt d'une pièce jointe ~20 Mo.
Les résultats sont rapportés tels quels, y compris les échecs.

## 7. Hors périmètre / risques déclarés
- Fonctions absentes de cette version (§2.6) : notamment pas de limitation de débit sur les endpoints publics anonymes.
- Chiffrement du disque, sauvegarde hors-site, test d'intrusion, durée d'archivage : non traités.
- Mot de passe SMTP éventuellement en clair dans le realm : à faire changer côté serveur de messagerie (décision de l'utilisateur).
- Certificat : format et chaîne complète à confirmer.
- Le nom de branche front (`dev-front`) est une hypothèse à confirmer.
