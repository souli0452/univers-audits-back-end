# Comptes de base de données limités — à lancer depuis `rpd` (point S-07 de l'audit du 2026-11-24)

## Pourquoi

L'application et Keycloak se connectent aujourd'hui à PostgreSQL avec **`postgres`, le compte administrateur**. Une faille dans l'un ou l'autre
donnerait le contrôle de toute la base : lecture des données des dossiers **et** des comptes de Keycloak, effacement, et même
commandes sur la machine qui héberge la base. Avec un compte limité, une faille ne donne accès qu'à ce que ce service doit toucher.

| Compte | Pour qui | Peut | Ne peut pas |
|---|---|---|---|
| `asce_app` | l'application | tout sur les tables de l'application (lire, écrire, migrer) | voir le schéma `keycloak`, lancer une commande système, lire un fichier, créer un compte ou une base |
| `asce_keycloak` | Keycloak | tout dans le schéma `keycloak` | voir les tables de l'application, ni les mêmes interdits |
| `postgres` | sauvegardes, administration | tout | — (plus utilisé par l'application ni par Keycloak) |

**Rien n'est déplacé ni modifié dans les données** : seul le propriétaire des tables change. Cette livraison est **indépendante** de celle du
durcissement (nginx, Spring Boot) : elle ne change aucune image, seulement des identifiants. Compter une dizaine de minutes ; Keycloak est
indisponible environ une minute (étape 8).

Essai automatique sur une base jetable avec les vraies migrations : `comptes-limites.sh` (27 contrôles). La suite complète des tests du serveur a
aussi été jouée avec `asce_app` comme compte de connexion.

## 1. Sauvegarde et mise à jour des sources

```
cd /opt/asce/deploy
bash scripts/backup.sh
( cd sources/back && git fetch -q origin && git reset -q --hard origin/feature/workflow-denociation-asce-fix )
ls sources/back/back-end/docs/deploiement/2026-11-24/ | grep comptes
```

Attendu : `appliquer-comptes-limites.sh`, `comptes-limites.sh`, `comptes-limites.sql`.

## 2. Essai sur une base jetable (recommandé, ne touche pas à la production)

```
bash sources/back/back-end/docs/deploiement/2026-11-24/comptes-limites.sh
```

Attendu : « TOUS LES CONTROLES PASSENT » (la première fois, Docker télécharge l'image `liquibase/liquibase:4.31`). Sinon **s'arrêter**.

## 3. Générer les deux mots de passe (dans `.env`, jamais à l'écran)

```
cp .env .env.avant-comptes-limites
[ -n "$(tail -c1 .env)" ] && echo >> .env
printf 'DB_APP_PASSWORD=%s\nDB_KEYCLOAK_PASSWORD=%s\n' "$(openssl rand -hex 24)" "$(openssl rand -hex 24)" >> .env
grep -c '^DB_APP_PASSWORD=.' .env
grep -c '^DB_KEYCLOAK_PASSWORD=.' .env
```

Attendu : `1` puis `1` (si `2`, la variable existait déjà : en garder une seule). Ne jamais coller ces mots de passe dans un message.

## 4. Créer les comptes

```
bash sources/back/back-end/docs/deploiement/2026-11-24/appliquer-comptes-limites.sh
```

Attendu : un tableau `0 | 0` (aucun objet resté à `postgres`) puis `OK : comptes asce_app et asce_keycloak en place.`
Le script peut être relancé sans danger. **À ce stade rien ne change pour les utilisateurs** : l'application et Keycloak utilisent encore `postgres`.

```
docker exec asce_postgres psql -U postgres -d bd_univers_audit -c "\du asce_*"
```

Attendu : deux lignes, **sans** « Superuser », « Create role » ni « Create DB ».

## 5. Vérifier que le fichier `docker-compose.yml` contient bien les lignes à modifier

```
cd /opt/asce/deploy
grep -cxF '      DB_PASSWORD: ${DB_PASSWORD}' docker-compose.yml
grep -cxF '      KC_DB_USERNAME: postgres' docker-compose.yml
grep -cxF '      KC_DB_PASSWORD: ${DB_PASSWORD}' docker-compose.yml
```

Attendu : `1`, `1`, `1`. Si l'un d'eux donne autre chose, **s'arrêter** et m'envoyer le résultat de `grep -n "DB_" docker-compose.yml` (les noms seulement, sans les valeurs).

## 6. Basculer l'application

```
cp docker-compose.yml docker-compose.yml.avant-comptes-limites
sed -i 's|^      DB_PASSWORD: ${DB_PASSWORD}$|&\n      SPRING_DATASOURCE_USERNAME: asce_app\n      SPRING_DATASOURCE_PASSWORD: ${DB_APP_PASSWORD}|' docker-compose.yml
grep -nE "SPRING_DATASOURCE_(USERNAME|PASSWORD)" docker-compose.yml
```

Attendu : deux lignes, `asce_app` et `${DB_APP_PASSWORD}` (le nom de la variable, pas sa valeur).

```
docker compose up -d backend
```

Attendre environ 90 secondes, puis :

```
docker compose logs --tail=80 backend | grep -iE "Started|FATAL|password authentication|permission denied|ERROR" | head
docker compose exec -T proxy wget -qO- http://backend:8081/actuator/health
```

Attendu : une ligne « Started … », aucune ligne `FATAL` / `permission denied`, et `{"status":"UP"…}`.

```
bash scripts/smoke-test.sh
```

Attendu : tous les contrôles passent. **En cas d'échec : retour arrière (étape 10)**.

## 7. Contrôler qui est connecté

```
docker exec asce_postgres psql -U postgres -d bd_univers_audit -c "SELECT usename, count(*) FROM pg_stat_activity WHERE datname='bd_univers_audit' GROUP BY 1 ORDER BY 1"
```

Attendu : `asce_app` (plusieurs connexions) ; `postgres` seulement pour Keycloak (et cette commande) tant que l'étape 8 n'est pas faite.

## 8. Basculer Keycloak (la connexion des agents est coupée environ une minute)

```
sed -i 's|^      KC_DB_USERNAME: postgres$|      KC_DB_USERNAME: asce_keycloak|; s|^      KC_DB_PASSWORD: ${DB_PASSWORD}$|      KC_DB_PASSWORD: ${DB_KEYCLOAK_PASSWORD}|' docker-compose.yml
grep -nE "KC_DB_(USERNAME|PASSWORD)" docker-compose.yml
```

Attendu : `asce_keycloak` et `${DB_KEYCLOAK_PASSWORD}`.

```
docker compose up -d keycloak
```

Attendre environ 90 secondes, puis :

```
docker compose logs --tail=60 keycloak | grep -iE "started in|ERROR|FATAL|permission denied" | head
bash scripts/smoke-test.sh
```

Attendu : une ligne « Keycloak … started in … », aucune erreur, tous les contrôles passent.

**Dans le navigateur** : se déconnecter, se reconnecter sur `/#/rpd` avec le code à usage unique, ouvrir un dossier. Vérifier aussi qu'un **dépôt citoyen** fonctionne.

## 9. Contrôle final

```
docker exec asce_postgres psql -U postgres -d bd_univers_audit -c "SELECT usename, count(*) FROM pg_stat_activity WHERE datname='bd_univers_audit' GROUP BY 1 ORDER BY 1"
```

Attendu : `asce_app`, `asce_keycloak`, et `postgres` **seulement** pour cette commande (et la sauvegarde quotidienne, quand elle tourne).

## 10. Retour arrière

Les comptes créés restent sans effet ; `postgres` continue de tout lire. On remet simplement l'ancien fichier :

```
cd /opt/asce/deploy
cp docker-compose.yml.avant-comptes-limites docker-compose.yml
docker compose up -d backend keycloak
```

## Plus tard

- **Mise à jour de Keycloak** (25 → 26) : `asce_keycloak` possède son schéma, donc les migrations de Keycloak passent sans changement.
- **Nouvelles migrations de l'application** : elles passent avec `asce_app` (propriétaire des tables), comme éprouvé dans l'essai.
- **Restauration d'une sauvegarde** : à faire avec `postgres`, puis relancer `appliquer-comptes-limites.sh` pour redonner la propriété des tables restaurées.
