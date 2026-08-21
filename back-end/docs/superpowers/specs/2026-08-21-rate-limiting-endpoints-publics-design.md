# Rate-limiting des endpoints publics — Design

## Statut

**Lot 8 — Portail externe**, redécoupé après découverte majeure : le cœur fonctionnel du
Lot 8 (dépôt anonyme, code de suivi, consultation publique, téléversement de pièces a
posteriori, notification de clôture) **existe déjà entièrement en production**, non
documenté avant cette exploration. Seul gap technique réel retenu comme sous-chantier :
protéger les endpoints publics contre l'abus (énumération, spam, scraping).

## Contexte

Exploration exhaustive de l'existant (`SecurityConfig.java:41-59`, `DossierController`,
`AttachmentController`, `AccessCodeGenerator`, `DossierAccessGuard`,
`NotificationDispatcherService`) avant toute conception, même prudence qu'aux Lots 6 et 7 :

- **`POST /api/v1/dossiers/public/submit`** : dépôt anonyme déjà fonctionnel, sans
  authentification, appelle le même `DossierService.submit()` que le flux interne.
- **`GET /api/v1/dossiers/public/track/{accessCode}`** : consultation publique déjà
  fonctionnelle, code de suivi 8 caractères (`AccessCodeGenerator`, alphabet sans
  ambiguïté, `SecureRandom`), réponse déjà masquée (`enrichAndMaskDetail`) pour ne pas
  fuiter les données d'instruction interne.
- **`POST /api/v1/attachments/dossier/**`** : un déclarant anonyme peut déjà revenir
  déposer des pièces (dossiers `SOUMIS`/`EN_ATTENTE_COMPLEMENT`) en présentant son
  `accessCode`, contrôlé par `DossierAccessGuard.checkAttachmentUploadAccess()`.
- **`GET /api/v1/stats/public`** : déjà public (`StatistiqueController.getPublicStats()`).
- Notification de clôture déjà générique (email/SMS, indépendant de l'authentification).
- **Aucune protection anti-abus n'existe nulle part dans le dépôt** — recherche exhaustive
  (`RateLimit`, `Bucket4j`, `throttl*`) : zéro résultat dans `src/` et `pom.xml`. Le
  `GET /public/track/{accessCode}` est énumérable par force brute sur un espace de
  8 caractères (33 valeurs possibles par position ≈ 1,4×10¹² combinaisons — un
  rate-limit ne rend pas l'énumération impossible mais la rend impraticable en pratique).
- « Restitution des supports de la stratégie de communication D.3 » (site web, SMS,
  téléphone vert, publications) : confirmé hors périmètre technique — aucun frontend dans
  ce dépôt (uniquement `src/`, `docs/`, `pom.xml`, `docker-compose*.yml`, `keycloak/`),
  c'est du contenu éditorial pour un portail citoyen qui vit ailleurs.

## Objectif

Ajouter un filtre servlet de rate-limiting sur les 4 endpoints publics identifiés,
adossé à Redis (déjà utilisé par l'application pour le cache — `spring-boot-starter-data-redis`,
client Lettuce) pour un comportement correct en déploiement multi-instance.

## Hors périmètre

- Le reste du Lot 8 (déjà livré, voir Contexte) — ce sous-chantier n'y touche pas.
- Les routes publiques de référence statique (`/config/enums`, `/config/types-declarant`,
  `/public/images/**`, actuator, swagger) — aucun vecteur d'abus réaliste, YAGNI.
- CAPTCHA ou toute protection applicative supplémentaire (hors périmètre, le texte source
  ne le demande pas, le rate-limiting seul répond au risque identifié).
- Modification de `SecurityConfig` — les routes restent `permitAll` (le filtre est un
  mécanisme orthogonal à l'authentification, pas un remplacement).

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Librairie | Bucket4j 8.19.0 (`bucket4j_jdk17-redis-common` + `bucket4j_jdk17-lettuce`) | Algorithme token-bucket (gère les rafales proprement, contrairement à une fenêtre fixe), backend Redis distribué natif, version courante verifiee (2026-05-19) |
| Backend de stockage | Redis, connexion Lettuce dédiée (`RedisClient`/`StatefulRedisConnection<String, byte[]>` construite depuis les memes proprietes `spring.data.redis.host`/`.port`/`.password` que Spring utilise déjà pour le cache) | Réutilise l'infrastructure Redis déjà en place, source unique de configuration de connexion — pas de nouvelle propriété dupliquée |
| Emplacement du filtre | Nouveau `RateLimitFilter` (`@Component extends OncePerRequestFilter`, `@Order(Ordered.HIGHEST_PRECEDENCE)`) | Même patron que `LoginAuditFilter` déjà dans le dépôt (`security/`) ; ordre le plus haut pour rejeter les abus avant tout traitement coûteux (authentification incluse) |
| Résolution IP client | Logique `X-Forwarded-For` dupliquée depuis `DossierController.getClientIp` | Un filtre servlet ne peut pas appeler une méthode privée de contrôleur ; la logique est petite (4 lignes) et déjà éprouvée en production |
| Découplage testabilité | Interface `RateLimiter` (métier) + `Bucket4jRedisRateLimiter` (implémentation Redis) | `RateLimitFilter` se teste par mock de `RateLimiter`, sans dépendre d'un vrai Redis — cohérent avec la convention du dépôt (aucun test n'utilise de Redis réel) |
| Format de réponse en cas de dépassement | Réutilise `GlobalExceptionHandler.ErrorResponse` existant (`status`, `code`, `message`, `timestamp`, `path`) | Cohérence du contrat d'erreur API — un consommateur frontend qui gère déjà ce format n'a pas de cas spécial à ajouter pour 429 |
| Limites par route | Config dédiée `rate-limit.*` dans `application.properties`, 4 routes nommées en dur (`submit`, `track`, `attachmentUpload`, `statsPublic`) | 4 routes fixes connues à l'avance — une infrastructure de config générique par liste serait sur-ingénierie pour ce nombre |
| Désactivation | `rate-limit.enabled=false` disponible (défaut `true`) | Permet de désactiver en environnement de dev/test sans Redis disponible, sans toucher au code |

### Limites proposées (configurables, valeurs par défaut)

| Route | Méthode | Capacité | Fenêtre de réapprovisionnement |
|---|---|---|---|
| `/api/v1/dossiers/public/submit` | POST | 5 | 10 minutes |
| `/api/v1/dossiers/public/track/{code}` | GET | 20 | 1 minute |
| `/api/v1/attachments/dossier/**` | POST | 10 | 10 minutes |
| `/api/v1/stats/public` | GET | 60 | 1 minute |

Toutes par IP cliente (clé Redis `rl:{route}:{ip}`).

## Composants

### 1. Dépendances Maven

Fichier : `pom.xml`

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j_jdk17-redis-common</artifactId>
    <version>8.19.0</version>
</dependency>
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j_jdk17-lettuce</artifactId>
    <version>8.19.0</version>
</dependency>
```

Aucune dépendance Lettuce supplémentaire nécessaire — `io.lettuce:lettuce-core` est déjà
apporté transitivement par `spring-boot-starter-data-redis`.

### 2. `RateLimitProperties` (nouveau)

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/shared/config/RateLimitProperties.java`

`@ConfigurationProperties(prefix = "rate-limit")`, 4 sous-objets `Rule` (capacité +
minutes de réapprovisionnement) + `enabled` (boolean, défaut `true`).

### 3. `RedisRateLimitConfig` (nouveau)

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/shared/config/RedisRateLimitConfig.java`

`@Configuration`, expose un bean `LettuceBasedProxyManager<String>` construit depuis un
`RedisClient` dédié (mêmes `@Value("${spring.data.redis.host}")` /
`${spring.data.redis.port}` / `${spring.data.redis.password}` que le reste de
l'application), connecté via `RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)`
pour obtenir un `StatefulRedisConnection<String, byte[]>`.

### 4. `RateLimiter` (interface, nouveau) + `Bucket4jRedisRateLimiter` (implémentation, nouveau)

Fichiers :
- `src/main/java/gov/bf/ascelc/univers_audits/shared/ratelimit/RateLimiter.java`
- `src/main/java/gov/bf/ascelc/univers_audits/shared/ratelimit/Bucket4jRedisRateLimiter.java`

```java
public interface RateLimiter {
    RateLimitResult tryConsume(String key, int capacity, Duration refillPeriod);

    record RateLimitResult(boolean allowed, long retryAfterSeconds) {}
}
```

`Bucket4jRedisRateLimiter implements RateLimiter` (`@Component`) : dépend du
`LettuceBasedProxyManager<String>` (Composant 3), construit une `BucketConfiguration` par
appel (`capacity(capacity).refillGreedy(capacity, refillPeriod)`), récupère le bucket via
`proxyManager.getProxy(key, () -> configuration)`, consomme via
`tryConsumeAndReturnRemaining(1)`, mappe le résultat vers `RateLimitResult`.

### 5. `RateLimitFilter` (nouveau)

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/security/RateLimitFilter.java`

`@Component extends OncePerRequestFilter`, `@Order(Ordered.HIGHEST_PRECEDENCE)`. Dépend de
`RateLimiter`, `RateLimitProperties`, `ObjectMapper` (pour sérialiser
`GlobalExceptionHandler.ErrorResponse`). Table de 4 règles fixes (méthode + patron Ant +
nom de route + capacité/fenêtre issus de `RateLimitProperties`). Pour chaque requête : si
`rate-limit.enabled=false` ou aucune règle ne correspond, laisse passer immédiatement ;
sinon résout l'IP cliente, appelle `rateLimiter.tryConsume(...)` ; si refusé, écrit
`HTTP 429`, header `Retry-After`, corps JSON `ErrorResponse` (`code = "RATE_LIMITED"`) et
n'appelle PAS `filterChain.doFilter()` ; sinon laisse passer normalement.

### 6. Propriétés par défaut

Fichier : `src/main/resources/application.properties`

```properties
rate-limit.enabled=true
rate-limit.submit.capacity=5
rate-limit.submit.refill-minutes=10
rate-limit.track.capacity=20
rate-limit.track.refill-minutes=1
rate-limit.attachment-upload.capacity=10
rate-limit.attachment-upload.refill-minutes=10
rate-limit.stats-public.capacity=60
rate-limit.stats-public.refill-minutes=1
```

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| Limite dépassée | `HTTP 429`, `Retry-After` (secondes), corps `ErrorResponse(status=429, code="RATE_LIMITED", message="Trop de requêtes, veuillez réessayer plus tard.")` |
| Redis indisponible au moment de la requête | Le client Lettuce lève une exception de connexion — **fail-open** : le filtre attrape cette exception spécifique, logue en `warn`, laisse passer la requête (ne jamais bloquer le portail public à cause d'une panne Redis, qui n'est pas critique pour la disponibilité du service) |
| `rate-limit.enabled=false` | Filtre inactif, aucun appel à Redis |
| Route non couverte par une des 4 règles | Laisse passer sans vérification |

## Tests

- **`RateLimitFilterTest`** (nouveau) : Mockito (`RateLimiter` mocké, `HttpServletRequest`/
  `HttpServletResponse`/`FilterChain` mockés) :
  - `doFilter_laisseSurPasserSiLimiteNonAtteinte`
  - `doFilter_bloqueAvec429SiLimiteAtteinte`
  - `doFilter_ecritRetryAfterEtCorpsErreur`
  - `doFilter_laisseSurPasserRouteNonCouverte`
  - `doFilter_laisseSurPasserSiDesactive`
  - `doFilter_failOpenSiRedisIndisponible`
  - `doFilter_appliqueLaBonneRoutePourChaqueMethodeEtPatron` (4 sous-cas, un par route)
- Pas de test pour `Bucket4jRedisRateLimiter` (wrapper fin autour d'une bibliothèque tierce
  déjà testée, nécessiterait un vrai Redis — cohérent avec l'absence de tout test
  d'infrastructure Redis ailleurs dans ce dépôt).
- Pas de test de contrôleur (convention du dépôt).

## Risques et points d'attention pour le plan d'implémentation

- **`RateLimitFilter` doit dépendre de l'interface `RateLimiter`, jamais directement de
  `Bucket4jRedisRateLimiter` ou de types Bucket4j/Lettuce** — c'est ce qui rend le filtre
  testable sans Redis réel.
- **Le comportement fail-open sur panne Redis n'est pas optionnel** — un portail public de
  dénonciation ne doit jamais devenir indisponible à cause d'une panne d'un composant
  d'infrastructure secondaire (le rate-limiting est une protection, pas une fonctionnalité
  cœur).
- **La clé Redis doit inclure le nom de route, pas seulement l'IP** — sinon un client qui
  épuise son quota sur `/track` bloquerait aussi ses accès à `/submit`.
- **Version Bucket4j 8.19.0 vérifiée via la documentation officielle courante** (context7,
  2026-08-21) — ne pas la deviner, les noms d'artefacts Maven ont changé entre versions
  majeures de Bucket4j (`bucket4j_jdk17-*`, pas `bucket4j-core`/`bucket4j-redis`).
