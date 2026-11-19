# Déploiement ASCE-LC sur Ubuntu (rpd) — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produire un dossier de déploiement `deploy-rpd/` qui, sur le serveur `rpd`, clone GitHub tel quel (back `feature/workflow-denociation-asce-fix`, front `dev-front`), construit les images et sert l'application en HTTPS sur `https://denoncer.asce-lc.bf`.

**Architecture:** Les dépôts sont clonés sans modification dans `deploy-rpd/sources/`. Un « overlay » (Dockerfiles, nginx, compose, schéma de départ, scripts) est ajouté au build. nginx (dans Docker) est seul exposé (80/443) : `/` → Angular, `/api/` → backend, `/auth/` → Keycloak. La config prod du backend passe par des variables d'environnement.

**Tech Stack:** Docker Compose, nginx 1.27, Keycloak 25.0.2, PostgreSQL 16, Redis 7, Spring Boot 3.3.5 / Java 17 / Maven, Angular 20 / Node 22, bash.

**Spec:** `docs/superpowers/specs/2026-09-23-deploiement-ubuntu-design.md`

## Global Constraints

- Nom d'accès unique : `denoncer.asce-lc.bf`, réseau local, HTTPS.
- Certificat (vérifié le 2026-09-23 sur le PC de développement et sur `rpd`) : `asce-lc.bf.crt` + `asce-lc.bf.key` + `gd_bundle-g2-g1.crt` (émetteur DigiCert Global G2 TLS RSA SHA256 2020 CA1), SAN `*.asce-lc.bf`, valide jusqu'au **2027-03-08**, clé RSA non chiffrée, appariement clé/certificat confirmé, chaîne `OK`. Déjà assemblé sur `rpd` dans `/opt/asce/certs/fullchain.pem` (certificat + bundle, 2 certificats) et `/opt/asce/certs/privkey.pem` (mode 600). nginx lit `fullchain.pem` et `privkey.pem`.
- Sources : back `https://github.com/souli0452/univers-audits-back-end.git` branche `feature/workflow-denociation-asce-fix` (dernier commit distant `0f5c633`, le dépôt contient le dossier `back-end/`) ; front `https://github.com/souli0452/univers-audits-front-end.git` branche `dev-front` (dernier commit `779ec2f`). **Aucune modification des sources clonées ni des dépôts de travail locaux de l'utilisateur.**
- Seul le service `proxy` publie des ports (80, 443). Postgres, Redis, Keycloak, backend : jamais publiés (Docker contourne `ufw`).
- Keycloak `quay.io/keycloak/keycloak:25.0.2` en mode `start` (pas `start-dev`), `KC_HTTP_RELATIVE_PATH=/auth`.
- Backend : `issuer-uri` public + `jwk-set-uri` interne (sinon 401 généralisés).
- `client_max_body_size 55m` (multipart backend : 25 Mo par fichier, 50 Mo par requête).
- Le `.env` de production est créé sur le serveur avec de nouveaux secrets, jamais versionné, jamais copié depuis le dev.
- Docker (Windows, Git Bash) : préfixer les scripts locaux de `MSYS_NO_PATHCONV=1`. Docker 29.1.2 disponible sur le PC ; `rpd` : Docker installé, accès Internet vérifié (GitHub, Maven Central, npm, quay.io).
- Le PC de développement (10.10.102.6) a `ssh`/`scp` ; le serveur WAMP n'en a pas. L'assistant ne se connecte pas à `rpd` : l'utilisateur exécute les commandes du guide et remonte les sorties. Aucun `git commit` / `git push` sans demande de l'utilisateur.
- Diagnostic : en cas d'échec, lire la chaîne `Caused by` **complète** dans `docker compose logs`, ne jamais conclure sur la première ligne.

## Review Focus

- **Base vide** : le backend distant ne démarre pas sur une base neuve sans le schéma de départ ; attendu : démarrage propre, Liquibase applique 1 changeset, l'agent admin est créé (Task 2).
- **Jetons refusés (401)** : émetteur public ≠ URL interne de Keycloak ; attendu : un jeton obtenu via `https://denoncer.asce-lc.bf/auth` est accepté par `/api/` (Task 5, smoke-test).
- **Corps de requête volumineux** : un envoi de 30 Mo doit passer nginx, un envoi de 60 Mo doit recevoir 413 (Task 5, smoke-test).
- **Ports internes exposés** : 8080/8081/5432/6379 ne doivent pas répondre depuis l'hôte (Task 5, smoke-test).
- **Changement de `DOMAIN` après le premier démarrage** : l'import du realm ne s'exécute que sur Keycloak vierge, un changement d'hôte n'a alors aucun effet sur les `redirectUris` ; attendu : le guide l'indique avec la procédure de remise à zéro (Task 6).

---

## File Structure

Dossier de travail : `C:\Users\siakour.drabo\Documents\LAB\UNIVERSAUDITES\deploy-rpd\` (hors des deux dépôts). Sur le serveur : `/opt/asce/deploy/`.

```
deploy-rpd/
├── .env.example              modèle des variables (sans secret)
├── .gitignore  .dockerignore
├── docker-compose.yml        stack de production (5 services)
├── build/
│   ├── back.Dockerfile       build Maven → JRE 17, applique le schéma de départ
│   ├── proxy.Dockerfile      build Angular → nginx (SPA + reverse proxy)
│   └── overlay/back/db/changelog/
│       ├── db.changelog-master.yaml
│       └── migrations/001-baseline.sql     (généré, Task 2)
├── nginx/asce.conf.template  vhosts 80/443
├── postgres/init/00-schemas.sql            schéma `keycloak`
├── scripts/
│   ├── fetch-sources.sh      clone/maj des 2 dépôts
│   ├── gen-baseline.sh       génère 001-baseline.sql (poste de dev uniquement)
│   ├── gen-realm.sh          adapte le realm Keycloak au domaine
│   ├── deploy.sh             enchaîne tout
│   ├── smoke-test.sh         contrôles de fumée
│   └── backup.sh             sauvegarde pg_dump (cron)
└── docs/deploiement-ubuntu.md
```

---

### Task 1: Squelette du dossier et récupération des sources

**Files:**
- Create: `deploy-rpd/.env.example`, `deploy-rpd/.gitignore`, `deploy-rpd/.dockerignore`, `deploy-rpd/scripts/fetch-sources.sh`

**Interfaces:**
- Produces: `sources/back/back-end/…` (contexte Maven), `sources/front/…` (contexte npm), variables `.env` : `DOMAIN, DB_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_CLIENT_SECRET, REDIS_PASSWORD, MAIL_PASSWORD, AT_API_KEY, ASCE_ADMIN_SUB, GITHUB_TOKEN, BACK_REPO, BACK_BRANCH, FRONT_REPO, FRONT_BRANCH, CERT_DIR`.

- [ ] **Step 1: Créer le dossier et `.env.example`**

```bash
mkdir -p "/c/Users/siakour.drabo/Documents/LAB/UNIVERSAUDITES/deploy-rpd"/{build/overlay/back/db/changelog/migrations,nginx,postgres/init,scripts,docs}
cd "/c/Users/siakour.drabo/Documents/LAB/UNIVERSAUDITES/deploy-rpd"
cat > .env.example <<'EOF'
# Domaine servi (doit correspondre au certificat SSL et au DNS interne)
DOMAIN=denoncer.asce-lc.bf
# Secrets : générer chacun avec `openssl rand -hex 24` (NE PAS réutiliser ceux de dev)
DB_PASSWORD=
KEYCLOAK_ADMIN_PASSWORD=
KEYCLOAK_CLIENT_SECRET=
REDIS_PASSWORD=
# Mot de passe du compte SMTP denoncer.dsida@asce-lc.bf (peut rester vide pour un premier essai : les e-mails échoueront)
MAIL_PASSWORD=
AT_API_KEY=unused
# Identifiant Keycloak (sub) de l'utilisateur admin.asce du realm importé
ASCE_ADMIN_SUB=eed35363-ade5-4204-8b37-83fd21947e0b
# Jeton GitHub en lecture (Contents: read) si les dépôts sont privés, sinon vide
GITHUB_TOKEN=
BACK_REPO=https://github.com/souli0452/univers-audits-back-end.git
BACK_BRANCH=feature/workflow-denociation-asce-fix
FRONT_REPO=https://github.com/souli0452/univers-audits-front-end.git
FRONT_BRANCH=dev-front
# Dossier contenant fullchain.pem et privkey.pem
CERT_DIR=/opt/asce/certs
EOF
printf '.env\nsources/\nbuild/realm/\nbuild/gen/\nbuild/certs-test/\n' > .gitignore
printf 'sources/**/.git\nsources/**/target\nsources/**/node_modules\nsources/**/dist\nsources/**/.angular\n.env\nbuild/certs-test\nbuild/gen\n' > .dockerignore
```

- [ ] **Step 2: Écrire `scripts/fetch-sources.sh`**

```bash
cat > scripts/fetch-sources.sh <<'EOF'
#!/bin/bash
# Clone (ou met à jour) les deux dépôts, sans jamais les modifier.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a

if [ -n "${GITHUB_TOKEN:-}" ]; then
  export GIT_CONFIG_COUNT=1
  export GIT_CONFIG_KEY_0=http.extraheader
  export GIT_CONFIG_VALUE_0="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$GITHUB_TOKEN" | base64 | tr -d '\n')"
fi

sync_repo() {
  local url=$1 branch=$2 dir=$3
  if [ -d "$dir/.git" ]; then
    git -C "$dir" fetch --quiet origin "$branch"
    git -C "$dir" reset --hard --quiet FETCH_HEAD
  else
    mkdir -p "$(dirname "$dir")"
    git -c core.longpaths=true clone --quiet --branch "$branch" --single-branch "$url" "$dir"
  fi
  echo "$dir -> $(git -C "$dir" rev-parse --short HEAD) ($branch)"
}

sync_repo "$BACK_REPO"  "$BACK_BRANCH"  sources/back
sync_repo "$FRONT_REPO" "$FRONT_BRANCH" sources/front
test -f sources/back/back-end/pom.xml   || { echo "ERREUR: sources/back/back-end/pom.xml absent"; exit 1; }
test -f sources/front/package.json      || { echo "ERREUR: sources/front/package.json absent"; exit 1; }
EOF
```

- [ ] **Step 3: Créer un `.env` local de test et exécuter**

Run:
```bash
cd "/c/Users/siakour.drabo/Documents/LAB/UNIVERSAUDITES/deploy-rpd"
cp .env.example .env && bash scripts/fetch-sources.sh
```
Expected: deux lignes `sources/back -> 0f5c633 (feature/workflow-denociation-asce-fix)` et `sources/front -> 779ec2f (dev-front)`. Si GitHub demande un identifiant (dépôt privé), le gestionnaire d'identifiants Git de Windows s'en charge ; sinon renseigner `GITHUB_TOKEN` dans `.env`.

- [ ] **Step 4: Vérifier l'idempotence**

Run: `bash scripts/fetch-sources.sh` une seconde fois.
Expected: mêmes deux lignes, sans erreur.

---

### Task 2: Schéma de départ et image du backend

**Files:**
- Create: `scripts/gen-baseline.sh`, `build/overlay/back/db/changelog/db.changelog-master.yaml`, `build/overlay/back/db/changelog/migrations/001-baseline.sql` (généré), `build/back.Dockerfile`

**Interfaces:**
- Consumes: `sources/back/back-end` (Task 1).
- Produces: image `asce-lc-backend:latest` ; fichier `001-baseline.sql` au format Liquibase-SQL (changeset `deploy:001-baseline`).

Contexte : le code distant n'a aucune migration créant `agent`, `dossier`, `departement` ; `ddl-auto=validate` échoue sur base vide. Méthode : Hibernate crée toutes les tables, on rejoue les migrations existantes (données de référence), on exporte le tout.

- [ ] **Step 1: Confirmer l'échec attendu sur base vide (test rouge)**

Run:
```bash
export MSYS_NO_PATHCONV=1; cd "/c/Users/siakour.drabo/Documents/LAB/UNIVERSAUDITES/deploy-rpd"
docker network create asce-t2 >/dev/null
docker run -d --name asce-t2-pg --network asce-t2 --network-alias postgres -e POSTGRES_DB=bd_univers_audit -e POSTGRES_PASSWORD=x postgres:16 >/dev/null
sleep 8
docker run --rm -v asce-m2-cache:/root/.m2 -v "$(pwd -W)/sources/back/back-end":/src -w /src maven:3.9-eclipse-temurin-17 mvn -q -B -DskipTests package
JAR=$(ls sources/back/back-end/target/*.jar | grep -v '\.original$' | head -1)
docker run --rm --network asce-t2 -v "$(pwd -W)/$JAR":/app.jar:ro -e SPRING_PROFILES_ACTIVE=prod -e DB_PASSWORD=x -e KEYCLOAK_CLIENT_SECRET=x -e MAIL_PASSWORD=x -e REDIS_PASSWORD=x -e ASCE_ADMIN_SUB= -e AT_API_KEY=x -e LOGGING_FILE_NAME=/tmp/a.log -e STORAGE_UPLOAD_DIR=/tmp/up eclipse-temurin:17-jre java -jar /app.jar 2>&1 | grep -E "Caused by|relation|does not exist|Started" | head -5
docker rm -f asce-t2-pg >/dev/null; docker network rm asce-t2 >/dev/null
```
Expected: échec de Liquibase ou de la validation, avec une erreur du type `relation "dossier" does not exist` / `Schema-validation: missing table`, et **pas** de ligne `Started`.

- [ ] **Step 2: Écrire `scripts/gen-baseline.sh`**

```bash
cat > scripts/gen-baseline.sh <<'EOF'
#!/bin/bash
# Génère build/overlay/back/db/changelog/migrations/001-baseline.sql depuis sources/back (poste de dev).
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."
HERE=$(pwd -W 2>/dev/null || pwd)
BACK=sources/back/back-end
MIG=$BACK/src/main/resources/db/changelog
OUT=build/overlay/back/db/changelog/migrations/001-baseline.sql
NET=asce-gen-net; PG=asce-gen-pg; APP=asce-gen-app
mkdir -p build/gen "$(dirname "$OUT")"

cleanup() { docker rm -f $APP $PG >/dev/null 2>&1 || true; docker network rm $NET >/dev/null 2>&1 || true; }
trap cleanup EXIT; cleanup
docker network create $NET >/dev/null
docker run -d --name $PG --network $NET --network-alias postgres \
  -e POSTGRES_DB=bd_univers_audit -e POSTGRES_PASSWORD=gen postgres:16 >/dev/null
until docker exec $PG pg_isready -U postgres -d bd_univers_audit >/dev/null 2>&1; do sleep 1; done

echo "[1/5] Build du jar"
docker run --rm -v asce-m2-cache:/root/.m2 -v "$HERE/$BACK":/src -w /src \
  maven:3.9-eclipse-temurin-17 mvn -q -B -DskipTests package
JAR=$(ls $BACK/target/*.jar | grep -v '\.original$' | head -1)

echo "[2/5] Hibernate crée toutes les tables (Liquibase désactivé)"
docker run -d --name $APP --network $NET -v "$HERE/$JAR":/app.jar:ro \
  -e SPRING_PROFILES_ACTIVE=prod -e DB_PASSWORD=gen -e KEYCLOAK_CLIENT_SECRET=x -e MAIL_PASSWORD=x \
  -e REDIS_PASSWORD=x -e ASCE_ADMIN_SUB= -e AT_API_KEY=x \
  -e SPRING_LIQUIBASE_ENABLED=false -e SPRING_JPA_HIBERNATE_DDL_AUTO=create \
  -e LOGGING_FILE_NAME=/tmp/a.log -e STORAGE_UPLOAD_DIR=/tmp/up \
  eclipse-temurin:17-jre java -jar /app.jar >/dev/null
for i in $(seq 1 90); do
  docker logs $APP 2>&1 | grep -q "Started UniversAuditsApplication" && break
  [ "$(docker inspect -f '{{.State.Running}}' $APP)" = "true" ] || { docker logs --tail 60 $APP; echo "ECHEC: l'application s'est arrêtée"; exit 1; }
  sleep 2
done
docker logs $APP 2>&1 | grep -q "Started UniversAuditsApplication" || { docker logs --tail 60 $APP; echo "ECHEC: démarrage > 180 s"; exit 1; }
docker stop $APP >/dev/null

echo "[3/5] Rejeu des migrations existantes (données de référence), dans l'ordre du changelog-master"
FILES="$(ls $MIG/migrations/*.sql | sort) $MIG/004-create-role-permission-tables.sql $MIG/005-create-audit-tables.sql $MIG/006-create-portal-config.sql $MIG/001-add-priority-to-dossier.sql"
: > build/gen/legacy-migrations.log
for f in $FILES; do
  echo "== $f" >> build/gen/legacy-migrations.log
  sed '1s/^\xEF\xBB\xBF//' "$f" | docker exec -i $PG psql -U postgres -d bd_univers_audit >> build/gen/legacy-migrations.log 2>&1
done
UNEXPECTED=$(grep 'ERROR:' build/gen/legacy-migrations.log | grep -v 'already exists' || true)
if [ -n "$UNEXPECTED" ]; then
  echo "ECHEC: erreurs inattendues lors du rejeu (voir build/gen/legacy-migrations.log) :"; echo "$UNEXPECTED"; exit 1
fi
echo "    $(grep -c 'ERROR:.*already exists' build/gen/legacy-migrations.log || true) erreur(s) 'already exists' ignorée(s) (attendu)"

echo "[4/5] Export"
docker exec $PG pg_dump -U postgres -d bd_univers_audit --schema=public \
  --no-owner --no-privileges --no-comments --inserts --column-inserts > build/gen/dump.sql
{ printf -- '--liquibase formatted sql\n--changeset deploy:001-baseline splitStatements:true endDelimiter:;\n\n'
  grep -vE '^(SET |SELECT pg_catalog.set_config|\\restrict|\\unrestrict)' build/gen/dump.sql
} > "$OUT"

echo "[5/5] OK -> $OUT ($(grep -c '^CREATE TABLE' "$OUT") tables, $(grep -c '^INSERT INTO' "$OUT") insertions)"
EOF
```

- [ ] **Step 3: Exécuter la génération**

Run: `MSYS_NO_PATHCONV=1 bash scripts/gen-baseline.sh`
Expected: 5 étapes, dernière ligne `OK -> build/overlay/back/db/changelog/migrations/001-baseline.sql (N tables, M insertions)` avec N ≥ 20 (l'application compte 26 entités + tables des migrations) et M > 0.
Si l'étape 3 affiche des erreurs inattendues : **s'arrêter**, lire le fichier `build/gen/legacy-migrations.log`, identifier la migration en cause (contrainte ou colonne différentes entre migration et entités) et décider avec l'utilisateur ; ne pas masquer l'erreur.

- [ ] **Step 4: Écrire le changelog-master de l'overlay**

```bash
cat > build/overlay/back/db/changelog/db.changelog-master.yaml <<'EOF'
databaseChangeLog:
  - includeAll:
      path: db/changelog/migrations/
      relativeToChangelogFile: false
EOF
```

- [ ] **Step 5: Écrire `build/back.Dockerfile`**

```bash
cat > build/back.Dockerfile <<'EOF'
# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY sources/back/back-end/pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY sources/back/back-end/src ./src
# Le schéma de départ remplace l'historique de migrations du dépôt (aucune table de base n'y est créée).
RUN rm -rf src/main/resources/db/changelog
COPY build/overlay/back/db/changelog src/main/resources/db/changelog
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package && cp target/*.jar /build/app.jar

FROM eclipse-temurin:17-jre
RUN groupadd -r asce && useradd -r -g asce -d /app asce \
 && mkdir -p /app/uploads /app/logs && chown -R asce:asce /app
WORKDIR /app
COPY --from=build --chown=asce:asce /build/app.jar app.jar
USER asce
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EOF
```

- [ ] **Step 6: Construire l'image**

Run: `cd deploy-rpd && MSYS_NO_PATHCONV=1 docker build -f build/back.Dockerfile -t asce-lc-backend:latest .`
Expected: `naming to docker.io/library/asce-lc-backend:latest`, sans erreur.

- [ ] **Step 7: Test vert — démarrage sur base neuve, config par défaut (Liquibase actif, `validate`)**

Run:
```bash
export MSYS_NO_PATHCONV=1
docker network create asce-t2 >/dev/null
docker run -d --name asce-t2-pg --network asce-t2 --network-alias postgres -e POSTGRES_DB=bd_univers_audit -e POSTGRES_PASSWORD=x postgres:16 >/dev/null; sleep 8
docker run -d --name asce-t2-app --network asce-t2 -e SPRING_PROFILES_ACTIVE=prod -e DB_PASSWORD=x -e KEYCLOAK_CLIENT_SECRET=x -e MAIL_PASSWORD=x -e REDIS_PASSWORD=x -e ASCE_ADMIN_SUB=eed35363-ade5-4204-8b37-83fd21947e0b -e AT_API_KEY=x asce-lc-backend:latest >/dev/null
for i in $(seq 1 60); do docker logs asce-t2-app 2>&1 | grep -q "Started UniversAuditsApplication" && break; sleep 3; done
docker logs asce-t2-app 2>&1 | grep -E "Started UniversAuditsApplication|Agent admin créé|ERROR|Caused by" | head
docker exec asce-t2-pg psql -U postgres -d bd_univers_audit -tAc "select count(*) from databasechangelog; select count(*) from agent; select count(*) from parametre_delai;"
docker rm -f asce-t2-app asce-t2-pg >/dev/null; docker network rm asce-t2 >/dev/null
```
Expected: ligne `Started UniversAuditsApplication`, ligne `Agent admin créé`, aucune ligne `Caused by` ; requêtes : `1`, `1`, et un nombre > 0 pour `parametre_delai` (données de référence présentes).

---

### Task 3: Image proxy (Angular + nginx)

**Files:**
- Create: `nginx/asce.conf.template`, `build/proxy.Dockerfile`

**Interfaces:**
- Consumes: `sources/front` (Task 1) ; variable `DOMAIN`.
- Produces: image `asce-lc-proxy:latest` ; attend des certificats montés dans `/etc/nginx/certs/{fullchain,privkey}.pem` ; upstreams internes `backend:8081` et `keycloak:8080`.

- [ ] **Step 1: Écrire `nginx/asce.conf.template`**

```bash
cat > nginx/asce.conf.template <<'EOF'
server {
    listen 80;
    server_name ${DOMAIN};
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    http2 on;
    server_name ${DOMAIN};

    ssl_certificate     /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_session_cache   shared:SSL:10m;

    client_max_body_size 55m;

    # Résolution DNS Docker à l'exécution : nginx démarre même si un service n'est pas encore prêt.
    resolver 127.0.0.11 valid=10s;

    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-Host  $host;
    proxy_set_header X-Forwarded-Port  443;

    location /auth/ {
        set $kc http://keycloak:8080;
        proxy_pass $kc;
        proxy_buffer_size 128k;
        proxy_buffers 4 256k;
        proxy_busy_buffers_size 256k;
    }

    location /api/ {
        set $be http://backend:8081;
        proxy_pass $be;
        proxy_read_timeout 120s;
    }

    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
        add_header Strict-Transport-Security "max-age=31536000" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-Frame-Options "SAMEORIGIN" always;
    }
}
EOF
```

- [ ] **Step 2: Écrire `build/proxy.Dockerfile`**

```bash
cat > build/proxy.Dockerfile <<'EOF'
# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
ARG DOMAIN
WORKDIR /app
COPY sources/front/package.json sources/front/package-lock.json ./
RUN npm ci --legacy-peer-deps --no-audit --no-fund
COPY sources/front/ ./
# Configuration de production propre au domaine (les sources d'origine ciblent des hôtes fictifs).
RUN printf "export const environment = {\n    production: true,\n    apiUrl: 'https://%s/api/v1',\n    keycloak: {\n        url: 'https://%s/auth',\n        realm: 'asce-lc',\n        clientId: 'asce-lc-frontend'\n    }\n};\n" "$DOMAIN" "$DOMAIN" > src/environments/environment.prod.ts
RUN sed -i "s#http://localhost:4200#https://$DOMAIN#g" src/app/layout/component/app.footer.ts
ENV NODE_OPTIONS=--max-old-space-size=4096
RUN npx ng build --configuration production
RUN test -f dist/sakai-ng/browser/index.html

FROM nginx:1.27-alpine
COPY nginx/asce.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/sakai-ng/browser /usr/share/nginx/html
EOF
```

- [ ] **Step 3: Construire l'image**

Run: `MSYS_NO_PATHCONV=1 docker build -f build/proxy.Dockerfile --build-arg DOMAIN=denoncer.asce-lc.bf -t asce-lc-proxy:latest .`
Expected: fin sans erreur (le build Angular prend plusieurs minutes). Si `test -f dist/sakai-ng/browser/index.html` échoue, relancer avec `--target build` puis `docker run --rm <image> find dist -maxdepth 2` pour trouver le vrai dossier de sortie, et corriger les deux chemins du Dockerfile.

- [ ] **Step 4: Vérifier qu'aucun hôte local ni fictif ne reste dans le bundle**

Run:
```bash
docker run --rm --entrypoint sh asce-lc-proxy:latest -c 'grep -rlE "localhost|api\.asce-lc\.bf|auth\.asce-lc\.bf" /usr/share/nginx/html || echo AUCUN'
docker run --rm --entrypoint sh asce-lc-proxy:latest -c 'grep -rl "denoncer.asce-lc.bf/auth" /usr/share/nginx/html | head -1'
```
Expected: première commande `AUCUN` ; deuxième : un nom de fichier `.js` (l'URL Keycloak est dans le bundle).

- [ ] **Step 5: Vérifier la syntaxe nginx avec un certificat jetable**

Run:
```bash
export MSYS_NO_PATHCONV=1; mkdir -p build/certs-test
openssl req -x509 -newkey rsa:2048 -nodes -days 30 -keyout build/certs-test/privkey.pem -out build/certs-test/fullchain.pem -subj "/CN=denoncer.asce-lc.bf" -addext "subjectAltName=DNS:denoncer.asce-lc.bf" 2>/dev/null
docker run --rm -e DOMAIN=denoncer.asce-lc.bf -v "$(pwd -W)/build/certs-test":/etc/nginx/certs:ro asce-lc-proxy:latest sh -c '/docker-entrypoint.d/20-envsubst-on-templates.sh >/dev/null && nginx -t'
```
Expected: `syntax is ok` et `test is successful`.

---

### Task 4: Stack Compose, realm Keycloak et répétition locale

**Files:**
- Create: `docker-compose.yml`, `postgres/init/00-schemas.sql`, `scripts/gen-realm.sh`

**Interfaces:**
- Consumes: images `asce-lc-backend:latest` (Task 2) et `asce-lc-proxy:latest` (Task 3, construite par Compose avec l'argument `DOMAIN`), `.env` (Task 1).
- Produces: services `postgres, redis, keycloak, backend, proxy` ; volumes nommés `asce_postgres_data, asce_keycloak_data, asce_redis_data, asce_backend_uploads, asce_backend_logs`.

- [ ] **Step 1: Schéma Keycloak dans Postgres**

```bash
echo 'CREATE SCHEMA IF NOT EXISTS keycloak;' > postgres/init/00-schemas.sql
```

- [ ] **Step 2: Écrire `scripts/gen-realm.sh`**

```bash
cat > scripts/gen-realm.sh <<'EOF'
#!/bin/bash
# Copie du realm avec les hôtes de développement remplacés par le domaine de production.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
SRC=sources/back/back-end/keycloak/import/realm-export.json
OUT=build/realm/realm-export.json
mkdir -p build/realm
sed -e "s#http://localhost:8080#https://${DOMAIN}/auth#g" \
    -e "s#http://localhost:8081#https://${DOMAIN}#g" \
    -e "s#http://localhost:4200#https://${DOMAIN}#g" \
    -e 's#" https://#"https://#g' "$SRC" > "$OUT"
if grep -q localhost "$OUT"; then echo "ERREUR: 'localhost' subsiste dans $OUT"; grep -n localhost "$OUT"; exit 1; fi
echo "realm généré -> $OUT ($(grep -c "https://${DOMAIN}" "$OUT") occurrences de https://${DOMAIN})"
EOF
```

- [ ] **Step 3: Écrire `docker-compose.yml`**

```bash
cat > docker-compose.yml <<'EOF'
name: asce

services:

  postgres:
    image: postgres:16
    container_name: asce_postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: bd_univers_audit
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD manquant dans .env}
    volumes:
      - asce_postgres_data:/var/lib/postgresql/data
      - ./postgres/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d bd_univers_audit"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks: [asce-data]

  redis:
    image: redis:7-alpine
    container_name: asce_redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD:?REDIS_PASSWORD manquant dans .env} --appendonly yes
    volumes:
      - asce_redis_data:/data
    networks: [asce-data]

  keycloak:
    image: quay.io/keycloak/keycloak:25.0.2
    container_name: asce_keycloak
    restart: unless-stopped
    command: start --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD manquant dans .env}
      KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET:?KEYCLOAK_CLIENT_SECRET manquant dans .env}
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/bd_univers_audit
      KC_DB_USERNAME: postgres
      KC_DB_PASSWORD: ${DB_PASSWORD}
      KC_DB_SCHEMA: keycloak
      KC_HOSTNAME: https://${DOMAIN:?DOMAIN manquant dans .env}/auth
      KC_HTTP_RELATIVE_PATH: /auth
      KC_HTTP_ENABLED: "true"
      KC_PROXY_HEADERS: xforwarded
    volumes:
      - asce_keycloak_data:/opt/keycloak/data
      - ./build/realm:/opt/keycloak/data/import:ro
      - ./sources/back/back-end/keycloak/themes/asce-lc:/opt/keycloak/themes/asce-lc:ro
    depends_on:
      postgres:
        condition: service_healthy
    networks: [asce-data, asce-front]

  backend:
    build:
      context: .
      dockerfile: build/back.Dockerfile
    image: asce-lc-backend:latest
    container_name: asce_backend
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_PASSWORD: ${DB_PASSWORD}
      KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET}
      MAIL_PASSWORD: ${MAIL_PASSWORD:-}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      ASCE_ADMIN_SUB: ${ASCE_ADMIN_SUB}
      AT_API_KEY: ${AT_API_KEY:-unused}
      # Émetteur public porté par les jetons ; les clés sont lues par le réseau interne.
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: https://${DOMAIN}/auth/realms/asce-lc
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: http://keycloak:8080/auth/realms/asce-lc/protocol/openid-connect/certs
      KEYCLOAK_ADMIN_SERVER_URL: http://keycloak:8080/auth
      APP_API_BASE_URL: https://${DOMAIN}
      APP_FRONTEND_URL: https://${DOMAIN}
      # Un serveur SMTP injoignable ne doit pas faire passer toute l'application en DOWN.
      MANAGEMENT_HEALTH_MAIL_ENABLED: "false"
    volumes:
      - asce_backend_uploads:/app/uploads
      - asce_backend_logs:/app/logs
    depends_on:
      postgres:
        condition: service_healthy
    networks: [asce-data, asce-front]

  proxy:
    build:
      context: .
      dockerfile: build/proxy.Dockerfile
      args:
        DOMAIN: ${DOMAIN}
    image: asce-lc-proxy:latest
    container_name: asce_proxy
    restart: unless-stopped
    environment:
      DOMAIN: ${DOMAIN}
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ${CERT_DIR:-/opt/asce/certs}:/etc/nginx/certs:ro
    depends_on:
      - backend
      - keycloak
    networks: [asce-front]

volumes:
  asce_postgres_data:
  asce_keycloak_data:
  asce_redis_data:
  asce_backend_uploads:
  asce_backend_logs:

networks:
  # Réseau sans accès sortant : postgres et redis n'y sont joignables que par keycloak/backend.
  asce-data:
    driver: bridge
    internal: true
  asce-front:
    driver: bridge
EOF
```

- [ ] **Step 4: Préparer un `.env` de répétition locale**

Run:
```bash
cd "/c/Users/siakour.drabo/Documents/LAB/UNIVERSAUDITES/deploy-rpd"
for k in DB_PASSWORD KEYCLOAK_ADMIN_PASSWORD KEYCLOAK_CLIENT_SECRET REDIS_PASSWORD; do sed -i "s|^$k=.*|$k=$(openssl rand -hex 24)|" .env; done
sed -i "s|^CERT_DIR=.*|CERT_DIR=./build/certs-test|" .env
docker compose config >/dev/null && echo CONFIG_OK
```
Expected: `CONFIG_OK`. (Ce `.env` de répétition n'est pas celui du serveur.)

- [ ] **Step 5: Vérifier que les ports 80/443 sont libres sur le PC**

Run: `netstat -ano | grep -E ":(80|443) .*LISTENING" || echo LIBRES`
Expected: `LIBRES`. Sinon arrêter le service qui les occupe avant de continuer.

- [ ] **Step 6: Générer le realm puis démarrer la stack**

Run:
```bash
export MSYS_NO_PATHCONV=1
bash scripts/gen-realm.sh
docker compose up -d --build
docker compose ps
```
Expected: `realm généré -> build/realm/realm-export.json (… occurrences …)` ; puis 5 services `running` (postgres `healthy`).

- [ ] **Step 7: Attendre le backend et lire les journaux**

Run:
```bash
for i in $(seq 1 60); do docker compose logs backend 2>&1 | grep -q "Started UniversAuditsApplication" && break; sleep 5; done
docker compose logs backend 2>&1 | grep -E "Started UniversAuditsApplication|KeycloakAdminService initialisé|Agent admin créé|Caused by" | head
docker compose logs keycloak 2>&1 | grep -E "Imported realm|Listening on|ERROR" | head
```
Expected: `Started UniversAuditsApplication`, `serverUrl=http://keycloak:8080/auth` dans la ligne `KeycloakAdminService initialisé`, `Agent admin créé`, `Imported realm asce-lc`, aucun `Caused by`. La ligne `serverUrl=…/auth` confirme que la variable d'environnement a bien été prise en compte.

---

### Task 5: Scripts de déploiement et contrôles de fumée

**Files:**
- Create: `scripts/smoke-test.sh`, `scripts/deploy.sh`

**Interfaces:**
- Consumes: stack de la Task 4, `.env`.
- Produces: `smoke-test.sh [IP]` (variable `SMOKE_INSECURE=1` pour un certificat auto-signé ; code de sortie ≠ 0 si un contrôle échoue) ; `deploy.sh` (enchaîne fetch, realm, build, up, attente, smoke-test).

- [ ] **Step 1: Écrire `scripts/smoke-test.sh`**

```bash
cat > scripts/smoke-test.sh <<'EOF'
#!/bin/bash
# Usage: bash scripts/smoke-test.sh [IP]   (IP: force la résolution de $DOMAIN, ex. 127.0.0.1)
# SMOKE_INSECURE=1 : accepte un certificat auto-signé (répétition locale uniquement).
set -uo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
BASE="https://$DOMAIN"
CURL=(curl -s -m 30)
[ -n "${1:-}" ] && CURL+=(--resolve "$DOMAIN:443:$1" --resolve "$DOMAIN:80:$1")
[ "${SMOKE_INSECURE:-0}" = "1" ] && CURL+=(-k)
FAIL=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (attendu: $3, obtenu: $2)"; FAIL=$((FAIL+1)); fi; }
code() { "${CURL[@]}" -o /dev/null -w '%{http_code}' "$@"; }

check "HTTP -> HTTPS (301)"                       "$(code "http://$DOMAIN/")" "301"
check "Page d'accueil Angular (200)"              "$(code "$BASE/")" "200"
check "Page d'accueil contient <app-root"         "$("${CURL[@]}" "$BASE/" | grep -c '<app-root')" "1"
ISS=$("${CURL[@]}" "$BASE/auth/realms/asce-lc/.well-known/openid-configuration" | sed -n 's/.*"issuer":"\([^"]*\)".*/\1/p')
check "Keycloak : émetteur public"                "$ISS" "$BASE/auth/realms/asce-lc"
check "Backend /actuator/health = UP (interne)"   "$(docker compose exec -T proxy wget -qO- http://backend:8081/actuator/health | grep -c '"status":"UP"')" "1"
check "Endpoint public sans jeton (200)"          "$(code "$BASE/api/v1/config/enums")" "200"
check "Endpoint protégé sans jeton (401)"         "$(code "$BASE/api/v1/dossiers")" "401"

TOKEN=$("${CURL[@]}" -d grant_type=client_credentials -d client_id=asce-lc-backend -d "client_secret=$KEYCLOAK_CLIENT_SECRET" \
  "$BASE/auth/realms/asce-lc/protocol/openid-connect/token" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
check "Jeton obtenu via l'URL publique"          "$([ -n "$TOKEN" ] && echo oui || echo non)" "oui"
CODE=$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/dossiers")
check "Endpoint protégé avec jeton (pas 401)"     "$([ "$CODE" != "401" ] && echo ok || echo 401)" "ok"

BIG30=$(head -c 30000000 /dev/zero | "${CURL[@]}" -o /dev/null -w '%{http_code}' -X POST --data-binary @- -H 'Content-Type: application/octet-stream' "$BASE/api/v1/dossiers/public/submit")
check "Corps de 30 Mo accepté par nginx (pas 413)" "$([ "$BIG30" != "413" ] && echo ok || echo 413)" "ok"
BIG60=$(head -c 60000000 /dev/zero | "${CURL[@]}" -o /dev/null -w '%{http_code}' -X POST --data-binary @- -H 'Content-Type: application/octet-stream' "$BASE/api/v1/dossiers/public/submit")
check "Corps de 60 Mo refusé par nginx (413)"      "$BIG60" "413"

for p in 8080 8081 5432 6379; do
  if (exec 3<>/dev/tcp/127.0.0.1/$p) 2>/dev/null; then r=ouvert; else r=ferme; fi
  check "Port interne $p non exposé sur l'hôte"    "$r" "ferme"
done

echo; [ "$FAIL" -eq 0 ] && echo "TOUS LES CONTROLES PASSENT" || echo "$FAIL CONTROLE(S) EN ECHEC"
exit "$FAIL"
EOF
```

- [ ] **Step 2: Écrire `scripts/deploy.sh`**

```bash
cat > scripts/deploy.sh <<'EOF'
#!/bin/bash
# Déploiement / mise à jour complet(e). À lancer depuis /opt/asce/deploy sur le serveur.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "ERREUR: .env absent (copier .env.example puis le remplir)"; exit 1; }
set -a; . ./.env; set +a
[ -f "$CERT_DIR/fullchain.pem" ] && [ -f "$CERT_DIR/privkey.pem" ] || { echo "ERREUR: fullchain.pem / privkey.pem absents de $CERT_DIR"; exit 1; }

bash scripts/fetch-sources.sh
bash scripts/gen-realm.sh
docker compose build
docker compose up -d

echo "Attente du backend (240 s max)..."
for i in $(seq 1 48); do
  docker compose exec -T proxy wget -qO- http://backend:8081/actuator/health 2>/dev/null | grep -q '"status":"UP"' && break
  if [ "$i" -eq 48 ]; then echo "ECHEC: backend non UP"; docker compose logs --tail=80 backend; exit 1; fi
  sleep 5
done
bash scripts/smoke-test.sh "${SMOKE_IP:-}"
EOF
```
(`${SMOKE_IP:-}` vide sur le serveur : la résolution passe par le DNS interne ; pour la répétition locale : `SMOKE_IP=127.0.0.1 SMOKE_INSECURE=1 bash scripts/deploy.sh`.)

- [ ] **Step 3: Lancer les contrôles sur la stack locale de la Task 4**

Run: `SMOKE_INSECURE=1 bash scripts/smoke-test.sh 127.0.0.1`
Expected: 15 lignes, toutes `PASS`, fin sur `TOUS LES CONTROLES PASSENT`.
Diagnostic si échec : `Keycloak : émetteur public` FAIL → relire `docker compose logs keycloak` (chaîne `Caused by` complète) et le `KC_HOSTNAME` ; `Endpoint protégé avec jeton` FAIL (401) → `docker compose logs backend | grep -i "invalid_token\|issuer"` (désaccord émetteur/jwk).

- [ ] **Step 4: Répéter le déploiement complet et sa relance (idempotence)**

Run: `SMOKE_IP=127.0.0.1 SMOKE_INSECURE=1 bash scripts/deploy.sh`
Expected: fetch (mêmes commits), realm regénéré, build (cache), `up -d` sans recréation inutile, contrôles tous `PASS`.

- [ ] **Step 5: Vérifier la persistance des pièces jointes après recréation du backend**

Run:
```bash
docker compose exec -T backend sh -c 'echo test > /app/uploads/persist.txt'
docker compose up -d --force-recreate backend
sleep 5; docker compose exec -T backend cat /app/uploads/persist.txt
```
Expected: `test` (le volume nommé conserve les fichiers).

- [ ] **Step 6: Arrêter la stack locale de répétition**

Run: `docker compose down -v`
Expected: conteneurs et volumes supprimés (données de test uniquement).

---

### Task 6: Guide d'exploitation et sauvegarde

**Files:**
- Create: `docs/deploiement-ubuntu.md`, `scripts/backup.sh`

**Interfaces:**
- Consumes: tous les scripts précédents.
- Produces: procédure pas à pas exécutable par l'utilisateur sur `rpd`.

- [ ] **Step 1: Écrire `scripts/backup.sh`**

```bash
cat > scripts/backup.sh <<'EOF'
#!/bin/bash
# Sauvegarde quotidienne de la base (à planifier avec cron). Conserve 14 jours.
set -euo pipefail
DEST=/opt/asce/backups
mkdir -p "$DEST"
FILE="$DEST/bd_univers_audit_$(date +%Y%m%d_%H%M%S).sql.gz"
docker exec asce_postgres pg_dump -U postgres -d bd_univers_audit | gzip > "$FILE"
test -s "$FILE" || { echo "ECHEC: sauvegarde vide"; rm -f "$FILE"; exit 1; }
find "$DEST" -name 'bd_univers_audit_*.sql.gz' -mtime +14 -delete
echo "OK $FILE"
EOF
```

- [ ] **Step 2: Tester la sauvegarde sur une stack locale démarrée**

Run: `docker compose up -d && sleep 30 && sed 's#/opt/asce/backups#/tmp/asce-backups#' scripts/backup.sh | bash && ls -l /tmp/asce-backups`
Expected: ligne `OK /tmp/asce-backups/bd_univers_audit_….sql.gz` et un fichier non vide. Puis `docker compose down -v`.

- [ ] **Step 3: Écrire `docs/deploiement-ubuntu.md`** avec les sections suivantes, chaque commande copiable telle quelle et **une commande par bloc de code** (le terminal de l'utilisateur mélange les collages multi-lignes) :

1. **Prérequis sur rpd** : `docker compose version` (≥ 2.17), `git --version`, `openssl version`, espace disque libre ≥ 10 Go (`df -h /var/lib/docker`), RAM ≥ 4 Go (`free -h`) — le build Angular utilise jusqu'à 4 Go ; sinon ajouter 2 Go de swap.
2. **Certificat SSL (déjà en place le 2026-09-23)** : décrire ce qui a été fait et comment le refaire — fichiers `asce-lc.bf.crt`, `asce-lc.bf.key`, `gd_bundle-g2-g1.crt` envoyés par `scp` **depuis le PC de développement** (le serveur WAMP n'a pas `scp`) vers `/opt/asce/certs/` (piège : la commande `scp` se tape sur le PC Windows, pas dans la session `rpd`, sinon `C:\…` est lu comme un nom d'hôte) ; assemblage `{ sed 's/\r$//' asce-lc.bf.crt; echo; sed 's/\r$//' gd_bundle-g2-g1.crt; } > fullchain.pem`, `sed 's/\r$//' asce-lc.bf.key > privkey.pem`, `chmod 600 privkey.pem asce-lc.bf.key` ; contrôles : `grep -c "BEGIN CERTIFICATE" fullchain.pem` = 2, `openssl x509 -in fullchain.pem -noout -subject -dates -ext subjectAltName`, empreintes identiques `openssl x509 -in fullchain.pem -noout -pubkey | openssl sha256` et `openssl pkey -in privkey.pem -pubout | openssl sha256` (note : un `openssl` Windows ajoute un `\r` et donne une empreinte différente ; comparer les deux empreintes de la même machine) ; **échéance du certificat : 2027-03-08**, à renouveler avant (refaire l'assemblage puis `docker compose restart proxy`) ; si le certificat vient d'une autorité interne, l'installer sur les postes clients.
3. **Copie du dossier** depuis le PC de développement (PowerShell ou Git Bash, commande en une ligne) : `cd deploy-rpd` puis `tar --exclude=./sources --exclude=./.env --exclude=./build/certs-test --exclude=./build/gen --exclude=./build/realm -czf - . | ssh administrator@ADRESSE "mkdir -p /opt/asce/deploy && tar -xzf - -C /opt/asce/deploy"` (`ADRESSE` = l'adresse utilisée pour `ssh`, sans `<` ni `>`).
4. **Création du `.env`** : `cd /opt/asce/deploy && cp .env.example .env`, génération des quatre secrets par `for k in DB_PASSWORD KEYCLOAK_ADMIN_PASSWORD KEYCLOAK_CLIENT_SECRET REDIS_PASSWORD; do sed -i "s|^$k=.*|$k=$(openssl rand -hex 24)|" .env; done`, renseigner `MAIL_PASSWORD`, `GITHUB_TOKEN` si dépôts privés (jeton fin-grain, lecture du contenu), `chmod 600 .env`, **noter `KEYCLOAK_ADMIN_PASSWORD` dans un endroit sûr**.
5. **Premier déploiement** : `bash scripts/deploy.sh` ; résultat attendu : `TOUS LES CONTROLES PASSENT` ; premier build de 10 à 20 minutes.
6. **Vérification depuis un poste client** : ouvrir `https://denoncer.asce-lc.bf`, se connecter ; `nc -zv <IP_RPD> 8081` doit échouer ; console Keycloak sur `https://denoncer.asce-lc.bf/auth/admin` avec l'utilisateur `admin`. Prérequis DNS : `denoncer.asce-lc.bf` doit pointer vers l'IP de `rpd` (et non vers le serveur WAMP, qui héberge déjà `auth.`, `agenda.`, `sda.`).
7. **Actions de sécurité immédiates** : le realm importé contient des comptes réels de développement (`admin.asce`, `asce55555`, etc., avec leurs mots de passe de dev) : les modifier ou les désactiver dans la console Keycloak avant toute ouverture aux utilisateurs ; faire changer le mot de passe SMTP `mailer.gov.bf` présent en clair dans `realm-export.json`.
8. **Mise à jour** : `cd /opt/asce/deploy && bash scripts/deploy.sh` (reclone/màj des deux branches, rebuild, relance). **Retour arrière** : dans `sources/back` ou `sources/front`, `git checkout <commit précédent>` puis `docker compose build && docker compose up -d` (ne pas relancer `fetch-sources.sh`, qui réaligne sur la branche).
9. **Sauvegarde** : `chmod +x scripts/backup.sh` et cron `0 2 * * * /opt/asce/deploy/scripts/backup.sh >> /var/log/asce-backup.log 2>&1` ; restauration : `gunzip -c <fichier> | docker exec -i asce_postgres psql -U postgres -d bd_univers_audit` sur base vide.
10. **Changer le `DOMAIN` après le premier démarrage** : l'import du realm ne se refait que sur Keycloak vierge ; procédure : `docker compose down`, `docker volume rm asce_asce_keycloak_data`, supprimer le schéma : `docker compose up -d postgres` puis `docker exec asce_postgres psql -U postgres -d bd_univers_audit -c 'DROP SCHEMA keycloak CASCADE; CREATE SCHEMA keycloak;'`, puis `bash scripts/deploy.sh` (les utilisateurs Keycloak repartent du realm d'origine).
11. **Diagnostic** : `docker compose ps`, `docker compose logs --tail=200 <service>` (lire la chaîne `Caused by` complète), tableau symptôme → cause : boucle de redirection Keycloak (redirectUris/hostname, point 10), 401 généralisé (émetteur/jwk), 413 (taille), page blanche (`docker compose logs proxy`), `nginx: [emerg] SSL` (certificat/clé non appariés, point 2).
12. **Limites connues de cette version** (code de juillet) : pas de limitation de débit sur les endpoints publics anonymes ; pas de 2FA ni de protection anti-force-brute Keycloak ; disque non chiffré ; sauvegardes non copiées hors du serveur ; certificat à renouveler avant le 2027-03-08 ; création d'utilisateurs depuis l'application à vérifier après déploiement (`KeycloakAdminService` s'authentifie sur le realm `master` avec le client `asce-lc-backend`, défini dans le realm `asce-lc`).

- [ ] **Step 4: Relire le guide contre la stack locale**

Exécuter chaque commande des sections 4, 5 et 8 dans un dossier de copie propre (`cp -r deploy-rpd /tmp/deploy-copy` puis suppression de `sources`, `.env`, `build/realm`) avec `SMOKE_IP=127.0.0.1 SMOKE_INSECURE=1` et `CERT_DIR=./build/certs-test`, et vérifier que le résultat correspond à ce que le guide annonce. Corriger le guide sur tout écart constaté.

- [ ] **Step 5: Livrer**

Rapporter à l'utilisateur : chemin de `deploy-rpd/`, commande de copie (section 3), et la sortie complète de la répétition locale (Task 5, Steps 3 à 5). Ne rien commiter ni pousser sans demande.

---

## Self-Review (effectuée)

- **Couverture du spec** : §1 critères → smoke-test (Task 5) ; §2.1 Dockerfiles → Tasks 2/3 ; §2.2 base vide → Task 2 ; §2.3 ports → Task 4/5 ; §2.4–2.5 hôtes → Tasks 3/4 (`environment.prod.ts` généré, realm, variables d'env) ; §2.6 limites → guide §12 ; §2.7 SMTP en clair → guide §7 ; §2.8 → guide §12 ; §4.1–4.8 → Tasks 2 à 6 ; §6 vérification → répétition locale Task 4/5.
- **Placeholders** : aucun ; les seuls éléments à remplir sont les secrets, générés par commande, et l'adresse de `rpd` (`ADRESSE`), inconnue de l'assistant.
- **Cohérence** : noms de volumes, services, conteneurs (`asce_postgres`, `asce_backend`…), variables `.env` et chemins `sources/back/back-end`, `sources/front` identiques entre tâches ; le nom de projet Compose `asce` produit les volumes préfixés `asce_` (d'où `asce_asce_keycloak_data` dans le guide §10).
- **Risques restants signalés** : Step 3 de la Task 2 (rejeu des migrations) peut révéler des divergences migration/entité qui exigent une décision ; le dossier de sortie Angular (`dist/sakai-ng/browser`) est vérifié par le Dockerfile ; le certificat expire le 2027-03-08.
