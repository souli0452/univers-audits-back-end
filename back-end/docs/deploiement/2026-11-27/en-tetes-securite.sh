#!/bin/bash
# Vérifie, avec un vrai nginx et un faux back, le durcissement du serveur web demandé par l'audit (OWASP ZAP du 2026-11-24).
#
#   bash tests/en-tetes-securite.sh [chemin/du/modele.template]      (par défaut : nginx/asce.conf.template)
#
# Attendu :
#   - la version de nginx n'est pas annoncée (« Server: nginx », sans numéro) ;
#   - toutes les réponses de l'application portent les en-têtes de sécurité (HSTS avec sous-domaines, nosniff, cadre
#     interdit à d'autres sites, politique de référent, permissions du navigateur, isolation des fenêtres/ressources) ;
#   - les chemins qu'un scanner essaie (.git, .env, actuator, swagger, sauvegardes, fichiers inexistants) répondent 404,
#     et non la page d'accueil avec un code 200 ;
#   - les adresses de l'application (sans extension) continuent de répondre 200 (page d'accueil) ;
#   - une méthode autre que GET ou HEAD sur une page statique est refusée (405), l'API reste joignable.
# Prérequis : docker, openssl, curl (Git Bash sous Windows suffit). Rien n'est installé ni modifié ailleurs.
set -uo pipefail
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
cd "$(dirname "$0")/.."
MODELE="${1:-nginx/asce.conf.template}"
IMG="${NGINX_IMAGE:-nginx:stable-alpine}"
RES="asce-test-entetes-$$"
T="$(mktemp -d)"
command -v cygpath >/dev/null 2>&1 && T="$(cygpath -m "$T")"
FAIL=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (attendu: $3, obtenu: $2)"; FAIL=$((FAIL+1)); fi; }
nettoyer() { docker rm -f "$RES-proxy" "backend-$$" >/dev/null 2>&1; docker network rm "$RES" >/dev/null 2>&1; rm -rf "$T"; }
trap nettoyer EXIT

mkdir -p "$T/certs" "$T/html/assets"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=test.local" \
  -keyout "$T/certs/privkey.pem" -out "$T/certs/fullchain.pem" >/dev/null 2>&1 || { echo "openssl indisponible"; exit 2; }
echo "<!doctype html><app-root></app-root>" > "$T/html/index.html"
echo "x" > "$T/html/assets/logo.png"
echo "console.log(1)" > "$T/html/main-ABCDEFG1.js"
cp "$MODELE" "$T/modele.template"
cat > "$T/back.conf" <<'EOF'
server { listen 8081; location / { return 200 "ok"; } }
server {
  listen 8080;
  location /auth/essai-cookies {
    add_header Set-Cookie "AUTH_SESSION_ID_LEGACY=abc;Path=/auth/;HttpOnly" always;
    add_header Set-Cookie "KEYCLOAK_IDENTITY_LEGACY=def;Path=/auth/;HttpOnly" always;
    add_header Set-Cookie "AUTH_SESSION_ID=ghi;Path=/auth/;Secure;HttpOnly;SameSite=None" always;
    return 200 "cookies";
  }
  location / { return 200 "keycloak"; }
}
EOF

docker network create "$RES" >/dev/null
# Un seul faux serveur répond sous les deux noms que le modèle attend : « backend » (8081) et « keycloak » (8080).
docker run -d --name "backend-$$" --network "$RES" --network-alias backend --network-alias keycloak \
  -v "$T/back.conf:/etc/nginx/conf.d/default.conf:ro" "$IMG" >/dev/null
docker run -d --name "$RES-proxy" --network "$RES" -p 18444:443 -e DOMAIN=test.local \
  -v "$T/modele.template:/etc/nginx/templates/default.conf.template:ro" \
  -v "$T/certs:/etc/nginx/certs:ro" -v "$T/html:/usr/share/nginx/html:ro" "$IMG" >/dev/null
sleep 4
docker logs "$RES-proxy" 2>&1 | grep -qE "emerg" && { echo "FAIL  nginx ne démarre pas :"; docker logs "$RES-proxy" 2>&1 | grep -E "emerg"; exit 1; }

U="https://localhost:18444"
code() { curl -sk -m 20 -o /dev/null -w '%{http_code}' "$@"; }
entetes() { curl -skI -m 20 "$1" | tr -d '\r'; }
a() { echo "$1" | grep -ic "^$2:" ; }                      # présence d'un en-tête
v() { echo "$1" | grep -i "^$2:" | head -1 | cut -d: -f2- | sed 's/^ *//'; }   # sa valeur

echo "== version de nginx =="
H="$(entetes "$U/")"
check "l'en-tête Server n'annonce pas de numéro de version" "$(v "$H" Server | grep -c '/')" "0"

echo "== en-têtes de sécurité sur chaque type de réponse =="
for chemin in "/" "/index.html" "/assets/logo.png" "/main-ABCDEFG1.js" "/portail/page-sans-extension"; do
  H="$(entetes "$U$chemin")"
  check "$chemin : HSTS avec sous-domaines"              "$(v "$H" Strict-Transport-Security | grep -c 'includeSubDomains')" "1"
  check "$chemin : X-Content-Type-Options nosniff"       "$(v "$H" X-Content-Type-Options)" "nosniff"
  check "$chemin : cadre limité au site (X-Frame-Options)" "$(v "$H" X-Frame-Options)" "SAMEORIGIN"
  check "$chemin : Referrer-Policy no-referrer"          "$(v "$H" Referrer-Policy)" "no-referrer"
  check "$chemin : Permissions-Policy limite caméra et micro" "$(v "$H" Permissions-Policy | grep -c 'camera=()')" "1"
  check "$chemin : le micro reste permis au site (message vocal)" "$(v "$H" Permissions-Policy | grep -c 'microphone=(self)')" "1"
  check "$chemin : Cross-Origin-Opener-Policy same-origin" "$(v "$H" Cross-Origin-Opener-Policy)" "same-origin"
  check "$chemin : Cross-Origin-Resource-Policy same-origin" "$(v "$H" Cross-Origin-Resource-Policy)" "same-origin"
  check "$chemin : CSP interdit l'intégration par d'autres sites (frame-ancestors)" "$(v "$H" Content-Security-Policy | grep -c "frame-ancestors 'self'")" "1"
done

echo "== chemins qu'un scanner essaie =="
for chemin in /.git/config /.env /.well-known/../.env /actuator/env /actuator/health /swagger-ui/index.html /v3/api-docs /api-docs /backup.sql /dump.sql.gz /config.json /x.js /old/app.bak /wp-login.php /server-status; do
  check "$chemin répond 404 (et non 200 avec la page d'accueil)" "$(code "$U$chemin")" "404"
done

echo "== les adresses de l'application restent servies =="
for chemin in / /index.html /rpd /portail/faq /portail/info/confidentialite; do
  check "$chemin répond 200" "$(code "$U$chemin")" "200"
done
check "un fichier réel du site est servi (/assets/logo.png)"      "$(code "$U/assets/logo.png")" "200"
check "un fichier à empreinte est servi (/main-ABCDEFG1.js)"       "$(code "$U/main-ABCDEFG1.js")" "200"
check "un fichier à empreinte inexistant répond 404"               "$(code "$U/main-ZZZZZZZZ.js")" "404"

echo "== méthodes et API =="
check "POST sur une page statique est refusé (405)"                "$(code -X POST -d x "$U/")" "405"
check "TRACE n'est pas accepté (jamais 200)"                       "$([ "$(code -X TRACE "$U/")" = 200 ] && echo 200 || echo refusé)" "refusé"
check "l'API reste joignable (faux back)"                          "$(code "$U/api/v1/stats/dashboard")" "200"
echo "== Keycloak (connexion) reste joignable, y compris les chemins à point et à extension =="
for chemin in /auth/realms/asce-lc/.well-known/openid-configuration /auth/realms/asce-lc/protocol/openid-connect/certs /auth/resources/abc/login/asce-lc/css/login.css /auth/resources/abc/common/keycloak/web_modules/x.js; do
  check "$chemin atteint Keycloak (200)" "$(code "$U$chemin")" "200"
done
check "le dépôt citoyen reste joignable (faux back)"               "$(code -X POST -d '{}' "$U/api/v1/dossiers/public/submit")" "200"

echo "== réponses de l'API (constat du scan du site complet, 2026-11-25) =="
for chemin in /api/v1/stats/public /api/v1/public/portal-config /api/v1/dossiers/public/track/ABC /api/v1/attachments/dossier/ABC; do
  H="$(curl -sk -m 20 -D - -o /dev/null "$U$chemin" | tr -d '')"
  check "$chemin : Referrer-Policy no-referrer"          "$(v "$H" Referrer-Policy)" "no-referrer"
  check "$chemin : Permissions-Policy"                   "$(a "$H" Permissions-Policy)" "1"
  check "$chemin : Cross-Origin-Resource-Policy same-origin" "$(v "$H" Cross-Origin-Resource-Policy)" "same-origin"
  check "$chemin : CSP qui n'autorise rien"              "$(v "$H" Content-Security-Policy | grep -c "default-src 'none'; frame-ancestors 'none'")" "1"
done

echo "== fichiers que les robots demandent =="
for chemin in /robots.txt /sitemap.xml; do
  H="$(curl -sk -m 20 -D - -o /dev/null "$U$chemin" | tr -d '')"
  check "$chemin répond 404"                             "$(code "$U$chemin")" "404"
  check "$chemin : CSP qui n'autorise rien"              "$(v "$H" Content-Security-Policy | grep -c "default-src 'none'")" "1"
  check "$chemin : HSTS"                                 "$(a "$H" Strict-Transport-Security)" "1"
  check "$chemin : CSP sans directive laissée ouverte"   "$(v "$H" Content-Security-Policy | grep "form-action 'none'" | grep -c "base-uri 'none'")" "1"
done

echo "== pages et fichiers de Keycloak =="
for chemin in /auth/realms/asce-lc/protocol/openid-connect/auth /auth/resources/abc/login/asce-lc/css/login.css; do
  H="$(curl -sk -m 20 -D - -o /dev/null "$U$chemin" | tr -d '')"
  check "$chemin : Permissions-Policy"                   "$(v "$H" Permissions-Policy | grep -c 'camera=()')" "1"
  check "$chemin : Cross-Origin-Resource-Policy same-origin" "$(v "$H" Cross-Origin-Resource-Policy)" "same-origin"
done

echo "== constats du scan du 2026-11-26 =="
for chemin in /robots.txt /sitemap.xml; do
  H="$(curl -sk -m 20 -D - -o /dev/null "$U$chemin" | tr -d '\r')"
  check "$chemin : Permissions-Policy"                   "$(a "$H" Permissions-Policy)" "1"
done
H="$(curl -sk -m 20 -D - -o /dev/null "$U/auth/realms/asce-lc/protocol/openid-connect/auth" | tr -d '\r')"
check "page de connexion : Cross-Origin-Opener-Policy" "$(v "$H" Cross-Origin-Opener-Policy)" "same-origin-allow-popups"
tls12() { echo | openssl s_client -connect localhost:18444 -tls1_2 -cipher "$1" >/dev/null 2>&1 && echo accepte || echo refuse; }
check "TLS 1.2 : chiffrement moderne (GCM) accepté"      "$(tls12 ECDHE-RSA-AES128-GCM-SHA256)" "accepte"
check "TLS 1.2 : chiffrement CBC refusé"                 "$(tls12 ECDHE-RSA-AES128-SHA256)" "refuse"
check "TLS 1.2 : chiffrement sans confidentialité persistante refusé" "$(tls12 AES128-GCM-SHA256)" "refuse"
check "TLS 1.3 accepté" "$(echo | openssl s_client -connect localhost:18444 -tls1_3 >/dev/null 2>&1 && echo accepte || echo refuse)" "accepte"

echo "== cookies de Keycloak : tous réservés à HTTPS =="
H="$(curl -sk -m 20 -D - -o /dev/null "$U/auth/essai-cookies" | tr -d '' | grep -i '^set-cookie:')"
check "trois cookies reçus"                              "$(echo "$H" | grep -c .)" "3"
check "aucun cookie sans Secure"                         "$(echo "$H" | grep -vic '; *secure')" "0"
check "HttpOnly conservé"                                "$(echo "$H" | grep -ic 'httponly')" "3"

echo; [ "$FAIL" -eq 0 ] && echo "TOUS LES CONTROLES PASSENT" || echo "$FAIL CONTROLE(S) EN ECHEC"
exit "$FAIL"
