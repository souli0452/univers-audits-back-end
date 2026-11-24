# Déploiement du 2026-11-24 — durcissement de sécurité (préparation de l'ANO)

À lancer **depuis le réseau local** (accès à `rpd`), dans le dossier `/opt/asce/deploy`. Rapport d'audit : `back-end/docs/securite/ANO-etat-des-lieux-2026-11-24.md`.

## Ce qui change

| Élément | Changement | Visible pour les utilisateurs ? |
|---|---|---|
| Serveur | Spring Boot 3.3.5 → 3.5.16, Tomcat 10.1.62, Netty 4.1.141, Jackson 2.21.8, pilote PostgreSQL 42.7.15 | Non |
| Écran | dépendances à jour (Angular 20.3.35…), politique de sécurité sans `localhost`, boutons « Écrire » et « Parler » avant le motif (PR #45, jamais déployée) | Oui, légèrement (ordre des boutons au dépôt) |
| nginx | image `nginx:1.30-alpine` (au lieu de `1.27-alpine`), version non annoncée, en-têtes de sécurité, 404 francs sur `/.env`, `/.git`, `/actuator`, `/swagger-ui`, fichiers inexistants à extension, méthodes autres que GET/HEAD refusées sur les pages statiques | Non |
| Keycloak | journal des connexions, PKCE imposé et flux « mot de passe direct » coupé sur `asce-lc-frontend` : **à régler dans la console** (le fichier d'import ne modifie pas un royaume existant) | Non |
| SMS | clé Africa's Talking retirée ; `AT_API_KEY` devient facultative | Non (SMS coupés) |

**Aucune migration de base.** Back : branche `feature/workflow-denociation-asce-fix` à `9a38a9d`. Front : `amandement_cge` à `ef93818` (déjà poussée).

## 1. Contrôles avant (lecture seule)

```
cd /opt/asce/deploy
grep -E "^(BACK|FRONT)_BRANCH" .env
```

Attendu : `BACK_BRANCH=feature/workflow-denociation-asce-fix` et `FRONT_BRANCH=amandement_cge`.

```
grep -n "^FROM" build/proxy.Dockerfile
```

Attendu : `FROM node:22-alpine AS build` puis `FROM nginx:1.27-alpine` (ou déjà `1.30`).

**Le secret du client Keycloak est-il celui qui est dans git ?** N'affiche que 8 caractères d'empreinte :

```
grep '^KEYCLOAK_CLIENT_SECRET=' .env | cut -d= -f2- | tr -d '\n' | sha256sum | cut -c1-8
```

Si le résultat est `e09b8a1b` ou `680f13c3`, le secret de production est dans git : le changer (console Keycloak → client `asce-lc-backend` → Credentials → Regenerate, puis `KEYCLOAK_CLIENT_SECRET` dans `.env`, puis `docker compose up -d backend`). Sinon, rien à faire.

## 2. Mise en place (avant `deploy.sh`)

```
cd /opt/asce/deploy
( cd sources/back && git fetch -q origin && git reset -q --hard origin/feature/workflow-denociation-asce-fix )
cp sources/back/back-end/docs/deploiement/2026-11-24/asce.conf.template nginx/asce.conf.template
mkdir -p tests
cp sources/back/back-end/docs/deploiement/2026-11-24/en-tetes-securite.sh tests/en-tetes-securite.sh
grep -c 'server_tokens off' nginx/asce.conf.template
```

Attendu : `2`. Garder une copie de l'ancien modèle avant de le remplacer si on veut revenir en arrière : `cp nginx/asce.conf.template nginx/asce.conf.template.avant-2026-11-24` (à faire **avant** le `cp` ci-dessus).

Changer la version de nginx de l'image :

```
cp build/proxy.Dockerfile build/proxy.Dockerfile.avant-2026-11-24
sed -i 's#^FROM nginx:1.27-alpine#FROM nginx:1.30-alpine#' build/proxy.Dockerfile
grep -n "^FROM nginx" build/proxy.Dockerfile
```

Attendu : `FROM nginx:1.30-alpine`.

Essayer le nouveau modèle avec un nginx jetable et un faux back (ne touche pas à la production ; utilise le port 18444) :

```
bash tests/en-tetes-securite.sh nginx/asce.conf.template
```

Attendu : « TOUS LES CONTROLES PASSENT » (77 contrôles). Sinon **ne pas déployer**.

## 3. Déploiement

```
cd /opt/asce/deploy
bash scripts/backup.sh
D=$(date +%F-%H%M)
docker tag asce-lc-backend:latest asce-lc-backend:avant-$D
docker tag asce-lc-proxy:latest asce-lc-proxy:avant-$D
bash scripts/deploy.sh
```

- La construction de l'écran dure environ 18 minutes. Si elle échoue sur `Connection reset` vers Maven Central, relancer : la production reste intacte tant que `up -d` n'a pas eu lieu (`docker compose build backend` seul puis `deploy.sh` ont déjà réglé le cas).
- Le conteneur nginx est **recréé** : ses anciens journaux disparaissent avec lui.
- Le thème Keycloak ne change pas : **pas de redémarrage de Keycloak** pour cette livraison.

## 4. Contrôles après

```
bash scripts/smoke-test.sh
```

Attendu : tous les contrôles passent.

En-têtes et chemins fermés (le domaine est `denoncer.asce-lc.bf`) :

```
curl -sI https://denoncer.asce-lc.bf/ | grep -iE "^(server|strict-transport|referrer-policy|permissions-policy|cross-origin|content-security)"
```

Attendu : `Server: nginx` **sans numéro**, HSTS avec `includeSubDomains`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, `Cross-Origin-Opener-Policy`, `Cross-Origin-Resource-Policy` et une `Content-Security-Policy` avec `frame-ancestors 'self'`.

```
for c in /.env /.git/config /actuator/env /swagger-ui/index.html /backup.sql; do echo "$c $(curl -s -o /dev/null -w '%{http_code}' https://denoncer.asce-lc.bf$c)"; done
```

Attendu : `404` partout.

```
curl -s https://denoncer.asce-lc.bf/ | grep -c "localhost"
```

Attendu : `0` (la politique de sécurité de la page n'a plus de `localhost`).

```
docker exec asce_proxy nginx -v
```

Attendu : `nginx/1.30.x`.

Dans le navigateur : ouvrir `https://denoncer.asce-lc.bf/`, vérifier l'accueil, le dépôt (boutons « Écrire mon récit » et « Parler dans ma langue » avant le motif), puis **se connecter** avec un compte de test sur `/#/rpd` (BRPD) et ouvrir un dossier.

## 5. Console Keycloak (royaume `asce-lc`) — à faire **après** les contrôles ci-dessus

1. **Realm settings → Events → User events settings** : activer **Save events**, durée de conservation **180 jours** ; types : LOGIN, LOGIN_ERROR, LOGOUT, UPDATE_PASSWORD, UPDATE_TOTP, RESET_PASSWORD, USER_DISABLED_BY_TEMPORARY_LOCKOUT (et les erreurs associées). Enregistrer.
2. **Realm settings → Events → Admin events settings** : activer **Save events**, laisser **Include representation** désactivé (le détail peut contenir un mot de passe saisi). Enregistrer.
3. **Clients → `asce-lc-frontend` → Settings → Capability config** : décocher **Direct access grants**. Enregistrer. (L'écran utilise le code d'autorisation avec PKCE ; le serveur utilise son propre client `asce-lc-backend`, qui n'est pas modifié.)
4. **Clients → `asce-lc-frontend` → Advanced → Advanced settings** : **Proof Key for Code Exchange Code Challenge Method** = `S256`. Enregistrer.

Vérifier ensuite : se déconnecter, se reconnecter sur `/#/rpd` avec le code à usage unique, ouvrir un dossier. Puis dans **Events**, voir la ligne `LOGIN`.

## 6. Retour arrière

Aucune migration : on remet simplement les anciennes images et l'ancien modèle.

```
cd /opt/asce/deploy
docker tag asce-lc-backend:avant-<date> asce-lc-backend:latest
docker tag asce-lc-proxy:avant-<date> asce-lc-proxy:latest
cp nginx/asce.conf.template.avant-2026-11-24 nginx/asce.conf.template
cp build/proxy.Dockerfile.avant-2026-11-24 build/proxy.Dockerfile
docker compose up -d backend proxy
```

Console Keycloak : si une connexion échoue après l'étape 3 ou 4, recocher **Direct access grants** et/ou remettre PKCE à « vide » sur `asce-lc-frontend`.
