#!/usr/bin/env bash
# Comptes de formation : un compte par rôle de l'application, avec le même mot de passe de formation.
#
# À lancer sur le serveur (là où tournent les conteneurs asce_keycloak et asce_postgres), jamais ailleurs.
#
#   bash comptes-formation.sh creer       crée (ou remet à jour) les comptes et leur fiche agent
#   bash comptes-formation.sh liste       affiche l'état de chaque compte
#   bash comptes-formation.sh desactiver  bloque tous les comptes de formation (fin de séance)
#   bash comptes-formation.sh activer     les réactive (début de séance)
#   bash comptes-formation.sh supprimer   supprime les comptes et leur fiche agent
#
# Le mot de passe de formation est demandé au clavier : il n'est écrit dans aucun fichier. Pour une
# exécution sans clavier (tests), le placer dans la variable d'environnement FORMATION_MDP.
# Les comptes ont l'adresse <nom>@formation.invalid : aucun e-mail réel ne peut leur être envoyé.
# La double authentification reste obligatoire : à la première connexion, chaque compte demande de
# configurer l'application d'authentification (c'est voulu : ne pas le contourner).

set -euo pipefail

KC="${KC_CONTENEUR:-asce_keycloak}"
PG="${PG_CONTENEUR:-asce_postgres}"
REALM="${REALM:-asce-lc}"
PREFIXE="${PREFIXE:-test}"
DOMAINE_MAIL="formation.invalid"
KCADM="/opt/keycloak/bin/kcadm.sh"

# suffixe | prénom | nom | rôles (séparés par une virgule) | code du département (vide = aucun)
COMPTES=(
  "admin|Test|Administrateur|ADMIN_DDIC|DSI"
  "cge|Test|CGE|CGE|"
  "cgea|Test|CGEA|CGEA|"
  "brpd|Test|Agent BRPD|AGENT_BRPD|"
  "juridique|Test|Conseiller juridique|CONSEILLER_JURIDIQUE|DSRAJ"
  "controleur|Test|Controleur d Etat|CONTROLEUR_ETAT|DEI"
  "ctadp|Test|Membre CTADP|MEMBRE_CTADP|"
  "dcp|Test|Communication|DCP|DCP"
  "chefdei|Test|Chef du DEI|CONTROLEUR_ETAT,CHEF_DEI|DEI"
  "chefdac|Test|Chef du DAC|CONTROLEUR_ETAT,CHEF_DAC|DAC"
)

erreur() { echo "ERREUR : $*" >&2; exit 1; }

command -v docker >/dev/null || erreur "docker est introuvable : lancez ce script sur le serveur."
docker inspect "$KC" >/dev/null 2>&1 || erreur "le conteneur $KC n'existe pas (variable KC_CONTENEUR)."
docker inspect "$PG" >/dev/null 2>&1 || erreur "le conteneur $PG n'existe pas (variable PG_CONTENEUR)."

PGUSER="$(docker exec "$PG" printenv POSTGRES_USER 2>/dev/null || echo postgres)"
PGDB="$(docker exec "$PG" printenv POSTGRES_DB 2>/dev/null || echo bd_univers_audit)"

# ── Keycloak : se connecter en administrateur (identifiants lus dans le conteneur, jamais affichés) ──
connecter_keycloak() {
  local url
  for url in "http://localhost:8080/auth" "http://localhost:8080"; do
    if docker exec "$KC" sh -c "$KCADM config credentials --server $url --realm master \
         --user \"\$KEYCLOAK_ADMIN\" --password \"\$KEYCLOAK_ADMIN_PASSWORD\"" >/dev/null 2>&1; then
      return 0
    fi
  done
  erreur "connexion administrateur Keycloak impossible (le mot de passe a peut-être été changé depuis l'installation)."
}

kc() { docker exec "$KC" "$KCADM" "$@"; }

# Exécute une commande kcadm qui a besoin du mot de passe de formation : il passe par l'entrée standard.
kc_avec_mdp() {
  printf '%s' "$MDP" | docker exec -i "$KC" sh -c "read -r FPW; $KCADM $*"
}

sql() { docker exec -i "$PG" psql -U "$PGUSER" -d "$PGDB" -v ON_ERROR_STOP=1 -q -At "$@"; }

nom_utilisateur() { echo "${PREFIXE}.$1"; }
matricule_de()    { echo "${PREFIXE^^}.${1^^}"; }

id_keycloak() {   # $1 = nom d'utilisateur ; vide si absent
  kc get users -r "$REALM" -q "username=$1" -q exact=true --fields id --format csv --noquotes 2>/dev/null | head -1 | tr -d '\r'
}

demander_mdp() {
  if [[ -n "${FORMATION_MDP:-}" ]]; then MDP="$FORMATION_MDP"; return; fi
  local a b
  read -r -s -p "Mot de passe de formation (commun aux comptes) : " a; echo
  read -r -s -p "Retapez-le : " b; echo
  [[ "$a" == "$b" ]] || erreur "les deux saisies sont différentes."
  [[ ${#a} -ge 8 ]] || erreur "mot de passe trop court (8 caractères au moins)."
  MDP="$a"
}

verifier_roles() {
  local existants ligne suffixe pn nom roles dep r
  existants="$(kc get roles -r "$REALM" --fields name --format csv --noquotes | tr -d '\r')"
  for ligne in "${COMPTES[@]}"; do
    IFS='|' read -r suffixe pn nom roles dep <<<"$ligne"
    IFS=',' read -ra liste <<<"$roles"
    for r in "${liste[@]}"; do
      grep -qx "$r" <<<"$existants" || erreur "le rôle $r n'existe pas dans Keycloak (royaume $REALM). Créez-le d'abord."
    done
  done
}

creer() {
  demander_mdp
  verifier_roles
  local ligne suffixe pn nom roles dep u id mat args
  for ligne in "${COMPTES[@]}"; do
    IFS='|' read -r suffixe pn nom roles dep <<<"$ligne"
    u="$(nom_utilisateur "$suffixe")"; mat="$(matricule_de "$suffixe")"
    id="$(id_keycloak "$u")"
    if [[ -z "$id" ]]; then
      id="$(kc create users -r "$REALM" -s "username=$u" -s enabled=true -s emailVerified=true \
            -s "email=$u@$DOMAINE_MAIL" -s "firstName=$pn" -s "lastName=$nom" -i | tr -d '\r')"
      echo "créé       : $u"
    else
      kc update "users/$id" -r "$REALM" -s enabled=true >/dev/null
      echo "mis à jour : $u"
    fi
    # Relancer le script avec le même mot de passe est sans danger : Keycloak refuse alors de le remettre
    # (historique des derniers mots de passe), ce qui prouve qu'il est déjà le bon.
    if ! sortie="$(kc_avec_mdp "set-password -r $REALM --username $u --new-password \"\$FPW\"" 2>&1)"; then
      if grep -q "invalidPasswordHistoryMessage" <<<"$sortie"; then
        echo "             (mot de passe déjà à jour)"
      else
        erreur "mot de passe refusé pour $u : $sortie"
      fi
    fi
    args=""
    IFS=',' read -ra liste <<<"$roles"
    for r in "${liste[@]}"; do args+=" --rolename $r"; done
    kc add-roles -r "$REALM" --uusername "$u" ${args# } >/dev/null

    sql <<SQL
INSERT INTO agent (id, matricule, first_name, last_name, email, keycloak_id, actif, departement_id, version, created_at, created_by_id)
VALUES (gen_random_uuid(), '$mat', '$pn', '$nom', '$u@$DOMAINE_MAIL', '$id', true,
        $( [[ -n "$dep" ]] && echo "(SELECT id FROM departement WHERE code = '$dep')" || echo "NULL" ), 0, now(), 'formation')
ON CONFLICT (matricule) DO UPDATE
   SET keycloak_id = EXCLUDED.keycloak_id, actif = true, departement_id = EXCLUDED.departement_id,
       email = EXCLUDED.email, updated_at = now();
SQL
  done
  echo
  liste_comptes
  cat <<TXT

Terminé. Pour chaque compte, à la première connexion :
  - identifiant : <nom ci-dessus> ; mot de passe : celui que vous venez de saisir ;
  - Keycloak demande de configurer l'authentification à deux facteurs (code à 6 chiffres) : le faire tout de
    suite, vous-même, pour chaque compte. Tant que ce n'est pas fait, c'est la première personne qui se
    connecte qui l'associe à son téléphone.
  - Une même application d'authentification peut enregistrer plusieurs comptes.
En fin de séance : bash comptes-formation.sh desactiver
TXT
}

liste_comptes() {
  printf '%-18s %-9s %-34s %s\n' "IDENTIFIANT" "ACTIF" "RÔLES" "FICHE AGENT"
  local ligne suffixe pn nom roles dep u id actif rolesreels fiche mat
  for ligne in "${COMPTES[@]}"; do
    IFS='|' read -r suffixe pn nom roles dep <<<"$ligne"
    u="$(nom_utilisateur "$suffixe")"; mat="$(matricule_de "$suffixe")"
    id="$(id_keycloak "$u")"
    if [[ -z "$id" ]]; then
      printf '%-18s %-9s %-34s %s\n' "$u" "absent" "-" "-"; continue
    fi
    actif="$(kc get "users/$id" -r "$REALM" --fields enabled --format csv --noquotes | tr -d '\r')"
    rolesreels="$(kc get "users/$id/role-mappings/realm" -r "$REALM" --fields name --format csv --noquotes | tr -d '\r' | grep -v '^default-roles\|^offline_access\|^uma_authorization' | paste -sd, -)"
    fiche="$(sql -c "SELECT CASE WHEN actif THEN 'oui' ELSE 'inactive' END || coalesce(' (' || (SELECT code FROM departement d WHERE d.id = a.departement_id) || ')','') FROM agent a WHERE matricule = '$mat'")"
    printf '%-18s %-9s %-34s %s\n' "$u" "$actif" "$rolesreels" "${fiche:-absente}"
  done
}

changer_etat() {   # $1 = true|false
  local ligne suffixe pn nom roles dep u id mat
  for ligne in "${COMPTES[@]}"; do
    IFS='|' read -r suffixe pn nom roles dep <<<"$ligne"
    u="$(nom_utilisateur "$suffixe")"; mat="$(matricule_de "$suffixe")"
    id="$(id_keycloak "$u")"
    [[ -n "$id" ]] || continue
    kc update "users/$id" -r "$REALM" -s "enabled=$1" >/dev/null
    sql -c "UPDATE agent SET actif = $1 WHERE matricule = '$mat'" >/dev/null
  done
  echo "Comptes de formation : enabled=$1"
  liste_comptes
}

supprimer() {
  local ligne suffixe pn nom roles dep u id mat
  read -r -p "Supprimer les comptes de formation (préfixe « $PREFIXE. ») ? Tapez OUI : " rep
  [[ "$rep" == "OUI" ]] || erreur "abandon."
  for ligne in "${COMPTES[@]}"; do
    IFS='|' read -r suffixe pn nom roles dep <<<"$ligne"
    u="$(nom_utilisateur "$suffixe")"; mat="$(matricule_de "$suffixe")"
    id="$(id_keycloak "$u")"
    [[ -n "$id" ]] && kc delete "users/$id" -r "$REALM" >/dev/null && echo "supprimé (Keycloak) : $u"
    if ! sql -c "DELETE FROM agent WHERE matricule = '$mat'" >/dev/null 2>&1; then
      echo "ATTENTION : la fiche agent $mat est liée à des dossiers et n'a pas pu être supprimée (elle est désactivée)." >&2
      sql -c "UPDATE agent SET actif = false WHERE matricule = '$mat'" >/dev/null
    fi
  done
}

case "${1:-aide}" in
  creer)       connecter_keycloak; creer ;;
  liste)       connecter_keycloak; liste_comptes ;;
  desactiver)  connecter_keycloak; changer_etat false ;;
  activer)     connecter_keycloak; changer_etat true ;;
  supprimer)   connecter_keycloak; supprimer ;;
  *)           sed -n '2,17p' "$0" ;;
esac
