# Plateforme INTÉGRITÉ+ (ASCE-LC) — état des lieux de sécurité avant demande d'ANO

Date : 24 novembre 2026. Périmètre : portail citoyen, espace agent (`/rpd`), serveur (Spring Boot), Keycloak, PostgreSQL,
serveur web nginx, déploiement sur `rpd`.

## 1. Ce que ce document est, et ce qu'il n'est pas

- C'est un **état des lieux technique** réalisé avec des outils reconnus (OWASP ZAP, relecture du code selon le Top 10 de
  l'OWASP, audit des dépendances, recherche de secrets, essais du serveur web). Il sert à **corriger avant** le test officiel.
- Ce n'est **pas** un audit de l'ANSSI ni d'un prestataire agréé, et il ne garantit pas l'avis de non-objection.
- Les **exigences officielles de l'ANSSI du Burkina Faso pour l'ANO n'ont pas été trouvées en accès public** (seul un projet de
  loi sur la sécurité des systèmes d'information circule). **À obtenir auprès de l'ANSSI** : le formulaire de demande, la
  liste des pièces et le référentiel d'audit. Ce document sera à ajuster à ce référentiel.
- Non encore faits : test d'intrusion actif authentifié, revue de la configuration de production de Keycloak, durcissement du
  système d'exploitation et de Docker sur `rpd`, essai de restauration des sauvegardes.

## 2. Méthode

| Contrôle | Outil | Cible |
|---|---|---|
| Analyse web passive (60 règles) | OWASP ZAP 2.x, scan de base | site en ligne |
| Dépendances de l'écran | `npm audit` | écran |
| Dépendances du serveur (182) | base d'avis GitHub (GHSA) | serveur |
| Secrets oubliés | gitleaks (code et historique) | dépôts écran et serveur |
| Chiffrement | `openssl` | site en ligne |
| Code | relecture OWASP Top 10 (injections, accès, SSRF, aléa, hachages, XSS) | serveur et écran |
| Serveur web | essai nginx jetable (73 contrôles) | modèle de configuration |

## 3. Synthèse

- **ZAP, site en ligne : 0 échec, 8 avertissements, 60 contrôles réussis.** Un avertissement de gravité « High » (version de nginx).
- **Dépendances de l'écran : 13 avis (7 élevés) → 0 élevé** après mise à jour (reste 1 faible sur `quill`).
- **Dépendances du serveur : 56 avis élevés ou critiques**, presque tous dus à Spring Boot 3.3.5 (fin de support). Montée en 3.5.16 préparée.
- **Secrets : 1 clé Africa's Talking réelle dans l'historique git** ; 2 secrets Keycloak/test à comparer avec la production.
- **Chiffrement : bon** (TLS 1.2 et 1.3 seulement, certificat DigiCert valide jusqu'au 08/03/2027).

## 4. Constats

Gravité : C = critique, É = élevé, M = moyen, F = faible, I = information. « Réf. » = catégorie OWASP Top 10 (2021).

| N° | Gr. | Réf. | Constat | Correction | État |
|---|---|---|---|---|---|
| S-01 | C | A06 | Spring Boot 3.3.5 (fin de support) : Tomcat 10.1.31 (24 avis), Spring MVC et Security, Netty, Jackson, PostgreSQL JDBC… | Montée à Spring Boot 3.5.16 | Branche `chore/spring-boot-3-5` : Spring Boot 3.5.16, Tomcat 10.1.62, Netty 4.1.141, Jackson 2.21.8, pilote PostgreSQL 42.7.15 ; 1076 tests |
| S-02 | É | A02 | Clé API Africa's Talking (SMS) **en clair dans l'historique git** (2 commits, plusieurs branches) | **Révoquer la clé chez Africa's Talking et en créer une nouvelle** ; garder seulement la variable d'environnement | **À faire (vous)** |
| S-03 | É | A02 | Secret de client Keycloak dans `test-asce.ps1` (32 caractères aléatoires) et valeur de secret dans `realm-export.json` | Comparer les empreintes avec la production (§6) ; si identiques, changer le secret dans Keycloak et `.env` ; retirer ces valeurs des fichiers | À faire (vous) |
| S-04 | É | A06 | nginx 1.27.5 : version annoncée et signalée avec vulnérabilités connues par ZAP | Image `nginx:stable-alpine` à la reconstruction ; `server_tokens off` | Modèle corrigé et essayé ; **image à changer sur `rpd`** |
| S-05 | M | A06 | Keycloak 25.0.2 : avis élevé sur `keycloak-core`, corrigé en 26.0.6 | Montée de version planifiée et essayée hors production | À planifier |
| S-06 | M | A06 | PostgreSQL 16.15 (bulletin CERT-BF CERTBFA-2026-VUL-167, CVSS 8,8) | Mise à jour en 16.16 | **Fait le 24/11** ; base non exposée (5432 interne) |
| S-07 | M | A05 | L'application se connecte à la base avec le compte administrateur `postgres` | Compte dédié sans droit d'administration (propriétaire du schéma seulement) | À faire (chantier à part) |
| S-08 | M | A09 | Événements Keycloak désactivés (`eventsEnabled=false`) : pas de journal de connexions côté Keycloak | Activer LOGIN, LOGIN_ERROR, LOGOUT, UPDATE_PASSWORD, UPDATE_TOTP, et les événements d'administration | Fichier d'import corrigé et testé (`KeycloakRealmSecuriteTest`) ; **à appliquer au royaume en place** (guide de déploiement) |
| S-09 | M | A07 | Flux « mot de passe direct » (ROPC) activé sur le client public `asce-lc-frontend` : inutile, car l'écran utilise le code d'autorisation avec PKCE | Désactiver sur `asce-lc-frontend` et imposer PKCE S256 (le garder sur `asce-lc-backend`, qui l'utilise) | Fichier d'import corrigé et testé ; **à appliquer au royaume en place** |
| S-10 | M | A05 | CSP : `style-src 'unsafe-inline'` (styles en ligne d'Angular et PrimeNG) ; `frame-ancestors` absent | `frame-ancestors 'self'` ajouté en en-tête ; les styles en ligne ne peuvent pas être retirés sans casser l'interface — risque accepté et documenté | `frame-ancestors` corrigé ; reste accepté |
| S-11 | F | A05 | Adresses `localhost` dans la CSP du site publié | `index.prod.html` sans `localhost`, contrôlé par test | **Corrigé** |
| S-12 | F | A05 | En-têtes absents : Referrer-Policy, Permissions-Policy, COOP, CORP | Ajoutés au modèle nginx (micro permis au site) | Modèle corrigé ; **à déployer** |
| S-13 | F | A05 | COEP absent | Non ajouté : `require-corp` casserait les cadres de Keycloak (connexion). Risque faible, accepté | Accepté |
| S-14 | F | A06 | Dépendances de l'écran : 13 avis (7 élevés) | `npm audit fix` : Angular 20.3.35 et autres | **Corrigé** (0 élevé) |
| S-15 | I | A05 | Le serveur répond 200 avec la page d'accueil sur `/.git/config`, `/.env`, `/actuator/env`, `/backup.sql`… (rien n'est exposé, mais les scanners le signalent) | 404 franc pour ces chemins | Modèle corrigé ; **à déployer** |
| S-16 | I | — | Pas de fichier `security.txt` (RFC 9116) pour signaler une vulnérabilité | Publier `/.well-known/security.txt` avec un contact | À décider (contact) |
| S-17 | I | A07 | Le changement de mot de passe vérifie l'ancien mot de passe par le flux direct ; avec un compte protégé par code à usage unique, ce flux peut échouer | À tester avec un compte réel | À tester |

## 5. Points forts constatés

- API protégée par défaut (`anyRequest().authenticated()`), routes publiques listées une à une ; Swagger coupé en production.
- Requêtes SQL toutes paramétrées ; aucune désérialisation dangereuse, aucun appel de commande système, un seul appel sortant (SMS, adresse fixe).
- Codes de suivi tirés avec `SecureRandom` ; aucun hachage faible ; pièces jointes assainies (métadonnées retirées).
- Pas de jeton conservé dans le navigateur (ni `localStorage` ni `sessionStorage`) ; aucun contournement de l'assainissement Angular pour du HTML.
- Keycloak : verrouillage après 5 échecs, mot de passe de 12 caractères avec historique, code à usage unique obligatoire, inscription fermée.
- Limitation de débit, journal d'audit applicatif, journaux du serveur web sans adresse complète ni code de suivi pour le dépôt citoyen.
- Chiffrement : TLS 1.2 et 1.3 seulement ; HSTS ; sauvegardes quotidiennes de la base ; base non exposée au réseau.

## 6. Comparer les secrets avec la production, sans jamais les afficher

Chaque ligne donne les 8 premiers caractères de l'empreinte SHA-256 de la valeur trouvée dans git. Sur `rpd`, calculez la même
empreinte de la valeur réellement utilisée ; si elle est **identique**, le secret de production est dans git : il faut le changer.

| Valeur dans git | Empreinte | Commande à lancer sur `rpd` (dossier `/opt/asce/deploy`) |
|---|---|---|
| Clé Africa's Talking (ancien commit) | `a60f6c91` | `grep '^AT_API_KEY=' .env \| cut -d= -f2- \| tr -d '\n' \| sha256sum \| cut -c1-8` |
| Secret du client Keycloak (`realm-export.json`) | `e09b8a1b` | `grep '^KEYCLOAK_CLIENT_SECRET=' .env \| cut -d= -f2- \| tr -d '\n' \| sha256sum \| cut -c1-8` |
| Secret de `test-asce.ps1` | `680f13c3` | même commande que la ligne précédente |

## 7. Plan avant le dépôt de la demande

1. Révoquer la clé Africa's Talking (S-02) et vérifier les empreintes (S-03).
2. Déployer le serveur sur Spring Boot 3.5.x une fois les tests passés (S-01), puis le nouveau modèle nginx avec `nginx:stable-alpine` (S-04, S-12, S-15).
3. Régler Keycloak : événements (S-08), flux direct de l'écran (S-09) ; planifier la montée de version (S-05).
4. Créer le compte de base dédié (S-07).
5. Rejouer OWASP ZAP : scan de base, puis scan **actif** de l'API sur un environnement d'essai jetable (jamais sur la production ni sur la base de travail).
6. Préparer les pièces du dossier : schéma d'architecture, flux de données, liste des comptes et des droits, politique de sauvegarde et de restauration (avec un essai de restauration daté), plan de réponse à incident, registre des traitements de données personnelles (loi n°001-2021/AN), cette liste de constats avec les preuves de correction.
