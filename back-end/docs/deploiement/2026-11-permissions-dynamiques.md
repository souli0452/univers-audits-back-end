# Déploiement — permissions pilotées par la base, chefs de département, circuit du rapport

Version préparée le 2026-11-16. Back : branche `feature/permissions-dynamiques` (migrations 028 à 034 ; 035 à 037 si la publication trimestrielle part avec).
Front : branche `feat/menus-metier-seulement`. **Le back et le front partent ensemble.**

Décision d'architecture : `back-end/docs/adr/0001-permissions-dynamiques.md`.

## Ce qui change pour les utilisateurs

| Qui | Avant | Après |
|---|---|---|
| ADMIN_DDIC | tout | tout, y compris l'identité des lanceurs d'alerte protégés (tracée, courriel à la DSI) |
| CGE, CGEA | métier + administration | métier seulement (plus d'agents, rôles, paramètres, audit, file des notifications) |
| CGEA | approuvait l'étape DEI du rapport et validait le plan d'enquête | ne fait plus ni l'un ni l'autre (chef du DEI) ; approuve toujours l'étape CGEA |
| CGE | n'avait pas le registre des auditions | a le registre des auditions |
| Nouveau rôle CHEF_DEI | — | approuve/rejette l'étape DEI du rapport, **valide le plan d'enquête**, registre des auditions, répartit les dossiers de son département |
| Nouveau rôle CHEF_DAC | — | répartit les dossiers de son département |
| Imputation à un département | tous les agents du département prévenus | seul le chef est prévenu et habilité ; il répartit à ses agents |
| Rôles | codés en dur | modifiables dans l'écran Rôles & Permissions, effet immédiat |

## 1. Avant le déploiement (poste de développement)

1. Les tests passent : back `./mvnw -q -o test` (892), front `npx ng test --watch=false --browsers=ChromeHeadless` (193).
2. Pousser les deux branches et ouvrir les demandes de fusion **en « merge commit », jamais en squash, sans jamais supprimer la branche** :
   - back : `feature/permissions-dynamiques` vers `feature/workflow-denociation-asce-fix` ;
   - front : `feat/menus-metier-seulement` vers `dev-front`, puis `git push origin origin/dev-front:refs/heads/amandement_cge`.

## 2. Contrôles avant d'appuyer sur « déployer » (sur `rpd`, base de production)

Se connecter à `rpd` (dossier `/opt/asce/deploy`) et ouvrir `psql` dans le conteneur `asce_postgres` (utilisateur et base : ceux du `.env`).

**a) Fiches déjà imputées à un département.** Après le déploiement, leurs agents perdent l'accès à la fiche ; seul un chef (ou un agent habilité) la retrouve.

```sql
SELECT d.number, dep.code, f.date_imputation
FROM fiche_affectation f
JOIN dossier d ON d.id = f.dossier_id
JOIN departement dep ON dep.id = f.departement_designe_id
WHERE f.type_designation = 'DEPARTEMENT';
```

S'il y en a : prévoir de réaffecter ces dossiers après le déploiement (une fois les chefs en place) ou d'accorder les habilitations à la main.

**b) Rapports en cours de circuit.** Ceux qui attendent l'étape DEI ne pourront plus être approuvés par le CGEA.

```sql
SELECT d.number, i.legal_advisor_approved_at, i.dei_approved_at, i.cgea_approved_at
FROM investigation i JOIN dossier d ON d.id = i.case_id
WHERE i.status = 'COMPLETED';
```

**c) Qui sont le chef du DEI et le chef du DAC, et dans quel département sont-ils rattachés ?**

```sql
SELECT dep.code, a.matricule, a.first_name, a.last_name, a.actif
FROM agent a LEFT JOIN departement dep ON dep.id = a.departement_id
ORDER BY dep.code, a.last_name;
```

Un chef doit être **actif** et **rattaché à son département** (DEI ou DAC) dans sa fiche agent. Corriger avant si besoin.

## 3. Déploiement (commandes à taper une par une)

```
cd /opt/asce/deploy
grep -E "^(BACK|FRONT)_BRANCH" .env
bash scripts/backup.sh
D=$(date +%F-%H%M)
docker tag asce-lc-backend:latest asce-lc-backend:avant-$D
docker tag asce-lc-proxy:latest asce-lc-proxy:avant-$D
bash scripts/deploy.sh
```

- `BACK_BRANCH` doit valoir `feature/workflow-denociation-asce-fix`, `FRONT_BRANCH` doit valoir `amandement_cge`.
- Si la construction échoue sur `Connection reset` vers Maven Central, relancer : c'est une coupure passagère ; la production reste intacte tant que `up -d` n'a pas eu lieu.
- Le thème Keycloak ne change pas : pas de redémarrage de Keycloak nécessaire.

## 4. Après le déploiement

**a) Les migrations ont tourné** (dans `psql`) :

```sql
SELECT id FROM databasechangelog WHERE id >= '028' ORDER BY id;
```

Attendu : 028, 029, 030, 031, 032, 033, 034 (et 035, 036, 037 avec la publication trimestrielle).

```sql
SELECT count(*) FROM permission WHERE active;
SELECT role_key FROM role_definition WHERE active ORDER BY display_order;
```

Attendu : 124 permissions actives ; 10 rôles (ADMIN_DDIC, CGE, CGEA, AGENT_BRPD, CONSEILLER_JURIDIQUE, CONTROLEUR_ETAT, MEMBRE_CTADP, DCP, CHEF_DEI, CHEF_DAC).

**b) Les rôles CHEF_DEI et CHEF_DAC existent dans Keycloak.** Au démarrage, le back les crée seul. Chercher la ligne dans les journaux :

```
docker compose logs backend | grep KeycloakRoleSynchronizer
```

- « … créé(s) dans Keycloak » : c'est bon.
- Une ligne `ERROR` « Synchronisation impossible » : **le droit de créer des rôles pour le compte technique du back n'a jamais été utilisé en production**. Dans ce cas, créer les deux rôles à la main : console Keycloak, royaume `asce-lc`, Realm roles, Create role (`CHEF_DEI`, puis `CHEF_DAC`), puis vérifier ensuite la ligne de log.

**c) Attribuer les rôles** (écran Administration, Agents, modifier l'agent) :

- chef du DEI : rôles **Contrôleur d'État + Chef du DEI** ;
- chef du DAC : rôles **Contrôleur d'État + Chef du DAC**.

Le serveur refuse un chef sans le rôle Contrôleur d'État, et refuse un agent chef des deux départements (message d'erreur à l'enregistrement).

**d) Vérifications par rôle** (se connecter avec un vrai compte de chaque rôle) :

| Rôle | À vérifier |
|---|---|
| ADMIN_DDIC | menu Administration complet ; écran Rôles & Permissions : 10 rôles ; Journal d'audit lisible |
| CGE | pas de groupe Administration ; « Dépassements par acteur » et « Registre des auditions » visibles |
| CGEA | pas de groupe Administration ; peut ouvrir la fenêtre d'affectation (liste des conseillers juridiques avec étiquettes « CJ/… ») |
| Chef du DEI | « Approuver DEI » apparaît seulement quand le juridique a approuvé ; « Valider » le plan d'enquête ; registre visible |
| DCP | uniquement statistiques, profil, notifications |
| AGENT_BRPD | enregistre un dépôt comme avant |

**e) Alerte de sécurité.** Ouvrir un dossier à lanceur d'alerte protégé avec un compte habilité : une ligne `CONSULTER_IDENTITE_LANCEUR_ALERTE` apparaît dans le Journal d'audit et un courriel « [ALERTE INTERNE] » arrive à `dsi@asce-lc.bf` (limité à un par heure pour la même personne et le même dossier). Prévenir la DSI avant ce test. L'adresse se remplace avec la variable `PROTECTED_IDENTITY_ALERT_TO`.

**f) Cas des dossiers déjà imputés** (voir 2a) : réaffecter, ou accorder les habilitations à la main.

## 5. Retour arrière

Les migrations 028 à 033 ne font qu'**ajouter** (permissions, rôles, une valeur de plus autorisée sur l'origine d'une habilitation) ou retirer des droits devenus inutiles ; l'ancien code les ignore. Un retour arrière du code suffit donc :

```
cd /opt/asce/deploy
docker tag asce-lc-backend:avant-<date> asce-lc-backend:latest
docker tag asce-lc-proxy:avant-<date> asce-lc-proxy:latest
docker compose up -d backend proxy
```

En dernier recours seulement (perd les données saisies depuis le déploiement) : restaurer la sauvegarde faite par `scripts/backup.sh` dans `/opt/asce/backups`.

## 6. Points d'attention connus

- Un seul conteneur back tourne : le cache des droits (5 minutes, vidé à chaque modification de rôle) est en mémoire. Avec plusieurs instances, un retrait de droit mettrait jusqu'à 5 minutes à s'appliquer sur les autres.
- Un rôle supprimé depuis l'écran est désactivé : sa clé ne peut plus être recréée.
- La suppression d'un rôle est refusée tant que des agents le portent (comptés parmi les porteurs directs dans Keycloak).
- Le circuit du rapport prévient désormais chaque étape ; un échec de notification est seulement écrit dans les journaux.
- La validation du plan d'enquête est au chef du DEI (migration 034) : les plans déjà soumis et non validés devront être validés par lui. Requête : `SELECT d.number FROM plan_investigation p JOIN investigation i ON i.id = p.investigation_id JOIN dossier d ON d.id = i.case_id WHERE p.validated_at IS NULL;`
- **Publication trimestrielle (migration 035)** : ajoute les natures « plainte-dénonciation » et « demande d'intervention », la plateforme Messenger, et deux colonnes facultatives (origine géographique, typologie institutionnelle). Rien n'est supprimé. Attention au retour arrière : dès qu'un agent a enregistré un dossier avec une des nouvelles valeurs, l'ancien code ne sait plus le lire. Pour revenir en arrière après usage, passer d'abord ces dossiers à `DENONCIATION` ou `PLAINTE`. Les chiffres publiés dépendent des champs renseignés : les dossiers sans valeur sont comptés « non précisé », et les allégations restent « non qualifié » tant que la liste des infractions n'est pas configurée dans Paramètres métier.
- **Référentiel géographique (migration 037)** : crée les tables `geo_region`, `geo_province`, `geo_commune` (17 régions, 47 provinces, 351 communes, d'après la liste du Ministère de l'Administration territoriale et de la Mobilité) et quatre colonnes sur `dossier` (pays, région, province, commune d'origine). Les identifiants sont calculés à partir des noms : identiques dans tous les environnements. La liste est servie sans connexion par `GET /api/v1/public/geographie` (le proxy ne doit pas la bloquer). Les communes à statut particulier (Ouagadougou, Bobo-Dioulasso) ne figurent pas dans les deux listes du Ministère : elles ont été ajoutées. Orthographe : celle des listes officielles (majuscules sans accent) remise en casse normale avec les accents d'usage ; à relire. Une modification du découpage = une nouvelle migration.
