# Rate-limiting des endpoints publics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protéger les 4 endpoints publics existants (`/dossiers/public/submit`, `/dossiers/public/track/{code}`, `/attachments/dossier/**`, `/stats/public`) contre l'abus (spam, énumération, scraping) via un rate-limiting par IP adossé à Redis.

**Architecture:** Un filtre servlet (`RateLimitFilter`) placé en tête de chaîne consulte un `RateLimiter` (interface) implémenté par `Bucket4jRedisRateLimiter`, adossé à un `ProxyManager<String>` Bucket4j/Lettuce/Redis. Comportement fail-open sur panne Redis. Réutilise le contrat d'erreur existant (`GlobalExceptionHandler.ErrorResponse`).

**Tech Stack:** Java 17, Spring Boot 3, Bucket4j 8.19.0 (Lettuce/Redis), JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-21-rate-limiting-endpoints-publics-design.md`

## Global Constraints

- `RateLimitFilter` dépend uniquement de l'interface `RateLimiter` — jamais directement de `Bucket4jRedisRateLimiter` ni d'aucun type Bucket4j/Lettuce. C'est ce qui rend le filtre testable sans Redis réel.
- Comportement **fail-open** obligatoire : toute exception levée par `RateLimiter.tryConsume(...)` (panne Redis ou autre) doit laisser la requête passer, jamais bloquer.
- Clé Redis au format `rl:{nomRoute}:{ip}` — le nom de route doit être inclus, pas seulement l'IP (sinon un quota épuisé sur une route bloque aussi les autres).
- Corps de réponse `429` : réutiliser `GlobalExceptionHandler.ErrorResponse.of(status, code, message, path)` existant (`gov.bf.ascelc.univers_audits.shared.exceptions.GlobalExceptionHandler.ErrorResponse`), `code = "RATE_LIMITED"`.
- Version Bucket4j **8.19.0** exactement, artefacts `com.bucket4j:bucket4j_jdk17-redis-common` et `com.bucket4j:bucket4j_jdk17-lettuce` (noms vérifiés contre le jar réel téléchargé le 2026-08-21 — ne pas utiliser `bucket4j-core`/`bucket4j-redis`, noms d'artefacts d'une ancienne génération de la librairie).
- Toutes les signatures Bucket4j/Lettuce ci-dessous ont été vérifiées par inspection directe des `.class` du jar 8.19.0 via `javap` (pas seulement la documentation, qui s'est révélée imprécise sur un point : `.withExpirationStrategy(...)` est la méthode réelle du builder Redis, pas `.expirationAfterWrite(...)` documenté ailleurs pour un builder générique différent) — copier le code ci-dessous verbatim, ne pas improviser d'autres signatures.

---

### Task 1 : Dépendance Maven + `RateLimitProperties` + propriétés par défaut

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/shared/config/RateLimitProperties.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Produces: `RateLimitProperties` avec getters `isEnabled()`, `getSubmit()`, `getTrack()`,
  `getAttachmentUpload()`, `getStatsPublic()` (chacun retournant `RateLimitProperties.Rule`
  avec `getCapacity(): int` et `getRefillMinutes(): int`) — consommé par Task 2 et Task 3.

- [ ] **Step 1: Ajouter les dépendances Bucket4j au `pom.xml`**

Dans `pom.xml`, ajouter ces deux dépendances dans la section `<dependencies>` (n'importe où
parmi les autres `<dependency>`, par exemple juste après le bloc `spring-boot-starter-data-redis`) :

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

Aucune dépendance Lettuce supplémentaire — `io.lettuce:lettuce-core` est déjà apporté
transitivement par `spring-boot-starter-data-redis`, déjà présent dans ce `pom.xml`.

- [ ] **Step 2: Vérifier que le projet compile avec les nouvelles dépendances**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS (aucune erreur de résolution de dépendance)

- [ ] **Step 3: Créer `RateLimitProperties`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/shared/config/RateLimitProperties.java` :

```java
package gov.bf.ascelc.univers_audits.shared.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit")
@Data
public class RateLimitProperties {

    private boolean enabled = true;
    private Rule submit = new Rule(5, 10);
    private Rule track = new Rule(20, 1);
    private Rule attachmentUpload = new Rule(10, 10);
    private Rule statsPublic = new Rule(60, 1);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rule {
        private int capacity;
        private int refillMinutes;
    }
}
```

- [ ] **Step 4: Ajouter les propriétés par défaut à `application.properties`**

Dans `src/main/resources/application.properties`, ajouter à la fin du fichier (après la
section Swagger existante) :

```properties

# ?? Rate limiting endpoints publics ?????????????????????????????
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

- [ ] **Step 5: Vérifier que le contexte Spring démarre avec le binding de propriétés**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS. (`RateLimitProperties` sera exercée par le contexte Spring complet
uniquement lors d'un `mvn test` avec base de données disponible — hors périmètre de cette
étape ; la compilation suffit à valider la syntaxe et les types.)

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/java/gov/bf/ascelc/univers_audits/shared/config/RateLimitProperties.java src/main/resources/application.properties
git commit -m "feat(rate-limit): add Bucket4j dependency and RateLimitProperties"
```

---

### Task 2 : `RedisRateLimitConfig` + `RateLimiter` + `Bucket4jRedisRateLimiter`

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/shared/config/RedisRateLimitConfig.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/shared/ratelimit/RateLimiter.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/shared/ratelimit/Bucket4jRedisRateLimiter.java`

**Interfaces:**
- Consumes: rien de Task 1 directement (lit `spring.data.redis.*`, propriétés déjà
  existantes dans `application-dev.properties`/`application-prod.properties`/`application-local.properties`
  avant ce chantier).
- Produces: `RateLimiter.tryConsume(String key, int capacity, Duration refillPeriod):
  RateLimiter.RateLimitResult` et le record `RateLimiter.RateLimitResult(boolean allowed,
  long retryAfterSeconds)` — consommés par Task 3.

- [ ] **Step 1: Créer l'interface `RateLimiter`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/shared/ratelimit/RateLimiter.java` :

```java
package gov.bf.ascelc.univers_audits.shared.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    RateLimitResult tryConsume(String key, int capacity, Duration refillPeriod);

    record RateLimitResult(boolean allowed, long retryAfterSeconds) {}
}
```

- [ ] **Step 2: Créer `RedisRateLimitConfig`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/shared/config/RedisRateLimitConfig.java` :

```java
package gov.bf.ascelc.univers_audits.shared.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedisRateLimitConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public ProxyManager<String> rateLimitProxyManager() {
        RedisURI.Builder uriBuilder = RedisURI.Builder.redis(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isBlank()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }

        RedisClient client = RedisClient.create(uriBuilder.build());
        StatefulRedisConnection<String, byte[]> connection = client.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofMinutes(15)))
                .build();
    }
}
```

**Ne pas remplacer `.withExpirationStrategy(...)` par `.expirationAfterWrite(...)`** — cette
dernière méthode existe dans Bucket4j mais pas sur ce builder Redis précis (vérifié par
`javap` sur le jar 8.19.0 réel, voir Global Constraints).

- [ ] **Step 3: Créer `Bucket4jRedisRateLimiter`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/shared/ratelimit/Bucket4jRedisRateLimiter.java` :

```java
package gov.bf.ascelc.univers_audits.shared.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class Bucket4jRedisRateLimiter implements RateLimiter {

    private final ProxyManager<String> proxyManager;

    @Override
    public RateLimitResult tryConsume(String key, int capacity, Duration refillPeriod) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, refillPeriod))
                .build();

        ConsumptionProbe probe = proxyManager.getProxy(key, () -> configuration)
                .tryConsumeAndReturnRemaining(1);

        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        return new RateLimitResult(probe.isConsumed(), retryAfterSeconds);
    }
}
```

- [ ] **Step 4: Vérifier la compilation**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/shared/config/RedisRateLimitConfig.java src/main/java/gov/bf/ascelc/univers_audits/shared/ratelimit/RateLimiter.java src/main/java/gov/bf/ascelc/univers_audits/shared/ratelimit/Bucket4jRedisRateLimiter.java
git commit -m "feat(rate-limit): wire Bucket4j/Lettuce/Redis proxy manager and RateLimiter"
```

Pas de test dédié pour `Bucket4jRedisRateLimiter` ni `RedisRateLimitConfig` — ce sont de
fins wrappers autour d'une bibliothèque tierce déjà testée, nécessiteraient un vrai Redis
pour être testés significativement (aucun test de ce dépôt n'utilise de Redis réel — voir
spec, section Tests).

---

### Task 3 : `RateLimitFilter` + tests

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/security/RateLimitFilter.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/security/RateLimitFilterTest.java`

**Interfaces:**
- Consumes: `RateLimiter.tryConsume(String, int, Duration): RateLimiter.RateLimitResult`
  (Task 2), `RateLimitProperties` (Task 1), `GlobalExceptionHandler.ErrorResponse.of(int,
  String, String, String): GlobalExceptionHandler.ErrorResponse` (déjà existant,
  `gov.bf.ascelc.univers_audits.shared.exceptions.GlobalExceptionHandler`).

- [ ] **Step 1: Écrire le test (échec de compilation attendu, `RateLimitFilter` n'existe pas encore)**

Créer `src/test/java/gov/bf/ascelc/univers_audits/security/RateLimitFilterTest.java` :

```java
package gov.bf.ascelc.univers_audits.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.bf.ascelc.univers_audits.shared.config.RateLimitProperties;
import gov.bf.ascelc.univers_audits.shared.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private RateLimiter rateLimiter;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        filter = new RateLimitFilter(rateLimiter, properties, new ObjectMapper());
    }

    @Test
    void doFilter_laisseSurPasserSiLimiteNonAtteinte() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/dossiers/public/submit");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(true, 0));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void doFilter_bloqueAvec429SiLimiteAtteinte() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/dossiers/public/track/ABCD1234");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(false, 42));
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_ecritRetryAfterEtCorpsErreur() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/dossiers/public/track/ABCD1234");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(false, 42));

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("Retry-After", "42");
        assertThat(sw.toString()).contains("RATE_LIMITED");
        assertThat(sw.toString()).contains("Trop de requêtes");
    }

    @Test
    void doFilter_laisseSurPasserRouteNonCouverte() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/config/enums");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void doFilter_laisseSurPasserSiDesactive() throws Exception {
        properties.setEnabled(false);
        filter = new RateLimitFilter(rateLimiter, properties, new ObjectMapper());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void doFilter_failOpenSiRedisIndisponible() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/dossiers/public/submit");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void doFilter_appliqueLaBonneRoutePourChaqueMethodeEtPatron() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(true, 0));

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/stats/public");
        filter.doFilterInternal(request, response, filterChain);
        verify(rateLimiter).tryConsume(eq("rl:statsPublic:10.0.0.1"), eq(60), eq(Duration.ofMinutes(1)));

        reset(request, filterChain);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/attachments/dossier/abc-123");
        filter.doFilterInternal(request, response, filterChain);
        verify(rateLimiter).tryConsume(eq("rl:attachmentUpload:10.0.0.1"), eq(10), eq(Duration.ofMinutes(10)));
    }
}
```

- [ ] **Step 2: Vérifier que le test échoue à la compilation**

Run: `./mvnw -q -Dtest=RateLimitFilterTest test`
Expected: FAIL — erreur de compilation, `RateLimitFilter` n'existe pas.

- [ ] **Step 3: Créer `RateLimitFilter`**

Créer `src/main/java/gov/bf/ascelc/univers_audits/security/RateLimitFilter.java` :

```java
package gov.bf.ascelc.univers_audits.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.bf.ascelc.univers_audits.shared.config.RateLimitProperties;
import gov.bf.ascelc.univers_audits.shared.exceptions.GlobalExceptionHandler;
import gov.bf.ascelc.univers_audits.shared.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<RouteRule> rules;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties,
                            ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.rules = List.of(
                new RouteRule("POST", "/api/v1/dossiers/public/submit", "submit",
                        properties.getSubmit().getCapacity(),
                        Duration.ofMinutes(properties.getSubmit().getRefillMinutes())),
                new RouteRule("GET", "/api/v1/dossiers/public/track/**", "track",
                        properties.getTrack().getCapacity(),
                        Duration.ofMinutes(properties.getTrack().getRefillMinutes())),
                new RouteRule("POST", "/api/v1/attachments/dossier/**", "attachmentUpload",
                        properties.getAttachmentUpload().getCapacity(),
                        Duration.ofMinutes(properties.getAttachmentUpload().getRefillMinutes())),
                new RouteRule("GET", "/api/v1/stats/public", "statsPublic",
                        properties.getStatsPublic().getCapacity(),
                        Duration.ofMinutes(properties.getStatsPublic().getRefillMinutes()))
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RouteRule matched = findMatchingRule(request);
        if (matched == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String key = "rl:" + matched.name() + ":" + ip;

        RateLimiter.RateLimitResult result;
        try {
            result = rateLimiter.tryConsume(key, matched.capacity(), matched.refillPeriod());
        } catch (RuntimeException e) {
            log.warn("Rate limiter indisponible, requête laissée passer (fail-open) : {}",
                    e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (!result.allowed()) {
            writeRateLimitedResponse(response, request, result.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RouteRule findMatchingRule(HttpServletRequest request) {
        for (RouteRule rule : rules) {
            if (rule.method().equalsIgnoreCase(request.getMethod())
                    && pathMatcher.match(rule.pattern(), request.getRequestURI())) {
                return rule;
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank())
                ? xff.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    private void writeRateLimitedResponse(HttpServletResponse response, HttpServletRequest request,
                                           long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json;charset=UTF-8");

        GlobalExceptionHandler.ErrorResponse body = GlobalExceptionHandler.ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMITED",
                "Trop de requêtes, veuillez réessayer plus tard.",
                request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), body);
    }

    private record RouteRule(String method, String pattern, String name, int capacity,
                              Duration refillPeriod) {}
}
```

- [ ] **Step 4: Vérifier que les tests passent**

Run: `./mvnw -q -Dtest=RateLimitFilterTest test`
Expected: PASS — 7 tests, 0 échec.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/security/RateLimitFilter.java src/test/java/gov/bf/ascelc/univers_audits/security/RateLimitFilterTest.java
git commit -m "feat(rate-limit): add RateLimitFilter protecting public endpoints"
```
