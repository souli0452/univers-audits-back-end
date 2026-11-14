package gov.bf.ascelc.univers_audits.security.access;

import gov.bf.ascelc.univers_audits.security.access.EndpointAccess.Kind;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lit, pour chaque point d'entrée de l'application, le contrôle d'accès réellement présent dans le code.
 * Source unique pour la matrice « Accès par rôle » et pour l'instantané de non-régression des accès.
 */
@Component
@RequiredArgsConstructor
public class EndpointAccessCollector {

    private static final String PACKAGE_APPLICATION = "gov.bf.ascelc";

    private final ApplicationContext context;

    /** Tous les points d'entrée de l'application, triés par {@code handler} puis par méthodes HTTP. */
    public List<EndpointAccess> collect() {
        RequestMappingHandlerMapping mapping =
                context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<EndpointAccess> resultat = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            Class<?> type = hm.getBeanType();
            if (!type.getName().startsWith(PACKAGE_APPLICATION)) {
                continue;
            }
            resultat.add(lire(type, hm.getMethod(), methodesHttp(e.getKey())));
        }
        resultat.sort(Comparator.comparing(EndpointAccess::handler).thenComparing(EndpointAccess::httpMethods));
        return resultat;
    }

    private static EndpointAccess lire(Class<?> type, Method methode, String httpMethods) {
        String handler = type.getSimpleName() + "#" + methode.getName() + "("
                + Arrays.stream(methode.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(","))
                + ")";

        PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(methode, PreAuthorize.class);
        if (pa == null) {
            pa = AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class);
        }
        if (pa == null) {
            return new EndpointAccess(handler, httpMethods, Kind.NONE, new TreeSet<>(), null);
        }
        Kind kind = kindOf(pa.value());
        Set<String> roles = kind == Kind.ROLES ? roles(pa.value()) : Set.of();
        return new EndpointAccess(handler, httpMethods, kind, new TreeSet<>(roles), pa.value());
    }

    private static String methodesHttp(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods().stream()
                .map(RequestMethod::name).sorted().collect(Collectors.joining(","));
    }

    // ─── Analyse des expressions @PreAuthorize ───────────────────────────────

    static Kind kindOf(String expression) {
        Set<String> roles = roles(expression);
        if (roles == null) {
            return Kind.AUTHENTICATED;
        }
        if (!roles.isEmpty()) {
            return Kind.ROLES;
        }
        return expression.contains("hasAuthority('PERM_") ? Kind.PERMISSION : Kind.SPECIFIC;
    }

    /**
     * Rôles cités par une expression : {@code null} = tout utilisateur connecté, ensemble vide = aucun rôle
     * cité (règle spécifique ou permission).
     */
    public static Set<String> roles(String expression) {
        Set<String> trouves = new LinkedHashSet<>();
        Matcher any = Pattern.compile("hasAnyRole\\(([^)]*)\\)").matcher(expression);
        while (any.find()) {
            Matcher q = Pattern.compile("'([^']+)'").matcher(any.group(1));
            while (q.find()) {
                trouves.add(q.group(1));
            }
        }
        Matcher one = Pattern.compile("hasRole\\('([^']+)'\\)").matcher(expression);
        while (one.find()) {
            trouves.add(one.group(1));
        }
        Matcher auth = Pattern.compile("hasAuthority\\('ROLE_([^']+)'\\)").matcher(expression);
        while (auth.find()) {
            trouves.add(auth.group(1));
        }

        if (!trouves.isEmpty()) {
            return trouves;
        }
        if (expression.contains("isAuthenticated()")) {
            return null;
        }
        return Collections.emptySet();
    }
}
