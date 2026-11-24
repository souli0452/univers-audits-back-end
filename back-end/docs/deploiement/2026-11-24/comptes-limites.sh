#!/bin/bash
# Essai des comptes de base de données limités (audit OWASP du 2026-11-24, point S-07).
#
#   bash docs/deploiement/2026-11-24/comptes-limites.sh        (depuis le dossier back-end, ou depuis rpd : sources/back/back-end)
#
# Avec un PostgreSQL 16 jetable (conteneur), les 41 vraies migrations (Liquibase) et le script comptes-limites.sql :
#   - l'état de départ est celui de la production d'aujourd'hui : tout appartient à « postgres », schéma « keycloak » à côté ;
#   - après le script, le compte de l'application (asce_app) n'est pas administrateur, possède les tables de l'application,
#     NE VOIT PAS le schéma de Keycloak, ne peut ni lancer de commande système, ni créer de compte, ni lire un fichier ;
#   - une migration future (ajout de colonne, d'index, de table) passe encore avec ce compte (c'est ce que fait le déploiement) ;
#   - le compte de Keycloak (asce_keycloak) crée et modifie dans son schéma, et ne voit pas les tables de l'application ;
#   - le script peut être relancé sans erreur.
# Prérequis : docker. Rien n'est installé ni modifié ailleurs. Aucune base existante n'est touchée.
set -uo pipefail
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
DIR="$(cd "$(dirname "$0")" && pwd)"
RACINE="$(cd "$DIR/../../.." && pwd)"
SQL="$DIR/comptes-limites.sql"
IMG_PG="${PG_IMAGE:-postgres:16}"
IMG_LB="${LIQUIBASE_IMAGE:-liquibase/liquibase:4.31}"
RES="asce-test-comptes-$$"
T="$(mktemp -d)"
RES_DIR="$RACINE/src/main/resources"
if command -v cygpath >/dev/null 2>&1; then T="$(cygpath -m "$T")"; RES_DIR="$(cygpath -m "$RES_DIR")"; fi
FAIL=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (attendu: $3, obtenu: $2)"; FAIL=$((FAIL+1)); fi; }
nettoyer() { docker rm -f "$RES-pg" >/dev/null 2>&1; docker network rm "$RES" >/dev/null 2>&1; rm -rf "$T"; }
trap nettoyer EXIT

[ -f "$SQL" ] || { echo "FAIL  comptes-limites.sql est absent ($SQL)"; exit 1; }
[ -d "$RES_DIR/db/changelog/migrations" ] || { echo "FAIL  migrations introuvables ($RES_DIR)"; exit 1; }

PA="essai-app-$RANDOM$RANDOM"; PK="essai-kc-$RANDOM$RANDOM"
docker network create "$RES" >/dev/null
docker run -d --name "$RES-pg" --network "$RES" --network-alias pg -e POSTGRES_PASSWORD=admin -e POSTGRES_DB=bd_univers_audit "$IMG_PG" >/dev/null
for i in $(seq 1 40); do docker exec "$RES-pg" pg_isready -U postgres -d bd_univers_audit >/dev/null 2>&1 && break; sleep 1; done
sleep 3   # le conteneur redémarre une fois après l'initialisation

admin() { docker exec -i "$RES-pg" psql -U postgres -d bd_univers_audit -v ON_ERROR_STOP=1 -q "$@"; }
# comme(compte, mot de passe, requête) : se connecte par le réseau (mot de passe exigé), renvoie la sortie sèche
comme() { docker exec -e PGPASSWORD="$2" "$RES-pg" psql -h pg -U "$1" -d bd_univers_audit -tAq -c "$3" 2>&1; }
refuse() { local s; s="$(comme "$1" "$2" "$3")"; if echo "$s" | grep -qiE "permission denied|must be (a )?superuser|must have|insufficient privilege|permission refus"; then echo refusé; else echo "ACCEPTÉ : $s"; fi; }
lb() { docker run --rm --network "$RES" -v "$RES_DIR:/liquibase/changelog:ro" -v "$T:/liquibase/extra:ro" "$IMG_LB" \
  --url="jdbc:postgresql://pg:5432/bd_univers_audit" --username="$1" --password="$2" \
  --search-path=/liquibase/changelog,/liquibase/extra --changelog-file="$3" update 2>&1; }
installer() { { printf "\\\\set app_password '%s'\n\\\\set keycloak_password '%s'\n" "$PA" "$PK"; cat "$SQL"; } | admin 2>&1; }

echo "== état de départ : la production d'aujourd'hui (tout appartient à postgres) =="
admin -c "CREATE SCHEMA keycloak; CREATE TABLE keycloak.user_entity(id int, username text); INSERT INTO keycloak.user_entity VALUES (1,'secret');" >/dev/null
SORTIE="$(lb postgres admin db/changelog/db.changelog-master.yaml)"
check "les 41 migrations passent avec postgres" "$(echo "$SORTIE" | grep -c 'Liquibase: Update has been successful')" "1"
NB_TABLES="$(admin -tAc "SELECT count(*) FROM pg_tables WHERE schemaname='public'")"

echo "== installation des comptes =="
SORTIE="$(installer)"
check "le script s'exécute sans erreur" "$(echo "$SORTIE" | grep -ciE 'error|erreur')" "0"
check "le script peut être relancé sans erreur" "$(installer | grep -ciE 'error|erreur')" "0"

echo "== le compte de l'application (asce_app) =="
check "n'est pas administrateur, ne crée ni base ni compte" "$(admin -tAc "SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls FROM pg_roles WHERE rolname='asce_app'")" "f"
check "se connecte avec son mot de passe"                   "$(comme asce_app "$PA" 'SELECT 1')" "1"
check "refuse un mauvais mot de passe"                      "$(comme asce_app mauvais 'SELECT 1' | grep -ci 'authentication failed')" "1"
check "possède toutes les tables de l'application"          "$(admin -tAc "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tableowner='asce_app'")" "$NB_TABLES"
check "possède aussi les séquences"                         "$(admin -tAc "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='S' AND pg_get_userbyid(c.relowner)<>'asce_app'")" "0"
check "lit et écrit dans les tables de l'application"       "$(comme asce_app "$PA" "SELECT count(*) FROM public.agent")" "0"
check "ne voit pas le schéma de Keycloak"                   "$(refuse asce_app "$PA" 'SELECT * FROM keycloak.user_entity')" "refusé"
check "ne peut pas lancer de commande système (COPY … PROGRAM)" "$(refuse asce_app "$PA" "COPY (SELECT 1) TO PROGRAM 'id'")" "refusé"
check "ne peut pas lire un fichier du serveur"              "$(refuse asce_app "$PA" "SELECT pg_read_file('/etc/passwd')")" "refusé"
check "ne peut pas créer de compte"                         "$(refuse asce_app "$PA" 'CREATE ROLE intrus LOGIN')" "refusé"
check "ne peut pas créer de base"                           "$(refuse asce_app "$PA" 'CREATE DATABASE intrus')" "refusé"
check "ne peut pas changer la configuration du serveur"     "$(refuse asce_app "$PA" "ALTER SYSTEM SET log_statement='all'")" "refusé"
check "n'a aucun droit sur le schéma de Keycloak"           "$(admin -tAc "SELECT has_schema_privilege('asce_app','keycloak','USAGE') OR has_schema_privilege('asce_app','keycloak','CREATE')")" "f"
check "ne peut pas se donner de droit sur Keycloak"         "$(comme asce_app "$PA" 'GRANT ALL ON SCHEMA keycloak TO asce_app; SELECT * FROM keycloak.user_entity' | grep -ci 'permission denied')" "1"

echo "== une migration future passe encore avec ce compte (déploiement suivant) =="
cat > "$T/ddl-essai.yaml" <<'EOF'
databaseChangeLog:
  - include:
      file: db/changelog/db.changelog-master.yaml
  - changeSet:
      id: zz-essai-ajout
      author: essai
      changes:
        - sql:
            sql: "ALTER TABLE public.agent ADD COLUMN zz_essai integer; CREATE INDEX zz_essai_idx ON public.agent(zz_essai); CREATE TABLE public.zz_essai_table(id int); INSERT INTO public.zz_essai_table VALUES (1);"
EOF
SORTIE="$(lb asce_app "$PA" ddl-essai.yaml)"
check "Liquibase applique l'ajout de colonne, d'index et de table" "$(echo "$SORTIE" | grep -c 'Liquibase: Update has been successful')" "1"
check "la colonne ajoutée existe"                           "$(comme asce_app "$PA" "SELECT count(*) FROM information_schema.columns WHERE table_name='agent' AND column_name='zz_essai'")" "1"
SORTIE="$(lb asce_app "$PA" db/changelog/db.changelog-master.yaml)"
check "sans nouveauté, la mise à jour passe aussi (déploiement sans migration)" "$(echo "$SORTIE" | grep -c "was executed successfully")" "1"

echo "== le compte de Keycloak (asce_keycloak) =="
check "n'est pas administrateur"                            "$(admin -tAc "SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls FROM pg_roles WHERE rolname='asce_keycloak'")" "f"
check "possède le schéma de Keycloak"                       "$(admin -tAc "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname='keycloak'")" "asce_keycloak"
check "possède ses tables"                                  "$(admin -tAc "SELECT count(*) FROM pg_tables WHERE schemaname='keycloak' AND tableowner<>'asce_keycloak'")" "0"
check "lit ses tables"                                      "$(comme asce_keycloak "$PK" 'SELECT count(*) FROM keycloak.user_entity')" "1"
check "crée et modifie dans son schéma"                     "$(comme asce_keycloak "$PK" 'CREATE TABLE keycloak.zz(a int); ALTER TABLE keycloak.zz ADD COLUMN b int; DROP TABLE keycloak.zz' | grep -ci 'error')" "0"
check "ne voit pas les tables de l'application"             "$(refuse asce_keycloak "$PK" 'SELECT * FROM public.agent')" "refusé"
check "ne peut pas lancer de commande système"              "$(refuse asce_keycloak "$PK" "COPY (SELECT 1) TO PROGRAM 'id'")" "refusé"

echo; [ "$FAIL" -eq 0 ] && echo "TOUS LES CONTROLES PASSENT" || echo "$FAIL CONTROLE(S) EN ECHEC"
exit "$FAIL"
