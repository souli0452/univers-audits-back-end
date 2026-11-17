package gov.bf.ascelc.univers_audits.security.permission;

import gov.bf.ascelc.univers_audits.model.entity.Permission;
import gov.bf.ascelc.univers_audits.model.entity.RoleDefinition;
import gov.bf.ascelc.univers_audits.repository.RoleDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transforme les rôles d'un utilisateur (portés par son jeton Keycloak) en autorités {@code PERM_xxx}, à partir des
 * attributions rôle → permissions stockées en base.
 *
 * <ul>
 *   <li>Les droits d'un rôle sont mis en cache {@code app.security.permissions-cache-ttl} (5 minutes par défaut) et
 *       le cache est vidé immédiatement par {@link #invalidate()} à chaque modification d'un rôle.</li>
 *   <li>Refus par défaut : rôle inconnu ou inactif, permission inactive ou hors catalogue, base injoignable →
 *       aucun droit. Une erreur de la base n'est jamais mise en cache.</li>
 *   <li>Le rôle d'administration garde en permanence les permissions {@linkplain AppPermission#protegee() protégées},
 *       même si la base les lui retire ou ne répond pas, pour qu'on puisse toujours réparer.</li>
 * </ul>
 */
@Slf4j
@Component
public class PermissionResolver {

    /** Rôle d'administration technique : il conserve le socle de permissions protégées. */
    public static final String ROLE_ADMINISTRATEUR = "ADMIN_DDIC";

    private record Entree(Set<String> autorites, Instant expireA) {
    }

    private final RoleDefinitionRepository repository;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entree> cache = new ConcurrentHashMap<>();

    /**
     * Incrémentée à chaque {@link #invalidate()} : une lecture commencée avant une invalidation ne doit pas
     * réécrire dans le cache des droits qui viennent d'être révoqués.
     */
    private final Object verrou = new Object();
    private long generation;

    @Autowired
    public PermissionResolver(RoleDefinitionRepository repository,
                              @Value("${app.security.permissions-cache-ttl:PT5M}") Duration ttl) {
        this(repository, ttl, Clock.systemUTC());
    }

    PermissionResolver(RoleDefinitionRepository repository, Duration ttl, Clock clock) {
        this.repository = repository;
        this.ttl = ttl;
        this.clock = clock;
    }

    /** Autorités {@code PERM_xxx} de l'ensemble de ces rôles (union), jamais {@code null}. */
    public Set<String> resolve(Collection<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return Set.of();
        }
        Instant maintenant = clock.instant();
        long generationLue = generationCourante();
        Set<String> resultat = new HashSet<>();
        List<String> aCharger = new ArrayList<>();
        for (String role : new LinkedHashSet<>(roleKeys)) {
            Entree entree = cache.get(role);
            if (entree != null && maintenant.isBefore(entree.expireA())) {
                resultat.addAll(entree.autorites());
            } else {
                aCharger.add(role);
            }
        }
        if (!aCharger.isEmpty()) {
            resultat.addAll(charger(aCharger, maintenant, generationLue));
        }
        return resultat;
    }

    /** Oublie tous les droits en cache : à appeler après toute modification d'un rôle ou de ses permissions. */
    public void invalidate() {
        synchronized (verrou) {
            generation++;
            cache.clear();
        }
    }

    private long generationCourante() {
        synchronized (verrou) {
            return generation;
        }
    }

    /** Met en cache seulement si aucune invalidation n'a eu lieu depuis le début de la lecture. */
    private void memoriser(String role, Set<String> autorites, Instant maintenant, long generationLue) {
        synchronized (verrou) {
            if (generation == generationLue) {
                cache.put(role, new Entree(Set.copyOf(autorites), maintenant.plus(ttl)));
            }
        }
    }

    private Set<String> charger(List<String> roles, Instant maintenant, long generationLue) {
        try {
            Map<String, Set<String>> parRole = new HashMap<>();
            for (RoleDefinition role : repository.findActiveByRoleKeyInWithPermissions(roles)) {
                parRole.put(role.getRoleKey(), autorites(role));
            }
            Set<String> union = new HashSet<>();
            for (String role : roles) {
                Set<String> autorites = avecSocle(role, parRole.getOrDefault(role, Set.of()));
                memoriser(role, autorites, maintenant, generationLue);
                union.addAll(autorites);
            }
            return union;
        } catch (RuntimeException e) {
            log.error("Permissions illisibles pour les rôles {} : aucun droit accordé (hors socle administrateur) — {}",
                    roles, e.toString());
            Set<String> repli = new HashSet<>();
            roles.forEach(role -> repli.addAll(avecSocle(role, Set.of())));
            return repli;
        }
    }

    private static Set<String> autorites(RoleDefinition role) {
        Set<String> autorites = new HashSet<>();
        for (Permission permission : role.getPermissions()) {
            if (!Boolean.TRUE.equals(permission.getActive())) {
                continue;
            }
            AppPermission.of(permission.getPermissionKey()).ifPresent(p -> autorites.add(p.authority()));
        }
        return autorites;
    }

    private static Set<String> avecSocle(String role, Set<String> autorites) {
        if (!ROLE_ADMINISTRATEUR.equals(role)) {
            return autorites;
        }
        Set<String> avecSocle = new HashSet<>(autorites);
        for (AppPermission p : AppPermission.values()) {
            if (p.protegee()) {
                avecSocle.add(p.authority());
            }
        }
        return avecSocle;
    }
}
