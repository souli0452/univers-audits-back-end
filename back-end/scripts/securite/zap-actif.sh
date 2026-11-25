#!/bin/bash
# Scan actif OWASP ZAP de l'API sur un environnement JETABLE (audit du 2026-11-24).
#
#   bash scripts/securite/zap-actif.sh up      construit l'environnement jetable (base, Redis, Keycloak, courriel factice, serveur)
#   bash scripts/securite/zap-actif.sh scan    attaque l'API : une fois sans connexion, une fois en administrateur
#   bash scripts/securite/zap-actif.sh down    supprime tout (conteneurs, réseau, dossier de travail)
#
# JAMAIS contre la production ni contre la base de travail : tout est neuf, sur des ports à part, avec des mots de passe
# aléatoires. Le courriel part vers un serveur factice (Mailpit, http://localhost:28025) : aucun message réel. Les SMS sont coupés.
# Le limiteur de débit est désactivé ici seulement, pour que ZAP ne soit pas freiné (il a ses propres tests unitaires).
# Prérequis : docker, Java 17+ et Maven (./mvnw), curl, node. Dossier de travail : $W (défaut : ./zap-travail).
set -uo pipefail
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
W="${W:-$RACINE/zap-travail}"
P=asce-zap                         # préfixe des conteneurs et du réseau
PORT_KC=28080; PORT_APP=28081; PORT_MAIL=28025
ZAP_IMAGE="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:stable}"
DUREE_MAX="${DUREE_MAX:-45}"       # minutes maximum d'attaque par scan
win() { command -v cygpath >/dev/null 2>&1 && cygpath -m "$1" || echo "$1"; }
etat() { cat "$W/$1" 2>/dev/null; }

cmd_down() {
  docker rm -f $P-app $P-kc $P-pg $P-redis $P-mail >/dev/null 2>&1
  docker network rm $P >/dev/null 2>&1
  [ "${1:-}" = "garder" ] || rm -rf "$W"
  echo "Environnement jetable supprimé."
}

cmd_up() {
  [ -d "$RACINE/src/main/resources/db/changelog" ] || { echo "ARRET : lancer depuis le dépôt du serveur"; exit 1; }
  cmd_down garder
  mkdir -p "$W/import" "$W/wrk"
  local PG_ADMIN PG_APP PG_KC REDIS KC_ADMIN CLIENT_SECRET
  PG_ADMIN="$(openssl rand -hex 16)"; PG_APP="$(openssl rand -hex 16)"; PG_KC="$(openssl rand -hex 16)"
  REDIS="$(openssl rand -hex 16)"; KC_ADMIN="$(openssl rand -hex 16)"; CLIENT_SECRET="$(openssl rand -hex 16)"
  printf '%s' "$PG_ADMIN" > "$W/pg_admin"; printf '%s' "$PG_APP" > "$W/pg_app"; printf '%s' "$CLIENT_SECRET" > "$W/client_secret"; printf '%s' "$KC_ADMIN" > "$W/kc_admin"

  docker network create $P >/dev/null
  docker run -d --name $P-pg --network $P --network-alias pg -p 25432:5432 -e POSTGRES_PASSWORD="$PG_ADMIN" -e POSTGRES_DB=bd_univers_audit postgres:16 >/dev/null
  docker run -d --name $P-redis --network $P --network-alias redis redis:7-alpine redis-server --requirepass "$REDIS" >/dev/null
  docker run -d --name $P-mail --network $P --network-alias mail -p $PORT_MAIL:8025 -e MP_SMTP_AUTH_ACCEPT_ANY=1 -e MP_SMTP_AUTH_ALLOW_INSECURE=1 axllent/mailpit >/dev/null
  for i in $(seq 1 40); do docker exec $P-pg pg_isready -U postgres -d bd_univers_audit >/dev/null 2>&1 && break; sleep 1; done; sleep 4

  # Comptes de base limités (même configuration que la production visée) : le serveur tourne avec asce_app, Keycloak avec asce_keycloak
  { printf "\\\\set app_password '%s'\n\\\\set keycloak_password '%s'\n" "$PG_APP" "$PG_KC"; cat "$RACINE/docs/deploiement/2026-11-24/comptes-limites.sql"; } \
    | docker exec -i $P-pg psql -U postgres -d bd_univers_audit -v ON_ERROR_STOP=1 -q >/dev/null || { echo "ARRET : comptes de base"; exit 1; }

  # Royaume : jetons valables 4 h (le scan dure longtemps), sinon identique au fichier d'import
  node -e "
    const fs=require('fs');const j=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));
    j.accessTokenLifespan=14400; j.ssoSessionIdleTimeout=28800; j.ssoSessionMaxLifespan=28800;
    // le fichier d'import porte le secret sous forme de paramètre ; en production gen-realm.sh le remplit : ici, valeur aléatoire
    j.clients.find(c=>c.clientId==='asce-lc-backend').secret=process.argv[4];
    fs.writeFileSync(process.argv[2],JSON.stringify(j));
    const u=j.users.find(x=>x.username==='admin.asce'); fs.writeFileSync(process.argv[3],u.id);
  " "$(win "$RACINE/keycloak/import/realm-export.json")" "$(win "$W/import/realm-export.json")" "$(win "$W/admin_sub")" "$CLIENT_SECRET" || { echo "ARRET : royaume"; exit 1; }

  docker run -d --name $P-kc --network $P --network-alias kc -p $PORT_KC:8080 \
    -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD="$KC_ADMIN" \
    -e KC_DB=postgres -e KC_DB_URL=jdbc:postgresql://pg:5432/bd_univers_audit -e KC_DB_USERNAME=asce_keycloak -e KC_DB_PASSWORD="$PG_KC" -e KC_DB_SCHEMA=keycloak \
    -e KC_HOSTNAME_STRICT=false -e KC_HOSTNAME_STRICT_HTTPS=false -e KC_HTTP_ENABLED=true -e KEYCLOAK_CLIENT_SECRET="$CLIENT_SECRET" \
    -v "$(win "$W/import"):/opt/keycloak/data/import:ro" -v "$(win "$RACINE/keycloak/themes/asce-lc"):/opt/keycloak/themes/asce-lc:ro" \
    quay.io/keycloak/keycloak:25.0.2 start-dev --import-realm >/dev/null
  echo "Keycloak démarre (1 à 2 minutes)…"
  for i in $(seq 1 90); do [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$PORT_KC/realms/asce-lc/.well-known/openid-configuration)" = 200 ] && break; sleep 3; done
  [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$PORT_KC/realms/asce-lc/.well-known/openid-configuration)" = 200 ] || { echo "ARRET : Keycloak ne démarre pas"; docker logs --tail 20 $P-kc; exit 1; }

  # Comptes de test : mot de passe connu, sans code à usage unique (le scan ne peut pas le saisir)
  local MDP; MDP="Zap-$(openssl rand -hex 8)"; printf '%s' "$MDP" > "$W/mdp_test"
  docker exec $P-kc /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password "$KC_ADMIN" >/dev/null 2>&1
  for u in admin.asce asce55555 asce4455565 asce20033; do
    docker exec $P-kc /opt/keycloak/bin/kcadm.sh set-password -r asce-lc --username "$u" --new-password "$MDP" >/dev/null 2>&1
    local id; id="$(docker exec $P-kc /opt/keycloak/bin/kcadm.sh get users -r asce-lc -q username="$u" --fields id --format csv --noquotes 2>/dev/null | tr -d '\r\n')"
    [ -n "$id" ] && docker exec $P-kc /opt/keycloak/bin/kcadm.sh update "users/$id" -r asce-lc -s 'requiredActions=[]' >/dev/null 2>&1
  done

  # Serveur (jar construit ici, lancé dans un conteneur Java 17 sur le même réseau)
  # (mvnw a besoin de la conversion de chemins de Git Bash, que ce script coupe pour Docker : on la rétablit pour lui)
  ls "$RACINE"/target/*.jar >/dev/null 2>&1 || (unset MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL; cd "$RACINE" && ./mvnw -B -q -DskipTests package) || { echo "ARRET : construction"; exit 1; }
  local JAR; JAR="$(ls "$RACINE"/target/*.jar | grep -v '\.original$' | head -1)"
  docker run -d --name $P-app --network $P --network-alias app -p $PORT_APP:8081 \
    -v "$(win "$JAR"):/app/app.jar:ro" \
    -e SPRING_PROFILES_ACTIVE=dev \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/bd_univers_audit -e SPRING_DATASOURCE_USERNAME=asce_app -e SPRING_DATASOURCE_PASSWORD="$PG_APP" -e DB_PASSWORD="$PG_APP" \
    -e SPRING_DATA_REDIS_HOST=redis -e SPRING_DATA_REDIS_PORT=6379 -e REDIS_PASSWORD="$REDIS" \
    -e KEYCLOAK_CLIENT_SECRET="$CLIENT_SECRET" -e ASCE_ADMIN_SUB="$(etat admin_sub)" -e KEYCLOAK_ADMIN_SERVER_URL=http://kc:8080 \
    -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:8080/realms/asce-lc \
    -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://kc:8080/realms/asce-lc/protocol/openid-connect/certs \
    -e SPRING_MAIL_HOST=mail -e SPRING_MAIL_PORT=1025 -e SPRING_MAIL_USERNAME=essai@jetable.test -e MAIL_PASSWORD=essai \
    -e SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=false -e SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false \
    -e AT_API_KEY= -e SMS_ENABLED=false -e RATE_LIMIT_ENABLED=false \
    -e STORAGE_UPLOAD_DIR=/tmp/uploads -e LOGGING_FILE_NAME=/tmp/app.log -e SPRING_JPA_SHOW_SQL=false \
    -e LOGGING_LEVEL_ROOT=INFO -e LOGGING_LEVEL_GOV_BF_ASCELC=INFO -e LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=INFO -e LOGGING_LEVEL_ORG_HIBERNATE_SQL=WARN \
    -e APP_API_BASE_URL=http://app:8081 -e APP_FRONTEND_URL=http://localhost:4200 \
    eclipse-temurin:17-jre java -jar /app/app.jar >/dev/null
  echo "Le serveur démarre (1 à 2 minutes)…"
  for i in $(seq 1 90); do curl -s http://localhost:$PORT_APP/actuator/health 2>/dev/null | grep -q '"status":"UP"' && break; sleep 3; done
  curl -s http://localhost:$PORT_APP/actuator/health | grep -q '"status":"UP"' || { echo "ARRET : le serveur ne démarre pas"; docker logs --tail 40 $P-app; exit 1; }
  echo "OK : environnement prêt. Serveur http://localhost:$PORT_APP , Keycloak http://localhost:$PORT_KC , courriels http://localhost:$PORT_MAIL"
}

jeton() {   # jeton <utilisateur>
  curl -s -d grant_type=password -d client_id=asce-lc-backend -d client_secret="$(etat client_secret)" \
       -d username="$1" -d password="$(etat mdp_test)" "http://localhost:$PORT_KC/realms/asce-lc/protocol/openid-connect/token" \
    | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{try{process.stdout.write(JSON.parse(s).access_token||'')}catch(e){}})"
}

cmd_scan() {
  [ -f "$W/client_secret" ] || { echo "ARRET : lancer d'abord « up »"; exit 1; }
  local TOKEN; TOKEN="$(jeton admin.asce)"
  [ -n "$TOKEN" ] || { echo "ARRET : pas de jeton pour admin.asce"; exit 1; }
  # (l émetteur du jeton est http://localhost:8080 : port interne de Keycloak, d où la configuration du serveur)
  curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:$PORT_APP/v3/api-docs" -o "$W/wrk/openapi.json"
  node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8'));console.log('Définition de l API : '+Object.keys(j.paths).length+' chemins')" "$(win "$W/wrk/openapi.json")" || { echo "ARRET : définition illisible"; exit 1; }
  # Le vrai appel : ZAP lit la définition, puis attaque chaque chemin
  local COMMUN="-config scanner.maxScanDurationInMins=$DUREE_MAX -config scanner.threadPerHost=4"
  echo "== Scan 1/2 : sans connexion (ce qu'un inconnu peut atteindre) =="
  docker run --rm --network $P -v "$(win "$W/wrk"):/zap/wrk:rw" "$ZAP_IMAGE" zap-api-scan.py \
    -t /zap/wrk/openapi.json -f openapi -O http://app:8081 -r anonyme.html -J anonyme.json -I -z "$COMMUN" > "$W/anonyme.log" 2>&1
  echo "terminé (code $?)"
  echo "== Scan 2/2 : connecté en administrateur (tous les droits : ce que fait un compte piraté) =="
  local AUTH="-config replacer.full_list\\(0\\).description=auth -config replacer.full_list\\(0\\).enabled=true -config replacer.full_list\\(0\\).matchtype=REQ_HEADER -config replacer.full_list\\(0\\).matchstring=Authorization -config replacer.full_list\\(0\\).regex=false -config replacer.full_list\\(0\\).replacement=\"Bearer $TOKEN\""
  docker run --rm --network $P -v "$(win "$W/wrk"):/zap/wrk:rw" "$ZAP_IMAGE" zap-api-scan.py \
    -t /zap/wrk/openapi.json -f openapi -O http://app:8081 -r admin.html -J admin.json -I -z "$COMMUN $AUTH" > "$W/admin.log" 2>&1
  echo "terminé (code $?)"
  echo "Rapports : $W/wrk/anonyme.html  $W/wrk/admin.html (et .json)"
}

case "${1:-}" in
  up) cmd_up ;;
  scan) cmd_scan ;;
  down) cmd_down ;;
  *) echo "Usage : $0 up | scan | down"; exit 2 ;;
esac
