# Formation : comptes de test, dossiers fictifs, remise à zéro

Préparé le 2026-11-17. Ces outils servent à former les agents sur la production, puis à repartir d'une base vierge.

| Outil | Rôle | Où le lancer |
|---|---|---|
| `scripts/formation/comptes-formation.sh` | crée un compte de test par rôle, avec un mot de passe de formation commun | sur le serveur |
| `scripts/formation/deposer_dossiers_formation.py` | dépose des dossiers fictifs par la page publique | depuis n'importe quel poste qui joint le site |
| Restauration d'une sauvegarde | remet la base dans l'état « avant la formation » | sur le serveur |

## 1. Avant de commencer

### a) Le rôle DCP existe-t-il dans Keycloak ?

Le script de comptes le vérifie et s'arrête avec un message clair s'il manque. Pour le créer : console Keycloak (`https://denoncer.asce-lc.bf/auth/admin/master/console/`, compte `admin`), royaume `asce-lc`, Realm roles, Create role, nom `DCP`. Les rôles `CHEF_DEI` et `CHEF_DAC` sont déjà créés.

### b) Keycloak range-t-il ses données dans la même base que l'application ?

Sur le serveur :

```
docker exec asce_keycloak printenv KC_DB_URL KC_DB_SCHEMA
```

Si l'adresse se termine par `/bd_univers_audit`, la sauvegarde de l'application contient aussi les comptes : la remise à zéro (partie 5) remet **tout** à l'état de départ, comptes compris. Sinon, il faudra aussi supprimer les comptes de test à la main (`comptes-formation.sh supprimer`).

### c) Paramètres à relire avant de créer les comptes

Aucun paramètre n'est vide : les migrations les ont déjà remplis. Il faut les **relire** et corriger ce qui ne convient pas à l'ASCE-LC.

| Où dans l'application | Contenu actuel | À vérifier |
|---|---|---|
| Administration, **Paramètres métier** | 21 délais (accusé de réception 7 jours ouvrables ; revue du rapport par le conseiller juridique 10 ; analyse du rapport par le DEI 15 ; approbation du CGEA 10 ; approbation du CGE 20 ; durée par défaut d'une investigation 90 ; validation du plan d'investigation par le DEI 8 ; etc.) | que chaque délai est celui du manuel des procédures |
| Administration, **Paramètres métier** | 16 types d'infraction (les 15 du tableau publié sur le site, plus « Défaut ou fausse déclaration d'intérêt ou de patrimoine ») | l'orthographe et les infractions à retenir |
| Administration, **Paramètres du portail** | adresse « Ouagadougou, Burkina Faso » ; e-mail `contact@asce-lc.bf` ; numéro vert 80 00 11 02 ; site `https://www.asce-lc.bf` ; devise ; logos | les coordonnées affichées au public |
| Administration, **Paramètres du portail** | 42 textes de notifications (objets et contenus) (récépissé, accusé de réception, alertes de délais, escalades, protection du lanceur d'alerte) | **relire chaque texte** : en réel, ils partent aux citoyens et aux agents |
| Départements | DAC, DCP, DDIP, DEI, DRH, DSI, DSRAJ | que la liste est complète (les chefs sont rattachés à DEI et DAC) |
| Décision en attente | le taux de traitement public est masqué sous 20 dossiers | confirmer le seuil de 20 |
| Alerte de sécurité | e-mail envoyé à `dsi@asce-lc.bf` quand une identité protégée est consultée | prévenir la DSI avant la formation |

## 2. Prendre l'état de départ

Quand les paramètres sont relus et que **les vrais comptes** (ceux qui resteront après la formation) sont en place, et **avant** d'ajouter la moindre donnée de test :

```
cd /opt/asce/deploy
bash scripts/backup.sh
ls -lt /opt/asce/backups | head -2
```

Notez le nom du fichier créé : c'est l'**état de départ**, celui qu'on restaurera à la fin.

## 3. Créer les comptes de test

Récupérer les scripts sur le serveur (sans redéployer) :

```
cd /opt/asce/deploy/sources/back
git fetch origin
git reset --hard origin/feature/workflow-denociation-asce-fix
cd back-end/scripts/formation
```

Créer les comptes (le mot de passe de formation est demandé deux fois, au clavier) :

```
bash comptes-formation.sh creer
```

Résultat : dix comptes, un par rôle. Le nom de connexion est le préfixe `test.` suivi du suffixe ci-dessous.

| Identifiant | Rôle(s) | Département | Ce qu'on y montre |
|---|---|---|---|
| `test.admin` | ADMIN_DDIC | DSI | tout : agents, rôles, paramètres, journal d'audit |
| `test.cge` | CGE | | imputation, décisions, registre des auditions |
| `test.cgea` | CGEA | | affectation des dossiers, approbation du rapport |
| `test.brpd` | AGENT_BRPD | | enregistrement des dépôts, accusés de réception |
| `test.juridique` | CONSEILLER_JURIDIQUE | DSRAJ | étude d'opportunité, revue du rapport |
| `test.controleur` | CONTROLEUR_ETAT | DEI | enquêtes, rapports |
| `test.ctadp` | MEMBRE_CTADP | | séances du comité |
| `test.dcp` | DCP | DCP | statistiques et chiffres à publier |
| `test.chefdei` | CONTROLEUR_ETAT + CHEF_DEI | DEI | validation du plan d'enquête, approbation DEI, répartition |
| `test.chefdac` | CONTROLEUR_ETAT + CHEF_DAC | DAC | répartition des dossiers du département |

Les adresses e-mail de ces comptes sont en `@formation.invalid` : aucun message réel ne peut leur être envoyé.

**Double authentification.** Elle reste obligatoire (décision à conserver). À la première connexion, chaque compte demande de configurer l'application d'authentification (code à 6 chiffres). Faites-le **vous-même, juste après la création des comptes** : tant que ce n'est pas fait, la première personne qui connaît le mot de passe et se connecte l'associe à son téléphone. Une même application d'authentification peut enregistrer plusieurs comptes.

Autres commandes :

```
bash comptes-formation.sh liste        # état de chaque compte
bash comptes-formation.sh desactiver   # bloque tous les comptes de test (fin de séance)
bash comptes-formation.sh activer      # les rouvre (début de séance)
bash comptes-formation.sh supprimer    # les supprime
```

Pour ne pas laisser un compte puissant (`test.admin`) ouvert hors des séances, utilisez `desactiver` à la fin de chaque séance et `activer` au début de la suivante.

## 4. Déposer des dossiers fictifs

Depuis un poste qui joint le site (Python 3 suffit, aucune installation) :

```
cd back-end/scripts/formation
python3 deposer_dossiers_formation.py --nombre 20
```

- Chaque dossier est marqué `[FORMATION]` dans son objet, avec une fausse identité (ou anonyme, 1 sur 5) et une origine géographique réaliste (Kadiogo, Guiriko, autres régions, quelques dépôts depuis l'étranger).
- Le serveur limite les dépôts à 5 par tranche de 10 minutes et par adresse : le script attend tout seul. Comptez une demi-heure pour 20 dossiers. Il peut être interrompu avec Ctrl+C.
- À la fin, il écrit `ajustements-formation.sql`. Ce fichier donne à une partie des dossiers la nature (plainte-dénonciation, demande d'intervention), le canal (écrit, courrier, mail, téléphone, WhatsApp, Messenger…) et le type d'institution que seul un agent choisit, pour que les tableaux publiés aient de quoi s'afficher. Appliquez-le sur le serveur :

```
docker exec -i asce_postgres psql -U <utilisateur> -d <base> < ajustements-formation.sql
```

(`<utilisateur>` et `<base>` : ceux du fichier `.env`, par défaut `postgres` et `bd_univers_audit`.)

Les dossiers restent « Soumis » : les stagiaires les traitent à la main avec leur compte (accusé de réception, étude d'opportunité, séance, investigation…).

Pour voir ce que le script ferait sans rien envoyer : `python3 deposer_dossiers_formation.py --nombre 10 --a-blanc`.

## 5. Précautions pendant la période de test

- **Le site public affiche des chiffres issus de la base** : les dossiers de test y apparaîtront (le taux de traitement reste masqué sous 20 dossiers). À faire avant l'annonce publique du site, ou à accepter.
- **N'utilisez jamais de vraies identités** dans les dossiers de test : adresses `@example.com` ou `@formation.invalid`, numéros fictifs.
- **Pièces jointes** : les fichiers téléversés pendant la formation restent sur le disque du serveur (volume des téléversements du conteneur `asce_backend`) : à vider à la fin.
- **Alerte de sécurité** : consulter l'identité d'un lanceur d'alerte protégé envoie un e-mail à la DSI.

## 6. Remise à zéro après la formation

Prérequis : l'état de départ de la partie 2, et la vérification 1.b (Keycloak dans la même base).

```
cd /opt/asce/deploy
docker compose stop proxy backend keycloak
docker exec asce_postgres psql -U postgres -d postgres -c "DROP DATABASE bd_univers_audit WITH (FORCE);"
docker exec asce_postgres psql -U postgres -d postgres -c "CREATE DATABASE bd_univers_audit;"
gunzip -c /opt/asce/backups/<fichier-etat-de-depart>.sql.gz | docker exec -i asce_postgres psql -U postgres -d bd_univers_audit -q
docker compose start keycloak backend proxy
```

Ensuite :

1. Attendre une minute, puis relancer le test de fumée : `bash scripts/smoke-test.sh`.
2. Vider les pièces jointes de test (volume des téléversements).
3. Se connecter avec un vrai compte : la base ne contient plus ni dossier de test, ni compte de test.

La procédure a été essayée sur une copie de la base locale (aucune erreur, mêmes nombres de lignes, comptes Keycloak compris). Si vous utilisez un autre nom d'utilisateur de base que `postgres`, adaptez l'option `-U`.
