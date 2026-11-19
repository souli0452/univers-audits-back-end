#!/bin/bash
# Vérifie, avec un vrai nginx et un faux back, ce que les journaux du serveur web gardent d'un visiteur.
#
#   bash tests/journaux-anonymes.sh [chemin/du/modele.template]      (par défaut : nginx/asce.conf.template)
#
# Attendu après le correctif du 2026-11-19 :
#   - un dépôt, un suivi ou un complément citoyen (et l'envoi de pièces) ne laisse AUCUNE ligne de journal d'accès,
#     ni de journal d'erreur (même pour un envoi trop gros, refusé en 413) ;
#   - le reste du site journalise sans l'adresse complète (dernier nombre mis à zéro) et sans code de suivi.
# Prérequis : docker, openssl, curl (Git Bash sous Windows suffit). Rien n'est installé ni modifié ailleurs.
set -uo pipefail
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
cd "$(dirname "$0")/.."
MODELE="${1:-nginx/asce.conf.template}"
IMG="nginx:1.27-alpine"
RES="asce-test-journaux-$$"
T="$(mktemp -d)"
command -v cygpath >/dev/null 2>&1 && T="$(cygpath -m "$T")"   # chemin Windows (openssl et Docker Desktop)
FAIL=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (attendu: $3, obtenu: $2)"; FAIL=$((FAIL+1)); fi; }
nettoyer() { docker rm -f "$RES-proxy" "backend-$$" >/dev/null 2>&1; docker network rm "$RES" >/dev/null 2>&1; rm -rf "$T"; }
trap nettoyer EXIT

mkdir -p "$T/certs" "$T/html"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=test.local" \
  -keyout "$T/certs/privkey.pem" -out "$T/certs/fullchain.pem" >/dev/null 2>&1 || { echo "openssl indisponible"; exit 2; }
echo "<!doctype html><app-root></app-root>" > "$T/html/index.html"
cp "$MODELE" "$T/modele.template"
# Faux back : répond 200 à tout sur 8081 (le nom DNS « backend » est celui que le modèle attend).
cat > "$T/back.conf" <<'EOF'
server { listen 8081; location / { return 200 "ok"; } }
EOF

docker network create "$RES" >/dev/null
docker run -d --name "backend-$$" --network "$RES" --network-alias backend \
  -v "$T/back.conf:/etc/nginx/conf.d/default.conf:ro" "$IMG" >/dev/null
docker run -d --name "$RES-proxy" --network "$RES" -p 18443:443 -e DOMAIN=test.local \
  -v "$T/modele.template:/etc/nginx/templates/default.conf.template:ro" \
  -v "$T/certs:/etc/nginx/certs:ro" -v "$T/html:/usr/share/nginx/html:ro" "$IMG" >/dev/null
sleep 4
docker logs "$RES-proxy" 2>&1 | grep -qE "emerg|\[error\]" && { echo "FAIL  nginx ne démarre pas :"; docker logs "$RES-proxy" 2>&1 | grep -E "emerg|error" | head -3; exit 1; }

C=(curl -sk -m 20 -o /dev/null -H 'User-Agent: Test')
U="https://localhost:18443"
"${C[@]}" "$U/"                                                       # page d'accueil
"${C[@]}" "$U/api/v1/stats/dashboard"                                  # route d'agent
"${C[@]}" -X POST -d '{}' "$U/api/v1/dossiers/public/submit"           # dépôt citoyen
"${C[@]}" "$U/api/v1/dossiers/public/track/CODESECRET1"                # suivi citoyen (code d'accès dans l'adresse)
"${C[@]}" -X POST -d '{}' "$U/api/v1/dossiers/public/complement/CODESECRET2"
"${C[@]}" -X POST -d x "$U/api/v1/attachments/dossier/11111111-1111-1111-1111-111111111111"
head -c 60000000 /dev/zero | "${C[@]}" -X POST --data-binary @- -H 'Content-Type: application/octet-stream' "$U/api/v1/dossiers/public/submit"  # trop gros : 413
head -c 60000000 /dev/zero | "${C[@]}" -X POST --data-binary @- -H 'Content-Type: application/octet-stream' "$U/api/v1/dossiers"                 # trop gros, route d'agent
sleep 1

LOG="$(docker logs "$RES-proxy" 2>&1)"
GW="$(docker network inspect "$RES" -f '{{(index .IPAM.Config 0).Gateway}}')"
n() { echo "$LOG" | grep -c "$1"; }
check "aucune ligne ne cite le code de suivi (CODESECRET)"                    "$(n CODESECRET)" "0"
check "dépôt citoyen : aucune ligne de journal"                              "$(n 'dossiers/public/submit')" "0"
check "envoi de pièces citoyen : aucune ligne de journal"                    "$(n 'attachments/dossier')" "0"
check "aucune ligne d'erreur ne cite le visiteur d'une route citoyenne"      "$(echo "$LOG" | grep 'public/' | grep -c 'client:')" "0"
check "route d'agent : toujours journalisée (utile à la sécurité)"           "$(n 'api/v1/stats/dashboard')" "1"
check "route d'agent : adresse complète absente du journal d'accès"          "$(echo "$LOG" | grep 'api/v1/stats/dashboard' | grep -c -F "$GW")" "0"
check "route d'agent : adresse tronquée (dernier nombre à 0)"                "$(echo "$LOG" | grep 'api/v1/stats/dashboard' | grep -c -E '^[0-9]+\.[0-9]+\.[0-9]+\.0 ')" "1"
echo "---- extrait du journal ----"; echo "$LOG" | grep -vE "^/docker-entrypoint|^\s*$|start worker|using the|nginx/|Configuration complete|Launching|Looking for|Sourcing|Running envsubst|Enabled|Ignoring|10-listen|20-envsubst|30-tune|info: " | cut -c1-200 | head -8
echo; [ "$FAIL" -eq 0 ] && echo "TOUS LES CONTROLES PASSENT" || echo "$FAIL CONTROLE(S) EN ECHEC"
exit "$FAIL"
