# Déploiement du 2026-11-19 — dépôt citoyen, travail de l'agent, pages d'erreur

Préparé le 2026-11-19. Il vient **après** le déploiement du 2026-11-17 (migrations 028 à 037, déjà en production).

- Back : branche `feature/workflow-denociation-asce-fix`, **après fusion de #15 (séparation des pouvoirs)** — une seule migration nouvelle : 038 (`038-pieces-jointes-telechargement`).
- Front : `dev-front` à `8a9ca43`, à pousser vers `amandement_cge` (qui est encore à `681cd7c`, 11 commits de retard).
- **Le back et le front partent ensemble** (sans le back, le bouton Télécharger n'apparaît pas et « Compléter la fiche » échoue).

Vérifié avant publication : 984 tests du serveur verts ; 331 tests de l'écran et 3 tests de garde verts ; construction de production de l'écran réussie ; aucune migration 028 à 037 modifiée (pas de risque de somme de contrôle Liquibase).

## Ce qui change pour les utilisateurs

| Qui | Avant | Après |
|---|---|---|
| Citoyen | dépôt en un écran dense, lecteur audio figé à 0:00, promesse de SMS | dépôt en 5 écrans, écran « Votre sécurité », protection lue avant d'être demandée, période au calendrier, retour à l'accueil, plus de promesse de SMS, **lecteur audio qui charge** |
| Tous les agents | pièces jointes téléchargeables par tous | on peut toujours les **consulter et les écouter** sur la plateforme ; le **téléchargement** est réservé à l'administrateur, au CGE, au CGEA et au chef du DEI, et **chaque téléchargement est inscrit au journal d'audit** |
| Agent BRPD, CGEA, CGE | seul l'agent BRPD enregistrait | **inchangé : aucune suppléance** — chacun reste responsable des tâches qui lui sont attribuées (décision du 2026-11-19, pour éviter les abus de pouvoir) ; le CGE et le CGEA n'enregistrent ni ne complètent un dossier à la place de l'agent BRPD |
| Agent BRPD (droit de modifier) | — | nouveau bouton « Compléter la fiche » (objet, faits, lieu, période, montant, nature, type d'institution), avec le lecteur audio en tête pour un dépôt vocal ; une note interne nomme les champs complétés, jamais leur contenu |
| Tous | écran muet après un dépôt | phrase « Prochaine étape » sur le dossier |
| Dépôt anonyme ou sous protection | adresse IP conservée | **plus d'adresse IP conservée** (nouveaux dépôts seulement : voir contrôle 2d) |
| Citoyen (invisible) | les journaux du serveur web gardaient l'adresse IP **et le code de suivi** de chaque visite | dépôt, suivi, complément et envoi de pièces **ne laissent aucune trace** dans ces journaux ; le reste du site journalise une adresse tronquée (dernier nombre à 0) |
| Tous | « Se déconnecter » menait à l'accueil public | mène à la page de connexion |
| Tous | pages 404 / accès refusé / erreur du modèle d'origine | pages au logo de l'ASCE-LC ; un agent sans le rôle requis voit « Accès refusé » (403) et non « Page introuvable » |

Le droit de **télécharger** est un droit comme les autres : il se retire ou s'accorde par rôle dans l'écran Rôles & Permissions, effet sous 5 minutes (cache des droits).

## 1. Avant le déploiement (poste de développement)

Déjà fait le 2026-11-19 : tests verts, construction de production réussie, branches fusionnées (back #12 et #13, front #19 à #23). Reste uniquement :

```
git push origin origin/dev-front:refs/heads/amandement_cge
```

(à lancer dans le dossier du front ; vérifier ensuite `git log --oneline -1 origin/amandement_cge` = `8a9ca43`).

Deux fichiers de cette livraison sont versionnés dans `back-end/docs/deploiement/2026-11-19/` et se copient **depuis le dépôt cloné sur `rpd`** (jamais depuis un poste Windows : les fins de ligne casseraient le script) :
- `asce.conf.template` → `/opt/asce/deploy/nginx/asce.conf.template` (remplace l'ancien : journaux sans adresse ni code de suivi) ;
- `journaux-anonymes.sh` → `/opt/asce/deploy/tests/journaux-anonymes.sh` (essai de ces journaux, voir 4c).

**Fusionner d'abord la demande de ce guide** (back #14) : sans cela, le dépôt cloné sur `rpd` ne contient pas ces fichiers.

## 2. Contrôles avant d'appuyer sur « déployer » (sur `rpd`)

Se connecter à `rpd`, dossier `/opt/asce/deploy`.

**a) Les branches lues par le déploiement.**

```
cd /opt/asce/deploy
grep -E "^(BACK|FRONT)_BRANCH" .env
```

Attendu : `BACK_BRANCH=feature/workflow-denociation-asce-fix` et `FRONT_BRANCH=amandement_cge`.

**b) Le mot de passe de la messagerie est renseigné** (sans l'afficher). Sans lui, aucun courriel de notification ne part, mais rien ne le signale :

```
grep -c '^MAIL_PASSWORD=.' .env
```

Attendu : `1`. Si `0` : renseigner `MAIL_PASSWORD` dans `.env` avant de déployer (compte `denoncer.dsida@asce-lc.bf`, serveur `mailer.gov.bf`).

**c) La base est bien à la migration 037** (dans `psql`, conteneur `asce_postgres`) :

```sql
SELECT id FROM databasechangelog WHERE id >= '035' ORDER BY id;
```

Attendu : 035, 036, 037 — et **pas** 038 (elle sera appliquée par le déploiement).

**d) Adresses IP déjà conservées sur des dépôts anonymes ou protégés.** Le correctif ne s'applique qu'aux nouveaux dépôts ; les anciens gardent ce qui a été enregistré. Lecture seule :

```sql
SELECT 'historique' AS source, count(*)
FROM status_history sh
JOIN dossier d ON d.id = sh.case_id
LEFT JOIN declarant dc ON dc.id = d.declarant_id
WHERE sh.ip_address IS NOT NULL AND (d.anonymous OR coalesce(dc.protection_requested, false))
UNION ALL
SELECT 'journal d''audit', count(*)
FROM audit_log a
JOIN dossier d ON a.entity_type = 'DOSSIER' AND a.entity_id = d.id::text
LEFT JOIN declarant dc ON dc.id = d.declarant_id
WHERE a.ip_address IS NOT NULL AND (d.anonymous OR coalesce(dc.protection_requested, false));
```

Si le résultat n'est pas 0, **décision à prendre** : effacer ces adresses (elles n'ont aucun usage métier pour un dépôt anonyme). Ne le faire qu'après accord explicite, et après la sauvegarde de l'étape 3 :

```sql
UPDATE status_history SET ip_address = NULL
WHERE id IN (SELECT sh.id FROM status_history sh
             JOIN dossier d ON d.id = sh.case_id
             LEFT JOIN declarant dc ON dc.id = d.declarant_id
             WHERE sh.ip_address IS NOT NULL AND (d.anonymous OR coalesce(dc.protection_requested, false)));
```

(même principe pour `audit_log` si besoin). Les journaux du serveur web (`nginx`) gardent aussi les adresses : voir « Points d'attention ».

**e) Les rôles existent dans Keycloak.** Les rôles Keycloak sont créés au démarrage par le serveur, mais cette création a déjà échoué en production (droit refusé) : vérifier plutôt que de supposer.

```
docker exec asce_keycloak sh -c '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null && /opt/keycloak/bin/kcadm.sh get roles -r asce-lc --fields name --format csv --noquotes'
```

Attendu, les 10 : ADMIN_DDIC, CGE, CGEA, AGENT_BRPD, CONSEILLER_JURIDIQUE, CONTROLEUR_ETAT, MEMBRE_CTADP, DCP, CHEF_DEI, CHEF_DAC. Si `/auth` est refusé, essayer sans `/auth`. Un rôle manquant se crée dans la console Keycloak (royaume `asce-lc`, Rôles du royaume, Créer).

## 3. Déploiement (commandes à taper une par une)

Avant la première commande, mettre en place les deux fichiers de l'étape 1, puis contrôler que le modèle `nginx` est le nouveau :

```
cd /opt/asce/deploy
( cd sources/back && git fetch -q origin && git reset -q --hard origin/feature/workflow-denociation-asce-fix )
mkdir -p tests
cp sources/back/back-end/docs/deploiement/2026-11-19/asce.conf.template nginx/asce.conf.template
cp sources/back/back-end/docs/deploiement/2026-11-19/journaux-anonymes.sh tests/journaux-anonymes.sh
grep -c 'access_log off' nginx/asce.conf.template
```

Attendu : `1`.

```
cd /opt/asce/deploy
bash scripts/backup.sh
D=$(date +%F-%H%M)
docker tag asce-lc-backend:latest asce-lc-backend:avant-$D
docker tag asce-lc-proxy:latest asce-lc-proxy:avant-$D
bash scripts/deploy.sh
```

- La construction de l'écran dure environ 18 minutes (`npm ci`).
- Si elle échoue sur `Connection reset` vers Maven Central, relancer : coupure passagère ; la production reste intacte tant que `up -d` n'a pas eu lieu.
- Le thème Keycloak ne change pas : pas de redémarrage de Keycloak.
- Le conteneur du serveur web est **recréé** : ses anciens journaux (qui contiennent des adresses IP et des codes de suivi) disparaissent avec lui. Si une trace doit être conservée pour une enquête, la copier avant : `docker logs asce_proxy > /opt/asce/backups/proxy-avant-$D.log` (à protéger ensuite, ce fichier contient ces données).

## 4. Après le déploiement

**a) La migration 038 a tourné** (`psql`) :

```sql
SELECT id FROM databasechangelog WHERE id >= '038';
SELECT r.role_key FROM role_permission rp
JOIN role_definition r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
WHERE p.permission_key = 'PIECE_JOINTE_TELECHARGER' ORDER BY 1;
SELECT count(*) FROM permission WHERE active;
```

Attendu : `038-pieces-jointes-telechargement` ; les rôles ADMIN_DDIC, CGE, CGEA, CHEF_DEI ; **125** permissions actives. **Aucune** ligne `…-et-suppleance` (si elle existe, c'est l'ancienne version de la migration : voir le point 6).

**b) Contrôles automatiques.** `bash scripts/smoke-test.sh` : tous les contrôles passent (le script ne couvre pas cette livraison, d'où les contrôles manuels ci-dessous).

**c) Contrôles propres à cette livraison** (depuis `rpd`, en remplaçant `denoncer.asce-lc.bf` si besoin) :

```
curl -s https://denoncer.asce-lc.bf/ | grep -c "media-src 'self' blob:"
curl -s -o /dev/null -w '%{http_code}\n' https://denoncer.asce-lc.bf/api/v1/attachments/00000000-0000-0000-0000-000000000001/telecharger
curl -s -o /dev/null -w '%{http_code}\n' https://denoncer.asce-lc.bf/api/v1/auth/permissions
```

Attendu : `1`, puis `401`, puis `401`.

**Journaux du serveur web** (le script lance un `nginx` jetable avec un faux back ; il ne touche pas à la production) :

```
cd /opt/asce/deploy
bash tests/journaux-anonymes.sh nginx/asce.conf.template
docker logs --since 10m asce_proxy | tail -5
```

Attendu : « TOUS LES CONTROLES PASSENT » ; dans les journaux réels, des lignes de la forme `41.138.12.0 - [..] "GET /index.html" 200 ..` (dernier nombre à 0), jamais de `/dossiers/public/` ni de code de suivi. Les erreurs `[error] … client: <adresse>` des routes **d'agents** (par exemple un envoi trop gros) gardent l'adresse : c'est voulu, ces adresses sont celles de votre personnel.

**d) Avec de vrais comptes, dans le navigateur** (page de connexion, puis les rôles ci-dessous) :

| Rôle | À vérifier |
|---|---|
| AGENT_BRPD | sur un dossier « Soumis » : boutons « Compléter la fiche » et « Enregistrer » ; onglet Pièces jointes : aperçu et écoute, **pas de bouton Télécharger** ; sur un dépôt vocal, le lecteur audio affiche la durée |
| CGEA | **ni « Enregistrer » ni « Compléter la fiche »** sur un dossier « Soumis » (la phrase « Prochaine étape » dit que l'agent BRPD s'en charge) ; le bouton Télécharger est présent ; un téléchargement apparaît au Journal d'audit (`TELECHARGER_PIECE_JOINTE`) |
| ADMIN_DDIC | Télécharger présent ; Journal d'audit lisible |
| Tout rôle | « Se déconnecter » mène à la page de connexion ; une adresse inconnue (`/#/nimportequoi`) affiche la page 404 au logo |
| Citoyen | dépôt en 5 écrans ; enregistrement vocal : le lecteur affiche une durée avant l'envoi |

**e) Le nom de l'agent dans le journal d'audit.** Il vient du jeton Keycloak : un compte sans prénom ni nom dans Keycloak laisse la colonne vide (l'identifiant de l'agent est toujours enregistré). Renseigner prénom et nom des comptes réels.

## 5. Retour arrière

La migration 038 ne fait qu'**ajouter** un droit et ses attributions. L'ancien code l'ignore ; Liquibase tolère une migration déjà jouée qu'il ne connaît plus. Retour arrière du code seulement :

```
cd /opt/asce/deploy
docker tag asce-lc-backend:avant-<date> asce-lc-backend:latest
docker tag asce-lc-proxy:avant-<date> asce-lc-proxy:latest
docker compose up -d backend proxy
```


En dernier recours seulement (perd les données saisies depuis le déploiement) : restaurer la sauvegarde de `scripts/backup.sh` dans `/opt/asce/backups`.

## 6. Points d'attention connus

- **Pas de suppléance entre rôles (décision du 2026-11-19).** Pour une absence, l'administrateur attribue temporairement le rôle nécessaire dans l'écran Agents, puis le retire ; chaque attribution est inscrite au Journal d'audit (`MODIFIER_ROLES_AGENT`, avec la liste des rôles). Surveiller ces lignes : c'est le seul chemin légitime pour qu'une personne fasse le travail d'une autre.
- **Base de développement ayant déjà joué l'ancienne 038** (elle donnait des droits de saisie au CGE et au CGEA) : à réparer à la main, la production n'est pas concernée (elle n'a jamais joué cette version) :

```sql
DELETE FROM role_permission rp USING role_definition r, permission p
WHERE rp.role_id = r.id AND rp.permission_id = p.id AND r.role_key = 'CGE'
  AND p.permission_key IN ('DOSSIER_ENREGISTRER','DOSSIER_MODIFIER','DOSSIER_MES_DOSSIERS_VOIR','PARTIE_VISEE_GERER','PARTIE_VISEE_SUPPRIMER','TEMOIN_GERER','TEMOIN_SUPPRIMER','PIECE_JOINTE_RECLASSER','PIECE_JOINTE_SUPPRIMER','SECTION_DOSSIER_TRAVAIL_GERER','INFO_PREOCCUPANTE_AUTOSAISIR','INFO_PREOCCUPANTE_CREER','INFO_PREOCCUPANTE_TRAITER','INFO_PREOCCUPANTE_VOIR');
DELETE FROM role_permission rp USING role_definition r, permission p
WHERE rp.role_id = r.id AND rp.permission_id = p.id AND r.role_key = 'CGEA'
  AND p.permission_key IN ('DOSSIER_ENREGISTRER','DOSSIER_MODIFIER','PARTIE_VISEE_GERER','TEMOIN_GERER','PIECE_JOINTE_RECLASSER','SECTION_DOSSIER_TRAVAIL_GERER','INFO_PREOCCUPANTE_AUTOSAISIR','INFO_PREOCCUPANTE_CREER','INFO_PREOCCUPANTE_TRAITER','INFO_PREOCCUPANTE_VOIR');
DELETE FROM databasechangelog WHERE id = '038-pieces-jointes-telechargement-et-suppleance';
```

  (le CGEA garde ses 4 droits d'avant : mes dossiers, suppression d'une partie, d'un témoin, d'une pièce ; supprimer aussi `target/classes/db/changelog/migrations/038-…-et-suppleance.sql` du dossier de compilation).
- **Journaux du serveur web : traité dans cette livraison** (voir 4c). Ce qui reste hors de portée de l'application : l'opérateur de connexion du déposant et l'hébergeur voient toujours qu'une connexion a eu lieu, et l'heure d'un dépôt peut être rapprochée de l'heure d'une connexion si quelqu'un a accès à la fois à la base et aux flux réseau. Conseiller l'usage d'un réseau public ou d'un navigateur de confidentialité à un lanceur d'alerte à risque (l'écran « Votre sécurité » du portail peut le dire).
- **Courriels de notification.** Dépendent de `MAIL_PASSWORD` (2b). Les SMS restent désactivés (`sms.enabled=false`) : l'application n'en promet plus.
- **Un compte sans fiche agent** reçoit « Agent introuvable. Contactez l'administrateur DDIC. » à la première action qui s'inscrit à son nom : créer les agents par l'écran Administration, pas seulement dans Keycloak.
- **Comptes de formation.** Voir `back-end/docs/formation/guide-formation.md` ; ne pas les créer avant d'avoir décidé de la remise à zéro de la base.
