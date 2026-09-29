# Lot 10 — Sécurisation et mise en production : volet documentaire

Ce document couvre les éléments du Lot 10 (`docs/reference/plan-de-travail-asce-lc.md`,
ligne 384-385) qui ne relèvent pas du code applicatif : chiffrement au repos, archivage, plan
de reprise d'activité, tests de sécurité et d'intrusion, formation des utilisateurs par rôle.

Le volet code du même Lot (durcissement Keycloak, cloisonnement réseau Docker, sauvegarde
automatisée Postgres) est livré et documenté séparément dans la mémoire projet ; ce document
s'appuie dessus sans le répéter.

---

## 1. Chiffrement au repos

**État actuel** : aucun chiffrement au repos n'est mis en œuvre par cette base de code. Les
volumes Docker (`asce_postgres_data`, `asce_postgres_backups`, `asce_keycloak_data`,
`asce_redis_data`) stockent leurs données en clair sur le disque de l'hôte, tel que Docker les
écrit par défaut.

**Pourquoi ce n'est pas un sous-chantier de code** : le chiffrement au repos pertinent ici se
situe au niveau du disque/volume physique, pas de l'application — PostgreSQL et Redis
eux-mêmes n'offrent pas de chiffrement transparent de fichier sans une couche externe
(chiffrement de disque du système d'exploitation, ou volume chiffré fourni par
l'hébergeur/le cloud). Rien dans ce dépôt ne peut activer ça : c'est une responsabilité de
l'équipe qui provisionne le serveur de production.

**Exigence à respecter à la mise en production** :
- Le disque (ou le volume cloud) hébergeant `/var/lib/docker/volumes/` doit être chiffré au
  repos avant le premier déploiement — LUKS sous Linux, ou le chiffrement de volume géré par
  le fournisseur cloud (chiffrement de disque managé, activé par défaut chez la plupart des
  fournisseurs modernes mais à vérifier explicitement, pas à supposer).
- Chiffrer un disque déjà en production avec des données dessus nécessite une migration de
  données (recréer le volume chiffré, restaurer les données) — donc à traiter **avant** la
  mise en production, pas après.

**Risque résiduel identifié** : les fichiers de sauvegarde produits par le service
`postgres-backup` (Lot 10, sous-chantier code 3/3) héritent du chiffrement du disque hôte,
mais ne sont pas chiffrés individuellement. Si ces sauvegardes sont un jour copiées hors de
l'hôte (stockage distant, clé USB, etc. — voir §2 Archivage), elles voyageront en clair sauf
ajout d'un chiffrement de fichier (GPG symétrique par exemple) au script
`scripts/backup-postgres.sh`. Non implémenté à ce stade car aucun transfert hors-site n'existe
encore.

---

## 2. Archivage

**Distinction avec la sauvegarde (déjà livrée)** : la sauvegarde (Lot 10 sous-chantier code
3/3) est un filet de secours opérationnel à court terme (rotation de 30 jours, restauration en
cas d'incident technique). L'archivage est une question différente : combien de temps les
dossiers d'enquête **clos** doivent-ils être conservés dans le système avant suppression ou
déplacement vers un stockage froid, pour répondre à une obligation légale/réglementaire de
conservation des dossiers de lutte contre la corruption.

**Ce point ne peut pas être tranché côté code.** Il relève d'un arbitrage métier/juridique
non disponible dans le texte source du plan de travail — au même titre que les points déjà
listés en section 13 du plan de travail ("Points à arbitrer avec le métier"). Aucune durée de
rétention n'est inventée ici.

**Question à poser à l'ASCE-LC avant d'implémenter quoi que ce soit** :
- Quelle est la durée légale de conservation des dossiers clos (enquêtes, PV, pièces
  jointes) ? Existe-t-il un texte burkinabè applicable (loi sur les archives publiques,
  réglementation anti-corruption spécifique) qui fixe cette durée ?
- Les dossiers archivés doivent-ils rester consultables (lecture seule) dans l'application,
  ou peuvent-ils être exportés puis supprimés de la base active ?
- Y a-t-il une distinction de durée entre dossiers classés sans suite et dossiers ayant
  abouti à une décision judiciaire ?

**Une fois la réponse obtenue**, l'implémentation technique (champ de statut "archivé",
export périodique, purge) suivra le processus habituel de ce dépôt (spec → plan →
implémentation) comme un sous-chantier à part entière — pas dans ce document.

---

## 3. Plan de reprise d'activité (PRA)

### 3.1 Objectifs de reprise

- **RPO (Recovery Point Objective — perte de données maximale tolérée)** : 24 heures,
  déterminé par la fréquence de sauvegarde actuelle (dump quotidien, Lot 10 sous-chantier
  code 3/3). Réduire le RPO nécessiterait une sauvegarde plus fréquente ou une réplication en
  continu (WAL streaming) — non implémenté, à envisager si l'ASCE-LC exige un RPO plus court.
- **RTO (Recovery Time Objective — délai maximal d'indisponibilité toléré)** : à confirmer
  avec l'ASCE-LC ; ce document propose une cible de 4 heures ouvrées comme point de départ
  (temps de reconstitution manuelle décrit en 3.3), à ajuster selon la criticité opérationnelle
  réelle du service.

### 3.2 Composants à restaurer, par ordre de dépendance

1. **Hôte / infrastructure Docker** — provisionner une machine avec Docker et Docker Compose,
   restaurer `docker-compose.prod.yml` et le fichier `.env` (gitignoré — doit être conservé
   séparément, par exemple dans un coffre-fort de secrets, jamais dans le dépôt Git).
2. **Volume `asce_postgres_data`** — restaurer depuis la dernière sauvegarde valide
   (`scripts/backup-postgres.sh`, fichiers `.sql.gz` dans le volume `asce_postgres_backups`,
   ou depuis une copie externe si elle existe — voir risque résiduel §1). Procédure :
   ```
   gunzip -c bd_univers_audit_<timestamp>.sql.gz | \
     PGPASSWORD="$DB_PASSWORD" psql -h postgres -U postgres -d bd_univers_audit
   ```
   sur une base fraîchement initialisée (volume vide, conteneur `postgres` démarré une
   première fois pour créer le rôle/la base).
3. **Keycloak** — la base Keycloak vit dans le même Postgres (schéma `keycloak`), donc
   restaurée par la même opération que le point 2. Si le volume `asce_keycloak_data` est
   perdu mais que le Postgres est restauré, Keycloak peut redémarrer sur les données du
   schéma `keycloak` restauré. **Rappel important** (déjà noté au Lot 10 sous-chantier code
   1/3) : `--import-realm` n'importe le realm que sur un Keycloak vierge — sur une
   restauration où le schéma `keycloak` contient déjà les données, l'import au démarrage est
   ignoré, ce qui est le comportement voulu ici (le realm restauré prévaut sur le fichier
   `realm-export.json`).
4. **Redis** — cache uniquement, aucune donnée durable critique (sessions, rate-limiting).
   Redémarrage à vide acceptable, aucune restauration nécessaire.
5. **Backend** — redéployer l'image `asce-lc-backend:latest` une fois Postgres/Keycloak/Redis
   opérationnels ; Liquibase valide le schéma au démarrage (`spring.jpa.hibernate.ddl-auto=
   validate` — échoue explicitement si le schéma restauré ne correspond pas aux migrations
   attendues, ce qui est un garde-fou volontaire plutôt qu'un problème).

### 3.3 Procédure de reprise (résumé opérationnel)

1. Provisionner un nouvel hôte (ou réparer l'hôte existant).
2. Restaurer `.env` depuis le coffre-fort de secrets (jamais depuis Git).
3. `docker compose -f docker-compose.prod.yml up -d postgres` — attendre l'état `healthy`.
4. Restaurer le dump le plus récent (commande §3.2 point 2).
5. `docker compose -f docker-compose.prod.yml up -d` — démarre keycloak, redis, backend,
   postgres-backup.
6. Vérifier `GET /actuator/health` (authentifié, rôle `ADMIN_DDIC` — durci au sous-chantier
   1/3) et une connexion applicative de bout en bout avant de rouvrir l'accès aux
   utilisateurs.

### 3.4 Non résolu par ce document

- **Aucune copie de sauvegarde hors-site** n'existe à ce jour (risque déjà noté §1 et au Lot
  10 sous-chantier code 3/3) : si l'hôte physique est détruit (incendie, vol, panne
  matérielle irrécupérable), les sauvegardes locales sont perdues avec lui. Une copie
  périodique vers un stockage distant (autre site, object storage) est nécessaire pour un PRA
  réellement robuste — hors périmètre de ce qui a été codé, à planifier comme amélioration
  ultérieure une fois l'hébergement de production choisi (le mécanisme de copie dépend du
  fournisseur retenu, inconnu à ce stade).
- **Terminaison TLS non présente dans la stack Docker** : `application-prod.properties`
  déclare `app.api-base-url=https://api.asce-lc.bf`, mais aucun composant de
  `docker-compose.prod.yml` ne termine TLS (pas de certificat, pas de reverse proxy) — la
  stack telle quelle sert du HTTP en clair sur les ports 8080/8081. **Un reverse proxy
  (nginx, Traefik, ou un load balancer géré par l'hébergeur) doit être positionné devant
  cette stack avant toute mise en production réelle**, sans quoi le trafic applicatif
  (identifiants, jetons JWT, données de dossiers confidentiels) circule sans chiffrement dès
  qu'il quitte le réseau interne. Constat fait en rédigeant ce document, non traité comme
  sous-chantier de code car le choix du reverse proxy dépend de l'hébergement retenu
  (information non disponible dans ce dépôt).
- **Adresse du client derrière le reverse proxy (limiteur de débit)** : `RateLimitFilter`
  limite par adresse IP. Le profil `prod` active `server.forward-headers-strategy=native`, ce
  qui fait lire l'adresse réelle du client dans `X-Forwarded-For`, **uniquement si la connexion
  vient d'un proxy interne** (127.x, 10.x, 172.16-31.x, 192.168.x). Le reverse proxy doit donc
  **remplacer** l'en-tête, jamais l'allonger avec ce que le client a envoyé :
  ```nginx
  proxy_set_header X-Forwarded-For $remote_addr;
  proxy_set_header X-Forwarded-Proto $scheme;
  proxy_set_header Host $host;
  ```
  (et non `$proxy_add_x_forwarded_for`, qui conserverait une valeur falsifiée par le client).
  Sans ces lignes côté nginx, tous les visiteurs partagent le même quota.

---

## 4. Tests de sécurité et d'intrusion

**Ce que ce dépôt ne peut pas faire** : un test d'intrusion réel (recherche active de
vulnérabilités exploitables, tentatives d'exploitation) est un exercice mené par une équipe
spécialisée externe, avec un mandat explicite et un périmètre défini — hors de ce qui peut
être produit par ce workflow de développement.

**Ce que ce document fournit à la place** : une liste de points de vigilance dérivés
directement du travail déjà fait dans ce dépôt, à donner à l'équipe/au prestataire qui mènera
le test, pour qu'il concentre son effort sur les zones les plus sensibles plutôt que de partir
de zéro.

### 4.1 Durcissements déjà en place (à valider, pas à redécouvrir)

- Contrôle d'accès à deux niveaux sur tout ce qui touche `Dossier`/`Investigation`
  (habilitation nominative + masquage de confidentialité) — présent dans ~25 endroits du
  code, patron de référence `PlanActionsService`. Un test d'intrusion devrait vérifier
  qu'aucun endpoint listant/consultant des dossiers ne permet de contourner ce double
  contrôle (énumération d'UUID, IDOR).
- Rate limiting sur les endpoints publics anonymes (Bucket4j/Redis, Lot 8).
- Politique de mot de passe et double authentification obligatoire sur Keycloak, anti-bourrage
  d'identifiants à 5 tentatives (Lot 10 sous-chantier code 1/3).
- Cloisonnement réseau Docker (Lot 10 sous-chantier code 2/3) — à tester en tentant une
  connexion directe à `postgres`/`redis` depuis un conteneur qui ne devrait pas y avoir accès.
- `/actuator/health` restreint aux appelants authentifiés avec le rôle `ADMIN_DDIC` pour les
  détails (Lot 10 sous-chantier code 1/3).

### 4.2 Zones jamais auditées par une revue de code, à prioriser pour un test externe

- **Absence de terminaison TLS dans la stack** (voir §3.4) — à vérifier en premier lieu :
  le trafic est-il effectivement chiffré de bout en bout une fois le reverse proxy de
  production en place ?
- **Flux d'authentification Keycloak lui-même** (redirections OAuth2/OIDC, validation de
  jeton côté backend, gestion de session) — jamais testé par une revue de code applicatif
  Java, car la logique vit dans Keycloak, pas dans ce dépôt.
- **Téléversement de pièces jointes** (`AttachmentStorageService`, dépôt public de pièces
  lors d'une soumission citoyenne) — surface exposée à des utilisateurs anonymes non
  authentifiés, candidat naturel pour des tests de téléversement de fichiers malveillants,
  contournement de limite de taille, traversée de chemin.
- **Le dépôt anonyme et le suivi par code** (`/api/v1/dossiers/public/track/**`,
  `/api/v1/dossiers/public/submit`) — endpoints publics par conception, à tester
  spécifiquement pour énumération de codes de suivi et fuite d'information via les messages
  d'erreur.

### 4.3 Recommandation

Engager un test d'intrusion externe qualifié avant la mise en production réelle (pas
seulement un environnement de démonstration), en fournissant ce document comme point de
départ. Renouveler après tout changement structurant touchant l'authentification ou le
contrôle d'accès.

---

## 5. Formation des utilisateurs par rôle

Sept rôles existent dans le système (`keycloak/import/realm-export.json`) :
`ADMIN_DDIC`, `CGE`, `CGEA`, `CONSEILLER_JURIDIQUE`, `CONTROLEUR_ETAT`, `MEMBRE_CTADP`,
`AGENT_BRPD`. Ce qui suit est un point de départ structurel (quels sujets couvrir par rôle),
pas un support de formation complet à produire séparément (supports pédagogiques,
présentations, exercices pratiques — hors périmètre de ce document).

| Rôle | Ce que ce rôle peut faire dans le système (résumé) | Sujets de formation prioritaires |
|---|---|---|
| `AGENT_BRPD` | Réception et enregistrement des saisines (Processus A/B), premiers actes sur un dossier | Circuit de réception, distinction des canaux de saisine, création/rattachement de dossier, **ne jamais voir un dossier confidentiel sans habilitation nominative** |
| `CONSEILLER_JURIDIQUE` | Analyse juridique, qualification pénale, avis avant transmission | Référentiel des types d'infraction/textes juridiques, rédaction d'avis, circuit d'approbation |
| `CONTROLEUR_ETAT` | Conduite des investigations, rédaction de PV, fiches RETEX | Habilitation nominative par dossier, confidentialité, rédaction des fiches RETEX (Lot 9), gestion des pièces de preuve |
| `MEMBRE_CTADP` | Participation aux instances de délibération (CTADP) | Rôle collégial, accès en lecture aux dossiers soumis en instance |
| `CGEA` | Approbation intermédiaire, administration de référentiels (rôle admin partagé avec `ADMIN_DDIC` sur la plupart des référentiels) | Circuit d'approbation complet, administration des référentiels (types d'infraction, indices de fraude, points de checklist) |
| `CGE` | Approbation finale, décisions structurantes, accès large aux dossiers confidentiels | **Un des seuls rôles avec `canSeeConfidential()`** — responsabilité particulière sur la confidentialité, publication des leçons à partager (Lot 9) |
| `ADMIN_DDIC` | Rôle transversal le plus large — administration système, tous les référentiels, accès `/actuator/health` détaillé | Formation technique/administrative complète : gestion des comptes Keycloak, supervision de la plateforme, procédure de restauration (§3 PRA) |

**Point de vigilance transversal à inclure dans toute formation, quel que soit le rôle** :
la confidentialité des dossiers n'est pas une option d'interface — c'est un contrôle
technique appliqué systématiquement (voir §4.1). Les agents doivent comprendre qu'un refus
d'accès affiché par le système n'est pas une erreur à contourner mais une protection légale
attendue.

**CGE/CGEA/ADMIN_DDIC uniquement** : formation spécifique à la double authentification
obligatoire (Lot 10 sous-chantier code 1/3) et à la procédure à suivre en cas de perte de
l'accès à l'application TOTP (procédure de réinitialisation par un administrateur Keycloak —
à documenter séparément par l'équipe d'exploitation, dépend de l'outil d'administration
Keycloak effectivement utilisé en production).

---

## Synthèse : ce qui reste à trancher avant mise en production réelle

1. **Chiffrement de disque** — action d'infrastructure, à faire avant le premier déploiement
   (§1).
2. **Durée légale d'archivage** — arbitrage métier/juridique à obtenir de l'ASCE-LC avant
   toute implémentation (§2).
3. **Reverse proxy / terminaison TLS** — composant manquant dans la stack actuelle, bloquant
   pour une mise en production réelle (§3.4).
4. **Sauvegarde hors-site** — amélioration recommandée du PRA, dépend de l'hébergement choisi
   (§3.4).
5. **Test d'intrusion externe** — à planifier avant mise en production, ce document sert de
   point de départ (§4).
