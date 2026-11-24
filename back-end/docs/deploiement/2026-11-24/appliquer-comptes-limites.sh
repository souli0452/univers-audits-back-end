#!/bin/bash
# Crée les comptes de base de données limités sur le serveur (voir comptes-limites.sql et 2026-11-24-compte-base-limite.md).
#
#   cd /opt/asce/deploy
#   bash sources/back/back-end/docs/deploiement/2026-11-24/appliquer-comptes-limites.sh
#
# Lit DB_APP_PASSWORD et DB_KEYCLOAK_PASSWORD dans .env (sans charger le reste du fichier) et les transmet à psql par
# l'entrée standard : aucun mot de passe n'apparaît dans une ligne de commande ni dans la liste des processus.
set -euo pipefail
ENV_FILE="${ENV_FILE:-.env}"
CONTENEUR="${CONTENEUR:-asce_postgres}"
BASE="${BASE:-bd_univers_audit}"
SQL="$(cd "$(dirname "$0")" && pwd)/comptes-limites.sql"

val() { grep -m1 "^$1=" "$ENV_FILE" | cut -d= -f2- | tr -d '\r\n' || true; }   # variable absente : valeur vide, pas d'arrêt silencieux
[ -f "$ENV_FILE" ] || { echo "ARRET : $ENV_FILE introuvable (lancer depuis /opt/asce/deploy)"; exit 1; }
PA="$(val DB_APP_PASSWORD)"; PK="$(val DB_KEYCLOAK_PASSWORD)"
[ -n "$PA" ] && [ -n "$PK" ] || { echo "ARRET : renseigner DB_APP_PASSWORD et DB_KEYCLOAK_PASSWORD dans $ENV_FILE"; exit 1; }
[ "$PA" != "$PK" ] || { echo "ARRET : les deux mots de passe doivent être différents"; exit 1; }
case "$PA$PK" in *[!A-Za-z0-9_.-]*) echo "ARRET : mots de passe limités aux lettres, chiffres, _ . - (générer avec : openssl rand -hex 24)"; exit 1 ;; esac
[ "${#PA}" -ge 24 ] && [ "${#PK}" -ge 24 ] || { echo "ARRET : mots de passe trop courts (24 caractères minimum)"; exit 1; }

{ printf '\\set app_password '"'"'%s'"'"'\n\\set keycloak_password '"'"'%s'"'"'\n' "$PA" "$PK"; cat "$SQL"; } \
  | docker exec -i "$CONTENEUR" psql -U postgres -d "$BASE" -v ON_ERROR_STOP=1 -q
echo "OK : comptes asce_app et asce_keycloak en place."
